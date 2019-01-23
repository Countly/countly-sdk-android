package ly.count.sdk.android.internal;

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
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import ly.count.sdk.android.sdk.R;
import ly.count.sdk.internal.ModuleRatingCore;

import static android.content.Context.UI_MODE_SERVICE;

public class ModuleRating extends ModuleRatingCore {

    /**
     * Call to manually show star rating dialog
     * @param activity
     * @param callback
     */
    public void showStarRating(Activity activity, final ModuleRating.RatingCallback callback){
        StarRatingPreferences srp = loadStarRatingPreferences();
        showStarRatingCustom(activity, srp.dialogTextTitle, srp.dialogTextMessage, srp.dialogTextDismiss, srp.isDialogCancellable, callback);
    }

    /**
     * Method that created the star rating dialog
     * @param activity
     * @param title
     * @param message
     * @param cancelText
     * @param isCancellable
     * @param callback
     */
    public void showStarRatingCustom(
            final Activity activity,
            final String title,
            final String message,
            final String cancelText,
            final boolean isCancellable,
            final ModuleRating.RatingCallback callback) {

        LayoutInflater inflater = (LayoutInflater) activity.getSystemService( Context.LAYOUT_INFLATER_SERVICE );
        View dialogLayout = inflater.inflate(R.layout.star_rating_layout, null);
        RatingBar ratingBar = (RatingBar) dialogLayout.findViewById(R.id.ratingBar);

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity)
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

                //if(Countly.sharedInstance().getConsent(Countly.CountlyFeatureNames.starRating)) {
                    Map<String, String> segm = new HashMap<>();
                    segm.put("platform", "android");
                    //segm.put("app_version", Device.dev.getAppVersion(_ctx));
                    segm.put("rating", "" + rating);

                    //Countly.sharedInstance().recordEvent(STAR_RATING_EVENT_KEY, segm, 1);
                //}

                dialog.dismiss();
                if(callback != null) {
                    callback.onRate(rating);
                }
            }
        });
    }

    /**
     * Register that a apps session has transpired. Will increase session counter and show automatic star rating if needed.
     * @param starRatingCallback
     */
    /*
    public void registerAppSession(Activity activity, RatingCallback starRatingCallback) {
        StarRatingPreferences srp = loadStarRatingPreferences();

        String currentAppVersion = Device.dev.getAppVersion(activity);

        //a new app version is released, reset all counters
        //if we show the rating once per apps lifetime, don't reset the counters
        if(currentAppVersion != null && !currentAppVersion.equals(srp.appVersion) && !srp.disabledAutomaticForNewVersions) {
            srp.appVersion = currentAppVersion;
            srp.isShownForCurrentVersion = false;
            srp.sessionAmount = 0;
        }

        srp.sessionAmount++;
        if(srp.sessionAmount >= srp.sessionLimit && !srp.isShownForCurrentVersion && srp.automaticRatingShouldBeShown && !(srp.disabledAutomaticForNewVersions && srp.automaticHasBeenShown)) {
            showStarRating(activity, starRatingCallback);
            srp.isShownForCurrentVersion = true;
            srp.automaticHasBeenShown = true;
        }

        saveStarRatingPreferences(srp);
    }
*/

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
/*
    protected static synchronized void showFeedbackPopup(final String widgetId, final String closeButtonText, final Activity activity, final Countly countly, final ConnectionQueue connectionQueue_, final FeedbackRatingCallback devCallback){
        L.d("Showing Feedback popup for widget id: [" + widgetId + "]");

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

            deviceIsTv = Device.dev.isDeviceTv(activity);

            if(!deviceIsTv) {
                deviceIsPhone = !Device.dev.isDeviceTablet(activity);
                deviceIsTablet = Device.dev.isDeviceTablet(activity);
            } else {
                deviceIsTablet = false;
                deviceIsPhone = false;
            }

            final String checkUrl = connectionQueue_.getServerURL() + "/o/feedback/widget?app_key=" + connectionQueue_.getAppKey() + "&widget_id=" + widgetId;
            final String ratingWidgetUrl = connectionQueue_.getServerURL() + "/feedback?widget_id=" + widgetId + "&device_id=" + connectionQueue_.getDeviceId().getId() + "&app_key=" + connectionQueue_.getAppKey();

            L.d("Check url: [" + checkUrl+ "], rating widget url :[" + ratingWidgetUrl + "]");

            (new RatingAvailabilityChecker()).execute(checkUrl, new InternalFeedbackRatingCallback() {
                @Override
                public void callback(JSONObject checkResponse) {
                    if(checkResponse == null){
                        L.d("Not possible to show Feedback popup for widget id: [" + widgetId + "], probably a lack of connection to the server");
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
                                L.d("Showing Feedback popup for widget id: [" + widgetId + "]");

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
                            L.e("Ēxception while handling rating request", e);
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
*/
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
     * Ascync task for checking the Rating dialog availability
     */
    private static class RatingAvailabilityChecker extends AsyncTask<Object, Void, JSONObject> {
        InternalFeedbackRatingCallback callback;

        protected JSONObject doInBackground(Object... params) {
            callback = (InternalFeedbackRatingCallback)params[1];

            HttpURLConnection connection = null;
            BufferedReader reader = null;

            try {
                URL url = new URL((String)params[0]);
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                InputStream stream = connection.getInputStream();

                reader = new BufferedReader(new InputStreamReader(stream));

                StringBuffer buffer = new StringBuffer();
                String line = "";

                while ((line = reader.readLine()) != null) {
                    buffer.append(line+"\n");
                }

                return new JSONObject(buffer.toString());
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
}
