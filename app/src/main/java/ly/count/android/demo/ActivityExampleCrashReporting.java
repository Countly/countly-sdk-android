package ly.count.android.demo;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

import java.io.IOException;

import ly.count.sdk.android.Countly;
import ly.count.sdk.android.Config;
import ly.count.sdk.internal.ModuleCrash;

/**
 * Demo Activity explaining {@link Config.Feature#CrashReporting}. Each {@code onClick}
 * method crashes application in a specific way. Countly is set up to report any crashes
 * occurring to the server, so after several seconds these crashes should appear on your
 * Countly dashboard.
 */

public class ActivityExampleCrashReporting extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_crash_reporting);
    }

    public void onClickNullPointer(View v) {
        ModuleCrash.crashTest(ModuleCrash.CrashType.NULLPOINTER_EXCEPTION);
    }

    public void onClickStackOverflow(View v) {
        ModuleCrash.crashTest(ModuleCrash.CrashType.STACK_OVERFLOW);
    }

    public void onClickOutOfMemory(View v) {
        ModuleCrash.crashTest(ModuleCrash.CrashType.OOM);
    }

    public void onClickDivisionByZero(View v) {
        ModuleCrash.crashTest(ModuleCrash.CrashType.DIVISION_BY_ZERO);
    }

    public void onClickKill(View v) {
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    public void onClickCustomFatal(View v) {
        Countly.session(this).addCrashReport(new RuntimeException("Fatal Exception"), true);
    }

    public void onClickCustomNonFatal(View v) {
        Countly.session(this).addCrashReport(new IOException("Non-Fatal Exception"), false);
    }
}
