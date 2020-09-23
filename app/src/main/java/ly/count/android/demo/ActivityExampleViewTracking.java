package ly.count.android.demo;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import java.util.HashMap;
import java.util.Map;

import ly.count.android.sdk.Countly;

@SuppressWarnings({"UnusedParameters", "unused"})
public class ActivityExampleViewTracking extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_view_tracking);
        Countly.onCreate(this);

    }

    public void onClickViewTrackingDisableAuto(View v) {
        Countly.sharedInstance().setViewTracking(false);
    }

    public void onClickViewTrackingEnableAuto(View v) {
        Countly.sharedInstance().setViewTracking(true);
    }

    public void onClickViewTracking03(View v) {

    }

    public void onClickViewTracking04(View v) {

    }

    public void onClickViewTracking05(View v) {

    }

    public void onClickViewTracking06(View v) {

    }

    public void onClickViewTrackingRecordView(View v) {
        Countly.sharedInstance().recordView("Awesome view", null);
    }

    public void onClickViewTrackingRecordViewWithSegmentation(View v) {
        Map<String, Object> viewSegmentation = new HashMap<>();

        viewSegmentation.put("Cats", 123);
        viewSegmentation.put("Moons", 9.98d);
        viewSegmentation.put("Moose", "Deer");

        Countly.sharedInstance().views().recordView("Better view", viewSegmentation);
    }


    @Override
    public void onStart()
    {
        super.onStart();
        Countly.sharedInstance().onStart(this);
    }

    @Override
    public void onStop()
    {
        Countly.sharedInstance().onStop();
        super.onStop();
    }

    @Override
    public void onConfigurationChanged (Configuration newConfig){
        super.onConfigurationChanged(newConfig);
        Countly.sharedInstance().onConfigurationChanged(newConfig);
    }
}
