package ly.count.android.sdk;

import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/**
 * This class is used to store crash data.
 */
public class CrashData {

    protected boolean crashChanged = false;
    protected boolean breadcrumbsAdded = false;
    private String stackTrace;
    private Map<String, Object> crashSegmentation;
    private List<String> breadcrumbs;
    private Boolean fatal;
    private JSONObject crashMetrics;

    public CrashData(String stackTrace, Map<String, Object> crashSegmentation, List<String> breadcrumbs, JSONObject crashMetrics, Boolean fatal) {
        this.stackTrace = stackTrace;
        this.crashSegmentation = crashSegmentation;
        this.breadcrumbs = breadcrumbs;
        this.crashMetrics = crashMetrics;
        this.fatal = fatal;
    }

    /**
     * Get the stack trace of the crash.
     *
     * @return the stack trace of the crash.
     */
    public String getStackTrace() {
        return stackTrace;
    }

    /**
     * Set the stack trace of the crash.
     *
     * @param stackTrace the stack trace of the crash.
     */
    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
        crashChanged = true;
    }

    /**
     * Get metrics of a crash
     *
     * @return crash as a JSONObject instance
     */
    public JSONObject getCrashMetrics() {
        return crashMetrics;
    }

    /**
     * Set metrics of a crash
     *
     * @param crashMetrics of a crash
     */
    public void setCrashMetrics(JSONObject crashMetrics) {
        this.crashMetrics = crashMetrics;
    }

    /**
     * Get whether or not crash is fatal
     *
     * @return fatal info of a crash
     */
    public Boolean getFatal() {
        return fatal;
    }

    /**
     * Set whether or not crash is fatal
     *
     * @param fatal info
     */
    public void setFatal(boolean fatal) {
        this.fatal = fatal;
    }

    /**
     * Get the breadcrumbs of the crash.
     *
     * @return the breadcrumbs of the crash.
     */
    public String getBreadcrumbsAsString() {
        StringBuilder breadcrumbsString = new StringBuilder();

        for (String breadcrumb : breadcrumbs) {
            breadcrumbsString.append(breadcrumb).append("\n");
        }

        return breadcrumbsString.toString();
    }

    /**
     * Get the segmentation of the crash.
     *
     * @return the segmentation of the crash.
     */
    public Map<String, Object> getCrashSegmentation() {
        return crashSegmentation;
    }

    /**
     * Set the segmentation of the crash.
     *
     * @param crashSegmentation the segmentation of the crash.
     */
    public void setCrashSegmentation(Map<String, Object> crashSegmentation) {
        this.crashSegmentation = crashSegmentation;
        crashChanged = true;
    }

    /**
     * Get the breadcrumbs of the crash.
     *
     * @return the breadcrumbs of the crash.
     */
    public List<String> getBreadcrumbs() {
        return breadcrumbs;
    }

    /**
     * Set the breadcrumbs of the crash.
     *
     * @param breadcrumbs the breadcrumbs of the crash.
     */
    public void setBreadcrumbs(List<String> breadcrumbs) {
        if (this.breadcrumbs.size() < breadcrumbs.size()) {
            breadcrumbsAdded = true;
        }
        this.breadcrumbs = breadcrumbs;
        crashChanged = true;
    }
}
