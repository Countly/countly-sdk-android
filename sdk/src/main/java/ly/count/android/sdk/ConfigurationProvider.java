package ly.count.android.sdk;

import java.util.Map;
import java.util.Set;

interface ConfigurationProvider {
    boolean getNetworkingEnabled();

    boolean getTrackingEnabled();

    boolean getSessionTrackingEnabled();

    boolean getViewTrackingEnabled();

    boolean getCustomEventTrackingEnabled();

    boolean getContentZoneEnabled();

    boolean getCrashReportingEnabled();

    boolean getLocationTrackingEnabled();

    boolean getRefreshContentZoneEnabled();

    // BACKOFF MECHANISM
    boolean getBOMEnabled();

    int getBOMAcceptedTimeoutSeconds();

    double getBOMRQPercentage();

    int getBOMRequestAge();

    int getBOMDuration();

    int getRequestTimeoutDurationMillis();

    int getUserPropertyCacheLimit();

    // LISTING FILTERS
    boolean getFilterIsWhitelist();

    Set<String> getEventFilterSet();

    Set<String> getUserPropertyFilterSet();

    Set<String> getSegmentationFilterSet();

    Map<String, Set<String>> getEventSegmentationFilterMap();
}
