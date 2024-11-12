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
    final static String keyCrashReporting = "crashes";

    //request keys
    final static String keyRTimestamp = "t";
    final static String keyRVersion = "v";
    final static String keyRConfig = "c";

    final static boolean defaultVTracking = true;
    final static boolean defaultVNetworking = true;
    final static boolean defaultVCrashReporting = true;

    boolean currentVTracking = true;
    boolean currentVNetworking = true;
    boolean currentVCrashReporting = true;
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
            updateConfigVariables();
        }
    }

    @Override
    void initFinished(@NonNull final CountlyConfig config) {
        if (serverConfigEnabled) {
            //once the SDK has loaded, init fetching the server config
            fetchConfigFromServer();
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
    void updateConfigVariables() {
        L.v("[ModuleConfiguration] updateConfigVariables");
        //set all to defaults
        currentVNetworking = defaultVNetworking;
        currentVTracking = defaultVTracking;
        currentVCrashReporting = defaultVCrashReporting;

        if (latestRetrievedConfiguration == null) {
            //no config, don't continue
            return;
        }

        //networking
        if (latestRetrievedConfiguration.has(keyNetworking)) {
            try {
                currentVNetworking = latestRetrievedConfiguration.getBoolean(keyNetworking);
            } catch (JSONException e) {
                L.w("[ModuleConfiguration] updateConfigVariables, failed to load 'networking', " + e);
            }
        }

        //tracking
        if (latestRetrievedConfiguration.has(keyTracking)) {
            try {
                currentVTracking = latestRetrievedConfiguration.getBoolean(keyTracking);
            } catch (JSONException e) {
                L.w("[ModuleConfiguration] updateConfigVariables, failed to load 'tracking', " + e);
            }
        }

        //tracking
        if (latestRetrievedConfiguration.has(keyCrashReporting)) {
            try {
                currentVCrashReporting = latestRetrievedConfiguration.getBoolean(keyCrashReporting);
            } catch (JSONException e) {
                L.w("[ModuleConfiguration] updateConfigVariables, failed to load 'crash_reporting', " + e);
            }
        }
    }

    void saveAndStoreDownloadedConfig(@NonNull JSONObject config) {
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
        updateConfigVariables();
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
    void fetchConfigFromServer() {
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

            saveAndStoreDownloadedConfig(checkResponse);
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

    @Override
    public boolean getCrashReportingEnabled() {
        if (!serverConfigEnabled) {
            return defaultVCrashReporting;
        }
        return currentVCrashReporting;
    }
}
