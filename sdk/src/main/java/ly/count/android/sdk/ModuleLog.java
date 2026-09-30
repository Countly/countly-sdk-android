package ly.count.android.sdk;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The SDK's logger, which also gathers its own lines when the server's 'lg' directive asks for them and uploads them
 * as 'sdk_logs' = {"i":gatherId,"d":dropped,"l":[{"t":ms,"l":"e","m":message}]}. Capture starts at the first log line
 * into a provisional buffer, since the init lines are the ones worth having, and a live directive adopts or drops it.
 */
public class ModuleLog {
    public interface LogCallback {
        void LogHappened(String logMessage, ModuleLog.LogLevel logLevel);
    }

    public enum LogLevel {Verbose, Debug, Info, Warning, Error}

    LogCallback logListener = null;

    HealthTracker healthTracker;

    int countWarnings = 0;
    int countErrors = 0;

    // Per-instance logging state. In a multi-instance setup every Countly object owns its own
    // ModuleLog, so console output honors that instance's own config instead of the singleton's.
    // loggingEnabled is mirrored from the owning Countly (see Countly#setLoggingEnabled); tag is set
    // once per named instance in Countly.instance(name) (named instances get "Countly-<name>", the
    // default keeps the plain "Countly" tag) so a named instance's logcat output is attributable.
    boolean loggingEnabled = false;
    String tag = Countly.TAG;
    // PER USER LOG GATHERING

    //'sdk_logs' payload keys
    final static String keyBatchGatherId = "i";
    final static String keyBatchDroppedCount = "d";
    final static String keyBatchLines = "l";

    //keys of a single line inside the payload
    final static String keyLineTimestamp = "t";
    final static String keyLineLevel = "l";
    final static String keyLineMessage = "m";

    //the server stores at most this many characters of a single message, so there is no point in uploading more
    final static int maxMessageLength = 4096;

    //second ceiling, on the total characters held. A line may be up to 'maxMessageLength' long, so a line count on its
    //own is not a bound on memory
    final static int gatheredCharCeiling = 128 * 1024;

    //a log line carrying an uploaded batch is never gathered, or each batch would nest the previous one
    final static String keySdkLogs = "sdk_logs";
    final static String transportMarker = keySdkLogs + "=";

    /** Guards the buffer and the directive mirror. Never call out (not even log) while holding it, lock order would invert. */
    private final Object logBufferLock = new Object();

    @NonNull private final List<LogLine> gatheredLines = new ArrayList<>();
    int droppedLogLineCount = 0;
    int gatheredChars = 0;

    //mirror of the directive, held here so that capturing a line never has to call the configuration provider.
    //the state is read on every single log call, without taking the lock
    @NonNull volatile ModuleConfiguration.LogGatheringState logGatheringState = ModuleConfiguration.LogGatheringState.UNDECIDED;
    @Nullable String logGatherId = null;
    @NonNull String logGatherLevels = ModuleConfiguration.logGatheringAllLevels;
    int logGatherBatchSize = ModuleConfiguration.logGatheringDefaultBatchSize;

    //set on a thread while it does this feature's own transport work, so what it logs meanwhile is not gathered
    private final ThreadLocal<Boolean> uploadingLogBatch = new ThreadLocal<>();

    @Nullable private volatile ExecutorService logDeliveryExecutor = null;
    private volatile boolean sdkInitFinished = false;

    @Nullable private ConfigurationProvider configProvider = null;
    @Nullable private ConsentProvider consentProvider = null;
    @Nullable private RequestQueueProvider requestQueueProvider = null;

    void SetListener(LogCallback logListener) {
        this.logListener = logListener;
    }

    void setLoggingEnabled(boolean loggingEnabled) {
        this.loggingEnabled = loggingEnabled;
    }

    void setTag(String tag) {
        this.tag = tag;
    }

    void trackWarning() {
        if (healthTracker == null) {
            countWarnings++;
        } else {
            healthTracker.logWarning();
        }
    }

    void trackError() {
        if (healthTracker == null) {
            countErrors++;
        } else {
            healthTracker.logError();
        }
    }

    void setHealthChecker(HealthTracker healthTracker) {
        v("[ModuleLog] Setting healthTracker W:" + countWarnings + " E:" + countErrors);
        this.healthTracker = healthTracker;

        if (healthTracker == null) {
            return;
        }

        for (int a = 0; a < countErrors; a++) {
            healthTracker.logError();
        }

        for (int a = 0; a < countWarnings; a++) {
            healthTracker.logWarning();
        }

        countWarnings = 0;
        countErrors = 0;
    }

    public void v(String msg) {
        if (!logLineWanted()) {
            return;
        }
        if (loggingEnabled) {
            Log.v(tag, msg);
        }
        informListener(msg, null, LogLevel.Verbose);
    }

    public void d(String msg) {
        if (!logLineWanted()) {
            return;
        }
        if (loggingEnabled) {
            Log.d(tag, msg);
        }
        informListener(msg, null, LogLevel.Debug);
    }

    public void i(String msg) {
        if (!logLineWanted()) {
            return;
        }
        if (loggingEnabled) {
            Log.i(tag, msg);
        }
        informListener(msg, null, LogLevel.Info);
    }

    public void w(String msg) {
        w(msg, null);
    }

    public void w(String msg, Throwable t) {
        trackWarning();
        if (!logLineWanted()) {
            return;
        }
        if (loggingEnabled) {
            Log.w(tag, msg);
        }
        informListener(msg, null, LogLevel.Warning);
    }

    public void e(String msg) {
        e(msg, null);
    }

    public void e(String msg, Throwable t) {
        trackError();
        if (!logLineWanted()) {
            return;
        }
        if (loggingEnabled) {
            Log.e(tag, msg, t);
        }
        informListener(msg, t, LogLevel.Error);
    }

    /** @return true if console or a listener wants log lines. Capture is not part of this, other classes gate dumps with it. */
    public boolean logEnabled() {
        return logListener != null || loggingEnabled;
    }

    /** @return true if anything at all wants this line: a developer facing sink, or the log gathering capture */
    private boolean logLineWanted() {
        return logEnabled() || isCapturingLogs();
    }

    private void informListener(String msg, final Throwable t, final LogLevel level) {
        try {
            if (msg == null) {
                msg = "";
            }
            if (t != null) {
                msg += Log.getStackTraceString(t);
            }

            if (logListener != null) {
                logListener.LogHappened(msg, level);
            }

            captureLogLine(msg, level);
        } catch (Exception ex) {
            Log.e(tag, "[ModuleLog] Failed to inform listener [" + ex.toString() + "]");
        }
    }

    // ---- per user log gathering ----

    /** Setters, not constructor parameters: the logger exists long before these do. Applies the parsed directive right away. */
    void setLogGatheringProviders(@NonNull ConfigurationProvider configProvider, @NonNull ConsentProvider consentProvider, @NonNull RequestQueueProvider requestQueueProvider) {
        v("[ModuleLog] Setting the log gathering providers, held lines:[" + heldLogLineCount() + "]");
        this.configProvider = configProvider;
        this.consentProvider = consentProvider;
        this.requestQueueProvider = requestQueueProvider;

        applyLogGatheringDirective("init");
    }

    /** Applies the provider's current directive to what is held. 'source' is for the log line only. */
    void applyLogGatheringDirective(@NonNull final String source) {
        ConfigurationProvider provider = configProvider;
        if (provider == null) {
            //the configuration module hands itself over during init, so until it does there is no directive to read.
            //Capture keeps going in the meantime
            v("[ModuleLog] applyLogGatheringDirective, no configuration provider yet, source:[" + source + "]");
            return;
        }

        ModuleConfiguration.LogGatheringState state = provider.getLogGatheringState();
        String gatherId = provider.getLogGatheringId();
        String levels = provider.getLogGatheringLevels();
        int batchSize = provider.getLogGatheringBatchSize();

        if (!adoptLogGatheringDirective(state, gatherId, levels, batchSize)) {
            return;
        }

        d("[ModuleLog] applyLogGatheringDirective, source:[" + source + "], state:[" + state + "], id:[" + gatherId + "], levels:[" + levels + "], batch size:[" + batchSize + "], held lines:[" + heldLogLineCount() + "]");

        if (state != ModuleConfiguration.LogGatheringState.GATHERING) {
            shutdownLogDelivery();
            return;
        }

        ensureLogDeliveryExecutor();
        scheduleLogDelivery(false);
    }

    /** The queue is usable only from here on, so this is the first chance for the init lines to go out. */
    void onSdkInitFinished() {
        sdkInitFinished = true;

        if (isGatheringLogs()) {
            d("[ModuleLog] onSdkInitFinished, uploading the lines gathered during init");
            scheduleLogDelivery(true);
        }
    }

    /** Timer flush, so a buffer that never fills a batch still goes out. */
    void flushGatheredLogs() {
        if (isGatheringLogs() && heldLogLineCount() > 0) {
            scheduleLogDelivery(true);
        }
    }

    /** Marks the calling thread as sending a gathered batch, so what it logs meanwhile is not gathered. */
    void setOwnTransportWork(boolean transporting) {
        if (transporting) {
            uploadingLogBatch.set(Boolean.TRUE);
        } else {
            uploadingLogBatch.remove();
        }
    }

    /** The process may not come back, get the tail out now. */
    void onAppEnteredBackground() {
        if (!isGatheringLogs()) {
            return;
        }

        //nothing is persisted, so the only way the tail survives the app going away is getting it out now
        scheduleLogDelivery(true);
    }

    /** Back to the fresh process state: undecided, empty buffer, capturing speculatively. Nothing is persisted. */
    void haltLogGathering() {
        shutdownLogDelivery();
        configProvider = null;
        consentProvider = null;
        requestQueueProvider = null;
        sdkInitFinished = false;

        synchronized (logBufferLock) {
            logGatheringState = ModuleConfiguration.LogGatheringState.UNDECIDED;
            logGatherId = null;
            logGatherLevels = ModuleConfiguration.logGatheringAllLevels;
            logGatherBatchSize = ModuleConfiguration.logGatheringDefaultBatchSize;
            clearBufferLocked();
        }
    }

    /** @return true while lines still have to be held, so the level methods reach the capture with console logging off */
    boolean isCapturingLogs() {
        return logGatheringState != ModuleConfiguration.LogGatheringState.NOT_GATHERING;
    }

    boolean isGatheringLogs() {
        return logGatheringState == ModuleConfiguration.LogGatheringState.GATHERING;
    }

    int heldLogLineCount() {
        synchronized (logBufferLock) {
            return gatheredLines.size();
        }
    }

    private void captureLogLine(@NonNull final String msg, @NonNull final LogLevel level) {
        if (Boolean.TRUE.equals(uploadingLogBatch.get())) {
            //this thread is uploading a batch, everything it logs is this feature's own transport commentary
            return;
        }
        if (!isCapturingLogs()) {
            return;
        }
        if (msg.contains(transportMarker)) {
            //a line that carries an uploaded batch. The request path logs the request it queues, and gathering that
            //would nest every batch inside the next one and keep an idle SDK gathering forever
            return;
        }

        final char levelChar = levelToChar(level);
        boolean batchIsFull;

        synchronized (logBufferLock) {
            if (!isCapturingLogs()) {
                return;
            }
            if (isGatheringLogs() && logGatherLevels.indexOf(levelChar) < 0) {
                //level filtering is deferred while nothing is decided: the wanted levels are not known yet, so every
                //level is captured and the buffer is filtered when a directive names them
                return;
            }

            LogLine line = new LogLine(UtilsTime.currentTimestampMs(), levelChar, trimLogMessage(msg));
            gatheredLines.add(line);
            gatheredChars += line.message.length();
            trimBufferLocked();

            batchIsFull = isGatheringLogs() && gatheredLines.size() >= logGatherBatchSize;
        }

        if (batchIsFull) {
            scheduleLogDelivery(false);
        }
    }

    /** Applies a directive to what is held. Returns true if anything changed. */
    private boolean adoptLogGatheringDirective(@NonNull final ModuleConfiguration.LogGatheringState newState, @Nullable final String newGatherId, @NonNull final String newLevels, final int newBatchSize) {
        synchronized (logBufferLock) {
            boolean changed = logGatheringState != newState
                || !Objects.equals(logGatherId, newGatherId)
                || !logGatherLevels.equals(newLevels)
                || logGatherBatchSize != newBatchSize;

            if (!changed) {
                return false;
            }

            final boolean wasGathering = isGatheringLogs();
            final String previousGatherId = logGatherId;

            logGatheringState = newState;
            logGatherId = newGatherId;
            logGatherLevels = newLevels;
            logGatherBatchSize = newBatchSize;

            switch (newState) {
                case NOT_GATHERING:
                    //decided against gathering, so everything held speculatively goes
                    clearBufferLocked();
                    break;
                case GATHERING:
                    if (wasGathering && previousGatherId != null && !previousGatherId.equals(newGatherId)) {
                        //a new gather, the held lines belong to the previous one and the server would reject them
                        clearBufferLocked();
                    } else {
                        //adoption, or a levels/batch size change inside the same gather: what is held is this gather's
                        filterBufferLocked();
                        trimBufferLocked();
                    }
                    break;
                case UNDECIDED:
                default:
                    //nothing has decided anything yet, keep capturing every level and wait for the server
                    break;
            }

            return true;
        }
    }

    /** Queues an upload on the delivery thread, never the caller's, which may be inside a synchronized store write. */
    private void scheduleLogDelivery(final boolean includePartialBatch) {
        if (!sdkInitFinished) {
            //the request queue is not usable before init finishes, the tail goes out from 'onSdkInitFinished'
            return;
        }

        ExecutorService executor = logDeliveryExecutor;
        if (executor == null || executor.isShutdown()) {
            return;
        }

        try {
            executor.submit(() -> deliverLogBatches(includePartialBatch));
        } catch (Exception e) {
            w("[ModuleLog] scheduleLogDelivery, could not queue the log batch upload, " + e);
        }
    }

    /** Uploads one batch, with capture suppressed on this thread, and queues another run while full batches remain. */
    private void deliverLogBatches(final boolean includePartialBatch) {
        RequestQueueProvider requestQueue = requestQueueProvider;
        if (requestQueue == null) {
            return;
        }

        setOwnTransportWork(true);
        try {
            ConfigurationProvider provider = configProvider;
            if (provider != null && !provider.getTrackingEnabled()) {
                //the request queue refuses everything while tracking is off, so taking the lines out of the buffer now
                //would only lose them. They stay held, oldest first, until tracking is back on
                d("[ModuleLog] deliverLogBatches, tracking is disabled, keeping the gathered lines buffered");
                return;
            }

            ConsentProvider consent = consentProvider;
            if (consent != null && !consent.anyConsentGiven()) {
                //gathered lines quote event keys, segmentation and whole queued requests, so they are user data.
                //Without any consent nothing else leaves the device either, the lines stay held until it is given
                d("[ModuleLog] deliverLogBatches, no consent given, keeping the gathered lines buffered");
                return;
            }

            LogBatch batch = takeLogBatch(includePartialBatch);
            if (batch == null) {
                return;
            }

            d("[ModuleLog] deliverLogBatches, uploading a gathered log batch, size:[" + batch.payload.length() + "] characters");
            if (!requestQueue.sendSdkLogs(batch.payload)) {
                //the queue refused it (torn down, or tracking flipped off in between), so the lines are still ours
                restoreLogBatch(batch);
                return;
            }

            if (hasFullLogBatch()) {
                scheduleLogDelivery(includePartialBatch);
            }
        } catch (Exception ex) {
            e("[ModuleLog] deliverLogBatches, failed to upload the gathered log batch, " + ex);
        } finally {
            setOwnTransportWork(false);
        }
    }

    /** One batch taken out of the buffer: the payload plus what it was built from, so a refused send can put it back. */
    private static class LogBatch {
        final String payload;
        final List<LogLine> lines;
        final int dropped;

        LogBatch(String payload, List<LogLine> lines, int dropped) {
            this.payload = payload;
            this.lines = lines;
            this.dropped = dropped;
        }
    }

    /** Takes up to one batch of the oldest lines, or null when there is nothing to upload. */
    @Nullable private LogBatch takeLogBatch(final boolean includePartialBatch) {
        synchronized (logBufferLock) {
            if (!isGatheringLogs() || logGatherId == null || gatheredLines.isEmpty()) {
                return null;
            }
            if (!includePartialBatch && gatheredLines.size() < logGatherBatchSize) {
                return null;
            }

            final int count = Math.min(logGatherBatchSize, gatheredLines.size());
            List<LogLine> taken = new ArrayList<>(gatheredLines.subList(0, count));
            String payload = buildLogBatchPayload(logGatherId, droppedLogLineCount, taken);
            if (payload == null) {
                //could not be serialised, keep the lines for the next attempt
                return null;
            }

            for (LogLine line : taken) {
                gatheredChars -= line.message.length();
            }
            gatheredLines.subList(0, count).clear();
            LogBatch batch = new LogBatch(payload, taken, droppedLogLineCount);
            //the loss is reported once, with the batch that follows it
            droppedLogLineCount = 0;

            return batch;
        }
    }

    /** Puts a batch the queue refused back at the front of the buffer, oldest first, drop count included. */
    private void restoreLogBatch(@NonNull final LogBatch batch) {
        synchronized (logBufferLock) {
            gatheredLines.addAll(0, batch.lines);
            for (LogLine line : batch.lines) {
                gatheredChars += line.message.length();
            }
            droppedLogLineCount += batch.dropped;
            trimBufferLocked();
        }
    }

    private boolean hasFullLogBatch() {
        synchronized (logBufferLock) {
            return isGatheringLogs() && gatheredLines.size() >= logGatherBatchSize;
        }
    }

    private void ensureLogDeliveryExecutor() {
        ExecutorService executor = logDeliveryExecutor;
        if (executor == null || executor.isShutdown()) {
            v("[ModuleLog] ensureLogDeliveryExecutor, creating the log batch delivery executor");
            logDeliveryExecutor = Executors.newSingleThreadExecutor();
        }
    }

    private void shutdownLogDelivery() {
        ExecutorService executor = logDeliveryExecutor;
        logDeliveryExecutor = null;

        if (executor != null) {
            v("[ModuleLog] shutdownLogDelivery, stopping the log batch delivery executor");
            //'shutdown' and not 'shutdownNow': an already queued tail upload is still allowed to finish
            executor.shutdown();
        }
    }

    /** Enforces both ceilings by dropping the oldest lines and counting them into 'd'. */
    private void trimBufferLocked() {
        while (gatheredLines.size() > ModuleConfiguration.logGatheringMaxBufferedLines
            || (gatheredChars > gatheredCharCeiling && gatheredLines.size() > 1)) {
            gatheredChars -= gatheredLines.remove(0).message.length();
            droppedLogLineCount++;
        }
    }

    /** Drops held lines of unwanted levels. Not counted as dropped, they were never wanted. */
    private void filterBufferLocked() {
        Iterator<LogLine> iterator = gatheredLines.iterator();
        while (iterator.hasNext()) {
            LogLine line = iterator.next();
            if (logGatherLevels.indexOf(line.level) < 0) {
                gatheredChars -= line.message.length();
                iterator.remove();
            }
        }
    }

    private void clearBufferLocked() {
        gatheredLines.clear();
        gatheredChars = 0;
        droppedLogLineCount = 0;
    }

    @Nullable private static String buildLogBatchPayload(@NonNull final String gatherId, final int dropped, @NonNull final List<LogLine> batch) {
        try {
            JSONArray payloadLines = new JSONArray();
            for (LogLine line : batch) {
                JSONObject payloadLine = new JSONObject();
                payloadLine.put(keyLineTimestamp, line.timestamp);
                payloadLine.put(keyLineLevel, String.valueOf(line.level));
                payloadLine.put(keyLineMessage, line.message);
                payloadLines.put(payloadLine);
            }

            JSONObject payload = new JSONObject();
            //the id has to be the one the directive named, the server rejects a batch that carries another
            payload.put(keyBatchGatherId, gatherId);
            payload.put(keyBatchDroppedCount, dropped);
            payload.put(keyBatchLines, payloadLines);

            return payload.toString();
        } catch (JSONException e) {
            return null;
        }
    }

    @NonNull private static String trimLogMessage(@NonNull final String msg) {
        if (msg.length() <= maxMessageLength) {
            return msg;
        }
        return msg.substring(0, maxMessageLength);
    }

    private static char levelToChar(@NonNull final LogLevel level) {
        switch (level) {
            case Error:
                return 'e';
            case Warning:
                return 'w';
            case Info:
                return 'i';
            case Debug:
                return 'd';
            case Verbose:
            default:
                return 'v';
        }
    }

    private static class LogLine {
        final long timestamp;
        final char level;
        @NonNull final String message;

        LogLine(final long timestamp, final char level, @NonNull final String message) {
            this.timestamp = timestamp;
            this.level = level;
            this.message = message;
        }
    }
}
