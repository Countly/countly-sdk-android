package ly.count.android.demo;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import java.util.HashMap;
import java.util.Map;
import ly.count.android.sdk.AttributionIndirectKey;
import ly.count.android.sdk.Countly;
import ly.count.android.sdk.CountlyConfig;
import ly.count.android.sdk.DeviceId;

@SuppressWarnings("UnusedParameters")
public class ActivityExampleOthers extends AppCompatActivity {
    Activity activity;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        activity = this;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_others);
    }

    public void onClickViewOther05(View v) {
        //set user location
        String countryCode = "us";
        String city = "Höuston";
        String latitude = "29.634933";
        String longitude = "-95.220255";
        String ipAddress = null;

        Countly.sharedInstance().location().setLocation(countryCode, city, latitude + "," + longitude, ipAddress);
    }

    public void onClickViewOther06(View v) {
        //disable location
        Countly.sharedInstance().location().disableLocation();
    }

    public void onClickViewOther08(View v) {
        //Clearing request queue
        Countly.sharedInstance().requestQueue().flushQueues();
    }

    public void onClickViewOther10(View v) {
        //Doing internally stored requests
        Countly.sharedInstance().requestQueue().attemptToSendStoredRequests();
    }

    public void onClickTestcrashFilterSample(View v) {
        Countly.sharedInstance().crashes().recordUnhandledException(new Throwable("A really secret exception"));
    }

    public void onClickRemoveAllConsent(View v) {
        Countly.sharedInstance().consent().removeConsentAll();
    }

    public void onClickGiveAllConsent(View v) {
        Countly.sharedInstance().consent().giveConsentAll();
    }

    public void onClickReportDirectAttribution(View v) {
        Countly.sharedInstance().attribution().recordDirectAttribution("countly", "{'cid':'campaign_id', 'cuid':'campaign_user_id'}");
    }

    String GetAdvertisingID(){
        return "12345";//this is only a dummy value
    }

    public void onClickReportIndirectAttribution(View v) {
        Map<String, String> attributionValues = new HashMap<>();
        attributionValues.put(AttributionIndirectKey.AdvertisingID, GetAdvertisingID());
        Countly.sharedInstance().attribution().recordIndirectAttribution(attributionValues);
    }

    public void onClickHaltAndInit(View v) {
        //this will destroy all currently stored data
        Countly.sharedInstance().halt();

        final String COUNTLY_SERVER_URL = "YOUR_SERVER";
        final String COUNTLY_APP_KEY = "YOUR_APP_KEY";

        CountlyConfig config = (new CountlyConfig(this, COUNTLY_APP_KEY, COUNTLY_SERVER_URL)).setIdMode(DeviceId.Type.OPEN_UDID)
            .enableCrashReporting().setLoggingEnabled(true).enableCrashReporting().setViewTracking(true).setAutoTrackingUseShortName(true)
            .setRequiresConsent(false);

        Countly.sharedInstance().init(config);
    }

    @Override
    public void onStart() {
        super.onStart();
        Countly.sharedInstance().onStart(this);
    }

    @Override
    public void onStop() {
        Countly.sharedInstance().onStop();
        super.onStop();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Countly.sharedInstance().onConfigurationChanged(newConfig);
    }
}
