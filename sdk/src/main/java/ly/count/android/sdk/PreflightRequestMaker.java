package ly.count.android.sdk;

import android.os.AsyncTask;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.IOException;
import java.net.HttpURLConnection;
import org.json.JSONObject;

class PreflightRequestMaker extends AsyncTask<Object, Void, Boolean> implements ImmediateRequestI {

    ImmediateRequestMaker.InternalImmediateRequestCallback callback;
    ModuleLog L;

    @Override
    public void doWork(@NonNull String requestData, @Nullable String customEndpoint, @NonNull ConnectionProcessor cp, boolean requestShouldBeDelayed, boolean networkingIsEnabled, @NonNull ImmediateRequestMaker.InternalImmediateRequestCallback callback, @NonNull ModuleLog log) {
        assert Utils.isNotNullOrEmpty(requestData);
        assert cp != null;
        assert log != null;
        assert callback != null;
        if (Countly.sharedInstance().useSerialExecutorInternal) {
            log.d("[PreflightRequestMaker] Using serial executor");
            this.execute(requestData, customEndpoint, cp, requestShouldBeDelayed, networkingIsEnabled, callback, log);
        } else {
            log.d("[PreflightRequestMaker] Using parallel executor");
            this.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, requestData, customEndpoint, cp, requestShouldBeDelayed, networkingIsEnabled, callback, log);
        }
    }

    /**
     * params fields:
     * 0 - requestData
     * 1 - custom endpoint
     * 2 - connection processor
     * 3 - requestShouldBeDelayed
     * 4 - networkingIsEnabled
     * 5 - callback
     * 6 - log module
     */
    protected Boolean doInBackground(Object... params) {
        final String urlRequest = (String) params[0];
        final ConnectionProcessor cp = (ConnectionProcessor) params[2];
        callback = (ImmediateRequestMaker.InternalImmediateRequestCallback) params[5];
        L = (ModuleLog) params[6];

        L.v("[PreflightRequestMaker] doPreflightRequest, Starting preflight request");

        HttpURLConnection connection = null;

        try {
            //getting connection ready
            try {
                connection = (HttpURLConnection) cp.urlConnectionForPreflightRequest(urlRequest);
            } catch (IOException e) {
                L.e("[PreflightRequestMaker] doPreflightRequest, IOException while preparing preflight request :[" + e + "]");
                return null;
            }

            int responseCode = connection.getResponseCode();

            L.v("[PreflightRequestMaker] doPreflightRequest, Preflight request finished, response code: " + responseCode);
            return responseCode >= 200 && responseCode < 400;
        } catch (Exception e) {
            L.e("[PreflightRequestMaker] doPreflightRequest, Received exception while making a immediate server request", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        L.v("[PreflightRequestMaker] doPreflightRequest, Finished request");
        return false;
    }

    @Override
    protected void onPostExecute(Boolean result) {
        super.onPostExecute(result);
        L.v("[PreflightRequestMaker] onPostExecute");

        if (callback != null) {
            callback.callback(result ? new JSONObject() : null);
        }
    }
}
