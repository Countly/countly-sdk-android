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
     * 0 - urlConnection
     * 1 - requestShouldBeDelayed
     * 2 - callback         *
     */
    protected JSONObject doInBackground(Object... params) {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.v(Countly.TAG, "Starting ImmediateRequestMaker request");
        }
        callback = (InternalFeedbackRatingCallback) params[2];
        boolean requestShouldBeDelayed = (boolean) params[1];

        HttpURLConnection connection = null;
        BufferedReader reader = null;
        boolean wasSuccess = true;

        try {
            if (requestShouldBeDelayed) {
                //used in cases after something has to be done after a device id change
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.v(Countly.TAG, "ImmediateRequestMaker request should be delayed, waiting for 0.5 seconds");
                }

                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    if (Countly.sharedInstance().isLoggingEnabled()) {
                        Log.w(Countly.TAG, "While waiting for 0.5 seconds in ImmediateRequestMaker, sleep was interrupted");
                    }
                }
            }

            connection = (HttpURLConnection) params[0];
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
                    Log.e(Countly.TAG, "Encountered problem while making a immediate server request, received stream was null");
                }
                return null;
            }

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
                    Log.e(Countly.TAG, "Encountered problem while making a immediate server request, :[" + buffer.toString() + "]");
                }
                return null;
            }
        } catch (Exception e) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.e(Countly.TAG, "Received exception while making a immediate server request");
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
            Log.v(Countly.TAG, "Finished ImmediateRequestMaker request");
        }
        return null;
    }

    @Override
    protected void onPostExecute(JSONObject result) {
        super.onPostExecute(result);
        //Log.d(TAG, "Post exec: [" + result + "]");

        if (callback != null) {
            callback.callback(result);
        }
    }
}
