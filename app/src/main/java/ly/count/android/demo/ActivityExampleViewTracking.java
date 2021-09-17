package ly.count.android.demo;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import java.util.HashMap;
import java.util.Map;

import ly.count.android.sdk.Countly;

@SuppressWarnings({ "UnusedParameters", "unused" })
public class ActivityExampleViewTracking extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_view_tracking);
    }

    public void onClickRecordViewA(View v) {
        Countly.sharedInstance().views().recordView("View A", null);
    }

    public void onClickRecordViewWithSegmentationA(View v) {
        Map<String, Object> viewSegmentation = new HashMap<>();

        viewSegmentation.put("Cats", 123);
        viewSegmentation.put("Moons", 1.8d);
        viewSegmentation.put("Moose", "Deer");

        Countly.sharedInstance().views().recordView("View A", viewSegmentation);
    }

    public void onClickRecordViewB(View v) {
        Countly.sharedInstance().views().recordView("View B", null);
    }

    public void onClickRecordViewWithSegmentationB(View v) {
        Map<String, Object> viewSegmentation = new HashMap<>();

        viewSegmentation.put("Dogs", 345);
        viewSegmentation.put("Sun", 4.33d);
        viewSegmentation.put("Moose", "Brownie");

        Countly.sharedInstance().views().recordView("View B", viewSegmentation);
    }

    public void onClickRecordViewC(View v) {
        Countly.sharedInstance().views().recordView("View C", null);
    }

    public void onClickRecordViewWithSegmentationC(View v) {
        Map<String, Object> viewSegmentation = new HashMap<>();

        viewSegmentation.put("Fish", 7);
        viewSegmentation.put("Jupiter", 9.98d);
        viewSegmentation.put("Moose", "Greenie");

        Countly.sharedInstance().views().recordView("View C", viewSegmentation);
    }

    public void onClickMiscTest(View v) {
        Countly.sharedInstance().events().recordEvent("Test Event");
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
