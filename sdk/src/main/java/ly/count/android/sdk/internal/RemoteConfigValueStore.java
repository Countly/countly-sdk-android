package ly.count.android.sdk.internal;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import ly.count.android.sdk.Countly;
import org.json.JSONException;
import org.json.JSONObject;

public class RemoteConfigValueStore {
    public JSONObject values = new JSONObject();

    //add new values to the current storage
    public void mergeValues(JSONObject newValues) {
        if (newValues == null) {
            return;
        }

        Iterator<String> iter = newValues.keys();
        while (iter.hasNext()) {
            String key = iter.next();
            try {
                Object value = newValues.get(key);
                values.put(key, value);
            } catch (Exception e) {
                Countly.sharedInstance().L.e("[RemoteConfigValueStore] Failed merging new remote config values");
            }
        }
    }

    private RemoteConfigValueStore(JSONObject values) {
        this.values = values;
    }

    public Object getValue(String key) {
        return values.opt(key);
    }

    public Map<String, Object> getAllValues() {
        Map<String, Object> ret = new HashMap<>();

        Iterator<String> keys = values.keys();

        while (keys.hasNext()) {
            String key = keys.next();

            try {
                ret.put(key, values.get(key));
            } catch (Exception ex) {
                Countly.sharedInstance().L.e("[RemoteConfigValueStore] Got JSON exception while calling 'getAllValues': " + ex.toString());
            }
        }

        return ret;
    }

    public static RemoteConfigValueStore dataFromString(String storageString) {
        if (storageString == null || storageString.isEmpty()) {
            return new RemoteConfigValueStore(new JSONObject());
        }

        JSONObject values;
        try {
            values = new JSONObject(storageString);
        } catch (JSONException e) {
            Countly.sharedInstance().L.e("[RemoteConfigValueStore] Couldn't decode RemoteConfigValueStore successfully: " + e.toString());
            values = new JSONObject();
        }
        return new RemoteConfigValueStore(values);
    }

    public String dataToString() {
        return values.toString();
    }
}
