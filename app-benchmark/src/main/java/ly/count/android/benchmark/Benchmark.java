package ly.count.android.benchmark;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import ly.count.android.sdk.Countly;
import ly.count.android.sdk.CountlyStore;
import org.json.JSONException;

public class Benchmark {
    private final BenchmarkUtil benchmarkUtil;
    protected static CountlyStore countlyStore;

    protected Benchmark() {
        benchmarkUtil = new BenchmarkUtil();
    }

    public void fillRequestQueue(int rqSize, int eventSize, int segmentSize) {
        Countly.sharedInstance().requestQueue().flushQueues();
        print("[Benchmark] fillRequestQueue, Filling request queue, rq is flushed");
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
        print("[Benchmark] fillRequestQueue, Request queue size: " + countlyStore.getRequests().length);
    }

    public void fillEventQueue(int eventSize, int segmentSize) {
        Countly.sharedInstance().requestQueue().flushQueues();
        print("[Benchmark] fillEventQueue, Filling event queue, rq is flushed");
        for (int i = 0; i < eventSize; i++) {
            Object[] randomEvent = benchmarkUtil.generateRandomEventBase(segmentSize);
            Countly.sharedInstance().events().recordEvent(randomEvent[0].toString(), (Map<String, Object>) randomEvent[1], (int) randomEvent[2], (double) randomEvent[3], (double) randomEvent[4]);
        }
        print("[Benchmark] fillEventQueue, Request queue size: " + countlyStore.getRequests().length);
    }

    protected void print(String message) {
        System.err.println(message);
    }
}