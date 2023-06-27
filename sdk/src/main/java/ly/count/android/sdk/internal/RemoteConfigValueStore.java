package ly.count.android.sdk.internal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import ly.count.android.sdk.Countly;
import ly.count.android.sdk.RCData;
import org.json.JSONException;
import org.json.JSONObject;

public class RemoteConfigValueStore {
    public JSONObject values;
    public boolean valuesCanBeCached;
    public static final String keyValue = "v";
    public static final String keyCacheFlag = "c";
    public static final int cacheValCached = 0;
    public static final int cacheValFresh = 1;

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
            JSONObject value = values.optJSONObject(key);

            if (value == null) {
                Object badVal = values.opt(key);
                Countly.sharedInstance().L.w("[RemoteConfigValueStore] cacheClearValues, stored entry was not a JSON object, key:[" + key + "] value:[" + badVal + "]");
                continue;
            }

            try {
                value.put(keyCacheFlag, cacheValCached);
                values.put(key, value);
            } catch (Exception e) {
                Countly.sharedInstance().L.e("[RemoteConfigValueStore] cacheClearValues, Failed caching remote config values, " + e);
            }
        }
    }

    public void clearValues() {
        values = new JSONObject();
    }

    //========================================
    // MERGING
    //========================================

    public void mergeValues(@NonNull Map<String, RCData> newValues, boolean fullUpdate) {
        //Countly.sharedInstance().L.i("[RemoteConfigValueStore] mergeValues, stored values:" + values.toString() + "provided values:" + newValues);
        Countly.sharedInstance().L.v("[RemoteConfigValueStore] mergeValues, stored values C:" + values.length() + "provided values C:" + newValues.size());

        if (fullUpdate) {
            clearValues();
        }

        for (Map.Entry<String, RCData> entry : newValues.entrySet()) {
            String key = entry.getKey();
            Object newValue = entry.getValue().value;
            JSONObject newObj = new JSONObject();
            try {
                newObj.put(keyValue, newValue);
                newObj.put(keyCacheFlag, cacheValFresh);
                values.put(key, newObj);
            } catch (Exception e) {
                Countly.sharedInstance().L.e("[RemoteConfigValueStore] Failed merging remote config values");
            }
        }
        Countly.sharedInstance().L.v("[RemoteConfigValueStore] merging done:" + values.toString());
    }

    //========================================
    // CONSTRUCTION
    //========================================

    private RemoteConfigValueStore(@NonNull JSONObject values, boolean valuesShouldBeCached) {
        this.values = values;
        this.valuesCanBeCached = valuesShouldBeCached;
    }

    //========================================
    // GET VALUES
    //========================================

    public @NonNull RCData getValue(@NonNull String key) {
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
                int rcObjCache = rcObj.getInt(keyCacheFlag);
                ret.put(key, new RCData(rcObjVal, (rcObjCache != cacheValCached)));
            } catch (Exception ex) {
                Countly.sharedInstance().L.e("[RemoteConfigValueStore] Got JSON exception while calling 'getAllValues': " + ex.toString());
            }
        }

        return ret;
    }

    public Object getValueLegacy(@NonNull String key) {
        JSONObject rcObj = values.optJSONObject(key);
        if (rcObj == null) {
            return null;
        }

        return rcObj.opt(keyValue);
    }

    public @NonNull Map<String, Object> getAllValuesLegacy() {
        Map<String, Object> ret = new HashMap<>();

        Iterator<String> keys = values.keys();

        while (keys.hasNext()) {
            String key = keys.next();

            JSONObject jobj = values.optJSONObject(key);
            if (jobj == null) {
                Countly.sharedInstance().L.e("[RemoteConfigValueStore] getAllValuesLegacy, inner object seems to be 'null', key:[" + key + "]");
                continue;
            }

            Object innerValue = jobj.opt(keyValue);

            if (innerValue == null) {
                Countly.sharedInstance().L.e("[RemoteConfigValueStore] getAllValuesLegacy, inner value seems to be 'null', key:[" + key + "]");
                continue;
            }

            ret.put(key, innerValue);
        }

        return ret;
    }

    //========================================
    // SERIALIZATION, DESERIALIZATION
    //========================================

    public static RemoteConfigValueStore dataFromString(@Nullable String storageString, boolean valuesShouldBeCached) {
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
        //Countly.sharedInstance().L.i("[RemoteConfigValueStore] serialization done, dataFromString:" + values.toString());
        return new RemoteConfigValueStore(values, valuesShouldBeCached);
    }

    public String dataToString() {
        return values.toString();
    }
}
