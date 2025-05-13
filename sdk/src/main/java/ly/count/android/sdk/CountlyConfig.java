package ly.count.android.sdk;

import android.app.Application;
import android.content.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CountlyConfig {

    /**
     * Internal fields and fields for testing
     */
    protected CountlyStore countlyStore = null;

    /**
     * Used to pass the consent provider to all modules and features
     */
    protected ConsentProvider consentProvider = null;

    /**
     * Used to pass the storage provider to all modules and features
     */
    protected StorageProvider storageProvider = null;

    protected EventProvider eventProvider = null;

    protected EventQueueProvider eventQueueProvider = null;

    protected RequestQueueProvider requestQueueProvider = null;

    protected DeviceIdProvider deviceIdProvider = null;

    protected ViewIdProvider viewIdProvider = null;

    protected BaseInfoProvider baseInfoProvider = null;

    protected ConfigurationProvider configProvider = null;

    protected SafeIDGenerator safeViewIDGenerator = null;

    protected SafeIDGenerator safeEventIDGenerator = null;

    protected ImmediateRequestGenerator immediateRequestGenerator = null;

    protected HealthTracker healthTracker;

    protected MetricProvider metricProviderOverride = null;

    protected DeviceInfo deviceInfo = null;

    protected ModuleBase testModuleListener = null;

    protected Map<String, Object> providedUserProperties = null;

    protected Countly.LifecycleObserver lifecycleObserver = null;

    //used to deliver this object to connection queue
    //protected DeviceId deviceIdInstance = null;

    // Fields used for SDK configuration during init

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
     * unique ID for the device the app is running on; note that null in deviceID means that Countly will fall back to UUID.
     */
    protected String deviceID = null;

    /**
     * sets the limit after how many sessions, for each apps version, the automatic star rating dialog is shown.
     */
    protected int starRatingSessionLimit = 5;

    /**
     * the callback function that will be called from the automatic star rating dialog.
     */
    protected StarRatingCallback starRatingCallback = null;

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

    protected boolean enableAutomaticViewTracking = false;

    protected boolean autoTrackingUseShortName = false;

    protected Class[] automaticViewTrackingExceptions = null;

    protected Map<String, Object> globalViewSegmentation = null;

    protected Map<String, String> customNetworkRequestHeaders = null;

    protected boolean pushIntentAddMetadata = false;

    protected boolean enableRemoteConfigAutomaticDownloadTriggers = false;

    protected boolean enableAutoEnrollFlag = false;

    boolean enableRemoteConfigValueCaching = false;
    protected RemoteConfigCallback remoteConfigCallbackLegacy = null;

    protected List<RCDownloadCallback> remoteConfigGlobalCallbackList = new ArrayList<>(2);

    protected boolean shouldRequireConsent = false;

    protected boolean enableAllConsents = false;
    protected String[] enabledFeatureNames = null;

    protected boolean httpPostForced = false;

    protected boolean temporaryDeviceIdEnabled = false;

    protected String tamperingProtectionSalt = null;

    protected Integer eventQueueSizeThreshold = null;

    protected boolean trackOrientationChange = true;

    protected boolean manualSessionControlEnabled = false;

    protected boolean manualSessionControlHybridModeEnabled = false;

    protected boolean disableUpdateSessionRequests = false;

    protected boolean shouldIgnoreAppCrawlers = false;

    protected String[] appCrawlerNames = null;

    protected String[] publicKeyPinningCertificates = null;

    protected String[] certificatePinningCertificates = null;

    protected Integer sessionUpdateTimerDelay = null;

    /**
     * @deprecated This is deprecated, will be removed in the future
     */
    protected CrashFilterCallback crashFilterCallback;

    protected boolean starRatingDialogIsCancellable = false;

    protected boolean starRatingShownAutomatically = false;

    protected boolean starRatingDisableAskingForEachAppVersion = false;

    protected Application application = null;

    boolean disableLocation = false;

    String locationCountyCode = null;

    String locationCity = null;

    String locationLocation = null;

    String locationIpAddress = null;

    Map<String, String> metricOverride = null;

    int maxRequestQueueSize = 1000;

    ModuleLog.LogCallback providedLogCallback;

    String daCampaignType = null;
    String daCampaignData = null;
    Map<String, String> iaAttributionValues = null;

    boolean explicitStorageModeEnabled = false;

    boolean healthCheckEnabled = true;

    // Requests older than this value in hours would be dropped (0 means this feature is disabled)
    int dropAgeHours = 0;
    String sdkBehaviorSettings;
    boolean backOffMechanismEnabled = true;

    /**
     * THIS VARIABLE SHOULD NOT BE USED
     * IT IS ONLY FOR INTERNAL TESTING
     * BREAKING CHANGES WILL BE DONE WITHOUT WARNING
     */
    public PerformanceCounterCollector pcc;

    /**
     * Sets how many segmentation values can be recorded when recording an event or view.
     * Values exceeding this count will be ignored.
     *
     * @param maxSegmentationValues to set
     * @return Returns the same config object for convenient linking
     * @deprecated this call is deprecated, use <pre>sdkInternalLimits.setMaxSegmentationValues(int)</pre> instead
     */
    public synchronized CountlyConfig setMaxSegmentationValues(int maxSegmentationValues) {
        sdkInternalLimits.setMaxSegmentationValues(maxSegmentationValues);
        return this;
    }

    /**
     * Set the maximum amount of breadcrumbs that can be recorded.
     * After exceeding the limit, the oldest values will be removed.
     *
     * @param maxBreadcrumbCount to set
     * @return Returns the same config object for convenient linking
     * @deprecated this call is deprecated, use <pre>sdkInternalLimits.setMaxBreadcrumbCount(int)</pre> instead
     */
    public synchronized CountlyConfig setMaxBreadcrumbCount(int maxBreadcrumbCount) {
        sdkInternalLimits.setMaxBreadcrumbCount(maxBreadcrumbCount);
        return this;
    }

    public CountlyConfig() {
    }

    /**
     * @param context
     * @param appKey
     * @param serverURL
     */
    public CountlyConfig(Context context, String appKey, String serverURL) {
        setContext(context);
        setAppKey(appKey);
        setServerURL(serverURL);
    }

    public CountlyConfig(Application application, String appKey, String serverURL) {
        setAppKey(appKey);
        setServerURL(serverURL);
        setApplication(application);
    }

    /**
     * Android context.
     * Mandatory field.
     *
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setContext(Context context) {
        this.context = context;
        return this;
    }

    /**
     * URL of the Countly server to submit data to.
     * Mandatory field.
     *
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setServerURL(String serverURL) {
        this.serverURL = serverURL;
        return this;
    }

    /**
     * app key for the application being tracked; find in the Countly Dashboard under Management &gt; Applications.
     * Mandatory field.
     *
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setAppKey(String appKey) {
        this.appKey = appKey;
        return this;
    }

    /**
     * unique ID for the device the app is running on; note that null in deviceID means that Countly will fall back to UUID.
     *
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setDeviceId(String deviceID) {
        this.deviceID = deviceID;
        return this;
    }

    /**
     * sets the limit after how many sessions, for each apps version, the automatic star rating dialog is shown.
     *
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setStarRatingSessionLimit(int starRatingLimit) {
        this.starRatingSessionLimit = starRatingLimit;
        return this;
    }

    /**
     * the callback function that will be called from the automatic star rating dialog.
     *
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setStarRatingCallback(StarRatingCallback starRatingCallback) {
        this.starRatingCallback = starRatingCallback;
        return this;
    }

    /**
     * the shown title text for the star rating dialogs.
     *
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setStarRatingTextTitle(String starRatingTextTitle) {
        this.starRatingTextTitle = starRatingTextTitle;
        return this;
    }

    /**
     * the shown message text for the star rating dialogs.
     *
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setStarRatingTextMessage(String starRatingTextMessage) {
        this.starRatingTextMessage = starRatingTextMessage;
        return this;
    }

    /**
     * the shown dismiss button text for the shown star rating dialogs.
     *
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setStarRatingTextDismiss(String starRatingTextDismiss) {
        this.starRatingTextDismiss = starRatingTextDismiss;
        return this;
    }

    /**
     * Set to true of you want to enable countly internal debugging logs
     * Those logs will be printed to the console
     *
     * @param enabled Set to true of you want to enable countly internal debugging logs
     */
    public synchronized CountlyConfig setLoggingEnabled(boolean enabled) {
        this.loggingEnabled = enabled;
        return this;
    }

    /**
     * Call to enable uncaught crash reporting
     *
     * @return Returns the same config object for convenient linking
     * @deprecated this call is deprecated, please use <pre>crashes.enableCrashReporting()</pre> instead
     */
    public synchronized CountlyConfig enableCrashReporting() {
        crashes.enableCrashReporting();
        return this;
    }

    /**
     * Set if automatic view tracking should be enabled
     *
     * @param enable
     * @return Returns the same config object for convenient linking
     * @deprecated Use "enableAutomaticViewTracking()"
     */
    public synchronized CountlyConfig setViewTracking(boolean enable) {
        this.enableAutomaticViewTracking = enable;
        return this;
    }

    /**
     * Enable automatic view tracking
     *
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig enableAutomaticViewTracking() {
        this.enableAutomaticViewTracking = true;
        return this;
    }

    /**
     * Enable short names for automatic view tracking
     *
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig enableAutomaticViewShortNames() {
        this.autoTrackingUseShortName = true;
        return this;
    }

    /**
     * Set if automatic activity tracking should use short names
     *
     * @param enable set true if you want short names
     * @return Returns the same config object for convenient linking
     * @deprecated use "enableAutomaticViewShortNames()"
     */
    public synchronized CountlyConfig setAutoTrackingUseShortName(boolean enable) {
        this.autoTrackingUseShortName = enable;
        return this;
    }

    /**
     * @param segmentation segmentation values that will be added for all recorded views (manual and automatic)
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setGlobalViewSegmentation(Map<String, Object> segmentation) {
        globalViewSegmentation = segmentation;
        return this;
    }

    /**
     * @param segmentation
     * @return Returns the same config object for convenient linking
     * @deprecated please use "setGlobalViewSegmentation(Map<String, Object>)"
     */
    public synchronized CountlyConfig setAutomaticViewSegmentation(Map<String, Object> segmentation) {
        globalViewSegmentation = segmentation;
        return this;
    }

    /**
     * Set which activities should be excluded from automatic view tracking
     *
     * @param exclusions activities which should be ignored
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setAutomaticViewTrackingExclusions(Class[] exclusions) {
        if (exclusions != null) {
            for (Class exception : exclusions) {
                if (exception == null) {
                    throw new IllegalArgumentException("setAutomaticViewTrackingExclusions(...) does not accept 'null' activities");
                }
            }
        }

        automaticViewTrackingExceptions = exclusions;
        return this;
    }

    /**
     * Set which activities should be excluded from automatic view tracking
     *
     * @param exceptions activities which should be ignored
     * @return Returns the same config object for convenient linking
     * @deprecated Use "setAutomaticViewTrackingExclusions(Class[])"
     */
    public synchronized CountlyConfig setAutoTrackingExceptions(Class[] exceptions) {
        return setAutomaticViewTrackingExclusions(exceptions);
    }

    /**
     * Allows you to add custom header key/value pairs to each request
     *
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig addCustomNetworkRequestHeaders(Map<String, String> customHeaderValues) {
        this.customNetworkRequestHeaders = customHeaderValues;
        return this;
    }

    /**
     * @param enable
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setPushIntentAddMetadata(boolean enable) {
        pushIntentAddMetadata = enable;
        return this;
    }

    /**
     * If enable, will automatically download newest remote config values.
     *
     * @param enabled set true for enabling it
     * @param callback callback called after the update was done
     * @return Returns the same config object for convenient linking
     * @deprecated use "enableRemoteConfigAutomaticTriggers" and "RemoteConfigRegisterGlobalCallback" in it's place
     */
    public synchronized CountlyConfig setRemoteConfigAutomaticDownload(boolean enabled, RemoteConfigCallback callback) {
        enableRemoteConfigAutomaticDownloadTriggers = enabled;
        remoteConfigCallbackLegacy = callback;
        return this;
    }

    /**
     * Calling this would enable automatic download triggers for remote config.
     * This way the SDK would automatically initiate remote config download at specific points.
     * For example, those include: the SDK finished initializing, device ID is changed, consent is given
     *
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig enableRemoteConfigAutomaticTriggers() {
        enableRemoteConfigAutomaticDownloadTriggers = true;
        return this;
    }

    /**
     * Calling this would enable automatic enrollment of the user to the available experiments when RC is downloaded.
     *
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig enrollABOnRCDownload() {
        enableAutoEnrollFlag = true;
        return this;
    }

    /**
     * This would set a time frame in which the requests older than the given hours would be dropped while sending a request
     * Ex: Setting this to 10 would mean any requests created more than 10 hours ago would be dropped if they were in the queue
     *
     * @param dropAgeHours A positive integer. Requests older than the 'dropAgeHours' (with respect to now) would be dropped
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setRequestDropAgeHours(int dropAgeHours) {
        this.dropAgeHours = dropAgeHours;
        return this;
    }

    /**
     * If this option is not enabled then when the device ID is changed without merging, remote config values are cleared
     * If this option is enabled then the previous values are not cleared but they are marked as not from the current user.
     *
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig enableRemoteConfigValueCaching() {
        enableRemoteConfigValueCaching = true;
        return this;
    }

    /**
     * Calling this adds global listeners for remote config download callbacks.
     * Calling this multiple times would add multiple listeners
     *
     * @param callback The callback that needs to be registered
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig RemoteConfigRegisterGlobalCallback(RCDownloadCallback callback) {
        remoteConfigGlobalCallbackList.add(callback);
        return this;
    }

    /**
     * Set if consent should be required
     *
     * @param shouldRequireConsent if set to "true" then the SDK will require consent to be used. If consent for features is not given, they would not function
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setRequiresConsent(boolean shouldRequireConsent) {
        this.shouldRequireConsent = shouldRequireConsent;
        return this;
    }

    /**
     * Sets which features are enabled in case consent is required
     *
     * @param featureNames
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setConsentEnabled(String[] featureNames) {
        enabledFeatureNames = featureNames;
        return this;
    }

    /**
     * Give consent to all features
     *
     * @return
     */
    public synchronized CountlyConfig giveAllConsents() {
        enableAllConsents = true;
        return this;
    }

    /**
     * Set the override for forcing to use HTTP POST for all connections to the server
     *
     * @param isForced the flag for the new status, set "true" if you want it to be forced
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setHttpPostForced(boolean isForced) {
        httpPostForced = isForced;
        return this;
    }

    /**
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig enableTemporaryDeviceIdMode() {
        temporaryDeviceIdEnabled = true;
        return this;
    }

    /**
     * @param callback
     * @return Returns the same config object for convenient linking
     * @deprecated This call is deprecated, please use <pre>crashes.setGlobalCrashFilterCallback(GlobalCrashFilterCallback)</pre> instead
     */
    public synchronized CountlyConfig setCrashFilterCallback(CrashFilterCallback callback) {
        crashFilterCallback = callback;
        return this;
    }

    /**
     * @param salt
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setParameterTamperingProtectionSalt(String salt) {
        tamperingProtectionSalt = salt;
        return this;
    }

    /**
     * @param shouldTrackOrientation
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setTrackOrientationChanges(boolean shouldTrackOrientation) {
        trackOrientationChange = shouldTrackOrientation;
        return this;
    }

    /**
     * @return Returns the same config object for convenient linking
     * @deprecated this call is deprecated, please use <pre>crashes.enableRecordAllThreadsWithCrash()</pre> instead
     */
    public synchronized CountlyConfig setRecordAllThreadsWithCrash() {
        crashes.enableRecordAllThreadsWithCrash();
        return this;
    }

    /**
     * Set if attribution should be enabled
     *
     * @param enableAttribution set true if you want to enable it, set false if you want to disable it
     * @return Returns the same config object for convenient linking
     * @deprecated This call will not do anything anymore. Use 'setDirectAttribution' or 'setIndirectAttribution' for attribution purposes
     */
    public synchronized CountlyConfig setEnableAttribution(boolean enableAttribution) {
        return this;
    }

    /**
     * Allows public key pinning.
     * Supply list of SSL certificates (base64-encoded strings between "-----BEGIN CERTIFICATE-----" and "-----END CERTIFICATE-----" without end-of-line)
     * along with server URL starting with "https://". Countly will only accept connections to the server
     * if public key of SSL certificate provided by the server matches one provided to this method.
     *
     * @param certificates List of SSL public keys
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig enablePublicKeyPinning(String[] certificates) {
        publicKeyPinningCertificates = certificates;
        return this;
    }

    /**
     * Allows certificate pinning.
     * Supply list of SSL certificates (base64-encoded strings between "-----BEGIN CERTIFICATE-----" and "-----END CERTIFICATE-----" without end-of-line)
     * along with server URL starting with "https://". Countly will only accept connections to the server
     * if certificate provided by the server matches one provided to this method.
     *
     * @param certificates List of SSL certificates
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig enableCertificatePinning(String[] certificates) {
        certificatePinningCertificates = certificates;
        return this;
    }

    /**
     * Set if Countly SDK should ignore app crawlers
     *
     * @param shouldIgnore if crawlers should be ignored
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setShouldIgnoreAppCrawlers(boolean shouldIgnore) {
        shouldIgnoreAppCrawlers = shouldIgnore;
        return this;
    }

    /**
     * List of app crawler names that should be ignored
     *
     * @param appCrawlerNames the names to be ignored
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setAppCrawlerNames(String[] appCrawlerNames) {
        this.appCrawlerNames = appCrawlerNames;
        return this;
    }

    /**
     * Set the threshold for event grouping. Event count that is bellow the
     * threshold will be sent on update ticks.
     *
     * @param threshold
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setEventQueueSizeToSend(int threshold) {
        eventQueueSizeThreshold = threshold;
        return this;
    }

    public synchronized CountlyConfig enableManualSessionControl() {
        manualSessionControlEnabled = true;
        return this;
    }

    public synchronized CountlyConfig enableManualSessionControlHybridMode() {
        manualSessionControlHybridModeEnabled = true;
        return this;
    }

    /**
     * Set custom crash segmentation which will be added to all recorded crashes
     *
     * @param crashSegment segmentation information. Accepted values are "Integer", "String", "Double", "Boolean"
     * @return Returns the same config object for convenient linking
     * @deprecated this call is deprecated, please use <pre>crashes.setCustomCrashSegmentation(Map<String, Object>)</pre> instead
     */
    public synchronized CountlyConfig setCustomCrashSegment(Map<String, Object> crashSegment) {
        crashes.setCustomCrashSegmentation(crashSegment);
        return this;
    }

    /**
     * For use during testing
     *
     * @param checkForDumps whether to check for native crash dumps
     * @return Returns the same config object for convenient linking
     * @deprecated this call is deprecated and will always be enabled
     */
    protected synchronized CountlyConfig checkForNativeCrashDumps(boolean checkForDumps) {
        return this;
    }

    /**
     * Sets the interval for the automatic session update calls
     * min value 1 (1 second)
     *
     * @param delay in seconds
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setUpdateSessionTimerDelay(int delay) {
        sessionUpdateTimerDelay = delay;
        return this;
    }

    /**
     * For use during testing
     *
     * @param store
     * @return Returns the same config object for convenient linking
     */
    protected synchronized CountlyConfig setCountlyStore(CountlyStore store) {
        countlyStore = store;
        return this;
    }

    /**
     * Disable periodic session time updates.
     * By default, Countly will send a request to the server each 60 seconds with a small update
     * containing session duration time. This method allows you to disable such behavior.
     * Note that event updates will still be sent every 100 events or 60 seconds after event recording.
     *
     * @param disable whether or not to disable session time updates
     * @return Returns the same config object for convenient linking
     */
    protected synchronized CountlyConfig setDisableUpdateSessionRequests(boolean disable) {
        disableUpdateSessionRequests = disable;
        return this;
    }

    /**
     * Set if the star rating dialog is cancellable
     *
     * @param isCancellable set this true if it should be cancellable
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setIfStarRatingDialogIsCancellable(boolean isCancellable) {
        starRatingDialogIsCancellable = isCancellable;
        return this;
    }

    /**
     * Set if the star rating should be shown automatically
     *
     * @param isShownAutomatically set it true if you want to show the app star rating dialog automatically for each new version after the specified session amount
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setIfStarRatingShownAutomatically(boolean isShownAutomatically) {
        starRatingShownAutomatically = isShownAutomatically;
        return this;
    }

    /**
     * Set if the star rating is shown only once per app lifetime
     *
     * @param disableAsking set true if you want to disable asking the app rating for each new app version (show it only once per apps lifetime)
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setStarRatingDisableAskingForEachAppVersion(boolean disableAsking) {
        starRatingDisableAskingForEachAppVersion = disableAsking;
        return this;
    }

    /**
     * Set the link to the application class
     *
     * @param application
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setApplication(Application application) {
        this.application = application;
        return this;
    }

    /**
     * Enable the recording of the app start time
     *
     * @param recordAppStartTime set true if you want to enable the recording of the app start time
     * @return Returns the same config object for convenient linking
     * @deprecated this call is deprecated, use <pre>apm.enableAppStartTracking()</pre> instead
     */
    public synchronized CountlyConfig setRecordAppStartTime(boolean recordAppStartTime) {
        apm.trackAppStartTime = recordAppStartTime;
        return this;
    }

    /**
     * Disable location tracking
     *
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setDisableLocation() {
        disableLocation = true;
        return this;
    }

    /**
     * Set location parameters.
     * This will be ignored if set together with `setDisableLocation`
     *
     * @param country_code ISO Country code for the user's country
     * @param city Name of the user's city
     * @param gpsCoordinates comma separate lat and lng values. For example, "56.42345,123.45325"
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setLocation(String country_code, String city, String gpsCoordinates, String ipAddress) {
        locationCountyCode = country_code;
        locationCity = city;
        locationLocation = gpsCoordinates;
        locationIpAddress = ipAddress;
        return this;
    }

    /**
     * Set the metrics you want to override or additional custom metrics you want to provide
     *
     * @param providedMetricOverride
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setMetricOverride(Map<String, String> providedMetricOverride) {
        metricOverride = providedMetricOverride;
        return this;
    }

    /**
     * Override the app start timestamp in case you have a more precise way to measure it
     *
     * @param appStartTimestampOverride The timestamp to use as the app start timestamp
     * @return Returns the same config object for convenient linking
     * @deprecated this call is deprecated, use <pre>apm.setAppStartTimestampOverride()</pre> instead
     */
    public synchronized CountlyConfig setAppStartTimestampOverride(long appStartTimestampOverride) {
        apm.setAppStartTimestampOverride(appStartTimestampOverride);
        return this;
    }

    /**
     * Set to manually trigger the moment when the app has finished loading
     *
     * @return Returns the same config object for convenient linking
     * @deprecated this call is deprecated, use <pre>apm.enableManualAppLoadedTrigger()</pre> instead
     */
    public synchronized CountlyConfig enableManualAppLoadedTrigger() {
        apm.enableManualAppLoadedTrigger();
        return this;
    }

    /**
     * Set this in case you want to control these triggers manually
     *
     * @return Returns the same config object for convenient linking
     * @deprecated this call is deprecated and will be removed in the future
     */
    public synchronized CountlyConfig enableManualForegroundBackgroundTriggerAPM() {
        apm.manualForegroundBackgroundTrigger = true;
        return this;
    }

    /**
     * Add a log callback that will duplicate all logs done by the SDK.
     * For each message you will receive the message string and it's targeted log level.
     *
     * @param logCallback
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setLogListener(ModuleLog.LogCallback logCallback) {
        providedLogCallback = logCallback;
        return this;
    }

    /**
     * Set's the new maximum size for the request queue.
     *
     * @param newMaxSize Minimum value is "1".
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setMaxRequestQueueSize(int newMaxSize) {
        maxRequestQueueSize = newMaxSize;
        return this;
    }

    /**
     * Report direct user attribution
     *
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setDirectAttribution(String campaignType, String campaignData) {
        daCampaignType = campaignType;
        daCampaignData = campaignData;
        return this;
    }

    /**
     * Report indirect user attribution
     *
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setIndirectAttribution(Map<String, String> attributionValues) {
        iaAttributionValues = attributionValues;
        return this;
    }

    /**
     * Used to provide user properties that would be sent as soon as possible
     *
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setUserProperties(Map<String, Object> userProperties) {
        providedUserProperties = userProperties;
        return this;
    }

    /**
     * If this mode is enabled then the SDK not write the request and event queues to disk
     * until the explicit write signal is given.
     *
     * The explicit write signal is given with:
     * 'Countly.sharedInstance().requestQueue().esWriteCachesToPersistence();'
     *
     * If not used properly, this mode will lead to data loss or data duplication.
     *
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig enableExplicitStorageMode() {
        explicitStorageModeEnabled = true;
        return this;
    }

    /**
     * This is an experimental feature and it can have breaking changes
     *
     * With this mode enable, the SDK will acquire additional configuration from it's Countly server
     *
     * @return Returns the same config object for convenient linking
     * @apiNote This is an EXPERIMENTAL feature, and it can have breaking changes
     * @deprecated and will do nothing
     */
    public synchronized CountlyConfig enableServerConfiguration() {
        return this;
    }

    protected synchronized CountlyConfig disableHealthCheck() {
        healthCheckEnabled = false;
        return this;
    }

    /**
     * Set the server configuration to be set while initializing the SDK
     *
     * @param sdkBehaviorSettings The server configuration to be set
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setSDKBehaviorSettings(String sdkBehaviorSettings) {
        this.sdkBehaviorSettings = sdkBehaviorSettings;
        return this;
    }

    /**
     * Disable the back off mechanism
     *
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig disableBackOffMechanism() {
        this.backOffMechanismEnabled = false;
        return this;
    }

    /**
     * APM configuration interface to be used with CountlyConfig
     */
    public final ConfigApm apm = new ConfigApm();

    /**
     * SDK Internal Limits configuration interface to be used with CountlyConfig
     */
    public final ConfigSdkInternalLimits sdkInternalLimits = new ConfigSdkInternalLimits();

    /**
     * Crash Reporting configuration interface to be used with CountlyConfig
     */
    public final ConfigCrashes crashes = new ConfigCrashes();

    /**
     * Content configuration interface to be used with CountlyConfig
     *
     * @apiNote This is an EXPERIMENTAL feature, and it can have breaking changes
     */
    public final ConfigContent content = new ConfigContent();

    /**
     * Experimental configuration interface to be used with CountlyConfig
     *
     * @apiNote This is an EXPERIMENTAL feature, and it can have breaking changes
     */
    public final ConfigExperimental experimental = new ConfigExperimental();
}
