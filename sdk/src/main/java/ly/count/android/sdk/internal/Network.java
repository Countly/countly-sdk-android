package ly.count.android.sdk.internal;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.Future;

import ly.count.android.sdk.*;

import static ly.count.android.sdk.internal.Utils.CRLF;
import static ly.count.android.sdk.internal.Utils.UTF8;

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
    private static final Log.Module L = Log.module("network");
    private static final String PARAMETER_TAMPERING_DIGEST = "SHA-256";
    private static final String CHECKSUM = "checksum256";
    private static final int[] BACKOFF = new int[] { 1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144, 233 };

    private InternalConfig config;
    private int slept;          // how many consecutive times networking slept
    private boolean sleeps;     // whether exponential backoff sleeping is enabled

    enum RequestResult {
        OK,         // success
        RETRY,      // retry MAX_RETRIES_BEFORE_SLEEP before switching to SLEEP
        REMOVE      // bad request, remove
    }

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
        // sha1 signing
        // 301/302 handling, probably configurable (like allowFollowingRedirects) and with response
        //      validation because public WiFi APs return 30X to login page which returns 200
        // exponential back off - not sure where to have it: either some
        //      sleeping delay in Future thread or in Service thread, probably better having it here
        // network status callbacks - may be
        // APM stuff - later

        L.i("Server: " + config.getServerURL());
        this.config = config;
        this.slept = 0;
        this.sleeps = true;
    }

//    void onContext(android.content.Context context) {
//        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
//        NetworkInfo activeNetInfo = connectivityManager.getActiveNetworkInfo();
//        NetworkInfo mobNetInfo = connectivityManager.getNetworkInfo(     ConnectivityManager.TYPE_MOBILE );
//  https://stackoverflow.com/questions/1783117/network-listener-android
//    }

    /**
     * For testing purposes
     */
    HttpURLConnection openConnection(String url, String params, boolean usingGET) throws IOException {
        URL u;
        if (usingGET) {
            u = new URL(url + params);
        } else {
            u = new URL(url);
        }

        HttpURLConnection connection = (HttpURLConnection) u.openConnection();

        connection.setUseCaches(false);
        connection.setDoInput(true);
        connection.setDoOutput(!usingGET);
        connection.setRequestMethod(usingGET ? "GET" : "POST");

        return connection;
    }

    /**
     * Open connection for particular request: choose GET or POST, choose multipart or urlencoded if POST,
     * set SSL context, calculate and add checksum, load and send user picture if needed.
     *
     * @param request request to send
     * @param user user to check for picture
     * @return connection, not {@link HttpURLConnection#connected} yet
     * @throws IOException from {@link HttpURLConnection} in case of error
     */
    HttpURLConnection connection(final Request request, final User user) throws IOException {
        String path = config.getServerURL().toString() + "/i?";
        String picture = request.params.remove(UserEditorImpl.PICTURE_PATH);
        boolean usingGET = !config.isUsePOST() && request.isGettable(config.getServerURL()) && Utils.isEmpty(picture);

        if (usingGET && config.getParameterTamperingProtectionSalt() != null) {
            request.params.add(CHECKSUM, Utils.digestHex(PARAMETER_TAMPERING_DIGEST, request.params.toString() + config.getParameterTamperingProtectionSalt()));
        }

        HttpURLConnection connection = openConnection(path, request.params.toString(), usingGET);
        connection.setConnectTimeout(1000 * config.getNetworkConnectionTimeout());
        connection.setReadTimeout(1000 * config.getNetworkReadTimeout());

//        if ((config.getPublicKeyPins() != null || config.getCertificatePins() != null) && config.getServerURL().getProtocol().equals("https")) {
//            HttpsURLConnection https = (HttpsURLConnection) connection;
//            https.setSSLSocketFactory(null);
//        }

        if (!usingGET) {
            OutputStream output = null;
            PrintWriter writer = null;
            try {
                L.d("Picture " + picture);
                byte[] data = picture == null ? null : pictureData(user, picture);

                if (data != null) {
                    String boundary = Long.toHexString(System.currentTimeMillis());

                    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

                    output = connection.getOutputStream();
                    writer = new PrintWriter(new OutputStreamWriter(output, UTF8), true);

                    addMultipart(output, writer, boundary, "", "profilePicture", "image", data);

                    StringBuilder salting = new StringBuilder();
                    Map<String, String> map = request.params.map();
                    for (String key : map.keySet()) {
                        String value = Utils.urldecode(map.get(key));
                        salting.append(key).append("=").append(value).append("&");
                        addMultipart(output, writer, boundary, "text/plain", key, value, null);
                    }

                    if (config.getParameterTamperingProtectionSalt() != null) {
                        addMultipart(output, writer, boundary, "text/plain", CHECKSUM, Utils.digestHex(PARAMETER_TAMPERING_DIGEST, salting.substring(0, salting.length() - 1) + config.getParameterTamperingProtectionSalt()), null);
                    }

                    writer.append(CRLF).append("--").append(boundary).append("--").append(CRLF).flush();

                } else {
                    if (config.getParameterTamperingProtectionSalt() != null) {
                        request.params.add(CHECKSUM, Utils.digestHex(PARAMETER_TAMPERING_DIGEST, request.params.toString() + config.getParameterTamperingProtectionSalt()));
                    }
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                    output = connection.getOutputStream();
                    writer = new PrintWriter(new OutputStreamWriter(output, UTF8), true);

                    writer.write(request.params.toString());
                    writer.flush();
                }
            } finally {
                if (writer != null) {
                    try {
                        writer.close();
                    } catch (Throwable ignored){}
                }
                if (output != null) {
                    try {
                        output.close();
                    } catch (Throwable ignored){}
                }
            }
        }

        return connection;
    }

    void addMultipart(OutputStream output, PrintWriter writer, String boundary, String contentType, String name, String value, Object file) throws IOException {
        writer.append("--").append(boundary).append(CRLF);
        if (file != null) {
            writer.append("Content-Disposition: form-data; name=\"").append(name).append("\"; filename=\"").append(value).append("\"").append(CRLF);
            writer.append("Content-Type: ").append(contentType).append(CRLF);
            writer.append("Content-Transfer-Encoding: binary").append(CRLF);
            writer.append(CRLF).flush();
            output.write((byte[])file);
            output.flush();
            writer.append(CRLF).flush();
        } else {
            writer.append("Content-Disposition: form-data; name=\"").append(name).append("\"").append(CRLF);
            writer.append("Content-Type: ").append(contentType).append("; charset=").append(UTF8).append(CRLF);
            writer.append(CRLF).append(value).append(CRLF).flush();
        }
    }

    byte[] pictureData(User user, String picture) throws IOException {
        byte[] data;
        if (UserEditorImpl.PICTURE_IN_USER_PROFILE.equals(picture)) {
            data = user.picture();
        } else {
            String protocol = null;
            try {
                URI uri = new URI(picture);
                protocol = uri.isAbsolute() ? uri.getScheme() : new URL(picture).getProtocol();
            } catch (Throwable t) {
                try {
                    File f = new File(picture);
                    protocol = f.toURI().toURL().getProtocol();
                } catch (Throwable tt) {
                    L.w("Couldn't determine picturePath protocol", tt);
                }
            }
            if (protocol != null && protocol.equals("file")) {
                File file = new File(picture);
                if (!file.exists()) {
                    return null;
                }
                FileInputStream input = new FileInputStream(file);
                ByteArrayOutputStream output = new ByteArrayOutputStream();

                byte[] buffer = new byte[1024];
                int len;
                while ((len = input.read(buffer)) != -1) {
                    output.write(buffer, 0, len);
                }

                input.close();
                data = output.toByteArray();
                output.close();
            } else {
                return null;
            }
        }

        return data;
    }

    String response(HttpURLConnection connection) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder total = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                total.append(line).append('\n');
            }
            return total.toString();
        } catch (IOException e) {
            L.w("Error while reading server response", e);
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {}
            }
        }
    }

    Tasks.Task<RequestResult> send(final Request request) {
        return new Tasks.Task<RequestResult>(request.storageId()) {
            @Override
            public RequestResult call() {
                RequestResult result = send();

                if (result == RequestResult.OK || result == RequestResult.REMOVE) {
                    slept = 0;
                    return result;
                } else if (result == RequestResult.RETRY) {
                    if (sleeps) {
                        slept = slept % BACKOFF.length;
                        try {
                            Log.d("Sleeping for " + BACKOFF[slept] + " seconds");
                            Thread.sleep(BACKOFF[slept] * 1000);
                        } catch (InterruptedException e) {
                            L.e("Interrupted while sleeping", e);
                        }
                        slept++;
                        return call();
                    } else {
                        return result;
                    }
                } else {
                    L.wtf("Bad RequestResult");
                    return RequestResult.REMOVE;
                }
            }

            public RequestResult send() {
                L.i("Sending request: " + request);

                ModuleRequests.addRequired(config, request);

                HttpURLConnection connection = null;
                try {
                    connection = connection(request, Core.instance.user());
                    connection.connect();

                    int code = connection.getResponseCode();
                    String response = response(connection);

                    return processResponse(code, response);

                } catch (IOException e) {
                    L.w("Error while sending request " + request, e);
                    return RequestResult.RETRY;
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
        };
    }

    RequestResult processResponse(int code, String response) {
        L.i("Code " + code + " response " + response);

        if (code >= 200 && code < 300) {
            if (Utils.isEmpty(response)) {
                L.w("Success (null response)");
                // null response but response code is ok, optimistically assuming request was sent
                return RequestResult.OK;
            } else if (response.contains("Success")) {
                // response looks like {"result": "Success"}
                L.d("Success");
                return RequestResult.OK;
            } else {
                // some unknown response, will resend this request
                L.w("Unknown response: " + response);
                return RequestResult.RETRY;
            }
        } else if (code >= 300 && code < 400) {
            // redirects
            L.w("Server returned redirect");
            return RequestResult.RETRY;
        } else if (code == 400) {
            L.e("Bad request: " + response);
            return RequestResult.REMOVE;
        } else if (code > 400) {
            // server is down, will retry later
            L.w("Server is down");
            return RequestResult.RETRY;
        } else {
            L.w("Bad response code " + code);
            return RequestResult.RETRY;
        }
    }
}
