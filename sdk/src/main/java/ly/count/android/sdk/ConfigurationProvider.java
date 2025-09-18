package ly.count.android.sdk;

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

    boolean getUseCutoutArea();
}
