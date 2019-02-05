package ly.count.android.demo;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import ly.count.android.sdk.Countly;
import ly.count.android.sdk.RemoteConfig;

public class ActivityExampleRemoteConfig extends Activity {
    Activity activity;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        activity = this;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_remote_config);
        Countly.onCreate(this);
    }

    public void onClickRemoteConfigUpdate(View v) {
        Countly.sharedInstance().remoteConfigUpdate(new RemoteConfig.RemoteConfigCallback() {
            @Override
            public void callback(String error) {
                if(error == null) {
                    Toast.makeText(activity, "Update finished", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(activity, "Error: " + error, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    public void onClickRemoteConfigGetValue(View v) {
        Object value = Countly.sharedInstance().getRemoteConfigValueForKey("a");
        if(value != null){
            Toast.makeText(activity, "Stored Remote Config Value with key 'a': [" + (int)value+ "]", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(activity, "No value stored for this key", Toast.LENGTH_SHORT).show();
        }
    }

    public void onClickRemoteConfigGetValueInclusion(View v) {
        Countly.sharedInstance().updateRemoteConfigForKeysOnly(new String[]{"a", "d"}, new RemoteConfig.RemoteConfigCallback() {
            @Override
            public void callback(String error) {
                if(error == null) {
                    Toast.makeText(activity, "Update with inclusion finished", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(activity, "Error: " + error, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    public void onClickRemoteConfigGetValueExclusion(View v) {
        Countly.sharedInstance().updateRemoteConfigExceptKeys(new String[]{"a", "d"}, new RemoteConfig.RemoteConfigCallback() {
            @Override
            public void callback(String error) {
                if (error == null) {
                    Toast.makeText(activity, "Update with exclusion finished", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(activity, "Error: " + error, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    public void onClickRemoteConfigClearValues(View v) {
        Countly.sharedInstance().remoteConfigClearValues();
    }

    public void onClickRemoteConfigPrintValues(View v) {
        //this sample assumes that there are 4 keys available on the server

        Object value_1 = Countly.sharedInstance().getRemoteConfigValueForKey("a");
        Object value_2 = Countly.sharedInstance().getRemoteConfigValueForKey("b");
        Object value_3 = Countly.sharedInstance().getRemoteConfigValueForKey("c");
        Object value_4 = Countly.sharedInstance().getRemoteConfigValueForKey("d");

        String printValues = "";

        if(value_1 != null){
            //int value
            printValues += (int)value_1;
        }

        if(value_2 != null){
            //float value
            printValues += "| " + (double)value_2;
        }

        if(value_3 != null) {
            //array
            JSONArray jArray = (JSONArray) value_3;
            printValues += "| " + value_3.toString();
        }

        if(value_4 != null) {
            //json object
            JSONObject jobj = (JSONObject) value_4;
            printValues += "| " + value_4.toString();
        }

        Toast.makeText(activity, "Stored Remote Config Values: [" + printValues + "]", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onStart()
    {
        super.onStart();
        Countly.sharedInstance().onStart(this);
    }

    @Override
    public void onStop()
    {
        Countly.sharedInstance().onStop();
        super.onStop();
    }
}
