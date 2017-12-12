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
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;

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

    protected static String salt;

    ConnectionProcessor(final String serverURL, final CountlyStore store, final DeviceId deviceId, final SSLContext sslContext) {
        serverURL_ = serverURL;
        store_ = store;
        deviceId_ = deviceId;
        sslContext_ = sslContext;
    }

    URLConnection urlConnectionForEventData(final String eventData) throws IOException {
        String urlStr = serverURL_ + "/i?";
        if(!eventData.contains("&crash=") && eventData.length() < 2048) {
            urlStr += eventData;
            urlStr += "&checksum=" + sha1Hash(eventData + salt);
        } else {
            urlStr += "checksum=" + sha1Hash(eventData + salt);
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
            if(eventData.contains("&crash=") || eventData.length() >= 2048 || Countly.sharedInstance().isHttpPostForced()){
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.d(Countly.TAG, "Using HTTP POST");
                }
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                OutputStream os = conn.getOutputStream();
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
                writer.write(eventData);
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

            // get first event from collection
            if (deviceId_.getId() == null) {
                // When device ID is supplied by OpenUDID or by Google Advertising ID.
                // In some cases it might take time for them to initialize. So, just wait for it.
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.i(Countly.TAG, "No Device ID available yet, skipping request " + storedEvents[0]);
                }
                break;
            }

            boolean deviceIdOverride = storedEvents[0].contains("&override_id=");
            boolean deviceIdChange = storedEvents[0].contains("&device_id=");

            final String eventData, newId;
            if (deviceIdOverride) {
                eventData = storedEvents[0].replace("&override_id=", "&device_id=");
                newId = null;
            } else if (deviceIdChange) {
                newId = storedEvents[0].substring(storedEvents[0].indexOf("&device_id=") + "&device_id=".length());
                if (newId.equals(deviceId_.getId())) {
                    eventData = storedEvents[0];
                    deviceIdChange = false;
                } else {
                    eventData = storedEvents[0] + "&old_device_id=" + deviceId_.getId();
                }
            } else {
                newId = null;
                eventData = storedEvents[0] + "&device_id=" + deviceId_.getId();
            }

            if(!(Countly.sharedInstance().isDeviceAppCrawler() && Countly.sharedInstance().ifShouldIgnoreCrawlers())) {
                //continue with sending the request to the server
                URLConnection conn = null;
                try {
                    // initialize and open connection
                    conn = urlConnectionForEventData(eventData);
                    conn.connect();

                    // response code has to be 2xx to be considered a success
                    boolean success = true;
                    final int responseCode;
                    if (conn instanceof HttpURLConnection) {
                        final HttpURLConnection httpConn = (HttpURLConnection) conn;
                        responseCode = httpConn.getResponseCode();
                        success = responseCode >= 200 && responseCode < 300;
                        if (!success && Countly.sharedInstance().isLoggingEnabled()) {
                            Log.w(Countly.TAG, "HTTP error response code was " + responseCode + " from submitting event data: " + eventData);
                        }
                    } else {
                        responseCode = 0;
                    }

                    // HTTP response code was good, check response JSON contains {"result":"Success"}
                    if (success) {
                        if (Countly.sharedInstance().isLoggingEnabled()) {
                            Log.d(Countly.TAG, "ok ->" + eventData);
                        }

                        // successfully submitted event data to Count.ly server, so remove
                        // this one from the stored events collection
                        store_.removeConnection(storedEvents[0]);

                        if (deviceIdChange) {
                            deviceId_.changeToDeveloperId(store_, newId);
                        }
                    } else if (responseCode >= 400 && responseCode < 500) {
                        if (Countly.sharedInstance().isLoggingEnabled()) {
                            Log.d(Countly.TAG, "fail " + responseCode + " ->" + eventData);
                        }
                        store_.removeConnection(storedEvents[0]);
                    } else {
                        // warning was logged above, stop processing, let next tick take care of retrying
                        break;
                    }
                } catch (Exception e) {
                    if (Countly.sharedInstance().isLoggingEnabled()) {
                        Log.w(Countly.TAG, "Got exception while trying to submit event data: " + eventData, e);
                    }
                    // if exception occurred, stop processing, let next tick take care of retrying
                    break;
                } finally {
                    // free connection resources
                    if (conn != null && conn instanceof HttpURLConnection) {
                        ((HttpURLConnection) conn).disconnect();
                    }
                }
            } else {
                //device is identified as a app crawler and nothing is sent to the server
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.i(Countly.TAG, "Device identified as a app crawler, skipping request " + storedEvents[0]);
                }

                //remove stored data
                store_.removeConnection(storedEvents[0]);
            }
        }
    }

    private static String sha1Hash (String toHash) {
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
