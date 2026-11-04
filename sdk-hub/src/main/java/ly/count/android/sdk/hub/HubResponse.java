package ly.count.android.sdk.hub;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The response the hub hands back to an app: the HTTP status, the response headers and the body,
 * either as the server sent them or, when the hub refused the request, as the hub made them up.
 */
public final class HubResponse {
    private final int status;
    private final Map<String, String> headers;
    private final byte[] body;

    /**
     * Creates a response.
     *
     * @param status the HTTP status code
     * @param headers the response headers, with several values of one header joined by ", "
     * @param body the response body, empty when there is none; it is kept as it is, not copied
     */
    public HubResponse(int status, @NonNull Map<String, String> headers, @NonNull byte[] body) {
        this.status = status;
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        this.body = body;
    }

    /**
     * @return the HTTP status code
     */
    public int getStatus() {
        return status;
    }

    /**
     * @return the response headers, unmodifiable
     */
    public @NonNull Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * Returns the value of a header, matching its name case-insensitively.
     *
     * @param name the header name
     * @return the value, or null when the response has no such header
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
     * @return the response body, empty when there is none; it must not be modified
     */
    public @NonNull byte[] getBody() {
        return body;
    }
}
