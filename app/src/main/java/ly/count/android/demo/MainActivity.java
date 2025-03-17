package ly.count.android.demo;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
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

        /*
        To send Referrer follow these steps.
        Steps:
        1. Start Google Play on the device using campaign link,
        for example, https://play.google.com/store/apps/details?id=ly.count.android.demo&referrer=utm_source%3Dtest_source%26utm_medium%3Dtest_medium%26
        utm_term%3Dtest-term%26utm_content%3Dtest_content%26utm_campaign%3Dtest_name
        (You can use google play generator: https://developers.google.com/analytics/devguides/collection/android/v3/campaigns#google-play-url-builder)
        2. DON'T TAP ON INSTALL BUTTON
        3. Install your test build using adb.

        Google Play will be returning your test campaign now.
         */
        referrerClient = InstallReferrerClient.newBuilder(this).build();
        referrerClient.startConnection(new InstallReferrerStateListener() {
            @Override
            public void onInstallReferrerSetupFinished(int responseCode) {
                switch (responseCode) {
                    case InstallReferrerClient.InstallReferrerResponse.OK:
                        try {
                            ReferrerDetails response = referrerClient.getInstallReferrer();

                            //you would retrieve the referrer url
                            String referrerUrl = response.getInstallReferrer();

                            //and then you would parse it and retrieve the required field to identify the campaign id and user id
                            String campaignId = "someCampaignId";
                            String userId = "someUserId";

                            // The string is usually URL Encoded, so we need to decode it.
                            String referrer = URLDecoder.decode(referrerUrl, "UTF-8");

                            // Log the referrer string.
                            Log.d(Countly.TAG, "Received Referrer url: " + referrer);

                            //retrieve specific parts from the referrer url
                            String[] parts = referrer.split("&");
                            for (String part : parts) {
                                if (part.startsWith("countly_cid")) {
                                    campaignId = part.replace("countly_cid=", "").trim();
                                }
                                if (part.startsWith("countly_cuid")) {
                                    userId = part.replace("countly_cuid=", "").trim();
                                }
                            }

                            //you would then pass those retrieved values as manual attribution:
                            Countly.sharedInstance().attribution().recordDirectAttribution("countly", "{\"cid\":" + campaignId + ",\"cuid\":" + userId + "}");
                            //Countly.sharedInstance().attribution().recordDirectAttribution(campaignId, userId);

                            referrerClient.endConnection();
                        } catch (Exception e) {
                            Log.e(demoTag, "Error while parsing referrer", e);
                        }
                        break;
                    case InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED:
                        // API not available on the current Play Store app.
                        break;
                    case InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE:
                        // Connection couldn't be established.
                        break;
                    default:
                        break;
                }
            }

            @Override
            public void onInstallReferrerServiceDisconnected() {
            }
        });

        //ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.BROADCAST_CLOSE_SYSTEM_DIALOGS }, 123);
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

    public void onClickButtonMultiThreading(View v) {
        //
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
}
