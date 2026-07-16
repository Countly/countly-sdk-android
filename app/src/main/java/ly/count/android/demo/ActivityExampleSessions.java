package ly.count.android.demo;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import ly.count.android.sdk.Countly;

public class ActivityExampleSessions extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_sessions);

        findViewById(R.id.btnBeginSession).setOnClickListener(v -> {
            Countly.sharedInstance().sessions().beginSession();
            Toast.makeText(this, "Session begun", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnUpdateSession).setOnClickListener(v -> {
            Countly.sharedInstance().sessions().updateSession();
            Toast.makeText(this, "Session updated", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnEndSession).setOnClickListener(v -> {
            Countly.sharedInstance().sessions().endSession();
            Toast.makeText(this, "Session ended", Toast.LENGTH_SHORT).show();
        });
    }
}
