package ly.count.android.sdk;

public class ConfigExperimental {
    protected boolean viewNameRecordingEnabled = false;
    protected boolean visibilityTrackingEnabled = false;

    /**
     * This will enabled view name recording for previous view name for views
     * and current view name for events.
     *
     * @return ConfigExperimental instance for chaining
     * @apiNote This is an EXPERIMENTAL feature, and it can have breaking changes
     */
    public ConfigExperimental enablePreviousNameRecording() {
        viewNameRecordingEnabled = true;
        return this;
    }

    /**
     * This will enable visibility tracking for events and views.
     * This will track at the view or event creation time, if the app is in the foreground or background.
     *
     * @return ConfigExperimental instance for chaining
     * @apiNote This is an EXPERIMENTAL feature, and it can have breaking changes
     */
    public ConfigExperimental enableVisibilityTracking() {
        visibilityTrackingEnabled = true;
        return this;
    }
}
