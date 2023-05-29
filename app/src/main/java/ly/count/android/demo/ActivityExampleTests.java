package ly.count.android.demo;

import android.content.res.Configuration;
import android.view.Gravity;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import java.util.Arrays;
import java.util.Map;
import ly.count.android.sdk.Countly;
import ly.count.android.sdk.RequestResponse;
import ly.count.android.sdk.RemoteConfigVariantCallback;

public class ActivityExampleTests extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_tests);
    }

    // For fetching all variants with a button click
    public void onClickFetchAllVariants(View v) {
        Countly.sharedInstance().remoteConfig().testingFetchVariantInformation(new RemoteConfigVariantCallback() {
            @Override
            public void callback(RequestResponse result) {
                if (result == RequestResponse.SUCCESS) {
                    Toast.makeText(getApplicationContext(), "Fetch finished", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(), "Error: " + result, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    // To get all variants from the storage and show them with a toast
    public void onClickVariantsPrintValues(View v) {
        Map<String, String[]> values = Countly.sharedInstance().remoteConfig().testingGetAllVariants();
        if (values == null) {
            Countly.sharedInstance().L.w("No variants present");
            return;
        }
        Countly.sharedInstance().L.d("Get all variants: " + values);

        StringBuilder sb = new StringBuilder();
        sb.append("Stored Variant Values:\n");
        for (Map.Entry<String, String[]> entry : values.entrySet()) {
            String key = entry.getKey();
            String[] variants = entry.getValue();
            sb.append(key).append(": ").append(Arrays.toString(variants)).append("\n");
        }

        Toast t = Toast.makeText(getApplicationContext(), sb.toString(), Toast.LENGTH_LONG);
        t.setGravity(Gravity.BOTTOM, 0, 0);
        t.show();
    }

    public void onClickEnrollVariant(View v) {
        Map<String, String[]> values = Countly.sharedInstance().remoteConfig().testingGetAllVariants();
        if (values == null) {
            Countly.sharedInstance().L.w("No variants present");
            return;
        }
        Countly.sharedInstance().L.d("Get all variants: [" + values.toString() + "]");

        // Get the first key and variant
        String key = null;
        String variant = null;
        for (Map.Entry<String, String[]> entry : values.entrySet()) {
            key = entry.getKey();
            variant = entry.getValue()[0]; // first variant
            break; // Get only the first key-value pair
        }

        Countly.sharedInstance().remoteConfig().testingEnrollIntoVariant(key, variant, new RemoteConfigVariantCallback() {
            @Override
            public void callback(RequestResponse result) {
                if (result == RequestResponse.SUCCESS) {
                    Toast.makeText(getApplicationContext(), "Fetch finished", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(), "Error: " + result, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        Countly.sharedInstance().onStart(this);
    }

    @Override
    public void onStop() {
        Countly.sharedInstance().onStop();
        super.onStop();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Countly.sharedInstance().onConfigurationChanged(newConfig);
    }
}