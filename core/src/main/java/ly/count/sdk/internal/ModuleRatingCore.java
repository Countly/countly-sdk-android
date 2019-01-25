package ly.count.sdk.internal;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;

public class ModuleRatingCore extends ModuleBase {

    protected static final String STAR_RATING_EVENT_KEY = "[CLY]_star_rating";

    protected static final Log.Module L = Log.module("Rating");

    //disabled is set when a empty module is created
    //in instances when the rating feature was not enabled
    //when a module is disabled, developer facing functions do nothing
    protected boolean disabledModule = false;

    public final static Long storableStorageId = 123L;
    public final static String storableStoragePrefix = "rating";

    InternalConfig internalConfig = null;
    protected CtxCore ctx = null;

    @Override
    public void init(InternalConfig config) {
        internalConfig = config;
    }

    @Override
    public void onContextAcquired(CtxCore ctx) {
        this.ctx = ctx;
    }

    @Override
    public Integer getFeature() {
        return CoreFeature.StarRating.getIndex();
    }

    public void disableModule(){
        disabledModule = true;
    }

    /**
     * Callbacks for star rating dialog
     */
    public interface RatingCallback {
        void onRate(int rating);
        void onDismiss();
    }

    public void PurgeRatingInfo(){
        ctx.getSDK().storablePurge(ctx, storableStoragePrefix);
    }

    /**
     * Returns a object with the loaded preferences
     * @return
     */
    protected StarRatingPreferences loadStarRatingPreferences() {
        StarRatingPreferences srp = new StarRatingPreferences();
        Storage.read(ctx, srp);
        return srp;
    }

    /**
     * Save the star rating preferences object
     * @param srp
     */
    protected void saveStarRatingPreferences(StarRatingPreferences srp) {
        Storage.push(ctx, srp);
    }

    /**
     * Setting things that would be provided during initial config
     * @param limit limit for automatic rating
     * @param starRatingTextTitle provided title
     * @param starRatingTextMessage provided message
     * @param starRatingTextDismiss provided dismiss text
     */
    public void setStarRatingInitConfig(int limit, String starRatingTextTitle, String starRatingTextMessage, String starRatingTextDismiss) {
        StarRatingPreferences srp = loadStarRatingPreferences();

        if(limit >= 0) {
            srp.sessionLimit = limit;
        }

        if(starRatingTextTitle != null) {
            srp.dialogTextTitle = starRatingTextTitle;
        }

        if(starRatingTextMessage != null) {
            srp.dialogTextMessage = starRatingTextMessage;
        }

        if(starRatingTextDismiss != null) {
            srp.dialogTextDismiss = starRatingTextDismiss;
        }

        saveStarRatingPreferences(srp);
    }

    /**
     * Set if the star rating dialog should be shown automatically
     * @param shouldShow
     */
    public void setShowDialogAutomatically(boolean shouldShow) {
        StarRatingPreferences srp = loadStarRatingPreferences();
        srp.automaticRatingShouldBeShown = shouldShow;
        saveStarRatingPreferences(srp);
    }

    /**
     * Set if automatic star rating should be disabled for each new version.
     * By default automatic star rating will be shown for every new app version.
     * If this is set to true, star rating will be shown only once over apps lifetime
     * @param disableAsking if set true, will not show star rating for every new app version
     */
    public void setStarRatingDisableAskingForEachAppVersion(boolean disableAsking) {
        StarRatingPreferences srp = loadStarRatingPreferences();
        srp.disabledAutomaticForNewVersions = disableAsking;
        saveStarRatingPreferences(srp);
    }

    /**
     * Returns the session limit set for automatic star rating
     */
    public int getAutomaticStarRatingSessionLimit(){
        StarRatingPreferences srp = loadStarRatingPreferences();
        return srp.sessionLimit;
    }

    /**
     * Returns how many sessions has star rating counted internally
     * @return
     */
    public int getCurrentVersionsSessionCount(){
        StarRatingPreferences srp = loadStarRatingPreferences();
        return srp.sessionAmount;
    }

    /**
     * Set the automatic star rating session count back to 0
     */
    public void clearAutomaticStarRatingSessionCount(){
        StarRatingPreferences srp = loadStarRatingPreferences();
        srp.sessionAmount = 0;
        saveStarRatingPreferences(srp);
    }

    /**
     * Set if the star rating dialog is cancellable
     * @param isCancellable
     */
    public void setIfRatingDialogIsCancellable(boolean isCancellable){
        StarRatingPreferences srp = loadStarRatingPreferences();
        srp.isDialogCancellable = isCancellable;
        saveStarRatingPreferences(srp);
    }

    /**
     * Class that handles star rating internal state
     */
    public static class StarRatingPreferences implements Storable {
        public String appVersion = ""; //the name of the current version that we keep track of
        public int sessionLimit = 5; //session limit for the automatic star rating
        public int sessionAmount = 0; //session amount for the current version
        public boolean isShownForCurrentVersion = false; //if automatic star rating has been shown for the current version
        public boolean automaticRatingShouldBeShown = false; //if the automatic star rating should be shown
        public boolean disabledAutomaticForNewVersions = false; //if the automatic star star should not be shown for every new apps version
        public boolean automaticHasBeenShown = false; //if automatic star rating has been shown for any app's version
        public boolean isDialogCancellable = true; //if star rating dialog is cancellable
        public String dialogTextTitle = "App rating";
        public String dialogTextMessage = "Please rate this app";
        public String dialogTextDismiss = "Cancel";

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
        public JSONObject toJSON() {
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
         * //@param json
         * @return
         */
        public void fromJSON(final JSONObject json) {

            if(json != null) {
                try {
                    appVersion = json.getString(KEY_APP_VERSION);
                    sessionLimit = json.optInt(KEY_SESSION_LIMIT, 5);
                    sessionAmount = json.optInt(KEY_SESSION_AMOUNT, 0);
                    isShownForCurrentVersion = json.optBoolean(KEY_IS_SHOWN_FOR_CURRENT, false);
                    automaticRatingShouldBeShown = json.optBoolean(KEY_AUTOMATIC_RATING_IS_SHOWN, true);
                    disabledAutomaticForNewVersions = json.optBoolean(KEY_DISABLE_AUTOMATIC_NEW_VERSIONS, false);
                    automaticHasBeenShown = json.optBoolean(KEY_AUTOMATIC_HAS_BEEN_SHOWN, false);
                    isDialogCancellable = json.optBoolean(KEY_DIALOG_IS_CANCELLABLE, true);

                    if(!json.isNull(KEY_DIALOG_TEXT_TITLE)) {
                        dialogTextTitle = json.getString(KEY_DIALOG_TEXT_TITLE);
                    }

                    if(!json.isNull(KEY_DIALOG_TEXT_MESSAGE)) {
                        dialogTextMessage = json.getString(KEY_DIALOG_TEXT_MESSAGE);
                    }

                    if(!json.isNull(KEY_DIALOG_TEXT_DISMISS)) {
                        dialogTextDismiss = json.getString(KEY_DIALOG_TEXT_DISMISS);
                    }

                } catch (JSONException e) {
                    L.w("Got exception converting JSON to a StarRatingPreferences", e);
                }
            }
        }

        @Override
        public Long storageId() {
            return ModuleRatingCore.storableStorageId;
        }

        @Override
        public String storagePrefix() {
            return ModuleRatingCore.storableStoragePrefix;
        }

        @Override
        public byte[] store() {
            try {
                return toJSON().toString().getBytes(Utils.UTF8);
            } catch (UnsupportedEncodingException e) {
                L.wtf("UTF is not supported for Rating", e);
                return null;
            }
        }

        @Override
        public boolean restore(byte[] data) {
            try {
                String json = new String (data, Utils.UTF8);
                try {
                    JSONObject obj = new JSONObject(json);
                    fromJSON(obj);
                } catch (JSONException e) {
                    L.e("Couldn't decode Rating data successfully", e);
                }
                return true;
            } catch (UnsupportedEncodingException e) {
                L.wtf("Cannot deserialize Rating", e);
            }

            return false;
        }
    }

    protected class RatingsCore {
        /**
         * Set's the text's for the different fields in the star rating dialog. Set value null if for some field you want to keep the old value
         * @param starRatingTextTitle dialog's title text
         * @param starRatingTextMessage dialog's message text
         * @param starRatingTextDismiss dialog's dismiss buttons text
         */
        public synchronized void setStarRatingDialogTexts(String starRatingTextTitle, String starRatingTextMessage, String starRatingTextDismiss) {
            if(disabledModule) { return; }

            L.d("Setting star rating texts");

            ModuleRatingCore.this.setStarRatingInitConfig(-1, starRatingTextTitle, starRatingTextMessage, starRatingTextDismiss);
        }

        /**
         * Set if the star rating should be shown automatically
         * @param IsShownAutomatically set it true if you want to show the app star rating dialog automatically for each new version after the specified session amount
         */
        public synchronized void setIfStarRatingShownAutomatically(boolean IsShownAutomatically) {
            if(disabledModule) { return; }

            L.d("Setting to show star rating automatically: [" + IsShownAutomatically + "]");

            ModuleRatingCore.this.setShowDialogAutomatically(IsShownAutomatically);
        }

        /**
         * Set if the star rating is shown only once per app lifetime
         * @param disableAsking set true if you want to disable asking the app rating for each new app version (show it only once per apps lifetime)
         */
        public synchronized void setStarRatingDisableAskingForEachAppVersion(boolean disableAsking) {
            if(disabledModule) { return; }
            L.d("Setting to disable showing of star rating for each app version:[" + disableAsking + "]");

            ModuleRatingCore.this.setStarRatingDisableAskingForEachAppVersion(disableAsking);
        }

        /**
         * Set after how many sessions the automatic star rating will be shown for each app version
         * @param limit app session amount for the limit
         * @return Returns link to Countly for call chaining
         */
        public synchronized void setAutomaticStarRatingSessionLimit(int limit) {
            if(disabledModule) { return; }

            L.d("Setting automatic star rating session limit: [" + limit + "]");
            ModuleRatingCore.this.setStarRatingInitConfig(limit, null, null, null);
        }

        /**
         * Returns the session limit set for automatic star rating
         */
        public int getAutomaticStarRatingSessionLimit(){
            if(disabledModule) { return -1; }

            int sessionLimit = ModuleRatingCore.this.getAutomaticStarRatingSessionLimit();

            L.d("Getting automatic star rating session limit: [" + sessionLimit + "]");
            return sessionLimit;
        }

        /**
         * Returns how many sessions has star rating counted internally for the current apps version
         */
        public int getStarRatingsCurrentVersionsSessionCount(){
            if(disabledModule) { return -1; }

            int sessionCount = ModuleRatingCore.this.getCurrentVersionsSessionCount();

            L.d("Getting star rating current version session count: [" + sessionCount + "]");
            return sessionCount;
        }

        /**
         * Set the automatic star rating session count back to 0
         */
        public void clearAutomaticStarRatingSessionCount(){
            if(disabledModule) { return; }

            L.d("Clearing star rating session count");

            ModuleRatingCore.this.clearAutomaticStarRatingSessionCount();
        }

        /**
         * Set if the star rating dialog is cancellable
         * @param isCancellable set this true if it should be cancellable
         */
        public synchronized void setIfStarRatingDialogIsCancellable(boolean isCancellable){
            if(disabledModule) { return; }

            L.d("Setting if star rating is cancellable: [" + isCancellable + "]");

            ModuleRatingCore.this.setIfRatingDialogIsCancellable(isCancellable);
        }
    }
}
