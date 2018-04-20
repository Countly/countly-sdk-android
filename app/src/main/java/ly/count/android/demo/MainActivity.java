package ly.count.android.demo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import java.util.HashMap;

import ly.count.android.sdk.Countly;


@SuppressWarnings("UnusedParameters")
public class MainActivity extends Activity {
    private String demoTag = "CountlyDemo";

    /** You should use try.count.ly instead of YOUR_SERVER for the line below if you are using Countly trial service */
    final String COUNTLY_SERVER_URL = "YOUR_SERVER";
    final String COUNTLY_APP_KEY = "YOUR_APP_KEY";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Context appC = getApplicationContext();

        Countly.onCreate(this);
        Countly.sharedInstance().setLoggingEnabled(true);
        Countly.sharedInstance().enableCrashReporting();
        Countly.sharedInstance().setViewTracking(true);
        Countly.sharedInstance().setAutoTrackingUseShortName(true);
        Countly.sharedInstance().setRequiresConsent(false);
        Countly.sharedInstance().setConsent(new String[]{Countly.CountlyFeatureNames.sessions}, true);
        //Countly.sharedInstance().setHttpPostForced(true);
        //Log.i(demoTag, "Before calling init. This should return 'false', the value is:" + Countly.sharedInstance().isInitialized());
        Countly.sharedInstance().init(appC, COUNTLY_SERVER_URL, COUNTLY_APP_KEY);
        //Log.i(demoTag, "After calling init. This should return 'true', the value is:" + Countly.sharedInstance().isInitialized());
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
        //
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

}
