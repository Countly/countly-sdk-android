package ly.count.android.sdk;

import java.util.Map;

/**
 * This class is used to store crash data.
 */
public class CrashData {

    private String stackTrace;
    private Map<String,Object> crashSegmentation;
    private String breadcrumbs;
    protected boolean crashChanged = false;

    public CrashData(String stackTrace, Map<String,Object> crashSegmentation, String breadcrumbs) {
        this.stackTrace = stackTrace;
        this.crashSegmentation = crashSegmentation;
        this.breadcrumbs = breadcrumbs;
    }

    /**
     * Get the stack trace of the crash.
     * @return the stack trace of the crash.
     */
    public String getStackTrace() {
        return stackTrace;
    }

    /**
     * Get the segmentation of the crash.
     * @return the segmentation of the crash.
     */
    public Map<String,Object> getCrashSegmentation() {
        return crashSegmentation;
    }

    /**
     * Get the breadcrumbs of the crash.
     * @return the breadcrumbs of the crash.
     */
    public String getBreadcrumbs() {
        return breadcrumbs;
    }

    /**
     * Set the stack trace of the crash.
     * @param stackTrace the stack trace of the crash.
     */
    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
        crashChanged = true;
    }

    /**
     * Set the segmentation of the crash.
     * @param crashSegmentation the segmentation of the crash.
     */
    public void setCrashSegmentation(Map<String,Object> crashSegmentation) {
        this.crashSegmentation = crashSegmentation;
        crashChanged = true;

    }

    /**
     * Set the breadcrumbs of the crash.
     * @param breadcrumbs the breadcrumbs of the crash.
     */
    public void setBreadcrumbs(String breadcrumbs) {
        this.breadcrumbs = breadcrumbs;
        crashChanged = true;
    }
}
