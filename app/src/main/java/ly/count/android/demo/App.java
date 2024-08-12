package ly.count.android.demo;

import android.app.Application;
import ly.count.android.sdk.Countly;
import ly.count.android.sdk.CountlyConfig;

public class App extends Application {
    /** You should use try.count.ly instead of YOUR_SERVER for the line below if you are using Countly trial service */
    private final static String COUNTLY_SERVER_URL = "https://your.server.ly";
    private final static String COUNTLY_APP_KEY = "YOUR_APP_KEY";

    @Override
    public void onCreate() {
        super.onCreate();

        CountlyConfig config = new CountlyConfig(this, COUNTLY_APP_KEY, COUNTLY_SERVER_URL)
            .setLoggingEnabled(true);

        config.contents.enableContentUpdates();
        Countly.sharedInstance().init(config);
    }
}
