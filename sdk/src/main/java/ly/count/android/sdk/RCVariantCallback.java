package ly.count.android.sdk;

public interface RCVariantCallback {

    /**
     * Called after fetching A/B test variants (can be used while fetching the experiment information too)
     *
     * @param rResult provides an enum for the result of the download request
     * @param error provides an error string if it exists
     */
    void callback(RequestResult rResult, String error);
}
