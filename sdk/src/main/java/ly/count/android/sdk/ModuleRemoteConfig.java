package ly.count.android.sdk;

import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ly.count.android.sdk.internal.RemoteConfigHelper;
import ly.count.android.sdk.internal.RemoteConfigValueStore;
import org.json.JSONException;
import org.json.JSONObject;

import static ly.count.android.sdk.ModuleConsent.ConsentChangeSource.ChangeConsentCall;

public class ModuleRemoteConfig extends ModuleBase {
    ImmediateRequestGenerator iRGenerator;
    boolean updateRemoteConfigAfterIdChange = false;
    Map<String, String[]> variantContainer = new HashMap<>(); // Stores the fetched A/B test variants
    Map<String, ExperimentInformation> experimentContainer = new HashMap<>(); // Stores the fetched A/B test information (includes exp ID, description etc.)
    RemoteConfig remoteConfigInterface = null;

    //if set to true, it will automatically download remote configs on module startup
    boolean automaticDownloadTriggersEnabled;

    // if set to true we should add 'oi=1' to our RC download call
    boolean autoEnrollEnabled;

    boolean remoteConfigValuesShouldBeCached = false;

    List<RCDownloadCallback> downloadCallbacks = new ArrayList<>(2);

    public final static String variantObjectNameKey = "name";

    @Nullable
    Map<String, String> metricOverride = null;

    ModuleRemoteConfig(Countly cly, final CountlyConfig config) {
        super(cly, config);
        L.v("[ModuleRemoteConfig] Initialising");

        metricOverride = config.metricOverride;
        iRGenerator = config.immediateRequestGenerator;

        L.d("[ModuleRemoteConfig] Setting if remote config Automatic triggers enabled, " + config.enableRemoteConfigAutomaticDownloadTriggers + ", caching enabled: " + config.enableRemoteConfigValueCaching + ", auto enroll enabled: " + config.enableAutoEnrollFlag);
        automaticDownloadTriggersEnabled = config.enableRemoteConfigAutomaticDownloadTriggers;
        remoteConfigValuesShouldBeCached = config.enableRemoteConfigValueCaching;
        autoEnrollEnabled = config.enableAutoEnrollFlag;

        downloadCallbacks.addAll(config.remoteConfigGlobalCallbackList);

        if (config.remoteConfigCallbackLegacy != null) {
            downloadCallbacks.add((downloadResult, error, fullValueUpdate, downloadedValues) -> config.remoteConfigCallbackLegacy.callback(error));
        }

        remoteConfigInterface = new RemoteConfig();
    }

    /**
     * Internal call for updating remote config keys
     *
     * @param keysOnly set if these are the only keys to update
     * @param keysExcept set if these keys should be ignored from the update
     * @param devProvidedCallback dev provided callback that is called after the update is done
     */
    void updateRemoteConfigValues(@Nullable final String[] keysOnly, @Nullable final String[] keysExcept, final boolean useLegacyAPI, @Nullable final RCDownloadCallback devProvidedCallback) {
        L.d("[ModuleRemoteConfig] Updating remote config values, legacyAPI:[" + useLegacyAPI + "]");

        String[] preparedKeys = RemoteConfigHelper.prepareKeysIncludeExclude(keysOnly, keysExcept, L);
        boolean fullUpdate = (preparedKeys[0] == null || preparedKeys[0].length() == 0) && (preparedKeys[1] == null || preparedKeys[1].length() == 0);

        try {
            // checks
            if (deviceIdProvider.getDeviceId() == null) {
                //device ID is null, abort
                L.d("[ModuleRemoteConfig] RemoteConfig value update was aborted, deviceID is null");
                NotifyDownloadCallbacks(devProvidedCallback, RequestResult.Error, "Can't complete call, device ID is null", fullUpdate, null);
                return;
            }

            if (deviceIdProvider.isTemporaryIdEnabled() || requestQueueProvider.queueContainsTemporaryIdItems()) {
                //temporary id mode enabled, abort
                L.d("[ModuleRemoteConfig] RemoteConfig value update was aborted, temporary device ID mode is set");
                NotifyDownloadCallbacks(devProvidedCallback, RequestResult.Error, "Can't complete call, temporary device ID is set", fullUpdate, null);
                return;
            }

            //prepare metrics and request data
            String preparedMetrics = deviceInfo.getMetrics(_cly.context_, metricOverride);
            String requestData;

            if (useLegacyAPI) {
                requestData = requestQueueProvider.prepareRemoteConfigRequestLegacy(preparedKeys[0], preparedKeys[1], preparedMetrics);
            } else {
                requestData = requestQueueProvider.prepareRemoteConfigRequest(preparedKeys[0], preparedKeys[1], preparedMetrics, autoEnrollEnabled);
            }
            L.d("[ModuleRemoteConfig] RemoteConfig requestData:[" + requestData + "]");

            ConnectionProcessor cp = requestQueueProvider.createConnectionProcessor();
            final boolean networkingIsEnabled = cp.configProvider_.getNetworkingEnabled();

            iRGenerator.CreateImmediateRequestMaker().doWork(requestData, "/o/sdk", cp, false, networkingIsEnabled, checkResponse -> {
                L.d("[ModuleRemoteConfig] Processing remote config received response, received response is null:[" + (checkResponse == null) + "]");
                if (checkResponse == null) {
                    NotifyDownloadCallbacks(devProvidedCallback, RequestResult.Error, "Encountered problem while trying to reach the server, possibly no internet connection", fullUpdate, null);
                    return;
                }

                String error = null;
                Map<String, RCData> newRC = RemoteConfigHelper.DownloadedValuesIntoMap(checkResponse);

                try {
                    boolean clearOldValues = keysExcept == null && keysOnly == null;
                    mergeCheckResponseIntoCurrentValues(clearOldValues, newRC);
                } catch (Exception ex) {
                    L.e("[ModuleRemoteConfig] updateRemoteConfigValues - execute, Encountered internal issue while trying to download remote config information from the server, [" + ex.toString() + "]");
                    error = "Encountered internal issue while trying to download remote config information from the server, [" + ex.toString() + "]";
                }

                NotifyDownloadCallbacks(devProvidedCallback, error == null ? RequestResult.Success : RequestResult.Error, error, fullUpdate, newRC);
            }, L);
        } catch (Exception ex) {
            L.e("[ModuleRemoteConfig] Encountered internal error while trying to perform a remote config update. " + ex.toString());
            NotifyDownloadCallbacks(devProvidedCallback, RequestResult.Error, "Encountered internal error while trying to perform a remote config update", fullUpdate, null);
        }
    }

    /**
     * Internal function to form and send a request to enroll user for given keys
     *
     * @param keys
     */
    void enrollIntoABTestsForKeysInternal(@NonNull String[] keys) {
        L.d("[ModuleRemoteConfig] Enrolling user for the given keys:" + keys);

        if (deviceIdProvider.isTemporaryIdEnabled() || requestQueueProvider.queueContainsTemporaryIdItems() || deviceIdProvider.getDeviceId() == null) {
            L.d("[ModuleRemoteConfig] Enrolling user was aborted, temporary device ID mode is set or device ID is null.");
            return;
        }

        requestQueueProvider.enrollToKeys(keys);
    }

    /**
     * Internal function to form and send the request to remove user from A/B testes for given keys
     *
     * @param keys
     */
    void exitABTestsForKeysInternal(@NonNull String[] keys) {
        L.d("[ModuleRemoteConfig] Removing user for the tests with given keys:" + keys);

        if (deviceIdProvider.isTemporaryIdEnabled() || requestQueueProvider.queueContainsTemporaryIdItems() || deviceIdProvider.getDeviceId() == null) {
            L.d("[ModuleRemoteConfig] Removing user from tests was aborted, temporary device ID mode is set or device ID is null.");
            return;
        }

        requestQueueProvider.exitForKeys(keys);
    }

    /**
     * Internal call for fetching all variants of A/B test experiments
     * There are 2 endpoints that can be used:
     *
     * @param callback called after the fetch is done
     * @param shouldFetchExperimentInfo if true this call would fetch experiment information including the variants
     */
    void testingFetchVariantInformationInternal(@NonNull final RCVariantCallback callback, final boolean shouldFetchExperimentInfo) {
        try {
            L.d("[ModuleRemoteConfig] Fetching all A/B test variants/info");

            if (deviceIdProvider.isTemporaryIdEnabled() || requestQueueProvider.queueContainsTemporaryIdItems() || deviceIdProvider.getDeviceId() == null) {
                L.d("[ModuleRemoteConfig] Fetching all A/B test variants was aborted, temporary device ID mode is set or device ID is null.");
                callback.callback(RequestResult.Error, "Temporary device ID mode is set or device ID is null.");
                return;
            }

            // prepare request data
            String requestData = shouldFetchExperimentInfo ? requestQueueProvider.prepareFetchAllExperiments() : requestQueueProvider.prepareFetchAllVariants();

            L.d("[ModuleRemoteConfig] Fetching all A/B test variants/info requestData:[" + requestData + "]");

            ConnectionProcessor cp = requestQueueProvider.createConnectionProcessor();
            final boolean networkingIsEnabled = cp.configProvider_.getNetworkingEnabled();

            iRGenerator.CreateImmediateRequestMaker().doWork(requestData, "/o/sdk", cp, false, networkingIsEnabled, checkResponse -> {
                L.d("[ModuleRemoteConfig] Processing Fetching all A/B test variants/info received response, received response is null:[" + (checkResponse == null) + "]");
                if (checkResponse == null) {
                    callback.callback(RequestResult.NetworkIssue, "Encountered problem while trying to reach the server, possibly no internet connection");
                    return;
                }

                if (shouldFetchExperimentInfo) {
                    experimentContainer = RemoteConfigHelper.convertExperimentInfoJsonToMap(checkResponse, L);
                } else {
                    variantContainer = RemoteConfigHelper.convertVariantsJsonToMap(checkResponse, L);
                }

                callback.callback(RequestResult.Success, null);
            }, L);
        } catch (Exception ex) {
            L.e("[ModuleRemoteConfig] Encountered internal error while trying to fetch all A/B test variants/info. " + ex.toString());
            callback.callback(RequestResult.Error, "Encountered internal error while trying to fetch all A/B test variants/info.");
        }
    }

    void testingEnrollIntoVariantInternal(@NonNull final String key, @NonNull final String variant, @NonNull final RCVariantCallback callback) {
        try {
            L.d("[ModuleRemoteConfig] Enrolling A/B test variants, Key/Variant pairs:[" + key + "][" + variant + "]");

            if (deviceIdProvider.isTemporaryIdEnabled() || requestQueueProvider.queueContainsTemporaryIdItems() || deviceIdProvider.getDeviceId() == null) {
                L.d("[ModuleRemoteConfig] Enrolling A/B test variants was aborted, temporary device ID mode is set or device ID is null.");
                callback.callback(RequestResult.Error, "Temporary device ID mode is set or device ID is null.");
                return;
            }

            // check Key and Variant
            if (TextUtils.isEmpty(key) || TextUtils.isEmpty(variant)) {
                L.w("[ModuleRemoteConfig] Enrolling A/B test variants, Key/Variant pair is invalid. Aborting.");
                callback.callback(RequestResult.Error, "Provided key/variant pair is invalid.");
                return;
            }

            // prepare request data
            String requestData = requestQueueProvider.prepareEnrollVariant(key, variant);

            L.d("[ModuleRemoteConfig] Enrolling A/B test variants requestData:[" + requestData + "]");

            ConnectionProcessor cp = requestQueueProvider.createConnectionProcessor();
            final boolean networkingIsEnabled = cp.configProvider_.getNetworkingEnabled();

            iRGenerator.CreateImmediateRequestMaker().doWork(requestData, "/i", cp, false, networkingIsEnabled, checkResponse -> {
                L.d("[ModuleRemoteConfig] Processing Fetching all A/B test variants received response, received response is null:[" + (checkResponse == null) + "]");
                if (checkResponse == null) {
                    callback.callback(RequestResult.NetworkIssue, "Encountered problem while trying to reach the server, possibly no internet connection");
                    return;
                }

                try {
                    if (!isResponseValid(checkResponse)) {
                        callback.callback(RequestResult.NetworkIssue, "Bad response from the server:" + checkResponse.toString());
                        return;
                    }

                    RCAutomaticDownloadTrigger(true);//todo afterwards cache only that one key

                    callback.callback(RequestResult.Success, null);
                } catch (Exception ex) {
                    L.e("[ModuleRemoteConfig] testingEnrollIntoVariantInternal - execute, Encountered internal issue while trying to enroll to the variant, [" + ex.toString() + "]");
                    callback.callback(RequestResult.Error, "Encountered internal error while trying to take care of the A/B test variant enrolment.");
                }
            }, L);
        } catch (Exception ex) {
            L.e("[ModuleRemoteConfig] Encountered internal error while trying to enroll A/B test variants. " + ex.toString());
            callback.callback(RequestResult.Error, "Encountered internal error while trying to enroll A/B test variants.");
        }
    }

    /**
     * Merge the values acquired from the server into the current values.
     * Clear if needed.
     *
     * @throws Exception it throws an exception so that it is escalated upwards
     */
    void mergeCheckResponseIntoCurrentValues(boolean clearOldValues, @NonNull Map<String, RCData> newRC) {
        //todo iterate over all response values and print a summary of the returned keys + ideally a summary of their payload.

        //merge the new values into the current ones
        RemoteConfigValueStore rcvs = loadConfig();
        rcvs.mergeValues(newRC, clearOldValues);

        L.d("[ModuleRemoteConfig] Finished remote config processing, starting saving");

        saveConfig(rcvs);

        L.d("[ModuleRemoteConfig] Finished remote config saving");
    }

    /**
     * Checks and evaluates the response from the server
     *
     * @param responseJson - JSONObject response
     * @return
     */
    boolean isResponseValid(@NonNull JSONObject responseJson) {
        boolean result = false;

        try {
            if (responseJson.get("result").equals("Success")) {
                result = true;
            }
        } catch (JSONException e) {
            L.e("[ModuleRemoteConfig] isResponseValid, encountered issue, " + e);
            return false;
        }

        return result;
    }

    RCData getRCValue(@NonNull String key) {
        try {
            RemoteConfigValueStore rcvs = loadConfig();
            return rcvs.getValue(key);
        } catch (Exception ex) {
            L.e("[ModuleRemoteConfig] getValue, Call failed:[" + ex.toString() + "]");
            return new RCData(null, true);
        }
    }

    Object getRCValueLegacy(@NonNull String key) {
        try {
            RemoteConfigValueStore rcvs = loadConfig();
            return rcvs.getValueLegacy(key);
        } catch (Exception ex) {
            L.e("[ModuleRemoteConfig] getValueLegacy, Call failed:[" + ex.toString() + "]");
            return null;
        }
    }

    void saveConfig(@NonNull RemoteConfigValueStore rcvs) {
        storageProvider.setRemoteConfigValues(rcvs.dataToString());
    }

    /**
     * @return
     * @throws Exception For some reason this might be throwing an exception
     */
    @NonNull RemoteConfigValueStore loadConfig() {
        String rcvsString = storageProvider.getRemoteConfigValues();
        //noinspection UnnecessaryLocalVariable
        RemoteConfigValueStore rcvs = RemoteConfigValueStore.dataFromString(rcvsString, remoteConfigValuesShouldBeCached);
        return rcvs;
    }

    void clearValueStoreInternal() {
        storageProvider.setRemoteConfigValues("");
    }

    @NonNull Map<String, Object> getAllRemoteConfigValuesInternalLegacy() {
        try {
            RemoteConfigValueStore rcvs = loadConfig();
            return rcvs.getAllValuesLegacy();
        } catch (Exception ex) {
            Countly.sharedInstance().L.e("[ModuleRemoteConfig] getAllRemoteConfigValuesInternal, Call failed:[" + ex.toString() + "]");
            return new HashMap<>();
        }
    }

    @NonNull Map<String, RCData> getAllRemoteConfigValuesInternal() {
        try {
            RemoteConfigValueStore rcvs = loadConfig();
            return rcvs.getAllValues();
        } catch (Exception ex) {
            Countly.sharedInstance().L.e("[ModuleRemoteConfig] getAllRemoteConfigValuesInternal, Call failed:[" + ex.toString() + "]");
            return new HashMap<>();
        }
    }

    /**
     * Gets all AB testing variants stored in the memory
     *
     * @return
     */
    @NonNull Map<String, String[]> testingGetAllVariantsInternal() {
        return variantContainer;
    }

    /**
     * Get all variants for a given key if exists. Else returns an empty array.
     *
     * @param key
     * @return
     */
    @Nullable String[] testingGetVariantsForKeyInternal(@NonNull String key) {
        String[] variantResponse = null;
        if (variantContainer.containsKey(key)) {
            variantResponse = variantContainer.get(key);
        }

        return variantResponse;
    }

    void clearAndDownloadAfterIdChange(boolean valuesShouldBeCacheCleared) {
        L.v("[RemoteConfig] Clearing remote config values and preparing to download after ID update, " + valuesShouldBeCacheCleared);

        if (valuesShouldBeCacheCleared) {
            CacheOrClearRCValuesIfNeeded();
        }
        if (automaticDownloadTriggersEnabled && consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
            updateRemoteConfigAfterIdChange = true;
        }
    }

    void CacheOrClearRCValuesIfNeeded() {
        L.v("[RemoteConfig] CacheOrClearRCValuesIfNeeded, cacheclearing values");
        RemoteConfigValueStore rc = loadConfig();
        rc.cacheClearValues();
        saveConfig(rc);
    }

    void NotifyDownloadCallbacks(RCDownloadCallback devProvidedCallback, RequestResult requestResult, String message, boolean fullUpdate, Map<String, RCData> downloadedValues) {
        for (RCDownloadCallback callback : downloadCallbacks) {
            callback.callback(requestResult, message, fullUpdate, downloadedValues);
        }

        if (devProvidedCallback != null) {
            devProvidedCallback.callback(requestResult, message, fullUpdate, downloadedValues);
        }
    }

    void RCAutomaticDownloadTrigger(boolean cacheClearOldValues) {
        if (cacheClearOldValues) {
            CacheOrClearRCValuesIfNeeded();
        }

        if (automaticDownloadTriggersEnabled && consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
            L.d("[RemoteConfig] Automatically updating remote config values");
            updateRemoteConfigValues(null, null, false, null);
        } else {
            L.v("[RemoteConfig] Automatic RC update trigger skipped");
        }
    }

    @Override
    void onConsentChanged(@NonNull final List<String> consentChangeDelta, final boolean newConsent, @NonNull final ModuleConsent.ConsentChangeSource changeSource) {
        if (consentChangeDelta.contains(Countly.CountlyFeatureNames.remoteConfig) && changeSource == ChangeConsentCall) {
            if (newConsent) {
                //if consent was just given trigger automatic RC download if needed
                RCAutomaticDownloadTrigger(false);
            } else {
                L.d("[RemoteConfig] removing remote-config consent. Clearing stored values");
                clearValueStoreInternal();
                // if consent is removed, we should clear remote config values
            }
        }
    }

    @Override
    void deviceIdChanged() {
        L.v("[RemoteConfig] Device ID changed will update values: [" + updateRemoteConfigAfterIdChange + "]");

        if (updateRemoteConfigAfterIdChange) {
            updateRemoteConfigAfterIdChange = false;
            RCAutomaticDownloadTrigger(true);
        }
    }

    @Override
    public void initFinished(@NonNull CountlyConfig config) {
        //update remote config_ values if automatic update is enabled and we are not in temporary id mode
        if (!deviceIdProvider.isTemporaryIdEnabled()) {
            RCAutomaticDownloadTrigger(false);
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
         *
         * @deprecated Use "clearAll"
         */
        public void clearStoredValues() {
            synchronized (_cly) {
                L.i("[RemoteConfig] clearStoredValues");

                clearValueStoreInternal();
            }
        }

        /**
         * @return
         * @deprecated You should use "getValues"
         */
        public Map<String, Object> getAllValues() {
            synchronized (_cly) {
                L.i("[RemoteConfig] getAllValues");

                return getAllRemoteConfigValuesInternalLegacy();
            }
        }

        /**
         * Get the stored value for the provided remote config_ key
         *
         * @param key
         * @return
         * @deprecated You should use "getValue"
         */
        public Object getValueForKey(String key) {
            synchronized (_cly) {
                L.i("[RemoteConfig] remoteConfigValueForKey, " + key);

                return getRCValueLegacy(key);
            }
        }

        /**
         * Manual remote config update call. Will update all keys except the ones provided
         *
         * @param keysToExclude
         * @param callback
         * @deprecated You should use "downloadOmittingKeys"
         */
        public void updateExceptKeys(String[] keysToExclude, RemoteConfigCallback callback) {
            synchronized (_cly) {
                L.i("[RemoteConfig] updateExceptKeys");

                if (!consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
                    if (callback != null) {
                        callback.callback("No consent given");
                    }
                    return;
                }
                if (keysToExclude == null) {
                    L.w("[RemoteConfig] updateExceptKeys passed 'keys to ignore' array is null");
                }

                RCDownloadCallback innerCall = (downloadResult, error, fullValueUpdate, downloadedValues) -> {
                    if (callback != null) {
                        callback.callback(error);
                    }
                };

                updateRemoteConfigValues(null, keysToExclude, true, innerCall);
            }
        }

        /**
         * Manual remote config update call. Will only update the keys provided.
         *
         * @param keysToInclude
         * @param callback
         * @deprecated You should use "downloadSpecificKeys"
         */
        public void updateForKeysOnly(String[] keysToInclude, RemoteConfigCallback callback) {
            synchronized (_cly) {
                L.i("[RemoteConfig] updateForKeysOnly");
                if (!consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
                    if (callback != null) {
                        callback.callback("No consent given");
                    }
                    return;
                }
                if (keysToInclude == null) {
                    L.w("[RemoteConfig] updateForKeysOnly passed 'keys to include' array is null");
                }

                RCDownloadCallback innerCall = (downloadResult, error, fullValueUpdate, downloadedValues) -> {
                    if (callback != null) {
                        callback.callback(error);
                    }
                };

                updateRemoteConfigValues(keysToInclude, null, true, innerCall);
            }
        }

        /**
         * Manually update remote config values
         *
         * @param callback
         * @deprecated You should use "downloadAllKeys"
         */
        public void update(RemoteConfigCallback callback) {
            synchronized (_cly) {
                L.i("[RemoteConfig] update");

                if (!consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
                    if (callback != null) {
                        callback.callback("No consent given");
                    }
                    return;
                }

                RCDownloadCallback innerCall = (downloadResult, error, fullValueUpdate, downloadedValues) -> {
                    if (callback != null) {
                        callback.callback(error);
                    }
                };

                updateRemoteConfigValues(null, null, true, innerCall);
            }
        }

        /**
         * Manual remote config call that will initiate a download of all except the given remote config keys.
         * If no keys are provided then it will download all available RC values
         *
         * @param keysToOmit A list of keys that need to be downloaded
         * @param callback This is called when the operation concludes
         */
        public void downloadOmittingKeys(@Nullable String[] keysToOmit, @Nullable RCDownloadCallback callback) {
            synchronized (_cly) {
                L.i("[RemoteConfig] downloadOmittingKeys");

                if (!consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
                    if (callback != null) {
                        callback.callback(RequestResult.Error, null, false, null);
                    }
                    return;
                }
                if (keysToOmit == null) {
                    L.w("[RemoteConfig] downloadOmittingKeys passed 'keys to ignore' array is null");
                }

                if (callback == null) {
                    callback = (downloadResult, error, fullValueUpdate, downloadedValues) -> {
                    };
                }

                updateRemoteConfigValues(null, keysToOmit, false, callback);
            }
        }

        /**
         * Manual remote config call that will initiate a download of only the given remote config keys.
         * If no keys are provided then it will download all available RC values
         *
         * @param keysToInclude Keys for which the RC should be initialized
         * @param callback This is called when the operation concludes
         */
        public void downloadSpecificKeys(@Nullable String[] keysToInclude, @Nullable RCDownloadCallback callback) {
            synchronized (_cly) {
                L.i("[RemoteConfig] downloadSpecificKeys");
                if (!consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
                    if (callback != null) {
                        callback.callback(RequestResult.Error, null, false, null);
                    }
                    return;
                }
                if (keysToInclude == null) {
                    L.w("[RemoteConfig] downloadSpecificKeys passed 'keys to include' array is null");
                }

                if (callback == null) {
                    callback = (downloadResult, error, fullValueUpdate, downloadedValues) -> {
                    };
                }

                updateRemoteConfigValues(keysToInclude, null, false, callback);
            }
        }

        /**
         * Manual remote config call that will initiate a download of all available remote config keys.
         *
         * @param callback This is called when the operation concludes
         */
        public void downloadAllKeys(@Nullable RCDownloadCallback callback) {
            synchronized (_cly) {
                L.i("[RemoteConfig] downloadAllKeys");

                if (!consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
                    if (callback != null) {
                        callback.callback(RequestResult.Error, null, true, null);
                    }
                    return;
                }

                if (callback == null) {
                    callback = (downloadResult, error, fullValueUpdate, downloadedValues) -> {
                    };
                }

                updateRemoteConfigValues(null, null, false, callback);
            }
        }

        /**
         * Returns all available remote config values
         *
         * @return The available RC values
         */
        public @NonNull Map<String, RCData> getValues() {
            synchronized (_cly) {
                L.i("[RemoteConfig] getValues");

                return getAllRemoteConfigValuesInternal();
            }
        }

        /**
         * Returns all available remote config values and enrolls to A/B tests for those values
         *
         * @return The available RC values
         */
        public @NonNull Map<String, RCData> getAllValuesAndEnroll() {
            synchronized (_cly) {
                L.i("[RemoteConfig] getAllValuesAndEnroll");
                Map<String, RCData> values = getAllRemoteConfigValuesInternal();

                if (values.isEmpty()) {
                    L.i("[RemoteConfig] getAllValuesAndEnroll, No value to enroll");
                } else {
                    // assuming the values is not empty enroll for the keys
                    Set<String> setOfKeys = values.keySet();
                    String[] arrayOfKeys = new String[setOfKeys.size()];

                    // set to array
                    int i = 0;
                    for (String key : setOfKeys) {
                        arrayOfKeys[i++] = key;
                    }

                    // enroll
                    enrollIntoABTestsForKeys(arrayOfKeys);
                }

                return values;
            }
        }

        /**
         * Return the remote config value for a specific key
         *
         * @param key Key for which the remote config value needs to be returned
         * @return The returned value. If no value existed for the key then the inner object (value) will be returned as "null"
         */
        public @NonNull RCData getValue(final @Nullable String key) {
            synchronized (_cly) {
                L.i("[RemoteConfig] getValue, key:[" + key + "]");

                if (key == null || key.equals("")) {
                    L.i("[RemoteConfig] getValue, A valid key should be provided to get its value.");
                    return new RCData(null, true);
                }

                return getRCValue(key);
            }
        }

        /**
         * Returns the remote config value for a specific key and enrolls to A/B tests for it
         *
         * @param key Key for which the remote config value needs to be returned
         * @return The returned value. If no value existed for the key then the inner object will be returned as "null"
         */
        public @NonNull RCData getValueAndEnroll(@Nullable String key) {
            synchronized (_cly) {
                L.i("[RemoteConfig] getValueAndEnroll, key:[" + key + "]");

                if (key == null || key.equals("")) {
                    L.i("[RemoteConfig] getValueAndEnroll, A valid key should be provided to get its value.");
                    return new RCData(null, true);
                }

                RCData data = getRCValue(key);

                if (data.value == null) {
                    L.i("[RemoteConfig] getValueAndEnroll, No value to enroll");
                } else {
                    // assuming value is not null enroll to key
                    String[] arrayOfKeys = { key };
                    enrollIntoABTestsForKeys(arrayOfKeys);
                }

                return data;
            }
        }

        /**
         * Enrolls user to AB tests of the given keys.
         *
         * @param keys - String array of keys (parameters)
         */
        public void enrollIntoABTestsForKeys(@Nullable String[] keys) {
            synchronized (_cly) {
                L.i("[RemoteConfig] enrollIntoABTestsForKeys");

                if (keys == null || keys.length == 0) {
                    L.w("[RemoteConfig] enrollIntoABTestsForKeys, A key should be provided to enroll the user.");
                    return;
                }

                if (!consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
                    return;
                }

                enrollIntoABTestsForKeysInternal(keys);
            }
        }

        /**
         * Removes user from A/B tests for the given keys. If no key provided would remove the user from all tests.
         *
         * @param keys - String array of keys (parameters)
         */
        public void exitABTestsForKeys(@Nullable String[] keys) {
            synchronized (_cly) {
                L.i("[RemoteConfig] exitABTestsForKeys");

                if (keys == null) {
                    keys = new String[0];
                }

                if (!consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
                    return;
                }

                exitABTestsForKeysInternal(keys);
            }
        }

        /**
         * Register a global callback for when download operations have finished
         *
         * @param callback The callback that should be added
         */
        public void registerDownloadCallback(@Nullable RCDownloadCallback callback) {
            synchronized (_cly) {
                L.i("[RemoteConfig] registerDownloadCallback");
                downloadCallbacks.add(callback);
            }
        }

        /**
         * Unregister a global download callback
         *
         * @param callback The callback that should be removed
         */
        public void removeDownloadCallback(@Nullable RCDownloadCallback callback) {
            synchronized (_cly) {
                L.i("[RemoteConfig] removeDownloadCallback");
                downloadCallbacks.remove(callback);
            }
        }

        /**
         * Clear all stored remote config values.
         */
        public void clearAll() {
            synchronized (_cly) {
                L.i("[RemoteConfig] clearAll");
                clearStoredValues();
            }
        }

        /**
         * Returns all variant information as a Map<String, String[]>
         *
         * This call is not meant for production. It should only be used to facilitate testing of A/B test experiments.
         *
         * @return Return the information of all available variants
         */
        public @NonNull Map<String, String[]> testingGetAllVariants() {
            synchronized (_cly) {
                L.i("[RemoteConfig] testingGetAllVariants");

                return testingGetAllVariantsInternal();
            }
        }

        /**
         * Returns all experiment information as a Map<String, ExperimentInformation>
         *
         * This call is not meant for production. It should only be used to facilitate testing of A/B test experiments.
         *
         * @return Return the information of all available variants
         */
        public @NonNull Map<String, ExperimentInformation> testingGetAllExperimentInfo() {
            synchronized (_cly) {
                L.i("[RemoteConfig] testingGetAllExperimentInfo");

                return experimentContainer;
            }
        }

        /**
         * Returns variant information for a key as a String[]
         *
         * This call is not meant for production. It should only be used to facilitate testing of A/B test experiments.
         *
         * @param key - key value to get variant information for
         * @return If returns the stored variants for the given key. Returns "null" if there are no variants for that key.
         */
        public @Nullable String[] testingGetVariantsForKey(@Nullable String key) {
            synchronized (_cly) {
                L.i("[RemoteConfig] testingGetVariantsForKey");

                if (key == null) {
                    L.i("[RemoteConfig] testingGetVariantsForKey, provided variant key can not be null");
                    return null;
                }

                return testingGetVariantsForKeyInternal(key);
            }
        }

        /**
         * Download all variants of A/B testing experiments
         *
         * This call is not meant for production. It should only be used to facilitate testing of A/B test experiments.
         *
         * @param completionCallback this callback will be called when the network request finished
         */
        public void testingDownloadVariantInformation(@Nullable RCVariantCallback completionCallback) {
            synchronized (_cly) {
                L.i("[RemoteConfig] testingFetchVariantInformation");

                if (!consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
                    return;
                }

                if (completionCallback == null) {
                    completionCallback = (result, error) -> {
                    };
                }

                testingFetchVariantInformationInternal(completionCallback, false);
            }
        }

        /**
         * Download all A/B testing experiments information
         *
         * This call is not meant for production. It should only be used to facilitate testing of A/B test experiments.
         *
         * @param completionCallback this callback will be called when the network request finished
         */
        public void testingDownloadExperimentInformation(@Nullable RCVariantCallback completionCallback) {
            synchronized (_cly) {
                L.i("[RemoteConfig] testingDownloadExperimentInformation");

                if (!consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
                    return;
                }

                if (completionCallback == null) {
                    completionCallback = (result, error) -> {
                    };
                }

                testingFetchVariantInformationInternal(completionCallback, true);
            }
        }

        /**
         * Enrolls user for a specific variant of A/B testing experiment
         *
         * This call is not meant for production. It should only be used to facilitate testing of A/B test experiments.
         *
         * @param keyName - key value retrieved from the fetched variants
         * @param variantName - name of the variant for the key to enroll
         * @param completionCallback
         */
        public void testingEnrollIntoVariant(@Nullable String keyName, String variantName, @Nullable RCVariantCallback completionCallback) {
            synchronized (_cly) {
                L.i("[RemoteConfig] testingEnrollIntoVariant");

                if (!consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
                    return;
                }

                if (keyName == null || variantName == null) {
                    L.w("[RemoteConfig] testEnrollIntoVariant, passed key or variant is null. Aborting.");
                    return;
                }

                if (completionCallback == null) {
                    completionCallback = (result, error) -> {
                    };
                }

                testingEnrollIntoVariantInternal(keyName, variantName, completionCallback);
            }
        }
    }
}
