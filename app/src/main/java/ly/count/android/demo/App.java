package ly.count.android.demo;

import android.app.Application;
import android.content.Context;
import android.os.Handler;

import ly.count.android.sdk.Config;
import ly.count.android.sdk.Countly;
import ly.count.android.sdk.Crash;
import ly.count.android.sdk.CrashProcessor;

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
//    private static final String COUNTLY_SERVER_URL = "http://192.168.3.77:3001";
    private static final String COUNTLY_SERVER_URL = "http://artem.count.ly";
//    private static final String COUNTLY_APP_KEY = "33a5dd24fd38c4471573da5ee06b355a3a9b1283";
//    private static final String COUNTLY_APP_KEY = "7e21d7e256952b150d3db09808fbcb255bc761e6";
    private static final String COUNTLY_APP_KEY = "db8cc5552bdb27cc5c9ad89bbb50e8f7ea6e620a";

    @Override
    public void onCreate() {
        super.onCreate();

        Countly.init(this, getCountlyConfig());
//
//        new Handler(getMainLooper()).postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                if (Countly.user() != null) {
//                    Countly.user().edit().addToCohort("manual").commit();
//                }
//            }
//        }, 3000);
    }

    public static Config getCountlyConfig () {
        return new Config(COUNTLY_SERVER_URL, COUNTLY_APP_KEY)
                .enableTestMode()
                .setLoggingLevel(Config.LoggingLevel.DEBUG)
                .setFeatures(Config.Feature.AutoSessionTracking, Config.Feature.Push, Config.Feature.Crash, Config.Feature.AutoViewTracking)
                .setDeviceIdStrategy(Config.DeviceIdStrategy.INSTANCE_ID);
    }
}
