package ly.count.android.demo;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

import junit.framework.Assert;

/**
 * Created by Arturs on 21.12.2016..
 */

public class ActivityExampleCrashReporting extends Activity {
    Activity activity;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        activity = this;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_crash_reporting);
        Countly.onCreate(this);

    }

    public void onClickCrashReporting01(View v) {
        Countly.sharedInstance().addCrashLog("Crash log 1");
    }

    public void onClickCrashReporting02(View v) {
        Countly.sharedInstance().addCrashLog("Crash log 2");
        int[] data = new int[]{};
        data[0] = 9;
    }

    public void onClickCrashReporting03(View v) {
        Countly.sharedInstance().addCrashLog("Crash log 3");
        Countly.sharedInstance().crashTest(3);
    }

    public void onClickCrashReporting04(View v) {
        Countly.sharedInstance().addCrashLog("Crash log 4");
    }

    public void onClickCrashReporting05(View v) {
        Countly.sharedInstance().addCrashLog("Crash log 5");
        Assert.assertEquals(1, 0);
    }

    public void onClickCrashReporting06(View v) {
        Countly.sharedInstance().addCrashLog("Crash log 6");
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    public void onClickCrashReporting07(View v) {
        Countly.sharedInstance().addCrashLog("Crash log 7");
        Countly.sharedInstance().addCrashLog("Adding some custom crash log");
        Countly.sharedInstance().crashTest(1);
    }

    public void onClickCrashReporting08(View v) {
        Countly.sharedInstance().addCrashLog("Crash log 8");
        Countly.sharedInstance().logException(new Exception("A logged exception"));
    }

    public void onClickCrashReporting09(View v) throws Exception {
        Countly.sharedInstance().addCrashLog("Crash log 9");
        throw new Exception("A unhandled uxception");
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
