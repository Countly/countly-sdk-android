package ly.count.android.sdk;

import androidx.annotation.NonNull;

class ModuleHealthCheck extends ModuleBase {

    ImmediateRequestGenerator immediateRequestGenerator;
    HealthCheckCounter hCounter;

    boolean healthCheckEnabled = true;

    ModuleHealthCheck(@NonNull Countly cly, @NonNull CountlyConfig config) {
        super(cly, config);

        L.v("[ModuleHealthCheck] Initialising, enabled: " + healthCheckEnabled);
        hCounter = new HealthCheckCounter(config.storageProvider, L);
        config.healthTracker = hCounter;
        immediateRequestGenerator = config.immediateRequestGenerator;
        healthCheckEnabled = config.healthCheckEnabled;
    }

    @Override
    void initFinished(@NonNull final CountlyConfig config) {
        // this is because init order of module is before module device id
        // the device id provider is not yet set, so we have to specifically set it here
        deviceIdProvider = config.deviceIdProvider;

        if (healthCheckEnabled) {
            sendHealthCheck();
        }
    }

    @Override
    void halt() {
        hCounter = null;
        immediateRequestGenerator = null;
    }

    @Override
    void onActivityStopped(int updatedActivityCount) {
        hCounter.saveState();
    }

    void sendHealthCheck() {
        L.v("[ModuleHealthCheck] sendHealthCheck, attempting to send health information");

        if (deviceIdProvider.isTemporaryIdEnabled()) {
            //temporary id mode enabled, abort
            L.d("[ModuleHealthCheck] sendHealthCheck, sending health info of the SDK to server is aborted, temporary device ID mode is set");
            return;
        }

        String preparedMetrics = deviceInfo.getMetricsHealthCheck(_cly.context_, _cly.config_.metricOverride);

        StringBuilder requestData = new StringBuilder(requestQueueProvider.prepareHealthCheckRequest(preparedMetrics));
        ConnectionProcessor cp = requestQueueProvider.createConnectionProcessor();
        final boolean networkingIsEnabled = cp.configProvider_.getNetworkingEnabled();
        requestData.append(hCounter.createRequestParam());

        immediateRequestGenerator.CreateImmediateRequestMaker().doWork(requestData.toString(), "/i", cp, false, networkingIsEnabled, checkResponse -> {
            if (checkResponse == null) {
                L.w("[ModuleHealthCheck] No response for sending health check Probably due to lack of connection to the server");
                //sending failed, keep counters
                return;
            }

            L.d("[ModuleHealthCheck] Retrieved request response: [" + checkResponse.toString() + "]");

            if (!checkResponse.has("result")) {
                L.d("[ModuleHealthCheck] Retrieved request response does not match expected pattern");
                return;
            }

            //at this point we can expect that the request succeed and we can clear the counters
            L.d("[ModuleHealthCheck] sendHealthCheck, SDK health information sent successfully");
            hCounter.clearAndSave();
        }, L);
    }
}
