package ly.count.android.demo;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
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
        Countly.sharedInstance().remoteConfig().update(new RemoteConfigCallback() {
            @Override
            public void callback(String error) {
                if (error == null) {
                    Toast.makeText(getApplicationContext(), "Update finished", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(), "Error: " + error, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    public void onClickRemoteConfigGetValue(View v) {
        Object value = Countly.sharedInstance().remoteConfig().getValueForKey("aa");
        if (value != null) {
            Toast.makeText(getApplicationContext(), "Stored Remote Config Value with key 'a': [" + (int) value + "]", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getApplicationContext(), "No value stored for this key", Toast.LENGTH_SHORT).show();
        }
    }

    public void onClickRemoteConfigGetValueInclusion(View v) {
        Countly.sharedInstance().remoteConfig().updateForKeysOnly(new String[] { "aa", "dd" }, new RemoteConfigCallback() {
            @Override
            public void callback(String error) {
                if (error == null) {
                    Toast.makeText(getApplicationContext(), "Update with inclusion finished", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(), "Error: " + error, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    public void onClickRemoteConfigGetValueExclusion(View v) {
        Countly.sharedInstance().remoteConfig().updateExceptKeys(new String[] { "aa", "dd" }, new RemoteConfigCallback() {
            @Override
            public void callback(String error) {
                if (error == null) {
                    Toast.makeText(getApplicationContext(), "Update with exclusion finished", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(), "Error: " + error, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    public void onClickRemoteConfigClearValues(View v) {
        Countly.sharedInstance().remoteConfig().clearStoredValues();
    }

    public void onClickRemoteConfigPrintValues(View v) {
        //this sample assumes that there are 4 keys available on the server

        Map<String, Object> values = Countly.sharedInstance().remoteConfig().getAllValues();

        Countly.sharedInstance().L.d("Get all values test: [" + values.toString() + "]");

        //access way #1
        Object value_1 = null;
        Object value_2 = null;
        Object value_3 = null;

        if (values != null) {
            value_1 = values.get("aa");
            value_2 = values.get("bb");
            value_3 = values.get("cc");
        }

        //access way #2
        Object value_4 = Countly.sharedInstance().remoteConfig().getValueForKey("dd");
        Object value_5 = Countly.sharedInstance().remoteConfig().getValueForKey("ee");

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
