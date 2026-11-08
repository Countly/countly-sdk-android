package ly.count.android.demo;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import ly.count.android.sdk.Countly;

@SuppressWarnings("UnusedParameters")
public class MainActivity extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView txtVersion = findViewById(R.id.txtSdkVersion);
        txtVersion.setText("SDK v" + Countly.sharedInstance().COUNTLY_SDK_VERSION_STRING);

        ImageView btnNightMode = findViewById(R.id.btnToggleNightMode);
        btnNightMode.setOnClickListener(v -> {
            int currentMode = AppCompatDelegate.getDefaultNightMode();
            if (currentMode == AppCompatDelegate.MODE_NIGHT_YES) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            }
        });

        // The install referrer is read by the SDK on first launch and sent raw to the server, which parses
        // utm_*, cly_clk, countly_cid / countly_cuid and dl from it. See setDeepLinkCallback in App.
    }

    public void onClickButtonCustomEvents(View v) {
        startActivity(new Intent(this, ActivityExampleCustomEvents.class));
    }

    public void onClickButtonCrashReporting(View v) {
        startActivity(new Intent(this, ActivityExampleCrashReporting.class));
    }

    public void onClickButtonUserDetails(View v) {
        startActivity(new Intent(this, ActivityExampleUserDetails.class));
    }

    public void onClickButtonAPM(View v) {
        startActivity(new Intent(this, ActivityExampleAPM.class));
    }

    public void onClickButtonViewTracking(View v) {
        startActivity(new Intent(this, ActivityExampleViewTracking.class));
    }

    public void onClickButtonAutoViewTracking(View v) {
        startActivity(new Intent(this, ActivityExampleAutoViewTracking.class));
    }

    public void onClickButtonOthers(View v) {
        startActivity(new Intent(this, ActivityExampleOthers.class));
    }

    public void onClickButtonRemoteConfig(View v) {
        startActivity(new Intent(this, ActivityExampleRemoteConfig.class));
    }

    public void onClickButtonTests(View v) {
        startActivity(new Intent(this, ActivityExampleTests.class));
    }

    public void onClickButtonDeviceId(View v) {
        startActivity(new Intent(this, ActivityExampleDeviceId.class));
    }

    public void onClickButtonRatings(View v) {
        startActivity(new Intent(this, ActivityExampleFeedback.class));
    }

    public void onClickContentZone(View v) {
        startActivity(new Intent(this, ActivityExampleContentZone.class));
    }

    public void onClickButtonFragments(View v) {
        startActivity(new Intent(this, ActivityExampleFragments.class));
    }

    public void onClickButtonConsent(View v) {
        startActivity(new Intent(this, ActivityExampleConsent.class));
    }

    public void onClickButtonLocation(View v) {
        startActivity(new Intent(this, ActivityExampleLocation.class));
    }

    public void onClickButtonSessions(View v) {
        startActivity(new Intent(this, ActivityExampleSessions.class));
    }

    public void onClickButtonMultiInstance(View v) {
        startActivity(new Intent(this, ActivityExampleMultiInstance.class));
    }
}
