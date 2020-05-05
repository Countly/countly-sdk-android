package ly.count.android.sdk;

/**
 * Callbacks for star rating dialog
 */
public interface StarRatingCallback {
    void onRate(int rating);

    void onDismiss();
}
