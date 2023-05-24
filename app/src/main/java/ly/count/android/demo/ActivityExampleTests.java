package ly.count.android.demo;

import android.content.res.Configuration;
import android.view.Gravity;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import java.util.Iterator;
import ly.count.android.sdk.Countly;
import ly.count.android.sdk.RemoteConfigCallback;
import org.json.JSONArray;
import org.json.JSONObject;

public class ActivityExampleTests extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_tests);
    }

    // For fetching all variants with a button click
    public void onClickFetchAllVariants(View v) {
        Countly.sharedInstance().remoteConfig().testFetchAllVariants(new RemoteConfigCallback() {
            @Override
            public void callback(String error) {
                if (error == null) {
                    Toast.makeText(getApplicationContext(), "Fetch finished", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(), "Error: " + error, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    // To get all variants from the storage and show them with a toast
    public void onClickVariantsPrintValues(View v) {
        JSONObject values = Countly.sharedInstance().remoteConfig().getAllVariants();
        if(values== null) {
            Countly.sharedInstance().L.w("No variants present");
            return;
        }
        Countly.sharedInstance().L.d("Get all variants: [" + values.toString() + "]");

        Toast t = Toast.makeText(getApplicationContext(), "Stored Variant Values: [" + values.toString() + "]", Toast.LENGTH_LONG);
        t.setGravity(Gravity.BOTTOM, 0, 0);
        t.show();
    }

    public void onClickEnrollVariant(View v) {
        JSONObject values = Countly.sharedInstance().remoteConfig().getAllVariants();
        if(values== null) {
            Countly.sharedInstance().L.w("No variants present");
            return;
        }
        Countly.sharedInstance().L.d("Get all variants: [" + values.toString() + "]");

        String[] result = null;

        Iterator<String> keys = values.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = values.opt(key);

            if (value instanceof JSONArray) {
                JSONArray jsonArray = (JSONArray) value;
                if (jsonArray.length() > 0 && jsonArray.opt(0) instanceof JSONObject) {
                    JSONObject jsonObject = jsonArray.optJSONObject(0);
                    String name = jsonObject.optString("name");
                    String variant = jsonObject.optString("value");

                    if (!name.isEmpty() && !variant.isEmpty()) {
                        result = new String[] { key, variant };
                        break;
                    }
                }
            }
        }

        Countly.sharedInstance().remoteConfig().testEnrollIntoVariant(result[0], result[1], new RemoteConfigCallback() {
            @Override
            public void callback(String error) {
                if (error == null) {
                    Toast.makeText(getApplicationContext(), "Fetch finished", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(), "Error: " + error, Toast.LENGTH_SHORT).show();
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