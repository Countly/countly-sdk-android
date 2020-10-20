package ly.count.android.sdk;

import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;

/**
 * Async task for making immediate server requests
 */
class ImmediateRequestMaker extends AsyncTask<Object, Void, JSONObject> {

    /**
     * Used for callback from async task
     */
    protected static interface InternalFeedbackRatingCallback {
        void callback(JSONObject checkResponse);
    }

    InternalFeedbackRatingCallback callback;

    /**
     * params fields:
     * 0 - requestData
     * 1 - custom endpoint
     * 2 - connection processor
     * 3 - requestShouldBeDelayed
     * 4 - callback
     */
    protected JSONObject doInBackground(Object... params) {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.v(Countly.TAG, "[ImmediateRequestMaker] Starting request");
        }
        final String requestData = (String) params[0];
        final String customEndpoint = (String) params[1];
        final ConnectionProcessor cp = (ConnectionProcessor) params[2];
        final boolean requestShouldBeDelayed = (boolean) params[3];
        callback = (InternalFeedbackRatingCallback) params[4];


        HttpURLConnection connection = null;
        BufferedReader reader = null;
        boolean wasSuccess = true;

        try {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.d(Countly.TAG, "[ImmediateRequestMaker] delayed[" + requestShouldBeDelayed + "] hasCallback[" + (callback != null) + "] endpoint[" + customEndpoint + "] request[" + requestData + "]");
            }

            if (requestShouldBeDelayed) {
                //used in cases after something has to be done after a device id change
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.v(Countly.TAG, "[ImmediateRequestMaker] request should be delayed, waiting for 0.5 seconds");
                }

                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    if (Countly.sharedInstance().isLoggingEnabled()) {
                        Log.w(Countly.TAG, "[ImmediateRequestMaker] While waiting for 0.5 seconds in ImmediateRequestMaker, sleep was interrupted");
                    }
                }
            }

            //getting connection ready
            try {
                connection = (HttpURLConnection) cp.urlConnectionForServerRequest(requestData, customEndpoint);
            } catch (IOException e) {
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.e(Countly.TAG, "[ImmediateRequestMaker] IOException while preparing remote config update request :[" + e.toString() + "]");
                }

                return null;
            }

            //connecting
            connection.connect();

            InputStream stream;

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
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.e(Countly.TAG, "[ImmediateRequestMaker] Encountered problem while making a immediate server request, received stream was null");
                }
                return null;
            }

            //getting result
            reader = new BufferedReader(new InputStreamReader(stream));

            StringBuilder buffer = new StringBuilder();
            String line = "";

            while ((line = reader.readLine()) != null) {
                buffer.append(line).append("\n");
            }

            if (wasSuccess) {
                return new JSONObject(buffer.toString());
            } else {
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.e(Countly.TAG, "[ImmediateRequestMaker] Encountered problem while making a immediate server request, :[" + buffer.toString() + "]");
                }
                return null;
            }
        } catch (Exception e) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.e(Countly.TAG, "[ImmediateRequestMaker] Received exception while making a immediate server request");
                e.printStackTrace();
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    e.printStackTrace();
                }
            }
        }
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.v(Countly.TAG, "[ImmediateRequestMaker] Finished request");
        }
        return null;
    }

    @Override
    protected void onPostExecute(JSONObject result) {
        super.onPostExecute(result);
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.v(Countly.TAG, "[ImmediateRequestMaker] onPostExecute");
        }

        if (callback != null) {
            callback.callback(result);
        }
    }
}
