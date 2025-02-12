package ly.count.android.sdk;

interface ConfigurationProvider {
    boolean getNetworkingEnabled();

    boolean getTrackingEnabled();

    int getRequestQueueSize();

    int getEventQueueSize();

    boolean getLoggingEnabled();

    int getSessionUpdateInterval();

    boolean getSessionTrackingEnabled();

    boolean getViewTrackingEnabled();

    boolean getCustomEventTrackingEnabled();

    boolean getContentZoneEnabled();

    int getContentZoneTimerInterval();

    int getConsentRequired();

    int getDropOldRequestTime();

    int getMaxKeyLength();

    int getMaxValueSize();

    int getMaxSegmentationValues();

    int getMaxBreadcrumbCount();

    int getMaxStackTraceLinesPerThread();

    int getMaxStackTraceLineLength();
}
