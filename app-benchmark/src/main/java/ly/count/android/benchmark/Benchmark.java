package ly.count.android.benchmark;

import android.util.Pair;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import ly.count.android.sdk.Countly;
import org.json.JSONException;

public class Benchmark {
    private final int[] loops = { 10, 100, 1000, 10000 };
    private final Pair<Integer, Integer>[] eventSize;

    private final BenchmarkUtil benchmarkUtil;

    protected Benchmark() {
        benchmarkUtil = new BenchmarkUtil();
        eventSize = new Pair[] {
            new Pair<>(0, 0), new Pair<>(1, 3), new Pair<>(100, 50), new Pair<>(1000, 100)
        };
    }

    protected void scenario_A() {
        print("------------------------------------------------------------");
        print("[Benchmark] scenario_A");
        print("------------------------------------------------------------");
        for (int loop : loops) {
            for (Pair<Integer, Integer> event : eventSize) {
                print("[Benchmark] scenario_A: loop: " + loop + ", events size: " + event.first + ", segment size: " + event.second);
                print("[Benchmark] scenario_A: Filling request queue");
                fillRequestQueue(loop, event.first, event.second);
                print("[Benchmark] scenario_A: Triggering sending");
                long time = System.nanoTime();
                Countly.sharedInstance().requestQueue().attemptToSendStoredRequests();
                time = System.nanoTime() - time;
                print("[Benchmark] scenario_A: Sending took: " + time / 1000000 + "ms");
                print("------------------------------------------------------------");
            }
        }
    }

    private void fillRequestQueue(int rqSize, int eventSize, int segmentSize) {
        for (int i = 0; i < rqSize; i++) {
            Map<String, String> additionalParams = null;
            if (eventSize < 1) {
                additionalParams = new ConcurrentHashMap<>();
                additionalParams.put("number", "1");
            }
            try {
                Map<String, String> request = benchmarkUtil.generateRequest(eventSize, segmentSize, additionalParams);
                Countly.sharedInstance().requestQueue().addDirectRequest(request);
            } catch (JSONException e) {
                print("[Benchmark] fillRequestQueue, Failed to generate request: " + e);
            }
        }
    }

    protected void print(String message) {
        System.out.println(message);
    }
}
