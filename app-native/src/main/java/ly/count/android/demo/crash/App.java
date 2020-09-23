package ly.count.android.demo.crash;

import android.app.Application;
import ly.count.android.sdk.Countly;
import ly.count.android.sdk.CountlyConfig;
import ly.count.android.sdknative.CountlyNative;

public class App extends Application {

    final String COUNTLY_SERVER_URL = "https://try.count.ly";
    final String COUNTLY_APP_KEY = "xxxxxxx";

    @Override public void onCreate() {
        super.onCreate();
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