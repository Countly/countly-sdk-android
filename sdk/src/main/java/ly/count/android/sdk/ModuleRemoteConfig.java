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
    JSONObject variantContainer; // Stores the fetched A/B test variants
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
     * Checks if device ID is null and cancels the process if necessary
     *
     * @param message - unique message indicating where this check was done
     * @param callback - callback to report if device ID is null
     */
    void checkDeviceId(String message, @Nullable final RemoteConfigCallback callback) {
        if (deviceIdProvider.getDeviceId() == null) {
            //device ID is null, abort
            L.d("[ModuleRemoteConfig] " + message + " was aborted, deviceID is null");
            if (callback != null) {
                callback.callback("Can't complete " + message + ", device ID is null");
            }
            return;
        }
    }

    /**
     * Checks if temp ID mode is on and cancels the process if necessary
     *
     * @param message - unique message indicating where this check was done
     * @param callback - callback to report if we are in temp ID mode
     */
    void checkTempId(String message, @Nullable final RemoteConfigCallback callback) {
        if (deviceIdProvider.isTemporaryIdEnabled() || requestQueueProvider.queueContainsTemporaryIdItems()) {
            //temporary id mode enabled, abort
            L.d("[ModuleRemoteConfig] " + message + " was aborted, temporary device ID mode is set");
            if (callback != null) {
                callback.callback("Can't complete " + message + ", temporary device ID is set");
            }
            return;
        }
    }

    /**
     * Callback to be used in 'makeRequest'
     */
    public interface SuccessOperation {
        void execute(JSONObject checkResponse) throws Exception;
    }

    /**
     * Uses ImmediateRequestMaker to make a request at 'o/sdk' for a given request data. Also executes the given function
     * TODO: should be able to provide API end point too
     *
     * @param message - unique message to show where we make the request
     * @param requestData - data to send (things after '?')
     * @param requestShouldBeDelayed - if a delay necessary in case of ID change
     * @param callback - Provided callback to relay info
     * @param successOperation - Things to do if response was valid
     */
    void makeRequest(String message, String requestData, boolean requestShouldBeDelayed, @Nullable final RemoteConfigCallback callback, @Nullable SuccessOperation successOperation) {
        L.d("[ModuleRemoteConfig] " + message + " requestData:[" + requestData + "]");

        ConnectionProcessor cp = requestQueueProvider.createConnectionProcessor();
        final boolean networkingIsEnabled = cp.configProvider_.getNetworkingEnabled();

        (new ImmediateRequestMaker()).doWork(requestData, "/o/sdk", cp, requestShouldBeDelayed, networkingIsEnabled, new ImmediateRequestMaker.InternalImmediateRequestCallback() {
            @Override
            public void callback(JSONObject checkResponse) {
                L.d("[ModuleRemoteConfig] Processing " + message + " received response, received response is null:[" + (checkResponse == null) + "]");
                if (checkResponse == null) {
                    if (callback != null) {
                        callback.callback("Encountered problem while trying to reach the server, possibly no internet connection");
                    }
                    return;
                }

                String error = null;
                try {
                    if (successOperation != null) {
                        successOperation.execute(checkResponse);
                    }
                } catch (Exception ex) {
                    L.e("[ModuleRemoteConfig] " + message + " - execute, Encountered critical issue:[" + ex.toString() + "]");
                    error = "Encountered critical issue:[" + ex.toString() + "]";
                }

                if (callback != null) {
                    callback.callback(error);
                }
            }
        }, L);
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
            checkDeviceId("RemoteConfig value update", callback);
            checkTempId("RemoteConfig value update", callback);

            //prepare metrics and request data
            String preparedMetrics = deviceInfo.getMetrics(_cly.context_, deviceInfo, metricOverride);
            String[] preparedKeys = prepareKeysIncludeExclude(keysOnly, keysExcept);
            String requestData = requestQueueProvider.prepareRemoteConfigRequest(preparedKeys[0], preparedKeys[1], preparedMetrics);

            // Make immediate request
            makeRequest("Remote Config", requestData, false, callback, new SuccessOperation() {
                @Override public void execute(JSONObject checkResponse) throws Exception {
                    boolean clearOldValues = keysExcept == null && keysOnly == null;
                    mergeCheckResponseIntoCurrentValues(clearOldValues, checkResponse);
                }
            });
        } catch (Exception ex) {
            L.e("[ModuleRemoteConfig] Encountered critical error while trying to perform a remote config update. " + ex.toString());
            if (callback != null) {
                callback.callback("Encountered critical error while trying to perform a remote config update");
            }
        }
    }

    /**
     * Internal call for fetching all variants of A/B test experiments
     *
     * @param requestShouldBeDelayed this is set to true in case of fetching after a deviceId change
     * @param callback called after the fetch is done
     */
    void testFetchAllVariantsInternal(final boolean requestShouldBeDelayed, @Nullable final RemoteConfigCallback callback) {
        try {
            L.d("[ModuleRemoteConfig] Fetching all A/B test variants, requestShouldBeDelayed:[" + requestShouldBeDelayed + "]");

            checkDeviceId("Fetching all A/B test variants", callback);
            checkTempId("Fetching all A/B test variants", callback);

            // prepare request data
            String requestData = requestQueueProvider.prepareFetchAllVariants();

            // Make immediate request
            makeRequest("Fetching all A/B test variants", requestData, false, callback, new SuccessOperation() {
                @Override public void execute(JSONObject checkResponse) throws Exception {
                    variantContainer = checkResponse;
                }
            });
        } catch (Exception ex) {
            L.e("[ModuleRemoteConfig] Encountered critical error while trying to fetch all A/B test variants. " + ex.toString());
            if (callback != null) {
                callback.callback("Encountered critical error while trying to fetch all A/B test variants");
            }
        }
    }

    void testEnrollIntoVariantInternal(@NonNull final String[] keyAndVariant, @Nullable final RemoteConfigCallback callback) {
        try {
            L.d("[ModuleRemoteConfig] Enrolling A/B test variants, Key/Variant pairs:[" + keyAndVariant.toString() + "]");

            checkDeviceId("Enrolling to A/B test variants", callback);

            // checkKeyAndVariant
            if (!(keyAndVariant.length >= 2) || TextUtils.isEmpty(keyAndVariant[0]) || TextUtils.isEmpty(keyAndVariant[1])) {
                L.w("[ModuleRemoteConfig] Enrolling A/B test variants, Key/Variant pair is invalid. Aborting.");
                return;
            }

            // prepare request data
            String requestData = requestQueueProvider.prepareEnrollVariant(keyAndVariant);

            // Make immediate request
            makeRequest("Enrolling to A/B test variants", requestData, false, callback, null);
        } catch (Exception ex) {
            L.e("[ModuleRemoteConfig] Encountered critical error while trying to enroll A/B test variants. " + ex.toString());
            if (callback != null) {
                callback.callback("Encountered critical error while trying to enroll A/B test variants");
            }
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
    JSONObject getAllRemoteConfigVariantsInternal() {
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

        public JSONObject getAllVariants() {
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
        public void testFetchAllVariants(RemoteConfigCallback callback) {
            synchronized (_cly) {
                L.i("[RemoteConfig] Calling 'testFetchAllVariants'");

                if (!consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
                    return;
                }

                testFetchAllVariantsInternal(false, callback);
                // TODO: requestShouldBeDelayed necessary?
            }
        }

        /**
         * Enrolls user for a specific variant of A/B testing experiment
         *
         * @param keysAndVariant - An array of String, first value should be the key and the second the variant name
         * @param callback
         */
        public void testEnrollIntoVariant(String[] keysAndVariant, RemoteConfigCallback callback) {
            synchronized (_cly) {
                L.i("[RemoteConfig] Calling 'testEnrollIntoVariant'");

                if (!consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
                    return;
                }

                if (keysAndVariant == null) {
                    L.w("[RemoteConfig] testEnrollIntoVariant, passed 'keysAndVariant' array is null. Aborting.");
                    return;
                }

                testEnrollIntoVariantInternal(keysAndVariant, callback);
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
