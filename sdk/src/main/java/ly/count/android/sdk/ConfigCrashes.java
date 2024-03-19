package ly.count.android.sdk;

import java.util.Map;

public class ConfigCrashes {

    protected boolean checkForNativeCrashDumps = true;
    protected Map<String, Object> customCrashSegment = null;
    protected boolean recordAllThreadsWithCrash = false;
    protected boolean enableUnhandledCrashReporting = false;

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
}
