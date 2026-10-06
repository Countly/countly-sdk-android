package ly.count.android.sdk.hub;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

/**
 * The default uplink: sends each request to the configured Countly server over HTTP(S).
 * <p>
 * The hub calls it from one thread, so there is never more than one request in flight. Every response
 * is read to the end and its stream closed without disconnecting, which hands the socket back to the
 * platform's keep-alive pool, so consecutive requests reuse one connection to the server instead of
 * opening a new one each time. A connection that failed is disconnected so that it is not reused.
 */
final class HttpsUplink implements HubUplink {
    private static final Set<String> HOP_BY_HOP_HEADERS = new HashSet<>(Arrays.asList(
        "host", "connection", "keep-alive", "proxy-connection", "proxy-authorization", "proxy-authenticate",
        "te", "trailer", "transfer-encoding", "upgrade", "content-length"));

    private final String serverUrl;
    private final SSLSocketFactory sslSocketFactory;
    private final int timeoutMillis;
    private final int maxResponseBytes;

    /**
     * @param serverUrl the server address without a trailing '/'
     * @param sslSocketFactory the factory for HTTPS connections, or null for the platform default
     * @param timeoutMillis the connect and read timeout
     * @param maxResponseBytes the largest response body accepted
     */
    HttpsUplink(@NonNull String serverUrl, @Nullable SSLSocketFactory sslSocketFactory, int timeoutMillis, int maxResponseBytes) {
        this.serverUrl = serverUrl;
        this.sslSocketFactory = sslSocketFactory;
        this.timeoutMillis = timeoutMillis;
        this.maxResponseBytes = maxResponseBytes;
    }

    /**
     * Sends the request to the configured server and reads the whole response.
     *
     * @param request the request
     * @return the server's response
     * @throws IOException if the server could not be reached or the response is larger than the limit
     */
    @Override
    public @NonNull HubResponse send(@NonNull HubRequest request) throws IOException {
        String address = serverUrl + request.getPath() + (request.getQuery() != null ? "?" + request.getQuery() : "");
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        boolean reusable = false;
        try {
            if (sslSocketFactory != null && connection instanceof HttpsURLConnection) {
                ((HttpsURLConnection) connection).setSSLSocketFactory(sslSocketFactory);
            }
            connection.setConnectTimeout(timeoutMillis);
            connection.setReadTimeout(timeoutMillis);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setDoInput(true);
            connection.setRequestMethod(request.getMethod());
            for (Map.Entry<String, String> header : request.getHeaders().entrySet()) {
                if (!HOP_BY_HOP_HEADERS.contains(header.getKey().toLowerCase(Locale.ROOT))) {
                    connection.setRequestProperty(header.getKey(), header.getValue());
                }
            }

            byte[] body = request.getBody();
            if (body != null && !"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod())) {
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(body.length);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body);
                }
            }

            int status = connection.getResponseCode();
            InputStream in = status >= HttpURLConnection.HTTP_BAD_REQUEST ? connection.getErrorStream() : connection.getInputStream();
            byte[] responseBody = in == null ? new byte[0] : readFully(in);
            Map<String, String> headers = responseHeaders(connection);
            reusable = true;
            return new HubResponse(status, headers, responseBody);
        } finally {
            if (!reusable) {
                connection.disconnect();
            }
        }
    }

    /**
     * Reads a response body to the end and closes the stream.
     *
     * @param in the response body stream
     * @return the body
     * @throws IOException if reading fails or the body is larger than the limit
     */
    private @NonNull byte[] readFully(@NonNull InputStream in) throws IOException {
        try (InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8 * 1024];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                if (out.size() > maxResponseBytes) {
                    throw new IOException("The server response is larger than " + maxResponseBytes + " bytes");
                }
            }
            return out.toByteArray();
        }
    }

    /**
     * @param connection the answered connection
     * @return its response headers, without the status line, several values of one header joined by ", "
     */
    private static @NonNull Map<String, String> responseHeaders(@NonNull HttpURLConnection connection) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> field : connection.getHeaderFields().entrySet()) {
            if (field.getKey() == null || field.getValue() == null) {
                continue;
            }
            StringBuilder value = new StringBuilder();
            for (String part : field.getValue()) {
                if (value.length() > 0) {
                    value.append(", ");
                }
                value.append(part);
            }
            headers.put(field.getKey(), value.toString());
        }
        return headers;
    }
}
