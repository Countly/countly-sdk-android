package ly.count.android.sdk;

import java.util.HashMap;
import java.util.Map;

public class PerformanceCounterCollector {
    HashMap<String, Double> perfCounter = new HashMap<String, Double>();

    public void Clear() {
        perfCounter.clear();
    }

    public void TrackCounter(String key, long value) {
        TrackCounter(key, value / 1000.0);
    }

    public void TrackCounter(String key, double value) {
        Double retrievedValue = perfCounter.get(key);

        if (retrievedValue == null) {
            retrievedValue = 0.0;
        }

        retrievedValue += value;

        perfCounter.put(key, retrievedValue);
    }

    public String ReturnResults() {
        StringBuilder res = new StringBuilder();

        for (Map.Entry<String, Double> entry : perfCounter.entrySet()) {
            String key = entry.getKey();
            Double value = entry.getValue();

            res.append(key).append(" - ").append(value).append("\n");
        }

        return res.toString();
    }
}
