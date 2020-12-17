package ly.count.android.demo;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import java.util.HashMap;

import ly.count.android.sdk.Countly;


@SuppressWarnings("UnusedParameters")
public class MainActivity extends AppCompatActivity {
    private String demoTag = "CountlyDemo";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Countly.onCreate(this);
    }

    public void onClickButtonCustomEvents(View v) {
        startActivity(new Intent(this, ActivityExampleCustomEvents.class));
    }

    public void onClickButtonCrashReporting(View v) {
        startActivity(new Intent(this, ActivityExampleCrashReporting.class));
    }

    public void onClickButtonUserDetails(View v) {
        startActivity(new Intent(this, ActivityExampleUserDetails.class));
    }

    public void onClickButtonAPM(View v) {
        startActivity(new Intent(this, ActivityExampleAPM.class));
    }

    public void onClickButtonViewTracking(View v) {
        startActivity(new Intent(this, ActivityExampleViewTracking.class));
    }

    public void onClickButtonMultiThreading(View v) {
        //
    }

    public void onClickButtonOthers(View v) {
        startActivity(new Intent(this, ActivityExampleOthers.class));
    }

    public void onClickButtonRemoteConfig(View v) {
        startActivity(new Intent(this, ActivityExampleRemoteConfig.class));
    }

    public void onClickButtonDeviceId(View v) {
        startActivity(new Intent(this, ActivityExampleDeviceId.class));
    }

    public void onClickButtonRatings(View v) {
        startActivity(new Intent(this, ActivityExampleFeedback.class));
    }

    public void onClickButtonReferrer(View v) {
        startActivity(new Intent(this, ActivityExampleReferrer.class));
    }


    public void enableCrashTracking(){
        //add some custom segments, like dependency library versions
        HashMap<String, String> data = new HashMap<>();
        data.put("Facebook", "3.5");
        data.put("Admob", "6.5");
        Countly.sharedInstance().setCustomCrashSegments(data);
        Countly.sharedInstance().enableCrashReporting();
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
