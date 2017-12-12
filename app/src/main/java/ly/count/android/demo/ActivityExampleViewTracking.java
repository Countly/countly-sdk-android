package ly.count.android.demo;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

import ly.count.android.sdk.Countly;

@SuppressWarnings({"UnusedParameters", "unused"})
public class ActivityExampleViewTracking extends Activity

    @Override
    public void onCreate(Bundle savedInstanceState) {
        activity = this;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_view_tracking);
        Countly.onCreate(this);

    }

    public void onClickViewTracking01(View v) {
        Countly.sharedInstance().setViewTracking(false);
    }

    public void onClickViewTracking02(View v) {
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

    public void onClickViewTracking07(View v) {
        Countly.sharedInstance().recordView("Awesome view");
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
}
