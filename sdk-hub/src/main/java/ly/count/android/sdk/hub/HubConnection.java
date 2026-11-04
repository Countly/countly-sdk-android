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
import java.util.Iterator;
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
 * <p>
 * The request headers are kept by the connection itself: before Android 7 the platform's
 * URLConnection discards them and leaves storing them to the connection classes of its HTTP stack.
 */
final class HubConnection extends HttpURLConnection {
    private static final int DEFAULT_TIMEOUT_MILLIS = 60_000;

    private final HubTransport transport;
    private ByteArrayOutputStream requestBody;
    private HubResponse response;
    private IOException failure;
    private final Map<String, List<String>> requestProperties = new LinkedHashMap<>();
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
     * Sets a request header, replacing every value it had, whatever the case of its name.
     *
     * @param key the header name
     * @param value the header value
     * @throws IllegalStateException if the request was already handed to the hub
     * @throws NullPointerException if the name is null
     */
    @Override
    public void setRequestProperty(String key, String value) {
        checkNotConnected(key);
        removeRequestProperty(key);
        List<String> values = new ArrayList<>();
        values.add(value);
        requestProperties.put(key, values);
    }

    /**
     * Adds a value to a request header, keeping the values it already had.
     *
     * @param key the header name
     * @param value the header value
     * @throws IllegalStateException if the request was already handed to the hub
     * @throws NullPointerException if the name is null
     */
    @Override
    public void addRequestProperty(String key, String value) {
        checkNotConnected(key);
        List<String> values = findRequestProperty(key);
        if (values == null) {
            values = new ArrayList<>();
            requestProperties.put(key, values);
        }
        values.add(value);
    }

    /**
     * @param key the header name, matched case-insensitively
     * @return the last value set for the header, or null when it has none
     * @throws IllegalStateException if the request was already handed to the hub
     */
    @Override
    public String getRequestProperty(String key) {
        if (connected) {
            throw new IllegalStateException("Already connected");
        }
        List<String> values = key == null ? null : findRequestProperty(key);
        return values == null || values.isEmpty() ? null : values.get(values.size() - 1);
    }

    /**
     * @return every request header and its values, unmodifiable
     * @throws IllegalStateException if the request was already handed to the hub
     */
    @Override
    public Map<String, List<String>> getRequestProperties() {
        if (connected) {
            throw new IllegalStateException("Already connected");
        }
        Map<String, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> property : requestProperties.entrySet()) {
            copy.put(property.getKey(), Collections.unmodifiableList(new ArrayList<>(property.getValue())));
        }
        return Collections.unmodifiableMap(copy);
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
     * @param key the name of a header about to be changed
     * @throws IllegalStateException if the request was already handed to the hub
     * @throws NullPointerException if the name is null
     */
    private void checkNotConnected(String key) {
        if (connected) {
            throw new IllegalStateException("Already connected");
        }
        if (key == null) {
            throw new NullPointerException("key is null");
        }
    }

    /**
     * @param key a header name
     * @return the values of the header whose name matches case-insensitively, or null
     */
    private List<String> findRequestProperty(@NonNull String key) {
        for (Map.Entry<String, List<String>> property : requestProperties.entrySet()) {
            if (property.getKey().equalsIgnoreCase(key)) {
                return property.getValue();
            }
        }
        return null;
    }

    /**
     * @param key a header name, every entry matching it case-insensitively is removed
     */
    private void removeRequestProperty(@NonNull String key) {
        Iterator<String> names = requestProperties.keySet().iterator();
        while (names.hasNext()) {
            if (names.next().equalsIgnoreCase(key)) {
                names.remove();
            }
        }
    }

    /**
     * Turns what the SDK configured on this connection into the request for the hub. Repeated slashes
     * in the path are merged, as the web server in front of Countly does, so that a server URL
     * configured with a trailing slash still reaches the allowed paths.
     *
     * @return the request
     */
    private @NonNull HubRequest buildRequest() {
        Map<String, String> headers = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> property : requestProperties.entrySet()) {
            headers.put(property.getKey(), join(property.getValue()));
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
