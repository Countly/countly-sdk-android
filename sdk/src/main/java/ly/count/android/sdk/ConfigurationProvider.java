package ly.count.android.sdk;

interface ConfigurationProvider {
    boolean getNetworkingEnabled();

    boolean getTrackingEnabled();

    boolean getSessionTrackingEnabled();

    boolean getViewTrackingEnabled();

    boolean getCustomEventTrackingEnabled();

    boolean getContentZoneEnabled();
}
