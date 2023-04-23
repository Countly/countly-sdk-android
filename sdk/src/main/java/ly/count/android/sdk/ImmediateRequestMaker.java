package ly.count.android.sdk;

import android.os.AsyncTask;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import org.json.JSONObject;

/**
 * Async task for making immediate server requests
 */
class ImmediateRequestMaker extends AsyncTask<Object, Void, JSONObject> implements ImmediateRequestI {

    /**
     * Used for callback from async task
     */
    protected interface InternalImmediateRequestCallback {
        void callback(JSONObject checkResponse);
    }

    InternalImmediateRequestCallback callback;
    ModuleLog L;

    @Override
    public void doWork(String requestData, String customEndpoint, ConnectionProcessor cp, boolean requestShouldBeDelayed, boolean networkingIsEnabled, InternalImmediateRequestCallback callback, ModuleLog log) {
        this.execute(requestData, customEndpoint, cp, requestShouldBeDelayed, networkingIsEnabled, callback, log);
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
    protected JSONObject doInBackground(Object... params) {
        final String requestData = (String) params[0];
        final String customEndpoint = (String) params[1];
        final ConnectionProcessor cp = (ConnectionProcessor) params[2];
        final boolean requestShouldBeDelayed = (boolean) params[3];
        final boolean networkingIsEnabled = (boolean) params[4];
        callback = (InternalImmediateRequestCallback) params[5];
        L = (ModuleLog) params[6];

        if (!networkingIsEnabled) {
            L.w("[ImmediateRequestMaker] ImmediateRequestMaker, Networking config is disabled, request cancelled. Endpoint[" + customEndpoint + "] request[" + requestData + "]");

            return null;
        }

        L.v("[ImmediateRequestMaker] Starting request");

        HttpURLConnection connection = null;
        BufferedReader reader = null;
        boolean wasSuccess = true;

        try {
            L.d("[ImmediateRequestMaker] delayed[" + requestShouldBeDelayed + "] hasCallback[" + (callback != null) + "] endpoint[" + customEndpoint + "] request[" + requestData + "] url[" + cp.getServerURL() + "]");

            if (requestShouldBeDelayed) {
                //used in cases after something has to be done after a device id change
                L.v("[ImmediateRequestMaker] request should be delayed, waiting for 0.5 seconds");

                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    L.w("[ImmediateRequestMaker] While waiting for 0.5 seconds in ImmediateRequestMaker, sleep was interrupted");
                }
            }

            //getting connection ready
            try {
                connection = (HttpURLConnection) cp.urlConnectionForServerRequest(requestData, customEndpoint);
            } catch (IOException e) {
                L.e("[ImmediateRequestMaker] IOException while preparing remote config update request :[" + e.toString() + "]");

                return null;
            }

            //connecting
            connection.connect();

            InputStream stream;

            //todo check response code
            try {
                //assume there will be no error
                stream = connection.getInputStream();
            } catch (Exception ex) {
                //in case of exception, assume there was a error in the request
                //and change streams
                stream = connection.getErrorStream();
                wasSuccess = false;
            }

            if (stream == null) {
                L.e("[ImmediateRequestMaker] Encountered problem while making a immediate server request, received stream was null");
                return null;
            }

            //getting result
            reader = new BufferedReader(new InputStreamReader(stream));

            StringBuilder buffer = new StringBuilder();
            String line = "";

            while ((line = reader.readLine()) != null) {
                buffer.append(line).append("\n");
            }

            final String receivedBuffer = buffer.toString();

            if (wasSuccess) {
                L.d("[ImmediateRequestMaker] Received the following response, :[" + receivedBuffer + "]");
                return new JSONObject(receivedBuffer);
            } else {
                L.e("[ImmediateRequestMaker] Encountered problem while making a immediate server request, :[" + receivedBuffer + "]");
                return null;
            }
        } catch (Exception e) {
            L.e("[ImmediateRequestMaker] Received exception while making a immediate server request", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                L.e("[ImmediateRequestMaker] ", e);
            }
        }
        L.v("[ImmediateRequestMaker] Finished request");
        return null;
    }

    @Override
    protected void onPostExecute(JSONObject result) {
        super.onPostExecute(result);
        L.v("[ImmediateRequestMaker] onPostExecute");

        if (callback != null) {
            callback.callback(result);
        }
    }
}
