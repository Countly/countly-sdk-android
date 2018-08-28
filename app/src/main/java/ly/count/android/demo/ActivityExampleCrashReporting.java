package ly.count.android.demo;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

import junit.framework.Assert;

import ly.count.android.sdk.Countly;

@SuppressWarnings("UnusedParameters")
public class ActivityExampleCrashReporting extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_crash_reporting);
        Countly.onCreate(this);

    }

    @SuppressWarnings("unused")
    void EmptyFunction_1(){
        //keep this here, it's for proguard testing
    }

    @SuppressWarnings("unused")
    void EmptyFunction_2(){
        //keep this here, it's for proguard testing
    }
    @SuppressWarnings("unused")
    void EmptyFunction_3(){
        //keep this here, it's for proguard testing
    }


    public void onClickCrashReporting01(View v) {
        Countly.sharedInstance().addCrashBreadcrumb("Unrecognized selector crash");
    }

    public void onClickCrashReporting02(View v) {
        Countly.sharedInstance().addCrashBreadcrumb("Out of bounds crash");
        //noinspection MismatchedReadAndWriteOfArray
        int[] data = new int[]{};
        data[0] = 9;
    }

    public void onClickCrashReporting03(View v) {
        Countly.sharedInstance().addCrashBreadcrumb("Null pointer crash");
        Countly.sharedInstance().crashTest(3);
    }

    public void onClickCrashReporting04(View v) {
        Countly.sharedInstance().addCrashBreadcrumb("Invalid Geometry crash");
    }

    public void onClickCrashReporting05(View v) {
        Countly.sharedInstance().addCrashBreadcrumb("Assert fail crash");
        Assert.assertEquals(1, 0);
    }

    public void onClickCrashReporting06(View v) {
        Countly.sharedInstance().addCrashBreadcrumb("Kill process crash");
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    public void onClickCrashReporting07(View v) {
        Countly.sharedInstance().addCrashBreadcrumb("Custom crash log crash");
        Countly.sharedInstance().addCrashBreadcrumb("Adding some custom crash log");
        Countly.sharedInstance().crashTest(2);
    }

    public void onClickCrashReporting08(View v) {
        Countly.sharedInstance().addCrashBreadcrumb("Recording handled exception 1");
        Countly.sharedInstance().recordHandledException(new Exception("A logged exception"));
        Countly.sharedInstance().addCrashBreadcrumb("Recording handled exception 3");
    }

    public void onClickCrashReporting09(View v) throws Exception {
        Countly.sharedInstance().addCrashBreadcrumb("Unhandled exception info");
        throw new Exception("A unhandled exception");
    }

    public void onClickCrashReporting13(View v){
        String largeCrumb = "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd";
        Countly.sharedInstance().addCrashBreadcrumb(largeCrumb);
    }

    public void onClickCrashReporting10(View v) throws Exception {
        deepFunctionCall_1();
    }

    public void onClickCrashReporting11(View v) throws Exception {
        recursiveDeepCall(3);
    }

    void deepFunctionCall_1() throws Exception{
        deepFunctionCall_2();
    }

    void deepFunctionCall_2() throws Exception{
        deepFunctionCall_3();
    }

    void deepFunctionCall_3() throws Exception{
        Utility.DeepCall_a();
    }

    void recursiveDeepCall(int depthLeft) {
        if(depthLeft > 0){
            recursiveDeepCall(depthLeft - 1);
        } else {
            Utility.AnotherRecursiveCall(3);
        }
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
