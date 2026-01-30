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

    FilterList<Set<String>> getEventFilterList();

    FilterList<Set<String>> getUserPropertyFilterList();

    FilterList<Set<String>> getSegmentationFilterList();

    FilterList<Map<String, Set<String>>> getEventSegmentationFilterList();

    Set<String> getJourneyTriggerEvents();

    class FilterList<T> {
        T filterList;
        boolean isWhitelist;

        FilterList(T filterList, boolean isWhitelist) {
            this.filterList = filterList;
            this.isWhitelist = isWhitelist;
        }
    }
}
