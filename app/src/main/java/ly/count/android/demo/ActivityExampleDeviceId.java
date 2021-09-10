package ly.count.android.demo;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import java.util.Random;

import ly.count.android.sdk.Countly;
import ly.count.android.sdk.DeviceId;

public class ActivityExampleDeviceId extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_device_id);
        Countly.onCreate(this);
    }

    public void onClickDeviceId01(View v) {
        //set device id without merge
        Countly.sharedInstance().changeDeviceIdWithoutMerge("New Device ID" + (new Random().nextInt()));
    }

    public void onClickDeviceId02(View v) {
        //set device id with merge
        Countly.sharedInstance().changeDeviceIdWithMerge("New Device ID!" + (new Random().nextInt()));
    }

    public void onClickDeviceId03(View v) {
        //Entering temporary id mode
        Countly.sharedInstance().enableTemporaryIdMode();
    }

    @Override
    public void onStart() {
        super.onStart();
        Countly.sharedInstance().onStart(this);
    }

    @Override
    public void onStop() {
        Countly.sharedInstance().onStop();
        super.onStop();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Countly.sharedInstance().onConfigurationChanged(newConfig);
    }
}
