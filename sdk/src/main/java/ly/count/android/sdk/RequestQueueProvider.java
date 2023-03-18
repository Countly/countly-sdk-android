package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Map;

interface RequestQueueProvider {
    void beginSession(boolean locationDisabled, @Nullable String locationCountryCode, @Nullable String locationCity, @Nullable String locationGpsCoordinates, @Nullable String locationIpAddress, @NonNull String preparedMetrics);

    void updateSession(final int duration);

    void changeDeviceId(String deviceId, final int duration);

    void tokenSession(String token, Countly.CountlyMessagingMode mode, Countly.CountlyMessagingProvider provider);

    void endSession(final int duration);

    void endSession(final int duration, String deviceIdOverride);

    void sendLocation(boolean locationDisabled, String locationCountryCode, String locationCity, String locationGpsCoordinates, String locationIpAddress);

    void sendUserData(String userdata);

    void sendIndirectAttribution(@NonNull String attributionObj);

    void sendDirectAttributionLegacy(@NonNull String campaignID, @Nullable String userID);

    void sendDirectAttributionTest(@NonNull String attributionData);

    void sendCrashReport(@NonNull final String crashData, final boolean nonFatalCrash);

    void recordEvents(final String events);

    void sendConsentChanges(String formattedConsentChanges);

    void sendAPMCustomTrace(String key, Long durationMs, Long startMs, Long endMs, String customMetrics);

    void sendAPMNetworkTrace(String networkTraceKey, Long responseTimeMs, int responseCode, int requestPayloadSize, int responsePayloadSize, Long startMs, Long endMs);

    void sendAPMAppStart(long durationMs, Long startMs, Long endMs);

    void sendAPMScreenTime(boolean recordForegroundTime, long durationMs, Long startMs, Long endMs);

    void sendDirectRequest(@NonNull final Map<String, String> requestData);

    //todo these should be moved or replaced in the future
    boolean queueContainsTemporaryIdItems();

    void tick();

    ConnectionProcessor createConnectionProcessor();

    String prepareRemoteConfigRequest(@Nullable String keysInclude, @Nullable String keysExclude, @NonNull String preparedMetrics);

    String prepareRatingWidgetRequest(String widgetId);

    String prepareFeedbackListRequest();
}
