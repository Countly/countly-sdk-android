package ly.count.android.demo;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Arrays;
import java.util.Map;
import ly.count.android.sdk.Countly;
import ly.count.android.sdk.ExperimentInformation;
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

        StringBuilder sb = new StringBuilder(28);
        sb.append("Experiment Information:\n");

        for (Map.Entry<String, ExperimentInformation> entry : values.entrySet()) {
            String expID = entry.getKey();
            ExperimentInformation experimentInfo = entry.getValue();

            sb.append("Experiment ID: ").append(expID);
            sb.append("\nExperiment Name: ").append(experimentInfo.experimentName);
            sb.append("\nExperiment Description: ").append(experimentInfo.experimentDescription);
            sb.append("\nCurrent Variant: ").append(experimentInfo.currentVariant);

            for (Map.Entry<String, Map<String, Object>> variantEntry : experimentInfo.variants.entrySet()) {
                String variantName = variantEntry.getKey();
                Map<String, Object> variantAttributes = variantEntry.getValue();

                sb.append("\nVariant: ").append(variantName).append('\n');
                for (Map.Entry<String, Object> attributeEntry : variantAttributes.entrySet()) {
                    String attributeName = attributeEntry.getKey();
                    Object attributeValue = attributeEntry.getValue();

                    sb.append(attributeName).append(": ").append(attributeValue).append('\n');
                }
            }

            sb.append('\n');
        }

        Toast t = Toast.makeText(getApplicationContext(), sb.toString(), Toast.LENGTH_LONG);
        t.setGravity(Gravity.BOTTOM, 0, 0);
        t.show();
    }

    // For fetching all variants with a button click
    public void onClickFetchAllExperiments(View v) {
        Countly.sharedInstance().remoteConfig().testingDownloadExperimentInformation((result, error) -> {
            if (result == RequestResult.Success) {
                Toast.makeText(getApplicationContext(), "Fetch finished", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getApplicationContext(), "Error: " + result, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // For fetching all variants with a button click
    public void onClickFetchAllVariants(View v) {
        Countly.sharedInstance().remoteConfig().testingDownloadVariantInformation((result, error) -> {
            if (result == RequestResult.Success) {
                Toast.makeText(getApplicationContext(), "Fetch finished", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getApplicationContext(), "Error: " + result, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // To get all variants from the storage and show them with a toast
    public void onClickVariantsPrintValues(View v) {
        Map<String, String[]> values = Countly.sharedInstance().remoteConfig().testingGetAllVariants();
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
        Countly.sharedInstance().L.d("Get all variants: [" + values + "]");

        Map.Entry<String, String[]> entry = values.entrySet().iterator().next();
        // Get the first key and variant
        String key = entry.getKey();
        String variant = entry.getValue()[0];

        Countly.sharedInstance().remoteConfig().testingEnrollIntoVariant(key, variant, (result, error) -> {
            if (result == RequestResult.Success) {
                Toast.makeText(getApplicationContext(), "Fetch finished", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getApplicationContext(), "Error: " + result, Toast.LENGTH_SHORT).show();
            }
        });
    }
}