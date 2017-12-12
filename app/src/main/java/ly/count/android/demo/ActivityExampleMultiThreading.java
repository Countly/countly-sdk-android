package ly.count.android.demo;

import android.app.Activity;
import android.os.Bundle;

import ly.count.android.sdk.Countly;

public class ActivityExampleMultiThreading extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_custom_events);
        Countly.onCreate(this);

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
