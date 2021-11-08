package ly.count.android.sdk;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ModuleRemoteConfig extends ModuleBase {
    boolean updateRemoteConfigAfterIdChange = false;

    RemoteConfig remoteConfigInterface = null;

    //if set to true, it will automatically download remote configs on module startup
    boolean remoteConfigAutomaticUpdateEnabled = false;
    RemoteConfigCallback remoteConfigInitCallback = null;

    ModuleRemoteConfig(Countly cly, final CountlyConfig config) {
        super(cly, config);
        L.v("[ModuleRemoteConfig] Initialising");

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
    void updateRemoteConfigValues(final String[] keysOnly, final String[] keysExcept, final boolean requestShouldBeDelayed, final RemoteConfigCallback callback) {
        L.d("[ModuleRemoteConfig] Updating remote config values, requestShouldBeDelayed:[" + requestShouldBeDelayed + "]");

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

        String[] preparedKeys = prepareKeysIncludeExclude(keysOnly, keysExcept);
        String requestData = requestQueueProvider.prepareRemoteConfigRequest(preparedKeys[0], preparedKeys[1]);
        L.d("[ModuleRemoteConfig] RemoteConfig requestData:[" + requestData + "]");

        ConnectionProcessor cp = requestQueueProvider.createConnectionProcessor();

        (new ImmediateRequestMaker()).execute(requestData, "/o/sdk", cp, requestShouldBeDelayed, new ImmediateRequestMaker.InternalFeedbackRatingCallback() {
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
                    L.e("[ModuleRemoteConfig] updateRemoteConfigValues - execute, Encountered critical issue while trying to download remote config information from the server, [" + ex.toString() + "]");
                    error = "Encountered critical issue while trying to download remote config information from the server, [" + ex.toString() + "]";
                }

                if (callback != null) {
                    callback.callback(error);
                }
            }
        }, L);
    }

    /**
     * Merge the values acquired from the server into the current values.
     * Clear if needed.
     *
     * @throws Exception it throws an exception so that it is escalated upwards
     */
    void mergeCheckResponseIntoCurrentValues(boolean clearOldValues, JSONObject checkResponse) throws Exception {
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

    String[] prepareKeysIncludeExclude(final String[] keysOnly, final String[] keysExcept) {
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
    public void initFinished(CountlyConfig config) {
        //update remote config_ values if automatic update is enabled and we are not in temporary id mode
        if (remoteConfigAutomaticUpdateEnabled && consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig) && !deviceIdProvider.isTemporaryIdEnabled()) {
            L.d("[Init] Automatically updating remote config values");
            updateRemoteConfigValues(null, null, false, remoteConfigInitCallback);
        }
    }

    @Override
    public void halt() {
        remoteConfigInterface = null;
    }

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
