package ly.count.android.sdk.hub;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;
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
 * CountlyHub.useHub(config, new HubClientConfig(context, "com.example.connectivity")
 *     .setHubSigningCertificateSha256(HUB_CERT_SHA256));
 * Countly.sharedInstance().init(config);
 * </pre>
 * Requires a Countly Android SDK version that provides {@code CountlyConfig.setConnectionFactory}.
 * The hub is meant for apps that show no Countly UI: content and feedback widgets are not supported,
 * and push notification images, which the SDK downloads itself, are not shown without network access.
 */
public final class CountlyHub {
    private static final String TAG = "CountlyHub";

    private CountlyHub() {
    }

    /**
     * Makes the SDK configured by the given config send all of its server requests through the hub.
     * Call it before passing the config to {@code Countly.sharedInstance().init}.
     * <p>
     * It also turns the SDK's WebView-based UI off, as with {@code CountlyConfig.disableWebView()}: the
     * hub does not relay the requests of content and feedback widgets, so that UI could not be shown.
     * <p>
     * In a debuggable build, a warning is logged when no signing certificate is pinned for the hub
     * with {@link HubClientConfig#setHubSigningCertificateSha256}.
     *
     * @param config the SDK configuration
     * @param hubConfig where the hub runs
     * @return the same SDK configuration, for chaining
     * @throws IllegalStateException if the app's Countly SDK is too old to send its requests through a hub
     */
    public static @NonNull CountlyConfig useHub(@NonNull CountlyConfig config, @NonNull HubClientConfig hubConfig) {
        Context context = hubConfig.context;
        if (hubConfig.hubSigningCertificateSha256 == null && (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            Log.w(TAG, "No signing certificate is pinned for the hub, so requests go to any app installed as " + hubConfig.hubPackageName + "; call HubClientConfig.setHubSigningCertificateSha256");
        }
        try {
            config.disableWebView();
            return config.setConnectionFactory(new HubConnectionFactory(new HubChannel(hubConfig)));
        } catch (LinkageError e) {
            throw new IllegalStateException("The Countly hub needs a Countly Android SDK version that provides CountlyConfig.setConnectionFactory", e);
        }
    }
}
