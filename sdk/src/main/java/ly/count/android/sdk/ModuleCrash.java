package ly.count.android.sdk;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ModuleCrash extends ModuleBase{
    //native crash
    private static final String countlyFolderName = "Countly";
    private static final String countlyNativeCrashFolderName = "CrashDumps";

    //crash filtering
    CrashFilterCallback crashFilterCallback;

    boolean recordAllThreads = false;

    //interface for SDK users
    final Crashes crashesInterface;

    ModuleCrash(Countly cly, CountlyConfig config){
        super(cly);

        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleCrash] Initialising");
        }

        setCrashFilterCallback(config.crashFilterCallback);

        recordAllThreads = config.recordAllThreadsWithCrash;

        _cly.setCustomCrashSegmentsInternal(config.customCrashSegment);

        crashesInterface = new Crashes();
    }

    /**
     * Called during init to check if there are any crash dumps saved
     * @param context android context
     */
    protected synchronized void checkForNativeCrashDumps(Context context){
        if (_cly.isLoggingEnabled()) {
            Log.d(_cly.TAG, "[ModuleCrash] Checking for native crash dumps");
        }

        String basePath = context.getCacheDir().getAbsolutePath();
        String finalPath = basePath + File.separator + countlyFolderName + File.separator + countlyNativeCrashFolderName;

        File folder = new File(finalPath);
        if (folder.exists()) {
            if (_cly.isLoggingEnabled()) {
                Log.d(_cly.TAG, "[ModuleCrash] Native crash folder exists, checking for dumps");
            }

            File[] dumpFiles = folder.listFiles();

            if (_cly.isLoggingEnabled()) {
                Log.d(_cly.TAG, "[ModuleCrash] Crash dump folder contains [" + dumpFiles.length + "] files");
            }

            for (File dumpFile : dumpFiles) {
                //record crash
                recordNativeException(dumpFile);

                //delete dump file
                dumpFile.delete();
            }
        } else {
            if (_cly.isLoggingEnabled()) {
                Log.d(_cly.TAG, "[ModuleCrash] Native crash folder does not exist");
            }
        }
    }

    private synchronized void recordNativeException(File dumpFile){
        if (_cly.isLoggingEnabled()) {
            Log.d(_cly.TAG, "[ModuleCrash] Recording native crash dump: [" + dumpFile.getName() + "]");
        }

        //check for consent
        if(!_cly.getConsent(Countly.CountlyFeatureNames.crashes)){
            return;
        }

        //read bytes
        int size = (int)dumpFile.length();
        byte[] bytes = new byte[size];

        try {
            BufferedInputStream buf = new BufferedInputStream(new FileInputStream(dumpFile));
            buf.read(bytes, 0, bytes.length);
            buf.close();
        } catch (Exception e) {
            if (_cly.isLoggingEnabled()) {
                Log.e(_cly.TAG, "[ModuleCrash] Failed to read dump file bytes");
            }
            e.printStackTrace();
            return;
        }

        //convert to base64
        String dumpString = Base64.encodeToString(bytes, Base64.NO_WRAP);

        //record crash
        _cly.connectionQueue_.sendCrashReport(dumpString, false, true);
    }

    void setCrashFilterCallback(CrashFilterCallback callback) {
        crashFilterCallback = callback;
    }

    /**
     * Call to check if crash matches one of the filters
     * If it does, the crash should be ignored
     * @param crash
     * @return true if a match was found
     */
    boolean crashFilterCheck(String crash){
        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleCrash] Calling crashFilterCheck");
        }

        if(crashFilterCallback == null){
            //no filter callback set, nothing to compare against
            return false;
        }

        return crashFilterCallback.filterCrash(crash);
    }

    void addAllThreadInformationToCrash(PrintWriter pw){
        Map<Thread, StackTraceElement[]> allThreads = Thread.getAllStackTraces();

        for (Map.Entry<Thread, StackTraceElement[]> entry : allThreads.entrySet()) {
            StackTraceElement[] val = entry.getValue();
            Thread thread = entry.getKey();

            if(val == null || thread == null) {
                continue;
            }

            pw.println();
            pw.println("Thread " + thread.getName());
            for(int a = 0 ; a < val.length ; a++) {
                pw.println(val[a].toString());
            }
        }
    }

    /**
     * Common call for handling exceptions
     * @param exception Exception to log
     * @param itIsHandled If the exception is handled or not (fatal)
     * @return Returns link to Countly for call chaining
     */
    synchronized Countly recordExceptionInternal(Throwable exception, boolean itIsHandled) {
        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleCrash] Logging exception, handled:[" + itIsHandled + "]");
        }

        if (!_cly.isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before recording exceptions");
        }

        if(!_cly.getConsent(Countly.CountlyFeatureNames.crashes)){
            return _cly;
        }

        if(exception == null){
            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[ModuleCrash] recordException, provided exception was null, returning");
            }

            return _cly;
        }

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        exception.printStackTrace(pw);

        if(recordAllThreads) {
            addAllThreadInformationToCrash(pw);
        }

        String exceptionString = sw.toString();

        if(crashFilterCheck(exceptionString)){
            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[ModuleCrash] Crash filter found a match, exception will be ignored, [" + exceptionString.substring(0, Math.min(exceptionString.length(), 60)) + "]");
            }
        } else {
            _cly.connectionQueue_.sendCrashReport(exceptionString, itIsHandled, false);
        }
        return _cly;
    }

    @SuppressWarnings("InfiniteRecursion")
    public void stackOverflow() {
        this.stackOverflow();
    }

    @SuppressWarnings("ConstantConditions")
    public synchronized Countly crashTest(int crashNumber) {

        if (crashNumber == 1){
            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "Running crashTest 1");
            }

            stackOverflow();

        }else if (crashNumber == 2){

            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "Running crashTest 2");
            }

            //noinspection UnusedAssignment,divzero
            @SuppressWarnings("NumericOverflow") int test = 10/0;

        }else if (crashNumber == 3){

            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "Running crashTest 3");
            }

            throw new RuntimeException("This is a crash");
        }
        else{
            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "Running crashTest 4");
            }

            String test = null;
            //noinspection ResultOfMethodCallIgnored
            test.charAt(1);
        }
        return Countly.sharedInstance();
    }

    @Override
    void halt(){

    }

    public class Crashes {
        /**
         * Add crash breadcrumb like log record to the log that will be send together with crash report
         * @param record String a bread crumb for the crash report
         * @return Returns link to Countly for call chaining
         */
        public synchronized Countly addCrashBreadcrumb(String record) {
            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[Crashes] Adding crash breadcrumb");
            }

            if(!_cly.getConsent(Countly.CountlyFeatureNames.crashes)){
                return _cly;
            }

            if(record == null || record.isEmpty()) {
                if (_cly.isLoggingEnabled()) {
                    Log.d(Countly.TAG, "[Crashes] Can't add a null or empty crash breadcrumb");
                }
                return _cly;
            }

            CrashDetails.addLog(record);
            return _cly;
        }

        /**
         * Log handled exception to report it to server as non fatal crash
         * @param exception Exception to log
         * @return Returns link to Countly for call chaining
         */
        public synchronized Countly recordHandledException(Exception exception) {
            return recordExceptionInternal(exception, true);
        }

        /**
         * Log handled exception to report it to server as non fatal crash
         * @param exception Throwable to log
         * @return Returns link to Countly for call chaining
         */
        public synchronized Countly recordHandledException(Throwable exception) {
            return recordExceptionInternal(exception, true);
        }

        /**
         * Log unhandled exception to report it to server as fatal crash
         * @param exception Exception to log
         * @return Returns link to Countly for call chaining
         */
        public synchronized Countly recordUnhandledException(Exception exception) {
            return recordExceptionInternal(exception, false);
        }

        /**
         * Log unhandled exception to report it to server as fatal crash
         * @param exception Throwable to log
         * @return Returns link to Countly for call chaining
         */
        public synchronized Countly recordUnhandledException(Throwable exception) {
            return recordExceptionInternal(exception, false);
        }
    }
}
