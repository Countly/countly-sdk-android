package ly.count.android.sdk;

import android.content.Context;

public class CountlyConfig {
    /**
     * Android context.
     * Mandatory field.
     */
    public Context context;

    /**
     * URL of the Countly server to submit data to.
     * Mandatory field.
     */
    public String serverURL;

    /**
     * app key for the application being tracked; find in the Countly Dashboard under Management &gt; Applications.
     * Mandatory field.
     */
    public String appKey;

    /**
     * unique ID for the device the app is running on; note that null in deviceID means that Countly will fall back to OpenUDID, then, if it's not available, to Google Advertising ID.
     */
    public String deviceID;

    /**
     * enum value specifying which device ID generation strategy Countly should use: OpenUDID or Google Advertising ID.
     */
    public DeviceId.Type idMode;

    /**
     * sets the limit after how many sessions, for each apps version, the automatic star rating dialog is shown.
     */
    public int starRatingLimit;

    /**
     * the callback function that will be called from the automatic star rating dialog.
     */
    public CountlyStarRating.RatingCallback starRatingCallback;

    /**
     * the shown title text for the star rating dialogs.
     */
    public String starRatingTextTitle;

    /**
     * the shown message text for the star rating dialogs.
     */
    public String starRatingTextMessage;

    /**
     * the shown dismiss button text for the shown star rating dialogs.
     */
    public String starRatingTextDismiss;

    /**
     * Android context.
     * Mandatory field.
     */
    public CountlyConfig setContext(Context context){
        this.context = context;
        return this;
    }

    /**
     * URL of the Countly server to submit data to.
     * Mandatory field.
     */
    public CountlyConfig setServerURL(String serverURL){
        this.serverURL = serverURL;
        return this;
    }

    /**
     * app key for the application being tracked; find in the Countly Dashboard under Management &gt; Applications.
     * Mandatory field.
     */
    public CountlyConfig setAppKey(String appKey){
        this.appKey = appKey;
        return this;
    }

    /**
     * unique ID for the device the app is running on; note that null in deviceID means that Countly will fall back to OpenUDID, then, if it's not available, to Google Advertising ID.
     */
    public CountlyConfig setDeviceId(String deviceID){
        this.deviceID = deviceID;
        return this;
    }

    /**
     * enum value specifying which device ID generation strategy Countly should use: OpenUDID or Google Advertising ID.
     */
    public CountlyConfig setIdMode(DeviceId.Type idMode){
        this.idMode = idMode;
        return this;
    }

    /**
     * sets the limit after how many sessions, for each apps version, the automatic star rating dialog is shown.
     */
    public CountlyConfig setStarRatingLimit(int starRatingLimit){
        this.starRatingLimit = starRatingLimit;
        return this;
    }

    /**
     * the callback function that will be called from the automatic star rating dialog.
     */
    public CountlyConfig setStarRatingCallback(CountlyStarRating.RatingCallback starRatingCallback){
        this.starRatingCallback = starRatingCallback;
        return this;
    }

    /**
     * the shown title text for the star rating dialogs.
     */
    public CountlyConfig setStarRatingTextTitle(String starRatingTextTitle){
        this.starRatingTextTitle = starRatingTextTitle;
        return this;
    }

    /**
     * the shown message text for the star rating dialogs.
     */
    public CountlyConfig setStarRatingTextMessage(String starRatingTextMessage){
        this.starRatingTextMessage = starRatingTextMessage;
        return this;
    }

    /**
     * the shown dismiss button text for the shown star rating dialogs.
     */
    public CountlyConfig setStarRatingTextDismiss(String starRatingTextDismiss){
        this.starRatingTextDismiss = starRatingTextDismiss;
        return this;
    }
}
