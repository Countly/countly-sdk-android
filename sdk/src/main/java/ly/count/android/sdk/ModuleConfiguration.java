package ly.count.android.sdk;

import androidx.annotation.NonNull;
import org.json.JSONException;
import org.json.JSONObject;

class ModuleConfiguration extends ModuleBase implements ConfigurationProvider {
    ImmediateRequestGenerator immediateRequestGenerator;

    boolean serverConfigEnabled = false;

    JSONObject latestRetrievedConfigurationFull = null;
    JSONObject latestRetrievedConfiguration = null;

    //config keys
    final static String keyTracking = "tracking";
    final static String keyNetworking = "networking";

    //request keys
    final static String keyRTimestamp = "t";
    final static String keyRVersion = "v";
    final static String keyRConfig = "c";
    final static String keyRReqQueueSize = "rqs";
    final static String keyREventQueueSize = "eqs";
    final static String keyRLogging = "log";
    final static String keyRSessionUpdateInterval = "sui";
    final static String keyRSessionTracking = "st";
    final static String keyRCrashReporting = "crt";
    final static String keyRViewTracking = "vt";

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

    final static boolean defaultVTracking = true;
    final static boolean defaultVNetworking = true;

    boolean currentVTracking = true;
    boolean currentVNetworking = true;
    boolean configurationFetched = false;

    ModuleConfiguration(@NonNull Countly cly, @NonNull CountlyConfig config) {
        super(cly, config);
        L.v("[ModuleConfiguration] Initialising");
        config.configProvider = this;
        configProvider = this;

        serverConfigEnabled = config.serverConfigurationEnabled;

        immediateRequestGenerator = config.immediateRequestGenerator;

        config.countlyStore.setConfigurationProvider(this);

        if (serverConfigEnabled) {
            //load the previously saved configuration
            loadConfigFromStorage();

            //update the config variables according to the new state
            updateConfigVariables(config);
        }
    }

    @Override
    void initFinished(@NonNull final CountlyConfig config) {
        if (serverConfigEnabled) {
            //once the SDK has loaded, init fetching the server config
            fetchConfigFromServer(config);
        }
    }

    @Override
    void halt() {

    }

    /**
     * Reads from storage to local json objects
     */
    void loadConfigFromStorage() {
        String sConfig = storageProvider.getServerConfig();
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

    //update the config variables according to the current config obj state
    void updateConfigVariables(@NonNull final CountlyConfig clyConfig) {
        L.v("[ModuleConfiguration] updateConfigVariables");
        //set all to defaults
        currentVNetworking = defaultVNetworking;
        currentVTracking = defaultVTracking;

        if (latestRetrievedConfiguration == null) {
            //no config, don't continue
            return;
        }

        //networking
        if (latestRetrievedConfiguration.has(keyNetworking)) {
            try {
                currentVNetworking = latestRetrievedConfiguration.getBoolean(keyNetworking);
            } catch (JSONException e) {
                L.w("[ModuleConfiguration] updateConfigs, failed to load 'networking', " + e);
            }
        }

        //tracking
        if (latestRetrievedConfiguration.has(keyTracking)) {
            try {
                currentVTracking = latestRetrievedConfiguration.getBoolean(keyTracking);
            } catch (JSONException e) {
                L.w("[ModuleConfiguration] updateConfigs, failed to load 'tracking', " + e);
            }
        }

        if (latestRetrievedConfiguration.has(keyRReqQueueSize)) {
            try {
                clyConfig.maxRequestQueueSize = latestRetrievedConfiguration.getInt(keyRReqQueueSize);
            } catch (JSONException e) {
                L.w("[ModuleConfiguration] updateConfigs, failed to load 'requestQueueSize', " + e);
            }
        }

        if (latestRetrievedConfiguration.has(keyREventQueueSize)) {
            try {
                clyConfig.eventQueueSizeThreshold = latestRetrievedConfiguration.getInt(keyREventQueueSize);
                _cly.EVENT_QUEUE_SIZE_THRESHOLD = clyConfig.eventQueueSizeThreshold;
            } catch (JSONException e) {
                L.w("[ModuleConfiguration] updateConfigs, failed to load 'eventQueueSize', " + e);
            }
        }

        if (latestRetrievedConfiguration.has(keyRLogging)) {
            try {
                clyConfig.loggingEnabled = latestRetrievedConfiguration.getInt(keyRLogging) == 1;
            } catch (JSONException e) {
                L.w("[ModuleConfiguration] updateConfigs, failed to load 'eventBatchSize', " + e);
            }
        }

        if (latestRetrievedConfiguration.has(keyRSessionUpdateInterval)) {
            try {
                clyConfig.sessionUpdateTimerDelay = latestRetrievedConfiguration.getInt(keyRSessionUpdateInterval);
            } catch (JSONException e) {
                L.w("[ModuleConfiguration] updateConfigs, failed to load 'sessionUpdateInterval', " + e);
            }
        }

        if (latestRetrievedConfiguration.has(keyRSessionTracking)) {

        }

        if (latestRetrievedConfiguration.has(keyRViewTracking)) {

        }

        if (latestRetrievedConfiguration.has(keyRLimitKeyLength)) {
            try {
                clyConfig.sdkInternalLimits.maxKeyLength = latestRetrievedConfiguration.getInt(keyRLimitKeyLength);
            } catch (JSONException e) {
                L.w("[ModuleConfiguration] updateConfigs, failed to load 'maxKeyLength', " + e);
            }
        }

        if (latestRetrievedConfiguration.has(keyRLimitValueSize)) {
            try {
                clyConfig.sdkInternalLimits.maxValueSize = latestRetrievedConfiguration.getInt(keyRLimitValueSize);
            } catch (JSONException e) {
                L.w("[ModuleConfiguration] updateConfigs, failed to load 'maxValueSize', " + e);
            }
        }

        if (latestRetrievedConfiguration.has(keyRLimitSegValues)) {
            try {
                clyConfig.sdkInternalLimits.maxSegmentationValues = latestRetrievedConfiguration.getInt(keyRLimitSegValues);
            } catch (JSONException e) {
                L.w("[ModuleConfiguration] updateConfigs, failed to load 'maxSegmentationValues', " + e);
            }
        }

        if (latestRetrievedConfiguration.has(keyRLimitBreadcrumb)) {
            try {
                clyConfig.sdkInternalLimits.maxBreadcrumbCount = latestRetrievedConfiguration.getInt(keyRLimitBreadcrumb);
            } catch (JSONException e) {
                L.w("[ModuleConfiguration] updateConfigs, failed to load 'maxBreadcrumbCount', " + e);
            }
        }

        if (latestRetrievedConfiguration.has(keyRLimitTraceLine)) {
            try {
                clyConfig.sdkInternalLimits.maxStackTraceLinesPerThread = latestRetrievedConfiguration.getInt(keyRLimitTraceLine);
            } catch (JSONException e) {
                L.w("[ModuleConfiguration] updateConfigs, failed to load 'maxStackTraceLinesPerThread', " + e);
            }
        }

        if (latestRetrievedConfiguration.has(keyRLimitTraceLength)) {
            try {
                clyConfig.sdkInternalLimits.maxStackTraceLineLength = latestRetrievedConfiguration.getInt(keyRLimitTraceLength);
            } catch (JSONException e) {
                L.w("[ModuleConfiguration] updateConfigs, failed to load 'maxStackTraceLineLength', " + e);
            }
        }

        if (latestRetrievedConfiguration.has(keyRCustomEventTracking)) {

        }

        if (latestRetrievedConfiguration.has(keyREnterContentZone)) {

        }

        if (latestRetrievedConfiguration.has(keyRContentZoneInterval)) {
            try {
                clyConfig.content.zoneTimerInterval = latestRetrievedConfiguration.getInt(keyRContentZoneInterval);
            } catch (JSONException e) {
                L.w("[ModuleConfiguration] updateConfigs, failed to load 'contentZoneInterval', " + e);
            }
        }

        if (latestRetrievedConfiguration.has(keyRConsentRequired)) {
            try {
                clyConfig.shouldRequireConsent = latestRetrievedConfiguration.getInt(keyRConsentRequired) == 1;
            } catch (JSONException e) {
                L.w("[ModuleConfiguration] updateConfigs, failed to load 'consentRequired', " + e);
            }
        }

        if (latestRetrievedConfiguration.has(keyRDropOldRequestTime)) {
            try {
                clyConfig.dropAgeHours = latestRetrievedConfiguration.getInt(keyRDropOldRequestTime);
            } catch (JSONException e) {
                L.w("[ModuleConfiguration] updateConfigs, failed to load 'dropOldRequestTime', " + e);
            }
        }
    }

    void saveAndStoreDownloadedConfig(@NonNull JSONObject config, @NonNull final CountlyConfig clyConfig) {
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
        String configAsString = null;

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
     *
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
    void fetchConfigFromServer(@NonNull final CountlyConfig config) {
        L.v("[ModuleConfiguration] fetchConfigFromServer");

        if (!serverConfigEnabled) {
            L.d("[ModuleConfiguration] fetchConfigFromServer, fetch config from the server is aborted, server config is disabled");
            return;
        }

        // why _cly? because module configuration is created before module device id, so we need to access it like this
        // call order to module device id is after module configuration and device id provider is module device id
        if (_cly.config_.deviceIdProvider.isTemporaryIdEnabled()) {
            //temporary id mode enabled, abort
            L.d("[ModuleConfiguration] fetchConfigFromServer, fetch config from the server is aborted, temporary device ID mode is set");
            return;
        }

        if (configurationFetched) {
            L.d("[ModuleConfiguration] fetchConfigFromServer, fetch config from the server is aborted, config already fetched");
            return;
        }

        configurationFetched = true;

        String requestData = requestQueueProvider.prepareServerConfigRequest();
        ConnectionProcessor cp = requestQueueProvider.createConnectionProcessor();

        immediateRequestGenerator.CreateImmediateRequestMaker().doWork(requestData, "/o/sdk", cp, false, true, checkResponse -> {
            if (checkResponse == null) {
                L.w("[ModuleConfiguration] Not possible to retrieve configuration data. Probably due to lack of connection to the server");
                return;
            }

            L.d("[ModuleConfiguration] Retrieved configuration response: [" + checkResponse.toString() + "]");

            saveAndStoreDownloadedConfig(checkResponse, config);
        }, L);
    }

    // configuration getters

    @Override
    public boolean getNetworkingEnabled() {
        if (!serverConfigEnabled) {
            return defaultVNetworking;
        }

        return currentVNetworking;
    }

    @Override
    public boolean getTrackingEnabled() {
        if (!serverConfigEnabled) {
            return defaultVTracking;
        }
        return currentVTracking;
    }

    @Override public int getRequestQueueSize() {
        return 0;
    }

    @Override public int getEventQueueSize() {
        return 0;
    }

    @Override public boolean getLoggingEnabled() {
        return false;
    }

    @Override public int getSessionUpdateInterval() {
        return 0;
    }

    @Override public boolean getSessionTrackingEnabled() {
        return false;
    }

    @Override public boolean getViewTrackingEnabled() {
        return false;
    }

    @Override public boolean getCustomEventTrackingEnabled() {
        return false;
    }

    @Override public boolean getContentZoneEnabled() {
        return false;
    }

    @Override public int getContentZoneTimerInterval() {
        return 0;
    }

    @Override public int getConsentRequired() {
        return 0;
    }

    @Override public int getDropOldRequestTime() {
        return 0;
    }

    @Override public int getMaxKeyLength() {
        return 0;
    }

    @Override public int getMaxValueSize() {
        return 0;
    }

    @Override public int getMaxSegmentationValues() {
        return 0;
    }

    @Override public int getMaxBreadcrumbCount() {
        return 0;
    }

    @Override public int getMaxStackTraceLinesPerThread() {
        return 0;
    }

    @Override public int getMaxStackTraceLineLength() {
        return 0;
    }
}
