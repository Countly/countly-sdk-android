package ly.count.android.sdk;

public class ConfigExperimental {
    protected boolean viewNameRecordingEnabled = false;
    protected boolean visibilityTrackingEnabled = false;

    public ConfigExperimental enableViewNameRecording() {
        viewNameRecordingEnabled = true;
        return this;
    }

    public ConfigExperimental enableVisibilityTracking() {
        visibilityTrackingEnabled = true;
        return this;
    }
}
