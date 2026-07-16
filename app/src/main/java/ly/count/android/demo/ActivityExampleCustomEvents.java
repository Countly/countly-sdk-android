package ly.count.android.demo;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.HashMap;
import java.util.Map;

import ly.count.android.sdk.Countly;

public class ActivityExampleCustomEvents extends AppCompatActivity {

    private TextInputEditText inputEventName, inputEventCount, inputEventSum, inputEventDuration;
    private TextInputEditText inputTimedEventName;
    private LinearLayout segmentationContainer;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_custom_events);

        inputEventName = findViewById(R.id.inputEventName);
        inputEventCount = findViewById(R.id.inputEventCount);
        inputEventSum = findViewById(R.id.inputEventSum);
        inputEventDuration = findViewById(R.id.inputEventDuration);
        inputTimedEventName = findViewById(R.id.inputTimedEventName);
        segmentationContainer = findViewById(R.id.segmentationContainer);

        findViewById(R.id.btnAddSegment).setOnClickListener(v -> addSegmentRow());
        findViewById(R.id.btnRecordEvent).setOnClickListener(v -> recordCustomEvent());
        findViewById(R.id.btnStartTimedEvent).setOnClickListener(v -> startTimedEvent());
        findViewById(R.id.btnEndTimedEvent).setOnClickListener(v -> endTimedEvent());
        findViewById(R.id.btnCancelTimedEvent).setOnClickListener(v -> cancelTimedEvent());
        findViewById(R.id.btnPreset1).setOnClickListener(v -> recordPresetWithSegmentation());
        findViewById(R.id.btnPreset2).setOnClickListener(v -> recordPresetWithCountSum());
        findViewById(R.id.btnTriggerSend).setOnClickListener(v -> {
            Countly.sharedInstance().requestQueue().attemptToSendStoredRequests();
            Toast.makeText(this, "Sending stored requests", Toast.LENGTH_SHORT).show();
        });

        // Add one default segment row
        addSegmentRow();
    }

    private void addSegmentRow() {
        View row = LayoutInflater.from(this).inflate(R.layout.row_segmentation, segmentationContainer, false);
        row.findViewById(R.id.btnRemoveSegment).setOnClickListener(v -> segmentationContainer.removeView(row));
        segmentationContainer.addView(row);
    }

    private Map<String, Object> collectSegmentation() {
        Map<String, Object> segmentation = new HashMap<>();
        for (int i = 0; i < segmentationContainer.getChildCount(); i++) {
            View row = segmentationContainer.getChildAt(i);
            EditText keyField = row.findViewById(R.id.inputSegKey);
            EditText valueField = row.findViewById(R.id.inputSegValue);
            String key = keyField.getText().toString().trim();
            String value = valueField.getText().toString().trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                // Try to parse as number
                try {
                    if (value.contains(".")) {
                        segmentation.put(key, Double.parseDouble(value));
                    } else {
                        segmentation.put(key, Integer.parseInt(value));
                    }
                } catch (NumberFormatException e) {
                    segmentation.put(key, value);
                }
            }
        }
        return segmentation;
    }

    private void recordCustomEvent() {
        String eventName = inputEventName.getText() != null ? inputEventName.getText().toString().trim() : "";
        if (eventName.isEmpty()) {
            inputEventName.setError("Event name is required");
            return;
        }

        int count = 1;
        double sum = 0;
        double duration = 0;

        try {
            String countStr = inputEventCount.getText() != null ? inputEventCount.getText().toString().trim() : "";
            if (!countStr.isEmpty()) count = Integer.parseInt(countStr);
        } catch (NumberFormatException ignored) {}

        try {
            String sumStr = inputEventSum.getText() != null ? inputEventSum.getText().toString().trim() : "";
            if (!sumStr.isEmpty()) sum = Double.parseDouble(sumStr);
        } catch (NumberFormatException ignored) {}

        try {
            String durStr = inputEventDuration.getText() != null ? inputEventDuration.getText().toString().trim() : "";
            if (!durStr.isEmpty()) duration = Double.parseDouble(durStr);
        } catch (NumberFormatException ignored) {}

        Map<String, Object> segmentation = collectSegmentation();

        if (segmentation.isEmpty()) {
            Countly.sharedInstance().events().recordEvent(eventName, count, sum);
        } else {
            Countly.sharedInstance().events().recordEvent(eventName, segmentation, count, sum, duration);
        }

        Toast.makeText(this, "Event '" + eventName + "' recorded", Toast.LENGTH_SHORT).show();
    }

    private void startTimedEvent() {
        String name = inputTimedEventName.getText() != null ? inputTimedEventName.getText().toString().trim() : "";
        if (name.isEmpty()) {
            inputTimedEventName.setError("Event name is required");
            return;
        }
        boolean started = Countly.sharedInstance().events().startEvent(name);
        Toast.makeText(this, started ? "Timed event '" + name + "' started" : "Could not start (already running?)", Toast.LENGTH_SHORT).show();
    }

    private void endTimedEvent() {
        String name = inputTimedEventName.getText() != null ? inputTimedEventName.getText().toString().trim() : "";
        if (name.isEmpty()) {
            inputTimedEventName.setError("Event name is required");
            return;
        }
        boolean ended = Countly.sharedInstance().events().endEvent(name);
        Toast.makeText(this, ended ? "Timed event '" + name + "' ended" : "Could not end (not running?)", Toast.LENGTH_SHORT).show();
    }

    private void cancelTimedEvent() {
        String name = inputTimedEventName.getText() != null ? inputTimedEventName.getText().toString().trim() : "";
        if (name.isEmpty()) {
            inputTimedEventName.setError("Event name is required");
            return;
        }
        boolean cancelled = Countly.sharedInstance().events().cancelEvent(name);
        Toast.makeText(this, cancelled ? "Timed event '" + name + "' cancelled" : "Could not cancel (not running?)", Toast.LENGTH_SHORT).show();
    }

    private void recordPresetWithSegmentation() {
        Map<String, Object> segmentation = new HashMap<>();
        segmentation.put("wall", "green");
        segmentation.put("flowers", 3);
        Countly.sharedInstance().events().recordEvent("Preset Segmentation Event", segmentation, 1, 0, 0);
        Toast.makeText(this, "Preset segmentation event recorded", Toast.LENGTH_SHORT).show();
    }

    private void recordPresetWithCountSum() {
        Map<String, Object> segmentation = new HashMap<>();
        segmentation.put("wall", "blue");
        Countly.sharedInstance().events().recordEvent("Preset Count Sum Event", segmentation, 5, 24.5, 0);
        Toast.makeText(this, "Preset count+sum event recorded", Toast.LENGTH_SHORT).show();
    }
}
