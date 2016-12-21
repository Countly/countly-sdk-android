package ly.count.android.sdk;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RatingBar;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Arturs on 29.11.2016..
 */
public class CountlyStarRating {

    public interface RatingCallback {
        void OnRate(int rating);
        void OnDismiss();
    }

    public static void showStarRating(Context context, final CountlyStarRating.RatingCallback callback){
        StarRatingPreferences srp = loadStarRatingPreferences(context);
        showStarRatingCustom(context, srp.dialogTextTitle, srp.dialogTextMessage, srp.dialogTextDismiss, callback);
    }

    public static void showStarRatingCustom(
            final Context context,
            final String title,
            final String message,
            final String cancelText,
            final CountlyStarRating.RatingCallback callback) {

        LayoutInflater inflater = (LayoutInflater) context.getSystemService( Context.LAYOUT_INFLATER_SERVICE );
        View dialoglayout = inflater.inflate(R.layout.star_rating_layout, null);
        RatingBar ratingBar = (RatingBar) dialoglayout.findViewById(R.id.ratingBar);

        final AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setView(dialoglayout)
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        if(callback != null) {
                            //call the dismiss callback ir the user clicks the back button or clicks outside the dialog
                            callback.OnDismiss();
                        }
                    }
                })
                .setPositiveButton(cancelText, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if(callback != null) {
                            //call the dismiss callback if the user clicks the "dismiss" button
                            callback.OnDismiss();
                        }
                    }
                });

        final AlertDialog dialog = builder.show();

        ratingBar.setOnRatingBarChangeListener(new RatingBar.OnRatingBarChangeListener() {
            @Override
            public void onRatingChanged(RatingBar ratingBar, float v, boolean b) {
                int rating = (int)v;
                Map<String,String> segm = new HashMap<String, String>();
                segm.put("platform", "android");
                segm.put("app_version", DeviceInfo.getAppVersion(context));
                segm.put("rating", "" + rating);

                Countly.sharedInstance().recordEvent("[CLY]_star_rating", segm, 1);

                dialog.cancel();
                if(callback != null) {
                    callback.OnRate(rating);
                }
            }
        });
    }

    static class StarRatingPreferences {
        String appVersion = ""; //the name of the current version that we keep track of
        int sessionLimit = 5;
        int sessionAmount = 0; //session amount for the current version
        boolean isShownForCurrentVersion = false;
        boolean automaticRatingShouldBeShown = false;
        boolean disabledAutomaticForNewVersions = false;
        boolean automaticHasBeenShown = false;
        String dialogTextTitle = "App rating";
        String dialogTextMessage = "Please rate this app";
        String dialogTextDismiss = "Cancel";

        private static String KEY_APP_VERSION = "sr_app_version";
        private static String KEY_SESSION_LIMIT = "sr_session_limit";
        private static String KEY_SESSION_AMOUNT = "sr_session_amount";
        private static String KEY_IS_SHOWN_FOR_CURRENT = "sr_is_shown";
        private static String KEY_AUTOMATIC_RATING_IS_SHOWN = "sr_is_automatic_shown";
        private static String KEY_DISABLE_AUTOMATIC_NEW_VERSIONS = "sr_is_disable_automatic_new";
        private static String KEY_AUTOMATIC_HAS_BEEN_SHOWN = "sr_automatic_has_been_shown";
        private static String KEY_DIALOG_TEXT_TITLE = "sr_text_title";
        private static String KEY_DIALOG_TEXT_MESSAGE = "sr_text_message";
        private static String KEY_DIALOG_TEXT_DISMISS = "sr_text_dismiss";

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
                json.put(KEY_DIALOG_TEXT_TITLE, dialogTextTitle);
                json.put(KEY_DIALOG_TEXT_MESSAGE, dialogTextMessage);
                json.put(KEY_DIALOG_TEXT_DISMISS, dialogTextDismiss);

            }
            catch (JSONException e) {
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.w(Countly.TAG, "Got exception converting an StarRatingPreferences to JSON", e);
                }
            }

            return json;
        }

        static StarRatingPreferences fromJSON(final JSONObject json) {

            StarRatingPreferences srp = new StarRatingPreferences();

            if(json != null) {
                try {
                    srp.appVersion = json.getString(KEY_APP_VERSION);
                    srp.sessionLimit = json.optInt(KEY_SESSION_LIMIT, 5);
                    srp.sessionAmount = json.optInt(KEY_SESSION_AMOUNT, 0);
                    srp.isShownForCurrentVersion = json.optBoolean(KEY_IS_SHOWN_FOR_CURRENT, false);
                    srp.automaticRatingShouldBeShown = json.optBoolean(KEY_AUTOMATIC_RATING_IS_SHOWN, false);
                    srp.disabledAutomaticForNewVersions = json.optBoolean(KEY_DISABLE_AUTOMATIC_NEW_VERSIONS, false);
                    srp.automaticHasBeenShown = json.optBoolean(KEY_AUTOMATIC_HAS_BEEN_SHOWN, false);

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
                    if (Countly.sharedInstance().isLoggingEnabled()) {
                        Log.w(Countly.TAG, "Got exception converting JSON to a StarRatingPreferences", e);
                    }
                }
            }

            return srp;
        }
    }

    public static void setStarRatingInitConfig(Context context, int limit, String starRatingTextTitle, String starRatingTextMessage, String starRatingTextDismiss) {
        StarRatingPreferences srp = loadStarRatingPreferences(context);

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

        saveStarRatingPreferences(context, srp);
    }

    private static StarRatingPreferences loadStarRatingPreferences(Context context) {
        CountlyStore cs = new CountlyStore(context);
        String srpString = cs.getStarRatingPreferences();
        StarRatingPreferences srp;

        if(!srpString.equals("")) {
            JSONObject srJSON = null;
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

    private static void saveStarRatingPreferences(Context context, StarRatingPreferences srp) {
        CountlyStore cs = new CountlyStore(context);
        cs.setStarRatingPreferences(srp.toJSON().toString());
    }

    public static void setShowDialogAutomatically(Context context, boolean shouldShow) {
        StarRatingPreferences srp = loadStarRatingPreferences(context);
        srp.automaticRatingShouldBeShown = shouldShow;
        saveStarRatingPreferences(context, srp);
    }

    public static void setStarRatingDisableAskingForEachAppVersion(Context context, boolean disableAsking) {
        StarRatingPreferences srp = loadStarRatingPreferences(context);
        srp.disabledAutomaticForNewVersions = disableAsking;
        saveStarRatingPreferences(context, srp);
    }

    public static void registerAppSession(Context context, RatingCallback starRatingCallback) {
        StarRatingPreferences srp = loadStarRatingPreferences(context);

        String currentAppVersion = DeviceInfo.getAppVersion(context);

        //a new app version is released, reset all counters
        //if we show the rating once per apps lifetime, don't reset the counters
        if(!currentAppVersion.equals(srp.appVersion) && !srp.disabledAutomaticForNewVersions) {
            srp.appVersion = currentAppVersion;
            srp.isShownForCurrentVersion = false;
            srp.sessionAmount = 0;
        }

        srp.sessionAmount++;
        if(srp.sessionAmount >= srp.sessionLimit && !srp.isShownForCurrentVersion && srp.automaticRatingShouldBeShown && !(srp.disabledAutomaticForNewVersions && srp.automaticHasBeenShown)) {
            showStarRating(context, starRatingCallback);
            srp.isShownForCurrentVersion = true;
            srp.automaticHasBeenShown = true;
        }

        saveStarRatingPreferences(context, srp);
    }
}
