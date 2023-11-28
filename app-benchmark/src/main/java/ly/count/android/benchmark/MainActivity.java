package ly.count.android.benchmark;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import com.android.installreferrer.api.InstallReferrerClient;
import com.android.installreferrer.api.InstallReferrerStateListener;
import com.android.installreferrer.api.ReferrerDetails;
import java.net.URLDecoder;
import ly.count.android.sdk.Countly;

public class MainActivity extends AppCompatActivity {

    InstallReferrerClient referrerClient;

    Benchmark benchmark;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        benchmark = new Benchmark();

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

    public void onClickBenchmark(View v) {
        benchmark.scenario_A();
    }
}