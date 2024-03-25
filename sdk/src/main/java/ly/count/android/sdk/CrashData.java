package ly.count.android.sdk;

import androidx.annotation.NonNull;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/**
 * This class is used to store crash data.
 */
public class CrashData {
    private @NonNull String stackTrace;
    private @NonNull Map<String, Object> crashSegmentation;
    private @NonNull List<String> breadcrumbs;
    private boolean fatal;
    private @NonNull JSONObject crashMetrics;
    private final String[] checksums = new String[5];

    public CrashData(@NonNull String stackTrace, @NonNull Map<String, Object> crashSegmentation, @NonNull List<String> breadcrumbs, @NonNull JSONObject crashMetrics, boolean fatal) {
        this.stackTrace = stackTrace;
        this.crashSegmentation = crashSegmentation;
        this.breadcrumbs = breadcrumbs;
        this.crashMetrics = crashMetrics;
        this.fatal = fatal;

        calculateChecksums(checksums);
    }

    /**
     * Get the stack trace of the crash.
     *
     * @return the stack trace of the crash.
     */
    public @NonNull String getStackTrace() {
        return stackTrace;
    }

    /**
     * Set the stack trace of the crash.
     *
     * @param stackTrace the stack trace of the crash.
     */
    public void setStackTrace(@NonNull String stackTrace) {
        if (stackTrace != null) {
            this.stackTrace = stackTrace;
        }
    }

    /**
     * Get metrics of a crash
     *
     * @return crash as a JSONObject instance
     */
    public @NonNull JSONObject getCrashMetrics() {
        return crashMetrics;
    }

    /**
     * Set metrics of a crash
     *
     * @param crashMetrics of a crash
     */
    public void setCrashMetrics(@NonNull JSONObject crashMetrics) {
        if (crashMetrics != null) {
            this.crashMetrics = crashMetrics;
        }
    }

    /**
     * Get whether crash is fatal
     *
     * @return fatal info of a crash
     */
    public boolean getFatal() {
        return fatal;
    }

    /**
     * Set whether crash is fatal
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
    protected @NonNull String getBreadcrumbsAsString() {
        if (breadcrumbs == null) {
            return "";
        }
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
    public @NonNull Map<String, Object> getCrashSegmentation() {
        return crashSegmentation;
    }

    /**
     * Set the segmentation of the crash.
     *
     * @param crashSegmentation the segmentation of the crash.
     */
    public void setCrashSegmentation(@NonNull Map<String, Object> crashSegmentation) {
        if (crashSegmentation != null) {
            this.crashSegmentation = crashSegmentation;
        }
    }

    /**
     * Get the breadcrumbs of the crash.
     *
     * @return the breadcrumbs of the crash.
     */
    public @NonNull List<String> getBreadcrumbs() {
        return breadcrumbs;
    }

    /**
     * Set the breadcrumbs of the crash.
     *
     * @param breadcrumbs the breadcrumbs of the crash.
     */
    public void setBreadcrumbs(@NonNull List<String> breadcrumbs) {
        if (breadcrumbs == null) {
            return;
        }

        this.breadcrumbs = breadcrumbs;
    }

    /**
     * Get the changed information of crash data in order of:
     * <pre>
     * 0 - stackTrace
     * 1 - crashSegmentation
     * 2 - breadcrumbs
     * 3 - crashMetrics
     * 4 - fatal
     * </pre>
     *
     * @return the checksums of the crash data.
     */
    protected @NonNull boolean[] getChangedFields() {
        boolean[] changedFields = new boolean[5];
        String[] checksumsNew = new String[5];
        calculateChecksums(checksumsNew);

        changedFields[0] = !checksums[0].equals(checksumsNew[0]);
        changedFields[1] = !checksums[1].equals(checksumsNew[1]);
        changedFields[2] = !checksums[2].equals(checksumsNew[2]);
        changedFields[3] = !checksums[3].equals(checksumsNew[3]);
        changedFields[4] = !checksums[4].equals(checksumsNew[4]);

        return changedFields;
    }

    protected String getChangedFieldsAsString() {
        boolean[] changedFields = getChangedFields();
        StringBuilder changedFieldsString = new StringBuilder();

        for (boolean changedField : changedFields) {
            changedFieldsString.append(changedField ? "1" : "0");
        }

        return changedFieldsString.toString();
    }

    private void calculateChecksums(@NonNull String[] checksums) {
        checksums[0] = UtilsNetworking.sha256Hash(stackTrace);
        checksums[1] = UtilsNetworking.sha256Hash(crashSegmentation != null ? crashSegmentation.toString() : null);
        checksums[2] = UtilsNetworking.sha256Hash(breadcrumbs != null ? breadcrumbs.toString() : null);
        checksums[3] = UtilsNetworking.sha256Hash(crashMetrics != null ? crashMetrics.toString() : null);
        checksums[4] = UtilsNetworking.sha256Hash(fatal + "");
    }
}
