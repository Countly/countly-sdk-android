package ly.count.android.sdk;

import android.content.Context;
import android.util.Base64;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

public class ModuleCrash extends ModuleBase {
    //native crash
    private static final String countlyFolderName = "Countly";
    private static final String countlyNativeCrashFolderName = "CrashDumps";

    //crash filtering
    CrashFilterCallback crashFilterCallback;

    boolean recordAllThreads = false;

    //interface for SDK users
    final Crashes crashesInterface;

    ModuleCrash(Countly cly, CountlyConfig config) {
        super(cly, config);
        L.v("[ModuleCrash] Initialising");

        //enable unhandled crash reporting
        if (config.enableUnhandledCrashReporting) {
            enableCrashReporting();
        }

        setCrashFilterCallback(config.crashFilterCallback);

        recordAllThreads = config.recordAllThreadsWithCrash;

        setCustomCrashSegmentsInternal(config.customCrashSegment);

        crashesInterface = new Crashes();
    }

    /**
     * Called during init to check if there are any crash dumps saved
     *
     * @param context android context
     */
    synchronized void checkForNativeCrashDumps(Context context) {
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

    private synchronized void recordNativeException(File dumpFile) {
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

        //record crash
        requestQueueProvider.sendCrashReport(dumpString, false, true, null);
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
            Utils.removeUnsupportedDataTypes(segments);
            CrashDetails.setCustomSegments(segments);
        }
    }

    void enableCrashReporting() {
        L.d("[ModuleCrash] Enabling unhandled crash reporting");
        //get default handler
        final Thread.UncaughtExceptionHandler oldHandler = Thread.getDefaultUncaughtExceptionHandler();

        Thread.UncaughtExceptionHandler handler = new Thread.UncaughtExceptionHandler() {

            @Override
            public void uncaughtException(Thread t, Throwable e) {
                L.d("[ModuleCrash] Uncaught crash handler triggered");
                if (consentProvider.getConsent(Countly.CountlyFeatureNames.crashes)) {

                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    e.printStackTrace(pw);

                    //add other threads
                    if (recordAllThreads) {
                        addAllThreadInformationToCrash(pw);
                    }

                    String exceptionString = sw.toString();

                    //check if it passes the crash filter
                    if (!crashFilterCheck(exceptionString)) {
                        requestQueueProvider.sendCrashReport(exceptionString, false, false, null);
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

    void setCrashFilterCallback(CrashFilterCallback callback) {
        crashFilterCallback = callback;
    }

    /**
     * Call to check if crash matches one of the filters
     * If it does, the crash should be ignored
     *
     * @param crash
     * @return true if a match was found
     */
    boolean crashFilterCheck(String crash) {
        L.d("[ModuleCrash] Calling crashFilterCheck");

        if (crashFilterCallback == null) {
            //no filter callback set, nothing to compare against
            return false;
        }

        return crashFilterCallback.filterCrash(crash);
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
    synchronized Countly recordExceptionInternal(final Throwable exception, final boolean itIsHandled, final Map<String, Object> customSegmentation) {
        L.i("[ModuleCrash] Logging exception, handled:[" + itIsHandled + "]");

        if (!_cly.isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before recording exceptions");
        }

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

        if (crashFilterCheck(exceptionString)) {
            L.d("[ModuleCrash] Crash filter found a match, exception will be ignored, [" + exceptionString.substring(0, Math.min(exceptionString.length(), 60)) + "]");
        } else {
            requestQueueProvider.sendCrashReport(exceptionString, itIsHandled, false, customSegmentation);
        }
        return _cly;
    }

    @Override
    void initFinished(CountlyConfig config) {
        //check for previous native crash dumps
        if (config.checkForNativeCrashDumps) {
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

                if (!consentProvider.getConsent(Countly.CountlyFeatureNames.crashes)) {
                    return _cly;
                }

                if (record == null || record.isEmpty()) {
                    L.e("[Crashes] Can't add a null or empty crash breadcrumb");
                    return _cly;
                }

                CrashDetails.addLog(record);
                return _cly;
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
            {
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
