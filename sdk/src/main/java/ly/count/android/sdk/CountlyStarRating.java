package ly.count.android.sdk;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.UiModeManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebView;
import android.widget.RatingBar;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

import static android.content.Context.UI_MODE_SERVICE;

public class CountlyStarRating {

    protected static final String STAR_RATING_EVENT_KEY = "[CLY]_star_rating";

    /**
     * Callbacks for star rating dialog
     */
    public interface RatingCallback {
        void onRate(int rating);
        void onDismiss();
    }

    /**
     * Call to manually show star rating dialog
     * @param context android context
     * @param callback
     */
    public static void showStarRating(Context context, final CountlyStarRating.RatingCallback callback){
        StarRatingPreferences srp = loadStarRatingPreferences(context);
        showStarRatingCustom(context, srp.dialogTextTitle, srp.dialogTextMessage, srp.dialogTextDismiss, srp.isDialogCancellable, callback);
    }

    /**
     * Method that created the star rating dialog
     * @param context android context
     * @param title
     * @param message
     * @param cancelText
     * @param isCancellable
     * @param callback
     */
    public static void showStarRatingCustom(
            final Context context,
            final String title,
            final String message,
            final String cancelText,
            final boolean isCancellable,
            final CountlyStarRating.RatingCallback callback) {

        if(!(context instanceof Activity)) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.e(Countly.TAG, "Can't show star rating dialog, the provided context is not based off a activity");
            }
            return;
        }

        LayoutInflater inflater = (LayoutInflater) context.getSystemService( Context.LAYOUT_INFLATER_SERVICE );
        View dialogLayout = inflater.inflate(R.layout.star_rating_layout, null);
        RatingBar ratingBar = dialogLayout.findViewById(R.id.ratingBar);

        final AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(isCancellable)
                .setView(dialogLayout)
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        if(callback != null) {
                            //call the dismiss callback ir the user clicks the back button or clicks outside the dialog
                            callback.onDismiss();
                        }
                    }
                })
                .setPositiveButton(cancelText, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if(callback != null) {
                            //call the dismiss callback if the user clicks the "dismiss" button
                            callback.onDismiss();
                        }
                    }
                });

        final AlertDialog dialog = builder.show();

        ratingBar.setOnRatingBarChangeListener(new RatingBar.OnRatingBarChangeListener() {
            @Override
            public void onRatingChanged(RatingBar ratingBar, float v, boolean b) {
                int rating = (int) v;

                if(Countly.sharedInstance().getConsent(Countly.CountlyFeatureNames.starRating)) {
                    Map<String, String> segm = new HashMap<>();
                    segm.put("platform", "android");
                    segm.put("app_version", DeviceInfo.getAppVersion(context));
                    segm.put("rating", "" + rating);

                    Countly.sharedInstance().recordEvent(STAR_RATING_EVENT_KEY, segm, 1);
                }

                dialog.dismiss();
                if(callback != null) {
                    callback.onRate(rating);
                }
            }
        });
    }

    /**
     * Class that handles star rating internal state
     */
    static class StarRatingPreferences {
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
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.w(Countly.TAG, "Got exception converting an StarRatingPreferences to JSON", e);
                }
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
                    if (Countly.sharedInstance().isLoggingEnabled()) {
                        Log.w(Countly.TAG, "Got exception converting JSON to a StarRatingPreferences", e);
                    }
                }
            }

            return srp;
        }
    }

    /**
     * Setting things that would be provided during initial config
     * @param context android context
     * @param limit limit for automatic rating
     * @param starRatingTextTitle provided title
     * @param starRatingTextMessage provided message
     * @param starRatingTextDismiss provided dismiss text
     */
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

    /**
     * Returns a object with the loaded preferences
     * @param context android context
     * @return
     */
    private static StarRatingPreferences loadStarRatingPreferences(Context context) {
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
     * @param context android context
     * @param srp
     */
    private static void saveStarRatingPreferences(Context context, StarRatingPreferences srp) {
        CountlyStore cs = new CountlyStore(context);
        cs.setStarRatingPreferences(srp.toJSON().toString());
    }

    /**
     * Set if the star rating dialog should be shown automatically
     * @param context android context
     * @param shouldShow
     */
    public static void setShowDialogAutomatically(Context context, boolean shouldShow) {
        StarRatingPreferences srp = loadStarRatingPreferences(context);
        srp.automaticRatingShouldBeShown = shouldShow;
        saveStarRatingPreferences(context, srp);
    }

    /**
     * Set if automatic star rating should be disabled for each new version.
     * By default automatic star rating will be shown for every new app version.
     * If this is set to true, star rating will be shown only once over apps lifetime
     * @param context android context
     * @param disableAsking if set true, will not show star rating for every new app version
     */
    public static void setStarRatingDisableAskingForEachAppVersion(Context context, boolean disableAsking) {
        StarRatingPreferences srp = loadStarRatingPreferences(context);
        srp.disabledAutomaticForNewVersions = disableAsking;
        saveStarRatingPreferences(context, srp);
    }

    /**
     * Register that a apps session has transpired. Will increase session counter and show automatic star rating if needed.
     * @param context android context
     * @param starRatingCallback
     */
    public static void registerAppSession(Context context, RatingCallback starRatingCallback) {
        StarRatingPreferences srp = loadStarRatingPreferences(context);

        String currentAppVersion = DeviceInfo.getAppVersion(context);

        //a new app version is released, reset all counters
        //if we show the rating once per apps lifetime, don't reset the counters
        if(currentAppVersion != null && !currentAppVersion.equals(srp.appVersion) && !srp.disabledAutomaticForNewVersions) {
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

    /**
     * Returns the session limit set for automatic star rating
     */
    public static int getAutomaticStarRatingSessionLimit(Context context){
        StarRatingPreferences srp = loadStarRatingPreferences(context);
        return srp.sessionLimit;
    }

    /**
     * Returns how many sessions has star rating counted internally
     * @param context android context
     * @return
     */
    public static int getCurrentVersionsSessionCount(Context context){
        StarRatingPreferences srp = loadStarRatingPreferences(context);
        return srp.sessionAmount;
    }

    /**
     * Set the automatic star rating session count back to 0
     * @param context android context
     */
    public static void clearAutomaticStarRatingSessionCount(Context context){
        StarRatingPreferences srp = loadStarRatingPreferences(context);
        srp.sessionAmount = 0;
        saveStarRatingPreferences(context, srp);
    }

    /**
     * Set if the star rating dialog is cancellable
     * @param context android context
     * @param isCancellable
     */
    public static void setIfRatingDialogIsCancellable(Context context, boolean isCancellable){
        StarRatingPreferences srp = loadStarRatingPreferences(context);
        srp.isDialogCancellable = isCancellable;
        saveStarRatingPreferences(context, srp);
    }

    /// Countly webDialog user rating

    /**
     * Used for callback from async task
     */
    protected interface InternalFeedbackRatingCallback {
        void callback(JSONObject checkResponse);
    }

    /**
     * Used for callback to developer from calling the Rating widget
     */
    public interface FeedbackRatingCallback {
        /**
         * Called after trying to show a rating dialog popup
         * @param error if is null, it means that no errors were encountered
         */
        void callback(String error);
    }

    protected static synchronized void showFeedbackPopup(final String widgetId, final String closeButtonText, final Activity activity, final Countly countly, final ConnectionQueue connectionQueue_, final FeedbackRatingCallback devCallback){
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Showing Feedback popup for widget id: [" + widgetId + "]");
        }

        if(widgetId == null || widgetId.isEmpty()){
            if(devCallback != null){
                devCallback.callback("Countly widgetId cannot be null or empty");
            }
            throw new IllegalArgumentException("Countly widgetId cannot be null or empty");
        }

        if(countly.getConsent(Countly.CountlyFeatureNames.starRating)) {
            //check the device type
            final boolean deviceIsPhone;
            final boolean deviceIsTablet;
            final boolean deviceIsTv;

            deviceIsTv = CountlyStarRating.isDeviceTv(activity);

            if(!deviceIsTv) {
                deviceIsPhone = !CountlyStarRating.isDeviceTablet(activity);
                deviceIsTablet = CountlyStarRating.isDeviceTablet(activity);
            } else {
                deviceIsTablet = false;
                deviceIsPhone = false;
            }

            ConnectionProcessor cp = connectionQueue_.createConnectionProcessor();
            URLConnection urlConnection;
            try {
                urlConnection = cp.urlConnectionForServerRequest("app_key=" + connectionQueue_.getAppKey() + "&widget_id=" + widgetId, "/o/feedback/widget?");
            } catch (IOException e) {
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.e(Countly.TAG, "IOException while checking for rating widget availability :[" + e.toString() + "]");
                }

                if(devCallback != null){ devCallback.callback("Encountered problem while checking for rating widget availability"); }
                return;
            }

            final String ratingWidgetUrl = connectionQueue_.getServerURL() + "/feedback?widget_id=" + widgetId + "&device_id=" + connectionQueue_.getDeviceId().getId() + "&app_key=" + connectionQueue_.getAppKey();

            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.d(Countly.TAG, "rating widget url :[" + ratingWidgetUrl + "]");
            }

            (new ImmediateRequestMaker()).execute(urlConnection, false, new InternalFeedbackRatingCallback() {
                @Override
                public void callback(JSONObject checkResponse) {
                    if(checkResponse == null){
                        if (Countly.sharedInstance().isLoggingEnabled()) {
                            Log.d(Countly.TAG, "Not possible to show Feedback popup for widget id: [" + widgetId + "], probably a lack of connection to the server");
                        }
                        if(devCallback != null){
                            devCallback.callback("Not possible to show Rating popup, probably no internet connection");
                        }
                    } else {
                        try {
                            JSONObject jDevices = checkResponse.getJSONObject("target_devices");

                            boolean showOnTv = jDevices.optBoolean("desktop", false);
                            boolean showOnPhone = jDevices.optBoolean("phone", false);
                            boolean showOnTablet = jDevices.optBoolean("tablet", false);

                            if((deviceIsPhone && showOnPhone) || (deviceIsTablet && showOnTablet) || (deviceIsTv && showOnTv)){
                                //it's possible to show the rating window on this device
                                if (Countly.sharedInstance().isLoggingEnabled()) {
                                    Log.d(Countly.TAG, "Showing Feedback popup for widget id: [" + widgetId + "]");
                                }

                                RatingDialogWebView webView = new RatingDialogWebView(activity);
                                webView.getSettings().setJavaScriptEnabled(true);
                                webView.loadUrl(ratingWidgetUrl);

                                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                                builder.setView(webView);
                                if(closeButtonText != null && !closeButtonText.isEmpty()) {
                                    builder.setNeutralButton(closeButtonText, null);
                                }
                                builder.show();
                            } else {
                                if(devCallback != null){
                                    devCallback.callback("Rating dialog is not meant for this form factor");
                                }
                            }

                        } catch (JSONException e) {
                            if (Countly.sharedInstance().isLoggingEnabled()) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            });
        } else {
            if(devCallback != null){
                devCallback.callback("Consent is not granted");
            }
        }
    }

    private static class RatingDialogWebView extends WebView {
        public RatingDialogWebView(Context context) {
            super(context);
        }

        /**
         * Without this override, the keyboard is not showing
         */
        @Override
        public boolean onCheckIsTextEditor() {
            return true;
        }
    }

    /**
     * Ascync task for making immediate server requests
     */
    protected static class ImmediateRequestMaker extends AsyncTask<Object, Void, JSONObject> {
        InternalFeedbackRatingCallback callback;

        /**
         * params fields:
         * 0 - urlConnection
         * 1 - requestShouldBeDelayed
         * 2 - callback         *
         */
        protected JSONObject doInBackground(Object... params) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.v(Countly.TAG, "Starting ImmediateRequestMaker request");
            }
            callback = (InternalFeedbackRatingCallback)params[2];
            boolean requestShouldBeDelayed = (boolean)params[1];

            HttpURLConnection connection = null;
            BufferedReader reader = null;
            boolean wasSuccess = true;

            try {
                if(requestShouldBeDelayed){
                    //used in cases after something has to be done after a device id change
                    if (Countly.sharedInstance().isLoggingEnabled()) {
                        Log.v(Countly.TAG, "ImmediateRequestMaker request should be delayed, waiting for 10.5 seconds");
                    }

                    try {
                        Thread.sleep(10500);
                    } catch (InterruptedException e) {
                        if (Countly.sharedInstance().isLoggingEnabled()) {
                            Log.w(Countly.TAG, "While waiting for 10 seconds in ImmediateRequestMaker, sleep was interrupted");
                        }
                    }
                }

                connection = (HttpURLConnection)params[0];
                connection.connect();

                InputStream stream;

                try{
                    //assume there will be no error
                    stream = connection.getInputStream();
                } catch (Exception ex){
                    //in case of exception, assume there was a error in the request
                    //and change streams
                    stream = connection.getErrorStream();
                    wasSuccess = false;
                }

                reader = new BufferedReader(new InputStreamReader(stream));

                StringBuilder buffer = new StringBuilder();
                String line = "";

                while ((line = reader.readLine()) != null) {
                    buffer.append(line).append("\n");
                }

                if(wasSuccess) {
                    return new JSONObject(buffer.toString());
                } else {
                    if (Countly.sharedInstance().isLoggingEnabled()) {
                        Log.e(Countly.TAG, "Encountered problem while making a immediate server request, :[" + buffer.toString() + "]");
                    }
                    return null;
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                try {
                    if (reader != null) {
                        reader.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(JSONObject result) {
            super.onPostExecute(result);
            //Log.d(TAG, "Post exec: [" + result + "]");

            if(callback != null){
                callback.callback(result);
            }
        }
    }

    //https://stackoverflow.com/a/40310535

    /**
     * Used for detecting if current device is a tablet of phone
     */
    protected static boolean isDeviceTablet(Activity activity) {
        Context activityContext = activity;
        boolean device_large = ((activityContext.getResources().getConfiguration().screenLayout &
                Configuration.SCREENLAYOUT_SIZE_MASK) ==
                Configuration.SCREENLAYOUT_SIZE_LARGE);

        if (device_large) {
            DisplayMetrics metrics = new DisplayMetrics();
            activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);

            //noinspection RedundantIfStatement
            if (metrics.densityDpi == DisplayMetrics.DENSITY_DEFAULT
                    || metrics.densityDpi == DisplayMetrics.DENSITY_HIGH
                    || metrics.densityDpi == DisplayMetrics.DENSITY_MEDIUM
                    || metrics.densityDpi == DisplayMetrics.DENSITY_TV
                    || metrics.densityDpi == DisplayMetrics.DENSITY_XHIGH) {
                return true;
            }
        }
        return false;
    }

    /**
     * Used for detecting if device is a tv
     * @return
     */
    @SuppressWarnings("RedundantIfStatement")
    protected static boolean isDeviceTv(Context context){
        final String TAG = "DeviceTypeRuntimeCheck";

        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(UI_MODE_SERVICE);

        if (uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true;
        } else {
            return false;
        }
    }

}
