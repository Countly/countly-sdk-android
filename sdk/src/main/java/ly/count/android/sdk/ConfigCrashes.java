package ly.count.android.sdk;

import java.util.Map;

public class ConfigCrashes {

    protected GlobalCrashFilterCallback globalCrashFilterCallback = null;
    protected boolean checkForNativeCrashDumps = true;
    protected Map<String,Object> customCrashSegment = null;
    protected boolean recordAllThreadsWithCrash = false;
    protected boolean enableUnhandledCrashReporting = false;
    protected Integer maxBreadcrumbCount = null;
    Integer maxStackTraceLinesPerThread;
    Integer maxStackTraceLineLength;
    int maxStackTraceThreadCount = 30;

    /**
     * @param callback the callback that will be called for each crash, allowing you to filter it
     * @return Returns the same config object for convenient linking
     */
    public synchronized ConfigCrashes setGlobalCrashFilterCallback(GlobalCrashFilterCallback callback) {
        globalCrashFilterCallback = callback;
        return this;
    }

    /**
     * For use during testing
     *
     * @return Returns the same config object for convenient linking
     */
    protected synchronized ConfigCrashes checkForNativeCrashDumps(boolean checkForDumps) {
        checkForNativeCrashDumps = checkForDumps;
        return this;
    }

    /**
     * Set custom crash segmentation which will be added to all recorded crashes
     *
     * @param crashSegment segmentation information. Accepted values are "Integer", "String", "Double", "Boolean"
     * @return Returns the same config object for convenient linking
     */
    public synchronized ConfigCrashes setCustomCrashSegmentation(Map<String, Object> crashSegment) {
        customCrashSegment = crashSegment;
        return this;
    }

    /**
     * @return Returns the same config object for convenient linking
     */
    public synchronized ConfigCrashes enableRecordAllThreadsWithCrash() {
        recordAllThreadsWithCrash = true;
        return this;
    }

    /**
     * Call to enable uncaught crash reporting
     *
     * @return Returns the same config object for convenient linking
     */
    public synchronized ConfigCrashes enableCrashReporting() {
        this.enableUnhandledCrashReporting = true;
        return this;
    }

    /**
     * Set the maximum amount of breadcrumbs that can be recorded.
     * After exceeding the limit, the oldest values will be removed.
     *
     * @param maxBreadcrumbCount
     * @return Returns the same config object for convenient linking
     */
    public synchronized ConfigCrashes setMaxBreadcrumbCount(int maxBreadcrumbCount) {
        this.maxBreadcrumbCount = maxBreadcrumbCount;
        return this;
    }
}
