package ly.count.android.demo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import ly.count.android.sdk.AttributionIndirectKey;
import ly.count.android.sdk.Countly;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
        Map<String, String> requestMap = new ConcurrentHashMap<>();
        requestMap.put("city", "Istanbul");
        requestMap.put("country_code", "TR");
        requestMap.put("ip_address", "41.0082,28.9784");

        try {
            JSONObject event = new JSONObject();
            event.putOpt("key", "test");
            event.putOpt("count", "201");
            event.putOpt("sum", "2010");
            event.putOpt("dur", "2010");

            JSONObject ffJson = new JSONObject();
            ffJson.putOpt("type", "FF");
            ffJson.putOpt("start_time", 123_456_789);
            ffJson.putOpt("end_time", 123_456_789);

            JSONObject skipJson = new JSONObject();
            skipJson.putOpt("type", "skip");
            skipJson.putOpt("start_time", 123_456_789);
            skipJson.putOpt("end_time", 123_456_789);

            JSONObject resumeJson = new JSONObject();
            resumeJson.putOpt("type", "resume_play");
            resumeJson.putOpt("start_time", 123_456_789);
            resumeJson.putOpt("end_time", 123_456_789);

            JSONArray trickPlay = new JSONArray();
            trickPlay.put(ffJson);
            trickPlay.put(skipJson);
            trickPlay.put(resumeJson);

            JSONObject segmentation = new JSONObject();
            segmentation.putOpt("trickplay", trickPlay);
            event.putOpt("segmentation", segmentation);

            JSONArray events = new JSONArray();
            events.put(event);
            requestMap.put("events", events.toString());
        } catch (JSONException e) {
            Log.e("Countly", "Failed to create JSON object", e);
        }
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

    String GetAdvertisingID() {
        return "12345";//this is only a dummy value
    }

    public void onClickReportIndirectAttribution(View v) {
        Map<String, String> attributionValues = new ConcurrentHashMap<>();
        attributionValues.put(AttributionIndirectKey.AdvertisingID, GetAdvertisingID());
        Countly.sharedInstance().attribution().recordIndirectAttribution(attributionValues);
    }

    public void onClickAttributionTest(View v) {
        Countly.sharedInstance().attribution().recordDirectAttribution("_special_test", "{'test_object':'some value', 'other value':'123'}");
    }

    public void onClickBeginSession(View v) {
        Countly.sharedInstance().sessions().beginSession();
    }

    public void onClickUpdateSession(View v) {
        Countly.sharedInstance().sessions().updateSession();
    }

    public void onClickEndSession(View v) {
        Countly.sharedInstance().sessions().endSession();
    }

    public void onClickTransparentActivity(View v) {
        startActivity(new Intent(this, ActivityExampleTransparentActivity.class));
    }
}
