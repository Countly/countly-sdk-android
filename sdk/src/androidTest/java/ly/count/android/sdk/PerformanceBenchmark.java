package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.mockito.Mockito.mock;

@RunWith(AndroidJUnit4.class)
public class PerformanceBenchmark {

    private final RandomUtil random = new RandomUtil();

    @Before
    public void setUp() {
        final CountlyStore countlyStore = new CountlyStore(getContext(), mock(ModuleLog.class));
        countlyStore.clear();
    }

    @Test
    public void dummy() {

    }

    private Map<String, String> generateRequest(int eventSize, int segmentSize, Map<String, String> additionalParams) throws JSONException {
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
            Map<String, Object> segment = new ConcurrentHashMap<>();
            for (int i = 0; i < segmentSize; i++) {
                segment.put(random.generateRandomString(8), random.generateRandomObject());
            }
            event.put("segment", segment);
        }

        return event;
    }
}
