package ly.count.android.sdk;

/**
 * @deprecated No SDK call takes this callback any more. Use {@link RCDownloadCallback} with the remote config download
 * calls, or register it with {@link CountlyConfig#RemoteConfigRegisterGlobalCallback(RCDownloadCallback)}.
 */
@Deprecated
public interface RemoteConfigCallback {
    /**
     * Called after receiving remote config update result
     *
     * @param error if is null, it means that no errors were encountered
     */
    void callback(String error);
}