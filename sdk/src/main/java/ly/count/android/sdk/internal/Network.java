package ly.count.android.sdk.internal;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

import ly.count.android.sdk.*;
import ly.count.android.sdk.Config;

/**
 * Class managing all networking operations.
 * Contract:
 * <ul>
 *     <li>Instantiated in a {@link android.app.Service} once.</li>
 *     <li>Doesn't have any queues, sends one request at a time (guaranteed to have maximum one {@link #send(Request)} unresolved call at a time).</li>
 *     <li>Returns a {@link Future} which resolves to either success or a failure.</li>
 *     <li>Doesn't do any storage or configuration-related operations, doesn't call modules, etc.</li>
 * </ul>
 */

//class Network extends ModuleBase { - may be

class Network {
    private static final int CONNECT_TIMEOUT_IN_MILLISECONDS = 30000;
    private static final int READ_TIMEOUT_IN_MILLISECONDS = 30000;
    private final ThreadPoolExecutor threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
    private InternalConfig _config;

    Network() {
    }

    /**
     * @see Module#init(InternalConfig)
     *
     * @param config
     * @throws IllegalArgumentException
     */
    void init (InternalConfig config) throws IllegalArgumentException {
        if(config == null){
            throw new IllegalArgumentException("Provided 'config' was null");
        }

        // ssl config (cert & public key pinning)
        // GET/POST handling
        // sha1 signing
        // 301/302 handling, probably configurable (like allowFollowingRedirects) and with response
        //      validation because public WiFi APs return 30X to login page which returns 200
        // exponential back off - not sure where to have it: either some
        //      sleeping delay in Future thread or in Service thread, probably better having it here
        // network status callbacks - may be
        // APM stuff - later

        _config = config;
    }

    Future<NetworkResponse> send(final Request request) throws IllegalArgumentException, MalformedURLException {
        if(request == null){
            Log.e("Provided 'request' was null");
            throw new IllegalArgumentException("Provided 'request' was null");
        }

        Log.d("Received new send request for network, adding to pool");

        android.util.Log.d("CountlyTests", "1");
        Future<NetworkResponse> cc = threadPool.submit(new Callable<NetworkResponse>() {
            @Override
            public NetworkResponse call() throws Exception {
                Log.d("Executing network request");
                android.util.Log.d("CountlyTests", "4");

                // setup for network request

                // url
                URL sURL = _config.getServerURL();

                // device id
                Config.DID did = _config.getDeviceId();
                String deviceID = did.toString();

                // request salt
                String salt = null;

                // SSL
                SSLContext sslContext_ = null;//todo finish this
                List<String> publicKeyPinCertificates = null;

                // determining appropriate request method
                boolean usingGetRequest = request.isGettable(sURL, deviceID, 3);

                if(_config.isUsePOST()){
                    Log.d("Forcing HTTP POST requests");
                    usingGetRequest = false;
                }

                // print received response and code

                NetworkResponse nResponse = NetworkResponse.createFailureObject();

                // Prepare data that has to be sent
                String eventData = request.params.toString();

                // doing the network request

                HttpURLConnection conn = null;
                try {
                    // initialize the connection
                    {
                        String serverURLStr = sURL.toString() + "/i?";

                        if(eventData.contains("&crash=")){
                            usingGetRequest = false;
                        }

                        if(usingGetRequest) {
                            serverURLStr += eventData;
                            serverURLStr += "&checksum=" + sha1Hash(eventData + salt);
                        } else {
                            serverURLStr += "checksum=" + sha1Hash(eventData + salt);
                        }

                        final URL url = new URL(serverURLStr);

                        if (publicKeyPinCertificates == null) {
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
                        if(usingGetRequest){
                            conn.setRequestMethod("GET");
                        } else {
                            conn.setRequestMethod("POST");
                        }

                        //String picturePath = UserData.getPicturePathFromQuery(url);
                        String picturePath = "";//todo finish this

                        Log.d("Got picturePath: " + picturePath);
                        if(!picturePath.equals("")){
                            //todo does this work with conn.setRequestMethod("POST"); ?
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
                            writer.append("--" + boundary + "--").append(CRLF).flush();
                        }
                        else if(!usingGetRequest){
                            Log.d("Using HTTP POST");
                            conn.setDoOutput(true);
                            OutputStream os = conn.getOutputStream();
                            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
                            writer.write(eventData);
                            writer.flush();
                            writer.close();
                            os.close();
                        }
                        else{
                            Log.d("Using HTTP GET");
                            conn.setDoOutput(false);
                        }
                    }

                    // open the connection and get the result
                    conn.connect();

                    if (conn instanceof HttpURLConnection) {
                        final HttpURLConnection httpConn = (HttpURLConnection) conn;
                        nResponse.responseCode = httpConn.getResponseCode();
                        nResponse.httpResponse = getResponseOutOfHttpURLConnection(httpConn);
                    } else {
                        nResponse.responseCode = 0;
                    }
                }
                catch (Exception e) {
                    Log.w("Got exception while trying to submit event data: " + eventData, e);
                }
                finally {
                    // free connection resources
                    if (conn != null && conn instanceof HttpURLConnection) {
                        conn.disconnect();
                    }
                }


                // deciding the result of the request
                // response code has to be 2xx to be considered a success
                if(nResponse.responseCode >= 200 && nResponse.responseCode < 300){
                    Log.d("Network request succeeded");
                    nResponse.requestSucceeded = true;
//                    Log.d("ok ->" + eventData);
//                    if (deviceIdChange) {
//                        deviceId_.changeToDeveloperId(store_, newId);
//                    }
                } else if(nResponse.responseCode >= 400 && nResponse.responseCode < 500){
                    Log.d("Network request returned error");
                    nResponse.requestSucceeded = false;
//                    Log.d("fail " + responseCode + " ->" + eventData);
                } else {
                    Log.d("Network request failed");
                    nResponse.requestSucceeded = false;
//                    Log.w("HTTP error response code was " + responseCode + " from submitting event data: " + eventData);
                }

                Log.d("Network request resolved, returning result");

                return NetworkResponse.createFailureObject();
            }
        });

        android.util.Log.d("CountlyTests", "2");

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        android.util.Log.d("CountlyTests", "3");
        return cc;
    }

    public static class NetworkResponse{
        public boolean requestSucceeded = false;
        public int responseCode = -1;
        public String httpResponse = "";

        public NetworkResponse(){

        }

        /**
         * Created in case of failures or exceptions where the response could not be created naturally as a failed request.
         * @return
         */
        public static NetworkResponse createFailureObject(){
            NetworkResponse fo = new NetworkResponse();
            fo.requestSucceeded = false;

            return fo;
        }

        /**
         * Return internal state for debug purposes
         * @return
         */
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();

            sb.append("Request succeeded: [").append(requestSucceeded).append("], ");
            sb.append("Response Code: [").append(responseCode).append("], ");
            sb.append("HTTP Response: [").append(httpResponse).append("]");

            return sb.toString();
        }
    }

    String getResponseOutOfHttpURLConnection(HttpURLConnection httpConn) throws IOException {
        BufferedReader br;
        if (200 <= httpConn.getResponseCode() && httpConn.getResponseCode() <= 299) {
            br = new BufferedReader(new InputStreamReader((httpConn.getInputStream())));
        } else {
            br = new BufferedReader(new InputStreamReader((httpConn.getErrorStream())));
        }

        StringBuilder total = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            total.append(line).append('\n');
        }

        return total.toString();
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
                android.util.Log.e(Countly.TAG, "Cannot tamper-protect params", e);
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
}
