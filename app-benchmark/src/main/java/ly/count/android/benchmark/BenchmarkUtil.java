package ly.count.android.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import ly.count.android.sdk.UtilsTime;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class BenchmarkUtil {
    private final RandomUtil random;

    protected BenchmarkUtil() {
        random = new RandomUtil();
    }

    protected Map<String, String> generateRequest(int eventSize, int segmentSize, Map<String, String> additionalParams) throws JSONException {
        Map<String, String> request = new ConcurrentHashMap<>();
        List<JSONObject> events = new ArrayList<>();
        for (int i = 0; i < eventSize; i++) {
            events.add(generateRandomEvent(segmentSize));
        }

        if (!events.isEmpty()) {
            request.put("events", new JSONArray(events).toString());
        }

        if (additionalParams != null && !additionalParams.isEmpty()) {
            request.putAll(additionalParams);
        }

        return request;
    }

    protected Map<String, Object> generateSegmentationMap(int segmentSize) {
        Map<String, Object> segment = new ConcurrentHashMap<>();

        for (int i = 0; i < segmentSize; i++) {
            String key = random.generateRandomString(8);

            while (segment.containsKey(key)) {
                key = random.generateRandomString(8);
            }
            segment.put(key, random.generateRandomImmutable());
        }

        return segment;
    }

    protected Object[] generateRandomEventBase(int segmentSize) {
        Object[] values = new Object[5];
        values[0] = random.generateRandomString(8); // key
        values[1] = generateSegmentationMap(segmentSize); // segmentation
        values[2] = random.generateRandomInt(1000) + 1; // count
        values[3] = random.generateRandomDouble() * 1000; // sum
        values[4] = random.generateRandomDouble() * 1000; // dur
        return values;
    }

    protected JSONObject generateRandomEvent(int segmentSize) throws JSONException {
        JSONObject event = new JSONObject();
        Object[] values = generateRandomEventBase(segmentSize);
        event.put("key", values[0]);
        event.put("count", values[2]);
        event.put("sum", values[3]);
        event.put("dur", values[4]);
        UtilsTime.Instant instant = UtilsTime.getCurrentInstant();
        event.put("timestamp", instant.timestampMs);
        event.put("hour", instant.hour);
        event.put("dow", instant.dow);

        if (segmentSize > 0) {
            event.put("segment", values[1]);
        }

        return event;
    }
}
