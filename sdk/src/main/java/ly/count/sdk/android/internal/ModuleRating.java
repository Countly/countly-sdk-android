package ly.count.sdk.android.internal;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebView;
import android.widget.RatingBar;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import ly.count.sdk.android.Countly;
import ly.count.sdk.android.sdk.R;
import ly.count.sdk.internal.CtxCore;
import ly.count.sdk.internal.InternalConfig;
import ly.count.sdk.internal.ModuleRatingCore;
import ly.count.sdk.internal.ModuleRequests;
import ly.count.sdk.internal.Request;

public class ModuleRating extends ModuleRatingCore {

    // timeout in milliseconds.
    // After how much time the timeout error is returned,
    // if the widget availability check does not return anything.
    long ratingWidgetTimeout = -1;

    // Link to the current rating widget availability request
    Request currentWidgetCheckRequest = null;

    // Callback to continue after receiving answer from availability request
    InternalFeedbackRatingCallback ratingWidgetCheckCallback = null;

    @Override
    public void init(InternalConfig config) {
        super.init(config);
    }

    @Override
    public void onContextAcquired(CtxCore ctx) {
        this.ctx = ctx;
        ratingWidgetTimeout = internalConfig.getRatingWidgetTimeout();

        Boolean shouldBeShown = internalConfig.getAutomaticStarRatingShouldBeShown();
        if(shouldBeShown != null){
            setShowDialogAutomatically(shouldBeShown);
        }

        setStarRatingInitConfig(internalConfig.getStarRatingSessionLimit(), internalConfig.getStarRatingTextTitle(), internalConfig.getStarRatingTextMessage(), internalConfig.getStarRatingTextDismiss());

        Boolean isCancelable = internalConfig.getStarRatingDialogIsCancelable();
        if(isCancelable != null) {
            setIfRatingDialogIsCancellable(isCancelable);
        }

        Boolean forNewVersion = internalConfig.getStarRatingDisabledForNewVersion();
        if(forNewVersion != null){
            setStarRatingDisableAskingForEachAppVersion(forNewVersion);
        }

        registerAppSession();
    }

    void ClearWidgetRequestFields (){
        ratingWidgetCheckCallback = null;
        currentWidgetCheckRequest = null;
    }

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

                Map<String, String> segm = new HashMap<>();
                segm.put("platform", "android");
                segm.put("rating", "" + rating);

                Countly.session(activity).event(STAR_RATING_EVENT_KEY)
                        .addSegment("platform", "android")
                        .addSegment("rating", "" + rating)
                        .setCount(1).record();

                dialog.dismiss();
                if(callback != null) {
                    callback.onRate(rating);
                }
            }
        });
    }

    /**
     * Register that a apps session has transpired. Will increase session counter and show automatic star rating if needed.
     */
    public void registerAppSession() {
        StarRatingPreferences srp = loadStarRatingPreferences();

        String currentAppVersion = Device.dev.getAppVersion((Ctx)ctx);

        //a new app version is released, reset all counters
        //if we show the rating once per apps lifetime, don't reset the counters
        if(currentAppVersion != null && !currentAppVersion.equals(srp.appVersion) && !srp.disabledAutomaticForNewVersions) {
            srp.appVersion = currentAppVersion;
            srp.isShownForCurrentVersion = false;
            srp.sessionAmount = 0;
        }

        srp.sessionAmount++;

        saveStarRatingPreferences(srp);
    }

    /**
     * Called to check if automatic star rating should be shown
     * @param activity
     * @param starRatingCallback
     */
    public void showAutomaticStarRatingIfNeeded(Activity activity, RatingCallback starRatingCallback){
        StarRatingPreferences srp = loadStarRatingPreferences();

        if(srp.sessionAmount >= srp.sessionLimit && !srp.isShownForCurrentVersion && srp.automaticRatingShouldBeShown && !(srp.disabledAutomaticForNewVersions && srp.automaticHasBeenShown)) {
            showStarRating(activity, starRatingCallback);
            srp.isShownForCurrentVersion = true;
            srp.automaticHasBeenShown = true;
            saveStarRatingPreferences(srp);
        }
    }

    @Override
    public Boolean onRequest(Request request){
        //indicate that this module is the owner
        request.own(ModuleRating.class);
        //returned to indicate that the request is ready
        return true;
    }

    /// Countly webDialog user rating

    /**
     * Used for callback from async task
     */
    protected interface InternalFeedbackRatingCallback {
        void callback(JSONObject checkResponse, String returnedError);
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

    @Override
    public void onRequestCompleted(Request request, String response, int responseCode) {
        if (currentWidgetCheckRequest == null) {
            //this incoming request probably is from a previous session, ignore it
            L.w("Received a widget availability response from a previous request");
            return;
        }

        if (!currentWidgetCheckRequest.storageId().equals(request.storageId())) {
            //if id's don't match then the response is not from the request we are expecting
            //assume that there is a another response coming and ignore this
            L.w("Received a widget availability response from a request with a different ID");
            return;
        }

        if (responseCode == 200) {
            //continue only if good response

            try {
                JSONObject jobj = new JSONObject(response);

                if (ratingWidgetCheckCallback != null) { ratingWidgetCheckCallback.callback(jobj, null); }
            } catch (JSONException e) {
                //can't create object, probably returned something unexpected, return error
                if (ratingWidgetCheckCallback != null) { ratingWidgetCheckCallback.callback(null, "Server returned unexpected result"); }
            }
        } else {
            //assume error
            L.w("Received a widget availability response from a request with a different ID");

            try {
                JSONObject jobj = new JSONObject(response);

                String returnedError = jobj.getString("result");

                if (ratingWidgetCheckCallback != null) { ratingWidgetCheckCallback.callback(null, "Server returned error: [" + returnedError + "]"); }
            } catch (JSONException e) {
                if (ratingWidgetCheckCallback != null) { ratingWidgetCheckCallback.callback(null, "Server returned unexpected result"); }
            }
        }

        //always cleat everything
        ClearWidgetRequestFields();
    }

    public synchronized void showFeedbackPopup(final String widgetId, final String closeButtonText, final Activity activity, final FeedbackRatingCallback devCallback){
        L.d("Showing Feedback popup for widget id: [" + widgetId + "]");

        if(widgetId == null || widgetId.isEmpty()){
            L.e("Countly widgetId cannot be null or empty");
            if(devCallback != null){
                devCallback.callback("Countly widgetId cannot be null or empty");
            }
            throw new IllegalArgumentException("Countly widgetId cannot be null or empty");
        }

        if(activity == null){
            L.e("Activity cannot be null or empty");
            if(devCallback != null){
                devCallback.callback("Activity cannot be null or empty");
            }
            throw new IllegalArgumentException("Activity cannot be null or empty");
        }

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


        InternalConfig config = ctx.getConfig();
        final String ratingWidgetUrl = config.getServerURL() + "/feedback?widget_id=" + widgetId + "&device_id=" + config.getDeviceId().id + "&app_key=" + config.getServerAppKey();

        //prepare request
        L.d("Preparing rating widget availability check request");
        final Request req = ModuleRequests.ratingWidgetAvailabilityCheck(ctx, widgetId, ctx.getConfig(), ModuleRating.class);
        currentWidgetCheckRequest = req;

        //create a timeout in case nothing returns for a while
        final Handler handler = new Handler();
        final Runnable timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                if(req != null && currentWidgetCheckRequest != null && req.storageId().equals(currentWidgetCheckRequest.storageId())) {
                    //continue with cancellation if the request id's are the same
                    if (devCallback != null) {
                        devCallback.callback("Timeout reached, request cancelled");
                    }

                    ClearWidgetRequestFields();
                }
            }
        };

        ratingWidgetCheckCallback = new InternalFeedbackRatingCallback() {
            @Override
            public void callback(JSONObject checkResponse, final String returnedError) {
                //cancel timeout timer
                handler.removeCallbacks(timeoutRunnable);

                if(returnedError != null){
                    L.d("Not possible to show Feedback popup for widget id: [" + widgetId + "], encountered problem during processing the request: [" + returnedError + "]");
                    if(devCallback != null) {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() { devCallback.callback(returnedError); }
                        });
                    }
                    return;
                }

                if(checkResponse == null){
                    L.d("Not possible to show Feedback popup for widget id: [" + widgetId + "], probably a lack of connection to the server");
                    if(devCallback != null){
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() { devCallback.callback("Not possible to show Rating popup, probably no internet connection"); }
                        });
                    }
                    return;
                }

                try {
                    JSONObject jDevices = checkResponse.getJSONObject("target_devices");

                    boolean showOnTv = jDevices.optBoolean("desktop", false);
                    boolean showOnPhone = jDevices.optBoolean("phone", false);
                    boolean showOnTablet = jDevices.optBoolean("tablet", false);

                    if((deviceIsPhone && showOnPhone) || (deviceIsTablet && showOnTablet) || (deviceIsTv && showOnTv)){
                        //it's possible to show the rating window on this device
                        L.d("Showing Feedback popup for widget id: [" + widgetId + "]");

                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                RatingDialogWebView webView = new RatingDialogWebView(activity);
                                webView.getSettings().setJavaScriptEnabled(true);
                                webView.loadUrl(ratingWidgetUrl);

                                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                                builder.setView(webView);
                                if(closeButtonText != null && !closeButtonText.isEmpty()) {
                                    builder.setNeutralButton(closeButtonText, null);
                                }
                                builder.show();
                            }
                        });
                    } else {
                        if(devCallback != null){
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() { devCallback.callback("Rating dialog is not meant for this form factor"); }
                            });
                        }
                    }
                } catch (JSONException e) {
                    L.e("Exception while handling rating request", e);
                    if(devCallback != null){
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() { devCallback.callback("Exception while handling rating widget request"); }
                        });
                    }
                }
            }
        };

        //after request has been prepared, push it into networking
        ModuleRequests.pushAsync(ctx, req);

        //start timeout timer
        handler.postDelayed(timeoutRunnable, ratingWidgetTimeout);
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

    public class Ratings extends RatingsCore {
        /**
         * Shows the star rating dialog
         * @param activity the activity that will own the dialog
         * @param callback callback for the star rating dialog "rate" and "dismiss" events
         */
        public void showStarRating(Activity activity, RatingCallback callback){
            if(disabledModule) { return; }
            L.d("Showing star rating");

            ModuleRating.this.showStarRating(activity, callback);
        }

        /**
         * Show the rating dialog to the user
         * @param widgetId ID that identifies this dialog
         * @return
         */
        public synchronized void showFeedbackPopup(final String widgetId, final String closeButtonText, final Activity activity, final ModuleRating.FeedbackRatingCallback callback){
            if(disabledModule) { return; }
            L.d("Showing rating feedback widget");

            ModuleRating.this.showFeedbackPopup(widgetId, closeButtonText, activity, callback);
        }

        public void showAutomaticStarRatingIfNeeded(Activity activity, RatingCallback starRatingCallback){
            if(disabledModule) { return; }
            L.d("Checking if automatic star rating should be shown");

            ModuleRating.this.showAutomaticStarRatingIfNeeded(activity, starRatingCallback);
        }
    }
}
