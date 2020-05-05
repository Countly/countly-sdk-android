package ly.count.android.sdk;

/**
 * Used for callback to developer from calling the Rating widget
 */
public interface FeedbackRatingCallback {
    /**
     * Called after trying to show a rating dialog popup
     *
     * @param error if is null, it means that no errors were encountered
     */
    void callback(String error);
}