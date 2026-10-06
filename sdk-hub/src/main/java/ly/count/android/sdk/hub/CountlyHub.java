package ly.count.android.sdk.hub;

import androidx.annotation.NonNull;
import ly.count.android.sdk.CountlyConfig;

/**
 * Entry point for apps that send their Countly requests through a hub.
 * <p>
 * The hub is a service in one host app on the device, the only app that needs network access. Apps
 * using it keep the regular Countly API and need no INTERNET permission: the SDK still builds every
 * request, hands it to the hub over Binder, and gets the server's response back. The host app runs
 * the hub by declaring a subclass of {@link CountlyHubService}.
 * <pre>
 * CountlyConfig config = new CountlyConfig(context, APP_KEY, SERVER_URL);
 * CountlyHub.useHub(config, new HubClientConfig(context, "com.example.connectivity"));
 * Countly.sharedInstance().init(config);
 * </pre>
 * Requires a Countly Android SDK version that provides {@code CountlyConfig.setConnectionFactory}.
 * WebView-based features (content, feedback and rating widgets) and push notification images load
 * from the network themselves and do not work without network access.
 */
public final class CountlyHub {
    private CountlyHub() {
    }

    /**
     * Makes the SDK configured by the given config send all of its server requests through the hub.
     * Call it before passing the config to {@code Countly.sharedInstance().init}.
     *
     * @param config the SDK configuration
     * @param hubConfig where the hub runs
     * @return the same SDK configuration, for chaining
     */
    public static @NonNull CountlyConfig useHub(@NonNull CountlyConfig config, @NonNull HubClientConfig hubConfig) {
        return config.setConnectionFactory(new HubConnectionFactory(new HubChannel(hubConfig)));
    }
}
