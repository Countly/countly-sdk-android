package ly.count.android.sdk.hub;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One request an app hands to the hub: the HTTP method, the server path and query, the request
 * headers and the body, exactly as the Countly SDK built them. The server address is not part of it,
 * the hub always sends to the server it was configured with.
 */
public final class HubRequest {
    private final String method;
    private final String path;
    private final String query;
    private final Map<String, String> headers;
    private final byte[] body;
    private final int timeoutMillis;

    /**
     * Creates a request.
     *
     * @param method the HTTP method, for example "GET" or "POST"
     * @param path the server path, for example "/i"
     * @param query the raw query without the leading '?', or null when there is none
     * @param headers the request headers, with several values of one header joined by ", "
     * @param body the request body, or null when there is none; it is kept as it is, not copied
     * @param timeoutMillis how long the caller waits for the response
     */
    public HubRequest(@NonNull String method, @NonNull String path, @Nullable String query, @NonNull Map<String, String> headers, @Nullable byte[] body, int timeoutMillis) {
        this.method = method;
        this.path = path;
        this.query = query;
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        this.body = body;
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * @return the HTTP method
     */
    public @NonNull String getMethod() {
        return method;
    }

    /**
     * @return the server path, for example "/i"
     */
    public @NonNull String getPath() {
        return path;
    }

    /**
     * @return the raw query without the leading '?', or null when there is none
     */
    public @Nullable String getQuery() {
        return query;
    }

    /**
     * @return the request headers, unmodifiable
     */
    public @NonNull Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * Returns the value of a header, matching its name case-insensitively.
     *
     * @param name the header name
     * @return the value, or null when the request has no such header
     */
    public @Nullable String getHeader(@NonNull String name) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * @return the request body, or null when there is none; it must not be modified
     */
    public @Nullable byte[] getBody() {
        return body;
    }

    /**
     * @return the size of the body in bytes, 0 when there is none
     */
    public int getBodyLength() {
        return body == null ? 0 : body.length;
    }

    /**
     * @return how long the caller waits for the response, in milliseconds
     */
    public int getTimeoutMillis() {
        return timeoutMillis;
    }
}
