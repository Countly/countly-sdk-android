package ly.count.sdk.internal;

import org.json.JSONException;
import org.json.JSONObject;

public class ModuleRating extends ModuleBase {

    protected static final String STAR_RATING_EVENT_KEY = "[CLY]_star_rating";

    protected static final Log.Module L = Log.module("Rating");

    InternalConfig internalConfig = null;

    @Override
    public void init(InternalConfig config) {
        internalConfig = config;
    }

    @Override
    public void onContextAcquired(Ctx ctx) {
        initiate(ctx);
    }

    @Override
    public void onLimitedContextAcquired(Ctx ctx) {
        initiate(ctx);
    }

    void initiate(Ctx ctx){

    }

    @Override
    public Integer getFeature() {
        return CoreFeature.StarRating.getIndex();
    }

    /**
     * Returns a object with the loaded preferences
     * @return
     */
    protected static StarRatingPreferences loadStarRatingPreferences() {
        Storage.read(this.)
        CountlyStore cs = new CountlyStore(context);
        String srpString = cs.getStarRatingPreferences();
        StarRatingPreferences srp;

        if(!srpString.equals("")) {
            JSONObject srJSON;
            try {
                srJSON = new JSONObject(srpString);
                srp = StarRatingPreferences.fromJSON(srJSON);
            } catch (JSONException e) {
                e.printStackTrace();
                srp = new StarRatingPreferences();
            }
        } else {
            srp = new StarRatingPreferences();
        }
        return srp;
    }

    /**
     * Save the star rating preferences object
     * @param srp
     */
    protected static void saveStarRatingPreferences(StarRatingPreferences srp) {
        CountlyStore cs = new CountlyStore(context);
        cs.setStarRatingPreferences(srp.toJSON().toString());
    }

    /**
     * Class that handles star rating internal state
     */
    static class StarRatingPreferences implements Storable {
        String appVersion = ""; //the name of the current version that we keep track of
        int sessionLimit = 5; //session limit for the automatic star rating
        int sessionAmount = 0; //session amount for the current version
        boolean isShownForCurrentVersion = false; //if automatic star rating has been shown for the current version
        boolean automaticRatingShouldBeShown = false; //if the automatic star rating should be shown
        boolean disabledAutomaticForNewVersions = false; //if the automatic star star should not be shown for every new apps version
        boolean automaticHasBeenShown = false; //if automatic star rating has been shown for any app's version
        boolean isDialogCancellable = true; //if star rating dialog is cancellable
        String dialogTextTitle = "App rating";
        String dialogTextMessage = "Please rate this app";
        String dialogTextDismiss = "Cancel";

        private static final String KEY_APP_VERSION = "sr_app_version";
        private static final String KEY_SESSION_LIMIT = "sr_session_limit";
        private static final String KEY_SESSION_AMOUNT = "sr_session_amount";
        private static final String KEY_IS_SHOWN_FOR_CURRENT = "sr_is_shown";
        private static final String KEY_AUTOMATIC_RATING_IS_SHOWN = "sr_is_automatic_shown";
        private static final String KEY_DISABLE_AUTOMATIC_NEW_VERSIONS = "sr_is_disable_automatic_new";
        private static final String KEY_AUTOMATIC_HAS_BEEN_SHOWN = "sr_automatic_has_been_shown";
        private static final String KEY_DIALOG_IS_CANCELLABLE = "sr_automatic_dialog_is_cancellable";
        private static final String KEY_DIALOG_TEXT_TITLE = "sr_text_title";
        private static final String KEY_DIALOG_TEXT_MESSAGE = "sr_text_message";
        private static final String KEY_DIALOG_TEXT_DISMISS = "sr_text_dismiss";

        /**
         * Create a JSONObject from the current state
         * @return
         */
        JSONObject toJSON() {
            final JSONObject json = new JSONObject();

            try {
                json.put(KEY_APP_VERSION, appVersion);
                json.put(KEY_SESSION_LIMIT, sessionLimit);
                json.put(KEY_SESSION_AMOUNT, sessionAmount);
                json.put(KEY_IS_SHOWN_FOR_CURRENT, isShownForCurrentVersion);
                json.put(KEY_AUTOMATIC_RATING_IS_SHOWN, automaticRatingShouldBeShown);
                json.put(KEY_DISABLE_AUTOMATIC_NEW_VERSIONS, disabledAutomaticForNewVersions);
                json.put(KEY_AUTOMATIC_HAS_BEEN_SHOWN, automaticHasBeenShown);
                json.put(KEY_DIALOG_IS_CANCELLABLE, isDialogCancellable);
                json.put(KEY_DIALOG_TEXT_TITLE, dialogTextTitle);
                json.put(KEY_DIALOG_TEXT_MESSAGE, dialogTextMessage);
                json.put(KEY_DIALOG_TEXT_DISMISS, dialogTextDismiss);

            }
            catch (JSONException e) {
                L.w("Got exception converting an StarRatingPreferences to JSON", e);
            }

            return json;
        }

        /**
         * Load the preference state from a JSONObject
         * @param json
         * @return
         */
        static StarRatingPreferences fromJSON(final JSONObject json) {

            StarRatingPreferences srp = new StarRatingPreferences();

            if(json != null) {
                try {
                    srp.appVersion = json.getString(KEY_APP_VERSION);
                    srp.sessionLimit = json.optInt(KEY_SESSION_LIMIT, 5);
                    srp.sessionAmount = json.optInt(KEY_SESSION_AMOUNT, 0);
                    srp.isShownForCurrentVersion = json.optBoolean(KEY_IS_SHOWN_FOR_CURRENT, false);
                    srp.automaticRatingShouldBeShown = json.optBoolean(KEY_AUTOMATIC_RATING_IS_SHOWN, true);
                    srp.disabledAutomaticForNewVersions = json.optBoolean(KEY_DISABLE_AUTOMATIC_NEW_VERSIONS, false);
                    srp.automaticHasBeenShown = json.optBoolean(KEY_AUTOMATIC_HAS_BEEN_SHOWN, false);
                    srp.isDialogCancellable = json.optBoolean(KEY_DIALOG_IS_CANCELLABLE, true);

                    if(!json.isNull(KEY_DIALOG_TEXT_TITLE)) {
                        srp.dialogTextTitle = json.getString(KEY_DIALOG_TEXT_TITLE);
                    }

                    if(!json.isNull(KEY_DIALOG_TEXT_MESSAGE)) {
                        srp.dialogTextMessage = json.getString(KEY_DIALOG_TEXT_MESSAGE);
                    }

                    if(!json.isNull(KEY_DIALOG_TEXT_DISMISS)) {
                        srp.dialogTextDismiss = json.getString(KEY_DIALOG_TEXT_DISMISS);
                    }

                } catch (JSONException e) {
                    L.w("Got exception converting JSON to a StarRatingPreferences", e);
                }
            }

            return srp;
        }

        @Override
        public Long storageId() {
            return null;
        }

        @Override
        public String storagePrefix() {
            return null;
        }

        @Override
        public byte[] store() {
            return new byte[0];
        }

        @Override
        public boolean restore(byte[] data) {
            return false;
        }
    }
}
