package ly.count.android.demo;

import android.app.Application;

import java.net.MalformedURLException;

import ly.count.android.sdk.Config;
import ly.count.android.sdk.CountlyNeo;
import ly.count.android.sdk.CountlyNeoLifecycle;

/**
 * Demo Application subclass with an example of how to initialize Countly SDK
 */

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        try {
            Config config = new Config("http://artem.count.ly", "b9e35f9f278064412c2d9ebf02c88de1b034f101")
                    .enableTestMode()
                    .setLoggingLevel(Config.LoggingLevel.DEBUG)
                    .setFeatures(Config.Feature.Push, Config.Feature.Crash)
                    .setDeviceIdStrategy(Config.DeviceIdStrategy.INSTANCE_ID);

            CountlyNeo.init(this, config);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }
}
