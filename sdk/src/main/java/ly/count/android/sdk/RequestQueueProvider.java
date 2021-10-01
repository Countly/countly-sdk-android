package ly.count.android.sdk;

import java.util.Map;

interface RequestQueueProvider {
    void beginSession(boolean locationDisabled, String locationCountryCode, String locationCity, String locationGpsCoordinates, String locationIpAddress);
    void updateSession(final int duration);
    void changeDeviceId(String deviceId, final int duration);
    void tokenSession(String token, Countly.CountlyMessagingMode mode, Countly.CountlyMessagingProvider provider);
    void endSession(final int duration);
    void endSession(final int duration, String deviceIdOverride);
    void sendLocation(boolean locationDisabled, String locationCountryCode, String locationCity, String locationGpsCoordinates, String locationIpAddress);
    void sendUserData(String userdata);
    void sendReferrerData(String referrer);
    void sendReferrerDataManual(String campaignID, String userID);
    void sendCrashReport(String error, boolean nonfatal, boolean isNativeCrash, final Map<String, Object> customSegmentation);
    void recordEvents(final String events);
    void sendConsentChanges(String formattedConsentChanges);
    void sendAPMCustomTrace(String key, Long durationMs, Long startMs, Long endMs, String customMetrics);
    void sendAPMNetworkTrace(String networkTraceKey, Long responseTimeMs, int responseCode, int requestPayloadSize, int responsePayloadSize, Long startMs, Long endMs);
    void sendAPMAppStart(long durationMs, Long startMs, Long endMs);
    void sendAPMScreenTime(boolean recordForegroundTime, long durationMs, Long startMs, Long endMs);

    //todo these should be moved or replaced in the future
    boolean queueContainsTemporaryIdItems();
    void tick();
    ConnectionProcessor createConnectionProcessor();
    String prepareRemoteConfigRequest(String keysInclude, String keysExclude);
    String prepareRatingWidgetRequest(String widgetId);
    String prepareFeedbackListRequest();

}
