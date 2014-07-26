/*
Copyright (c) 2012, 2013, 2014 Countly

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/
package ly.count.android.api;

import android.os.Build;
import android.util.Log;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

/**
 * ConnectionProcessor is a Runnable that is executed on a background
 * thread to submit session & event data to a Count.ly server.
 *
 * NOTE: This class is only public to facilitate unit testing, because
 *       of this bug in dexmaker: https://code.google.com/p/dexmaker/issues/detail?id=34
 */
public class ConnectionProcessor implements Runnable {
    private static final int CONNECT_TIMEOUT_IN_MILLISECONDS = 30000;
    private static final int READ_TIMEOUT_IN_MILLISECONDS = 30000;

    private final CountlyStore store_;
    private final String serverURL_;

    ConnectionProcessor(final String serverURL, final CountlyStore store) {
        serverURL_ = serverURL;
        store_ = store;

        // HTTP connection reuse which was buggy pre-froyo
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.FROYO) {
            System.setProperty("http.keepAlive", "false");
        }
    }

    URLConnection urlConnectionForEventData(final String eventData) throws IOException {
        final String urlStr = serverURL_ + "/i?" + eventData;
        final URL url = new URL(urlStr);
        final URLConnection conn = url.openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_IN_MILLISECONDS);
        conn.setReadTimeout(READ_TIMEOUT_IN_MILLISECONDS);
        conn.setUseCaches(false);
        conn.setDoInput(true);
        conn.setDoOutput(false);
        return conn;
    }

    @Override
    public void run() {
        while (true) {
            final String[] storedEvents = store_.connections();
            if (storedEvents == null || storedEvents.length == 0) {
                // currently no data to send, we are done for now
                break;
            }

            // get first event from collection
            final String deviceId = DeviceInfo.getDeviceID();
            if (deviceId == null) {
                // When device ID is supplied by OpenUDID, in some cases it might take
                // time for OpenUDID service to initialize. So, just wait for it.
                break;
            }
            final String eventData = storedEvents[0] + "&device_id=" + deviceId;

            URLConnection conn = null;
            BufferedInputStream responseStream = null;
            try {
                // initialize and open connection
                conn = urlConnectionForEventData(eventData);
                conn.connect();

                // consume response stream
                responseStream = new BufferedInputStream(conn.getInputStream());
                final ByteArrayOutputStream responseData = new ByteArrayOutputStream(256); // big enough to handle success response without reallocating
                int c;
                while ((c = responseStream.read()) != -1) {
                    responseData.write(c);
                }

                // response code has to be 2xx to be considered a success
                boolean success = true;
                if (conn instanceof HttpURLConnection) {
                    final HttpURLConnection httpConn = (HttpURLConnection) conn;
                    final int responseCode = httpConn.getResponseCode();
                    success = responseCode >= 200 && responseCode < 300;
                    if (!success) {
                        Log.w(Countly.TAG, "HTTP error response code was " + responseCode + " from submitting event data: " + eventData);
                    }
                }

                // HTTP response code was good, check response JSON contains {"result":"Success"}
                if (success) {
                    final JSONObject responseDict = new JSONObject(responseData.toString("UTF-8"));
                    success = responseDict.optString("result").equalsIgnoreCase("success");
                    if (!success) {
                        Log.w(Countly.TAG, "Response from Countly server did not report success, it was: " + responseData.toString("UTF-8"));
                    }
                }

                if (success) {
                    Log.d(Countly.TAG, "ok ->" + eventData);

                    // successfully submitted event data to Count.ly server, so remove
                    // this one from the stored events collection
                    store_.removeConnection(eventData);
                }
                else {
                    // warning was logged above, stop processing, let next tick take care of retrying
                    break;
                }
            }
            catch (Exception e) {
                Log.w(Countly.TAG, "Got exception while trying to submit event data: " + eventData, e);
                // if exception occurred, stop processing, let next tick take care of retrying
                break;
            }
            finally {
                // free connection resources
                if (responseStream != null) {
                    try { responseStream.close(); } catch (IOException ignored) {}
                }
                if (conn != null && conn instanceof HttpURLConnection) {
                    ((HttpURLConnection)conn).disconnect();
                }
            }
        }
    }

    // for unit testing
    String getServerURL() { return serverURL_; }
    CountlyStore getCountlyStore() { return store_; }
}
