package ly.count.android.sdk.internal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import ly.count.android.sdk.Countly;
import ly.count.android.sdk.ExperimentInformation;
import ly.count.android.sdk.ModuleLog;
import ly.count.android.sdk.ModuleRemoteConfig;
import ly.count.android.sdk.RCData;
import org.json.JSONArray;
import org.json.JSONObject;

public class RemoteConfigHelper {

    public static @NonNull Map<String, RCData> DownloadedValuesIntoMap(@Nullable JSONObject jsonObject) {
        Map<String, RCData> ret = new HashMap<>();

        if (jsonObject == null) {
            return ret;
        }

        Iterator<String> iter = jsonObject.keys();
        while (iter.hasNext()) {
            String key = iter.next();
            try {
                Object value = jsonObject.get(key);
                ret.put(key, new RCData(value, true));
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

    /**
     * Converts A/B testing info  fetched from the server (JSONObject) into a map
     *
     * @param experimentObj - JSON Object fetched from the server
     * @return
     */
    public static @NonNull Map<String, ExperimentInformation> convertExperimentInfoJsonToMap(@NonNull JSONObject experimentObj, @NonNull ModuleLog L) {
        Map<String, ExperimentInformation> experimentInfoMap = new HashMap<>();
        L.i("[ModuleRemoteConfig] convertExperimentInfoJsonToMap, parsing:[" + experimentObj + "]");

        if (!experimentObj.has("jsonArray")) {
            L.e("[ModuleRemoteConfig] convertVariantsJsonToMap, no json array found ");
            return experimentInfoMap;
        }

        JSONArray jsonArray = experimentObj.optJSONArray("jsonArray");

        L.i("[ModuleRemoteConfig] convertExperimentInfoJsonToMap, array:[" + jsonArray + "]");

        if (jsonArray == null) {
            return experimentInfoMap;
        }

        try {
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                L.i("[ModuleRemoteConfig] convertExperimentInfoJsonToMap, object:[" + jsonObject + "]");
                String expID = jsonObject.getString("id");
                String expName = jsonObject.getString("name");
                String expDescription = jsonObject.getString("description");
                String currentVariant = jsonObject.optString("currentVariant");

                JSONObject variantsObject = jsonObject.getJSONObject("variants");
                Map<String, Map<String, Object>> variantsMap = new HashMap<>();

                Iterator<String> variantNames = variantsObject.keys();
                while (variantNames.hasNext()) {

                    String variantName = variantNames.next();
                    JSONObject variantDetails = variantsObject.getJSONObject(variantName);
                    L.i("[ModuleRemoteConfig] convertExperimentInfoJsonToMap, details:[" + variantDetails + "]");
                    Map<String, Object> variantMap = new HashMap<>();

                    Iterator<String> variantKeys = variantDetails.keys();
                    while (variantKeys.hasNext()) {
                        String key = variantKeys.next();
                        variantMap.put(key, variantDetails.get(key));
                    }

                    variantsMap.put(variantName, variantMap);
                }

                ExperimentInformation experimentInfo = new ExperimentInformation(expID, expName, expDescription, currentVariant, variantsMap);
                experimentInfoMap.put(expID, experimentInfo);
            }
        } catch (Exception ex) {
            L.e("[ModuleRemoteConfig] convertVariantsJsonToMap, failed parsing:[" + ex.toString() + "]");
            return new HashMap<>();
        }

        L.i("[ModuleRemoteConfig] convertExperimentInfoJsonToMap, conversion result:[" + experimentInfoMap + "]");
        return experimentInfoMap;
    }
}
