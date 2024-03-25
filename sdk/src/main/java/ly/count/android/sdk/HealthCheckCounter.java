package ly.count.android.sdk;

import androidx.annotation.NonNull;
import java.io.UnsupportedEncodingException;
import org.json.JSONException;
import org.json.JSONObject;

public class HealthCheckCounter implements HealthTracker {
    public long countLogWarning = 0;
    public long countLogError = 0;
    public int statusCode = -1;
    public String errorMessage = "";

    final String keyLogError = "LErr";
    final String keyLogWarning = "LWar";
    final String keyStatusCode = "RStatC";
    final String keyErrorMessage = "REMsg";

    final String requestKeyErrorCount = "el";
    final String requestKeyWarningCount = "wl";
    final String requestKeyStatusCode = "sc";
    final String requestKeyRequestError = "em";

    StorageProvider storageProvider;
    ModuleLog L;

    public HealthCheckCounter(@NonNull StorageProvider sp, @NonNull ModuleLog L) {
        assert sp != null;
        assert L != null;

        this.L = L;
        storageProvider = sp;
        setupInitialCounters(storageProvider.getHealthCheckCounterState());
    }

    void setupInitialCounters(@NonNull String initialState) {
        assert initialState != null;

        if (initialState == null || initialState.isEmpty()) {
            return;
        }

        try {
            JSONObject jsonObject = new JSONObject(initialState);

            countLogWarning = jsonObject.optLong(keyLogWarning, 0);
            countLogError = jsonObject.optLong(keyLogError, 0);
            statusCode = jsonObject.optInt(keyStatusCode, -1);
            errorMessage = jsonObject.optString(keyErrorMessage, "");

            L.d("[HealthCheckCounter] Loaded initial health check state: [" + jsonObject.toString() + "]");
        } catch (Exception e) {
            clearAndSave();
            L.w("[HealthCheckCounter] Failed to read initial state, " + e);
        }
    }

    @Override public void logWarning() {
        countLogWarning++;
    }

    @Override public void logError() {
        countLogError++;
    }

    @Override public void logFailedNetworkRequest(int statusCode, @NonNull String errorResponse) {
        assert statusCode > 0;
        assert statusCode < 1000;
        assert errorResponse != null;
        
        this.statusCode = statusCode;

        if (errorResponse.length() > 1000) {
            //cap the error length
            this.errorMessage = errorResponse.substring(0, 1000);
        } else {
            this.errorMessage = errorResponse;
        }
    }

    @Override public void logSessionStartedWhileRunning() {

    }

    @Override public void logSessionEndedWhileNotRunning() {

    }

    @Override public void logSessionUpdatedWhileNotRunning() {

    }

    @Override public void clearAndSave() {
        clearValues();//clear values
        storageProvider.setHealthCheckCounterState("");//clear stored State
    }

    @Override public void saveState() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put(keyLogWarning, countLogWarning);
            jsonObject.put(keyLogError, countLogError);
            jsonObject.put(keyStatusCode, statusCode);
            jsonObject.put(keyErrorMessage, errorMessage);

            storageProvider.setHealthCheckCounterState(jsonObject.toString());
        } catch (Exception e) {
            L.w("[HealthCheckCounter] Failed to save current state, " + e);
        }
    }

    void clearValues() {
        L.v("[HealthCheckCounter] Clearing counters");
        countLogWarning = 0;
        countLogError = 0;
        statusCode = -1;
        errorMessage = "";
    }

    @NonNull String createRequestParam() {
        StringBuilder sb = new StringBuilder(100);
        sb.append("&hc=");

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put(requestKeyErrorCount, countLogError);

            jsonObject.put(requestKeyWarningCount, countLogWarning);
            jsonObject.put(requestKeyStatusCode, statusCode);
            jsonObject.put(requestKeyRequestError, errorMessage);
        } catch (JSONException e) {
            L.w("[HealthCheckCounter] Failed to create param for hc request, " + e);
        }

        String encodedData = jsonObject.toString();

        try {
            encodedData = java.net.URLEncoder.encode(encodedData, "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            // should never happen because Android guarantees UTF-8 support
            Countly.sharedInstance().L.e("[getMetrics] encode failed, [" + ex + "]");
        }

        sb.append(encodedData);

        return sb.toString();
    }
}
