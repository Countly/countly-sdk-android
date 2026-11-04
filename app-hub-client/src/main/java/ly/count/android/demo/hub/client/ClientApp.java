package ly.count.android.demo.hub.client;

import android.app.Application;
import ly.count.android.sdk.Countly;
import ly.count.android.sdk.CountlyConfig;
import ly.count.android.sdk.hub.CountlyHub;
import ly.count.android.sdk.hub.HubClientConfig;

/**
 * Initializes the Countly SDK the way an app without network access does: the regular setup, plus
 * one line that sends every request through the hub.
 */
public class ClientApp extends Application {
    /**
     * Sets the SDK up with the hub.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        CountlyConfig config = new CountlyConfig(this, BuildConfig.APP_KEY, BuildConfig.SERVER_URL)
            .setApplication(this)
            .setLoggingEnabled(true)
            .enableCrashReporting();
        CountlyHub.useHub(config, new HubClientConfig(this, BuildConfig.HUB_PACKAGE));
        Countly.sharedInstance().init(config);
    }
}
