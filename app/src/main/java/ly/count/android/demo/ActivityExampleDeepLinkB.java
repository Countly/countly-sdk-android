package ly.count.android.demo;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import ly.count.android.sdk.messaging.CountlyPush;

public class ActivityExampleDeepLinkB extends AppCompatActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_deep_link_b);

        Intent intent = getIntent();
        String action = intent.getAction();
        Uri data = intent.getData();

        Bundle bun = intent.getBundleExtra(CountlyPush.EXTRA_MESSAGE);
        CountlyPush.Message message = null;

        if (bun != null) {
            message = bun.getParcelable(CountlyPush.EXTRA_MESSAGE);
        }
        int actionIndex = intent.getIntExtra(CountlyPush.EXTRA_ACTION_INDEX, -100);
        Log.v("Countly", "Action index: " + actionIndex + " Data: " + data + " Action: " + action + " Message: " + message);
    }
}
