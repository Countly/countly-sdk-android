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

import android.util.Log;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * ConnectionProcessor is a Runnable that is executed on a background
 * thread to submit session &amp; event data to a Count.ly server.
 *
 * NOTE: This class is only public to facilitate unit testing, because
 * of this bug in dexmaker: https://code.google.com/p/dexmaker/issues/detail?id=34
 */
public class ConnectionProcessor implements Runnable {
    private static final int CONNECT_TIMEOUT_IN_MILLISECONDS = 30000;
    private static final int READ_TIMEOUT_IN_MILLISECONDS = 30000;

    private final CountlyStore store_;
    private final DeviceId deviceId_;
    private final String serverURL_;
    private final SSLContext sslContext_;

    private final Map<String, String> requestHeaderCustomValues_;

    protected static String salt;

    private enum RequestResult {
        OK,         // success
        RETRY,      // retry MAX_RETRIES_BEFORE_SLEEP before switching to SLEEP
        REMOVE      // bad request, remove
    }

    ConnectionProcessor(final String serverURL, final CountlyStore store, final DeviceId deviceId, final SSLContext sslContext, final Map<String, String> requestHeaderCustomValues) {
        serverURL_ = serverURL;
        store_ = store;
        deviceId_ = deviceId;
        sslContext_ = sslContext;
        requestHeaderCustomValues_ = requestHeaderCustomValues;
    }

    synchronized public URLConnection urlConnectionForServerRequest(String requestData, final String customEndpoint) throws IOException {
        String urlEndpoint = "/i";
        if (customEndpoint != null) {
            urlEndpoint = customEndpoint;
        }

        boolean usingHttpPost = (requestData.contains("&crash=") || requestData.length() >= 2048 || Countly.sharedInstance().isHttpPostForced());

        String urlStr = serverURL_ + urlEndpoint;
        if (usingHttpPost) {
            requestData += "&checksum256=" + UtilsNetworking.sha256Hash(requestData + salt);
        } else {
            urlStr += "?" + requestData;
            urlStr += "&checksum256=" + UtilsNetworking.sha256Hash(requestData + salt);
        }
        final URL url = new URL(urlStr);
        final HttpURLConnection conn;
        if (Countly.publicKeyPinCertificates == null && Countly.certificatePinCertificates == null) {
            conn = (HttpURLConnection) url.openConnection();
        } else {
            HttpsURLConnection c = (HttpsURLConnection) url.openConnection();
            c.setSSLSocketFactory(sslContext_.getSocketFactory());
            conn = c;
        }
        conn.setConnectTimeout(CONNECT_TIMEOUT_IN_MILLISECONDS);
        conn.setReadTimeout(READ_TIMEOUT_IN_MILLISECONDS);
        conn.setUseCaches(false);
        conn.setDoInput(true);
        conn.setRequestMethod("GET");

        if (requestHeaderCustomValues_ != null) {
            //if there are custom header values, add them
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.v(Countly.TAG, "[Connection Processor] Adding [" + requestHeaderCustomValues_.size() + "] custom header fields");
            }
            for (Map.Entry<String, String> entry : requestHeaderCustomValues_.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key != null && value != null && !key.isEmpty()) {
                    conn.addRequestProperty(key, value);
                }
            }
        }

        String picturePath = UserData.getPicturePathFromQuery(url);
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.v(Countly.TAG, "[Connection Processor] Got picturePath: " + picturePath);
        }
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.v(Countly.TAG, "[Connection Processor] Using HTTP POST: [" + usingHttpPost + "] forced:[" + Countly.sharedInstance().isHttpPostForced() + "] length:[" + (requestData.length() >= 2048) + "] crash:[" + requestData.contains("&crash=") + "]");
        }
        //Log.v(Countly.TAG, "Used url: " + urlStr);
        if (!picturePath.equals("")) {
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
            writer.append("--").append(boundary).append(CRLF);
            writer.append("Content-Disposition: form-data; name=\"binaryFile\"; filename=\"").append(binaryFile.getName()).append("\"").append(CRLF);
            writer.append("Content-Type: ").append(URLConnection.guessContentTypeFromName(binaryFile.getName())).append(CRLF);
            writer.append("Content-Transfer-Encoding: binary").append(CRLF);
            writer.append(CRLF).flush();
            FileInputStream fileInputStream = new FileInputStream(binaryFile);
            byte[] buffer = new byte[1024];
            int len;
            try {
                while ((len = fileInputStream.read(buffer)) != -1) {
                    output.write(buffer, 0, len);
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            output.flush(); // Important before continuing with writer!
            writer.append(CRLF).flush(); // CRLF is important! It indicates end of boundary.
            fileInputStream.close();

            // End of multipart/form-data.
            writer.append("--").append(boundary).append("--").append(CRLF).flush();
        } else {
            if (usingHttpPost) {
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                OutputStream os = conn.getOutputStream();
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
                writer.write(requestData);
                writer.flush();
                writer.close();
                os.close();
            } else {
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.v(Countly.TAG, "[Connection Processor] Using HTTP GET");
                }
                conn.setDoOutput(false);
            }
        }
        return conn;
    }

    @Override
    public void run() {
        while (true) {
            final String[] storedEvents = store_.connections();
            int storedEventCount = storedEvents == null ? 0 : storedEvents.length;

            if (Countly.sharedInstance().isLoggingEnabled()) {
                String msg = "[Connection Processor] Starting to run, there are [" + storedEventCount + "] requests stored";
                if (storedEventCount == 0) {
                    Log.v(Countly.TAG, msg);
                } else {
                    Log.i(Countly.TAG, msg);
                }
            }

            if (storedEvents == null || storedEventCount == 0) {
                // currently no data to send, we are done for now
                break;
            }

            // get first event from collection
            if (deviceId_.getId() == null) {
                // When device ID is supplied by OpenUDID or by Google Advertising ID.
                // In some cases it might take time for them to initialize. So, just wait for it.
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.i(Countly.TAG, "[Connection Processor] No Device ID available yet, skipping request " + storedEvents[0]);
                }
                break;
            }

            String temporaryIdOverrideTag = "&override_id=" + DeviceId.temporaryCountlyDeviceId;
            String temporaryIdTag = "&device_id=" + DeviceId.temporaryCountlyDeviceId;
            boolean containsTemporaryIdOverride = storedEvents[0].contains(temporaryIdOverrideTag);
            boolean containsTemporaryId = storedEvents[0].contains(temporaryIdTag);
            if (containsTemporaryIdOverride || containsTemporaryId || deviceId_.temporaryIdModeEnabled()) {
                //we are about to change ID to the temporary one or
                //the internally set id is the temporary one

                //abort and wait for exiting temporary mode
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.i(Countly.TAG, "[Connection Processor] Temporary ID detected, stalling requests. Id override:[" + containsTemporaryIdOverride + "], tmp id tag:[" + containsTemporaryId + "], temp ID set:[" + deviceId_.temporaryIdModeEnabled() + "]");
                }
                break;
            }

            boolean deviceIdOverride = storedEvents[0].contains("&override_id="); //if the sendable data contains a override tag
            boolean deviceIdChange = storedEvents[0].contains("&device_id="); //if the sendable data contains a device_id tag. In this case it means that we will have to change the stored device ID

            //add the device_id to the created request
            final String eventData, newId;
            if (deviceIdOverride) {
                // if the override tag is used, it means that the device_id will be changed
                // to finish the session of the previous device_id, we have cache it into the request
                // this is indicated by having the "override_id" tag. This just means that we
                // don't use the id provided in the deviceId variable as this might have changed already.

                eventData = storedEvents[0].replace("&override_id=", "&device_id=");
                newId = null;
            } else {
                if (deviceIdChange) {
                    // this branch will be used if a new device_id is provided
                    // and a device_id merge on server has to be performed

                    final int endOfDeviceIdTag = storedEvents[0].indexOf("&device_id=") + "&device_id=".length();
                    newId = UtilsNetworking.urlDecodeString(storedEvents[0].substring(endOfDeviceIdTag));

                    if (newId.equals(deviceId_.getId())) {
                        // If the new device_id is the same as previous,
                        // we don't do anything to change it

                        eventData = storedEvents[0];
                        deviceIdChange = false;

                        if (Countly.sharedInstance().isLoggingEnabled()) {
                            Log.d(Countly.TAG, "[Connection Processor] Provided device_id is the same as the previous one used, nothing will be merged");
                        }
                    } else {
                        //new device_id provided, make sure it will be merged
                        eventData = storedEvents[0] + "&old_device_id=" + UtilsNetworking.urlEncodeString(deviceId_.getId());
                    }
                } else {
                    // this branch will be used in almost all requests.
                    // This just adds the device_id to them

                    newId = null;
                    eventData = storedEvents[0] + "&device_id=" + UtilsNetworking.urlEncodeString(deviceId_.getId());
                }
            }

            if (!(Countly.sharedInstance().isDeviceAppCrawler() && Countly.sharedInstance().ifShouldIgnoreCrawlers())) {
                //continue with sending the request to the server
                URLConnection conn = null;
                InputStream connInputStream = null;
                try {
                    // initialize and open connection
                    conn = urlConnectionForServerRequest(eventData, null);
                    conn.connect();

                    int responseCode = 0;
                    String responseString = "";
                    if (conn instanceof HttpURLConnection) {
                        final HttpURLConnection httpConn = (HttpURLConnection) conn;

                        try {
                            //assume there will be no error
                            connInputStream = httpConn.getInputStream();
                        } catch (Exception ex) {
                            //in case of exception, assume there was a error in the request and change streams
                            connInputStream = httpConn.getErrorStream();
                        }

                        responseCode = httpConn.getResponseCode();
                        responseString = Utils.inputStreamToString(connInputStream);
                    }

                    if (Countly.sharedInstance().isLoggingEnabled()) {
                        Log.d(Countly.TAG, "[Connection Processor] code:[" + responseCode + "], response:[" + responseString + "], request: " + eventData);
                    }

                    final RequestResult rRes;

                    if (responseCode >= 200 && responseCode < 300) {

                        if (responseString.isEmpty()) {
                            if (Countly.sharedInstance().isLoggingEnabled()) {
                                Log.v(Countly.TAG, "[Connection Processor] Response was empty, will retry");
                            }
                            rRes = RequestResult.RETRY;
                        } else {
                            JSONObject jsonObject;
                            try {
                                jsonObject = new JSONObject(responseString);
                            } catch (JSONException ex) {
                                //failed to parse, so not a valid json
                                jsonObject = null;
                            }

                            if (jsonObject == null) {
                                //received unparseable response, retrying
                                if (Countly.sharedInstance().isLoggingEnabled()) {
                                    Log.v(Countly.TAG, "[Connection Processor] Response was a unknown, will retry");
                                }
                                rRes = RequestResult.RETRY;
                            } else {
                                if (jsonObject.has("result")) {
                                    //contains result entry
                                    if (Countly.sharedInstance().isLoggingEnabled()) {
                                        Log.v(Countly.TAG, "[Connection Processor] Response was a success");
                                    }
                                    rRes = RequestResult.OK;
                                } else {
                                    if (Countly.sharedInstance().isLoggingEnabled()) {
                                        Log.v(Countly.TAG, "[Connection Processor] Response does not contain 'result', will retry");
                                    }
                                    rRes = RequestResult.RETRY;
                                }
                            }
                        }
                    } else if (responseCode >= 300 && responseCode < 400) {
                        //assume redirect
                        if (Countly.sharedInstance().isLoggingEnabled()) {
                            Log.d(Countly.TAG, "[Connection Processor] Encountered redirect, will retry");
                        }
                        rRes = RequestResult.RETRY;
                    } else if (responseCode == 400 || responseCode == 404) {
                        if (Countly.sharedInstance().isLoggingEnabled()) {
                            Log.w(Countly.TAG, "[Connection Processor] Bad request, will still retry");
                        }
                        rRes = RequestResult.RETRY;
                    } else if (responseCode > 400) {
                        //server down, try again later
                        if (Countly.sharedInstance().isLoggingEnabled()) {
                            Log.d(Countly.TAG, "[Connection Processor] Server is down, will retry");
                        }
                        rRes = RequestResult.RETRY;
                    } else {
                        if (Countly.sharedInstance().isLoggingEnabled()) {
                            Log.d(Countly.TAG, "[Connection Processor] Bad response code, will retry");
                        }
                        rRes = RequestResult.RETRY;
                    }

                    switch (rRes) {
                        case OK:
                            // successfully submitted event data to Count.ly server, so remove
                            // this one from the stored events collection
                            store_.removeConnection(storedEvents[0]);

                            if (deviceIdChange) {
                                deviceId_.changeToDeveloperProvidedId(store_, newId);
                            }

                            if (deviceIdChange || deviceIdOverride) {
                                Countly.sharedInstance().notifyDeviceIdChange();
                            }
                            break;
                        case REMOVE:
                            //bad request, will be removed
                            store_.removeConnection(storedEvents[0]);
                            break;
                        case RETRY:
                            // warning was logged above, stop processing, let next tick take care of retrying
                            break;
                    }
                } catch (Exception e) {
                    if (Countly.sharedInstance().isLoggingEnabled()) {
                        Log.w(Countly.TAG, "[Connection Processor] Got exception while trying to submit event data: [" + eventData + "] [" + e + "]");
                    }
                    // if exception occurred, stop processing, let next tick take care of retrying
                    break;
                } finally {
                    // free connection resources
                    if (conn instanceof HttpURLConnection) {
                        try {
                            if (connInputStream != null) {
                                connInputStream.close();
                            }
                        } catch (Throwable ignored) {
                        }

                        ((HttpURLConnection) conn).disconnect();
                    }
                }
            } else {
                //device is identified as a app crawler and nothing is sent to the server
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.i(Countly.TAG, "[Connection Processor] Device identified as a app crawler, skipping request " + storedEvents[0]);
                }

                //remove stored data
                store_.removeConnection(storedEvents[0]);
            }
        }
    }

    // for unit testing
    String getServerURL() {
        return serverURL_;
    }

    CountlyStore getCountlyStore() {
        return store_;
    }

    DeviceId getDeviceId() {
        return deviceId_;
    }
}
