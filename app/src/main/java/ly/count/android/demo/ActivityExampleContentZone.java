package ly.count.android.demo;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import java.util.UUID;
import ly.count.android.sdk.Countly;

public class ActivityExampleContentZone extends AppCompatActivity {

    Activity activity;
    EditText deviceIdEditText;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        activity = this;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_content_zone);
        deviceIdEditText = findViewById(R.id.editTextDeviceIdContentZone);
    }

    public void onClickEnterContentZone(View v) {
        Countly.sharedInstance().contents().enterContentZone();
    }

    public void onClickExitContentZone(View v) {
        Countly.sharedInstance().contents().exitContentZone();
    }

    public void onClickRefreshContentZone(View v) {
        Countly.sharedInstance().contents().refreshContentZone();
    }

    public void onClickChangeDeviceIdContentZone(View v) {
        String deviceId = deviceIdEditText.getText().toString();
        String newDeviceId = deviceId.isEmpty() ? UUID.randomUUID().toString() : deviceId;

        Countly.sharedInstance().deviceId().setID(newDeviceId);
    }
}
