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

    protected Map<String, String> generateRequest(int eventSize, int segmentSize) throws JSONException {
        return generateRequest(eventSize, segmentSize, null);
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

    private Map<String, Object> generateSegmentationMap(int segmentSize) {
        Map<String, Object> segment = new ConcurrentHashMap<>();

        for (int i = 0; i < segmentSize; i++) {
            Map.Entry<String, Object> entry = random.generateRandomKeyValuePair();
            segment.put(entry.getKey(), entry.getValue());
        }

        return segment;
    }

    protected JSONObject generateRandomEvent(int segmentSize) throws JSONException {
        JSONObject event = new JSONObject();
        event.put("key", random.generateRandomString(8));
        event.put("count", random.generateRandomInt(1000) + 1);
        event.put("sum", random.generateRandomDouble() * 1000);
        event.put("dur", random.generateRandomInt(1000) + 1);
        UtilsTime.Instant instant = UtilsTime.getCurrentInstant();
        event.put("timestamp", instant.timestampMs);
        event.put("hour", instant.hour);
        event.put("dow", instant.dow);

        if (segmentSize > 0) {
            Map<String, Object> segment = generateSegmentationMap(segmentSize);
            event.put("segment", segment);
        }

        return event;
    }
}
