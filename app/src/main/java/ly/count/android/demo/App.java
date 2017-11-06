package ly.count.android.demo;

import android.app.Application;

import java.net.MalformedURLException;

import ly.count.android.sdk.Config;
import ly.count.android.sdk.Countly;

/**
 * Demo Application subclass with an example of how to initialize Countly SDK
 */

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        try {
            Config config = new Config("http://192.168.3.77:3001", "33a5dd24fd38c4471573da5ee06b355a3a9b1283")
                    .enableTestMode()
                    .setLoggingLevel(Config.LoggingLevel.DEBUG)
                    .setFeatures(Config.Feature.Push, Config.Feature.Crash)
                    .setDeviceIdStrategy(Config.DeviceIdStrategy.INSTANCE_ID);

            Countly.init(this, config);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }
}
