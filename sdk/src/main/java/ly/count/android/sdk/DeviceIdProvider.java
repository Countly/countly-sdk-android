package ly.count.android.sdk;

interface DeviceIdProvider {
    String getDeviceId();
    DeviceId getDeviceIdInstance();
    boolean isTemporaryIdEnabled();
}
