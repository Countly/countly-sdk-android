package ly.count.android.demo;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import java.util.Random;

import ly.count.android.sdk.Countly;

public class ActivityExampleDeviceId extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_device_id);
    }

    public void onClickDeviceId01(View v) {
        //set device id without merge
        Countly.sharedInstance().deviceId().changeWithoutMerge("New Device ID" + (new Random().nextInt()));

        //give all consents after changing device ID without merging (that removes all consent)
        Countly.sharedInstance().consent().giveConsentAll();
    }

    public void onClickDeviceId02(View v) {
        //set device id with merge
        Countly.sharedInstance().deviceId().changeWithMerge("New Device ID!" + (new Random().nextInt()));
    }

    public void onClickDeviceId03(View v) {
        //Entering temporary id mode
        Countly.sharedInstance().deviceId().enableTemporaryIdMode();
        //give all consents after entering temporary device ID mode (that removes all consent)
        Countly.sharedInstance().consent().giveConsentAll();
    }
}
