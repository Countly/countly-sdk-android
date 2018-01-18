package ly.count.android.demo;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import ly.count.android.sdk.Countly;
import ly.count.android.sdk.CountlyStarRating;
import ly.count.android.sdk.DeviceId;

@SuppressWarnings("UnusedParameters")
public class ActivityExampleOthers extends Activity {
    Activity activity;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        activity = this;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_others);
        Countly.onCreate(this);
    }

    @SuppressWarnings("unused")
    public void onClickViewOther01(View v) {

    }

    public void onClickViewOther02(View v) {
        Countly.sharedInstance().showStarRating(activity, new CountlyStarRating.RatingCallback() {
            @Override
            public void onRate(int rating) {
                Toast.makeText(activity, "onRate called with rating: " + rating, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDismiss() {
                Toast.makeText(activity, "onDismiss called", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void onClickViewOther03(View v) {
        Countly.sharedInstance().changeDeviceId(DeviceId.Type.DEVELOPER_SUPPLIED, "New Device ID");
    }

    public void onClickViewOther04(View v) {
        Countly.sharedInstance().changeDeviceId("New Device ID");
    }

    public void onClickViewOther05(View v) {
        //set location
        Countly.sharedInstance().setLocation("us", "Houston", "29.634933,-95.220255", null);
    }

    public void onClickViewOther06(View v) {
        //disable location
        Countly.sharedInstance().disableLocation();
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
