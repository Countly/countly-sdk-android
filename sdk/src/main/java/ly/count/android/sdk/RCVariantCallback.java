package ly.count.android.sdk;

public interface RCVariantCallback {

    /**
     * Called after fetching A/B test variants
     *
     * @param result provides an enum for the result of fetch request
     * @param error provides an error string if it exists
     */
    void callback(RequestResponse result, String error);
}
