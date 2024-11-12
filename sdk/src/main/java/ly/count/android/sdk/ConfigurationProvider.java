package ly.count.android.sdk;

interface ConfigurationProvider {
    boolean getNetworkingEnabled();

    boolean getTrackingEnabled();

    boolean getCrashReportingEnabled();
}
