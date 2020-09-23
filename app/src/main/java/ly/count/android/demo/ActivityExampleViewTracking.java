package ly.count.android.demo;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentTransaction;
import java.util.HashMap;
import java.util.Map;

import ly.count.android.sdk.Countly;
import ly.count.android.sdk.PersistentName;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings({"UnusedParameters", "unused"})
@PersistentName("ActivityExampleViewTracking")
public class ActivityExampleViewTracking extends AppCompatActivity {

    // track views by a string name
    private static final String STR_VIEW = "Awesome view";
    private static final String STR_VIEW2 = "Better view";

    // track views by their identity hashcode, not by a string name
    // you can track multiple views with the same name with this approach
    private static final Object OBJ_VIEW = new Object();
    private static final Object OBJ_VIEW2 = new Object();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_view_tracking);
        newFragment(R.id.fragment_left, ContextCompat.getColor(this, R.color.colorPrimary));
        newFragment(R.id.fragment_right, ContextCompat.getColor(this, R.color.colorPrimaryDark));
    }

    public void onClickViewTrackingRecordViewById(View v) {
        Countly.sharedInstance().views().recordView(OBJ_VIEW, null, "Best View");
    }

    public void onClickViewTrackingRecordView(View v) {
        Countly.sharedInstance().views().recordView(STR_VIEW, null);
    }

    public void onClickViewTrackingEndById(View v) {
        Countly.sharedInstance().views().endViewRecording(OBJ_VIEW);
    }

    public void onClickViewTrackingEnd(View v) {
        Countly.sharedInstance().views().endViewRecording(STR_VIEW);
    }

    public void onClickViewTrackingRecordViewByIdWithSegmentation(View v) {
        Map<String, Object> viewSegmentation = new HashMap<>();
        viewSegmentation.put("Puppies", 789);
        viewSegmentation.put("Black Holes", 5.56d);
        viewSegmentation.put("Caribou", "White-tail");
        Countly.sharedInstance().views().recordView(OBJ_VIEW2, viewSegmentation, "Best View");
    }

    public void onClickViewTrackingEndByIdWithSegmentation(View v) {
        Countly.sharedInstance().views().endViewRecording(OBJ_VIEW2);
    }

    public void onClickViewTrackingRecordViewWithSegmentation(View v) {
        Map<String, Object> viewSegmentation = new HashMap<>();
        viewSegmentation.put("Cats", 123);
        viewSegmentation.put("Moons", 9.98d);
        viewSegmentation.put("Moose", "Deer");
        Countly.sharedInstance().views().recordView(STR_VIEW2, viewSegmentation);
    }

    public void onClickViewTrackingEndWithSegmentation(View v) {
        Countly.sharedInstance().views().endViewRecording(STR_VIEW2);
    }

    public void onFragmentClick(@NotNull View v) {
        int containerId = v.getId();
        ColorFragment f = (ColorFragment) getSupportFragmentManager().findFragmentById(containerId);
        int primary = ContextCompat.getColor(this, R.color.colorPrimary);
        int targetColor = (f != null && f.getColor() == primary) ? ContextCompat.getColor(this, R.color.colorPrimaryDark) : primary;
        newFragment(containerId, targetColor);
    }

    private void newFragment(int containerId, int color) {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(containerId, ColorFragment.newInstance(color));
        ft.commit();
    }
}
