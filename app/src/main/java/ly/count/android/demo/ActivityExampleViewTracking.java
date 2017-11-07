package ly.count.android.demo;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

import ly.count.android.sdk.Countly;

/**
 * Created by Arturs on 21.12.2016..
 */

public class ActivityExampleViewTracking extends Activity {
    private static final String VIEW_NAME = "Awesome View";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_view_tracking);
    }

    public void onClickCustomViewStart(View v) {
        Countly.session(this).view(VIEW_NAME).start(false);
        findViewById(R.id.btnCustomViewStart).setEnabled(false);
        findViewById(R.id.btnCustomViewStop).setEnabled(true);
    }

    public void onClickCustomViewStop(View v) {
        Countly.session(this).view(VIEW_NAME).start(false);
        findViewById(R.id.btnCustomViewStop).setEnabled(false);
    }
}
