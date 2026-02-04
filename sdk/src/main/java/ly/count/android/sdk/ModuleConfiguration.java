package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

class ModuleConfiguration extends ModuleBase implements ConfigurationProvider {
    ImmediateRequestGenerator immediateRequestGenerator;
    CountlyTimer serverConfigUpdateTimer;

    JSONObject latestRetrievedConfigurationFull = null;
    JSONObject latestRetrievedConfiguration = null;

    //config keys
    final static String keyRTracking = "tracking";
    final static String keyRNetworking = "networking";

    //request keys
    final static String keyRTimestamp = "t";
    final static String keyRVersion = "v";
    final static String keyRConfig = "c";
    final static String keyRReqQueueSize = "rqs";
    final static String keyREventQueueSize = "eqs";
    final static String keyRLogging = "log";
    final static String keyRSessionUpdateInterval = "sui";
    final static String keyRSessionTracking = "st";
    final static String keyRViewTracking = "vt";
    final static String keyRLocationTracking = "lt";
    final static String keyRRefreshContentZone = "rcz";
    final static String keyRLimitKeyLength = "lkl";
    final static String keyRLimitValueSize = "lvs";
    final static String keyRLimitSegValues = "lsv";
    final static String keyRLimitBreadcrumb = "lbc";
    final static String keyRLimitTraceLine = "ltlpt";
    final static String keyRLimitTraceLength = "ltl";
    final static String keyRCustomEventTracking = "cet";
    final static String keyREnterContentZone = "ecz";
    final static String keyRContentZoneInterval = "czi";
    final static String keyRConsentRequired = "cr";
    final static String keyRDropOldRequestTime = "dort";
    final static String keyRCrashReporting = "crt";
    final static String keyRServerConfigUpdateInterval = "scui";
    final static String keyRBackoffMechanism = "bom";
    final static String keyRBOMAcceptedTimeout = "bom_at";
    final static String keyRBOMRQPercentage = "bom_rqp";
    final static String keyRBOMRequestAge = "bom_ra";
    final static String keyRBOMDuration = "bom_d";
    final static String keyRUserPropertyCacheLimit = "upcl";
    final static String keyREventBlacklist = "eb";
    final static String keyRUserPropertyBlacklist = "upb";
    final static String keyRSegmentationBlacklist = "sb";
    final static String keyREventSegmentationBlacklist = "esb"; // json
    final static String keyREventWhitelist = "ew";
    final static String keyRUserPropertyWhitelist = "upw";
    final static String keyRSegmentationWhitelist = "sw";
    final static String keyREventSegmentationWhitelist = "esw"; // json
    final static String keyRJourneyTriggerEvents = "jte";
    final static String keyRListingFilterPreset = "filter_preset";

    // FLAGS
    boolean currentVTracking = true;
    boolean currentVNetworking = true;
    boolean currentVSessionTracking = true;
    boolean currentVViewTracking = true;
    boolean currentVCustomEventTracking = true;
    boolean currentVContentZone = false;
    boolean currentVCrashReporting = true;
    boolean currentVLocationTracking = true;
    boolean currentVRefreshContentZone = true;
    boolean currentVBackoffMechanism = true;

    // PROPERTIES
    int currentVBOMAcceptedTimeoutSeconds = 10;
    double currentVBOMRQPercentage = 0.5;
    int currentVBOMRequestAge = 24; // in hours
    int currentVBOMDuration = 60; // in seconds
    int currentVUserPropertyCacheLimit = 100;

    // FILTERS
    FilterList<Set<String>> currentVEventFilterList = new FilterList<>(new HashSet<>(), false);
    FilterList<Set<String>> currentVUserPropertyFilterList = new FilterList<>(new HashSet<>(), false);
    FilterList<Set<String>> currentVSegmentationFilterList = new FilterList<>(new HashSet<>(), false);
    FilterList<Map<String, Set<String>>> currentVEventSegmentationFilterList = new FilterList<>(new ConcurrentHashMap<>(), false);
    Set<String> currentVJourneyTriggerEvents = new HashSet<>();

    // SERVER CONFIGURATION PARAMS
    Integer serverConfigUpdateInterval; // in hours
    int currentServerConfigUpdateInterval = 4;
    long lastServerConfigFetchTimestamp = -1;
    private final boolean serverConfigRequestsDisabled;

    ModuleConfiguration(@NonNull Countly cly, @NonNull CountlyConfig config) {
        super(cly, config);
        L.v("[ModuleConfiguration] Initialising");
        config.configProvider = this;
        configProvider = this;

        immediateRequestGenerator = config.immediateRequestGenerator;
        serverConfigUpdateTimer = new CountlyTimer();
        serverConfigUpdateInterval = currentServerConfigUpdateInterval;
        serverConfigRequestsDisabled = config.sdkBehaviorSettingsRequestsDisabled;

        config.countlyStore.setConfigurationProvider(this);

        //load the previously saved configuration
        loadConfigFromStorage(config.sdkBehaviorSettings);

        //update the config variables according to the new state
        updateConfigVariables(config);
    }

    @Override
    void initFinished(@NonNull final CountlyConfig config) {
        //once the SDK has loaded, init fetching the server config
        L.d("[ModuleConfiguration] initFinished");
        if (!serverConfigRequestsDisabled) {
            fetchConfigFromServer(config);
            startServerConfigUpdateTimer();
        }
    }

    @Override
    void halt() {
        serverConfigUpdateTimer.stopTimer(L);
    }

    @Override
    void onSdkConfigurationChanged(@NonNull CountlyConfig config) {
        if (currentServerConfigUpdateInterval != serverConfigUpdateInterval) {
            currentServerConfigUpdateInterval = serverConfigUpdateInterval;
            startServerConfigUpdateTimer();
        }
    }

    private void startServerConfigUpdateTimer() {
        serverConfigUpdateTimer.startTimer((long) currentServerConfigUpdateInterval * 60 * 60 * 1000, (long) currentServerConfigUpdateInterval * 60 * 60 * 1000, new Runnable() {
            @Override
            public void run() {
                fetchConfigFromServer(_cly.config_);
            }
        }, L);
    }

    /**
     * Reads from storage to local json objects
     */
    void loadConfigFromStorage(@Nullable String sdkBehaviorSettings) {
        String sConfig = storageProvider.getServerConfig();

        if (Utils.isNullOrEmpty(sConfig) && !Utils.isNullOrEmpty(sdkBehaviorSettings)) {
            sConfig = sdkBehaviorSettings;
        }

        L.v("[ModuleConfiguration] loadConfigFromStorage, [" + sConfig + "]");

        if (sConfig == null || sConfig.isEmpty()) {
            L.d("[ModuleConfiguration] loadStoredConfig, no configs persistently stored");
            return;
        }

        try {
            latestRetrievedConfigurationFull = new JSONObject(sConfig);
            latestRetrievedConfiguration = latestRetrievedConfigurationFull.getJSONObject(keyRConfig);
            L.d("[ModuleConfiguration] loadStoredConfig, stored config loaded [" + sConfig + "]");
            saveAndStoreDownloadedConfig(latestRetrievedConfigurationFull);
        } catch (JSONException e) {
            L.w("[ModuleConfiguration] loadStoredConfig, failed to parse, " + e);

            latestRetrievedConfigurationFull = null;
            latestRetrievedConfiguration = null;
        }
    }

    private <T> T extractValue(String key, StringBuilder sb, T currentValue, T defaultValue, Class<T> clazz, @Nullable ConfigurationValueValidator<T> validator) {
        if (latestRetrievedConfiguration.has(key)) {
            try {
                Object value = latestRetrievedConfiguration.get(key);
                if (!value.equals(currentValue)) {
                    T extractedValue = clazz.cast(value);
                    if (validator != null && !validator.validate(extractedValue)) {
                        L.w("[ModuleConfiguration] updateConfigs, value for '" + key + "' is not valid according to the validator, value: [" + extractedValue + "]");
                    } else {
                        sb.append(key).append(":[").append(value).append("], ");
                        return extractedValue;
                    }
                }
            } catch (Exception e) {
                L.w("[ModuleConfiguration] updateConfigs, failed to load '" + key + "', " + e.getMessage());
            }
        }

        if (currentValue == null) {
            return defaultValue;
        }

        return currentValue;
    }

    private Boolean extractValue(String key, StringBuilder sb, Boolean currentValue, Boolean defaultValue) {
        return extractValue(key, sb, currentValue, defaultValue, Boolean.class, null);
    }

    //update the config variables according to the current config obj state
    private void updateConfigVariables(@NonNull final CountlyConfig clyConfig) {
        L.v("[ModuleConfiguration] updateConfigVariables");
        if (latestRetrievedConfiguration == null) {
            //no config, don't continue
            return;
        }

        StringBuilder sb = new StringBuilder();

        currentVNetworking = extractValue(keyRNetworking, sb, currentVNetworking, currentVNetworking);
        currentVTracking = extractValue(keyRTracking, sb, currentVTracking, currentVTracking);
        currentVSessionTracking = extractValue(keyRSessionTracking, sb, currentVSessionTracking, currentVSessionTracking);
        currentVCrashReporting = extractValue(keyRCrashReporting, sb, currentVCrashReporting, currentVCrashReporting);
        currentVViewTracking = extractValue(keyRViewTracking, sb, currentVViewTracking, currentVViewTracking);
        currentVCustomEventTracking = extractValue(keyRCustomEventTracking, sb, currentVCustomEventTracking, currentVCustomEventTracking);
        currentVLocationTracking = extractValue(keyRLocationTracking, sb, currentVLocationTracking, currentVLocationTracking);
        currentVContentZone = extractValue(keyREnterContentZone, sb, currentVContentZone, currentVContentZone);
        serverConfigUpdateInterval = extractValue(keyRServerConfigUpdateInterval, sb, serverConfigUpdateInterval, currentServerConfigUpdateInterval, Integer.class, (Integer value) -> value > 0);
        currentVRefreshContentZone = extractValue(keyRRefreshContentZone, sb, currentVRefreshContentZone, currentVRefreshContentZone);
        currentVBackoffMechanism = extractValue(keyRBackoffMechanism, sb, clyConfig.backOffMechanismEnabled, currentVBackoffMechanism);
        currentVBOMAcceptedTimeoutSeconds = extractValue(keyRBOMAcceptedTimeout, sb, currentVBOMAcceptedTimeoutSeconds, currentVBOMAcceptedTimeoutSeconds, Integer.class, (Integer value) -> value > 0);
        currentVBOMRQPercentage = extractValue(keyRBOMRQPercentage, sb, currentVBOMRQPercentage, currentVBOMRQPercentage, Double.class, (Double value) -> value > 0.0 && value < 1.0);
        currentVBOMRequestAge = extractValue(keyRBOMRequestAge, sb, currentVBOMRequestAge, currentVBOMRequestAge, Integer.class, (Integer value) -> value > 0);
        currentVBOMDuration = extractValue(keyRBOMDuration, sb, currentVBOMDuration, currentVBOMDuration, Integer.class, (Integer value) -> value > 0);
        currentVUserPropertyCacheLimit = extractValue(keyRUserPropertyCacheLimit, sb, currentVUserPropertyCacheLimit, currentVUserPropertyCacheLimit, Integer.class, (Integer value) -> value > 0);

        clyConfig.setMaxRequestQueueSize(extractValue(keyRReqQueueSize, sb, clyConfig.maxRequestQueueSize, clyConfig.maxRequestQueueSize, Integer.class, (Integer value) -> value > 0));
        clyConfig.setEventQueueSizeToSend(extractValue(keyREventQueueSize, sb, clyConfig.eventQueueSizeThreshold, Countly.sharedInstance().EVENT_QUEUE_SIZE_THRESHOLD, Integer.class, (Integer value) -> value > 0));
        clyConfig.setLoggingEnabled(extractValue(keyRLogging, sb, clyConfig.loggingEnabled, clyConfig.loggingEnabled));
        clyConfig.setUpdateSessionTimerDelay(extractValue(keyRSessionUpdateInterval, sb, clyConfig.sessionUpdateTimerDelay, Long.valueOf(Countly.TIMER_DELAY_IN_SECONDS).intValue(), Integer.class, (Integer value) -> value > 0));
        clyConfig.sdkInternalLimits.setMaxKeyLength(extractValue(keyRLimitKeyLength, sb, clyConfig.sdkInternalLimits.maxKeyLength, Countly.maxKeyLengthDefault, Integer.class, (Integer value) -> value > 0));
        clyConfig.sdkInternalLimits.setMaxValueSize(extractValue(keyRLimitValueSize, sb, clyConfig.sdkInternalLimits.maxValueSize, Countly.maxValueSizeDefault, Integer.class, (Integer value) -> value > 0));
        clyConfig.sdkInternalLimits.setMaxSegmentationValues(extractValue(keyRLimitSegValues, sb, clyConfig.sdkInternalLimits.maxSegmentationValues, Countly.maxSegmentationValuesDefault, Integer.class, (Integer value) -> value > 0));
        clyConfig.sdkInternalLimits.setMaxBreadcrumbCount(extractValue(keyRLimitBreadcrumb, sb, clyConfig.sdkInternalLimits.maxBreadcrumbCount, Countly.maxBreadcrumbCountDefault, Integer.class, (Integer value) -> value > 0));
        clyConfig.sdkInternalLimits.setMaxStackTraceLinesPerThread(extractValue(keyRLimitTraceLine, sb, clyConfig.sdkInternalLimits.maxStackTraceLinesPerThread, Countly.maxStackTraceLinesPerThreadDefault, Integer.class, (Integer value) -> value > 0));
        clyConfig.sdkInternalLimits.setMaxStackTraceLineLength(extractValue(keyRLimitTraceLength, sb, clyConfig.sdkInternalLimits.maxStackTraceLineLength, Countly.maxStackTraceLineLengthDefault, Integer.class, (Integer value) -> value > 0));
        clyConfig.content.setZoneTimerInterval(extractValue(keyRContentZoneInterval, sb, clyConfig.content.zoneTimerInterval, clyConfig.content.zoneTimerInterval, Integer.class, (Integer value) -> value >= 16));
        clyConfig.setRequiresConsent(extractValue(keyRConsentRequired, sb, clyConfig.shouldRequireConsent, clyConfig.shouldRequireConsent));
        clyConfig.setRequestDropAgeHours(extractValue(keyRDropOldRequestTime, sb, clyConfig.dropAgeHours, clyConfig.dropAgeHours, Integer.class, (Integer value) -> value >= 0));

        updateListingFilters();

        String updatedValues = sb.toString();
        if (!updatedValues.isEmpty()) {
            L.i("[ModuleConfiguration] updateConfigVariables, SDK configuration has changed, notifying the SDK, new values: [" + updatedValues + "]");
            _cly.onSdkConfigurationChanged(clyConfig);
        }
    }

    private void updateListingFilters() {
        L.d("[ModuleConfiguration] updateListingFilters, current listing filters before updating: \n" +
            "Event Filter List: " + currentVEventFilterList.filterList + ", isWhitelist: " + currentVEventFilterList.isWhitelist + "\n" +
            "User Property Filter List: " + currentVUserPropertyFilterList.filterList + ", isWhitelist: " + currentVUserPropertyFilterList.isWhitelist + "\n" +
            "Segmentation Filter List: " + currentVSegmentationFilterList.filterList + ", isWhitelist: " + currentVSegmentationFilterList.isWhitelist + "\n" +
            "Event Segmentation Filter List: " + currentVEventSegmentationFilterList.filterList + ", isWhitelist: " + currentVEventSegmentationFilterList.isWhitelist + "\n" +
            "Journey Trigger Events: " + currentVJourneyTriggerEvents);
        JSONArray eventBlacklistJSARR = latestRetrievedConfiguration.optJSONArray(keyREventBlacklist);
        JSONArray eventWhitelistJSARR = latestRetrievedConfiguration.optJSONArray(keyREventWhitelist);
        JSONArray userPropertyBlacklistJSARR = latestRetrievedConfiguration.optJSONArray(keyRUserPropertyBlacklist);
        JSONArray userPropertyWhitelistJSARR = latestRetrievedConfiguration.optJSONArray(keyRUserPropertyWhitelist);
        JSONArray segmentationBlacklistJSARR = latestRetrievedConfiguration.optJSONArray(keyRSegmentationBlacklist);
        JSONArray segmentationWhitelistJSARR = latestRetrievedConfiguration.optJSONArray(keyRSegmentationWhitelist);
        JSONObject eventSegmentationBlacklistJSOBJ = latestRetrievedConfiguration.optJSONObject(keyREventSegmentationBlacklist);
        JSONObject eventSegmentationWhitelistJSOBJ = latestRetrievedConfiguration.optJSONObject(keyREventSegmentationWhitelist);
        JSONArray journeyTriggerEventsJSARR = latestRetrievedConfiguration.optJSONArray(keyRJourneyTriggerEvents);

        if (eventBlacklistJSARR != null) {
            extractFilterSetFromJSONArray(eventBlacklistJSARR, currentVEventFilterList.filterList);
            currentVEventFilterList.isWhitelist = false;
        } else if (eventWhitelistJSARR != null) {
            extractFilterSetFromJSONArray(eventWhitelistJSARR, currentVEventFilterList.filterList);
            currentVEventFilterList.isWhitelist = true;
        }

        if (userPropertyBlacklistJSARR != null) {
            extractFilterSetFromJSONArray(userPropertyBlacklistJSARR, currentVUserPropertyFilterList.filterList);
            currentVUserPropertyFilterList.isWhitelist = false;
        } else if (userPropertyWhitelistJSARR != null) {
            extractFilterSetFromJSONArray(userPropertyWhitelistJSARR, currentVUserPropertyFilterList.filterList);
            currentVUserPropertyFilterList.isWhitelist = true;
        }

        if (segmentationBlacklistJSARR != null) {
            extractFilterSetFromJSONArray(segmentationBlacklistJSARR, currentVSegmentationFilterList.filterList);
            currentVSegmentationFilterList.isWhitelist = false;
        } else if (segmentationWhitelistJSARR != null) {
            extractFilterSetFromJSONArray(segmentationWhitelistJSARR, currentVSegmentationFilterList.filterList);
            currentVSegmentationFilterList.isWhitelist = true;
        }

        if (eventSegmentationBlacklistJSOBJ != null) {
            currentVEventSegmentationFilterList.filterList.clear();
            currentVEventSegmentationFilterList.isWhitelist = false;
            Iterator<String> keys = eventSegmentationBlacklistJSOBJ.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONArray jsonArray = eventSegmentationBlacklistJSOBJ.optJSONArray(key);
                if (jsonArray != null) {
                    Set<String> filterSet = new HashSet<>();
                    extractFilterSetFromJSONArray(jsonArray, filterSet);
                    currentVEventSegmentationFilterList.filterList.put(key, filterSet);
                }
            }
        } else if (eventSegmentationWhitelistJSOBJ != null) {
            currentVEventSegmentationFilterList.filterList.clear();
            currentVEventSegmentationFilterList.isWhitelist = true;
            Iterator<String> keys = eventSegmentationWhitelistJSOBJ.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONArray jsonArray = eventSegmentationWhitelistJSOBJ.optJSONArray(key);
                if (jsonArray != null) {
                    Set<String> filterSet = new HashSet<>();
                    extractFilterSetFromJSONArray(jsonArray, filterSet);
                    currentVEventSegmentationFilterList.filterList.put(key, filterSet);
                }
            }
        }

        if (journeyTriggerEventsJSARR != null) {
            extractFilterSetFromJSONArray(journeyTriggerEventsJSARR, currentVJourneyTriggerEvents);
        }

        L.d("[ModuleConfiguration] updateListingFilters, current listing filters after updating: \n" +
            "Event Filter List: " + currentVEventFilterList.filterList + ", isWhitelist: " + currentVEventFilterList.isWhitelist + "\n" +
            "User Property Filter List: " + currentVUserPropertyFilterList.filterList + ", isWhitelist: " + currentVUserPropertyFilterList.isWhitelist + "\n" +
            "Segmentation Filter List: " + currentVSegmentationFilterList.filterList + ", isWhitelist: " + currentVSegmentationFilterList.isWhitelist + "\n" +
            "Event Segmentation Filter List: " + currentVEventSegmentationFilterList.filterList + ", isWhitelist: " + currentVEventSegmentationFilterList.isWhitelist + "\n" +
            "Journey Trigger Events: " + currentVJourneyTriggerEvents);
    }

    private void extractFilterSetFromJSONArray(@Nullable JSONArray jsonArray, @NonNull Set<String> targetSet) {
        if (jsonArray == null) {
            return;
        }
        targetSet.clear();
        for (int i = 0; i < jsonArray.length(); i++) {
            String item = jsonArray.optString(i, null);
            if (item != null) {
                targetSet.add(item);
            }
        }
    }

    boolean validateServerConfig(@NonNull JSONObject config) {
        JSONObject newInner = config.optJSONObject(keyRConfig);

        L.v("[ModuleConfiguration] validateServerConfig");
        if (!config.has(keyRVersion)) {
            L.w("[ModuleConfiguration] validateServerConfig, Retrieved configuration does not has a 'version' field. Config will be ignored.");
            return false;
        } else if (!config.has(keyRTimestamp)) {
            L.w("[ModuleConfiguration] validateServerConfig, Retrieved configuration does not has a 'timestamp' field. Config will be ignored.");
            return false;
        } else if (!config.has(keyRConfig)) {
            L.w("[ModuleConfiguration] validateServerConfig, Retrieved configuration does not has a 'configuration' field. Config will be ignored.");
            return false;
        } else if (config.length() != 3) {
            L.w("[ModuleConfiguration] validateServerConfig, Retrieved configuration does not have the expected number of keys. Config will be ignored.");
            return false;
        } else if (newInner == null || newInner.length() == 0) {
            L.d("[ModuleConfiguration] validateServerConfig, Config rejected: inner 'c' object is invalid or empty.");
            return false;
        }

        removeUnsupportedKeys(newInner);
        return true;
    }

    private void removeUnsupportedKeys(@NonNull JSONObject newInner) {
        Iterator<String> keys = newInner.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = newInner.opt(key);

            boolean isValid = false;

            // --- Boolean keys ---
            switch (key) {
                case keyRNetworking:
                case keyRTracking:
                case keyRSessionTracking:
                case keyRCrashReporting:
                case keyRViewTracking:
                case keyRCustomEventTracking:
                case keyRLocationTracking:
                case keyREnterContentZone:
                case keyRRefreshContentZone:
                case keyRBackoffMechanism:
                case keyRLogging:
                case keyRConsentRequired:
                    isValid = value instanceof Boolean;
                    break;

                case keyRListingFilterPreset:
                    isValid = value instanceof String && (value.equals("Whitelisting") || value.equals("Blacklisting"));
                    break;

                // --- Positive Integer keys (> 0) ---
                case keyRServerConfigUpdateInterval:
                case keyRBOMAcceptedTimeout:
                case keyRBOMRequestAge:
                case keyRBOMDuration:
                case keyRReqQueueSize:
                case keyREventQueueSize:
                case keyRSessionUpdateInterval:
                case keyRLimitKeyLength:
                case keyRLimitValueSize:
                case keyRLimitSegValues:
                case keyRLimitBreadcrumb:
                case keyRLimitTraceLine:
                case keyRLimitTraceLength:
                case keyRUserPropertyCacheLimit:
                    isValid = value instanceof Integer && ((Integer) value) > 0;
                    break;

                // --- Integer >= 0 ---
                case keyRDropOldRequestTime:
                    isValid = value instanceof Integer && ((Integer) value) >= 0;
                    break;

                // --- Integer >= 16 ---
                case keyRContentZoneInterval:
                    isValid = value instanceof Integer && ((Integer) value) >= 16;
                    break;

                // --- Double between 0.0 and 1.0 ---
                case keyRBOMRQPercentage:
                    isValid = value instanceof Double && ((Double) value > 0.0 && (Double) value < 1.0);
                    break;

                // --- Filtering keys ---
                case keyREventBlacklist:
                case keyRSegmentationBlacklist:
                case keyRUserPropertyBlacklist:
                case keyREventWhitelist:
                case keyRSegmentationWhitelist:
                case keyRUserPropertyWhitelist:
                case keyRJourneyTriggerEvents:
                    isValid = value instanceof JSONArray;
                    break;
                case keyREventSegmentationBlacklist:
                case keyREventSegmentationWhitelist:
                    isValid = value instanceof JSONObject;
                    break;
                // --- Unknown keys ---
                default:
                    L.w("[ModuleConfiguration] removeUnsupportedKeys, Unknown key: [" + key + "], removing it. value: [" + value + "]");
                    break;
            }

            // --- If not valid or not known, remove it ---
            if (!isValid) {
                L.w("[ModuleConfiguration] removeUnsupportedKeys, Invalid or unknown key: [" + key + "], removing it. value: [" + value + "]");
                keys.remove();
            }
        }
    }

    void saveAndStoreDownloadedConfig(@NonNull JSONObject config) {
        L.v("[ModuleConfiguration] saveAndStoreDownloadedConfig");
        boolean validConfig = validateServerConfig(config);
        if (!validConfig) {
            L.w("[ModuleConfiguration] saveAndStoreDownloadedConfig, Retrieved configuration is not valid, ignoring it.");
            latestRetrievedConfigurationFull = null;
            latestRetrievedConfiguration = null;
            return;
        }

        JSONObject newInner = config.optJSONObject(keyRConfig);
        assert newInner != null;
        if (latestRetrievedConfigurationFull == null) {
            latestRetrievedConfigurationFull = new JSONObject();
            latestRetrievedConfiguration = new JSONObject();
            try {
                latestRetrievedConfigurationFull.put(keyRConfig, latestRetrievedConfiguration);
            } catch (JSONException ignored) {
            }
        }

        // Merge timestamp and version
        try {
            latestRetrievedConfigurationFull.put(keyRTimestamp, config.get(keyRTimestamp));
            latestRetrievedConfigurationFull.put(keyRVersion, config.get(keyRVersion));
        } catch (JSONException e) {
            L.w("[ModuleConfiguration] saveAndStoreDownloadedConfig, Failed to merge version/timestamp.", e);
        }

        removeListingFilterKeysFromConfig(newInner);

        Iterator<String> keys = newInner.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = newInner.opt(key);
            if (value != null && !JSONObject.NULL.equals(value)) {
                try {
                    latestRetrievedConfiguration.put(key, value);
                } catch (JSONException e) {
                    L.w("[ModuleConfiguration] saveAndStoreDownloadedConfig, Failed to merge inner config key: " + key, e);
                }
            }
        }

        // Save updated config
        storageProvider.setServerConfig(latestRetrievedConfigurationFull.toString());
    }

    private void removeListingFilterKeysFromConfig(JSONObject newConfig) {
        String filterPreset = newConfig.optString(keyRListingFilterPreset, "Blacklisting");

        if (filterPreset.equals("Whitelisting")) {
            latestRetrievedConfiguration.remove(keyREventBlacklist);
            latestRetrievedConfiguration.remove(keyRUserPropertyBlacklist);
            latestRetrievedConfiguration.remove(keyRSegmentationBlacklist);
            latestRetrievedConfiguration.remove(keyREventSegmentationBlacklist);
        } else {
            latestRetrievedConfiguration.remove(keyREventWhitelist);
            latestRetrievedConfiguration.remove(keyRUserPropertyWhitelist);
            latestRetrievedConfiguration.remove(keyRSegmentationWhitelist);
            latestRetrievedConfiguration.remove(keyREventSegmentationWhitelist);
        }
    }

    /**
     * Perform network request for retrieving latest config
     * If valid config is downloaded, save it, and update the values
     * Example response:
     * {
     * "v":1,
     * "t":1681808287464,
     * "c":{
     * "tracking":false,
     * "networking":false,
     * "crashes":false,
     * "views":false,
     * "heartbeat":61,
     * "event_queue":11,
     * "request_queue":1001
     * }
     * }
     */
    void fetchConfigFromServer(@NonNull CountlyConfig config) {
        L.v("[ModuleConfiguration] fetchConfigFromServer");
        if (serverConfigRequestsDisabled) {
            L.v("[ModuleConfiguration] fetchConfigFromServer, fetch config from the server is aborted, server config requests are disabled");
            return;
        }

        // why _cly? because module configuration is created before module device id, so we need to access it like this
        // call order to module device id is after module configuration and device id provider is module device id
        if (_cly.config_.deviceIdProvider.isTemporaryIdEnabled()) {
            //temporary id mode enabled, abort
            L.d("[ModuleConfiguration] fetchConfigFromServer, fetch config from the server is aborted, temporary device ID mode is set");
            return;
        }

        lastServerConfigFetchTimestamp = UtilsTime.currentTimestampMs();
        String requestData = requestQueueProvider.prepareServerConfigRequest();
        ConnectionProcessor cp = requestQueueProvider.createConnectionProcessor();

        immediateRequestGenerator.CreateImmediateRequestMaker().doWork(requestData, "/o/sdk", cp, false, true, checkResponse -> {
            if (checkResponse == null) {
                L.w("[ModuleConfiguration] Not possible to retrieve configuration data. Probably due to lack of connection to the server");
                return;
            }

            L.d("[ModuleConfiguration] Retrieved configuration response: [" + checkResponse + "]");

            saveAndStoreDownloadedConfig(checkResponse);
            updateConfigVariables(config);
        }, L);
    }

    void fetchIfTimeIsUpForFetchingServerConfig() {
        if (serverConfigRequestsDisabled) {
            return;
        }

        if (lastServerConfigFetchTimestamp > 0) {
            long currentTime = UtilsTime.currentTimestampMs();
            long timePassed = currentTime - lastServerConfigFetchTimestamp;

            if (timePassed > (long) currentServerConfigUpdateInterval * 60 * 60 * 1000) {
                fetchConfigFromServer(_cly.config_);
            }
        }
    }

    // configuration getters

    @Override
    public boolean getNetworkingEnabled() {
        return currentVNetworking;
    }

    @Override
    public boolean getTrackingEnabled() {
        return currentVTracking;
    }

    @Override public boolean getSessionTrackingEnabled() {
        return currentVSessionTracking;
    }

    @Override public boolean getViewTrackingEnabled() {
        return currentVViewTracking;
    }

    @Override public boolean getCustomEventTrackingEnabled() {
        return currentVCustomEventTracking;
    }

    @Override public boolean getContentZoneEnabled() {
        return currentVContentZone;
    }

    @Override public boolean getCrashReportingEnabled() {
        return currentVCrashReporting;
    }

    @Override public boolean getLocationTrackingEnabled() {
        return currentVLocationTracking;
    }

    @Override public boolean getRefreshContentZoneEnabled() {
        return currentVRefreshContentZone;
    }

    @Override public boolean getBOMEnabled() {
        return currentVBackoffMechanism;
    }

    @Override public int getBOMAcceptedTimeoutSeconds() {
        return currentVBOMAcceptedTimeoutSeconds;
    }

    @Override public double getBOMRQPercentage() {
        return currentVBOMRQPercentage;
    }

    @Override public int getBOMRequestAge() {
        return currentVBOMRequestAge;
    }

    @Override public int getBOMDuration() {
        return currentVBOMDuration;
    }

    @Override public int getRequestTimeoutDurationMillis() {
        return _cly.config_.requestTimeoutDuration * 1000;
    }

    @Override public int getUserPropertyCacheLimit() {
        return currentVUserPropertyCacheLimit;
    }

    @Override public FilterList<Set<String>> getEventFilterList() {
        return currentVEventFilterList;
    }

    @Override public FilterList<Set<String>> getUserPropertyFilterList() {
        return currentVUserPropertyFilterList;
    }

    @Override public FilterList<Set<String>> getSegmentationFilterList() {
        return currentVSegmentationFilterList;
    }

    @Override public FilterList<Map<String, Set<String>>> getEventSegmentationFilterList() {
        return currentVEventSegmentationFilterList;
    }

    @Override public Set<String> getJourneyTriggerEvents() {
        return currentVJourneyTriggerEvents;
    }
}
