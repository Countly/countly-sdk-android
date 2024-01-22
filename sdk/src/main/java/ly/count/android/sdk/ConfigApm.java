package ly.count.android.sdk;

public class ConfigApm {
    /**
     * Whether to track the app start time
     */
    protected boolean trackAppStartTime = false;
    /**
     * Whether to track the app foreground / background state
     */
    protected boolean trackForegroundBackground = false;
    /**
     * Whether to track the app foreground / background state manually
     */
    protected boolean manualForegroundBackgroundTrigger = false;
    /**
     * Whether to track the app start time manually
     */
    protected boolean appLoadedManualTrigger = false;
    /**
     * Whether to track the app start time manually
     */
    protected Long appStartTimestampOverride = null;

    //we enable features

    /**
     * Enable the recording of the app start time
     *
     * @return Returns the same config object for convenient linking
     */
    public synchronized ConfigApm enableAppStartTimeTracking() {
        this.trackAppStartTime = true;
        return this;
    }

    /**
     * Enable the recording of the app foreground / background state
     *
     * @return Returns the same config object for convenient linking
     */
    public synchronized ConfigApm enableForegroundBackgroundTracking() {
        this.trackForegroundBackground = true;
        return this;
    }

    //we configure features / set manual overrides

    /**
     * Set to manually trigger the moment when the app has finished loading
     *
     * @return Returns the same config object for convenient linking
     */
    public synchronized ConfigApm enableManualAppLoadedTrigger() {
        appLoadedManualTrigger = true;
        return this;
    }

    /**
     * Override the app start timestamp in case you have a more precise way to measure it
     *
     * @param appStartTimestampOverride The timestamp to use as the app start timestamp
     * @return Returns the same config object for convenient linking
     */
    public synchronized ConfigApm setAppStartTimestampOverride(long appStartTimestampOverride) {
        this.appStartTimestampOverride = appStartTimestampOverride;
        return this;
    }
}
