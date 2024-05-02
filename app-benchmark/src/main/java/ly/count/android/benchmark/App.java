package ly.count.android.benchmark;

import android.app.Application;
import ly.count.android.sdk.Countly;
import ly.count.android.sdk.CountlyConfig;
import ly.count.android.sdk.CountlyStore;
import ly.count.android.sdk.ModuleLog;
import ly.count.android.sdk.PerformanceCounterCollector;

public class App extends Application {
    private final static String COUNTLY_SERVER_URL = "https://xxx.count.ly";
    private final static String COUNTLY_APP_KEY = "YOUR_APP_KEY";
    private final static String DEVICE_ID = "YOUR_DEVICE_ID";

    public static PerformanceCounterCollector appPcc;

    @Override
    public void onCreate() {
        super.onCreate();

        CountlyConfig config = new CountlyConfig(this, COUNTLY_APP_KEY, COUNTLY_SERVER_URL)
            .setDeviceId(DEVICE_ID)
            .setLoggingEnabled(true)
            .giveAllConsents()
            .setRequestDropAgeHours(10)//to trigger the age blocks
            .setEventQueueSizeToSend(100)//for testing the main use case
            .setParameterTamperingProtectionSalt("test-benchmark-salt");

        appPcc = new PerformanceCounterCollector();
        config.pcc = appPcc;

        Countly.sharedInstance().init(config);

        //clear initial state to erase past data
        Countly.sharedInstance().requestQueue().flushQueues();

        Benchmark.countlyStore = new CountlyStore(this, new ModuleLog());
    }

    /*
     * Benchmark scenario - 1
     * Generate events and not requests: yes
     * wait: yes
     * EQ threshold: 10
     * segm values per event: 5
     * Generated request count: value doesn't matter
     * Event count: 10000
     * Fill to 1000 requests which equals to 10000 events generated
     *
     * steps:
     * 1) turn off internet
     * 2) clear counters
     * 3) Fill RQ/EQ
     * 4) turn on internet
     * 5) send requests
     * 6) wait till all sent
     * 7) print counters
     *
     * Scenario 2
     * RQ size 1000
     * Generate a mix of 1200 requests
     * EQ threshold 100
     * 4 direct requests : 1 event request
     * direct requests - 960
     * event requests - 240
     * events generated - 24000
     * segm values per event: 6
     */
}
