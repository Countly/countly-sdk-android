package ly.count.android.demo.crash;

import android.app.Application;
import android.util.Log;
import ly.count.android.sdk.Countly;
import ly.count.android.sdk.CountlyConfig;
import ly.count.android.sdknative.CountlyNative;

public class App extends Application {

    private final static String COUNTLY_SERVER_URL = "https://your.server.ly";
    private final static String COUNTLY_APP_KEY = "YOUR_APP_KEY";
    private final static String DEFAULT_URL = "https://your.server.ly";
    private final static String DEFAULT_APP_KEY = "YOUR_APP_KEY";

    @Override public void onCreate() {
        super.onCreate();

        if (DEFAULT_URL.equals(COUNTLY_SERVER_URL) || DEFAULT_APP_KEY.equals(COUNTLY_APP_KEY)) {
            Log.e("CountlyCrashDemo", "Please provide correct COUNTLY_SERVER_URL and COUNTLY_APP_KEY");
            return;
        }

        Countly.applicationOnCreate();

        CountlyConfig config = new CountlyConfig(this, COUNTLY_APP_KEY, COUNTLY_SERVER_URL).setDeviceId("4432")
            .setLoggingEnabled(true)
            .enableAutomaticViewTracking()
            .setRequiresConsent(false);

        config.crashes.enableCrashReporting();

        Countly.sharedInstance().init(config);

        CountlyNative.initNative(getApplicationContext());
    }
}
