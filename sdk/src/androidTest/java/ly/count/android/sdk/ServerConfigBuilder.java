package ly.count.android.sdk;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;

import static ly.count.android.sdk.ModuleConfiguration.keyRConfig;
import static ly.count.android.sdk.ModuleConfiguration.keyRConsentRequired;
import static ly.count.android.sdk.ModuleConfiguration.keyRContentZoneInterval;
import static ly.count.android.sdk.ModuleConfiguration.keyRCrashReporting;
import static ly.count.android.sdk.ModuleConfiguration.keyRCustomEventTracking;
import static ly.count.android.sdk.ModuleConfiguration.keyRDropOldRequestTime;
import static ly.count.android.sdk.ModuleConfiguration.keyREnterContentZone;
import static ly.count.android.sdk.ModuleConfiguration.keyREventQueueSize;
import static ly.count.android.sdk.ModuleConfiguration.keyRLimitBreadcrumb;
import static ly.count.android.sdk.ModuleConfiguration.keyRLimitKeyLength;
import static ly.count.android.sdk.ModuleConfiguration.keyRLimitSegValues;
import static ly.count.android.sdk.ModuleConfiguration.keyRLimitTraceLength;
import static ly.count.android.sdk.ModuleConfiguration.keyRLimitTraceLine;
import static ly.count.android.sdk.ModuleConfiguration.keyRLimitValueSize;
import static ly.count.android.sdk.ModuleConfiguration.keyRLocationTracking;
import static ly.count.android.sdk.ModuleConfiguration.keyRLogging;
import static ly.count.android.sdk.ModuleConfiguration.keyRNetworking;
import static ly.count.android.sdk.ModuleConfiguration.keyRRefreshContentZone;
import static ly.count.android.sdk.ModuleConfiguration.keyRReqQueueSize;
import static ly.count.android.sdk.ModuleConfiguration.keyRServerConfigUpdateInterval;
import static ly.count.android.sdk.ModuleConfiguration.keyRSessionTracking;
import static ly.count.android.sdk.ModuleConfiguration.keyRSessionUpdateInterval;
import static ly.count.android.sdk.ModuleConfiguration.keyRTimestamp;
import static ly.count.android.sdk.ModuleConfiguration.keyRTracking;
import static ly.count.android.sdk.ModuleConfiguration.keyRVersion;
import static ly.count.android.sdk.ModuleConfiguration.keyRViewTracking;

class ServerConfigBuilder {
    private final Map<String, Object> config;
    private long timestamp;
    private String version;

    public ServerConfigBuilder() {
        config = new HashMap<>();
        timestamp = System.currentTimeMillis();
        version = "1";
    }

    ServerConfigBuilder timestamp(long timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    ServerConfigBuilder version(String version) {
        this.version = version;
        return this;
    }

    ServerConfigBuilder tracking(boolean enabled) {
        config.put(keyRTracking, enabled);
        return this;
    }

    ServerConfigBuilder networking(boolean enabled) {
        config.put(keyRNetworking, enabled);
        return this;
    }

    ServerConfigBuilder crashReporting(boolean enabled) {
        config.put(keyRCrashReporting, enabled);
        return this;
    }

    ServerConfigBuilder viewTracking(boolean enabled) {
        config.put(keyRViewTracking, enabled);
        return this;
    }

    ServerConfigBuilder sessionTracking(boolean enabled) {
        config.put(keyRSessionTracking, enabled);
        return this;
    }

    ServerConfigBuilder customEventTracking(boolean enabled) {
        config.put(keyRCustomEventTracking, enabled);
        return this;
    }

    ServerConfigBuilder contentZone(boolean enabled) {
        config.put(keyREnterContentZone, enabled);
        return this;
    }

    ServerConfigBuilder locationTracking(boolean enabled) {
        config.put(keyRLocationTracking, enabled);
        return this;
    }

    ServerConfigBuilder refreshContentZone(boolean enabled) {
        config.put(keyRRefreshContentZone, enabled);
        return this;
    }

    ServerConfigBuilder serverConfigUpdateInterval(int interval) {
        config.put(keyRServerConfigUpdateInterval, interval);
        return this;
    }

    ServerConfigBuilder requestQueueSize(int size) {
        config.put(keyRReqQueueSize, size);
        return this;
    }

    ServerConfigBuilder eventQueueSize(int size) {
        config.put(keyREventQueueSize, size);
        return this;
    }

    ServerConfigBuilder logging(boolean enabled) {
        config.put(keyRLogging, enabled);
        return this;
    }

    ServerConfigBuilder sessionUpdateInterval(int interval) {
        config.put(keyRSessionUpdateInterval, interval);
        return this;
    }

    ServerConfigBuilder contentZoneInterval(int interval) {
        config.put(keyRContentZoneInterval, interval);
        return this;
    }

    ServerConfigBuilder consentRequired(boolean required) {
        config.put(keyRConsentRequired, required);
        return this;
    }

    ServerConfigBuilder dropOldRequestTime(int hours) {
        config.put(keyRDropOldRequestTime, hours);
        return this;
    }

    ServerConfigBuilder keyLengthLimit(int limit) {
        config.put(keyRLimitKeyLength, limit);
        return this;
    }

    ServerConfigBuilder valueSizeLimit(int limit) {
        config.put(keyRLimitValueSize, limit);
        return this;
    }

    ServerConfigBuilder segmentationValuesLimit(int limit) {
        config.put(keyRLimitSegValues, limit);
        return this;
    }

    ServerConfigBuilder breadcrumbLimit(int limit) {
        config.put(keyRLimitBreadcrumb, limit);
        return this;
    }

    ServerConfigBuilder traceLengthLimit(int limit) {
        config.put(keyRLimitTraceLength, limit);
        return this;
    }

    ServerConfigBuilder traceLinesLimit(int limit) {
        config.put(keyRLimitTraceLine, limit);
        return this;
    }

    ServerConfigBuilder defaults() {
        // Feature flags
        tracking(true);
        networking(true);
        crashReporting(true);
        viewTracking(true);
        sessionTracking(true);
        customEventTracking(true);
        contentZone(false);
        locationTracking(true);
        refreshContentZone(true);

        // Intervals and sizes
        serverConfigUpdateInterval(4);
        requestQueueSize(1000);
        eventQueueSize(100);
        logging(false);
        sessionUpdateInterval(60);
        contentZoneInterval(30);
        consentRequired(false);
        dropOldRequestTime(0);

        // Set default limits
        keyLengthLimit(Countly.maxKeyLengthDefault);
        valueSizeLimit(Countly.maxValueSizeDefault);
        segmentationValuesLimit(Countly.maxSegmentationValuesDefault);
        breadcrumbLimit(Countly.maxBreadcrumbCountDefault);
        traceLengthLimit(Countly.maxStackTraceLineLengthDefault);
        traceLinesLimit(Countly.maxStackTraceLinesPerThreadDefault);

        return this;
    }

    String build() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(keyRTimestamp, timestamp);
        jsonObject.put(keyRVersion, version);
        jsonObject.put(keyRConfig, new JSONObject(config));
        return jsonObject.toString();
    }

    Map<String, Object> buildConfigMap() {
        return new ConcurrentHashMap<String, Object>(config) {
        };
    }

    /**
     * Validates the configuration values against the provided Countly instance
     */
    void validateAgainst(Countly countly) {
        validateFeatureFlags(countly);
        validateIntervalsAndSizes(countly);
        validateLimits(countly);
    }

    private void validateFeatureFlags(Countly countly) {
        Assert.assertEquals(config.get(keyRTracking), countly.config_.configProvider.getTrackingEnabled());
        Assert.assertEquals(config.get(keyRNetworking), countly.config_.configProvider.getNetworkingEnabled());
        Assert.assertEquals(config.get(keyRCrashReporting), countly.config_.configProvider.getCrashReportingEnabled());
        Assert.assertEquals(config.get(keyRViewTracking), countly.config_.configProvider.getViewTrackingEnabled());
        Assert.assertEquals(config.get(keyRSessionTracking), countly.config_.configProvider.getSessionTrackingEnabled());
        Assert.assertEquals(config.get(keyRCustomEventTracking), countly.config_.configProvider.getCustomEventTrackingEnabled());
        Assert.assertEquals(config.get(keyREnterContentZone), countly.config_.configProvider.getContentZoneEnabled());
        Assert.assertEquals(config.get(keyRLocationTracking), countly.config_.configProvider.getLocationTrackingEnabled());
        Assert.assertEquals(config.get(keyRRefreshContentZone), countly.config_.configProvider.getRefreshContentZoneEnabled());
    }

    private void validateIntervalsAndSizes(Countly countly) {
        Assert.assertEquals(config.get(keyRServerConfigUpdateInterval), countly.moduleConfiguration.serverConfigUpdateInterval);
        Assert.assertEquals(config.get(keyRReqQueueSize), countly.config_.maxRequestQueueSize);
        Assert.assertEquals(config.get(keyREventQueueSize), countly.EVENT_QUEUE_SIZE_THRESHOLD);
        Assert.assertEquals(config.get(keyRLogging), countly.config_.loggingEnabled);

        try {
            Assert.assertEquals(config.get(keyRSessionUpdateInterval), countly.config_.sessionUpdateTimerDelay);
        } catch (AssertionError _ignored) {
            // This is a workaround for the issue where sessionUpdateTimerDelay is null by default
            Assert.assertNull(countly.config_.sessionUpdateTimerDelay);
        }

        Assert.assertEquals(config.get(keyRContentZoneInterval), countly.config_.content.zoneTimerInterval);
        Assert.assertEquals(config.get(keyRConsentRequired), countly.config_.shouldRequireConsent);
        Assert.assertEquals(config.get(keyRDropOldRequestTime), countly.config_.dropAgeHours);
    }

    private void validateLimits(Countly countly) {
        Assert.assertEquals(config.get(keyRLimitKeyLength), countly.config_.sdkInternalLimits.maxKeyLength);
        Assert.assertEquals(config.get(keyRLimitValueSize), countly.config_.sdkInternalLimits.maxValueSize);
        Assert.assertEquals(config.get(keyRLimitSegValues), countly.config_.sdkInternalLimits.maxSegmentationValues);
        Assert.assertEquals(config.get(keyRLimitBreadcrumb), countly.config_.sdkInternalLimits.maxBreadcrumbCount);
        Assert.assertEquals(config.get(keyRLimitTraceLength), countly.config_.sdkInternalLimits.maxStackTraceLineLength);
        Assert.assertEquals(config.get(keyRLimitTraceLine), countly.config_.sdkInternalLimits.maxStackTraceLinesPerThread);
    }
} 