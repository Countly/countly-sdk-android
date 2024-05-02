package ly.count.android.sdk;

import android.annotation.SuppressLint;
import android.os.Build;
import android.util.Log;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PerformanceCounterCollector {
    HashMap<String, Double> perfCounter = new HashMap<String, Double>();

    public void Clear() {
        perfCounter.clear();
    }

    public void TrackCounterTimeNs(String key, long valueNs) {
        TrackCounter(key, valueNs / 1_000_000_000.0);
    }

    public void TrackCounter(String key, double value) {
        assert Utils.isNotNullOrEmpty(key);

        if (value < 0) {
            Log.w("Countly", "Problem, we should only log positive values");
        }

        Double retrievedValue = perfCounter.get(key);

        if (retrievedValue == null) {
            retrievedValue = 0.0;
        }

        retrievedValue += value;

        perfCounter.put(key, retrievedValue);
    }

    public String ReturnResults() {
        List<String> entries = new ArrayList<>(perfCounter.size());

        //create all string entries
        for (Map.Entry<String, Double> entry : perfCounter.entrySet()) {
            String key = entry.getKey();
            Double value = entry.getValue();

            @SuppressLint("DefaultLocale")
            String strValue = String.format("%.6f", value);

            entries.add(key + " - " + strValue + "\n");
        }

        //sort if possible
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            entries.sort(String::compareTo);
        }

        //combine into printable String
        StringBuilder res = new StringBuilder();
        for (String s : entries) {
            res.append(s);
        }

        return res.toString();
    }
}
