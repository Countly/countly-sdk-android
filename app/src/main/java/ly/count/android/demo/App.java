package ly.count.android.demo;

import android.app.Application;

import java.net.MalformedURLException;

import ly.count.android.sdk.Config;
import ly.count.android.sdk.CountlyNeo;

/**
 * Created by artem on 04/08/2017.
 */

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        try {
            CountlyNeo.init(this, new Config("http://artem.count.ly", "b9e35f9f278064412c2d9ebf02c88de1b034f101").enableTestMode().setLoggingLevel(Config.LoggingLevel.DEBUG));
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }
}
