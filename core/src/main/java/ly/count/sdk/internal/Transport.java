package ly.count.sdk.internal;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
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
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import ly.count.sdk.User;

/**
 * Class managing all networking operations.
 * Contract:
 * <ul>
 *     <li>Instantiated once.</li>
 *     <li>Doesn't have any queues, sends one request at a time (guaranteed to have maximum one {@link #send(Request)} unresolved call at a time).</li>
 *     <li>Returns a {@link Future} which resolves to either success or a failure.</li>
 *     <li>Doesn't do any storage or configuration-related operations, doesn't call modules, etc.</li>
 * </ul>
 */

//class Network extends ModuleBase { - may be

public class Transport implements X509TrustManager {
    private static final Log.Module L = Log.module("network");
    private static final String PARAMETER_TAMPERING_DIGEST = "SHA-256";
    private static final String CHECKSUM = "checksum256";
    private static final int[] BACKOFF = new int[] { 1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144, 233 };

    private InternalConfig config;
    private int slept;          // how many consecutive times networking slept
    private boolean sleeps;     // whether exponential backoff sleeping is enabled

    private SSLContext sslContext;          // ssl context to use if pinning is enabled
    private List<byte[]> keyPins = null;    // list of parsed key pins
    private List<byte[]> certPins = null;   // list of parsed cert pins
    private X509TrustManager defaultTrustManager = null;    // default TrustManager to call along with Network one

    public enum RequestResult {
        OK,         // success
        RETRY,      // retry MAX_RETRIES_BEFORE_SLEEP before switching to SLEEP
        REMOVE      // bad request, remove
    }

    public Transport() {
    }

    /**
     * @see Module#init(InternalConfig)
     *
     * @param config
     * @throws IllegalArgumentException
     */
    public void init (InternalConfig config) throws IllegalArgumentException {
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

        try {
            setPins(config.getPublicKeyPins(), config.getCertificatePins());
        } catch (CertificateException e) {
            throw new IllegalArgumentException(e);
        }
    }

//    void onContext(android.content.Ctx context) {
//        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(android.content.Ctx.CONNECTIVITY_SERVICE);
//        NetworkInfo activeNetInfo = connectivityManager.getActiveNetworkInfo();
//        NetworkInfo mobNetInfo = connectivityManager.getNetworkInfo(     ConnectivityManager.TYPE_MOBILE );
//  https://stackoverflow.com/questions/1783117/network-listener-android
//    }

    /**
     * For testing purposes
     */
    public HttpURLConnection openConnection(String url, String params, boolean usingGET) throws IOException {
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

        if (connection instanceof HttpsURLConnection && sslContext != null) {
            HttpsURLConnection https = (HttpsURLConnection) connection;
            https.setSSLSocketFactory(sslContext.getSocketFactory());
        }

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
                    writer = new PrintWriter(new OutputStreamWriter(output, Utils.UTF8), true);

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

                    writer.append(Utils.CRLF).append("--").append(boundary).append("--").append(Utils.CRLF).flush();

                } else {
                    if (config.getParameterTamperingProtectionSalt() != null) {
                        request.params.add(CHECKSUM, Utils.digestHex(PARAMETER_TAMPERING_DIGEST, request.params.toString() + config.getParameterTamperingProtectionSalt()));
                    }
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                    output = connection.getOutputStream();
                    writer = new PrintWriter(new OutputStreamWriter(output, Utils.UTF8), true);

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
        writer.append("--").append(boundary).append(Utils.CRLF);
        if (file != null) {
            writer.append("Content-Disposition: form-data; name=\"").append(name).append("\"; filename=\"").append(value).append("\"").append(Utils.CRLF);
            writer.append("Content-Type: ").append(contentType).append(Utils.CRLF);
            writer.append("Content-Transfer-Encoding: binary").append(Utils.CRLF);
            writer.append(Utils.CRLF).flush();
            output.write((byte[])file);
            output.flush();
            writer.append(Utils.CRLF).flush();
        } else {
            writer.append("Content-Disposition: form-data; name=\"").append(name).append("\"").append(Utils.CRLF);
            writer.append("Content-Type: ").append(contentType).append("; charset=").append(Utils.UTF8).append(Utils.CRLF);
            writer.append(Utils.CRLF).append(value).append(Utils.CRLF).flush();
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

    public Tasks.Task<RequestResult> send(final Request request) {
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

                HttpURLConnection connection = null;
                try {
                    ModuleRequests.addRequired(config, request);

                    connection = connection(request, SDKCore.instance.user());
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
        } else if (code == 400 || code == 404) {
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

    private static String trimPem(String pem) {
        pem = pem.trim();

        final String beginPK = "-----BEGIN PUBLIC KEY-----";
        if (pem.startsWith(beginPK)) {
            pem = pem.substring(pem.indexOf(beginPK) + beginPK.length());
        }

        final String beginCert = "-----BEGIN CERTIFICATE-----";
        if (pem.startsWith(beginCert)) {
            pem = pem.substring(pem.indexOf(beginCert) + beginCert.length());
        }

        if (pem.contains("-----END ")) {
            pem = pem.substring(0, pem.indexOf("-----END"));
        }
        String res = pem.replaceAll("\n", "");
        return res;
    }

    private void setPins(Set<String> keys, Set<String> certs) throws CertificateException {
        keyPins = new ArrayList<>();
        certPins = new ArrayList<>();

        if (keys != null) for (String key : keys) {
            try {
                byte[] data = Utils.readStream(Transport.class.getClassLoader().getResourceAsStream(key));
                if (data != null) {
                    String string = new String(data);
                    if (string.contains("--BEGIN")) {
                        data = Utils.Base64.decode(trimPem(string));
                    }
                } else {
                    data = Utils.Base64.decode(trimPem(key));
                }

                try {
                    X509EncodedKeySpec spec = new X509EncodedKeySpec(data);
                    KeyFactory kf = KeyFactory.getInstance("RSA");
                    PublicKey k = kf.generatePublic(spec);
                    keyPins.add(k.getEncoded());
                } catch (InvalidKeySpecException e) {
                    L.d("Certificate in instead of public key it seems", e);
                    CertificateFactory cf = CertificateFactory.getInstance("X.509");
                    Certificate cert = cf.generateCertificate(new ByteArrayInputStream(data));
                    keyPins.add(cert.getPublicKey().getEncoded());
                }
            } catch (NoSuchAlgorithmException e) {
                L.d("Shouldn't happen " + key);
            }
        }

        if (certs != null) for (String cert : certs) {
            byte[] data = Utils.readStream(Transport.class.getClassLoader().getResourceAsStream(cert));
            if (data != null) {
                String string = new String(data);
                if (string.contains("--BEGIN")) {
                    data = Utils.Base64.decode(trimPem(string));
                }
            } else {
                data = Utils.Base64.decode(trimPem(cert));
            }

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate certificate = cf.generateCertificate(new ByteArrayInputStream(data));
            certPins.add(certificate.getEncoded());
        }

        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("X509");
            trustManagerFactory.init((KeyStore) null);

            for (TrustManager trustManager : trustManagerFactory.getTrustManagers()) {
                if (trustManager instanceof X509TrustManager) {
                    defaultTrustManager = (X509TrustManager) trustManager;
                }
            }

            if (!keyPins.isEmpty() || !certPins.isEmpty()) {
                sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, new TrustManager[]{this}, null);
            }
        } catch (Throwable t) {
            throw new CertificateException(t);
        }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        if (defaultTrustManager != null) {
            defaultTrustManager.checkClientTrusted(chain, authType);
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        if (keyPins.size() == 0 && certPins.size() == 0) {
            return;
        }

        if (chain == null) {
            throw new IllegalArgumentException("PublicKeyManager: X509Certificate array is null");
        }

        if (!(chain.length > 0)) {
            throw new IllegalArgumentException("PublicKeyManager: X509Certificate is empty");
        }

        if (!(null != authType && authType.contains("RSA"))) {
            throw new CertificateException("PublicKeyManager: AuthType is not RSA");
        }

        // Perform standard SSL/TLS checks
        if (defaultTrustManager != null) {
            defaultTrustManager.checkServerTrusted(chain, authType);
        }

        byte serverPublicKey[] = chain[0].getPublicKey().getEncoded();
        byte serverCertificate[] = chain[0].getEncoded();

        for (byte[] key : keyPins) {
            if (Arrays.equals(key, serverPublicKey)) {
                return;
            }
        }

        for (byte[] key : certPins) {
            if (Arrays.equals(key, serverCertificate)) {
                return;
            }
        }

        throw new CertificateException("Neither certificate nor public key passed pinning validation");
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }
}
