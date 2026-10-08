package ly.count.android.sdk;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The SDK's logger, which also gathers its own lines when the server's 'lg' directive asks for them and uploads them
 * as 'sdk_logs' = {"i":gatherId,"d":dropped,"l":[{"t":ms,"l":"e","m":message}]}. Capture starts at the first log line
 * into a provisional buffer, since the init lines are the ones worth having, and a live directive adopts or drops it.
 * A line whose consent is not given when it is logged is held apart until it is, see {@link #logLineConsent}.
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

    //second ceiling, on the characters each list of lines holds, since one line can be 'maxMessageLength' long
    final static int gatheredCharCeiling = 128 * 1024;

    //a log line carrying an uploaded batch is never gathered, or each batch would nest the previous one
    final static String keySdkLogs = "sdk_logs";
    final static String transportMarker = keySdkLogs + "=";

    /**
     * The consent a gathered line needs while consent is required, by the name its message starts with in brackets,
     * as in "[ModuleEvents] ..." or "[CountlyPush, init] ...". A line of any other name, such as those of the store,
     * the request queue and init, can quote any feature's data, so it needs both the events and the users consent.
     */
    private static final Map<String, String[]> logLineConsent;

    //where the events module logs the key of the event it records, as in "recordEventInternal, key:[[CLY]_view]"
    private static final String eventKeyField = "key:[";

    static {
        Map<String, String[]> consent = new HashMap<>();
        mapLogLineConsent(consent, Countly.CountlyFeatureNames.events, "ModuleEvents", "Events");
        mapLogLineConsent(consent, Countly.CountlyFeatureNames.sessions, "ModuleSessions", "Sessions");
        mapLogLineConsent(consent, Countly.CountlyFeatureNames.views, "ModuleViews", "Views", "View");
        mapLogLineConsent(consent, Countly.CountlyFeatureNames.users, "ModuleUserProfile", "UserProfile", "UserData");
        mapLogLineConsent(consent, Countly.CountlyFeatureNames.crashes, "ModuleCrash", "Crashes", "BreadcrumbHelper");
        mapLogLineConsent(consent, Countly.CountlyFeatureNames.location, "ModuleLocation", "Location");
        mapLogLineConsent(consent, Countly.CountlyFeatureNames.attribution, "ModuleAttribution", "Attribution");
        mapLogLineConsent(consent, Countly.CountlyFeatureNames.starRating, "ModuleRatings", "Ratings");
        mapLogLineConsent(consent, Countly.CountlyFeatureNames.feedback, "ModuleFeedback", "Feedback", "FeedbackDialogWebViewClient", "reportFeedbackWidgetCancelButton");
        mapLogLineConsent(consent, Countly.CountlyFeatureNames.remoteConfig, "ModuleRemoteConfig", "RemoteConfig", "RemoteConfigValueStore");
        mapLogLineConsent(consent, Countly.CountlyFeatureNames.apm, "ModuleAPM", "Apm");
        mapLogLineConsent(consent, Countly.CountlyFeatureNames.content, "ModuleContent");
        mapLogLineConsent(consent, Countly.CountlyFeatureNames.push, "CountlyPush", "CountlyPushActivity", "CountlyConfigPush", "MessageImpl", "onRegistrationId");
        logLineConsent = Collections.unmodifiableMap(consent);
    }

    /** Guards the buffer and the directive mirror. Never call out (not even log) while holding it, lock order would invert. */
    private final Object logBufferLock = new Object();

    @NonNull private final LogLineList gatheredLines = new LogLineList();
    int droppedLogLineCount = 0;

    //lines whose consent was not given when they were logged, kept out of every batch until it is
    @NonNull private final LogLineList linesAwaitingConsent = new LogLineList();
    //orders the lines of both lists, since the SDK's millisecond timestamps are not monotonic within a burst
    private long nextLineSequence = 0;

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
    @Nullable private volatile ConsentProvider consentProvider = null;
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
        v("[ModuleLog] Setting the log gathering providers, held lines:[" + heldLogLineCount() + "], awaiting consent:[" + awaitingConsentLineCount() + "]");
        this.configProvider = configProvider;
        this.consentProvider = consentProvider;
        this.requestQueueProvider = requestQueueProvider;

        releaseConsentedLogLines();
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

    /**
     * Uploads every buffered line on the calling thread, so that each request is queued with the current device ID.
     * Called right before the device ID changes without merge, after which the gather is stopped. A delivery already
     * under way is let finish first, since the batch it took belongs to the current device ID as well.
     */
    void flushGatheredLogsBeforeDeviceIdChange() {
        final RequestQueueProvider requestQueue = requestQueueProvider;
        if (!isGatheringLogs() || requestQueue == null) {
            return;
        }

        awaitLogDelivery();
        setOwnTransportWork(true);
        try {
            //a refused batch ends the loop, it is put back and the stop that follows drops it
            boolean uploaded;
            do {
                uploaded = uploadLogBatch(requestQueue, true);
            } while (uploaded);
        } catch (Exception ex) {
            e("[ModuleLog] flushGatheredLogsBeforeDeviceIdChange, failed to upload the gathered lines, " + ex);
        } finally {
            setOwnTransportWork(false);
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

    /** @return how many lines wait for their consent, outside of the buffer */
    int awaitingConsentLineCount() {
        synchronized (logBufferLock) {
            return linesAwaitingConsent.size();
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
        final String[] consentFeatures = logLineConsentFeatures(msg);
        final boolean consentGiven = logLineConsentGiven(consentFeatures);
        boolean completesBatch = false;

        synchronized (logBufferLock) {
            if (!isCapturingLogs()) {
                return;
            }
            if (isGatheringLogs() && logGatherLevels.indexOf(levelChar) < 0) {
                //level filtering is deferred while nothing is decided: the wanted levels are not known yet, so every
                //level is captured and the buffer is filtered when a directive names them
                return;
            }

            LogLine line = new LogLine(nextLineSequence++, UtilsTime.currentTimestampMs(), levelChar, trimLogMessage(msg), consentFeatures);
            if (consentGiven) {
                final boolean heldFullBatch = gatheredLines.size() >= logGatherBatchSize;
                gatheredLines.add(line);
                trimBufferLocked();

                //only the line that completes a batch asks for a delivery, so lines held back by tracking do not queue one each
                completesBatch = isGatheringLogs() && !heldFullBatch && gatheredLines.size() >= logGatherBatchSize;
            } else {
                linesAwaitingConsent.add(line);
                //not counted into 'd', a line waiting for its consent has not joined the gather
                linesAwaitingConsent.trim();
            }
        }

        if (completesBatch) {
            scheduleLogDelivery(false);
        } else if (!consentGiven && logLineConsentGiven(consentFeatures)) {
            //given since it was read above, possibly right after the release that would have moved this line
            releaseConsentedLogLines();
        }
    }

    /**
     * Maps the bracketed names a feature's log lines start with to the consent of that feature.
     *
     * @param consent the map to fill
     * @param feature the feature whose consent the lines need
     * @param names the names, without the brackets
     */
    private static void mapLogLineConsent(@NonNull final Map<String, String[]> consent, @NonNull final String feature, @NonNull final String... names) {
        final String[] features = { feature };
        for (String name : names) {
            consent.put(name, features);
        }
    }

    /**
     * Finds the consent a log line needs from the bracketed name it starts with, up to the closing bracket or a comma.
     * An events line about another feature's internal event, such as a view, needs the consent that event is recorded
     * under, see {@link ModuleEvents#internalEventConsents}.
     *
     * @param msg the line as it was logged
     * @return the features any one of whose consent lets the line go, or null for a line that needs both the events and
     * the users consent
     */
    @Nullable static String[] logLineConsentFeatures(@NonNull final String msg) {
        if (msg.isEmpty() || msg.charAt(0) != '[') {
            return null;
        }
        int end = msg.indexOf(']');
        final int comma = msg.indexOf(',');
        if (comma > 0 && (end < 0 || comma < end)) {
            end = comma;
        }
        final String[] features = end > 1 ? logLineConsent.get(msg.substring(1, end)) : null;
        if (features == null || !Countly.CountlyFeatureNames.events.equals(features[0])) {
            return features;
        }

        //the events module logs every internal event before checking the consent of the feature it belongs to
        final int keyStart = msg.indexOf(eventKeyField);
        if (keyStart < 0 || !msg.startsWith(ModuleEvents.internalEventKeyPrefix, keyStart + eventKeyField.length())) {
            return features;
        }
        final int key = keyStart + eventKeyField.length();
        final int keyEnd = msg.indexOf(']', key + ModuleEvents.internalEventKeyPrefix.length());
        return keyEnd < 0 ? null : ModuleEvents.internalEventConsents.get(msg.substring(key, keyEnd));
    }

    /**
     * Tells whether a line needing the given consent may be gathered now. Reads the consent without logging, as it runs
     * inside log calls, and says no until the consent provider is set, so the lines logged before that are held.
     *
     * @param consentFeatures what {@link #logLineConsentFeatures(String)} returned for the line
     * @return true when consent is not required or the consent the line needs is given
     */
    private boolean logLineConsentGiven(@Nullable final String[] consentFeatures) {
        final ConsentProvider consent = consentProvider;
        if (consent == null) {
            return false;
        }
        if (consentFeatures == null) {
            return consent.getConsentSilently(Countly.CountlyFeatureNames.events) && consent.getConsentSilently(Countly.CountlyFeatureNames.users);
        }
        for (String feature : consentFeatures) {
            if (consent.getConsentSilently(feature)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Moves the lines whose consent is given by now into the buffer, in capture order with the lines already in it, and
     * asks for a delivery when a full batch is held. Called when consent is given, when the configuration changes,
     * which can lift the consent requirement or turn tracking back on, and when the providers are set.
     */
    void releaseConsentedLogLines() {
        final ConsentProvider consent = consentProvider;
        if (consent == null) {
            return;
        }

        //decided before taking the lock, which nothing may call out of
        final Set<String> givenFeatures = new HashSet<>();
        for (String feature : ModuleConsent.validFeatureNames) {
            if (consent.getConsentSilently(feature)) {
                givenFeatures.add(feature);
            }
        }
        final boolean unattributedGiven = givenFeatures.contains(Countly.CountlyFeatureNames.events) && givenFeatures.contains(Countly.CountlyFeatureNames.users);

        synchronized (logBufferLock) {
            List<LogLine> released = linesAwaitingConsent.removeIf(line -> line.consentFeatures == null ? unattributedGiven : anyGiven(line.consentFeatures, givenFeatures));
            if (!released.isEmpty()) {
                gatheredLines.merge(released);
                trimBufferLocked();
            }
        }

        if (hasFullLogBatch()) {
            scheduleLogDelivery(false);
        }
    }

    /**
     * @param features the features a line can go with the consent of
     * @param givenFeatures the features whose consent is given
     * @return true when any of the features has its consent given
     */
    private static boolean anyGiven(@NonNull final String[] features, @NonNull final Set<String> givenFeatures) {
        for (String feature : features) {
            if (givenFeatures.contains(feature)) {
                return true;
            }
        }
        return false;
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

            if (uploadLogBatch(requestQueue, includePartialBatch) && hasFullLogBatch()) {
                scheduleLogDelivery(includePartialBatch);
            }
        } catch (Exception ex) {
            e("[ModuleLog] deliverLogBatches, failed to upload the gathered log batch, " + ex);
        } finally {
            setOwnTransportWork(false);
        }
    }

    /**
     * Takes one batch out of the buffer and hands it to the request queue, putting it back if the queue refuses it. The
     * caller marks its thread as this feature's own transport work.
     *
     * @param requestQueue the queue to hand the batch to
     * @param includePartialBatch whether a batch smaller than the batch size may be taken
     * @return true when a batch was handed over
     */
    private boolean uploadLogBatch(@NonNull final RequestQueueProvider requestQueue, final boolean includePartialBatch) {
        LogBatch batch = takeLogBatch(includePartialBatch);
        if (batch == null) {
            return false;
        }

        d("[ModuleLog] uploadLogBatch, uploading a gathered log batch, size:[" + batch.payload.length() + "] characters");
        if (!requestQueue.sendSdkLogs(batch.payload)) {
            //the queue refused it (torn down, or tracking flipped off in between), so the lines are still ours
            restoreLogBatch(batch);
            return false;
        }
        return true;
    }

    /** Stops the delivery thread and lets a delivery already under way finish, so it can not land after what follows. */
    private void awaitLogDelivery() {
        ExecutorService executor = logDeliveryExecutor;
        if (executor == null) {
            return;
        }

        executor.shutdown();
        try {
            //bounded, as the caller is usually the main thread
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                w("[ModuleLog] awaitLogDelivery, a log batch delivery is still running");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /** One batch taken out of the buffer: the payload plus what it was built from, so a refused send can put it back. */
    private static class LogBatch {
        final String gatherId;
        final String payload;
        final List<LogLine> lines;
        final int dropped;

        LogBatch(String gatherId, String payload, List<LogLine> lines, int dropped) {
            this.gatherId = gatherId;
            this.payload = payload;
            this.lines = lines;
            this.dropped = dropped;
        }
    }

    /** Takes up to one batch of the oldest lines, or null when there is nothing to upload. */
    @Nullable private LogBatch takeLogBatch(final boolean includePartialBatch) {
        synchronized (logBufferLock) {
            if (!isGatheringLogs() || logGatherId == null || gatheredLines.size() == 0) {
                return null;
            }
            if (!includePartialBatch && gatheredLines.size() < logGatherBatchSize) {
                return null;
            }

            final int count = Math.min(logGatherBatchSize, gatheredLines.size());
            List<LogLine> taken = gatheredLines.first(count);
            String payload = buildLogBatchPayload(logGatherId, droppedLogLineCount, taken);
            if (payload == null) {
                //could not be serialised, keep the lines for the next attempt
                return null;
            }

            gatheredLines.removeFirst(count);
            LogBatch batch = new LogBatch(logGatherId, payload, taken, droppedLogLineCount);
            //the loss is reported once, with the batch that follows it
            droppedLogLineCount = 0;

            return batch;
        }
    }

    /**
     * Puts a batch the queue refused back at the front of the buffer, oldest first, drop count included. A batch of a
     * gather that has ended since is dropped instead, the next gather must not adopt it.
     */
    private void restoreLogBatch(@NonNull final LogBatch batch) {
        synchronized (logBufferLock) {
            if (!isGatheringLogs() || !batch.gatherId.equals(logGatherId)) {
                return;
            }
            gatheredLines.addFirst(batch.lines);
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

    /** Enforces both ceilings on the buffer by dropping the oldest lines and counting them into 'd'. */
    private void trimBufferLocked() {
        droppedLogLineCount += gatheredLines.trim();
    }

    /** Drops the lines of unwanted levels from both lists. Not counted as dropped, they were never wanted. */
    private void filterBufferLocked() {
        final String levels = logGatherLevels;
        gatheredLines.removeIf(line -> levels.indexOf(line.level) < 0);
        linesAwaitingConsent.removeIf(line -> levels.indexOf(line.level) < 0);
    }

    private void clearBufferLocked() {
        gatheredLines.clear();
        linesAwaitingConsent.clear();
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
        final long sequence;
        final long timestamp;
        final char level;
        @NonNull final String message;
        //the features any one of whose consent lets the line go, null when it needs both the events and the users consent
        @Nullable final String[] consentFeatures;

        LogLine(final long sequence, final long timestamp, final char level, @NonNull final String message, @Nullable final String[] consentFeatures) {
            this.sequence = sequence;
            this.timestamp = timestamp;
            this.level = level;
            this.message = message;
            this.consentFeatures = consentFeatures;
        }
    }

    /** Selects log lines, see {@link LogLineList#removeIf(LinePredicate)}. */
    private interface LinePredicate {
        /**
         * @param line the line to look at
         * @return true to select it
         */
        boolean test(@NonNull LogLine line);
    }

    /** Lines in capture order and the characters they hold, kept within both ceilings by dropping the oldest. */
    private static final class LogLineList {
        @NonNull private final List<LogLine> lines = new ArrayList<>();
        private int chars = 0;

        /** @return how many lines the list holds */
        int size() {
            return lines.size();
        }

        /** @param line the line to add after the others */
        void add(@NonNull final LogLine line) {
            lines.add(line);
            chars += line.message.length();
        }

        /** @param added lines to add, after which the list is put back in capture order */
        void merge(@NonNull final List<LogLine> added) {
            for (LogLine line : added) {
                add(line);
            }
            Collections.sort(lines, (first, second) -> Long.compare(first.sequence, second.sequence));
        }

        /** @param restored lines taken from the front, put back in front of the rest */
        void addFirst(@NonNull final List<LogLine> restored) {
            lines.addAll(0, restored);
            for (LogLine line : restored) {
                chars += line.message.length();
            }
        }

        /**
         * @param count how many lines to copy, at most the size
         * @return a copy of the oldest lines, which stay in the list
         */
        @NonNull List<LogLine> first(final int count) {
            return new ArrayList<>(lines.subList(0, count));
        }

        /** @param count how many of the oldest lines to remove, at most the size */
        void removeFirst(final int count) {
            List<LogLine> removed = lines.subList(0, count);
            for (LogLine line : removed) {
                chars -= line.message.length();
            }
            removed.clear();
        }

        /**
         * @param predicate selects the lines to remove
         * @return the removed lines, in their order
         */
        @NonNull List<LogLine> removeIf(@NonNull final LinePredicate predicate) {
            List<LogLine> removed = new ArrayList<>();
            Iterator<LogLine> iterator = lines.iterator();
            while (iterator.hasNext()) {
                LogLine line = iterator.next();
                if (predicate.test(line)) {
                    iterator.remove();
                    chars -= line.message.length();
                    removed.add(line);
                }
            }
            return removed;
        }

        /** @return how many of the oldest lines were dropped to bring the list within both ceilings */
        int trim() {
            int dropped = 0;
            while (lines.size() > ModuleConfiguration.logGatheringMaxBufferedLines || (chars > gatheredCharCeiling && lines.size() > 1)) {
                chars -= lines.remove(0).message.length();
                dropped++;
            }
            return dropped;
        }

        /** Removes every line. */
        void clear() {
            lines.clear();
            chars = 0;
        }
    }
}
