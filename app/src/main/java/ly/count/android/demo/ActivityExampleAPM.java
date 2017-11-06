package ly.count.android.demo;

import android.app.Activity;
import android.os.Bundle;

/**
 * Created by Arturs on 21.12.2016..
 */

public class ActivityExampleAPM extends Activity {
    Activity activity;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        activity = this;
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
