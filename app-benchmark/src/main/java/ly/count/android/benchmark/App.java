package ly.count.android.benchmark;

import android.app.Application;
import android.os.StrictMode;
import ly.count.android.sdk.Countly;
import ly.count.android.sdk.CountlyConfig;

public class App extends Application {
    final String COUNTLY_SERVER_URL = "https://try.count.ly";
    final String COUNTLY_APP_KEY = "YOUR_APP_KEY";
    final String DEVICE_ID = "YOUR_DEVICE_ID";

    @Override
    public void onCreate() {
        super.onCreate();

        if (false) {
            //setting up strict mode for additional validation
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build());

            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build());
        }

        CountlyConfig config = (new CountlyConfig(this, COUNTLY_APP_KEY, COUNTLY_SERVER_URL))
            .setDeviceId(DEVICE_ID)
            .setLoggingEnabled(true)
            .enableCrashReporting()
            .setRecordAllThreadsWithCrash()
            .giveAllConsents()
            .setHttpPostForced(false)
            .setParameterTamperingProtectionSalt("test-benchmark-salt");

        Countly.sharedInstance().init(config);
    }
}
