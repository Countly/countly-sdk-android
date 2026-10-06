package ly.count.android.sdk.hub;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.net.ssl.SSLSocketFactory;

/**
 * Configures a hub: the Countly server it sends to, the apps it accepts requests from, and the limits
 * it enforces. The host app returns one from {@link CountlyHubService#onCreateHubConfig()}.
 * <pre>
 * new HubConfig("https://countly.example.com")
 *     .allowApp(new HubAllowedApp("com.example.navigation", NAVIGATION_APP_KEY))
 *     .allowApp(new HubAllowedApp("com.example.music", MUSIC_APP_KEY));
 * </pre>
 */
public final class HubConfig {
    /**
     * The server paths the Countly Android SDK sends to, the paths a hub relays unless configured otherwise.
     */
    public static final List<String> DEFAULT_ALLOWED_PATHS = Collections.unmodifiableList(Arrays.asList(
        "/i",
        "/o/sdk",
        "/o/sdk/content",
        "/o/feedback/widget",
        "/o/surveys/survey/widget",
        "/o/surveys/nps/widget",
        "/o/surveys/rating/widget"));

    final String serverUrl;
    final Map<String, HubAllowedApp> allowedApps = new LinkedHashMap<>();
    Set<String> allowedPaths = new LinkedHashSet<>(DEFAULT_ALLOWED_PATHS);
    int maxRequestBytes = 8 * 1024 * 1024;
    int maxResponseBytes = 512 * 1024;
    int maxQueuedRequests = 10;
    int uplinkTimeoutMillis = 30_000;
    SSLSocketFactory sslSocketFactory = null;
    HubUplink uplink = null;

    /**
     * Creates a configuration for the given server.
     *
     * @param serverUrl the Countly server, for example "https://countly.example.com"; requests are sent
     * to this address whatever server URL the apps were configured with
     */
    public HubConfig(@NonNull String serverUrl) {
        String trimmed = serverUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (!trimmed.startsWith("https://") && !trimmed.startsWith("http://")) {
            throw new IllegalArgumentException("The hub server URL must start with https:// or http://");
        }
        this.serverUrl = trimmed;
    }

    /**
     * Accepts requests from an app. Requests from any app that was not allowed are refused.
     *
     * @param app the app and the app keys it may send data for
     * @return Returns the same config object for convenient linking
     */
    public synchronized HubConfig allowApp(@NonNull HubAllowedApp app) {
        allowedApps.put(app.packageName, app);
        return this;
    }

    /**
     * Replaces the server paths the hub relays, {@link #DEFAULT_ALLOWED_PATHS} by default. Requests to
     * any other path are refused.
     *
     * @param paths the paths, each starting with '/'
     * @return Returns the same config object for convenient linking
     */
    public synchronized HubConfig setAllowedPaths(@NonNull Collection<String> paths) {
        allowedPaths = new LinkedHashSet<>(paths);
        return this;
    }

    /**
     * Sets the largest request body the hub accepts, 8 MB by default, which leaves room for crash
     * reports with native dumps.
     *
     * @param maxRequestBytes the limit in bytes, values below 1 are ignored
     * @return Returns the same config object for convenient linking
     */
    public synchronized HubConfig setMaxRequestBytes(int maxRequestBytes) {
        if (maxRequestBytes > 0) {
            this.maxRequestBytes = maxRequestBytes;
        }
        return this;
    }

    /**
     * Sets how many accepted requests may wait for their turn on the single uplink, 10 by default. A
     * request arriving when that many are waiting fails right away, and the app's SDK retries it later,
     * so a burst from many apps cannot tie up the hub.
     *
     * @param maxQueuedRequests the limit, values below 1 are ignored
     * @return Returns the same config object for convenient linking
     */
    public synchronized HubConfig setMaxQueuedRequests(int maxQueuedRequests) {
        if (maxQueuedRequests > 0) {
            this.maxQueuedRequests = maxQueuedRequests;
        }
        return this;
    }

    /**
     * Sets the connect and read timeout of the default HTTPS uplink, 30 seconds by default.
     *
     * @param uplinkTimeoutMillis the timeout in milliseconds, values below 1 are ignored
     * @return Returns the same config object for convenient linking
     */
    public synchronized HubConfig setUplinkTimeoutMillis(int uplinkTimeoutMillis) {
        if (uplinkTimeoutMillis > 0) {
            this.uplinkTimeoutMillis = uplinkTimeoutMillis;
        }
        return this;
    }

    /**
     * Sets the SSLSocketFactory of the default HTTPS uplink, for example one that pins the server's
     * certificate.
     *
     * @param sslSocketFactory the factory, or null for the platform default
     * @return Returns the same config object for convenient linking
     */
    public synchronized HubConfig setSSLSocketFactory(@Nullable SSLSocketFactory sslSocketFactory) {
        this.sslSocketFactory = sslSocketFactory;
        return this;
    }

    /**
     * Replaces the default HTTPS uplink, for example with one that carries the requests over a
     * connection the host app already keeps to its own backend.
     *
     * @param uplink the uplink, or null for the default HTTPS uplink
     * @return Returns the same config object for convenient linking
     */
    public synchronized HubConfig setUplink(@Nullable HubUplink uplink) {
        this.uplink = uplink;
        return this;
    }
}
