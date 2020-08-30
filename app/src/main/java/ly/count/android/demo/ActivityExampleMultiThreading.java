package ly.count.android.demo;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import ly.count.android.sdk.PersistentName;

@PersistentName("ActivityExampleMultiThreading")
public class ActivityExampleMultiThreading extends AppCompatActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_custom_events);
    }
}
