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
    static final String keyValue = "v";
    static final String keyCacheFlag = "c";
    static final int cacheValCached = 0;
    static final int cacheValFresh = 1;
    public boolean dirty = false;

    //  Structure of the JSON objects we will have
    //   {
    //      “key”: {
    //          “v”: “value”,
    //          “c”: 0
    //      }
    //   }

    //========================================
    // CLEANSING
    //========================================

    public void cacheClearValues() {
        if (!valuesCanBeCached) {
            clearValues();
            return;
        }

        Iterator<String> iter = values.keys();
        while (iter.hasNext()) {
            String key = iter.next();
            try {
                JSONObject value = values.getJSONObject(key);
                if (value != null) {
                    value.put(keyCacheFlag, cacheValCached);
                    values.put(key, value);
                }
            } catch (Exception e) {
                Countly.sharedInstance().L.e("[RemoteConfigValueStore] Failed caching remote config values");
            }
        }
        dirty = true;
    }

    public void clearValues() {
        values = new JSONObject();
        dirty = true;
    }

    //========================================
    // MERGING
    //========================================

    public void mergeValues(Map<String, Object> newValues, boolean fullUpdate) {
        Countly.sharedInstance().L.i("[RemoteConfigValueStore] mergeValues, stored values:" + values.toString());
        if (newValues != null) {
            Countly.sharedInstance().L.i("[RemoteConfigValueStore] mergeValues, provided values:" + newValues.toString());
        }
        if (fullUpdate) {
            clearValues();
        }

        for (Map.Entry<String, Object> entry : newValues.entrySet()) {
            String key = entry.getKey();
            Object newValue = entry.getValue();
            JSONObject newObj = new JSONObject();
            try {
                newObj.put(keyValue, newValue);
                newObj.put(keyCacheFlag, cacheValFresh);
                values.put(key, newObj);
            } catch (Exception e) {
                Countly.sharedInstance().L.e("[RemoteConfigValueStore] Failed merging remote config values");
            }
        }
        dirty = true;
        Countly.sharedInstance().L.i("[RemoteConfigValueStore] merging done:" + values.toString());
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

    //========================================
    // CONSTRUCTION
    //========================================

    private RemoteConfigValueStore(JSONObject values, boolean valuesShouldBeCached) {
        this.values = values;
        this.valuesCanBeCached = valuesShouldBeCached;
    }

    //========================================
    // GET VALUES
    //========================================

    public @NonNull RCData getValue(String key) {
        RCData res = new RCData(null, true);
        try {
            JSONObject rcObj = values.optJSONObject(key);
            if (rcObj == null) {
                return res;
            }
            res.value = rcObj.get(keyValue);
            res.isCurrentUsersData = rcObj.getInt(keyCacheFlag) != cacheValCached;
            return res;
        } catch (Exception ex) {
            Countly.sharedInstance().L.e("[RemoteConfigValueStore] Got JSON exception while calling 'getValue': " + ex.toString());
        }
        return res;
    }

    public @NonNull Map<String, RCData> getAllValues() {
        Map<String, RCData> ret = new HashMap<>();

        Iterator<String> keys = values.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            try {
                JSONObject rcObj = values.optJSONObject(key);
                if (rcObj == null) {
                    continue;
                }
                Object rcObjVal = rcObj.opt(keyValue);
                Integer rcObjCache = rcObj.getInt(keyCacheFlag);
                ret.put(key, new RCData(rcObjVal, (rcObjCache != cacheValCached)));
            } catch (Exception ex) {
                Countly.sharedInstance().L.e("[RemoteConfigValueStore] Got JSON exception while calling 'getAllValues': " + ex.toString());
            }
        }

        return ret;
    }

    public Object getValueLegacy(String key) {
        return values.opt(key);
    }

    public Map<String, Object> getAllValuesLegacy() {
        Map<String, Object> ret = new HashMap<>();

        Iterator<String> keys = values.keys();

        while (keys.hasNext()) {
            String key = keys.next();

            try {
                ret.put(key, values.get(key));
            } catch (Exception ex) {
                Countly.sharedInstance().L.e("[RemoteConfigValueStore] Got JSON exception while calling 'getAllValuesLegacy': " + ex.toString());
            }
        }

        return ret;
    }

    //========================================
    // SERIALIZATION
    //========================================

    public static RemoteConfigValueStore dataFromString(String storageString, boolean valuesShouldBeCached) {
        if (storageString == null || storageString.isEmpty()) {
            return new RemoteConfigValueStore(new JSONObject(), valuesShouldBeCached);
        }

        JSONObject values;
        try {
            values = new JSONObject(storageString);
            //iterate through all values and check if each value an instance of json object. and if it isnt convert to new data
            Iterator<String> iter = values.keys();
            while (iter.hasNext()) {
                String key = iter.next();
                try {
                    JSONObject value = values.optJSONObject(key);
                    if (value == null) {
                        value = new JSONObject();
                        value.put(keyValue, values.get(key));
                        value.put(keyCacheFlag, cacheValFresh);
                        values.put(key, value);
                    }
                } catch (Exception e) {
                    Countly.sharedInstance().L.e("[RemoteConfigValueStore] Failed caching remote config values, dataFromString:" + e.toString());
                }
            }
        } catch (JSONException e) {
            Countly.sharedInstance().L.e("[RemoteConfigValueStore] Couldn't decode RemoteConfigValueStore successfully: " + e.toString());
            values = new JSONObject();
        }
        Countly.sharedInstance().L.i("[RemoteConfigValueStore] serialization done, dataFromString:" + values.toString());
        return new RemoteConfigValueStore(values, valuesShouldBeCached);
    }

    public String dataToString() {
        return values.toString();
    }
}
