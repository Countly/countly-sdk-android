package ly.count.android.demo;

import android.view.Gravity;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import java.util.Arrays;
import java.util.Map;
import ly.count.android.sdk.Countly;
import ly.count.android.sdk.ExperimentInformation;
import ly.count.android.sdk.RCVariantCallback;
import ly.count.android.sdk.RequestResult;

public class ActivityExampleTests extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_tests);
    }

    // gets and prints all stored experiment information
    public void onClickExperimentsPrintValues(View v) {
        Map<String, ExperimentInformation> values = Countly.sharedInstance().remoteConfig().testingGetAllExperimentInfo();
        if (values == null) {
            Countly.sharedInstance().L.w("No experiments present");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Experiment Information:\n");

        for (Map.Entry<String, ExperimentInformation> entry : values.entrySet()) {
            String expID = entry.getKey();
            ExperimentInformation experimentInfo = entry.getValue();

            sb.append("Experiment ID: ").append(expID).append("\n");
            sb.append("Experiment Name: ").append(experimentInfo.experimentName).append("\n");
            sb.append("Experiment Description: ").append(experimentInfo.experimentDescription).append("\n");
            sb.append("Current Variant: ").append(experimentInfo.currentVariant).append("\n");

            for (Map.Entry<String, Map<String, Object>> variantEntry : experimentInfo.variants.entrySet()) {
                String variantName = variantEntry.getKey();
                Map<String, Object> variantAttributes = variantEntry.getValue();

                sb.append("Variant: ").append(variantName).append("\n");
                for (Map.Entry<String, Object> attributeEntry : variantAttributes.entrySet()) {
                    String attributeName = attributeEntry.getKey();
                    Object attributeValue = attributeEntry.getValue();

                    sb.append(attributeName).append(": ").append(attributeValue).append("\n");
                }
            }

            sb.append("\n");
        }

        Toast t = Toast.makeText(getApplicationContext(), sb.toString(), Toast.LENGTH_LONG);
        t.setGravity(Gravity.BOTTOM, 0, 0);
        t.show();
    }

    // For fetching all variants with a button click
    public void onClickFetchAllExperiments(View v) {
        Countly.sharedInstance().remoteConfig().testingDownloadExperimentInformation(new RCVariantCallback() {
            @Override
            public void callback(RequestResult result, String error) {
                if (result == RequestResult.Success) {
                    Toast.makeText(getApplicationContext(), "Fetch finished", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(), "Error: " + result, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    // For fetching all variants with a button click
    public void onClickFetchAllVariants(View v) {
        Countly.sharedInstance().remoteConfig().testingDownloadVariantInformation(new RCVariantCallback() {
            @Override
            public void callback(RequestResult result, String error) {
                if (result == RequestResult.Success) {
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

        Countly.sharedInstance().remoteConfig().testingEnrollIntoVariant(key, variant, new RCVariantCallback() {
            @Override
            public void callback(RequestResult result, String error) {
                if (result == RequestResult.Success) {
                    Toast.makeText(getApplicationContext(), "Fetch finished", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(), "Error: " + result, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}