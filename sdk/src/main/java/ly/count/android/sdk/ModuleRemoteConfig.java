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
    boolean updateRemoteConfigAfterIdChange = false;
    Map<String,String[]> variantContainer; // Stores the fetched A/B test variants
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

        if (config.enableRemoteConfigAutomaticDownload) {
            L.d("[ModuleRemoteConfig] Setting if remote config Automatic download will be enabled, " + config.enableRemoteConfigAutomaticDownload);

            remoteConfigAutomaticUpdateEnabled = config.enableRemoteConfigAutomaticDownload;

            if (config.remoteConfigCallbackNew != null) {
                remoteConfigInitCallback = config.remoteConfigCallbackNew;
            } else if (config.remoteConfigCallbackOld != null) {
                remoteConfigInitCallback = new RemoteConfigCallback() {
                    @Override
                    public void callback(String error) {
                        config.remoteConfigCallbackOld.callback(error);
                    }
                };
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
    void testFetchAllVariantsInternal(@NonNull final RemoteConfigVariantCallback callback) {
        try {
            L.d("[ModuleRemoteConfig] Fetching all A/B test variants");

            if (deviceIdProvider.isTemporaryIdEnabled() || requestQueueProvider.queueContainsTemporaryIdItems() || deviceIdProvider.getDeviceId() == null) {
                L.d("[ModuleRemoteConfig] Fetching all A/B test variants was aborted, temporary device ID mode is set or device ID is null.");
                callback.callback(ImmediateRequestResponse.USAGE_ERROR);
                return;
            }

            // prepare request data
            String requestData = requestQueueProvider.prepareFetchAllVariants();

            L.d("[ModuleRemoteConfig] Fetching all A/B test variants requestData:[" + requestData + "]");

            ConnectionProcessor cp = requestQueueProvider.createConnectionProcessor();
            final boolean networkingIsEnabled = cp.configProvider_.getNetworkingEnabled();

            (new ImmediateRequestMaker()).doWork(requestData, "/o/sdk", cp, false, networkingIsEnabled, new ImmediateRequestMaker.InternalImmediateRequestCallback() {
                @Override
                public void callback(JSONObject checkResponse) {
                    L.d("[ModuleRemoteConfig] Processing Fetching all A/B test variants received response, received response is null:[" + (checkResponse == null) + "]");
                    if (checkResponse == null) {
                        callback.callback(ImmediateRequestResponse.NETWORK_ISSUE);
                        return;
                    }

                    // TODO: wrt checkResponse handle enums here

                    try {
                        Map<String,String[]> parsedResponse = convertVariantsJsonToMap(checkResponse);
                        variantContainer = parsedResponse;
                    } catch (Exception ex) {
                        L.e("[ModuleRemoteConfig] testFetchAllVariantsInternal - execute, Encountered internal issue while trying to fetch information from the server, [" + ex.toString() + "]");
                    }

                    callback.callback(ImmediateRequestResponse.SUCCESS);
                }
            }, L);
        } catch (Exception ex) {
            L.e("[ModuleRemoteConfig] Encountered internal error while trying to fetch all A/B test variants. " + ex.toString());
            callback.callback(ImmediateRequestResponse.INTERNAL_ERROR);
        }
    }

    void testEnrollIntoVariantInternal(@NonNull final String key, @NonNull final String variant, @NonNull final RemoteConfigVariantCallback callback) {
        try {
            L.d("[ModuleRemoteConfig] Enrolling A/B test variants, Key/Variant pairs:[" + key + "][" + variant + "]");

            if (deviceIdProvider.isTemporaryIdEnabled() || requestQueueProvider.queueContainsTemporaryIdItems() || deviceIdProvider.getDeviceId() == null) {
                L.d("[ModuleRemoteConfig] Enrolling A/B test variants was aborted, temporary device ID mode is set or device ID is null.");
                callback.callback(ImmediateRequestResponse.USAGE_ERROR);
                return;
            }

            // check Key and Variant
            if (TextUtils.isEmpty(key) || TextUtils.isEmpty(variant)) {
                L.w("[ModuleRemoteConfig] Enrolling A/B test variants, Key/Variant pair is invalid. Aborting.");
                callback.callback(ImmediateRequestResponse.USAGE_ERROR);
                return;
            }

            // prepare request data
            String requestData = requestQueueProvider.prepareEnrollVariant(key, variant);

            L.d("[ModuleRemoteConfig] Enrolling A/B test variants requestData:[" + requestData + "]");

            ConnectionProcessor cp = requestQueueProvider.createConnectionProcessor();
            final boolean networkingIsEnabled = cp.configProvider_.getNetworkingEnabled();

            (new ImmediateRequestMaker()).doWork(requestData, "/o/sdk", cp, false, networkingIsEnabled, new ImmediateRequestMaker.InternalImmediateRequestCallback() {
                @Override
                public void callback(JSONObject checkResponse) {
                    L.d("[ModuleRemoteConfig] Processing Fetching all A/B test variants received response, received response is null:[" + (checkResponse == null) + "]");
                    if (checkResponse == null) {
                        callback.callback(ImmediateRequestResponse.NETWORK_ISSUE);
                        return;
                    }

                    // TODO: wrt checkResponse handle enums here
                    if (remoteConfigAutomaticUpdateEnabled) {
                        // TODO: Check consent here? Should not call directly?
                        updateRemoteConfigValues(null, null, false, new RemoteConfigCallback() {
                            @Override public void callback(String error) {
                                if (error != null) {
                                    L.d("[ModuleRemoteConfig] Updated remote config after enrolling to a variant");
                                } else {
                                    L.e("[ModuleRemoteConfig] Attempt to update the remote config after enrolling to a variant failed:" + error.toString());
                                }
                            }
                        });
                    }

                    callback.callback(ImmediateRequestResponse.SUCCESS);
                }
            }, L);
        } catch (Exception ex) {
            L.e("[ModuleRemoteConfig] Encountered internal error while trying to enroll A/B test variants. " + ex.toString());
            callback.callback(ImmediateRequestResponse.INTERNAL_ERROR);
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
     * Converts A/B testing variants fetched from the server (JSONObject) into a map
     * @param variantsObj - JSON Object fetched from the server
     * @return
     * @throws JSONException
     */
    static Map<String, String[]> convertVariantsJsonToMap(@NonNull JSONObject variantsObj) throws JSONException {
        Map<String, String[]> map = new HashMap<>();

        // Iterate over the keys of the JSON object
        Iterator<String> keys = variantsObj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONArray jsonArray = variantsObj.getJSONArray(key);

            // Extract the variant names from the JSON array
            String[] variants = new String[jsonArray.length()];
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject variantObj = jsonArray.getJSONObject(i);
                String variant = variantObj.getString("name");
                variants[i] = variant;
            }

            // Add the key and variant names to the map
            map.put(key, variants);
        }

        return map;
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
    Map<String, String[]> getAllRemoteConfigVariantsInternal() {
        return variantContainer;
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

        public Map<String, String[]> getAllVariants() {
            synchronized (_cly) {
                L.i("[RemoteConfig] Calling 'getAllVariants'");

                if (!consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
                    return null;
                }

                return getAllRemoteConfigVariantsInternal();
            }
        }

        /**
         * Fetches all variants of A/B testing experiments
         *
         * @param callback
         */
        public void testFetchAllVariants(RemoteConfigVariantCallback callback) {
            synchronized (_cly) {
                L.i("[RemoteConfig] Calling 'testFetchAllVariants'");

                if (!consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
                    return;
                }

                if (callback == null) {
                    callback = new RemoteConfigVariantCallback() {
                        @Override public void callback(Enum result) {
                           }
                    };
                }

                testFetchAllVariantsInternal(callback);
            }
        }

        /**
         * Enrolls user for a specific variant of A/B testing experiment
         *
         * @param key - key value retrieved from the fetched variants
         * @param variantName - name of the variant for the key to enroll
         * @param callback
         */
        public void testEnrollIntoVariant(String key, String variantName, RemoteConfigVariantCallback callback) {
            synchronized (_cly) {
                L.i("[RemoteConfig] Calling 'testEnrollIntoVariant'");

                if (!consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
                    return;
                }

                if (key == null || variantName == null) {
                    L.w("[RemoteConfig] testEnrollIntoVariant, passed key or variant is null. Aborting.");
                    return;
                }

                if (callback == null) {
                    callback = new RemoteConfigVariantCallback() {
                        @Override public void callback(Enum result) {
                        }
                    };
                }

                testEnrollIntoVariantInternal(key, variantName, callback);
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
