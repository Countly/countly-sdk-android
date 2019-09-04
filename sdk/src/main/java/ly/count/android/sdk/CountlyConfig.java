package ly.count.android.sdk;

import android.content.Context;

import java.util.HashMap;

public class CountlyConfig {
    /**
     * Android context.
     * Mandatory field.
     */
    protected Context context;

    /**
     * URL of the Countly server to submit data to.
     * Mandatory field.
     */
    protected String serverURL;

    /**
     * app key for the application being tracked; find in the Countly Dashboard under Management &gt; Applications.
     * Mandatory field.
     */
    protected String appKey;

    /**
     * unique ID for the device the app is running on; note that null in deviceID means that Countly will fall back to OpenUDID, then, if it's not available, to Google Advertising ID.
     */
    protected String deviceID;

    /**
     * enum value specifying which device ID generation strategy Countly should use: OpenUDID or Google Advertising ID.
     */
    protected DeviceId.Type idMode;

    /**
     * sets the limit after how many sessions, for each apps version, the automatic star rating dialog is shown.
     */
    protected int starRatingLimit;

    /**
     * the callback function that will be called from the automatic star rating dialog.
     */
    protected CountlyStarRating.RatingCallback starRatingCallback;

    /**
     * the shown title text for the star rating dialogs.
     */
    protected String starRatingTextTitle;

    /**
     * the shown message text for the star rating dialogs.
     */
    protected String starRatingTextMessage;

    /**
     * the shown dismiss button text for the shown star rating dialogs.
     */
    protected String starRatingTextDismiss;

    protected boolean loggingEnabled = false;

    protected boolean enableUnhandledCrashReporting = false;

    protected boolean enableViewTracking = false;

    protected boolean autoTrackingUseShortName = false;

    protected HashMap<String, String> customNetworkRequestHeaders = null;

    protected boolean pushIntentAddMetadata = false;

    protected boolean enableRemoteConfigAutomaticDownload = false;
    RemoteConfig.RemoteConfigCallback remoteConfigCallback = null;

    protected boolean shouldRequireConsent = false;
    protected String[] enabledFeatureNames = null;

    boolean httpPostForced = false;

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

    /**
     * Set to true of you want to enable countly internal debugging logs
     * @param enabled
     */
    public CountlyConfig setLoggingEnabled(boolean enabled){
        this.loggingEnabled = enabled;
        return this;
    }

    public CountlyConfig enableCrashReporting(){
        this.enableUnhandledCrashReporting = true;
        return this;
    }

    public CountlyConfig setViewTracking(boolean enable){
        this.enableViewTracking = enable;
        return this;
    }

    public CountlyConfig setAutoTrackingUseShortName(boolean enable){
        this.autoTrackingUseShortName = enable;
        return this;
    }

    public CountlyConfig addCustomNetworkRequestHeaders(HashMap<String, String> customHeaderValues){
        this.customNetworkRequestHeaders = customHeaderValues;
        return this;
    }

    public CountlyConfig setPushIntentAddMetadata(boolean enable){
        pushIntentAddMetadata = enable;
        return this;
    }

    public CountlyConfig setRemoteConfigAutomaticDownload(boolean enabled, RemoteConfig.RemoteConfigCallback callback){
        enableRemoteConfigAutomaticDownload = enabled;
        remoteConfigCallback = callback;
        return this;
    }

    /**
     * Set if consent should be required
     * @param shouldRequireConsent
     * @return
     */
    public CountlyConfig setRequiresConsent(boolean shouldRequireConsent){
        this.shouldRequireConsent = shouldRequireConsent;
        return this;
    }

    /**
     * Sets which features are enabled in case consent is required
     * @param featureNames
     * @return
     */
    public CountlyConfig setConsentEnabled(String[] featureNames){
        enabledFeatureNames = featureNames;
        return this;
    }

    public CountlyConfig setHttpPostForced(boolean isForced){
        httpPostForced = isForced;
        return this;
    }
}
