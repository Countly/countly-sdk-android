package ly.count.android.sdk;

import android.content.Context;

import java.util.Map;
import java.util.regex.Pattern;

public class CountlyConfig {

    /**
     * Internal fields for testing
     */

    protected CountlyStore countlyStore = null;

    protected boolean checkForNativeCrashDumps = true;

    /**
     * Android context.
     * Mandatory field.
     */
    protected Context context = null;

    /**
     * URL of the Countly server to submit data to.
     * Mandatory field.
     */
    protected String serverURL = null;

    /**
     * app key for the application being tracked; find in the Countly Dashboard under Management &gt; Applications.
     * Mandatory field.
     */
    protected String appKey = null;

    /**
     * unique ID for the device the app is running on; note that null in deviceID means that Countly will fall back to OpenUDID, then, if it's not available, to Google Advertising ID.
     */
    protected String deviceID = null;

    /**
     * enum value specifying which device ID generation strategy Countly should use: OpenUDID or Google Advertising ID.
     */
    protected DeviceId.Type idMode = null;

    /**
     * sets the limit after how many sessions, for each apps version, the automatic star rating dialog is shown.
     */
    protected int starRatingLimit = 5;

    /**
     * the callback function that will be called from the automatic star rating dialog.
     */
    protected CountlyStarRating.RatingCallback starRatingCallback = null;

    /**
     * the shown title text for the star rating dialogs.
     */
    protected String starRatingTextTitle = null;

    /**
     * the shown message text for the star rating dialogs.
     */
    protected String starRatingTextMessage = null;

    /**
     * the shown dismiss button text for the shown star rating dialogs.
     */
    protected String starRatingTextDismiss = null;

    protected boolean loggingEnabled = false;

    protected boolean enableUnhandledCrashReporting = false;

    protected boolean enableViewTracking = false;

    protected boolean autoTrackingUseShortName = false;

    protected Class[] autoTrackingExceptions = null;

    protected Map<String, Object> automaticViewSegmentation = null;

    protected Map<String, String> customNetworkRequestHeaders = null;

    protected boolean pushIntentAddMetadata = false;

    protected boolean enableRemoteConfigAutomaticDownload = false;
    protected RemoteConfig.RemoteConfigCallback remoteConfigCallback = null;

    protected boolean shouldRequireConsent = false;
    protected String[] enabledFeatureNames = null;

    protected boolean httpPostForced = false;

    protected boolean temporaryDeviceIdEnabled = false;

    protected Pattern[] crashRegexFilters = null;

    protected String tamperingProtectionSalt = null;

    protected Integer eventQueueSizeThreshold = null;

    protected boolean trackOrientationChange = false;

    boolean recordAllThreadsWithCrash = false;

    protected boolean shouldIgnoreAppCrawlers = false;

    protected String[] appCrawlerNames = null;

    public CountlyConfig() {
    }

    public CountlyConfig(Context context, String appKey, String serverURL) {
        setContext(context);
        setAppKey(appKey);
        setServerURL(serverURL);
    }

    /**
     * Android context.
     * Mandatory field.
     */
    public CountlyConfig setContext(Context context) {
        this.context = context;
        return this;
    }

    /**
     * URL of the Countly server to submit data to.
     * Mandatory field.
     */
    public CountlyConfig setServerURL(String serverURL) {
        this.serverURL = serverURL;
        return this;
    }

    /**
     * app key for the application being tracked; find in the Countly Dashboard under Management &gt; Applications.
     * Mandatory field.
     */
    public CountlyConfig setAppKey(String appKey) {
        this.appKey = appKey;
        return this;
    }

    /**
     * unique ID for the device the app is running on; note that null in deviceID means that Countly will fall back to OpenUDID, then, if it's not available, to Google Advertising ID.
     */
    public CountlyConfig setDeviceId(String deviceID) {
        this.deviceID = deviceID;
        return this;
    }

    /**
     * enum value specifying which device ID generation strategy Countly should use: OpenUDID or Google Advertising ID.
     */
    public CountlyConfig setIdMode(DeviceId.Type idMode) {
        this.idMode = idMode;
        return this;
    }

    /**
     * sets the limit after how many sessions, for each apps version, the automatic star rating dialog is shown.
     */
    public CountlyConfig setStarRatingLimit(int starRatingLimit) {
        this.starRatingLimit = starRatingLimit;
        return this;
    }

    /**
     * the callback function that will be called from the automatic star rating dialog.
     */
    public CountlyConfig setStarRatingCallback(CountlyStarRating.RatingCallback starRatingCallback) {
        this.starRatingCallback = starRatingCallback;
        return this;
    }

    /**
     * the shown title text for the star rating dialogs.
     */
    public CountlyConfig setStarRatingTextTitle(String starRatingTextTitle) {
        this.starRatingTextTitle = starRatingTextTitle;
        return this;
    }

    /**
     * the shown message text for the star rating dialogs.
     */
    public CountlyConfig setStarRatingTextMessage(String starRatingTextMessage) {
        this.starRatingTextMessage = starRatingTextMessage;
        return this;
    }

    /**
     * the shown dismiss button text for the shown star rating dialogs.
     */
    public CountlyConfig setStarRatingTextDismiss(String starRatingTextDismiss) {
        this.starRatingTextDismiss = starRatingTextDismiss;
        return this;
    }

    /**
     * Set to true of you want to enable countly internal debugging logs
     *
     * @param enabled
     */
    public CountlyConfig setLoggingEnabled(boolean enabled) {
        this.loggingEnabled = enabled;
        return this;
    }

    public CountlyConfig enableCrashReporting() {
        this.enableUnhandledCrashReporting = true;
        return this;
    }

    public CountlyConfig setViewTracking(boolean enable) {
        this.enableViewTracking = enable;
        return this;
    }

    public CountlyConfig setAutoTrackingUseShortName(boolean enable) {
        this.autoTrackingUseShortName = enable;
        return this;
    }

    public CountlyConfig setAutomaticViewSegmentation(Map<String, Object> segmentation) {
        automaticViewSegmentation = segmentation;
        return this;
    }

    /**
     * Set which activities should be excluded from automatic view tracking
     *
     * @param exceptions activities which should be ignored
     * @return
     */
    public CountlyConfig setAutoTrackingExceptions(Class[] exceptions) {
        if (exceptions != null) {
            for (int a = 0; a < exceptions.length; a++) {
                if (exceptions[a] == null) {
                    throw new IllegalArgumentException("setAutoTrackingExceptions() does not accept 'null' activities");
                }
            }
        }

        autoTrackingExceptions = exceptions;
        return this;
    }

    public CountlyConfig addCustomNetworkRequestHeaders(Map<String, String> customHeaderValues) {
        this.customNetworkRequestHeaders = customHeaderValues;
        return this;
    }

    public CountlyConfig setPushIntentAddMetadata(boolean enable) {
        pushIntentAddMetadata = enable;
        return this;
    }

    public CountlyConfig setRemoteConfigAutomaticDownload(boolean enabled, RemoteConfig.RemoteConfigCallback callback) {
        enableRemoteConfigAutomaticDownload = enabled;
        remoteConfigCallback = callback;
        return this;
    }

    /**
     * Set if consent should be required
     *
     * @param shouldRequireConsent
     * @return
     */
    public CountlyConfig setRequiresConsent(boolean shouldRequireConsent) {
        this.shouldRequireConsent = shouldRequireConsent;
        return this;
    }

    /**
     * Sets which features are enabled in case consent is required
     *
     * @param featureNames
     * @return
     */
    public CountlyConfig setConsentEnabled(String[] featureNames) {
        enabledFeatureNames = featureNames;
        return this;
    }

    public CountlyConfig setHttpPostForced(boolean isForced) {
        httpPostForced = isForced;
        return this;
    }

    public CountlyConfig enableTemporaryDeviceIdMode() {
        temporaryDeviceIdEnabled = true;
        return this;
    }

    public CountlyConfig setCrashFilters(Pattern[] regexFilters) {
        crashRegexFilters = regexFilters;
        return this;
    }

    public CountlyConfig setParameterTamperingProtectionSalt(String salt) {
        tamperingProtectionSalt = salt;
        return this;
    }

    public CountlyConfig setTrackOrientationChanges(boolean shouldTrackOrientation) {
        trackOrientationChange = shouldTrackOrientation;
        return this;
    }

    public CountlyConfig setRecordAllThreadsWithCrash(){
        recordAllThreadsWithCrash = true;
        return this;
    }

    public CountlyConfig setShouldIgnoreAppCrawlers(boolean shouldIgnore){
        shouldIgnoreAppCrawlers = shouldIgnore;
        return this;
    }

    public CountlyConfig setAppCrawlerNames(String[] appCrawlerNames){
        this.appCrawlerNames = appCrawlerNames;
        return this;
    }

    public CountlyConfig setEventQueueSizeToSend(int threshold) {
        eventQueueSizeThreshold = threshold;
        return this;
    }


    protected CountlyConfig checkForNativeCrashDumps(boolean checkForDumps) {
        checkForNativeCrashDumps = checkForDumps;
        return this;
    }

    protected CountlyConfig setCountlyStore(CountlyStore store) {
        countlyStore = store;
        return this;
    }
}
