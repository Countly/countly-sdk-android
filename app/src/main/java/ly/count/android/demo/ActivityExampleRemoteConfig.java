package ly.count.android.demo;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import ly.count.android.sdk.RCData;
import ly.count.android.sdk.RCDownloadCallback;
import ly.count.android.sdk.RequestResult;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;

import ly.count.android.sdk.Countly;
import ly.count.android.sdk.RemoteConfigCallback;

public class ActivityExampleRemoteConfig extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_remote_config);
    }

    public void onClickRemoteConfigUpdate(View v) {
        Countly.sharedInstance().remoteConfig().downloadAllKeys(new RCDownloadCallback() {
            @Override public void callback(RequestResult downloadResult, String error, boolean fullValueUpdate, Map<String, RCData> downloadedValues) {
                if (error == null) {
                    Toast.makeText(getApplicationContext(), "Update finished", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(), "Error: " + error, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    public void onClickRemoteConfigGetValue(View v) {
        Object value = Countly.sharedInstance().remoteConfig().getValue("aa").value;
        if (value != null) {
            Toast.makeText(getApplicationContext(), "Stored Remote Config Value with key 'aa': [" + (int) value + "]", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getApplicationContext(), "No value stored for this key", Toast.LENGTH_SHORT).show();
        }
    }

    public void onClickRemoteConfigGetValueAndEnroll(View v) {
        Object value = Countly.sharedInstance().remoteConfig().getValueAndEnroll("aa").value;
        if (value != null) {
            Toast.makeText(getApplicationContext(), "Stored Remote Config Value with key 'aa': [" + (int) value + "]", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getApplicationContext(), "No value stored for this key", Toast.LENGTH_SHORT).show();
        }
    }

    public void onClickRemoteConfigGetValueInclusion(View v) {
        Countly.sharedInstance().remoteConfig().downloadSpecificKeys(new String[] { "aa", "dd" }, new RCDownloadCallback() {
            @Override public void callback(RequestResult downloadResult, String error, boolean fullValueUpdate, Map<String, RCData> downloadedValues) {
                if (downloadResult != RequestResult.Success) {
                    Toast.makeText(getApplicationContext(), "Update with inclusion finished", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(), "Error: " + error, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    public void onClickRemoteConfigGetValueExclusion(View v) {
        Countly.sharedInstance().remoteConfig().downloadOmittingKeys(new String[] { "aa", "dd" }, new RCDownloadCallback() {
            @Override public void callback(RequestResult downloadResult, String error, boolean fullValueUpdate, Map<String, RCData> downloadedValues) {
                if (downloadResult != RequestResult.Success) {
                    Toast.makeText(getApplicationContext(), "Update with exclusion finished", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(), "Error: " + error, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    public void onClickRemoteConfigClearValues(View v) {
        Countly.sharedInstance().remoteConfig().clearAll();
    }

    public void onClickRemoteConfigEnrollForKeys(View v) {
        String[] keys = { "kk", "bb" };
        Countly.sharedInstance().remoteConfig().enrollIntoABTestsForKeys(keys);
        Toast.makeText(getApplicationContext(), "Enrolling for Keys", Toast.LENGTH_SHORT).show();
    }

    public void onClickRemoteConfigExitForKeys(View v) {
        Countly.sharedInstance().remoteConfig().exitABTestsForKeys(null); // exit all
        Toast.makeText(getApplicationContext(), "Exiting for Keys", Toast.LENGTH_SHORT).show();
    }

    public void onClickRemoteConfigPrintValues(View v) {
        //this sample assumes that there are 5 keys available on the server

        Map<String, RCData> values = Countly.sharedInstance().remoteConfig().getValues();

        Countly.sharedInstance().L.d("Get all values test: [" + values.toString() + "]");

        //access way #1
        Object value_1 = null;
        Object value_2 = null;
        Object value_3 = null;

        if (values.containsKey("aa")) {
            value_1 = values.get("aa").value;
        }
        if (values.containsKey("bb")) {
            value_2 = values.get("bb").value;
        }
        if (values.containsKey("cc")) {
            value_3 = values.get("cc").value;
        }

        //access way #2
        Object value_4 = Countly.sharedInstance().remoteConfig().getValue("dd").value;
        Object value_5 = Countly.sharedInstance().remoteConfig().getValue("ee").value;

        String printValues = "";

        if (value_1 != null) {
            //int value
            printValues += (int) value_1;
        }

        if (value_2 != null) {
            //float value
            printValues += "| " + (double) value_2;
        }

        if (value_3 != null) {
            //String value
            printValues += "| " + (String) value_3;
        }

        if (value_4 != null) {
            //array
            JSONArray jArray = (JSONArray) value_4;
            printValues += "| " + jArray.toString();
        }

        if (value_5 != null) {
            //json object
            JSONObject jobj = (JSONObject) value_5;
            printValues += "| " + jobj.toString();
        }

        Toast t = Toast.makeText(getApplicationContext(), "Stored Remote Config Values: [" + printValues + "]", Toast.LENGTH_LONG);
        t.setGravity(Gravity.BOTTOM, 0, 0);
        t.show();
    }

    public void onClickRemoteConfigPrintValuesAndEnroll(View v) {
        //this sample assumes that there is 1 key available on the server

        Map<String, RCData> values = Countly.sharedInstance().remoteConfig().getAllValuesAndEnroll();

        Countly.sharedInstance().L.d("Get all values test: [" + values.toString() + "]");

        Object value_1 = null;

        if (values != null && values.containsKey("aa")) {
            value_1 = values.get("aa").value;
        }

        String printValues = "";

        if (value_1 instanceof Integer) {
            int intValue = (int) value_1;
            printValues += intValue;
        }

        Toast t = Toast.makeText(getApplicationContext(), "Stored Remote Config Values: [" + printValues + "]", Toast.LENGTH_LONG);
        t.setGravity(Gravity.BOTTOM, 0, 0);
        t.show();
    }
}
