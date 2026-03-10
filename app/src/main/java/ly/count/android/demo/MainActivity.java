package ly.count.android.demo;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.android.installreferrer.api.InstallReferrerClient;
import com.android.installreferrer.api.InstallReferrerStateListener;
import com.android.installreferrer.api.ReferrerDetails;
import java.net.URLDecoder;
import ly.count.android.sdk.Countly;

@SuppressWarnings("UnusedParameters")
public class MainActivity extends AppCompatActivity {
    private final static String demoTag = "CountlyDemo";
    InstallReferrerClient referrerClient;

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

        referrerClient = InstallReferrerClient.newBuilder(this).build();
        referrerClient.startConnection(new InstallReferrerStateListener() {
            @Override
            public void onInstallReferrerSetupFinished(int responseCode) {
                switch (responseCode) {
                    case InstallReferrerClient.InstallReferrerResponse.OK:
                        try {
                            ReferrerDetails response = referrerClient.getInstallReferrer();
                            String referrerUrl = response.getInstallReferrer();
                            String campaignId = "someCampaignId";
                            String userId = "someUserId";
                            String referrer = URLDecoder.decode(referrerUrl, "UTF-8");
                            Log.d(Countly.TAG, "Received Referrer url: " + referrer);
                            String[] parts = referrer.split("&");
                            for (String part : parts) {
                                if (part.startsWith("countly_cid")) {
                                    campaignId = part.replace("countly_cid=", "").trim();
                                }
                                if (part.startsWith("countly_cuid")) {
                                    userId = part.replace("countly_cuid=", "").trim();
                                }
                            }
                            Countly.sharedInstance().attribution().recordDirectAttribution("countly", "{\"cid\":" + campaignId + ",\"cuid\":" + userId + "}");
                            referrerClient.endConnection();
                        } catch (Exception e) {
                            Log.e(demoTag, "Error while parsing referrer", e);
                        }
                        break;
                    case InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED:
                        break;
                    case InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE:
                        break;
                    default:
                        break;
                }
            }

            @Override
            public void onInstallReferrerServiceDisconnected() {
            }
        });
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
}
