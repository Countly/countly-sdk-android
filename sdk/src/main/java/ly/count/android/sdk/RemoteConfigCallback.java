package ly.count.android.sdk;

public interface RemoteConfigCallback {
    /**
     * Called after receiving remote config update result
     *
     * @param error if is null, it means that no errors were encountered
     */
    void callback(String error);
}