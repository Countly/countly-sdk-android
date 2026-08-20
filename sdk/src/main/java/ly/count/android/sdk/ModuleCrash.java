package ly.count.android.sdk;

import android.content.Context;
import android.util.Base64;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class ModuleCrash extends ModuleBase {
    //native crash
    private static final String countlyFolderName = "Countly";
    private static final String countlyNativeCrashFolderName = "CrashDumps";

    //crash filtering
    GlobalCrashFilterCallback globalCrashFilterCallback;
    //Deprecated, will be removed in the future
    CrashFilterCallback crashFilterCallback;

    boolean recordAllThreads = false;

    //tracks whether the unhandled crash handler has already been installed, so we only wrap the global handler once
    volatile boolean unhandledCrashHandlerInstalled = false;

    //Thread.getDefaultUncaughtExceptionHandler()/setDefaultUncaughtExceptionHandler is process-global state,
    //and installing wraps the handler that is there now. Two instances initialising on two threads hold no
    //common monitor (init synchronises on its own Countly), so without this lock both could read the same
    //existing handler and the second setDefault would drop the first instance out of the chain for good.
    private static final Object crashHandlerLock = new Object();

    //Kept so halt() can unlink this instance from the process-global handler chain; otherwise a halted
    //instance stays reachable from Thread.getDefaultUncaughtExceptionHandler() and keeps recording.
    private Thread.UncaughtExceptionHandler installedCrashHandler = null;
    private Thread.UncaughtExceptionHandler previousCrashHandler = null;
    //Set when we could not unlink; the handler then only delegates. Volatile: crashes hit any thread.
    private volatile boolean crashHandlerDetached = false;

    @Nullable
    Map<String, Object> customCrashSegments = null;

    //interface for SDK users
    final Crashes crashesInterface;

    @Nullable
    Map<String, String> metricOverride = null;

    BreadcrumbHelper breadcrumbHelper;

    ModuleCrash(Countly cly, CountlyConfig config) {
        super(cly, config);
        L.v("[ModuleCrash] Initialising");

        globalCrashFilterCallback = config.crashes.globalCrashFilterCallback;
        crashFilterCallback = config.crashFilterCallback;

        recordAllThreads = config.crashes.recordAllThreadsWithCrash;

        //Copy first, for the same reason as ModuleViews' global segmentation: this truncates in place and the
        //map belongs to the developer's config, which a second instance may also be built from.
        setCustomCrashSegmentsInternal(config.crashes.customCrashSegment == null ? null : new LinkedHashMap<>(config.crashes.customCrashSegment));

        metricOverride = config.metricOverride;

        crashesInterface = new Crashes();
        //the limit this instance RESOLVED (developer config plus the server behaviour settings), not the raw
        //developer value on the config - this was the last reader still bypassing sdkInternalLimits_
        breadcrumbHelper = new BreadcrumbHelper(cly.sdkInternalLimits_.maxBreadcrumbCount, L);

        assert breadcrumbHelper != null;
    }

    /**
     * Called during init to check if there are any crash dumps saved
     *
     * @param context android context
     */
    void checkForNativeCrashDumps(@NonNull Context context) {
        assert context != null;

        L.d("[ModuleCrash] Checking for native crash dumps");

        String basePath = context.getCacheDir().getAbsolutePath();
        String finalPath = basePath + File.separator + countlyFolderName + File.separator + countlyNativeCrashFolderName;

        File folder = new File(finalPath);
        if (folder.exists()) {
            L.d("[ModuleCrash] Native crash folder exists, checking for dumps");

            File[] dumpFiles = folder.listFiles();

            int dumpFileCount = -1;

            if (dumpFiles != null) {
                dumpFileCount = dumpFiles.length;
            }

            L.d("[ModuleCrash] Crash dump folder contains [" + dumpFileCount + "] files");

            if (dumpFiles != null) {
                for (File dumpFile : dumpFiles) {
                    //record crash
                    recordNativeException(dumpFile);

                    //Always drop the dump, including when consent is missing: retaining it would need a
                    //cache with its own retention policy, and minidumps are raw process memory we do not
                    //want sitting on the device waiting for a consent that may never come.
                    dumpFile.delete();
                }
            }
        } else {
            L.d("[ModuleCrash] Native crash folder does not exist");
        }
    }

    private void recordNativeException(@NonNull File dumpFile) {
        assert dumpFile != null;

        L.d("[ModuleCrash] Recording native crash dump: [" + dumpFile.getName() + "]");

        //check for consent
        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.crashes)) {
            return;
        }

        //read bytes
        int size = (int) dumpFile.length();
        byte[] bytes = new byte[size];

        try {
            BufferedInputStream buf = new BufferedInputStream(new FileInputStream(dumpFile));
            buf.read(bytes, 0, bytes.length);
            buf.close();
        } catch (Exception e) {
            L.e("[ModuleCrash] Failed to read dump file bytes");
            e.printStackTrace();
            return;
        }

        //convert to base64
        String dumpString = Base64.encodeToString(bytes, Base64.NO_WRAP);

        CrashData crashData = prepareCrashData(dumpString, false, true, null);
        if (!crashFilterCheck(crashData, true)) {
            sendCrashReportToQueue(crashData, true);
        }
    }

    private CrashData prepareCrashData(@NonNull String error, final boolean handled, final boolean isNativeCrash, @Nullable Map<String, Object> customSegmentation) {
        assert error != null;

        if (!isNativeCrash) {
            error = error.substring(0, Math.min(20_000, error.length()));
        }

        Map<String, Object> combinedSegmentationValues = new HashMap<>();
        if (customCrashSegments != null) {
            combinedSegmentationValues.putAll(customCrashSegments);
        }
        if (customSegmentation != null) {
            UtilsInternalLimits.applySdkInternalLimitsToSegmentation(customSegmentation, _cly.sdkInternalLimits_, L, "[ModuleCrash] sendCrashReportToQueue");
            combinedSegmentationValues.putAll(customSegmentation);
        }

        UtilsInternalLimits.truncateSegmentationValues(combinedSegmentationValues, _cly.sdkInternalLimits_.maxSegmentationValues, "[ModuleCrash] prepareCrashData", L);

        return new CrashData(error, combinedSegmentationValues, breadcrumbHelper.getBreadcrumbs(), deviceInfo.getCrashMetrics(_cly.context_, isNativeCrash, metricOverride, L), !handled);
    }

    private String prepareStackTrace(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);

        if (recordAllThreads) {
            addAllThreadInformationToCrash(pw, _cly.sdkInternalLimits_);
        }

        String truncatedStackTrace = UtilsInternalLimits.applyInternalLimitsToStackTraces(sw.toString(), _cly.sdkInternalLimits_.maxStackTraceLineLength, "[ModuleCrash] prepareStackTrace", L);
        return truncatedStackTrace;
    }

    public void sendCrashReportToQueue(@NonNull CrashData crashData, final boolean isNativeCrash) {
        assert crashData != null;
        L.d("[ModuleCrash] sendCrashReportToQueue");

        String crashDataString = deviceInfo.getCrashDataJSON(crashData, isNativeCrash).toString();
        requestQueueProvider.sendCrashReport(crashDataString, !crashData.getFatal());
    }

    /**
     * Sets custom segments to be reported with crash reports
     * In custom segments you can provide any string key values to segments crashes by
     *
     * @param segments Map&lt;String, Object&gt; key segments and their values
     */
    void setCustomCrashSegmentsInternal(@Nullable Map<String, Object> segments) {
        L.d("[ModuleCrash] Calling setCustomCrashSegmentsInternal");

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.crashes)) {
            return;
        }

        Map<String, Object> customSegments;
        if (segments == null) {
            customSegments = new HashMap<>();
        } else {
            customSegments = segments;
        }

        UtilsInternalLimits.applySdkInternalLimitsToSegmentation(customSegments, _cly.sdkInternalLimits_, L, "[ModuleCrash] setCustomCrashSegmentsInternal");

        customCrashSegments = customSegments;
    }

    void enableCrashReporting() {
        //read-then-set on the process-global handler chain: serialise it against any other instance doing
        //the same, so no instance is silently dropped out of the chain
        boolean installed = false;
        synchronized (crashHandlerLock) {
            if (!unhandledCrashHandlerInstalled && !crashHandlerDetached) {
                //crashHandlerDetached means this module was already torn down; installing then would put a
                //dead instance into the process-global chain with nothing left to unlink it
                unhandledCrashHandlerInstalled = true;
                //get default handler
                final Thread.UncaughtExceptionHandler oldHandler = Thread.getDefaultUncaughtExceptionHandler();
                previousCrashHandler = oldHandler;

                installedCrashHandler = new CountlyCrashHandler(this, oldHandler);

                Thread.setDefaultUncaughtExceptionHandler(installedCrashHandler);
                installed = true;
            }
        }

        //log outside the lock: informListener runs the app's LogCallback, and no other SDK lock is held
        //while calling into app code
        if (installed) {
            L.d("[ModuleCrash] Enabling unhandled crash reporting");
        }
    }

    /**
     * The handler this instance installs into the process-global uncaught-exception chain.
     * <p>
     * Static, and holds the module only weakly, because {@code halt()} can unlink from the chain only
     * while this handler is still the process default. Once the host app (or another Countly instance)
     * installs a handler on top, there is no way to remove a link from the middle of the chain, so this
     * object stays there for the life of the process. A strong reference would pin the halted module,
     * and through it the whole Countly instance, its context and its queues, forever. Delegation to the
     * previous handler must keep working either way, so that link is held strongly.
     */
    private static final class CountlyCrashHandler implements Thread.UncaughtExceptionHandler {
        private final WeakReference<ModuleCrash> moduleRef;
        private final Thread.UncaughtExceptionHandler previous;

        CountlyCrashHandler(@NonNull ModuleCrash module, @Nullable Thread.UncaughtExceptionHandler previous) {
            this.moduleRef = new WeakReference<>(module);
            this.previous = previous;
        }

        @Override
        public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
            ModuleCrash module = moduleRef.get();
            //a halted (or already collected) instance only delegates, its queues are torn down
            if (module != null && !module.crashHandlerDetached) {
                module.recordUnhandledCrash(e);
            }

            //if there was another handler before, notify it also
            if (previous != null) {
                previous.uncaughtException(t, e);
            }
        }
    }

    /**
     * Records an unhandled crash on this instance. Kept off the handler itself so the handler can stay a
     * static class with no strong link back to this module.
     */
    private void recordUnhandledCrash(@NonNull Throwable e) {
        L.d("[ModuleCrash] Uncaught crash handler triggered");
        if (consentProvider.getConsent(Countly.CountlyFeatureNames.crashes) && configProvider.getAutomaticCrashReportingEnabled()) {
            String stackTrace = prepareStackTrace(e);
            CrashData crashData = prepareCrashData(stackTrace, false, false, null);
            if (!crashFilterCheck(crashData, false)) {
                sendCrashReportToQueue(crashData, false);
            }
        }
    }

    /**
     * Call to check if crash matches one of the filters
     * If it does, the crash should be ignored
     *
     * @param crashData CrashData object to check
     * @param isNativeCrash whether the crash is a native crash dump (base64 string, not a Java stack trace)
     * @return true if a match was found
     */
    boolean crashFilterCheck(@NonNull CrashData crashData, final boolean isNativeCrash) {
        assert crashData != null;

        L.d("[ModuleCrash] Calling crashFilterCheck");

        if (crashFilterCallback != null) {
            return crashFilterCallback.filterCrash(crashData.getStackTrace());
        }

        if (globalCrashFilterCallback == null) {
            return false;
        }

        if (globalCrashFilterCallback.filterCrash(crashData)) {
            L.d("[ModuleCrash] crashFilterCheck, Global Crash filter found a match, exception will be ignored, [" + crashData.getStackTrace().substring(0, Math.min(crashData.getStackTrace().length(), 60)) + "]");
            return true;
        }

        crashData.calculateChangedFields();

        UtilsInternalLimits.applyInternalLimitsToBreadcrumbs(crashData.getBreadcrumbs(), _cly.sdkInternalLimits_, L, "[ModuleCrash] sendCrashReportToQueue");
        UtilsInternalLimits.applySdkInternalLimitsToSegmentation(crashData.getCrashSegmentation(), _cly.sdkInternalLimits_, L, "[ModuleCrash] sendCrashReportToQueue");
        // Stack trace line limits must not be applied to native crashes: the "stack trace" of a
        // native crash is a single-line base64 dump, so per-line truncation would corrupt the dump.
        if (!isNativeCrash) {
            String truncatedStackTrace = UtilsInternalLimits.applyInternalLimitsToStackTraces(crashData.getStackTrace(), _cly.sdkInternalLimits_.maxStackTraceLineLength, "[ModuleCrash] sendCrashReportToQueue", L);
            crashData.setStackTrace(truncatedStackTrace);
        }
        UtilsInternalLimits.removeUnsupportedDataTypes(crashData.getCrashSegmentation(), L);
        UtilsInternalLimits.removeUnsupportedDataTypes(crashData.getCrashMetrics(), L);

        return false;
    }

    void addAllThreadInformationToCrash(@NonNull PrintWriter pw, @NonNull ConfigSdkInternalLimits sdkInternalLimits) {
        assert pw != null;
        assert sdkInternalLimits != null;

        Map<Thread, StackTraceElement[]> allThreads = Thread.getAllStackTraces();
        int threadCount = 0;

        for (Map.Entry<Thread, StackTraceElement[]> entry : allThreads.entrySet()) {
            if (threadCount >= sdkInternalLimits.maxStackTraceThreadCount) {
                break;
            }

            StackTraceElement[] val = entry.getValue();
            Thread thread = entry.getKey();

            if (val == null || thread == null) {
                continue;
            }

            pw.println();
            pw.println("Thread " + thread.getName());

            for (int i = 0; i < Math.min(val.length, sdkInternalLimits.maxStackTraceLinesPerThread); i++) {
                pw.println(val[i].toString());
            }
            threadCount++;
        }
    }

    /**
     * Common call for handling exceptions
     *
     * @param exception Exception to log
     * @param itIsHandled If the exception is handled or not (fatal)
     * @return Returns link to Countly for call chaining
     */
    Countly recordExceptionInternal(@Nullable final Throwable exception, final boolean itIsHandled, final Map<String, Object> customSegmentation) {
        L.i("[ModuleCrash] Logging exception, handled:[" + itIsHandled + "]");

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.crashes)) {
            return _cly;
        }

        if (!configProvider.getCrashReportingEnabled()) {
            L.d("[ModuleCrash] recordExceptionInternal, Crash reporting is disabled in the server configuration");
            return _cly;
        }

        if (exception == null) {
            L.d("[ModuleCrash] recordException, provided exception was null, returning");
            return _cly;
        }

        String exceptionString = prepareStackTrace(exception);

        CrashData crashData = prepareCrashData(exceptionString, itIsHandled, false, customSegmentation);
        if (crashFilterCheck(crashData, false)) {
            L.d("[ModuleCrash] Crash filter found a match, exception will be ignored, [" + exceptionString.substring(0, Math.min(exceptionString.length(), 60)) + "]");
        } else {
            sendCrashReportToQueue(crashData, false);
        }
        return _cly;
    }

    Countly addBreadcrumbInternal(@Nullable String breadcrumb) {
        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.crashes)) {
            return _cly;
        }

        if (breadcrumb == null || breadcrumb.isEmpty()) {
            L.w("[ModuleCrash] addBreadcrumbInternal, Can't add a null or empty crash breadcrumb");
            return _cly;
        }

        breadcrumbHelper.addBreadcrumb(breadcrumb, _cly.sdkInternalLimits_.maxValueSize, _cly.sdkInternalLimits_.maxBreadcrumbCount);
        return _cly;
    }

    @Override
    void initFinished(@NonNull CountlyConfig config) {
        if (!configProvider.getCrashReportingEnabled()) {
            L.d("[ModuleCrash] initFinished, Crash reporting is disabled in the server configuration");
            return;
        }

        //enable unhandled crash reporting
        if (!configProvider.getCrashReportingEnabled()) {
            L.w("[ModuleCrash] initFinished, Crash reporting is disabled in the server configuration");
            return;
        }

        // Install the uncaught-exception handler when automatic crash reporting is enabled. The flag is
        // resolved through the SBS precedence chain (seeded from the developer's enableUnhandledCrashReporting,
        // overridden by the server), so the server can enable it even if the developer did not. We never wrap
        // the global handler while automatic crash reporting is disabled, so apps that did not enable it (and
        // whose server does not enable it) keep their own handler intact, avoiding interference with other crash tools.
        installUnhandledCrashHandlerIfEnabled();

        //check for previous native crash dumps
        if (config.crashes.checkForNativeCrashDumps) {
            //sdk-native writes dumps to one fixed process-wide path, so only the storage owner may
            //consume them - otherwise the first instance to init claims every dump under its app key.
            if (_cly.storageNamespace_.isEmpty()) {
                //flag so that this can be turned off during testing
                _cly.moduleCrash.checkForNativeCrashDumps(config.context);
            } else {
                //Warn, not debug: reading the folder is also what deletes it, so in an app that only ever
                //initialises named instances nothing consumes the dumps and they accumulate on disk.
                //Initialise the default instance as well if you use native crash reporting.
                L.w("[ModuleCrash] initFinished, skipping the native crash dump check: the process-global dump folder belongs to the default instance, so native crashes are only reported (and the dumps only deleted) when the default instance is initialised");
            }
        }
    }

    /**
     * Installs the unhandled crash handler if automatic crash reporting is enabled (resolved through the SBS
     * precedence chain) and it has not been installed yet. Called at init and whenever the SDK configuration
     * changes, so a server that enables 'acr' at runtime starts catching crashes.
     */
    private void installUnhandledCrashHandlerIfEnabled() {
        if (!unhandledCrashHandlerInstalled && configProvider.getCrashReportingEnabled() && configProvider.getAutomaticCrashReportingEnabled()) {
            enableCrashReporting();
        }
    }

    @Override
    void onSdkConfigurationChanged(@NonNull CountlyConfig config) {
        installUnhandledCrashHandlerIfEnabled();
    }

    @Override
    void halt() {
        //stop recording first, so a crash racing this teardown can not touch the dying queues. This runs
        //before any check: Countly#tearDown reaches the module halts after the store and the connection
        //queue are already gone, so leaving this instance recording for even a moment longer is exactly
        //what the flag exists to prevent.
        crashHandlerDetached = true;

        //Read installedCrashHandler under the same lock that installs it, so an install racing this teardown
        //cannot slip between the null check and the unlink. Log outside the lock: informListener runs the
        //app's LogCallback, and no other SDK lock is ever held while calling into app code.
        String outcome = null;
        synchronized (crashHandlerLock) {
            if (installedCrashHandler != null) {
                if (Thread.getDefaultUncaughtExceptionHandler() == installedCrashHandler) {
                    //still the process default, so restoring unlinks us entirely and makes the instance collectable
                    Thread.setDefaultUncaughtExceptionHandler(previousCrashHandler);
                    outcome = "[ModuleCrash] halt, restored the previously installed uncaught exception handler";
                } else {
                    //Another handler sits on top and there is no way to remove a link from the middle of the
                    //chain, so ours stays there for the life of the process, neutralised. It only holds this
                    //module weakly, so the halted instance is still collectable; it keeps delegating downstream.
                    outcome = "[ModuleCrash] halt, another uncaught exception handler was installed on top of this one, it stays in the chain but will no longer record crashes";
                }

                installedCrashHandler = null;
                previousCrashHandler = null;
                unhandledCrashHandlerInstalled = false;
            }
        }

        if (outcome != null) {
            L.d(outcome);
        }
    }

    public class Crashes {
        /**
         * Add crash breadcrumb like log record to the log that will be send together with crash report
         *
         * @param record String a bread crumb for the crash report
         * @return Returns link to Countly for call chaining
         */
        public Countly addCrashBreadcrumb(String record) {
            synchronized (_cly) {
                L.i("[Crashes] Adding crash breadcrumb");

                return addBreadcrumbInternal(record);
            }
        }

        /**
         * Log handled exception to report it to server as non fatal crash
         *
         * @param exception Exception to log
         * @return Returns link to Countly for call chaining
         */
        public Countly recordHandledException(Exception exception) {
            synchronized (_cly) {
                return recordExceptionInternal(exception, true, null);
            }
        }

        /**
         * Log handled exception to report it to server as non fatal crash
         *
         * @param exception Throwable to log
         * @return Returns link to Countly for call chaining
         */
        public Countly recordHandledException(Throwable exception) {
            synchronized (_cly) {
                return recordExceptionInternal(exception, true, null);
            }
        }

        /**
         * Log unhandled exception to report it to server as fatal crash
         *
         * @param exception Exception to log
         * @return Returns link to Countly for call chaining
         */
        public Countly recordUnhandledException(Exception exception) {
            synchronized (_cly) {
                return recordExceptionInternal(exception, false, null);
            }
        }

        /**
         * Log unhandled exception to report it to server as fatal crash
         *
         * @param exception Throwable to log
         * @return Returns link to Countly for call chaining
         */
        public Countly recordUnhandledException(Throwable exception) {
            synchronized (_cly) {
                return recordExceptionInternal(exception, false, null);
            }
        }

        /**
         * Log handled exception to report it to server as non fatal crash
         *
         * @param exception Throwable to log
         * @return Returns link to Countly for call chaining
         */
        public Countly recordHandledException(final Throwable exception, final Map<String, Object> customSegmentation) {
            synchronized (_cly) {
                return recordExceptionInternal(exception, true, customSegmentation);
            }
        }

        /**
         * Log unhandled exception to report it to server as fatal crash
         *
         * @param exception Throwable to log
         * @return Returns link to Countly for call chaining
         */
        public Countly recordUnhandledException(final Throwable exception, final Map<String, Object> customSegmentation) {
            synchronized (_cly) {
                return recordExceptionInternal(exception, false, customSegmentation);
            }
        }
    }
}
