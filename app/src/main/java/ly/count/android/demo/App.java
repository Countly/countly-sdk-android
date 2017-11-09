package ly.count.android.demo;

import android.app.Application;

import ly.count.android.sdk.Config;
import ly.count.android.sdk.Countly;

/**
 * Demo Application subclass with an example of how to initialize Countly SDK
 */

public class App extends Application {
    /** You should use try.count.ly instead of YOUR_SERVER for the line below if you are using Countly trial service */
//    private static final String COUNTLY_SERVER_URL = "http://YOUR_SERVER.com";
//    private static final String COUNTLY_APP_KEY = "YOUR_APP_KEY";
    private static final String COUNTLY_SERVER_URL = "http://192.168.3.77:3001";
    private static final String COUNTLY_APP_KEY = "33a5dd24fd38c4471573da5ee06b355a3a9b1283";

    @Override
    public void onCreate() {
        super.onCreate();
        Config config = new Config(COUNTLY_SERVER_URL, COUNTLY_APP_KEY)
                .enableTestMode()
                .setLoggingLevel(Config.LoggingLevel.DEBUG)
                .setFeatures(Config.Feature.Push, Config.Feature.Crash, Config.Feature.AutoViewTracking)
                .setDeviceIdStrategy(Config.DeviceIdStrategy.INSTANCE_ID);

        Countly.init(this, config);
    }
}
