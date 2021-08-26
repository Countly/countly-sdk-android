package ly.count.android.demo;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import ly.count.android.sdk.Countly;

public class ActivityExampleAPM extends AppCompatActivity {

    int[] successCodes = new int[] { 100, 101, 200, 201, 202, 205, 300, 301, 303, 305 };
    int[] failureCodes = new int[] { 400, 402, 405, 408, 500, 501, 502, 505 };

    Random rnd = new Random();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_apm);
        Countly.onCreate(this);
    }

    @Override
    public void onStart() {
        super.onStart();
        Countly.sharedInstance().onStart(this);
    }

    @Override
    public void onStop() {
        Countly.sharedInstance().onStop();
        super.onStop();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Countly.sharedInstance().onConfigurationChanged(newConfig);
    }

    public void onClickStartTrace_1(View v) {
        Countly.sharedInstance().apm().startTrace("Some_trace_key_1");
    }

    public void onClickStartTrace_2(View v) {
        Countly.sharedInstance().apm().startTrace("another key_1");
    }

    public void onClickEndTrace_1(View v) {
        Map<String, Integer> customMetric = new HashMap();
        customMetric.put("ABC", 1233);
        customMetric.put("C44C", 1337);

        Countly.sharedInstance().apm().endTrace("Some_trace_key_1", customMetric);
    }

    public void onClickEndTrace_2(View v) {
        Countly.sharedInstance().apm().endTrace("another key_1", null);
    }

    public void onClickStartNetworkTrace_1(View v) {
        Countly.sharedInstance().apm().startNetworkRequest("api/endpoint.1", "1337");
    }

    public void onClickEndNetworkTrace_1(View v) {
        // network trace of a succeeding request
        int requestBytes = rnd.nextInt(700) + 200;
        int responseBytes = rnd.nextInt(700) + 200;

        int responseCode = successCodes[rnd.nextInt(successCodes.length)];

        Countly.sharedInstance().apm().endNetworkRequest("api/endpoint.1", "1337", responseCode, requestBytes, responseBytes);
    }

    public void onClickStartNetworkTrace_2(View v) {
        Countly.sharedInstance().apm().startNetworkRequest("api/endpoint.1", "7331");
    }

    public void onClickEndNetworkTrace_2(View v) {
        // network trace of a failing request
        int requestBytes = rnd.nextInt(700) + 250;
        int responseBytes = rnd.nextInt(700) + 250;

        int responseCode = failureCodes[rnd.nextInt(failureCodes.length)];

        Countly.sharedInstance().apm().endNetworkRequest("api/endpoint.1", "7331", responseCode, requestBytes, responseBytes);
    }
}
