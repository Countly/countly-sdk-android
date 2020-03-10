package ly.count.android.demo;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.util.Random;
import java.util.regex.Pattern;

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
        Countly.sharedInstance().recordUnhandledException(new Throwable("A really secret exception"));
    }

    public void onClickRemoveAllConsent(View v){
        Countly.sharedInstance().removeConsentAll();
    }

    public void onClickGiveAllConsent(View v){
        Countly.sharedInstance().consent().giveConsentAll();
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
