package ly.count.android.demo;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

import java.util.HashMap;
import java.util.Map;

import ly.count.android.sdk.Countly;

/**
 * Demo Activity explaining how to record {@link ly.count.android.sdk.Event}s.
 */

public class ActivityExampleCustomEvents extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_custom_events);
    }

    public void onClickRecordEvent01(View v) {
        Countly.session(this).event("Custom event 1").record();
    }

    public void onClickRecordEvent02(View v) {
        Countly.session(this).event("Custom event 2").setCount(3).record();
    }

    public void onClickRecordEvent03(View v) {
        Countly.session(this).event("Custom event 3").setCount(3).setSum(134).record();
    }

    public void onClickRecordEvent04(View v) {
        Countly.session(this).event("Custom event 4").setCount(1).setDuration(55).record();
    }

    public void onClickRecordEvent05(View v) {
        Countly.session(this).event("Custom event 5").addSegment("wall", "green").record();
    }

    public void onClickRecordEvent06(View v) {
        Countly.session(this).event("Custom event 5").addSegment("wall", "red").setCount(15).record();
    }

    public void onClickRecordEvent07(View v) {
        Countly.session(this).event("Custom event 5").addSegment("wall", "blue").setCount(25).setSum(10).record();
    }

    public void onClickRecordEvent08(View v) {
        Countly.session(this).event("Custom event 5").addSegment("wall", "yellow").setCount(25).setSum(10).setDuration(50).record();
    }

    public void onClickRecordEvent09(View v) {
        Countly.session(this).timedEvent("Custom event 9");
    }

    public void onClickRecordEvent10(View v) {
        Countly.session(this).timedEvent("Custom event 9").endAndRecord();
    }

    public void onClickRecordEvent11(View v) {
        Countly.session(this).timedEvent("Custom event 9").addSegment("wall", "orange").setCount(4).setSum(34).endAndRecord();
    }
}
