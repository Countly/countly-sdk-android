package ly.count.android.sdk;

import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/**
 * This class is used to store crash data.
 */
public class CrashData {
    protected byte crashBits = 0b00000;
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
        if (!this.stackTrace.equals(stackTrace)) {
            this.stackTrace = stackTrace;
            setChanged(CrashDataProps.STACK_TRACE.value);
        }
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
        if (!this.crashMetrics.equals(crashMetrics)) {
            this.crashMetrics = crashMetrics;
            setChanged(CrashDataProps.METRICS.value);
        }
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
        if (fatal != this.fatal) {
            this.fatal = fatal;
            setChanged(CrashDataProps.FATAL.value);
        }
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
        if (!this.crashSegmentation.equals(crashSegmentation)) {
            this.crashSegmentation = crashSegmentation;
            setChanged(CrashDataProps.SEGMENTATION.value);
        }
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
        if (!this.breadcrumbs.equals(breadcrumbs)) {
            this.breadcrumbs = breadcrumbs;
            setChanged(CrashDataProps.BREADCRUMBS.value);
        }
    }

    private void setChanged(byte value) {
        if ((crashBits & value) != value) {
            crashBits = (byte) (crashBits | value);
        }
    }

    private enum CrashDataProps {
        STACK_TRACE((byte) 0b00001), SEGMENTATION((byte) 0b00010), BREADCRUMBS((byte) 0b00100), FATAL((byte) 0b01000), METRICS((byte) 0b10000);

        final byte value;

        CrashDataProps(byte value) {
            this.value = value;
        }
    }
}
