package ly.count.android.sdk.internal;

import androidx.annotation.NonNull;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import ly.count.android.sdk.Countly;
import ly.count.android.sdk.RCData;
import org.json.JSONException;
import org.json.JSONObject;

public class RemoteConfigValueStore {
    public JSONObject values = new JSONObject();

    public boolean valuesCanBeCached = false;

    public void cacheClearValues() {
        if (!valuesCanBeCached) {
            clearValues();
            return;
        }

        // values can be cached, do that
    }

    public void clearValues() {
        values = new JSONObject();
    }

    public void mergeValues(Map<String, Object> newValues, boolean fullUpdate) {
        //todo must be finished
    }

    /**
     * add new values to the current storage
     *
     * @param newValues
     * @param fullUpdate clear all previous values in case of full update
     */
    public void mergeValuesToBeRemoved(JSONObject newValues, boolean fullUpdate) {
        if (fullUpdate) {
            clearValues();
        }

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

    private RemoteConfigValueStore(JSONObject values, boolean valuesShouldBeCached) {
        this.values = values;
        this.valuesCanBeCached = valuesShouldBeCached;
    }

    public Object getValueLegacy(String key) {
        return values.opt(key);
    }

    public @NonNull RCData getValue(String key) {
        return null;
    }

    public @NonNull Map<String, RCData> getAllValues() {
        return null;
    }

    public Map<String, Object> getAllValuesLegacy() {
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

    public static RemoteConfigValueStore dataFromString(String storageString, boolean valuesShouldBeCached) {
        if (storageString == null || storageString.isEmpty()) {
            return new RemoteConfigValueStore(new JSONObject(), valuesShouldBeCached);
        }

        JSONObject values;
        try {
            values = new JSONObject(storageString);
        } catch (JSONException e) {
            Countly.sharedInstance().L.e("[RemoteConfigValueStore] Couldn't decode RemoteConfigValueStore successfully: " + e.toString());
            values = new JSONObject();
        }
        return new RemoteConfigValueStore(values, valuesShouldBeCached);
    }

    public String dataToString() {
        return values.toString();
    }
}
