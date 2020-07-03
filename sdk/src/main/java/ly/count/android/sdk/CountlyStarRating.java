package ly.count.android.sdk;

public class CountlyStarRating {

    /**
     * Callbacks for star rating dialog
     */
    //@Deprecated use StarRatingCallback
    public interface RatingCallback {
        void onRate(int rating);

        void onDismiss();
    }

    /**
     * Used for callback to developer from calling the Rating widget
     */
    //@Deprecated
    public interface FeedbackRatingCallback {
        /**
         * Called after trying to show a rating dialog popup
         *
         * @param error if is null, it means that no errors were encountered
         */
        void callback(String error);
    }
}
