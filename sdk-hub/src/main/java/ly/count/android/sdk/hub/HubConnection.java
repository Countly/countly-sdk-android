package ly.count.android.sdk.hub;

import androidx.annotation.NonNull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The connection the SDK sends a request through when it uses the hub. It opens no socket: it
 * collects the request the SDK configures and writes, hands it to the hub the first time the SDK
 * needs the response, and then plays the hub's answer back through the usual HttpURLConnection calls.
 * <p>
 * A failed hand-over is remembered and thrown again by every later call that needs the response, the
 * same way a connection that could not reach its server behaves.
 */
final class HubConnection extends HttpURLConnection {
    private static final int DEFAULT_TIMEOUT_MILLIS = 60_000;

    private final HubTransport transport;
    private ByteArrayOutputStream requestBody;
    private HubResponse response;
    private IOException failure;
    private final List<String> responseHeaderNames = new ArrayList<>();
    private final List<String> responseHeaderValues = new ArrayList<>();

    /**
     * Creates a connection for one request.
     *
     * @param url the full request URL as the SDK built it
     * @param transport carries the request to the hub
     */
    HubConnection(@NonNull URL url, @NonNull HubTransport transport) {
        super(url);
        this.transport = transport;
    }

    /**
     * Does nothing. There is no socket to open: the request is handed to the hub when its response is
     * first needed, after the SDK has finished writing it.
     */
    @Override
    public void connect() {
    }

    /**
     * Does nothing, the connection holds no resources beyond the buffered request and response.
     */
    @Override
    public void disconnect() {
    }

    /**
     * @return false, the hub is not a proxy in the HTTP sense
     */
    @Override
    public boolean usingProxy() {
        return false;
    }

    /**
     * Returns the stream the SDK writes the request body into. The body is buffered until the request
     * is handed to the hub.
     *
     * @return the body stream
     * @throws ProtocolException if output was not enabled or the request was already handed over
     */
    @Override
    public OutputStream getOutputStream() throws IOException {
        if (!getDoOutput()) {
            throw new ProtocolException("A request body can only be written after setDoOutput(true)");
        }
        if (response != null || failure != null) {
            throw new ProtocolException("The request was already handed to the hub");
        }
        if (requestBody == null) {
            requestBody = new ByteArrayOutputStream();
        }
        return requestBody;
    }

    /**
     * Hands the request to the hub if that has not happened yet and returns the response status.
     *
     * @return the HTTP status code
     * @throws IOException if the request could not be handed over or answered
     */
    @Override
    public int getResponseCode() throws IOException {
        return exchange().getStatus();
    }

    /**
     * Hands the request to the hub if that has not happened yet and returns the response body. Like
     * a regular HTTP connection it throws for error statuses, and the body is then available through
     * {@link #getErrorStream()}.
     *
     * @return the response body
     * @throws IOException if the request could not be handed over or answered, or the status is 400 or higher
     */
    @Override
    public InputStream getInputStream() throws IOException {
        HubResponse hubResponse = exchange();
        int status = hubResponse.getStatus();
        if (status == HTTP_NOT_FOUND || status == HTTP_GONE) {
            throw new FileNotFoundException(url.toString());
        }
        if (status >= HTTP_BAD_REQUEST) {
            throw new IOException("Server returned HTTP response code: " + status + " for URL: " + url);
        }
        return new ByteArrayInputStream(hubResponse.getBody());
    }

    /**
     * Returns the body of an error response. Like a regular HTTP connection it never hands the request
     * over itself.
     *
     * @return the body when a response with status 400 or higher was received, otherwise null
     */
    @Override
    public InputStream getErrorStream() {
        if (response == null || response.getStatus() < HTTP_BAD_REQUEST) {
            return null;
        }
        return new ByteArrayInputStream(response.getBody());
    }

    /**
     * Hands the request to the hub if that has not happened yet and returns the name of a response
     * header.
     *
     * @param n the header index
     * @return the header name, or null when there is no such header or the request could not be handed over
     */
    @Override
    public String getHeaderFieldKey(int n) {
        if (!exchangeQuietly() || n < 0 || n >= responseHeaderNames.size()) {
            return null;
        }
        return responseHeaderNames.get(n);
    }

    /**
     * Hands the request to the hub if that has not happened yet and returns the value of a response
     * header.
     *
     * @param n the header index
     * @return the header value, or null when there is no such header or the request could not be handed over
     */
    @Override
    public String getHeaderField(int n) {
        if (!exchangeQuietly() || n < 0 || n >= responseHeaderValues.size()) {
            return null;
        }
        return responseHeaderValues.get(n);
    }

    /**
     * Hands the request to the hub if that has not happened yet and returns the value of a response
     * header, matching its name case-insensitively.
     *
     * @param name the header name
     * @return the header value, or null when there is no such header or the request could not be handed over
     */
    @Override
    public String getHeaderField(String name) {
        if (name == null || !exchangeQuietly()) {
            return null;
        }
        return response.getHeader(name);
    }

    /**
     * Hands the request to the hub if that has not happened yet and returns all response headers.
     *
     * @return the headers, empty when the request could not be handed over
     */
    @Override
    public Map<String, List<String>> getHeaderFields() {
        if (!exchangeQuietly()) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> fields = new LinkedHashMap<>();
        for (int i = 0; i < responseHeaderNames.size(); i++) {
            fields.put(responseHeaderNames.get(i), Collections.singletonList(responseHeaderValues.get(i)));
        }
        return Collections.unmodifiableMap(fields);
    }

    /**
     * Hands the request over for the calls that cannot throw.
     *
     * @return true when a response is available
     */
    private boolean exchangeQuietly() {
        try {
            exchange();
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    /**
     * Hands the request to the hub on the first call, and returns the response or throws the failure
     * of that first attempt on every later call.
     *
     * @return the response
     * @throws IOException if the request could not be handed over or answered
     */
    private @NonNull HubResponse exchange() throws IOException {
        if (response != null) {
            return response;
        }
        if (failure != null) {
            throw failure;
        }
        HubRequest request = buildRequest();
        connected = true;
        try {
            HubResponse hubResponse = transport.exchange(request);
            for (Map.Entry<String, String> header : hubResponse.getHeaders().entrySet()) {
                responseHeaderNames.add(header.getKey());
                responseHeaderValues.add(header.getValue());
            }
            responseCode = hubResponse.getStatus();
            response = hubResponse;
            return hubResponse;
        } catch (IOException e) {
            failure = e;
            throw e;
        }
    }

    /**
     * Turns what the SDK configured on this connection into the request for the hub. It has to run
     * before the connection is marked connected, because the request properties can only be read until then.
     * Repeated slashes in the path are merged, as the web server in front of Countly does, so that a
     * server URL configured with a trailing slash still reaches the allowed paths.
     *
     * @return the request
     */
    private @NonNull HubRequest buildRequest() {
        Map<String, String> headers = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> property : getRequestProperties().entrySet()) {
            if (property.getKey() != null && property.getValue() != null) {
                headers.put(property.getKey(), join(property.getValue()));
            }
        }

        int timeoutMillis = getConnectTimeout() + getReadTimeout();
        if (timeoutMillis <= 0) {
            timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
        }

        byte[] body = requestBody == null ? null : requestBody.toByteArray();
        String path = url.getPath() == null || url.getPath().isEmpty() ? "/" : url.getPath().replaceAll("/{2,}", "/");
        return new HubRequest(getRequestMethod(), path, url.getQuery(), headers, body, timeoutMillis);
    }

    /**
     * @param values the values of one header
     * @return the values joined by ", "
     */
    private static @NonNull String join(@NonNull List<String> values) {
        StringBuilder joined = new StringBuilder();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            if (joined.length() > 0) {
                joined.append(", ");
            }
            joined.append(value);
        }
        return joined.toString();
    }
}
