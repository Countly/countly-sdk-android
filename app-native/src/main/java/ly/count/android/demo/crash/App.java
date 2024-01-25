package ly.count.android.demo.crash;

import android.app.Application;
import ly.count.android.sdk.Countly;
import ly.count.android.sdk.CountlyConfig;
import ly.count.android.sdknative.CountlyNative;

public class App extends Application {

    final static String COUNTLY_SERVER_URL = "https://your.server.ly";
    final static String COUNTLY_APP_KEY = "YOUR_APP_KEY";

    @Override public void onCreate() {
        super.onCreate();
        if (COUNTLY_SERVER_URL.equals("https://your.server.ly") || COUNTLY_APP_KEY.equals("YOUR_APP_KEY")) {
            System.err.println("Please provide correct COUNTLY_SERVER_URL and COUNTLY_APP_KEY");
            return;
        }

        Countly.applicationOnCreate();

        CountlyConfig config = (new CountlyConfig(this, COUNTLY_APP_KEY, COUNTLY_SERVER_URL)).setDeviceId("4432")
            .setLoggingEnabled(true)
            .enableCrashReporting()
            .setViewTracking(true)
            .setRequiresConsent(false);
        Countly.sharedInstance().init(config);

        CountlyNative.initNative(getApplicationContext());
    }
}