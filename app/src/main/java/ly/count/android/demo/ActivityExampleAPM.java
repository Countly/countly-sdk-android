package ly.count.android.demo;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;

import ly.count.android.sdk.Countly;

public class ActivityExampleAPM extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_apm);
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

    @Override
    public void onConfigurationChanged (Configuration newConfig){
        super.onConfigurationChanged(newConfig);
        Countly.sharedInstance().onConfigurationChanged(newConfig);
    }

    public void onClickStartTrace_1(View v) {
        Countly.sharedInstance().apm().startTrace("Some_trace_key_1");
    }

    public void onClickStartTrace_2(View v) {
        Countly.sharedInstance().apm().startTrace("another key_1");
    }

    public void onClickEndTrace_1(View v) {
        Countly.sharedInstance().apm().endTrace("Some_trace_key_1");
    }

    public void onClickEndTrace_2(View v) {
        Countly.sharedInstance().apm().endTrace("another key_1");
    }
}
