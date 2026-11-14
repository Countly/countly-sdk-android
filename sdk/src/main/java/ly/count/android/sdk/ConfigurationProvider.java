package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

    boolean getAutomaticSessionTrackingEnabled();

    boolean getAutomaticViewTrackingEnabled();

    boolean getAutomaticCrashReportingEnabled();

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

    Set<String> getJourneyTriggerViews();

    // REQUEST COMPRESSION

    /**
     * @return whether a request body may be sent gzip-compressed now: the developer enabled it, the current server
     * said in its SDK behavior settings answer that it reads compressed bodies, and no compressed request was refused
     * in this run
     */
    default boolean getRequestCompressionEnabled() {
        return false;
    }

    /**
     * Stops request compression until the SDK starts again, called after the server or something in front of it
     * answered a compressed request without accepting it
     */
    default void blockRequestCompression() {
    }

    // PER USER LOG GATHERING

    // The defaults below are the "no directive seen yet" state, so a provider that does not deal with log
    // gathering (test fakes included) does not have to spell them out.

    /**
     * @return whether a log gathering directive has been seen at all and, if it has, what it decided
     */
    default @NonNull ModuleConfiguration.LogGatheringState getLogGatheringState() {
        return ModuleConfiguration.LogGatheringState.UNDECIDED;
    }

    /**
     * @return the gather id that every uploaded batch has to carry, null while log gathering is not on
     */
    default @Nullable String getLogGatheringId() {
        return null;
    }

    /**
     * @return the level characters to gather, a subset of "ewidv" (e=Error w=Warning i=Info d=Debug v=Verbose), never empty
     */
    default @NonNull String getLogGatheringLevels() {
        return ModuleConfiguration.logGatheringAllLevels;
    }

    /**
     * @return how many lines to buffer before uploading a batch, always within [10, 500]
     */
    default int getLogGatheringBatchSize() {
        return ModuleConfiguration.logGatheringDefaultBatchSize;
    }

    class FilterList<T> {
        T filterList;
        boolean isWhitelist;

        FilterList(T filterList, boolean isWhitelist) {
            this.filterList = filterList;
            this.isWhitelist = isWhitelist;
        }
    }
}
