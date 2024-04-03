package ly.count.android.sdk;

import androidx.annotation.NonNull;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * This class is used to store crash data.
 */
public class CrashData {
    private @NonNull String stackTrace;
    private @NonNull Map<String, Object> crashSegmentation;
    private @NonNull List<String> breadcrumbs;
    private boolean fatal;
    private @NonNull Map<String, Object> crashMetrics;
    /**
     * 0 - stackTrace
     * 1 - crashSegmentation
     * 2 - breadcrumbs
     * 3 - crashMetrics
     * 4 - fatal
     */
    private final String[] checksums = new String[5];
    private final boolean[] changedFields = new boolean[5];

    protected CrashData(@NonNull String stackTrace, @NonNull Map<String, Object> crashSegmentation, @NonNull List<String> breadcrumbs, @NonNull JSONObject crashMetrics, boolean fatal) {
        assert stackTrace != null;
        assert crashSegmentation != null;
        assert breadcrumbs != null;
        assert crashMetrics != null;

        this.stackTrace = stackTrace;
        this.crashSegmentation = crashSegmentation;
        this.breadcrumbs = breadcrumbs;
        this.crashMetrics = convertJSONToMap(crashMetrics);
        this.fatal = fatal;

        calculateChecksums(checksums);
    }

    /**
     * Get the stack trace of the crash.
     *
     * @return the stack trace of the crash.
     */
    public @NonNull String getStackTrace() {
        assert stackTrace != null;
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
    public @NonNull Map<String, Object> getCrashMetrics() {
        assert crashMetrics != null;
        return crashMetrics;
    }

    /**
     * Set metrics of a crash
     *
     * @param crashMetrics of a crash
     */
    public void setCrashMetrics(@NonNull Map<String, Object> crashMetrics) {
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
        assert breadcrumbs != null;

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
        assert crashSegmentation != null;
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
        assert breadcrumbs != null;
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

    protected JSONObject getCrashMetricsJSON() {
        JSONObject crashMetrics = new JSONObject();
        for (Map.Entry<String, Object> entry : this.crashMetrics.entrySet()) {
            try {
                crashMetrics.put(entry.getKey(), entry.getValue());
            } catch (JSONException ignored) {
            }
        }
        return crashMetrics;
    }

    protected Map<String, Object> convertJSONToMap(JSONObject json) {
        Map<String, Object> map = new HashMap<>();
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = json.opt(key);
            if (value != null) {
                map.put(key, value);
            }
        }
        return map;
    }

    /**
     * Trigger checksum recalculation
     *
     * @return the checksums of the crash data.
     */
    protected void calculateChangedFields() {
        assert checksums != null;
        assert checksums.length == 5;

        String[] checksumsNew = new String[5];
        calculateChecksums(checksumsNew);

        changedFields[0] = !checksums[0].equals(checksumsNew[0]);
        changedFields[1] = !checksums[1].equals(checksumsNew[1]);
        changedFields[2] = !checksums[2].equals(checksumsNew[2]);
        changedFields[3] = !checksums[3].equals(checksumsNew[3]);
        changedFields[4] = !checksums[4].equals(checksumsNew[4]);
    }

    protected int getChangedFieldsAsInt() {
        int result = 0;
        for (int i = changedFields.length - 1; i >= 0; i--) {
            if (changedFields[i]) {
                result |= (1 << (changedFields.length - 1 - i));
            }
        }
        return result;
    }

    private void calculateChecksums(@NonNull String[] checksumArrayToSet) {
        assert checksumArrayToSet != null;
        assert checksumArrayToSet.length == 5;
        assert stackTrace != null;
        assert crashSegmentation != null;
        assert breadcrumbs != null;
        assert crashMetrics != null;

        checksumArrayToSet[0] = UtilsNetworking.sha256Hash(stackTrace);
        checksumArrayToSet[1] = UtilsNetworking.sha256Hash(crashSegmentation.toString());
        checksumArrayToSet[2] = UtilsNetworking.sha256Hash(breadcrumbs.toString());
        checksumArrayToSet[3] = UtilsNetworking.sha256Hash(crashMetrics.toString());
        checksumArrayToSet[4] = UtilsNetworking.sha256Hash(fatal + "");
    }
}
