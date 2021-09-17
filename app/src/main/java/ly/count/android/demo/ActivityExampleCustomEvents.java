package ly.count.android.demo;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import ly.count.android.sdk.Countly;

@SuppressWarnings("UnusedParameters")
public class ActivityExampleCustomEvents extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_custom_events);
    }

    public void onClickRecordEvent01(View v) {
        Countly.sharedInstance().events().recordEvent("Custom event 1");
    }

    public void onClickRecordEvent02(View v) {
        Countly.sharedInstance().events().recordEvent("Custom event 2", 3);
    }

    public void onClickRecordEvent03(View v) {
        Countly.sharedInstance().events().recordEvent("Custom event 3", 1, 134);
    }

    public void onClickRecordEvent04(View v) {
        Countly.sharedInstance().events().recordEvent("Custom event 4", null, 1, 0, 55);
    }

    public void onClickRecordEvent05(View v) {
        Map<String, Object> segmentation = new HashMap<>();
        segmentation.put("wall", "green");
        Countly.sharedInstance().events().recordEvent("Custom event 5", segmentation, 1, 0, 0);
    }

    public void onClickRecordEvent06(View v) {
        Map<String, Object> segmentation = new HashMap<>();
        segmentation.put("wall", "red");
        segmentation.put("flowers", 3);
        segmentation.put("area", 1.23);
        segmentation.put("volume", 7.88);
        Countly.sharedInstance().events().recordEvent("Custom event 6", segmentation, 15, 0, 0);
    }

    public void onClickRecordEvent07(View v) {
        Map<String, Object> segmentation = new HashMap<>();
        segmentation.put("wall", "blue");
        segmentation.put("flowers", new Random().nextInt());
        segmentation.put("area", new Random().nextDouble());
        segmentation.put("volume", new Random().nextDouble());

        Countly.sharedInstance().events().recordEvent("Custom event 7", segmentation, 25, 10, 0);
    }

    public void onClickRecordEvent08(View v) {
        Map<String, Object> segmentation = new HashMap<>();
        segmentation.put("wall", "yellow");
        Countly.sharedInstance().events().recordEvent("Custom event 8", segmentation, 25, 10, 50);
    }

    public void onClickRecordEvent09(View v) {
        //start timed event
        Countly.sharedInstance().events().recordEvent("Custom event 9");
    }

    public void onClickRecordEvent10(View v) {
        //stop timed event
        Countly.sharedInstance().events().recordEvent("Custom event 9");
    }

    public void onClickRecordEvent12(View v) {
        //cancel timed event
        Countly.sharedInstance().events().cancelEvent("Custom event 9");
    }

    public void onClickRecordEvent11(View v) {
        Map<String, Object> segmentation = new HashMap<>();
        segmentation.put("wall", "orange");
        Countly.sharedInstance().events().recordEvent("Custom event 9", segmentation, 4, 34);
    }

    public void onClickTriggerSendingEvents(View v) {
        Countly.sharedInstance().requestQueue().attemptToSendStoredRequests();
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
