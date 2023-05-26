package ly.count.android.sdk;

public interface RemoteConfigVariantCallback {

    /**
     * Called after fetching A/B test variants
     *
     * @param result provides an enum for the result of fetch request
     */
    void callback(ImmediateRequestResponse result);
}
