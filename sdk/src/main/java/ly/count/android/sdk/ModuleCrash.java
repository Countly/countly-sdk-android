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
    Pattern[] crashRegexFilters = null;

    boolean recordAllThreads = false;

    //interface for SDK users
    final Crashes crashesInterface;

    ModuleCrash(Countly cly, CountlyConfig config){
        super(cly);

        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleCrash] Initialising");
        }

        setCrashFiltersInternal(config.crashRegexFilters);

        recordAllThreads = config.recordAllThreadsWithCrash;

        crashesInterface = new Crashes();
    }

    /**
     * Called during init to check if there are any crash dumps saved
     * @param context android context
     */
    protected synchronized void checkForNativeCrashDumps(Context context){
        Log.d(_cly.TAG, "[ModuleCrash] Checking for native crash dumps");

        String basePath = context.getCacheDir().getAbsolutePath();
        String finalPath = basePath + File.separator + countlyFolderName + File.separator + countlyNativeCrashFolderName;

        File folder = new File(finalPath);
        if (folder.exists()) {
            Log.d(_cly.TAG, "[ModuleCrash] Native crash folder exists, checking for dumps");

            File[] dumpFiles = folder.listFiles();
            Log.d(_cly.TAG,"[ModuleCrash] Crash dump folder contains [" + dumpFiles.length + "] files");
            for (File dumpFile : dumpFiles) {
                //record crash
                recordNativeException(dumpFile);

                //delete dump file
                dumpFile.delete();
            }
        } else {
            Log.d(_cly.TAG, "[ModuleCrash] Native crash folder does not exist");
        }
    }

    private synchronized void recordNativeException(File dumpFile){
        Log.d(_cly.TAG, "[ModuleCrash] Recording native crash dump: [" + dumpFile.getName() + "]");

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
            Log.e(_cly.TAG, "[ModuleCrash] Failed to read dump file bytes");
            e.printStackTrace();
            return;
        }

        //convert to base64
        String dumpString = Base64.encodeToString(bytes, Base64.NO_WRAP);

        //record crash
        _cly.connectionQueue_.sendCrashReport(dumpString, false, true);
    }

    /**
     * A way to validate created filters
     * @param regexFilters filters you want to validate
     * @param sampleCrash sample crashes you are worrying about
     * @return
     */
    boolean[] crashFilterTestInternal(Pattern[] regexFilters, String[] sampleCrash){
        boolean[] res = new boolean[sampleCrash.length];

        for(int a = 0 ; a < res.length ; a++){
            res[a] = crashFilterCheck(regexFilters, sampleCrash[a]);
        }

        return res;
    }

    /**
     * Call to check if crash matches one of the filters
     * If it does, the crash should be ignored
     * @param regexFilters
     * @param crash
     * @return true if a match was found
     */
    boolean crashFilterCheck(Pattern[] regexFilters, String crash){
        if (_cly.isLoggingEnabled()) {
            int filterCount = 0;
            if(regexFilters != null){
                filterCount = regexFilters.length;
            }
            Log.d(Countly.TAG, "[ModuleCrash] Calling crashFilterCheck, filter count:[" + filterCount + "]");
        }

        if(regexFilters == null){
            //no filter set, nothing to compare against
            return false;
        }

        for (Pattern regexFilter : regexFilters) {
            Matcher m = regexFilter.matcher(crash);
            if (m.matches()) {
                return true;
            }
        }
        return false;
    }

    void setCrashFiltersInternal(Pattern[] regexFilters){
        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleCrash] Calling setCrashFiltersInternal");

            if(regexFilters != null){
                Log.d(Countly.TAG, "[ModuleCrash] Setting the following crash regex filters:");
                for (int a = 0; a < regexFilters.length; a++) {
                    Log.d(Countly.TAG, (a + 1) + ") [" + regexFilters[a].toString() + "]");
                }
            }
        }

        crashRegexFilters = regexFilters;
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
    synchronized Countly recordException(Throwable exception, boolean itIsHandled) {
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

        if(crashFilterCheck(crashRegexFilters, exceptionString)){
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

            Object[] o = null;
            //noinspection InfiniteLoopStatement
            while (true) { o = new Object[] { o }; }


        }else if (crashNumber == 4){

            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "Running crashTest 4");
            }

            throw new RuntimeException("This is a crash");
        }
        else{
            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "Running crashTest 5");
            }

            String test = null;
            //noinspection ResultOfMethodCallIgnored
            test.charAt(1);
        }
        return Countly.sharedInstance();
    }

    @Override
    void halt(){
        crashRegexFilters = null;
    }

    public class Crashes {
        /**
         * Call to set regex filters that will be used for crash filtering
         * Set null to disable it
         */
        public Countly setCrashFilters(Pattern[] regexFilters){
            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[Crashes] Calling setCrashFilters");
            }

            if (!_cly.isInitialized()) {
                throw new IllegalStateException("Countly.sharedInstance().init must be called before setCrashFilters");
            }

            setCrashFiltersInternal(regexFilters);

            return _cly;
        }

        /**
         * A way to validate created filters
         * @param regexFilters filters you want to validate
         * @param sampleCrash sample crashes you are worrying about
         * @return
         */
        public boolean[] crashFilterTest(Pattern[] regexFilters, String[] sampleCrash){
            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[Crashes] Calling crashFilterTest");
            }

            return crashFilterTestInternal(regexFilters, sampleCrash);
        }

        /**
         * Sets custom segments to be reported with crash reports
         * In custom segments you can provide any string key values to segments crashes by
         * @param segments Map&lt;String, String&gt; key segments and their values
         * @return Returns link to Countly for call chaining
         */
        public synchronized Countly setCustomCrashSegments(Map<String, String> segments) {
            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[Crashes] Setting custom crash segments");
            }

            if(!_cly.getConsent(Countly.CountlyFeatureNames.crashes)){
                return _cly;
            }

            if(segments != null) {
                CrashDetails.setCustomSegments(segments);
            }

            return _cly;
        }

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
            return recordException(exception, true);
        }

        /**
         * Log handled exception to report it to server as non fatal crash
         * @param exception Throwable to log
         * @return Returns link to Countly for call chaining
         */
        public synchronized Countly recordHandledException(Throwable exception) {
            return recordException(exception, true);
        }

        /**
         * Log unhandled exception to report it to server as fatal crash
         * @param exception Exception to log
         * @return Returns link to Countly for call chaining
         */
        public synchronized Countly recordUnhandledException(Exception exception) {
            return recordException(exception, false);
        }

        /**
         * Log unhandled exception to report it to server as fatal crash
         * @param exception Throwable to log
         * @return Returns link to Countly for call chaining
         */
        public synchronized Countly recordUnhandledException(Throwable exception) {
            return recordException(exception, false);
        }
    }
}
