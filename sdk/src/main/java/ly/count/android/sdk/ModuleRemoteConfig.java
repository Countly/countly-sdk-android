package ly.count.android.sdk;

import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ly.count.android.sdk.internal.RemoteConfigHelper;
import ly.count.android.sdk.internal.RemoteConfigValueStore;
import org.json.JSONException;
import org.json.JSONObject;

import static ly.count.android.sdk.ModuleConsent.ConsentChangeSource.ChangeConsentCall;

public class ModuleRemoteConfig extends ModuleBase {
    ImmediateRequestGenerator immediateRequestGenerator;
    boolean updateRemoteConfigAfterIdChange = false;
    Map<String, String[]> variantContainer; // Stores the fetched A/B test variants
    RemoteConfig remoteConfigInterface = null;

    //if set to true, it will automatically download remote configs on module startup
    boolean remoteConfigAutomaticUpdateEnabled = false;

    boolean remoteConfigValuesShouldBeCached = false;
    RemoteConfigCallback remoteConfigInitCallback = null;

    public final static String variantObjectNameKey = "name";

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
    void updateRemoteConfigValues(@Nullable final String[] keysOnly, @Nullable final String[] keysExcept, final boolean requestShouldBeDelayed, final boolean useLegacyAPI, @Nullable final RemoteConfigCallback callback) {
        try {
            L.d("[ModuleRemoteConfig] Updating remote config values, requestShouldBeDelayed:[" + requestShouldBeDelayed + "], legacyAPI:[" + useLegacyAPI + "]");

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
            String[] preparedKeys = RemoteConfigHelper.prepareKeysIncludeExclude(keysOnly, keysExcept, L);
            String requestData;

            if (useLegacyAPI) {
                requestData = requestQueueProvider.prepareRemoteConfigRequestLegacy(preparedKeys[0], preparedKeys[1], preparedMetrics);
            } else {
                requestData = requestQueueProvider.prepareRemoteConfigRequest(preparedKeys[0], preparedKeys[1], preparedMetrics);
            }
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

        String requestData = requestQueueProvider.prepareEnrollmentParameters(keys);
        L.d("[ModuleRemoteConfig] Enrollment requestData:[" + requestData + "]");

        ConnectionProcessor cp = requestQueueProvider.createConnectionProcessor();
        final boolean networkingIsEnabled = cp.configProvider_.getNetworkingEnabled();

        (new ImmediateRequestMaker()).doWork(requestData, "/o/sdk", cp, false, networkingIsEnabled, new ImmediateRequestMaker.InternalImmediateRequestCallback() {
            @Override
            public void callback(JSONObject checkResponse) {
                L.d("[ModuleRemoteConfig] Processing received response, received response is null:[" + (checkResponse == null) + "]");
                if (checkResponse == null) {
                    return;
                }

                try {
                    if (checkResponse.has("result") && checkResponse.getString("result").equals("Success")) {
                        L.d("[ModuleRemoteConfig]  Enrolled user for the A/B test");
                    } else {
                        L.w("[ModuleRemoteConfig]  Encountered a network error while enrolling the user for the A/B test.");
                    }
                } catch (Exception ex) {
                    L.e("[ModuleRemoteConfig] Encountered an internal error while trying to enroll the user for A/B test. " + ex.toString());
                }
            }
        }, L);
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

        String requestData = requestQueueProvider.prepareRemovalParameters(keys);
        L.d("[ModuleRemoteConfig] Removal requestData:[" + requestData + "]");

        ConnectionProcessor cp = requestQueueProvider.createConnectionProcessor();
        final boolean networkingIsEnabled = cp.configProvider_.getNetworkingEnabled();

        (new ImmediateRequestMaker()).doWork(requestData, "/o/sdk", cp, false, networkingIsEnabled, new ImmediateRequestMaker.InternalImmediateRequestCallback() {
            @Override
            public void callback(JSONObject checkResponse) {
                L.d("[ModuleRemoteConfig] Processing received response, received response is null:[" + (checkResponse == null) + "]");
                if (checkResponse == null) {
                    return;
                }

                try {
                    if (checkResponse.has("result") && checkResponse.getString("result").equals("Success")) {
                        L.d("[ModuleRemoteConfig]  Removed user from the A/B test");
                    } else {
                        L.w("[ModuleRemoteConfig]  Encountered a network error while removing the user from A/B testing.");
                    }
                } catch (Exception ex) {
                    L.e("[ModuleRemoteConfig] Encountered an internal error while trying to remove user from A/B testing. " + ex.toString());
                }
            }
        }, L);
    }

    /**
     * Internal call for fetching all variants of A/B test experiments
     *
     * @param callback called after the fetch is done
     */
    void testingFetchVariantInformationInternal(@NonNull final RCVariantCallback callback) {
        try {
            L.d("[ModuleRemoteConfig] Fetching all A/B test variants");

            if (deviceIdProvider.isTemporaryIdEnabled() || requestQueueProvider.queueContainsTemporaryIdItems() || deviceIdProvider.getDeviceId() == null) {
                L.d("[ModuleRemoteConfig] Fetching all A/B test variants was aborted, temporary device ID mode is set or device ID is null.");
                callback.callback(RequestResult.Error, "Temporary device ID mode is set or device ID is null.");
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
                        callback.callback(RequestResult.NetworkIssue, "Encountered problem while trying to reach the server, possibly no internet connection");
                        return;
                    }

                    variantContainer = RemoteConfigHelper.convertVariantsJsonToMap(checkResponse, L);

                    callback.callback(RequestResult.Success, null);
                }
            }, L);
        } catch (Exception ex) {
            L.e("[ModuleRemoteConfig] Encountered internal error while trying to fetch all A/B test variants. " + ex.toString());
            callback.callback(RequestResult.Error, "Encountered internal error while trying to fetch all A/B test variants.");
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

            immediateRequestGenerator.CreateImmediateRequestMaker().doWork(requestData, "/i/sdk", cp, false, networkingIsEnabled, new ImmediateRequestMaker.InternalImmediateRequestCallback() {
                @Override
                public void callback(JSONObject checkResponse) {
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

                        RCAutomaticDownloadTrigger(true);

                        callback.callback(RequestResult.Success, null);
                    } catch (Exception ex) {
                        L.e("[ModuleRemoteConfig] testingEnrollIntoVariantInternal - execute, Encountered internal issue while trying to enroll to the variant, [" + ex.toString() + "]");
                        callback.callback(RequestResult.Error, "Encountered internal error while trying to take care of the A/B test variant enrolment.");
                    }
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
    void mergeCheckResponseIntoCurrentValues(boolean clearOldValues, JSONObject checkResponse) throws Exception {
        //todo iterate over all response values and print a summary of the returned keys + ideally a summary of their payload.

        //merge the new values into the current ones
        RemoteConfigValueStore rcvs = loadConfig();
        rcvs.mergeValues(checkResponse, clearOldValues);

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

    Object getValue(@NonNull String key) {
        try {
            RemoteConfigValueStore rcvs = loadConfig();
            return rcvs.getValueLegacy(key);
        } catch (Exception ex) {
            L.e("[ModuleRemoteConfig] getValue, Call failed:[" + ex.toString() + "]");
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

    @NonNull Map<String, Object> getAllRemoteConfigValuesInternal() {
        try {
            RemoteConfigValueStore rcvs = loadConfig();
            return rcvs.getAllValuesLegacy();
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
    @NonNull Map<String, String[]> testingGetAllVariantsInternal() {
        return variantContainer;
    }

    /**
     * Get all variants for a given key if exists. Else returns an empty array.
     *
     * @param key
     * @return
     */
    @NonNull String[] testingGetVariantsForKeyInternal(@NonNull String key) {
        if (variantContainer.containsKey(key)) {
            return variantContainer.get(key);
        }

        return new String[0];
    }

    void clearAndDownloadAfterIdChange() {
        L.v("[RemoteConfig] Clearing remote config values and preparing to download after ID update");

        CacheOrClearRCValuesIfNeeded();
        if (remoteConfigAutomaticUpdateEnabled && consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
            updateRemoteConfigAfterIdChange = true;
        }
    }

    void CacheOrClearRCValuesIfNeeded() {
        clearValueStoreInternal();
    }

    void RCAutomaticDownloadTrigger(boolean cacheClearOldValues) {
        if (cacheClearOldValues) {
            clearValueStoreInternal();//todo finish
        }

        if (remoteConfigAutomaticUpdateEnabled && consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
            L.d("[RemoteConfig] Automatically updating remote config values");
            updateRemoteConfigValues(null, null, false, false, remoteConfigInitCallback);
        } else {
            L.v("[RemoteConfig] Automatically RC update trigger skipped");
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
         * @deprecated
         */
        public void clearStoredValues() {
            synchronized (_cly) {
                L.i("[RemoteConfig] Calling 'clearStoredValues'");

                clearValueStoreInternal();
            }
        }

        /**
         * @return
         * @deprecated
         */
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
         * @deprecated
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
         * @deprecated
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
                updateRemoteConfigValues(null, keysToExclude, false, true, callback);
            }
        }

        /**
         * Manual remote config_ update call. Will only update the keys provided.
         *
         * @param keysToInclude
         * @param callback
         * @deprecated
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
                updateRemoteConfigValues(keysToInclude, null, false, true, callback);
            }
        }

        /**
         * Manually update remote config_ values
         *
         * @param callback
         * @deprecated
         */
        public void update(RemoteConfigCallback callback) {
            synchronized (_cly) {
                L.i("[RemoteConfig] Manually calling to updateRemoteConfig");

                if (!consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
                    if (callback != null) {
                        callback.callback("No consent given");
                    }
                    return;
                }

                updateRemoteConfigValues(null, null, false, true, callback);
            }
        }

        /**
         * Manual remote config update call. Will update all keys except the ones provided
         *
         * @param keysToOmit
         * @param callback
         */
        public void DownloadOmittingKeys(String[] keysToOmit, RCDownloadCallback callback) {
            synchronized (_cly) {
                L.i("[RemoteConfig] Manually calling to updateRemoteConfig with exclude keys");

                if (!consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
                    if (callback != null) {
                        callback.callback(RequestResult.Error, null, false, null);
                    }
                    return;
                }
                if (keysToOmit == null) {
                    L.w("[RemoteConfig] updateRemoteConfigExceptKeys passed 'keys to ignore' array is null");
                }
                updateRemoteConfigValues(null, keysToOmit, false, false, null); // TODO: this callback was not expected
            }
        }

        /**
         * Manual remote config_ update call. Will only update the keys provided.
         *
         * @param keysToInclude
         * @param callback
         */
        public void DownloadSpecificKeys(String[] keysToInclude, RCDownloadCallback callback) {
            synchronized (_cly) {
                L.i("[RemoteConfig] Manually calling to updateRemoteConfig with include keys");
                if (!consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
                    if (callback != null) {
                        callback.callback(RequestResult.Error, null, false, null);
                    }
                    return;
                }
                if (keysToInclude == null) {
                    L.w("[RemoteConfig] updateRemoteConfigExceptKeys passed 'keys to include' array is null");
                }
                updateRemoteConfigValues(keysToInclude, null, false, false, null); // TODO: this callback was not expected
            }
        }

        public void DownloadAllKeys(RCDownloadCallback callback) {
            synchronized (_cly) {
                L.i("[RemoteConfig] Manually calling to update Remote Config v2");

                if (!consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
                    if (callback != null) {
                        callback.callback(RequestResult.Error, null, true, null);
                    }
                    return;
                }

                updateRemoteConfigValues(null, null, false, false, null); // TODO: this callback was not expected
            }
        }

        public @NonNull Map<String, RCData> GetAllValues() {
            synchronized (_cly) {
                L.i("[RemoteConfig] Getting all Remote config values v2");

                if (!consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
                    return new HashMap<>();
                }

                return new HashMap<>();
            }
        }

        public @NonNull RCData GetValue(String key) {
            synchronized (_cly) {
                L.i("[RemoteConfig] Getting Remote config values for key:[" + key + "] v2");

                if (!consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
                    return new RCData(null, true);
                }

                return new RCData(null, true);
            }
        }

        /**
         * Enrolls user to AB tests of the given keys.
         *
         * @param keys - String array of keys (parameters)
         */
        public void enrollIntoABTestsForKeys(String[] keys) {
            synchronized (_cly) {
                L.i("[RemoteConfig] Enrolling user into A/B tests.");

                if (keys == null || keys.length == 0) {
                    L.w("[RemoteConfig] A key should be provided to enroll the user.");
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
        public void exitABTestsForKeys(String[] keys) {
            synchronized (_cly) {
                L.i("[RemoteConfig] Removing user from A/B tests.");

                if (keys == null) {
                    keys = new String[0];
                }

                if (!consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
                    return;
                }

                exitABTestsForKeysInternal(keys);
            }
        }

        public void registerDownloadCallback(RCDownloadCallback callback) {

        }

        public void removeDownloadCallback(RCDownloadCallback callback) {

        }

        public void clearAll() {

        }

        /**
         * Returns all variant information as a Map<String, String[]>
         *
         * @return
         */
        public @NonNull Map<String, String[]> testingGetAllVariants() {
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
        public @NonNull String[] testingGetVariantsForKey(String key) {
            synchronized (_cly) {
                L.i("[RemoteConfig] Calling 'testingGetVariantsForKey'");

                if (!consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
                    return null;
                }

                return testingGetVariantsForKeyInternal(key);
            }
        }

        /**
         * Download all variants of A/B testing experiments
         *
         * @param completionCallback
         */
        public void TestingDownloadVariantInformation(RCVariantCallback completionCallback) {
            synchronized (_cly) {
                L.i("[RemoteConfig] Calling 'testingFetchVariantInformation'");

                if (!consentProvider.getConsent(Countly.CountlyFeatureNames.remoteConfig)) {
                    return;
                }

                if (completionCallback == null) {
                    completionCallback = (result, error) -> {
                    };
                }

                testingFetchVariantInformationInternal(completionCallback);
            }
        }

        /**
         * Enrolls user for a specific variant of A/B testing experiment
         *
         * @param keyName - key value retrieved from the fetched variants
         * @param variantName - name of the variant for the key to enroll
         * @param completionCallback
         */
        public void testingEnrollIntoVariant(String keyName, String variantName, RCVariantCallback completionCallback) {
            synchronized (_cly) {
                L.i("[RemoteConfig] Calling 'testingEnrollIntoVariant'");

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
