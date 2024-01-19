package ly.count.android.sdk;

public class ApmConfig {
    protected boolean trackAppStartTime = false;
    protected boolean trackForegroundBackground = false;
    protected boolean manualForegroundBackgroundTrigger = false;
    protected boolean appLoadedManualTrigger = false;
    protected Long appStartTimestampOverride = null;

    //we enable features

    /**
     * Enable the recording of the app start time
     *
     * @param trackAppStartTime set true if you want to enable the recording of the app start time
     * @return Returns the same config object for convenient linking
     */
    public synchronized ApmConfig enableAppStartTimeTracking(boolean trackAppStartTime) {
        this.trackAppStartTime = trackAppStartTime;
        return this;
    }

    /**
     * Enable the recording of the app foreground / background state
     *
     * @param trackForegroundBackground set true if you want to enable the recording of the app foreground / background state
     * @return Returns the same config object for convenient linking
     */
    public synchronized ApmConfig enableForegroundBackgroundTracking(boolean trackForegroundBackground) {
        this.trackForegroundBackground = trackForegroundBackground;
        return this;
    }

    //we configure features / set manual overrides

    /**
     * Set to manually trigger the moment when the app has finished loading
     *
     * @return Returns the same config object for convenient linking
     */
    public synchronized ApmConfig enableManualAppLoadedTrigger() {
        appLoadedManualTrigger = true;
        return this;
    }

    /**
     * Override the app start timestamp in case you have a more precise way to measure it
     *
     * @param appStartTimestampOverride The timestamp to use as the app start timestamp
     * @return Returns the same config object for convenient linking
     */
    public synchronized ApmConfig setAppStartTimestampOverride(long appStartTimestampOverride) {
        this.appStartTimestampOverride = appStartTimestampOverride;
        return this;
    }
}
