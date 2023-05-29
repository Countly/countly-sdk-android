package ly.count.android.sdk;

import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ModuleRemoteConfig extends ModuleBase {
    ImmediateRequestGenerator immediateRequestGenerator;
    boolean updateRemoteConfigAfterIdChange = false;
    Map<String, String[]> variantContainer; // Stores the fetched A/B test variants
    RemoteConfig remoteConfigInterface = null;

    //if set to true, it will automatically download remote configs on module startup
    boolean remoteConfigAutomaticUpdateEnabled = false;
    RemoteConfigCallback remoteConfigInitCallback = null;

    @Nullable
    Map<String, String> metricOverride = null;

    ModuleRemoteConfig(Countly cly, final CountlyConfig config) {
        super(cly, config);
        L.v("[ModuleRemoteConfig] Initialising");

        metricOverride = config.metricOverride;
        immediateRequestGenerator = config.immediateRequestGenerator;

        if (config.enableRemoteConfigAutomaticDownload) {
            L.d("[ModuleRemoteConfig] Setting if remote config Automatic download will be enabled, " + config.enableRemoteConfigAutomaticDownload);

            remoteConfigAutomaticUpdateEnabled = config.enableRemoteConfigAutomaticDownload;

            if (config.remoteConfigCallbackNew != null) {
                remoteConfigInitCallback = config.remoteConfigCallbackNew;
            }
        }

        remoteConfigInterface = new RemoteConfig();
    }

    /**
     * Internal call for updating remote config keys
     *
     * @param keysOnly set if these are the only keys to update
     * @param keysExcept set if these keys should be ignored from the update
     * @param requestShouldBeDelayed this is set to true in case of update after a deviceId change
     * @param callback called after the update is done
     */
    void updateRemoteConfigValues(@Nullable final String[] keysOnly, @Nullable final String[] keysExcept, final boolean requestShouldBeDelayed, @Nullable final RemoteConfigCallback callback) {
        try {
            L.d("[ModuleRemoteConfig] Updating remote config values, requestShouldBeDelayed:[" + requestShouldBeDelayed + "]");

            // checks
            if (deviceIdProvider.getDeviceId() == null) {
                //device ID is null, abort
                L.d("[ModuleRemoteConfig] RemoteConfig value update was aborted, deviceID is null");
                if (callback != null) {
                    callback.callback("Can't complete call, device ID is null");
                }
                return;
            }

            if (deviceIdProvider.isTemporaryIdEnabled() || requestQueueProvider.queueContainsTemporaryIdItems()) {
                //temporary id mode enabled, abort
                L.d("[ModuleRemoteConfig] RemoteConfig value update was aborted, temporary device ID mode is set");
                if (callback != null) {
                    callback.callback("Can't complete call, temporary device ID is set");
                }
                return;
            }

            //prepare metrics and request data
            String preparedMetrics = deviceInfo.getMetrics(_cly.context_, deviceInfo, metricOverride);
            String[] preparedKeys = prepareKeysIncludeExclude(keysOnly, keysExcept);
            String requestData = requestQueueProvider.prepareRemoteConfigRequest(preparedKeys[0], preparedKeys[1], preparedMetrics);
            L.d("[ModuleRemoteConfig] RemoteConfig requestData:[" + requestData + "]");

            ConnectionProcessor cp = requestQueueProvider.createConnectionProcessor();
            final boolean networkingIsEnabled = cp.configProvider_.getNetworkingEnabled();

            (new ImmediateRequestMaker()).doWork(requestData, "/o/sdk", cp, requestShouldBeDelayed, networkingIsEnabled, new ImmediateRequestMaker.InternalImmediateRequestCallback() {
                @Override
                public void callback(JSONObject checkResponse) {
                    L.d("[ModuleRemoteConfig] Processing remote config received response, received response is null:[" + (checkResponse == null) + "]");
                    if (checkResponse == null) {
                        if (callback != null) {
                            callback.callback("Encountered problem while trying to reach the server, possibly no internet connection");
                        }
                        return;
                    }

                    String error = null;
                    try {
                        boolean clearOldValues = keysExcept == null && keysOnly == null;
                        mergeCheckResponseIntoCurrentValues(clearOldValues, checkResponse);
                    } catch (Exception ex) {
                        L.e("[ModuleRemoteConfig] updateRemoteConfigValues - execute, Encountered internal issue while trying to download remote config information from the server, [" + ex.toString() + "]");
                        error = "Encountered internal issue while trying to download remote config information from the server, [" + ex.toString() + "]";
                    }

                    if (callback != null) {
                        callback.callback(error);
                    }
                }
            }, L);
        } catch (Exception ex) {
            L.e("[ModuleRemoteConfig] Encountered internal error while trying to perform a remote config update. " + ex.toString());
            if (callback != null) {
                callback.callback("Encountered internal error while trying to perform a remote config update");
            }
        }
    }

    /**
     * Internal call for fetching all variants of A/B test experiments
     *
     * @param callback called after the fetch is done
     */
    void testingFetchVariantInformationInternal(@NonNull final RemoteConfigVariantCallback callback) {
        try {
            L.d("[ModuleRemoteConfig] Fetching all A/B test variants");

            if (deviceIdProvider.isTemporaryIdEnabled() || requestQueueProvider.queueContainsTemporaryIdItems() || deviceIdProvider.getDeviceId() == null) {
                L.d("[ModuleRemoteConfig] Fetching all A/B test variants was aborted, temporary device ID mode is set or device ID is null.");
                callback.callback(RequestResponse.ERROR);
                return;
            }

            // prepare request data
            String requestData = requestQueueProvider.prepareFetchAllVariants();

            L.d("[ModuleRemoteConfig] Fetching all A/B test variants requestData:[" + requestData + "]");

            ConnectionProcessor cp = requestQueueProvider.createConnectionProcessor();
            final boolean networkingIsEnabled = cp.configProvider_.getNetworkingEnabled();

            immediateRequestGenerator.CreateImmediateRequestMaker().doWork(requestData, "/i/sdk", cp, false, networkingIsEnabled, new ImmediateRequestMaker.InternalImmediateRequestCallback() {
                @Override
                public void callback(JSONObject checkResponse) {
                    L.d("[ModuleRemoteConfig] Processing Fetching all A/B test variants received response, received response is null:[" + (checkResponse == null) + "]");
                    if (checkResponse == null) {
                        callback.callback(RequestResponse.NETWORK_ISSUE);
                        return;
                    }

                    try {
                        Map<String, String[]> parsedResponse = convertVariantsJsonToMap(checkResponse);
                        variantContainer = parsedResponse;
                    } catch (Exception ex) {
                        L.e("[ModuleRemoteConfig] testingFetchVariantInformationInternal - execute, Encountered internal issue while trying to fetch information from the server, [" + ex.toString() + "]");
                    }

                    callback.callback(RequestResponse.SUCCESS);
                }
            }, L);
        } catch (Exception ex) {
            L.e("[ModuleRemoteConfig] Encountered internal error while trying to fetch all A/B test variants. " + ex.toString());
            callback.callback(RequestResponse.ERROR);
        }
    }

    void testingEnrollIntoVariantInternal(@NonNull final String key, @NonNull final String variant, @NonNull final RemoteConfigVariantCallback callback) {
        try {
            L.d("[ModuleRemoteConfig] Enrolling A/B test variants, Key/Variant pairs:[" + key + "][" + variant + "]");

            if (deviceIdProvider.isTemporaryIdEnabled() || requestQueueProvider.queueContainsTemporaryIdItems() || deviceIdProvider.getDeviceId() == null) {
                L.d("[ModuleRemoteConfig] Enrolling A/B test variants was aborted, temporary device ID mode is set or device ID is null.");
                callback.callback(RequestResponse.ERROR);
                return;
            }

            // check Key and Variant
            if (TextUtils.isEmpty(key) || TextUtils.isEmpty(variant)) {
                L.w("[ModuleRemoteConfig] Enrolling A/B test variants, Key/Variant pair is invalid. Aborting.");
                callback.callback(RequestResponse.ERROR);
                return;
            }

            // prepare request data
            String requestData = requestQueueProvider.prepareEnrollVariant(key, variant);

            L.d("[ModuleRemoteConfig] Enrolling A/B test variants requestData:[" + requestData + "]");

            ConnectionProcessor cp = requestQueueProvider.createConnectionProcessor();
            final boolean networkingIsEnabled = cp.configProvider_.getNetworkingEnabled();

            immediateRequestGenerator.CreateImmediateRequestMaker().doWork(requestData, "/i/sdk", cp, false, networkingIsEnabled, new ImmediateRequestMaker.InternalImmediateRequestCallback() {
                @Override
                public void callback(JSONObject checkResponse) {
                    L.d("[ModuleRemoteConfig] Processing Fetching all A/B test variants received response, received response is null:[" + (checkResponse == null) + "]");
                    if (checkResponse == null) {
                        callback.callback(RequestResponse.NETWORK_ISSUE);
                        return;
                    }

                    try {
                        if (!isResponseValid(checkResponse)) {
                            callback.callback(RequestResponse.NETWORK_ISSUE);
                            return;
                        }

                        // Update Remote Config
                        if (remoteConfigAutomaticUpdateEnabled) {
                            updateRemoteConfigValues(null, null, false, new RemoteConfigCallback() {
                                @Override public void callback(String error) {
                                    if (error == null) {
                                        L.d("[ModuleRemoteConfig] Updated remote config after enrolling to a variant");
                                    } else {
                                        L.e("[ModuleRemoteConfig] Attempt to update the remote config after enrolling to a variant failed:" + error.toString());
                                    }
                                }
                            });
                        }

                        callback.callback(RequestResponse.SUCCESS);
                    } catch (Exception ex) {
                        L.e("[ModuleRemoteConfig] testingEnrollIntoVariantInternal - execute, Encountered internal issue while trying to enroll to the variant, [" + ex.toString() + "]");
                    }
                }
            }, L);
        } catch (Exception ex) {
            L.e("[ModuleRemoteConfig] Encountered internal error while trying to enroll A/B test variants. " + ex.toString());
            callback.callback(RequestResponse.ERROR);
        }
    }

    /**
     * Merge the values acquired from the server into the current values.
     * Clear if needed.
     *
     * @throws Exception it throws an exception so that it is escalated upwards
     */
    void mergeCheckResponseIntoCurrentValues(boolean clearOldValues, JSONObject checkResponse) throws Exception {
        //todo iterate over all response values and print a summary of the returned keys + ideally a summary of their payload.

        //merge the new values into the current ones
        RemoteConfigValueStore rcvs = loadConfig();
        if (clearOldValues) {
            //in case of full updates, clear old values
            rcvs.values = new JSONObject();
        }
        rcvs.mergeValues(checkResponse);

        L.d("[ModuleRemoteConfig] Finished remote config processing, starting saving");

        saveConfig(rcvs);

        L.d("[ModuleRemoteConfig] Finished remote config saving");
    }

    /**
     * Checks and evaluates the response from the server
     *
     * @param responseJson - JSONObject response
     * @return
     * @throws JSONException
     */
    boolean isResponseValid(@NonNull JSONObject responseJson) throws JSONException {
        boolean result = false;

        if (responseJson.get("result").equals("Success")) {
            result = true;
        }

        return result;
    }

    /**
     * Converts A/B testing variants fetched from the server (JSONObject) into a map
     *
     * @param variantsObj - JSON Object fetched from the server
     * @return
     * @throws JSONException
     */
    static Map<String, String[]> convertVariantsJsonToMap(@NonNull JSONObject variantsObj) throws JSONException {
        // Initialize the map to store the results
        Map<String, String[]> resultMap = new HashMap<>();

        try {
            // Get the keys of the JSON object using names() method
            JSONArray keys = variantsObj.names();
            if (keys != null) {
                for (int i = 0; i < keys.length(); i++) {
                    String key = keys.getString(i);
                    Object value = variantsObj.get(key);

                    // Set the key and and an empty Array initially
                    String[] emptyArray = new String[0];
                    resultMap.put(key, emptyArray);

                    // Check if the value is a JSON array
                    if (value instanceof JSONArray) {
                        JSONArray jsonArray = (JSONArray) value;

                        // Check if the JSON array contains objects
                        if (jsonArray.length() > 0 && jsonArray.get(0) instanceof JSONObject) {
                            // Extract the values from the JSON objects
                            String[] variants = new String[jsonArray.length()];
                            int count = 0;
                            for (int j = 0; j < jsonArray.length(); j++) {
                                JSONObject variantObject = jsonArray.getJSONObject(j);
                                if (variantObject.has("name")) {
                                    variants[count] = variantObject.getString("name");
                                    count++;
                                }
                            }

                            // Map the key and its corresponding variants
                            if (count > 0) {
                                String[] filteredVariants = new String[count];
                                System.arraycopy(variants, 0, filteredVariants, 0, count);
                                resultMap.put(key, filteredVariants);
                            } // else if the JSON object had no key 'name' we return String[0]
                        } // else if values of JSON array are not JSON object(all?) or no values at all we return String[0]
                    } // else if value is not JSON array we return String[0]
                }
            }
        } catch (Exception ex) {
            Countly.sharedInstance().L.e("[ModuleRemoteConfig] convertVariantsJsonToMap, failed parsing:[" + ex.toString() + "]");
            return new HashMap<>();
        }

        return resultMap;
    }

    /*
     * Decide which keys to use
     * Useful if both 'keysExcept' and 'keysOnly' set
     * */
    @NonNull String[] prepareKeysIncludeExclude(@Nullable final String[] keysOnly, @Nullable final String[] keysExcept) {
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

    Object getValue(String key) {
        try {
            RemoteConfigValueStore rcvs = loadConfig();
            return rcvs.getValue(key);
        } catch (Exception ex) {
            L.e("[ModuleRemoteConfig] getValue, Call failed:[" + ex.toString() + "]");
            return null;
        }
    }

    void saveConfig(RemoteConfigValueStore rcvs) throws Exception {
        storageProvider.setRemoteConfigValues(rcvs.dataToString());
    }

    /**
     * @return
     * @throws Exception For some reason this might be throwing an exception
     */
    RemoteConfigValueStore loadConfig() throws Exception {
        String rcvsString = storageProvider.getRemoteConfigValues();
        //noinspection UnnecessaryLocalVariable
        RemoteConfigValueStore rcvs = RemoteConfigValueStore.dataFromString(rcvsString);
        return rcvs;
    }

    void clearValueStoreInternal() {
        storageProvider.setRemoteConfigValues("");
    }

    Map<String, Object> getAllRemoteConfigValuesInternal() {
        try {
            RemoteConfigValueStore rcvs = loadConfig();
            return rcvs.getAllValues();
        } catch (Exception ex) {
            Countly.sharedInstance().L.e("[ModuleRemoteConfig] getAllRemoteConfigValuesInternal, Call failed:[" + ex.toString() + "]");
            return null;
        }
    }

    /**
     * Gets all AB testing variants stored in the memory
     *
     * @return
     */
    Map<String, String[]> testingGetAllVariantsInternal() {
        return variantContainer;
    }

    /**
     * Get all variants for a given key if exists. Else returns an empty array.
     *
     * @param key
     * @return
     */
    String[] testingGetVariantsForKeyInternal(String key) {
        if (variantContainer.containsKey(key)) {
            return variantContainer.get(key);
        }

        return new String[0];
    }

    static class RemoteConfigValueStore {
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

    void clearAndDownloadAfterIdChange() {
        L.v("[RemoteConfig] Clearing remote config values and preparing to download after ID update");

        clearValueStoreInternal();
        if (remoteConfigAutomaticUpdateEnabled && consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
            updateRemoteConfigAfterIdChange = true;
        }
    }

    @Override
    void deviceIdChanged() {
        L.v("[RemoteConfig] Device ID changed will update values: [" + updateRemoteConfigAfterIdChange + "]");

        if (updateRemoteConfigAfterIdChange) {
            updateRemoteConfigAfterIdChange = false;
            updateRemoteConfigValues(null, null, true, null);
        }
    }

    @Override
    public void initFinished(@NonNull CountlyConfig config) {
        //update remote config_ values if automatic update is enabled and we are not in temporary id mode
        if (remoteConfigAutomaticUpdateEnabled && consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig) && !deviceIdProvider.isTemporaryIdEnabled()) {
            L.d("[RemoteConfig] Automatically updating remote config values");
            updateRemoteConfigValues(null, null, false, remoteConfigInitCallback);
        }
    }

    @Override
    public void halt() {
        remoteConfigInterface = null;
    }

    // ==================================================================
    // ==================================================================
    // INTERFACE
    // ==================================================================
    // ==================================================================

    public class RemoteConfig {
        /**
         * Clear all stored remote config_ values
         */
        public void clearStoredValues() {
            synchronized (_cly) {
                L.i("[RemoteConfig] Calling 'clearStoredValues'");

                clearValueStoreInternal();
            }
        }

        public Map<String, Object> getAllValues() {
            synchronized (_cly) {
                L.i("[RemoteConfig] Calling 'getAllValues'");

                if (!consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
                    return null;
                }

                return getAllRemoteConfigValuesInternal();
            }
        }

        /**
         * Returns all variant information as a Map<String, String[]>
         *
         * @return
         */
        public Map<String, String[]> testingGetAllVariants() {
            synchronized (_cly) {
                L.i("[RemoteConfig] Calling 'testingGetAllVariants'");

                if (!consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
                    return null;
                }

                return testingGetAllVariantsInternal();
            }
        }

        /**
         * Returns variant information for a key as a String[]
         *
         * @param key - key value to get variant information for
         * @return
         */
        public String[] testingGetVariantsForKey(String key) {
            synchronized (_cly) {
                L.i("[RemoteConfig] Calling 'testingGetVariantsForKey'");

                if (!consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
                    return null;
                }

                return testingGetVariantsForKeyInternal(key);
            }
        }

        /**
         * Fetches all variants of A/B testing experiments
         *
         * @param callback
         */
        public void testingFetchVariantInformation(RemoteConfigVariantCallback callback) {
            synchronized (_cly) {
                L.i("[RemoteConfig] Calling 'testingFetchVariantInformation'");

                if (!consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
                    return;
                }

                if (callback == null) {
                    callback = new RemoteConfigVariantCallback() {
                        @Override public void callback(RequestResponse result) {
                        }
                    };
                }

                testingFetchVariantInformationInternal(callback);
            }
        }

        /**
         * Enrolls user for a specific variant of A/B testing experiment
         *
         * @param key - key value retrieved from the fetched variants
         * @param variantName - name of the variant for the key to enroll
         * @param callback
         */
        public void testingEnrollIntoVariant(String key, String variantName, RemoteConfigVariantCallback callback) {
            synchronized (_cly) {
                L.i("[RemoteConfig] Calling 'testingEnrollIntoVariant'");

                if (!consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
                    return;
                }

                if (key == null || variantName == null) {
                    L.w("[RemoteConfig] testEnrollIntoVariant, passed key or variant is null. Aborting.");
                    return;
                }

                if (callback == null) {
                    callback = new RemoteConfigVariantCallback() {
                        @Override public void callback(RequestResponse result) {
                        }
                    };
                }

                testingEnrollIntoVariantInternal(key, variantName, callback);
            }
        }

        /**
         * Get the stored value for the provided remote config_ key
         *
         * @param key
         * @return
         */
        public Object getValueForKey(String key) {
            synchronized (_cly) {
                L.i("[RemoteConfig] Calling remoteConfigValueForKey, " + key);

                if (!consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
                    return null;
                }

                return getValue(key);
            }
        }

        /**
         * Manual remote config update call. Will update all keys except the ones provided
         *
         * @param keysToExclude
         * @param callback
         */
        public void updateExceptKeys(String[] keysToExclude, RemoteConfigCallback callback) {
            synchronized (_cly) {
                L.i("[RemoteConfig] Manually calling to updateRemoteConfig with exclude keys");

                if (!consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
                    if (callback != null) {
                        callback.callback("No consent given");
                    }
                    return;
                }
                if (keysToExclude == null) {
                    L.w("[RemoteConfig] updateRemoteConfigExceptKeys passed 'keys to ignore' array is null");
                }
                updateRemoteConfigValues(null, keysToExclude, false, callback);
            }
        }

        /**
         * Manual remote config_ update call. Will only update the keys provided.
         *
         * @param keysToInclude
         * @param callback
         */
        public void updateForKeysOnly(String[] keysToInclude, RemoteConfigCallback callback) {
            synchronized (_cly) {
                L.i("[RemoteConfig] Manually calling to updateRemoteConfig with include keys");
                if (!consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
                    if (callback != null) {
                        callback.callback("No consent given");
                    }
                    return;
                }
                if (keysToInclude == null) {
                    L.w("[RemoteConfig] updateRemoteConfigExceptKeys passed 'keys to include' array is null");
                }
                updateRemoteConfigValues(keysToInclude, null, false, callback);
            }
        }

        /**
         * Manually update remote config_ values
         *
         * @param callback
         */
        public void update(RemoteConfigCallback callback) {
            synchronized (_cly) {
                L.i("[RemoteConfig] Manually calling to updateRemoteConfig");

                if (!consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
                    return;
                }

                updateRemoteConfigValues(null, null, false, callback);
            }
        }
    }
}
