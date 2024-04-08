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
import java.util.HashMap;
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

        setCustomCrashSegmentsInternal(config.crashes.customCrashSegment);

        metricOverride = config.metricOverride;

        crashesInterface = new Crashes();
        breadcrumbHelper = new BreadcrumbHelper(config.sdkInternalLimits.maxBreadcrumbCount, L);

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

                    //delete dump file
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
        if (!crashFilterCheck(crashData)) {
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
            combinedSegmentationValues.putAll(customSegmentation);
        }

        UtilsInternalLimits.removeUnsupportedDataTypes(combinedSegmentationValues);
        UtilsInternalLimits.truncateSegmentationValues(combinedSegmentationValues, _cly.config_.sdkInternalLimits.maxSegmentationValues, "[ModuleCrash] prepareCrashData", L);

        return new CrashData(error, combinedSegmentationValues, breadcrumbHelper.getBreadcrumbs(), deviceInfo.getCrashMetrics(_cly.context_, isNativeCrash, metricOverride), !handled);
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
    void setCustomCrashSegmentsInternal(Map<String, Object> segments) {
        L.d("[ModuleCrash] Calling setCustomCrashSegmentsInternal");

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.crashes)) {
            return;
        }

        if (segments != null) {
            UtilsInternalLimits.removeUnsupportedDataTypes(segments);
        }
        customCrashSegments = segments;
    }

    void enableCrashReporting() {
        L.d("[ModuleCrash] Enabling unhandled crash reporting");
        //get default handler
        final Thread.UncaughtExceptionHandler oldHandler = Thread.getDefaultUncaughtExceptionHandler();

        Thread.UncaughtExceptionHandler handler = new Thread.UncaughtExceptionHandler() {

            @Override
            public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
                L.d("[ModuleCrash] Uncaught crash handler triggered");
                if (consentProvider.getConsent(Countly.CountlyFeatureNames.crashes)) {

                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    e.printStackTrace(pw);

                    //add other threads
                    if (recordAllThreads) {
                        addAllThreadInformationToCrash(pw);
                    }

                    //check if it passes the crash filter
                    CrashData crashData = prepareCrashData(sw.toString(), false, false, null);
                    if (!crashFilterCheck(crashData)) {
                        sendCrashReportToQueue(crashData, false);
                    }
                }

                //if there was another handler before
                if (oldHandler != null) {
                    //notify it also
                    oldHandler.uncaughtException(t, e);
                }
            }
        };

        Thread.setDefaultUncaughtExceptionHandler(handler);
    }

    /**
     * Call to check if crash matches one of the filters
     * If it does, the crash should be ignored
     *
     * @param crashData CrashData object to check
     * @return true if a match was found
     */
    boolean crashFilterCheck(@NonNull CrashData crashData) {
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

        L.d("[ModuleCrash] crashFilterCheck, while filtering new breadcrumbs are added, checking for maxBreadcrumbCount: [" + _cly.config_.sdkInternalLimits.maxBreadcrumbCount + "]");
        if (crashData.getBreadcrumbs().size() > _cly.config_.sdkInternalLimits.maxBreadcrumbCount) {
            L.d("[ModuleCrash] crashFilterCheck, after filtering, breadcrumbs limit is exceeded. clipping oldest count:[" + crashData.getBreadcrumbs().size() + "]");
            int gonnaClip = crashData.getBreadcrumbs().size() - _cly.config_.sdkInternalLimits.maxBreadcrumbCount;
            if (gonnaClip > 0) {
                crashData.getBreadcrumbs().subList(0, gonnaClip).clear();
            }
        }

        UtilsInternalLimits.removeUnsupportedDataTypes(crashData.getCrashSegmentation());
        UtilsInternalLimits.removeUnsupportedDataTypes(crashData.getCrashMetrics());
        UtilsInternalLimits.truncateSegmentationValues(crashData.getCrashSegmentation(), _cly.config_.sdkInternalLimits.maxSegmentationValues, "[ModuleCrash] sendCrashReportToQueue", L);

        return false;
    }

    void addAllThreadInformationToCrash(PrintWriter pw) {
        Map<Thread, StackTraceElement[]> allThreads = Thread.getAllStackTraces();

        for (Map.Entry<Thread, StackTraceElement[]> entry : allThreads.entrySet()) {
            StackTraceElement[] val = entry.getValue();
            Thread thread = entry.getKey();

            if (val == null || thread == null) {
                continue;
            }

            pw.println();
            pw.println("Thread " + thread.getName());
            for (StackTraceElement stackTraceElement : val) {
                pw.println(stackTraceElement.toString());
            }
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

        if (exception == null) {
            L.d("[ModuleCrash] recordException, provided exception was null, returning");
            return _cly;
        }

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        exception.printStackTrace(pw);

        if (recordAllThreads) {
            addAllThreadInformationToCrash(pw);
        }

        String exceptionString = sw.toString();

        CrashData crashData = prepareCrashData(exceptionString, itIsHandled, false, customSegmentation);
        if (crashFilterCheck(crashData)) {
            L.d("[ModuleCrash] Crash filter found a match, exception will be ignored, [" + exceptionString.substring(0, Math.min(exceptionString.length(), 60)) + "]");
        } else {
            //in case the exception needs to be recorded, truncate it
            //String[] splitRes = exceptionString.split("\n");
            //int totalAllowedLines = _cly.config_.maxStackTraceThreadCount * _cly.config_.maxStackTraceLinesPerThread;
            //StringBuilder sb = new StringBuilder(exceptionString.length());
            //
            //for(int a = 0 ; a < splitRes.length && a < totalAllowedLines ; a++) {
            //    sb.append(splitRes[a].substring(0, Math.min(splitRes[a].length(), _cly.config_.maxStackTraceLineLength)));
            //}
            //sendCrashReportToQueue(sb.toString(), itIsHandled, false, customSegmentation);
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

        breadcrumbHelper.addBreadcrumb(breadcrumb, _cly.config_.sdkInternalLimits.maxValueSize);
        return _cly;
    }

    @Override
    void initFinished(@NonNull CountlyConfig config) {
        //enable unhandled crash reporting
        if (config.crashes.enableUnhandledCrashReporting) {
            enableCrashReporting();
        }

        //check for previous native crash dumps
        if (config.crashes.checkForNativeCrashDumps) {
            //flag so that this can be turned off during testing
            _cly.moduleCrash.checkForNativeCrashDumps(config.context);
        }
    }

    @Override
    void halt() {

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
