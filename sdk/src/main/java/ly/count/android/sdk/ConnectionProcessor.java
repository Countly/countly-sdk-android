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
package ly.count.android.sdk;

import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ConnectionProcessor is a Runnable that is executed on a background
 * thread to submit session &amp; event data to a Count.ly server.
 *
 * NOTE: This class is only public to facilitate unit testing, because
 *       of this bug in dexmaker: https://code.google.com/p/dexmaker/issues/detail?id=34
 */
public class ConnectionProcessor implements Runnable {
    private static final int CONNECT_TIMEOUT_IN_MILLISECONDS = 30000;
    private static final int READ_TIMEOUT_IN_MILLISECONDS = 30000;
    private static final int WAIT_FOR_REQUEST_TIME_IN_SECONDS = 10;

    private final CountlyStore store_;
    private final DeviceId deviceId_;
    private final String serverURL_;
    private final OnResultListener listener_;


    private static ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private static Future<?> future;

    public static class Result {
        public static final int SKIPPING_NO_DEVICE_ID = 496;
        public static final int ERROR_EXCEPTION = 497;
        public static final int ERROR_INVALID_RESPONSE = 498;
        public static final int ERROR_NO_REQUESTS = 499;
        public String request;
        public int code;
        public String response;
        public JSONObject json;

        public boolean isSuccess() {
            return code >= 200 && code < 300;
        }
    }

    public interface OnResultListener {
        public void onResult(ConnectionProcessor.Result result);
    }

    public static void start(final CountlyStore store, final String serverURL, final DeviceId deviceId) {
        if (future == null) handleResult(store, serverURL, deviceId, null);
    }

    private static synchronized void handleResult(final CountlyStore store, final String serverURL, final DeviceId deviceId, Result result) {
        if (result == null || result.isSuccess()) {
            if (result != null && result.isSuccess()) {
                // handling result of previous request
                store.removeConnection(result.request);
            }
            // just go ahead and send a request if there are any
            if (store.hasNoConnections()) {
                waitAndSendNextRequest(store, serverURL, deviceId, null);
            } else {
                future = executor.submit(new ConnectionProcessor(serverURL, store, deviceId, new OnResultListener() {
                    @Override
                    public void onResult(Result result) {
                        handleResult(store, serverURL, deviceId, result);
                    }
                }));
            }
        } else {
            // retrying a bit later in case of error
            Log.e(Countly.TAG, "Couldn't send request, retrying in " + WAIT_FOR_REQUEST_TIME_IN_SECONDS + " seconds");
            waitAndSendNextRequest(store, serverURL, deviceId, null);
        }
    }

    private static void waitAndSendNextRequest(final CountlyStore store, final String serverURL, final DeviceId deviceId, Result result) {
        future = executor.schedule(new Runnable() {
            @Override
            public void run() {
                handleResult(store, serverURL, deviceId, null);
            }
        }, WAIT_FOR_REQUEST_TIME_IN_SECONDS, TimeUnit.SECONDS);
    }

    ConnectionProcessor(final String serverURL, final CountlyStore store, final DeviceId deviceId, final OnResultListener listener) {
        serverURL_ = serverURL;
        store_ = store;
        deviceId_ = deviceId;
        listener_ = listener;

        // HTTP connection reuse which was buggy pre-froyo
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.FROYO) {
            System.setProperty("http.keepAlive", "false");
        }
    }

    URLConnection urlConnectionForEventData(final String eventData) throws IOException {
        final String urlStr = serverURL_ + "/i?" + eventData;
        final URL url = new URL(urlStr);
        final HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_IN_MILLISECONDS);
        conn.setReadTimeout(READ_TIMEOUT_IN_MILLISECONDS);
        conn.setUseCaches(false);
        conn.setDoInput(true);
        String picturePath = UserData.getPicturePathFromQuery(url);
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Got picturePath: " + picturePath);
        }
        if(!picturePath.equals("")){
        	//Uploading files:
        	//http://stackoverflow.com/questions/2793150/how-to-use-java-net-urlconnection-to-fire-and-handle-http-requests
        	
        	File binaryFile = new File(picturePath);
        	conn.setDoOutput(true);
        	// Just generate some unique random value.
        	String boundary = Long.toHexString(System.currentTimeMillis());
        	// Line separator required by multipart/form-data.
        	String CRLF = "\r\n";
        	String charset = "UTF-8";
        	conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        	OutputStream output = conn.getOutputStream();
        	PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, charset), true);
        	// Send binary file.
            writer.append("--" + boundary).append(CRLF);
            writer.append("Content-Disposition: form-data; name=\"binaryFile\"; filename=\"" + binaryFile.getName() + "\"").append(CRLF);
            writer.append("Content-Type: " + URLConnection.guessContentTypeFromName(binaryFile.getName())).append(CRLF);
            writer.append("Content-Transfer-Encoding: binary").append(CRLF);
            writer.append(CRLF).flush();
            FileInputStream fileInputStream = new FileInputStream(binaryFile);
            byte[] buffer = new byte[1024];
            int len;
            while ((len = fileInputStream.read(buffer)) != -1) {
                output.write(buffer, 0, len);
            }
            output.flush(); // Important before continuing with writer!
            writer.append(CRLF).flush(); // CRLF is important! It indicates end of boundary.
            fileInputStream.close();

            // End of multipart/form-data.
            writer.append("--" + boundary + "--").append(CRLF).flush();
        }
        else{
        	conn.setDoOutput(false);
        }
        return conn;
    }

    @Override
    public void run() {
        final ConnectionProcessor.Result result = new ConnectionProcessor.Result();

        final String[] connections = store_.connections();
        if (connections == null || connections.length == 0) {
            // currently no data to send, we are done for now
            result.code = Result.ERROR_NO_REQUESTS;
            if (listener_ != null) {
                listener_.onResult(result);
            }
            return;
        }

        result.request = connections[0];

        // get first event from collection
        if (deviceId_.getId() == null) {
            // When device ID is supplied by OpenUDID or by Google Advertising ID.
            // In some cases it might take time for them to initialize. So, just wait for it.
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.i(Countly.TAG, "No Device ID available yet, skipping request " + connections[0]);
            }
            result.code = Result.SKIPPING_NO_DEVICE_ID;
            if (listener_ != null) {
                listener_.onResult(result);
            }
            return;
        }
        final String request = connections[0] + "&device_id=" + deviceId_.getId();

        // Small hook to help previous request to end up in MongoDB before proceeding with this one
        if (request.contains("token_session")) {
            SystemClock.sleep(5000);
        }

        URLConnection conn = null;
        BufferedInputStream responseStream = null;
        try {
            conn = urlConnectionForEventData(request);
            conn.connect();

            // consume response stream
            responseStream = new BufferedInputStream(conn.getInputStream());
            final ByteArrayOutputStream responseData = new ByteArrayOutputStream(256); // big enough to handle success response without reallocating
            int c;
            while ((c = responseStream.read()) != -1) {
                responseData.write(c);
            }

            result.response = responseData.toString("UTF-8");

            // response code has to be 2xx to be considered a success
            boolean success = true;
            if (conn instanceof HttpURLConnection) {
                final HttpURLConnection httpConn = (HttpURLConnection) conn;
                result.code = httpConn.getResponseCode();
                success = result.isSuccess();
                if (!success && Countly.sharedInstance().isLoggingEnabled()) {
                    Log.w(Countly.TAG, "HTTP error response code was " + result.code + " from submitting event data: " + request);
                }
            }

            // HTTP response code was good, check response JSON contains {"result":"Success"}
            if (success) {
                result.json = new JSONObject(result.response);
                success = result.json.optString("result").equalsIgnoreCase("success");
                if (!success && Countly.sharedInstance().isLoggingEnabled()) {
                    Log.w(Countly.TAG, "Response from Countly server did not report success, it was: " + responseData.toString("UTF-8"));
                }
            }

            if (success) {
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.d(Countly.TAG, "ok ->" + request);
                }
            }
            else {
                result.code = Result.ERROR_INVALID_RESPONSE;
            }
        }
        catch (Exception e) {
            result.code = Result.ERROR_EXCEPTION;
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.w(Countly.TAG, "Got exception while trying to submit event data: " + request, e);
            }
        }
        finally {
            // free connection resources
            if (responseStream != null) {
                try { responseStream.close(); } catch (IOException ignored) {}
            }
            if (conn != null && conn instanceof HttpURLConnection) {
                ((HttpURLConnection)conn).disconnect();
            }
            if (listener_ != null) {
                listener_.onResult(result);
            }
        }
    }

    // for unit testing
    String getServerURL() { return serverURL_; }
    CountlyStore getCountlyStore() { return store_; }
    DeviceId getDeviceId() { return deviceId_; }
}
