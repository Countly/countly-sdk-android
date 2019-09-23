package ly.count.android.demo;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import java.util.Random;

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
        //show star rating
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

    public void onClickViewOther07(View v) {
        //show rating widget
        String widgetId = "xxxxx";
        Countly.sharedInstance().showFeedbackPopup(widgetId, "Close", activity, new CountlyStarRating.FeedbackRatingCallback() {
            @Override
            public void callback(String error) {
                if(error != null){
                    Toast.makeText(activity, "Encountered error while showing feedback dialog: [" + error + "]", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    public void onClickViewOther03(View v) {
        Countly.sharedInstance().changeDeviceId(DeviceId.Type.DEVELOPER_SUPPLIED, "New Device ID" + (new Random().nextInt()));
    }

    public void onClickViewOther04(View v) {
        Countly.sharedInstance().changeDeviceId("New Device ID!" + (new Random().nextInt()));
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

    public void onClickViewOther09(View v) {
        //Entering temporary id mode
        Countly.sharedInstance().enableTemporaryIdMode();
    }

    public void onClickViewOther10(View v) {
        //Doing internally stored requests
        Countly.sharedInstance().doStoredRequests();
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
