package ly.count.android.demo;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import com.android.installreferrer.api.InstallReferrerClient;
import com.android.installreferrer.api.InstallReferrerStateListener;
import com.android.installreferrer.api.ReferrerDetails;
import java.util.HashMap;
import ly.count.android.sdk.Countly;
import org.json.JSONObject;

public class ActivityExampleReferrer extends AppCompatActivity {
    InstallReferrerClient referrerClient;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_referrer);
        Countly.onCreate(this);

        referrerClient = InstallReferrerClient.newBuilder(this).build();
    }

    @Override
    public void onStart() {
        super.onStart();
        Countly.sharedInstance().onStart(this);
    }

    public void onClickSendReferrer
        (View v) {

        referrerClient.startConnection(new InstallReferrerStateListener() {
            @Override
            public void onInstallReferrerSetupFinished(int responseCode) {
                switch (responseCode) {
                    case InstallReferrerClient.InstallReferrerResponse.OK:
                        try {
                            ReferrerDetails response = referrerClient.getInstallReferrer();

                            String referrerUrl = response.getInstallReferrer();

                            HashMap<String, Object> custom = new HashMap<>();
                            custom.put("install_referrer", referrerUrl);
                            custom.put("referrer_click_timestamp_seconds", response.getReferrerClickTimestampSeconds());
                            custom.put("install_begin_timestamp_seconds", response.getInstallBeginTimestampSeconds());
                            custom.put("google_play_instant", response.getGooglePlayInstantParam());
                            custom.put("install_version", response.getInstallVersion());

                            JSONObject jsonObject = new JSONObject(custom);
                            Countly.userData.setProperty("custom_referrer", jsonObject.toString(1));
                            Countly.userData.save();

                            referrerClient.endConnection();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        break;
                    case InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED:
                        // API not available on the current Play Store app.
                        break;
                    case InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE:
                        // Connection couldn't be established.
                        break;
                }
            }

            @Override
            public void onInstallReferrerServiceDisconnected() {
            }
        });
    }


    @Override
    public void onStop() {
        Countly.sharedInstance().onStop();
        referrerClient.endConnection();
        super.onStop();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Countly.sharedInstance().onConfigurationChanged(newConfig);
    }
}
