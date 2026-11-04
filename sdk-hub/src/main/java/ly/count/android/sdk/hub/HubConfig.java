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
 *     .allowApp(new HubAllowedApp("com.example.navigation", NAVIGATION_APP_KEY)
 *         .setSigningCertificateSha256(NAVIGATION_CERT_SHA256))
 *     .allowApp(new HubAllowedApp("com.example.music", MUSIC_APP_KEY)
 *         .setSigningCertificateSha256(MUSIC_CERT_SHA256));
 * </pre>
 * Things to know when deploying a hub:
 * <ul>
 *   <li>The hub is meant for apps that show no Countly UI. Content and feedback widgets are not
 *   supported: {@link CountlyHub#useHub} turns the SDK's WebView-based UI off, and the hub does not relay
 *   the requests those features make.</li>
 *   <li>A hub sends to one server. The server URL the apps were configured with is not used, and a path
 *   prefix in it, such as "/countly" in "https://host/countly", is dropped; put any prefix the server
 *   needs into this configuration's server URL instead.</li>
 *   <li>Every app key an app sends with must be allowed for it. When an app key is added or rotated on
 *   the server, allow the new key here before the apps start using it; until then their requests are
 *   refused and kept by their SDKs.</li>
 *   <li>The hub's traffic is the host app's traffic: Android counts its data usage for the host app,
 *   and the host app's network settings apply to it, including a network the process is bound to, its
 *   network security configuration, a car maker network preference set for it, its process-wide proxy,
 *   cookie and keep-alive settings. A process-wide default SSL socket factory is not used; set one here
 *   with {@link #setSSLSocketFactory} when needed.</li>
 *   <li>A host app that is not part of the system image runs once per Android user, which on a car
 *   means one hub, and one server connection, per active driver profile.</li>
 *   <li>The host app should keep running while the apps send, for example as a persistent system app
 *   or with a foreground service. While Android has the host app stopped or frozen in the background,
 *   the apps' requests cannot be handed over, and their SDKs keep them until the hub runs again.</li>
 * </ul>
 */
public final class HubConfig {
    /**
     * The server paths of the Countly Android SDK's data, remote config and SDK behavior settings
     * requests, the paths a hub relays unless configured otherwise. The paths of content and feedback
     * widgets are not among them.
     */
    public static final List<String> DEFAULT_ALLOWED_PATHS = Collections.unmodifiableList(Arrays.asList(
        "/i",
        "/o/sdk"));

    final String serverUrl;
    final Map<String, HubAllowedApp> allowedApps = new LinkedHashMap<>();
    Set<String> allowedPaths = new LinkedHashSet<>(DEFAULT_ALLOWED_PATHS);
    int maxRequestBytes = 8 * 1024 * 1024;
    int maxResponseBytes = 1024 * 1024;
    int maxQueuedRequests = 100;
    int maxQueuedRequestsPerApp = 25;
    long maxQueuedBodyBytes = 16 * 1024 * 1024;
    int uplinkTimeoutMillis = 30_000;
    SSLSocketFactory sslSocketFactory = null;
    HubUplink uplink = null;
    boolean loggingEnabled = false;

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
     * any other path are refused for good. Whatever the path, a request is relayed only when it carries
     * an app key that is allowed for the app sending it.
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
     * reports with native dumps. A larger request is refused for good, and the app's SDK drops it so that
     * it does not hold back the requests after it.
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
     * Sets the largest response body the hub passes back to an app, 1 MB by default. A larger response
     * counts as a failed request.
     *
     * @param maxResponseBytes the limit in bytes, values below 1 are ignored
     * @return Returns the same config object for convenient linking
     */
    public synchronized HubConfig setMaxResponseBytes(int maxResponseBytes) {
        if (maxResponseBytes > 0) {
            this.maxResponseBytes = maxResponseBytes;
        }
        return this;
    }

    /**
     * Sets how many admitted requests may wait for their turn on the single uplink, 100 by default. A
     * request arriving when that many are waiting is turned away right away, and the app's SDK retries
     * it later. Waiting requests hold no thread of the host app, only their memory.
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
     * Sets how many requests of one app may be waiting or in flight at the same time, 25 by default, so
     * that one app cannot take every place in the queue.
     *
     * @param maxQueuedRequestsPerApp the limit, values below 1 are ignored
     * @return Returns the same config object for convenient linking
     */
    public synchronized HubConfig setMaxQueuedRequestsPerApp(int maxQueuedRequestsPerApp) {
        if (maxQueuedRequestsPerApp > 0) {
            this.maxQueuedRequestsPerApp = maxQueuedRequestsPerApp;
        }
        return this;
    }

    /**
     * Sets how many request body bytes the hub may hold for waiting and in-flight requests together,
     * 16 MB by default. A request that would go over it is turned away for now, unless nothing else is
     * held, so a single request up to {@link #setMaxRequestBytes} always fits.
     *
     * @param maxQueuedBodyBytes the limit in bytes, values below 1 are ignored
     * @return Returns the same config object for convenient linking
     */
    public synchronized HubConfig setMaxQueuedBodyBytes(long maxQueuedBodyBytes) {
        if (maxQueuedBodyBytes > 0) {
            this.maxQueuedBodyBytes = maxQueuedBodyBytes;
        }
        return this;
    }

    /**
     * Sets the connect and read timeout of the default HTTPS uplink, 30 seconds by default. A custom
     * uplink should finish within twice this time too, because the apps wait for a response only that
     * long once their request is sent.
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
     * certificate. Without one the uplink uses the platform's default TLS settings, not a process-wide
     * default the host app may have set for its own servers.
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

    /**
     * Turns on the hub's per-request logging, off by default so the hub stays quiet in production.
     * Warnings about a misconfiguration, such as an allowed app without a pinned signing certificate,
     * are logged regardless.
     *
     * @param loggingEnabled whether the hub logs each refused or failed request
     * @return Returns the same config object for convenient linking
     */
    public synchronized HubConfig setLoggingEnabled(boolean loggingEnabled) {
        this.loggingEnabled = loggingEnabled;
        return this;
    }
}
