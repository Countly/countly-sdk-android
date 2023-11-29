package ly.count.android.benchmark;

import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import com.android.installreferrer.api.InstallReferrerClient;
import com.android.installreferrer.api.InstallReferrerStateListener;
import com.android.installreferrer.api.ReferrerDetails;
import java.net.URLDecoder;
import java.util.concurrent.CompletableFuture;
import ly.count.android.sdk.Countly;

public class MainActivity extends AppCompatActivity {

    InstallReferrerClient referrerClient;

    Benchmark benchmark;

    int loop = 10;
    int segmentSize = 0;
    int eventSize = 0;

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

    public void onHardKillPressed(View v) {
        int id = android.os.Process.myPid();
        android.os.Process.killProcess(id);
    }

    public void onClickFillRequestQueue(View v) {
        loop = Integer.parseInt(((EditText) findViewById(R.id.loop)).getText().toString());
        segmentSize = Integer.parseInt(((EditText) findViewById(R.id.segmentSize)).getText().toString());
        eventSize = Integer.parseInt(((EditText) findViewById(R.id.eventSize)).getText().toString());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            CompletableFuture.runAsync(() -> {
                try {
                    benchmark.fillRequestQueue(loop, eventSize, segmentSize);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }

    public void onClickBenchmark(View v) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            CompletableFuture.runAsync(() -> {
                try {
                    scenario_A(loop, eventSize, segmentSize);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }

    protected void scenario_A(int loop, int eventSize, int segmentSize) {
        benchmark.print("------------------------------------------------------------");
        benchmark.print("[Benchmark] scenario_A");
        benchmark.print("------------------------------------------------------------");
        benchmark.print("[Benchmark] scenario_A: rqSize: " + Benchmark.countlyStore.getRequests().length);

        benchmark.print("[Benchmark] scenario_A: loop: " + loop + ", events size: " + eventSize + ", segment size: " + segmentSize);
        benchmark.print("[Benchmark] scenario_A: Triggering sending");
        long startTime = System.currentTimeMillis();
        Countly.sharedInstance().requestQueue().attemptToSendStoredRequests();
        while (Benchmark.countlyStore.getRequests().length > 0) ; // wait for RQ to finish sending
        long endTime = System.currentTimeMillis();
        benchmark.print("[Benchmark] scenario_A: SENDING TOOK: " + (endTime - startTime) + "MS");
        benchmark.print("------------------------------------------------------------");
    }
}