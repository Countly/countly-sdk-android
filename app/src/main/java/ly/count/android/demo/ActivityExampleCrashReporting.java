package ly.count.android.demo;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import ly.count.android.sdk.Countly;

@SuppressWarnings({ "UnusedParameters", "ConstantConditions" })
public class ActivityExampleCrashReporting extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_crash_reporting);
    }

    @SuppressWarnings("unused")
    void EmptyFunction_1() {
        //keep this here, it's for proguard testing
    }

    @SuppressWarnings("unused")
    void EmptyFunction_2() {
        //keep this here, it's for proguard testing
    }

    @SuppressWarnings("unused")
    void EmptyFunction_3() {
        //keep this here, it's for proguard testing
    }

    public void onClickOutOfBounds(View v) {
        Countly.sharedInstance().crashes().addCrashBreadcrumb("Out of bounds crash");
        //noinspection MismatchedReadAndWriteOfArray
        int[] data = new int[] {};
        data[0] = 9;
    }

    public void onClickNullPointer(View v) {
        Countly.sharedInstance().crashes().addCrashBreadcrumb("Null pointer crash");

        Object[] o = null;
        o[0].getClass();
    }

    public void onClickCrashReporting06(View v) {
        Countly.sharedInstance().crashes().addCrashBreadcrumb("Kill process crash");
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    public void onClickDivZero(View v) {
        Countly.sharedInstance().crashes().addCrashBreadcrumb("Custom crash log crash");
        Countly.sharedInstance().crashes().addCrashBreadcrumb("Adding some custom crash log");

        //noinspection UnusedAssignment,divzero
        @SuppressWarnings("NumericOverflow") int test = 10 / 0;
    }

    public void onClickHandledException(View v) {
        Countly.sharedInstance().crashes().addCrashBreadcrumb("Recording handled exception 1");
        Countly.sharedInstance().crashes().recordHandledException(new Exception("A custom error text"));
        Countly.sharedInstance().crashes().addCrashBreadcrumb("Recording handled exception 3");
    }

    public void onClickUNhandledException(View v) throws Exception {
        Countly.sharedInstance().crashes().addCrashBreadcrumb("Unhandled exception info");
        throw new Exception("A unhandled exception");
    }

    public void onClickLargeBreadcrumb(View v) {
        String largeCrumb =
            "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd";
        Countly.sharedInstance().crashes().addCrashBreadcrumb(largeCrumb);
    }

    public void onClickDeepUnhandled(View v) throws Exception {
        deepFunctionCall_1();
    }

    public void onClickDeepHandled(View v) throws Exception {
        recursiveDeepCall(3);
    }

    void deepFunctionCall_1() throws Exception {
        deepFunctionCall_2();
    }

    void deepFunctionCall_2() throws Exception {
        deepFunctionCall_3();
    }

    void deepFunctionCall_3() throws Exception {
        Utility.DeepCall_a();
    }

    void recursiveDeepCall(int depthLeft) {
        if (depthLeft > 0) {
            recursiveDeepCall(depthLeft - 1);
        } else {
            Utility.AnotherRecursiveCall(3);
        }
    }
}
