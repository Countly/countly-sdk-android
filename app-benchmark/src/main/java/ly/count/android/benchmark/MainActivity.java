package ly.count.android.benchmark;

import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Switch;
import androidx.appcompat.app.AppCompatActivity;
import com.android.installreferrer.api.InstallReferrerClient;
import com.android.installreferrer.api.InstallReferrerStateListener;
import com.android.installreferrer.api.ReferrerDetails;
import java.net.URLDecoder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
                            Countly.sharedInstance().L.e("[MainActivity] onInstallReferrerSetupFinished, Failed to get install referrer", e);
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

    public void onSchedulerPressed(View v) {
        readLoopSegmentEventSize();
        futureWrapper(() -> {
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
            // Schedule the task to run every 10 seconds
            scheduler.scheduleAtFixedRate(() -> {
                benchmark.fillRequestQueue(1, eventSize, segmentSize);
                BENCHMARK(1, Countly.sharedInstance().requestQueue()::attemptToSendStoredRequests);
            }, 0, 10, TimeUnit.SECONDS);
        });
    }

    public void onClickFillRequestQueue(View v) {
        readLoopSegmentEventSize();
        futureWrapper(() -> benchmark.fillRequestQueue(loop, eventSize, segmentSize));
        if (((Switch) findViewById(R.id.switch1)).isChecked()) {
            futureWrapper(this::standByForOnTimer);
        }
    }

    private void standByForOnTimer() {
        benchmark.print("[MainActivity] standByForOnTimer, wait is true, waiting for onTimer to be called");
        //while (Countly.sharedInstance().standBy.get()) ;
        benchmark.print("[MainActivity] standByForOnTimer, standBy is false, sending requests");
        BENCHMARK(loop, null);
    }

    public void onClickBenchmark(View v) {
        futureWrapper(() -> BENCHMARK(loop, Countly.sharedInstance().requestQueue()::attemptToSendStoredRequests));
    }

    protected void BENCHMARK(int loop, Runnable runnable) {
        benchmark.print("------------------------------------------------------------");
        benchmark.print("[MainActivity] BENCHMARK");
        benchmark.print("------------------------------------------------------------");
        benchmark.print("[MainActivity] BENCHMARK, rqSize: " + Benchmark.countlyStore.getRequests().length);

        benchmark.print("[MainActivity] BENCHMARK loop: " + loop + ", events size: " + eventSize + ", segment size: " + segmentSize);
        benchmark.print("[MainActivity] BENCHMARK Triggering sending");
        long startTime = System.currentTimeMillis();
        if (runnable != null) {
            runnable.run();
        }
        while (Benchmark.countlyStore.getRequests().length > 0) ; // wait for RQ to finish sending
        long endTime = System.currentTimeMillis();
        benchmark.print("[MainActivity] BENCHMARK, SENDING TOOK: " + (endTime - startTime) + "MS");
        benchmark.print("------------------------------------------------------------");
    }

    private void readLoopSegmentEventSize() {
        loop = Integer.parseInt(((EditText) findViewById(R.id.loop)).getText().toString());
        segmentSize = Integer.parseInt(((EditText) findViewById(R.id.segmentSize)).getText().toString());
        eventSize = Integer.parseInt(((EditText) findViewById(R.id.eventSize)).getText().toString());
    }

    private void futureWrapper(Runnable runnable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            CompletableFuture.runAsync(() -> {
                try {
                    runnable.run();
                } catch (Exception e) {
                    Countly.sharedInstance().L.e("[MainActivity] futureWrapper, Failed to run scheduler", e);
                }
            });
        }
    }
}