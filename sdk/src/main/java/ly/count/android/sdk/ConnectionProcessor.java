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
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

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

    private final CountlyStore store_;
    private final DeviceId deviceId_;
    private final String serverURL_;
    private final SSLContext sslContext_;

    private final Map<String, String> requestHeaderCustomValues_;

    protected static String salt;

    ConnectionProcessor(final String serverURL, final CountlyStore store, final DeviceId deviceId, final SSLContext sslContext, final Map<String, String> requestHeaderCustomValues) {
        serverURL_ = serverURL;
        store_ = store;
        deviceId_ = deviceId;
        sslContext_ = sslContext;
        requestHeaderCustomValues_ = requestHeaderCustomValues;
    }

    public URLConnection urlConnectionForServerRequest(final String requestData, final String customEndpoint) throws IOException {
        String urlEndpoint = "/i?";
        if(customEndpoint != null) {urlEndpoint = customEndpoint;}
        String urlStr = serverURL_ + urlEndpoint;
        if(!requestData.contains("&crash=") && requestData.length() < 2048) {
            urlStr += requestData;
            urlStr += "&checksum=" + sha1Hash(requestData + salt);
        } else {
            urlStr += "checksum=" + sha1Hash(requestData + salt);
        }
        final URL url = new URL(urlStr);
        final HttpURLConnection conn;
        if (Countly.publicKeyPinCertificates == null && Countly.certificatePinCertificates == null) {
            conn = (HttpURLConnection)url.openConnection();
        } else {
            HttpsURLConnection c = (HttpsURLConnection)url.openConnection();
            c.setSSLSocketFactory(sslContext_.getSocketFactory());
            conn = c;
        }
        conn.setConnectTimeout(CONNECT_TIMEOUT_IN_MILLISECONDS);
        conn.setReadTimeout(READ_TIMEOUT_IN_MILLISECONDS);
        conn.setUseCaches(false);
        conn.setDoInput(true);
        conn.setRequestMethod("GET");

        if(requestHeaderCustomValues_ != null){
            //if there are custom header values, add them
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.v(Countly.TAG, "Adding [" + requestHeaderCustomValues_.size() + "] custom header fields");
            }
            for (Map.Entry<String, String> entry : requestHeaderCustomValues_.entrySet())
            {
                String key = entry.getKey();
                String value = entry.getValue();
                if(key != null && value != null && !key.isEmpty()){
                    conn.addRequestProperty(key, value);
                }
            }
        }


        String picturePath = UserData.getPicturePathFromQuery(url);
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Got picturePath: " + picturePath);
        }
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.v(Countly.TAG, "Is the HTTP POST forced: " + Countly.sharedInstance().isHttpPostForced());
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
            }catch(IOException ex){
                ex.printStackTrace();
            }
            output.flush(); // Important before continuing with writer!
            writer.append(CRLF).flush(); // CRLF is important! It indicates end of boundary.
            fileInputStream.close();

            // End of multipart/form-data.
            writer.append("--").append(boundary).append("--").append(CRLF).flush();
        }
        else {
            if(requestData.contains("&crash=") || requestData.length() >= 2048 || Countly.sharedInstance().isHttpPostForced()){
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.d(Countly.TAG, "Using HTTP POST");
                }
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                OutputStream os = conn.getOutputStream();
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
                writer.write(requestData);
                writer.flush();
                writer.close();
                os.close();
            }
            else{
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.d(Countly.TAG, "Using HTTP GET");
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
            if (storedEvents == null || storedEvents.length == 0) {
                // currently no data to send, we are done for now
                break;
            }

            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.i(Countly.TAG, "[Connection Processor] Starting run, there are [" + storedEvents.length + "] requests stored");
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
                }
                break;
            }

            boolean deviceIdOverride = storedEvents[0].contains("&override_id="); //if the sendable data contains a override tag
            boolean deviceIdChange = storedEvents[0].contains("&device_id="); //if the sendable data contains a device_id tag. In this case it means that we will have to change the stored device ID
            boolean pushTokenRequest = storedEvents[0].contains("&token_session="); //check if we are about to begin a push token_session

            boolean waitForSecondsBeforeRequest = false; //if we should wait for 10 seconds before continuing the current request

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
                    newId = ConnectionProcessor.urlDecodeString(storedEvents[0].substring(endOfDeviceIdTag));

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
                        eventData = storedEvents[0] + "&old_device_id=" + deviceId_.getId();

                        // since the new_id will be merged with the old one, we wait 10 seconds before sending this request
                        // to give the server time to finish processing previous requests.

                        if (Countly.sharedInstance().isLoggingEnabled()) {
                            Log.d(Countly.TAG, "[Connection Processor] Waiting 10 seconds before sending device_id merge request");
                        }

                        waitForSecondsBeforeRequest = true;
                    }
                } else {
                    // this branch will be used in almost all requests.
                    // This just adds the device_id to them

                    newId = null;
                    eventData = storedEvents[0] + "&device_id=" + ConnectionProcessor.urlEncodeString(deviceId_.getId());
                }
            }

            if(pushTokenRequest){
                waitForSecondsBeforeRequest = true;
            }

            if(waitForSecondsBeforeRequest) {
                //giving server time to finish processing previous requests
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.d(Countly.TAG, "[Connection Processor] Starting 10 second wait");
                }

                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    if (Countly.sharedInstance().isLoggingEnabled()) {
                        Log.w(Countly.TAG, "[Connection Processor] While waiting for 10 seconds, sleep was interrupted");
                    }
                }

                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.d(Countly.TAG, "[Connection Processor] Wait (for changing device_id / starting token session) finished, continuing processing request");
                }
            }

            if(!(Countly.sharedInstance().isDeviceAppCrawler() && Countly.sharedInstance().ifShouldIgnoreCrawlers())) {
                //continue with sending the request to the server
                URLConnection conn = null;
                try {
                    // initialize and open connection
                    conn = urlConnectionForServerRequest(eventData, null);
                    conn.connect();

                    // response code has to be 2xx to be considered a success
                    boolean success = true;
                    final int responseCode;
                    if (conn instanceof HttpURLConnection) {
                        final HttpURLConnection httpConn = (HttpURLConnection) conn;
                        responseCode = httpConn.getResponseCode();
                        success = responseCode >= 200 && responseCode < 300;

                        if (!success && Countly.sharedInstance().isLoggingEnabled()) {
                            Log.w(Countly.TAG, "[Connection Processor] HTTP error response code was " + responseCode + " from submitting event data: " + eventData);
                        }
                    } else {
                        responseCode = 0;
                    }

                    // HTTP response code was good, check response JSON contains {"result":"Success"}
                    if (success) {
                        if (Countly.sharedInstance().isLoggingEnabled()) {
                            Log.d(Countly.TAG, "[Connection Processor] ok ->" + eventData);
                        }

                        // successfully submitted event data to Count.ly server, so remove
                        // this one from the stored events collection
                        store_.removeConnection(storedEvents[0]);

                        if (deviceIdChange) {
                            deviceId_.changeToDeveloperProvidedId(store_, newId);
                        }
                    } else if (responseCode >= 400 && responseCode < 500) {
                        if (Countly.sharedInstance().isLoggingEnabled()) {
                            Log.d(Countly.TAG, "[Connection Processor] fail " + responseCode + " ->" + eventData);
                        }
                        store_.removeConnection(storedEvents[0]);
                    } else {
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
                    if (conn != null && conn instanceof HttpURLConnection) {
                        try {
                            InputStream stream = conn.getInputStream();
                            stream.close();
                        } catch (Throwable ignored){}

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

    protected static String urlEncodeString(String givenValue){
        String result = "";

        try {
            result = java.net.URLEncoder.encode(givenValue, "UTF-8");
        } catch (UnsupportedEncodingException ignored) {
            // should never happen because Android guarantees UTF-8 support
        }

        return result;
    }

    protected static String urlDecodeString(String givenValue){
        String decodedResult = "";

        try {
            decodedResult = java.net.URLDecoder.decode(givenValue, "UTF-8");
        } catch (UnsupportedEncodingException ignored) {
            // should never happen because Android guarantees UTF-8 support
        }

        return decodedResult;
    }

    protected static String sha1Hash (String toHash) {
        String hash = null;
        try {
            MessageDigest digest = MessageDigest.getInstance( "SHA-1" );
            byte[] bytes = toHash.getBytes("UTF-8");
            digest.update(bytes, 0, bytes.length);
            bytes = digest.digest();

            // This is ~55x faster than looping and String.formating()
            hash = bytesToHex( bytes );
        }
        catch( Throwable e ) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.e(Countly.TAG, "Cannot tamper-protect params", e);
            }
        }
        return hash;
    }

    // http://stackoverflow.com/questions/9655181/convert-from-byte-array-to-hex-string-in-java
    final private static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex( byte[] bytes ) {
        char[] hexChars = new char[ bytes.length * 2 ];
        for( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[ j ] & 0xFF;
            hexChars[ j * 2 ] = hexArray[ v >>> 4 ];
            hexChars[ j * 2 + 1 ] = hexArray[ v & 0x0F ];
        }
        return new String( hexChars ).toLowerCase();
    }

    // for unit testing
    String getServerURL() { return serverURL_; }
    CountlyStore getCountlyStore() { return store_; }
    DeviceId getDeviceId() { return deviceId_; }
}
