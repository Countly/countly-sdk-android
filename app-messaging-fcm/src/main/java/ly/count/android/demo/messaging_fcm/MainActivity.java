package ly.count.android.demo.messaging_fcm;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import ly.count.android.sdk.Countly;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Countly.onCreate(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();

        Countly.sharedInstance().onStart(this);
    }

    @Override
    protected void onStop() {
        super.onStop();

        Countly.sharedInstance().onStop();
    }
}
