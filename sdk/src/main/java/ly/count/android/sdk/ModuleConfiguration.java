package ly.count.android.sdk;

import androidx.annotation.NonNull;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;

class ModuleConfiguration extends ModuleBase implements ConfigurationProvider {
    boolean serverConfigEnabled = false;

    JSONObject latestRetrievedConfigurationFull = null;
    JSONObject latestRetrievedConfiguration = null;

    //config keys
    final String keyTracking = "tracking";
    final String keyNetworking = "networking";

    //request keys
    final String keyRTimestamp = "t";
    final String keyRVersion = "v";
    final String keyRConfig = "c";

    final boolean defaultVTracking = true;
    final boolean defaultVNetworking = true;

    boolean currentVTracking = true;
    boolean currentVNetworking = true;

    ModuleConfiguration(@NonNull Countly cly, @NonNull CountlyConfig config) {
        super(cly, config);
        config.configProvider = this;
        configProvider = this;

        serverConfigEnabled = config.serverConfigurationEnabled;

        if (serverConfigEnabled) {
            //load the previously saved configuration
            loadStoredConfig();
            updateConfigs();
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

    void loadStoredConfig() {

    }

    void updateConfigs() {
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
    }

    void saveAndStoreDownloadedConfig(@NonNull JSONObject config) {
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

        try {
            latestRetrievedConfiguration = config.getJSONObject(keyRConfig);
        } catch (JSONException e) {
            latestRetrievedConfigurationFull = null;
            latestRetrievedConfiguration = null;

            L.w("[ModuleConfiguration] saveAndStoreDownloadedConfig, Failed retrieving internal config, " + e);
            return;
        }

        //save to storage
    }

    /**
     * Perform network request for retrieving latest config
     * If valid configu is downloaded, save it, and update the values
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
        String requestData = requestQueueProvider.prepareServerConfigRequest();
        ConnectionProcessor cp = requestQueueProvider.createConnectionProcessor();

        (new ImmediateRequestMaker()).doWork(requestData, "/o/sdk", cp, false, true, new ImmediateRequestMaker.InternalImmediateRequestCallback() {
            @Override public void callback(JSONObject checkResponse) {
                if (checkResponse == null) {
                    L.w("[ModuleConfiguration] Not possible to retrieve configuration data. Probably due to lack of connection to the server");
                    return;
                }

                L.d("[ModuleConfiguration] Retrieved configuration response: [" + checkResponse.toString() + "]");

                saveAndStoreDownloadedConfig(checkResponse);
            }
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
}
