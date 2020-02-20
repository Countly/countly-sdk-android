package ly.count.android.sdk;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;

class ModuleRatings extends ModuleBase {
    static final String STAR_RATING_EVENT_KEY = "[CLY]_star_rating";

    final Ratings ratingsInterface;

    ModuleRatings(Countly cly, CountlyConfig config) {
        super(cly);

        ratingsInterface = new Ratings();
    }

    private void recordManualRatingInternal(String widgetId, int rating, String email, String comment, boolean userCanBeContacted){
        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleRatings] Calling recordManualRatingInternal");
        }

        if(!Countly.sharedInstance().getConsent(Countly.CountlyFeatureNames.starRating)) {
            return;
        }

        if(widgetId == null){
            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[ModuleRatings] recordManualRatingInternal, provided widget ID is null, returning");
            }
            return;
        }

        if(rating < 0){
            rating = 0;

            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[ModuleRatings] recordManualRatingInternal, given rating too low, defaulting to 0");
            }
        }

        if(rating > 5){
            rating = 5;

            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[ModuleRatings] recordManualRatingInternal, given rating too high, defaulting to 5");
            }
        }

        Map<String, Object> segm = new HashMap<>();
        segm.put("platform", "android");
        segm.put("app_version", DeviceInfo.getAppVersion(_cly.context_));
        segm.put("rating", "" + rating);
        segm.put("widget_id", widgetId);
        segm.put("contactMe", userCanBeContacted);

        if(email != null && !email.isEmpty()) {
            segm.put("email", email);
        }

        if(comment != null && !comment.isEmpty()) {
            segm.put("comment", comment);
        }

        _cly.moduleEvents.recordEventInternal(ModuleRatings.STAR_RATING_EVENT_KEY, 1, 0, 0, segm, null);
    }

    @Override
    void halt(){

    }

    public class Ratings {

        /**
         * Record user rating manually without showing any message dialog.
         * @param widgetId widget ID to which this rating will be tied. You get it from the dashboard
         * @param rating value from 0 to 5 that will be set as the rating value
         * @param email email of the user
         * @param comment comment set by the user
         * @param userCanBeContacted set true if the user wants you to contact him
         */
        public void recordManualRating(String widgetId, int rating, String email, String comment, boolean userCanBeContacted){
            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[Ratings] Calling recordManualRating");
            }

            if (!_cly.isInitialized()) {
                throw new IllegalStateException("Countly.sharedInstance().init must be called before recordView");
            }

            if(widgetId == null || widgetId.isEmpty()){
                throw new IllegalStateException("A valid widgetID must be provided. The current one is either null or empty");
            }

            recordManualRatingInternal(widgetId, rating, email, comment, userCanBeContacted);
        }
    }
}
