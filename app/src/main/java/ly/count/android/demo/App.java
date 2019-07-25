package ly.count.android.demo;

import android.app.Application;
import android.content.Context;
import android.os.Handler;

import ly.count.sdk.android.Config;
import ly.count.sdk.android.Countly;

/**
 * Demo Application subclass with an example of how to initialize Countly SDK.
 * Note that this sample code is also run in {@link android.app.Service} in case you use 2-process model.
 * Inside {@link android.app.Service}, there is no {@link Countly#user()} or {@link Countly#session(Context)}.
 */

public class App extends Application {
    /** You should use try.count.ly instead of YOUR_SERVER for the line below if you are using Countly trial service */
//    private static final String COUNTLY_SERVER_URL = "http://YOUR_SERVER.com";
//    private static final String COUNTLY_APP_KEY = "YOUR_APP_KEY";
//    private static final String COUNTLY_SERVER_URL = "http://192.168.1.149:3001";
    private static final String COUNTLY_SERVER_URL = "http://192.168.3.77:3001";
//    private static final String COUNTLY_SERVER_URL = "http://artem.count.ly";
//    private static final String COUNTLY_APP_KEY = "33a5dd24fd38c4471573da5ee06b355a3a9b1283";
    private static final String COUNTLY_APP_KEY = "f564319de7ad388c7c2bcc7ce89b1caa0806302d";
//    private static final String COUNTLY_APP_KEY = "33a5dd24fd38c4471573da5ee06b355a3a9b1283";

    @Override
    public void onCreate() {
        super.onCreate();

        Countly.init(this, getCountlyConfig());

        new Handler(getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (Countly.user(getApplicationContext()) != null) {
                    Countly.user(getApplicationContext()).edit().addToCohort("manual").commit();
                }
            }
        }, 3000);
    }

    public static Config getCountlyConfig () {
        return (Config) new Config(COUNTLY_SERVER_URL, COUNTLY_APP_KEY)
                .setDeviceIdStrategy(Config.DeviceIdStrategy.CUSTOM_ID, "sdfert")
                .enableFeatures(Config.Feature.Events, Config.Feature.Sessions, Config.Feature.CrashReporting, Config.Feature.UserProfiles, Config.Feature.StarRating, Config.Feature.RemoteConfig)
                .setAutoSessionsTracking(true)
                .enableTestMode()
                .setRequiresConsent(true)
                .setLoggingLevel(Config.LoggingLevel.DEBUG)
                .setEnableAutomaticRemoteConfig(true);
    }
}
