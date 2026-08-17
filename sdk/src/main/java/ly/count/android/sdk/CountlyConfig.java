package ly.count.android.sdk;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLSocketFactory;

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

    // Storage namespace of the instance that last init()ed with this config; null until one does.
    String initialisedForNamespace = null;
    // What these fields held before any init() touched them. See DerivedFieldSnapshot.
    DerivedFieldSnapshot derivedFieldSnapshot = null;

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

    // Optional, advisory instance name. This field does NOT by itself create or namespace an
    // instance - the effective instance name is the one passed to Countly.instance(name). At init
    // this value is only cross-checked against that registry name and a warning is logged on a
    // mismatch (or when it is set on the default instance, where it is ignored).
    protected String instanceName = null;

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

    protected boolean disableSDKLoggingInProduction = false;

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

    protected SSLSocketFactory customSSLSocketFactory = null;

    protected Integer sessionUpdateTimerDelay = null;

    /**
     * @deprecated This is deprecated, will be removed in the future
     */
    protected CrashFilterCallback crashFilterCallback;

    protected boolean starRatingDialogIsCancellable = false;

    protected boolean starRatingShownAutomatically = false;

    protected boolean starRatingDisableAskingForEachAppVersion = false;

    protected Application application = null;

    protected Activity initialActivity = null;

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
    boolean sdkBehaviorSettingsRequestsDisabled = false;
    int requestTimeoutDuration = 30; // in seconds

    // If set to true, immediate requests will use serial AsyncTask executor instead of the thread pool
    boolean useSerialExecutor = false;
    WebViewDisplayOption webViewDisplayOption = WebViewDisplayOption.IMMERSIVE;
    boolean webViewEnabled = true;

    // If set to true, request queue cleaner will remove all overflow at once instead of gradually (loop limited) removing
    boolean disableGradualRequestCleaner = false;

    // If set to true, the SDK will not store the default push consent state on initialization for not requiring consent
    boolean disableStoringDefaultPushConsent = false;

    // If set to true, the SDK will not restart manual views while switching between foreground and background
    boolean disableViewRestartForManualRecording = false;

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
     * Optionally records the intended instance name on this config. The effective name of an instance
     * is the one you pass to {@code Countly.instance(name)} - that is what creates the instance and
     * isolates its storage (request queue, event queue, device id, configuration). This setter does
     * not create or namespace anything on its own; at init the value is only cross-checked against
     * the name the instance was obtained with, and a warning is logged if they differ (or if it is
     * set on the default instance obtained via {@code sharedInstance()}, where it is ignored). To
     * create an isolated named instance, use {@code Countly.instance(name).init(config)} - passing
     * your app key as the name is the natural choice for one instance per Countly application.
     *
     * @param instanceName the name to record on the config (should match the name passed to instance())
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setInstanceName(String instanceName) {
        this.instanceName = instanceName;
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
     * Call this if you want the SDK to keep its console (logcat) logging disabled
     * when the host app is built as a production (non-debuggable) build, even if
     * logging was enabled through {@link #setLoggingEnabled(boolean)} or through the
     * runtime call {@link Countly#setLoggingEnabled(boolean)}.
     * A production build is detected as one that is not flagged debuggable in its
     * application info. This only affects console output. A log listener provided
     * through {@link #setLogListener(ModuleLog.LogCallback)} keeps receiving logs.
     */
    public synchronized CountlyConfig disableSDKLoggingInProduction() {
        this.disableSDKLoggingInProduction = true;
        return this;
    }

    /**
     * Set a custom metric provider to override default device metrics.
     * Only the methods you override will replace the SDK defaults.
     * Methods that return null will fall back to the SDK's built-in values.
     *
     * @param metricProvider Your custom MetricProvider implementation
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setMetricProvider(MetricProvider metricProvider) {
        this.metricProviderOverride = metricProvider;
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
     * <p>
     * The SDK copies these entries when it initialises, so changing the map you passed in afterwards has
     * no effect on the requests it sends. To change a header while the SDK is running - rotating an
     * authorization token, for example - call
     * {@code Countly.sharedInstance().requestQueue().addCustomNetworkRequestHeaders(Map)}.
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
     * Provide a custom SSLSocketFactory that Countly uses for all of its HTTPS requests
     * (session, event, remote-config, feedback/rating/content availability, health-check and
     * preflight requests).
     * <p>
     * Use this to route Countly's network traffic through your own TLS provider — for example a
     * FIPS 140-3 validated cryptographic module — or to enforce a specific TLS protocol version
     * or cipher suite. Protocol and cipher-suite restrictions must be applied inside the supplied
     * factory (for example by wrapping it and calling {@code setEnabledProtocols} /
     * {@code setEnabledCipherSuites} on each created socket, or through {@code SSLParameters});
     * Countly applies the factory as it is.
     * <p>
     * Notes:
     * <ul>
     *   <li>Applies only to "https://" server URLs. It has no effect on a plain "http://" server URL.</li>
     *   <li>Takes precedence over {@link #enablePublicKeyPinning(String[])} and
     *   {@link #enableCertificatePinning(String[])}. When both are provided, this factory is used and
     *   the built-in pinning trust manager is not applied; add pinning to your own factory if you need it.</li>
     *   <li>Does not apply to WebView-rendered content, feedback and rating widgets (the Android WebView
     *   uses its own network stack) nor to push notification image downloads.</li>
     * </ul>
     *
     * @param sslSocketFactory the factory to use; a null value leaves the default behavior unchanged
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setCustomSSLSocketFactory(SSLSocketFactory sslSocketFactory) {
        customSSLSocketFactory = sslSocketFactory;
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
     * Set the initial activity reference for SDK initialization.
     * This is needed for frameworks like Flutter and React Native where the host activity
     * is already started before the SDK registers its lifecycle callbacks.
     * Setting this ensures that content overlays and feedback widgets can display correctly.
     *
     * @param activity the current foreground activity
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setInitialActivity(Activity activity) {
        this.initialActivity = activity;
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
    public synchronized CountlyConfig disableBackoffMechanism() {
        this.backOffMechanismEnabled = false;
        return this;
    }

    /**
     * Disable the SDK behavior settings update calls to the server
     *
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig disableSDKBehaviorSettingsUpdates() {
        this.sdkBehaviorSettingsRequestsDisabled = true;
        return this;
    }

    /**
     * Set the request timeout duration in seconds
     * Minimum value is "1" second
     * Default value is "30" seconds
     *
     * @param requestTimeoutDuration The request timeout duration in seconds
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setRequestTimeoutDuration(int requestTimeoutDuration) {
        int tempRequestTimeoutDuration = requestTimeoutDuration;
        if (tempRequestTimeoutDuration <= 0) {
            tempRequestTimeoutDuration = 1;
        }
        this.requestTimeoutDuration = tempRequestTimeoutDuration;
        return this;
    }

    /**
     * Set the webview display option for Content and Feedback Widgets
     *
     * @param displayOption IMMERSIVE for full screen with hidden system UI, or
     * SAFE_AREA to use app usable area and not overlap system UI
     * @return config content to chain calls
     */
    public synchronized CountlyConfig setWebviewDisplayOption(WebViewDisplayOption displayOption) {
        if (displayOption != null) {
            this.webViewDisplayOption = displayOption;
        }
        return this;
    }

    /**
     * Disable all WebView-based UI in the SDK. When called, no WebView is ever created or shown
     * for any feature. This covers the Content feature overlay, Feedback Widgets (surveys, NPS,
     * and rating widgets), and the rating popup. WebView UI is enabled by default.
     *
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig disableWebView() {
        this.webViewEnabled = false;
        return this;
    }

    /**
     * Applies the SDK's recommended security hardening in a single call: disables all WebView-based
     * UI ({@link #disableWebView()}) and keeps console logging off in production, non-debuggable
     * builds ({@link #disableSDKLoggingInProduction()}). Intended for apps that do not use the SDK's
     * Content, Feedback, or rating WebView UI. Apps that do use that UI should set the individual
     * options they need instead. (The WebView lockdown and dangerous-scheme blocking are the
     * always-on baseline that protects any WebView the SDK does show; this call does not re-enable
     * the WebView it just disabled.) For push notifications, see
     * {@code CountlyConfigPush.enableRecommendedSecuritySettings(List)}.
     *
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig enableRecommendedSecuritySettings() {
        disableWebView();
        disableSDKLoggingInProduction();
        return this;
    }

    /**
     * To select the legacy AsyncTask.execute (serial executor) or
     * instead executeOnExecutor(THREAD_POOL_EXECUTOR)
     * Default is false and the SDK will use the thread pool executor.
     *
     * @param useSerial set to true to use serial executor
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig setUseSerialExecutor(boolean useSerial) {
        this.useSerialExecutor = useSerial;
        return this;
    }

    /**
     * Disable the gradual request cleaner. By default when the request queue exceeds the configured
     * maximum size, only a limited number of the oldest requests are removed per cleanup cycle
     * (capped by an internal loop limit of 100) to gradually shrink the queue. Calling this method changes
     * the behavior so that whenever the queue exceeds the maximum size, all overflowing requests
     * (plus one extra slot for the new request) are removed in a single operation.
     *
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig disableGradualRequestCleaner() {
        this.disableGradualRequestCleaner = true;
        return this;
    }

    /**
     * Disable storing the default push consent on initialization.
     * By default, if consent is required and push consent is not set,
     * the SDK was storing push consent as false on initialization.
     * Now, if consent is not required, the SDK will store push consent as true on initialization.
     *
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig disableStoringDefaultPushConsent() {
        this.disableStoringDefaultPushConsent = true;
        return this;
    }

    /**
     * Disable view restart when manual view recording is done.
     * By default, if automatic view tracking is not enabled and a manual view is recorded,
     * the SDK was restarting the views to properly track the view duration in bg/fg transitions.
     * Now, with this option enabled, the SDK will not restart the views on manual view recording.
     *
     * @return Returns the same config object for convenient linking
     */
    public synchronized CountlyConfig disableViewRestartForManualRecording() {
        this.disableViewRestartForManualRecording = true;
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

    /**
     * What a config's derived fields held before any {@code init()} touched them.
     * <p>
     * A CountlyConfig is meant to be used by exactly one Countly instance, but nothing stops a developer
     * from passing one config to two instances - and {@code init()} plus the module constructors write
     * their results back onto the config (the store, the queues, every {@code *Provider} back-reference,
     * the DeviceInfo, and the temporary-device-id sentinel). Reusing such a config would silently hand
     * the next instance the previous instance's objects: its store and request queue, its consent and
     * device-id providers, even its app key and server URL through {@code baseInfoProvider}.
     * <p>
     * So the first {@code init()} to use a config snapshots these fields, and every later {@code init()}
     * restores the snapshot before it starts. Each init then sees exactly what the developer configured,
     * whether it is a second instance or the same instance re-initialised after {@code halt()}.
     * <p>
     * The same applies to the values the server behaviour settings resolve to: {@code ModuleConfiguration}
     * writes those back onto the config from its own constructor, reading the STORED settings, so no
     * server round trip is needed for one instance's settings to become the next instance's configuration.
     * They are snapshotted as well, because inheriting a stored "consent not required" would silently
     * switch consent gating off for an instance whose developer required it.
     * <p>
     * Deliberately NOT snapshotted: {@link CountlyConfig#initialActivity} (init clears it on purpose - a
     * later init must not re-seed a possibly destroyed activity) and the idempotent value normalisations
     * (server-URL trailing slash and the queue-size clamps), which yield the same result when re-applied.
     * <p>
     * Note the residual limitation: {@code init()} also aliases the config into the instance's
     * {@code config_}, so two instances handed one config keep reading the same object after init.
     * Restoring at init fixes what an instance STARTS with, not later cross-writes. One config per
     * instance remains the rule, which is why init warns loudly when it sees reuse.
     */
    static final class DerivedFieldSnapshot {
        private final CountlyStore countlyStore;
        private final StorageProvider storageProvider;
        private final EventQueueProvider eventQueueProvider;
        private final RequestQueueProvider requestQueueProvider;
        private final EventProvider eventProvider;
        private final ConsentProvider consentProvider;
        private final DeviceIdProvider deviceIdProvider;
        private final BaseInfoProvider baseInfoProvider;
        private final ViewIdProvider viewIdProvider;
        private final ConfigurationProvider configProvider;
        private final HealthTracker healthTracker;
        private final DeviceInfo deviceInfo;
        private final ImmediateRequestGenerator immediateRequestGenerator;
        private final Countly.LifecycleObserver lifecycleObserver;
        private final SafeIDGenerator safeViewIDGenerator;
        private final SafeIDGenerator safeEventIDGenerator;
        //What the developer had set on the config before any init touched it, and what the SDK left on it at
        //the end of the last init. A value is only reset when it still holds what the SDK left - see
        //restoreValuesOnto. The values themselves are the ones the SDK writes back: the temporary-device-id
        //sentinel and the settings the server behaviour settings resolve.
        private final Object[] originalValues;
        private Object[] appliedValues;

        DerivedFieldSnapshot(@NonNull CountlyConfig config) {
            countlyStore = config.countlyStore;
            storageProvider = config.storageProvider;
            eventQueueProvider = config.eventQueueProvider;
            requestQueueProvider = config.requestQueueProvider;
            eventProvider = config.eventProvider;
            consentProvider = config.consentProvider;
            deviceIdProvider = config.deviceIdProvider;
            baseInfoProvider = config.baseInfoProvider;
            viewIdProvider = config.viewIdProvider;
            configProvider = config.configProvider;
            healthTracker = config.healthTracker;
            deviceInfo = config.deviceInfo;
            immediateRequestGenerator = config.immediateRequestGenerator;
            lifecycleObserver = config.lifecycleObserver;
            safeViewIDGenerator = config.safeViewIDGenerator;
            safeEventIDGenerator = config.safeEventIDGenerator;
            originalValues = readValues(config);
        }

        /**
         * The values the SDK itself writes back onto the config, in a fixed order. Held as an array rather
         * than as three parallel copies of every field, so that "what the developer set", "what the SDK last
         * left here" and "what is here now" can be compared position by position.
         * <p>
         * ADDING A VALUE THE SDK WRITES ONTO THE CONFIG MEANS ADDING IT TO BOTH readValues AND writeValue.
         */
        private static Object[] readValues(@NonNull CountlyConfig config) {
            return new Object[] {
                config.deviceID,
                config.loggingEnabled,
                config.shouldRequireConsent,
                config.eventQueueSizeThreshold,
                config.sessionUpdateTimerDelay,
                config.maxRequestQueueSize,
                config.dropAgeHours,
                config.content.zoneTimerInterval,
            };
        }

        private static void writeValue(@NonNull CountlyConfig config, int index, Object value) {
            switch (index) {
                case 0: config.deviceID = (String) value; break;
                case 1: config.loggingEnabled = (Boolean) value; break;
                case 2: config.shouldRequireConsent = (Boolean) value; break;
                case 3: config.eventQueueSizeThreshold = (Integer) value; break;
                case 4: config.sessionUpdateTimerDelay = (Integer) value; break;
                case 5: config.maxRequestQueueSize = (Integer) value; break;
                case 6: config.dropAgeHours = (Integer) value; break;
                case 7: config.content.zoneTimerInterval = (Integer) value; break;
                default: break;
            }
        }

        /**
         * Records what the config holds now, at the end of a successful init, as "what the SDK left here".
         * A later init restores a value only when it is still untouched since this point - so the SDK's own
         * write-backs (the temporary-device-id sentinel, the server-resolved settings) are undone, while a
         * value the developer deliberately changed between the two inits is honoured.
         */
        void captureApplied(@NonNull CountlyConfig config) {
            appliedValues = readValues(config);
        }

        private void restoreValuesOnto(@NonNull CountlyConfig config) {
            if (appliedValues == null) {
                //no init has completed with this config yet, so nothing has been written back to undo
                return;
            }
            Object[] current = readValues(config);
            for (int i = 0; i < current.length; i++) {
                if (equal(current[i], appliedValues[i])) {
                    writeValue(config, i, originalValues[i]);
                }
            }
        }

        private static boolean equal(Object a, Object b) {
            return a == null ? b == null : a.equals(b);
        }

        void restoreOnto(@NonNull CountlyConfig config) {
            restoreValuesOnto(config);
            config.countlyStore = countlyStore;
            config.storageProvider = storageProvider;
            config.eventQueueProvider = eventQueueProvider;
            config.requestQueueProvider = requestQueueProvider;
            config.eventProvider = eventProvider;
            config.consentProvider = consentProvider;
            config.deviceIdProvider = deviceIdProvider;
            config.baseInfoProvider = baseInfoProvider;
            config.viewIdProvider = viewIdProvider;
            config.configProvider = configProvider;
            config.healthTracker = healthTracker;
            config.deviceInfo = deviceInfo;
            config.immediateRequestGenerator = immediateRequestGenerator;
            config.lifecycleObserver = lifecycleObserver;
            config.safeViewIDGenerator = safeViewIDGenerator;
            config.safeEventIDGenerator = safeEventIDGenerator;
        }

    }
}
