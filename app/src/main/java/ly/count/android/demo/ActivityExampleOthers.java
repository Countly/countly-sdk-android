package ly.count.android.demo;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import ly.count.android.sdk.Countly;
import ly.count.android.sdk.CountlyConfig;
import ly.count.android.sdk.DeviceId;
import ly.count.android.sdk.PersistentName;

@PersistentName("ActivityExampleOthers")
public class ActivityExampleOthers extends AppCompatActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_others);
    }

    public void onClickViewOther05(View v) {
        //set user location
        String countryCode = "us";
        String city = "Houston";
        String latitude = "29.634933";
        String longitude = "-95.220255";
        String ipAddress = null;

        Countly.sharedInstance().setLocation(countryCode, city, latitude + "," + longitude, ipAddress);
    }

    public void onClickViewOther06(View v) {
        //disable location
        Countly.sharedInstance().disableLocation();
    }

    public void onClickViewOther08(View v) {
        //Clearing request queue
        Countly.sharedInstance().flushRequestQueues();
    }

    public void onClickViewOther10(View v) {
        //Doing internally stored requests
        Countly.sharedInstance().doStoredRequests();
    }

    public void onClickTestcrashFilterSample(View v) {
        Countly.sharedInstance().crashes().recordUnhandledException(new Throwable("A really secret exception"));
    }

    public void onClickRemoveAllConsent(View v){
        Countly.sharedInstance().consent().removeConsentAll();
    }

    public void onClickGiveAllConsent(View v){
        Countly.sharedInstance().consent().giveConsentAll();
    }

    public void onClickHaltAndInit(View v) {
        Countly.sharedInstance().halt();

        final String COUNTLY_SERVER_URL = "YOUR_SERVER";
        final String COUNTLY_APP_KEY = "YOUR_APP_KEY";

        CountlyConfig config = (new CountlyConfig(this, COUNTLY_APP_KEY, COUNTLY_SERVER_URL)).setIdMode(DeviceId.Type.OPEN_UDID)
                .enableCrashReporting().setLoggingEnabled(true).enableCrashReporting().setViewTracking(true).setAutoTrackingUseShortName(true)
                .setRequiresConsent(false);

        Countly.sharedInstance().init(config);
    }
}
