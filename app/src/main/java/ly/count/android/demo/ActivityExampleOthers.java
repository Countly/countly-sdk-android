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
import ly.count.android.sdk.DeviceIdType;

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

    public void onAddDirectRequestClick(View v) {
        Map<String, String> requestMap = new HashMap<>();
        requestMap.put("city", "Istanbul");
        requestMap.put("country_code", "TR");
        requestMap.put("ip_address", "41.0082,28.9784");
        requestMap.put("events", "[{\"key\":\"test\",\"count\":201,\"sum\":2010,\"dur\":2010,\"segmentation\":{\"trickplay\":[{\"type\":\"FF\",\"start_time\":123456789,\"end_time\":123456789},{\"type\":\"skip\",\"start_time\":123456789,\"end_time\":123456789},{\"type\":\"resume_play\",\"start_time\":123456789,\"end_time\":123456789}]}}]");
        Countly.sharedInstance().requestQueue().addDirectRequest(requestMap);
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

    public void onClickAttributionTest(View v) {
        Countly.sharedInstance().attribution().recordDirectAttribution("_special_test", "{'test_object':'some value', 'other value':'123'}");
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
