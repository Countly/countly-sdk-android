package ly.count.android.sdk.internal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import ly.count.android.sdk.Countly;
import ly.count.android.sdk.ModuleLog;
import ly.count.android.sdk.ModuleRemoteConfig;
import org.json.JSONArray;
import org.json.JSONObject;

public class RemoteConfigHelper {

    public static @NonNull Map<String, Object> DownloadedValuesIntoMap(@Nullable JSONObject jsonObject) {
        Map<String, Object> ret = new HashMap<>();

        if (jsonObject == null) {
            return ret;
        }

        Iterator<String> iter = jsonObject.keys();
        while (iter.hasNext()) {
            String key = iter.next();
            try {
                Object value = jsonObject.get(key);
                ret.put(key, value);
            } catch (Exception e) {
                Countly.sharedInstance().L.e("[RemoteConfigValueStore] Failed merging new remote config values");
            }
        }

        return ret;
    }

    /*
     * Decide which keys to use
     * Useful if both 'keysExcept' and 'keysOnly' set
     * */
    public static @NonNull String[] prepareKeysIncludeExclude(@Nullable final String[] keysOnly, @Nullable final String[] keysExcept, @NonNull ModuleLog L) {
        String[] res = new String[2];//0 - include, 1 - exclude

        try {
            if (keysOnly != null && keysOnly.length > 0) {
                //include list takes precedence
                //if there is at least one item, use it
                JSONArray includeArray = new JSONArray();
                for (String key : keysOnly) {
                    includeArray.put(key);
                }
                res[0] = includeArray.toString();
            } else if (keysExcept != null && keysExcept.length > 0) {
                //include list was not used, use the exclude list
                JSONArray excludeArray = new JSONArray();
                for (String key : keysExcept) {
                    excludeArray.put(key);
                }
                res[1] = excludeArray.toString();
            }
        } catch (Exception ex) {
            L.e("[ModuleRemoteConfig] prepareKeysIncludeExclude, Failed at preparing keys, [" + ex.toString() + "]");
        }

        return res;
    }

    /**
     * Converts A/B testing variants fetched from the server (JSONObject) into a map
     *
     * @param variantsObj - JSON Object fetched from the server
     * @return
     */
    public static @NonNull Map<String, String[]> convertVariantsJsonToMap(@NonNull JSONObject variantsObj, @NonNull ModuleLog L) {
        Map<String, String[]> resultMap = new HashMap<>();
        JSONArray keys = variantsObj.names();
        if (keys == null) {
            return resultMap;
        }

        List<String> tempVariantColl = new ArrayList<>(5);

        try {
            for (int i = 0; i < keys.length(); i++) {
                String key = keys.getString(i);
                Object value = variantsObj.get(key);

                if (!(value instanceof JSONArray)) {
                    //we only care about json arrays, all other values are skipped
                    continue;
                }

                tempVariantColl.clear();
                JSONArray jsonArray = (JSONArray) value;

                for (int j = 0; j < jsonArray.length(); j++) {
                    JSONObject variantObject = jsonArray.optJSONObject(j);

                    //skip for null values
                    if (variantObject == null || variantObject.isNull(ModuleRemoteConfig.variantObjectNameKey)) {
                        continue;
                    }

                    tempVariantColl.add(variantObject.optString(ModuleRemoteConfig.variantObjectNameKey));
                }

                //write the filtered array to map
                resultMap.put(key, tempVariantColl.toArray(new String[0]));
            }
        } catch (Exception ex) {
            L.e("[ModuleRemoteConfig] convertVariantsJsonToMap, failed parsing:[" + ex.toString() + "]");
            return new HashMap<>();
        }

        return resultMap;
    }
}
