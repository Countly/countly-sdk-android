package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

    // SERVER CONFIGURATION PARAMS
    Integer serverConfigUpdateInterval; // in hours
    int currentServerConfigUpdateInterval = 4;
    long lastServerConfigFetchTimestamp = -1;
    private boolean serverConfigDisabled = false;

    ModuleConfiguration(@NonNull Countly cly, @NonNull CountlyConfig config) {
        super(cly, config);
        L.v("[ModuleConfiguration] Initialising");
        config.configProvider = this;
        configProvider = this;

        immediateRequestGenerator = config.immediateRequestGenerator;
        serverConfigUpdateTimer = new CountlyTimer();
        serverConfigUpdateInterval = currentServerConfigUpdateInterval;
        serverConfigDisabled = config.sdkBehaviorSettingsDisabled;

        config.countlyStore.setConfigurationProvider(this);

        if (!serverConfigDisabled) {
            //load the previously saved configuration
            loadConfigFromStorage(config.sdkBehaviorSettings);
            //update the config variables according to the new state
            updateConfigVariables(config);
        }
    }

    @Override
    void initFinished(@NonNull final CountlyConfig config) {
        //once the SDK has loaded, init fetching the server config
        L.d("[ModuleConfiguration] initFinished");
        if (!serverConfigDisabled) {
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
        serverConfigUpdateTimer.startTimer((long) currentServerConfigUpdateInterval * 60 * 60 * 1000, (long) currentServerConfigUpdateInterval * 60 * 60, new Runnable() {
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

        if (Utils.isNullOrEmpty(sConfig)) {
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
        } catch (JSONException e) {
            L.w("[ModuleConfiguration] loadStoredConfig, failed to parse, " + e);

            latestRetrievedConfigurationFull = null;
            latestRetrievedConfiguration = null;
        }
    }

    private <T> T extractValue(String key, StringBuilder sb, T currentValue, T defaultValue, Class<T> clazz) {
        if (latestRetrievedConfiguration.has(key)) {
            try {
                Object value = latestRetrievedConfiguration.get(key);
                if (!value.equals(currentValue)) {
                    sb.append(key).append(":[").append(value).append("], ");
                    return clazz.cast(value);
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

    //update the config variables according to the current config obj state
    private void updateConfigVariables(@NonNull final CountlyConfig clyConfig) {
        L.v("[ModuleConfiguration] updateConfigVariables");
        if (latestRetrievedConfiguration == null) {
            //no config, don't continue
            return;
        }

        StringBuilder sb = new StringBuilder();

        currentVNetworking = extractValue(keyRNetworking, sb, currentVNetworking, currentVNetworking, Boolean.class);
        currentVTracking = extractValue(keyRTracking, sb, currentVTracking, currentVTracking, Boolean.class);
        currentVSessionTracking = extractValue(keyRSessionTracking, sb, currentVSessionTracking, currentVSessionTracking, Boolean.class);
        currentVCrashReporting = extractValue(keyRCrashReporting, sb, currentVCrashReporting, currentVCrashReporting, Boolean.class);
        currentVViewTracking = extractValue(keyRViewTracking, sb, currentVViewTracking, currentVViewTracking, Boolean.class);
        currentVCustomEventTracking = extractValue(keyRCustomEventTracking, sb, currentVCustomEventTracking, currentVCustomEventTracking, Boolean.class);
        currentVLocationTracking = extractValue(keyRLocationTracking, sb, currentVLocationTracking, currentVLocationTracking, Boolean.class);
        currentVContentZone = extractValue(keyREnterContentZone, sb, currentVContentZone, currentVContentZone, Boolean.class);
        serverConfigUpdateInterval = extractValue(keyRServerConfigUpdateInterval, sb, serverConfigUpdateInterval, currentServerConfigUpdateInterval, Integer.class);
        currentVRefreshContentZone = extractValue(keyRRefreshContentZone, sb, currentVRefreshContentZone, currentVRefreshContentZone, Boolean.class);
        currentVBackoffMechanism = extractValue(keyRBackoffMechanism, sb, clyConfig.backOffMechanismEnabled, currentVBackoffMechanism, Boolean.class);
        currentVBOMAcceptedTimeoutSeconds = extractValue(keyRBOMAcceptedTimeout, sb, currentVBOMAcceptedTimeoutSeconds, currentVBOMAcceptedTimeoutSeconds, Integer.class);
        currentVBOMRQPercentage = extractValue(keyRBOMRQPercentage, sb, currentVBOMRQPercentage, currentVBOMRQPercentage, Double.class);
        currentVBOMRequestAge = extractValue(keyRBOMRequestAge, sb, currentVBOMRequestAge, currentVBOMRequestAge, Integer.class);
        currentVBOMDuration = extractValue(keyRBOMDuration, sb, currentVBOMDuration, currentVBOMDuration, Integer.class);

        clyConfig.setMaxRequestQueueSize(extractValue(keyRReqQueueSize, sb, clyConfig.maxRequestQueueSize, clyConfig.maxRequestQueueSize, Integer.class));
        clyConfig.setEventQueueSizeToSend(extractValue(keyREventQueueSize, sb, clyConfig.eventQueueSizeThreshold, Countly.sharedInstance().EVENT_QUEUE_SIZE_THRESHOLD, Integer.class));
        clyConfig.setLoggingEnabled(extractValue(keyRLogging, sb, clyConfig.loggingEnabled, clyConfig.loggingEnabled, Boolean.class));
        clyConfig.setUpdateSessionTimerDelay(extractValue(keyRSessionUpdateInterval, sb, clyConfig.sessionUpdateTimerDelay, Long.valueOf(Countly.TIMER_DELAY_IN_SECONDS).intValue(), Integer.class));
        clyConfig.sdkInternalLimits.setMaxKeyLength(extractValue(keyRLimitKeyLength, sb, clyConfig.sdkInternalLimits.maxKeyLength, Countly.maxKeyLengthDefault, Integer.class));
        clyConfig.sdkInternalLimits.setMaxValueSize(extractValue(keyRLimitValueSize, sb, clyConfig.sdkInternalLimits.maxValueSize, Countly.maxValueSizeDefault, Integer.class));
        clyConfig.sdkInternalLimits.setMaxSegmentationValues(extractValue(keyRLimitSegValues, sb, clyConfig.sdkInternalLimits.maxSegmentationValues, Countly.maxSegmentationValuesDefault, Integer.class));
        clyConfig.sdkInternalLimits.setMaxBreadcrumbCount(extractValue(keyRLimitBreadcrumb, sb, clyConfig.sdkInternalLimits.maxBreadcrumbCount, Countly.maxBreadcrumbCountDefault, Integer.class));
        clyConfig.sdkInternalLimits.setMaxStackTraceLinesPerThread(extractValue(keyRLimitTraceLine, sb, clyConfig.sdkInternalLimits.maxStackTraceLinesPerThread, Countly.maxStackTraceLinesPerThreadDefault, Integer.class));
        clyConfig.sdkInternalLimits.setMaxStackTraceLineLength(extractValue(keyRLimitTraceLength, sb, clyConfig.sdkInternalLimits.maxStackTraceLineLength, Countly.maxStackTraceLineLengthDefault, Integer.class));
        clyConfig.content.setZoneTimerInterval(extractValue(keyRContentZoneInterval, sb, clyConfig.content.zoneTimerInterval, clyConfig.content.zoneTimerInterval, Integer.class));
        clyConfig.setRequiresConsent(extractValue(keyRConsentRequired, sb, clyConfig.shouldRequireConsent, clyConfig.shouldRequireConsent, Boolean.class));
        clyConfig.setRequestDropAgeHours(extractValue(keyRDropOldRequestTime, sb, clyConfig.dropAgeHours, clyConfig.dropAgeHours, Integer.class));

        String updatedValues = sb.toString();
        if (!updatedValues.isEmpty()) {
            L.i("[ModuleConfiguration] updateConfigVariables, SDK configuration has changed, notifying the SDK, new values: [" + updatedValues + "]");
            _cly.onSdkConfigurationChanged(clyConfig);
        }
    }

    void saveAndStoreDownloadedConfig(@NonNull JSONObject config, @NonNull CountlyConfig clyConfig) {
        L.v("[ModuleConfiguration] saveAndStoreDownloadedConfig");
        if (!config.has(keyRVersion)) {
            L.w("[ModuleConfiguration] saveAndStoreDownloadedConfig, Retrieved configuration does not has a 'version' field. Config will be ignored.");
            return;
        }

        if (!config.has(keyRTimestamp)) {
            L.w("[ModuleConfiguration] saveAndStoreDownloadedConfig, Retrieved configuration does not has a 'timestamp' field. Config will be ignored.");
            return;
        }

        if (!config.has(keyRConfig)) {
            L.w("[ModuleConfiguration] saveAndStoreDownloadedConfig, Retrieved configuration does not has a 'configuration' field. Config will be ignored.");
            return;
        }

        //at this point it is a valid response
        latestRetrievedConfigurationFull = config;
        String configAsString;

        try {
            latestRetrievedConfiguration = config.getJSONObject(keyRConfig);
            configAsString = config.toString();
        } catch (JSONException e) {
            latestRetrievedConfigurationFull = null;
            latestRetrievedConfiguration = null;

            L.w("[ModuleConfiguration] saveAndStoreDownloadedConfig, Failed retrieving internal config, " + e);
            return;
        }

        //save to storage
        storageProvider.setServerConfig(configAsString);

        //update config variables
        updateConfigVariables(clyConfig);
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
        if (serverConfigDisabled) {
            return;
        }
        L.v("[ModuleConfiguration] fetchConfigFromServer");

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

            saveAndStoreDownloadedConfig(checkResponse, config);
        }, L);
    }

    void fetchIfTimeIsUpForFetchingServerConfig() {
        if (serverConfigDisabled) {
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
}
