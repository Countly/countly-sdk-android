package ly.count.android.sdk;

public interface RCVariantCallback {

    /**
     * Called after fetching A/B test variants
     *
     * @param result provides an enum for the result of fetch request
     */
    void callback(RequestResponse result);
}
