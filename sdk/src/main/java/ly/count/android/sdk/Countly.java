/*
Copyright (c) 2012, 2013, 2014 Countly

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/
package ly.count.android.sdk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ly.count.android.sdk.CountlyStarRating.STAR_RATING_EVENT_KEY;

/**
 * This class is the public API for the Countly Android SDK.
 * Get more details <a href="https://github.com/Countly/countly-sdk-android">here</a>.
 */
@SuppressWarnings("JavadocReference")
public class Countly {

    /**
     * Current version of the Count.ly Android SDK as a displayable string.
     */
    public static final String COUNTLY_SDK_VERSION_STRING = "19.09";
    /**
     * Used as request meta data on every request
     */
    protected static final String COUNTLY_SDK_NAME = "java-native-android";
    /**
     * Default string used in the begin session metrics if the
     * app version cannot be found.
     */
    protected static final String DEFAULT_APP_VERSION = "1.0";
    /**
     * Tag used in all logging in the Count.ly SDK.
     */
    public static final String TAG = "Countly";

    /**
     * Broadcast sent when consent set is changed
     */
    public static final String CONSENT_BROADCAST = "ly.count.android.sdk.Countly.CONSENT_BROADCAST";

    /**
     * Determines how many custom events can be queued locally before
     * an attempt is made to submit them to a Count.ly server.
     */
    private static int EVENT_QUEUE_SIZE_THRESHOLD = 10;
    /**
     * How often onTimer() is called.
     */
    private static final long TIMER_DELAY_IN_SECONDS = 60;

    protected static List<String> publicKeyPinCertificates;
    protected static List<String> certificatePinCertificates;

    protected static final Map<String, Event> timedEvents = new HashMap<>();

    /**
     * Enum used in Countly.initMessaging() method which controls what kind of
     * app installation it is. Later (in Countly Dashboard or when calling Countly API method),
     * you'll be able to choose whether you want to send a message to test devices,
     * or to production ones.
     */
    public enum CountlyMessagingMode {
        TEST,
        PRODUCTION,
    }

    // see http://stackoverflow.com/questions/7048198/thread-safe-singletons-in-java
    private static class SingletonHolder {
        @SuppressLint("StaticFieldLeak")
        static final Countly instance = new Countly();
    }

    private ConnectionQueue connectionQueue_;
    private final ScheduledExecutorService timerService_;
    private EventQueue eventQueue_;
    private long prevSessionDurationStartTime_;
    private int activityCount_;
    private boolean disableUpdateSessionRequests_;
    private boolean enableLogging_;
    private Countly.CountlyMessagingMode messagingMode_;
    private Context context_;

    //user data access
    public static UserData userData;

    //track views
    private String lastView = null;
    private int lastViewStart = 0;
    private boolean firstView = true;
    private boolean autoViewTracker = false;
    private final static String VIEW_EVENT_KEY = "[CLY]_view";

    //overrides
    private boolean isHttpPostForced = false;//when true, all data sent to the server will be sent using HTTP POST

    //app crawlers
    private boolean shouldIgnoreCrawlers = true;//ignore app crawlers by default
    private boolean deviceIsAppCrawler = false;//by default assume that device is not a app crawler
    @SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
    private final List<String> appCrawlerNames = new ArrayList<>(Arrays.asList("Calypso AppCrawler"));//List against which device name is checked to determine if device is app crawler

    //star rating
    @SuppressWarnings("FieldCanBeLocal")
    private CountlyStarRating.RatingCallback starRatingCallback_;// saved callback that is used for automatic star rating

    //push related
    private boolean addMetadataToPushIntents = false;// a flag that indicates if metadata should be added to push notification intents

    //internal flags
    private boolean calledAtLeastOnceOnStart = false;//flag for if the onStart function has been called at least once

    //activity tracking
    boolean automaticTrackingShouldUseShortName = false;//flag for using short names

    //attribution
    protected boolean isAttributionEnabled = true;

    protected boolean isBeginSessionSent = false;

    //remote config_
    //if set to true, it will automatically download remote configs on module startup
    boolean remoteConfigAutomaticUpdateEnabled = false;
    RemoteConfig.RemoteConfigCallback remoteConfigInitCallback = null;

    //custom request header fields
    Map<String, String> requestHeaderCustomValues;

    //crash filtering
    String[] crashRegexFilters = null;
    Pattern[] crashRegexFiltersCompiled = null;

    //native crash
    static final String countlyFolderName = "Countly";
    static final String countlyNativeCrashFolderName = "CrashDumps";

    //automatic view segmentation
    protected Map<String, Object> automaticViewSegmentation = null;

    //GDPR
    protected boolean requiresConsent = false;

    private final Map<String, Boolean> featureConsentValues = new HashMap<>();
    private final Map<String, String[]> groupedFeatures = new HashMap<>();
    private final List<String> collectedConsentChanges = new ArrayList<>();

    Boolean delayedPushConsent = null;//if this is set, consent for push has to be set before finishing init and sending push changes
    boolean delayedLocationErasure = false;//if location needs to be cleared at the end of init

    CountlyConfig config_ = null;

    public static class CountlyFeatureNames {
        public static final String sessions = "sessions";
        public static final String events = "events";
        public static final String views = "views";
        //public static final String scrolls = "scrolls";
        //public static final String clicks = "clicks";
        //public static final String forms = "forms";
        public static final String location = "location";
        public static final String crashes = "crashes";
        public static final String attribution = "attribution";
        public static final String users = "users";
        public static final String push = "push";
        public static final String starRating = "star-rating";
        //public static final String accessoryDevices = "accessory-devices";
    }

    //a list of valid feature names that are used for checking
    private final String[] validFeatureNames = new String[]{
            CountlyFeatureNames.sessions,
            CountlyFeatureNames.events,
            CountlyFeatureNames.views,
            CountlyFeatureNames.location,
            CountlyFeatureNames.crashes,
            CountlyFeatureNames.attribution,
            CountlyFeatureNames.users,
            CountlyFeatureNames.push,
            CountlyFeatureNames.starRating};

    /**
     * Returns the Countly singleton.
     */
    public static Countly sharedInstance() {
        return SingletonHolder.instance;
    }

    /**
     * Constructs a Countly object.
     * Creates a new ConnectionQueue and initializes the session timer.
     */
    Countly() {
        connectionQueue_ = new ConnectionQueue();
        Countly.userData = new UserData(connectionQueue_);
        timerService_ = Executors.newSingleThreadScheduledExecutor();
        timerService_.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                onTimer();
            }
        }, TIMER_DELAY_IN_SECONDS, TIMER_DELAY_IN_SECONDS, TimeUnit.SECONDS);

        initConsent();
    }


    /**
     * Initializes the Countly SDK. Call from your main Activity's onCreate() method.
     * Must be called before other SDK methods can be used.
     * Device ID is supplied by OpenUDID service if available, otherwise Advertising ID is used.
     * BE CAUTIOUS!!!! If neither OpenUDID, nor Advertising ID is available, Countly will ignore this user.
     * @param context application context
     * @param serverURL URL of the Countly server to submit data to; use "https://try.count.ly" for Countly trial server
     * @param appKey app key for the application being tracked; find in the Countly Dashboard under Management &gt; Applications
     * @return Countly instance for easy method chaining
     * @throws IllegalArgumentException if context, serverURL, appKey, or deviceID are invalid
     * @throws IllegalStateException if the Countly SDK has already been initialized
     * @deprecated use {@link CountlyConfig} to pass data to init.
     */
    public Countly init(final Context context, final String serverURL, final String appKey) {
        return init(context, serverURL, appKey, null, OpenUDIDAdapter.isOpenUDIDAvailable() ? DeviceId.Type.OPEN_UDID : DeviceId.Type.ADVERTISING_ID);
    }

    /**
     * Initializes the Countly SDK. Call from your main Activity's onCreate() method.
     * Must be called before other SDK methods can be used.
     * @param context application context
     * @param serverURL URL of the Countly server to submit data to
     * @param appKey app key for the application being tracked; find in the Countly Dashboard under Management &gt; Applications
     * @param deviceID unique ID for the device the app is running on; note that null in deviceID means that Countly will fall back to OpenUDID, then, if it's not available, to Google Advertising ID
     * @return Countly instance for easy method chaining
     * @throws IllegalArgumentException if context, serverURL, appKey, or deviceID are invalid
     * @throws IllegalStateException if init has previously been called with different values during the same application instance
     * @deprecated use {@link CountlyConfig} to pass data to init.
     */
    public Countly init(final Context context, final String serverURL, final String appKey, final String deviceID) {
        return init(context, serverURL, appKey, deviceID, null);
    }

    /**
     * Initializes the Countly SDK. Call from your main Activity's onCreate() method.
     * Must be called before other SDK methods can be used.
     * @param context application context
     * @param serverURL URL of the Countly server to submit data to
     * @param appKey app key for the application being tracked; find in the Countly Dashboard under Management &gt; Applications
     * @param deviceID unique ID for the device the app is running on; note that null in deviceID means that Countly will fall back to OpenUDID, then, if it's not available, to Google Advertising ID
     * @param idMode enum value specifying which device ID generation strategy Countly should use: OpenUDID or Google Advertising ID
     * @return Countly instance for easy method chaining
     * @throws IllegalArgumentException if context, serverURL, appKey, or deviceID are invalid
     * @throws IllegalStateException if init has previously been called with different values during the same application instance
     * @deprecated use {@link CountlyConfig} to pass data to init.
     */
    public synchronized Countly init(final Context context, final String serverURL, final String appKey, final String deviceID, DeviceId.Type idMode) {
        return init(context, serverURL, appKey, deviceID, idMode, -1, null, null, null, null);
    }


    /**
     * Initializes the Countly SDK. Call from your main Activity's onCreate() method.
     * Must be called before other SDK methods can be used.
     * @param context application context
     * @param serverURL URL of the Countly server to submit data to
     * @param appKey app key for the application being tracked; find in the Countly Dashboard under Management &gt; Applications
     * @param deviceID unique ID for the device the app is running on; note that null in deviceID means that Countly will fall back to OpenUDID, then, if it's not available, to Google Advertising ID
     * @param idMode enum value specifying which device ID generation strategy Countly should use: OpenUDID or Google Advertising ID
     * @param starRatingLimit sets the limit after how many sessions, for each apps version, the automatic star rating dialog is shown
     * @param starRatingCallback the callback function that will be called from the automatic star rating dialog
     * @param starRatingTextTitle the shown title text for the star rating dialogs
     * @param starRatingTextMessage the shown message text for the star rating dialogs
     * @param starRatingTextDismiss the shown dismiss button text for the shown star rating dialogs
     * @return Countly instance for easy method chaining
     * @throws IllegalArgumentException if context, serverURL, appKey, or deviceID are invalid
     * @throws IllegalStateException if init has previously been called with different values during the same application instance
     * @deprecated use {@link CountlyConfig} to pass data to init.
     */
    public synchronized Countly init(final Context context, String serverURL, final String appKey, final String deviceID, DeviceId.Type idMode,
                                     int starRatingLimit, CountlyStarRating.RatingCallback starRatingCallback, String starRatingTextTitle, String starRatingTextMessage, String starRatingTextDismiss) {
        CountlyConfig config = new CountlyConfig();
        config.setContext(context).setServerURL(serverURL).setAppKey(appKey).setDeviceId(deviceID)
                .setIdMode(idMode).setStarRatingLimit(starRatingLimit).setStarRatingCallback(starRatingCallback)
                .setStarRatingTextTitle(starRatingTextTitle).setStarRatingTextMessage(starRatingTextMessage)
                .setStarRatingTextDismiss(starRatingTextDismiss);
        return init(config);
    }

    /**
     * Initializes the Countly SDK. Call from your main Activity's onCreate() method.
     * Must be called before other SDK methods can be used.
     * @param config contains all needed information to init SDK
     */
    public synchronized Countly init(CountlyConfig config){

        //enable logging
        if(config.loggingEnabled){
            //enable logging before any potential logging calls
            setLoggingEnabled(true);
        }

        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "[Init] Initializing Countly SDk version " + COUNTLY_SDK_VERSION_STRING);
        }

        if (config.context == null) {
            throw new IllegalArgumentException("valid context is required in Countly init, but was provided 'null'");
        }

        if (!UtilsNetworking.isValidURL(config.serverURL)) {
            throw new IllegalArgumentException("valid serverURL is required");
        }

        //enable unhandled crash reporting
        if(config.enableUnhandledCrashReporting){
            enableCrashReporting();
        }

        //react to given consent
        if(config.shouldRequireConsent){
            setRequiresConsent(true);
            setConsent(config.enabledFeatureNames, true);
        }

        if (config.serverURL.charAt(config.serverURL.length() - 1) == '/') {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.i(Countly.TAG, "[Init] Removing trailing '/' from provided server url");
            }
            config.serverURL = config.serverURL.substring(0, config.serverURL.length() - 1);//removing trailing '/' from server url
        }

        if (config.appKey == null || config.appKey.length() == 0) {
            throw new IllegalArgumentException("valid appKey is required, but was provided either 'null' or empty String");
        }

        if (config.deviceID != null && config.deviceID.length() == 0) {
            //device ID is provided but it's a empty string
            throw new IllegalArgumentException("valid deviceID is required, but was provided as empty String");
        }
        if (config.deviceID == null && config.idMode == null) {
            //device ID was not provided and no preferred mode specified. Choosing defaults
            if (OpenUDIDAdapter.isOpenUDIDAvailable()) config.idMode = DeviceId.Type.OPEN_UDID;
            else if (AdvertisingIdAdapter.isAdvertisingIdAvailable()) config.idMode = DeviceId.Type.ADVERTISING_ID;
        }
        if (config.deviceID == null && config.idMode == DeviceId.Type.OPEN_UDID && !OpenUDIDAdapter.isOpenUDIDAvailable()) {
            //choosing OPEN_UDID as ID type, but it's not available on this device
            throw new IllegalArgumentException("valid deviceID is required because OpenUDID is not available");
        }
        if (config.deviceID == null && config.idMode == DeviceId.Type.ADVERTISING_ID && !AdvertisingIdAdapter.isAdvertisingIdAvailable()) {
            //choosing advertising ID as type, but it's available on this device
            throw new IllegalArgumentException("valid deviceID is required because Advertising ID is not available (you need to include Google Play services 4.0+ into your project)");
        }
        if (eventQueue_ != null && (!connectionQueue_.getServerURL().equals(config.serverURL) ||
                !connectionQueue_.getAppKey().equals(config.appKey) ||
                !DeviceId.deviceIDEqualsNullSafe(config.deviceID, config.idMode, connectionQueue_.getDeviceId()) )) {
            //not sure if this needed
            throw new IllegalStateException("Countly cannot be reinitialized with different values");
        }

        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "[Init] Checking init parameters");
            Log.d(Countly.TAG, "[Init] Is consent required? [" + requiresConsent + "]");

            // Context class hierarchy
            // Context
            //|- ContextWrapper
            //|- - Application
            //|- - ContextThemeWrapper
            //|- - - - Activity
            //|- - Service
            //|- - - IntentService

            Class contextClass = config.context.getClass();
            Class contextSuperClass = contextClass.getSuperclass();

            String contextText = "[Init] Provided Context [" + config.context.getClass().getSimpleName() + "]";
            if(contextSuperClass != null){
                contextText += ", it's superclass: [" + contextSuperClass.getSimpleName() + "]";
            }

            Log.d(Countly.TAG, contextText);

        }
        //init view related things
        setViewTracking(config.enableViewTracking);

        setAutoTrackingUseShortName(config.autoTrackingUseShortName);

        setAutomaticViewSegmentationInternal(config.automaticViewSegmentation);

        //init other things
        addCustomNetworkRequestHeaders(config.customNetworkRequestHeaders);

        setPushIntentAddMetadata(config.pushIntentAddMetadata);

        setRemoteConfigAutomaticDownload(config.enableRemoteConfigAutomaticDownload, config.remoteConfigCallback);

        setHttpPostForced(config.httpPostForced);

        setCrashFiltersInternal(config.crashRegexFilters);

        enableParameterTamperingProtectionInternal(config.tamperingProtectionSalt);

        //set the star rating values
        starRatingCallback_ = config.starRatingCallback;
        CountlyStarRating.setStarRatingInitConfig(config.context, config.starRatingLimit, config.starRatingTextTitle, config.starRatingTextMessage, config.starRatingTextDismiss);

        //app crawler check
        checkIfDeviceIsAppCrawler();

        //set internal context, it's allowed to be changed on the second init call
        context_ = config.context.getApplicationContext();

        // if we get here and eventQueue_ != null, init is being called again with the same values,
        // so there is nothing to do, because we are already initialized with those values
        if (eventQueue_ == null) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.d(Countly.TAG, "[Init] About to init internal systems");
            }

            config_ = config;
            final CountlyStore countlyStore = new CountlyStore(config.context);

            boolean doingTemporaryIdMode = false;
            boolean customIDWasProvided = (config.deviceID != null);
            if(config.temporaryDeviceIdEnabled && !customIDWasProvided){
                //if we want to use temporary ID mode and no developer custom ID is provided
                //then we override that custom ID to set the temporary mode
                config.deviceID = DeviceId.temporaryCountlyDeviceId;
                doingTemporaryIdMode = true;
            }

            DeviceId deviceIdInstance;
            if (config.deviceID != null) {
                //if the developer provided a ID
                deviceIdInstance = new DeviceId(countlyStore, config.deviceID);
            } else {
                //the dev provided only a type, generate a appropriate ID
                deviceIdInstance = new DeviceId(countlyStore, config.idMode);
            }

            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.d(Countly.TAG, "[Init] Currently cached advertising ID [" + countlyStore.getCachedAdvertisingId() + "]");
            }
            AdvertisingIdAdapter.cacheAdvertisingID(config.context, countlyStore);

            deviceIdInstance.init(config.context, countlyStore, true);

            boolean temporaryDeviceIdWasEnabled = deviceIdInstance.temporaryIdModeEnabled();
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.d(Countly.TAG, "[Init] [TemporaryDeviceId] Previously was enabled: [" + temporaryDeviceIdWasEnabled + "]");
            }

            if(temporaryDeviceIdWasEnabled){
                //if we previously we're in temporary ID mode

                if(!config.temporaryDeviceIdEnabled || customIDWasProvided){
                    //if we don't set temporary device ID mode or
                    //a custom device ID is explicitly provided
                    //that means we have to exit temporary ID mode

                    if (Countly.sharedInstance().isLoggingEnabled()) {
                        Log.d(Countly.TAG, "[Init] [TemporaryDeviceId] Decided we have to exit temporary device ID mode, mode enabled: [" + config.temporaryDeviceIdEnabled + "], custom Device ID Set: [" + customIDWasProvided + "]");
                    }
                } else {
                    //we continue to stay in temporary ID mode
                    //no changes need to happen

                    if (Countly.sharedInstance().isLoggingEnabled()) {
                        Log.d(Countly.TAG, "[Init] [TemporaryDeviceId] Decided to stay in temporary ID mode");
                    }
                }
            } else {
                if(config.temporaryDeviceIdEnabled && config.deviceID == null){
                    //temporary device ID mode is enabled and
                    //no custom device ID is provided
                    //we can safely enter temporary device ID mode

                    if (Countly.sharedInstance().isLoggingEnabled()) {
                        Log.d(Countly.TAG, "[Init] [TemporaryDeviceId] Decided to enter temporary ID mode");
                    }
                }
            }

            //initialize networking queues
            connectionQueue_.setServerURL(config.serverURL);
            connectionQueue_.setAppKey(config.appKey);
            connectionQueue_.setCountlyStore(countlyStore);
            connectionQueue_.setDeviceId(deviceIdInstance);
            connectionQueue_.setRequestHeaderCustomValues(requestHeaderCustomValues);
            connectionQueue_.setContext(context_);

            eventQueue_ = new EventQueue(countlyStore);

            if(doingTemporaryIdMode) {
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.d(Countly.TAG, "[Init] Trying to enter temporary ID mode");
                }
                //if we are doing temporary ID, make sure it is applied
                //if it's not, change ID to it
                if(!deviceIdInstance.temporaryIdModeEnabled()){
                    if (Countly.sharedInstance().isLoggingEnabled()) {
                        Log.d(Countly.TAG, "[Init] Temporary ID mode was not enabled, entering it");
                    }
                    //temporary ID is not set
                    changeDeviceId(DeviceId.temporaryCountlyDeviceId);
                } else {
                    if (Countly.sharedInstance().isLoggingEnabled()) {
                        Log.d(Countly.TAG, "[Init] Temporary ID mode was enabled previously, nothing to enter");
                    }
                }

            }

            //do star rating related things
            if(getConsent(CountlyFeatureNames.starRating)) {
                CountlyStarRating.registerAppSession(config.context, starRatingCallback_);
            }

            //update remote config_ values if automatic update is enabled and we are not in temporary id mode
            if(remoteConfigAutomaticUpdateEnabled && anyConsentGiven() && !doingTemporaryIdMode){
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.d(Countly.TAG, "[Init] Automatically updating remote config values");
                }
                RemoteConfig.updateRemoteConfigValues(context_, null, null, connectionQueue_, false, remoteConfigInitCallback);
            }
        } else {
            //if this is not the first time we are calling init

            // context is allowed to be changed on the second init call
            connectionQueue_.setContext(context_);
        }


        if(requiresConsent) {
            //do delayed push consent action, if needed
            if(delayedPushConsent != null){
                doPushConsentSpecialAction(delayedPushConsent);
            }

            //do delayed location erasure, if needed
            if(delayedLocationErasure){
                doLocationConsentSpecialErasure();
            }

            //send collected consent changes that were made before initialization
            if (collectedConsentChanges.size() != 0) {
                for (String changeItem : collectedConsentChanges) {
                    connectionQueue_.sendConsentChanges(changeItem);
                }
                collectedConsentChanges.clear();
            }

            context_.sendBroadcast(new Intent(CONSENT_BROADCAST));

            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.d(Countly.TAG, "[Init] Countly is initialized with the current consent state:");
                checkAllConsent();
            }
        }

        //check for previous native crash dumps
        checkForNativeCrashDumps(config.context);

        return this;
    }

    /**
     * Checks whether Countly.init has been already called.
     * @return true if Countly is ready to use
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public synchronized boolean isInitialized() {
        return eventQueue_ != null;
    }

    /**
     * Immediately disables session &amp; event tracking and clears any stored session &amp; event data.
     * This API is useful if your app has a tracking opt-out switch, and you want to immediately
     * disable tracking when a user opts out. The onStart/onStop/recordEvent methods will throw
     * IllegalStateException after calling this until Countly is reinitialized by calling init
     * again.
     */
    public synchronized void halt() {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.i(Countly.TAG, "Halting Countly!");
        }
        eventQueue_ = null;
        final CountlyStore countlyStore = connectionQueue_.getCountlyStore();
        if (countlyStore != null) {
            countlyStore.clear();
        }
        connectionQueue_.setContext(null);
        connectionQueue_.setServerURL(null);
        connectionQueue_.setAppKey(null);
        connectionQueue_.setCountlyStore(null);
        prevSessionDurationStartTime_ = 0;
        activityCount_ = 0;
    }

    /**
     * Tells the Countly SDK that an Activity has started. Since Android does not have an
     * easy way to determine when an application instance starts and stops, you must call this
     * method from every one of your Activity's onStart methods for accurate application
     * session tracking.
     * @throws IllegalStateException if Countly SDK has not been initialized
     */
    public synchronized void onStart(Activity activity) {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Countly onStart called, [" + activityCount_ + "] -> [" + (activityCount_ + 1) + "] activities now open");
        }

        appLaunchDeepLink = false;
        if (eventQueue_ == null) {
            throw new IllegalStateException("init must be called before onStart");
        }

        ++activityCount_;
        if (activityCount_ == 1) {
            onStartHelper();
        }

        //check if there is an install referrer data
        String referrer = ReferrerReceiver.getReferrer(context_);
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Checking referrer: " + referrer);
        }
        if(referrer != null){
            connectionQueue_.sendReferrerData(referrer);
            ReferrerReceiver.deleteReferrer(context_);
        }

        CrashDetails.inForeground();

        if(autoViewTracker){
            String usedActivityName;

            if(automaticTrackingShouldUseShortName){
                usedActivityName = activity.getClass().getSimpleName();
            } else {
                usedActivityName = activity.getClass().getName();
            }
            recordView(usedActivityName, automaticViewSegmentation);
        }

        calledAtLeastOnceOnStart = true;
    }

    /**
     * Called when the first Activity is started. Sends a begin session event to the server
     * and initializes application session tracking.
     */
    private void onStartHelper() {
        prevSessionDurationStartTime_ = System.nanoTime();
        connectionQueue_.beginSession();
    }

    /**
     * Tells the Countly SDK that an Activity has stopped. Since Android does not have an
     * easy way to determine when an application instance starts and stops, you must call this
     * method from every one of your Activity's onStop methods for accurate application
     * session tracking.
     * @throws IllegalStateException if Countly SDK has not been initialized, or if
     *                               unbalanced calls to onStart/onStop are detected
     */
    public synchronized void onStop() {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Countly onStop called, [" + activityCount_ + "] -> [" + (activityCount_ - 1) + "] activities now open");
        }

        if (eventQueue_ == null) {
            throw new IllegalStateException("init must be called before onStop");
        }
        if (activityCount_ == 0) {
            throw new IllegalStateException("must call onStart before onStop");
        }

        --activityCount_;
        if (activityCount_ == 0) {
            onStopHelper();
        }

        CrashDetails.inBackground();

        //report current view duration
        reportViewDuration();
    }

    /**
     * Called when final Activity is stopped. Sends an end session event to the server,
     * also sends any unsent custom events.
     */
    private void onStopHelper() {
        connectionQueue_.endSession(roundedSecondsSinceLastSessionDurationUpdate());
        prevSessionDurationStartTime_ = 0;

        if (eventQueue_.size() > 0) {
            connectionQueue_.recordEvents(eventQueue_.events());
        }
    }

    /**
     * Called when GCM Registration ID is received. Sends a token session event to the server.
     */
    public void onRegistrationId(String registrationId) {
        onRegistrationId(registrationId, messagingMode_);
    }

    /**
     * DON'T USE THIS!!!!
     */
    public void onRegistrationId(String registrationId, CountlyMessagingMode mode) {
        if(!getConsent(CountlyFeatureNames.push)) {
            return;
        }

        connectionQueue_.tokenSession(registrationId, mode);
    }

    /**
     * Changes current device id type to the one specified in parameter. Closes current session and
     * reopens new one with new id. Doesn't merge user profiles on the server
     * @param type Device ID type to change to
     * @param deviceId Optional device ID for a case when type = DEVELOPER_SPECIFIED
     */
    public void changeDeviceId(DeviceId.Type type, String deviceId) {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Calling [changeDeviceId] with type and ID");
        }
        if (eventQueue_ == null) {
            throw new IllegalStateException("init must be called before changeDeviceId");
        }
        //if (activityCount_ == 0) {
//            throw new IllegalStateException("must call onStart before changeDeviceId");
//        }
        if (type == null) {
            throw new IllegalStateException("type cannot be null");
        }

        if(!anyConsentGiven()){
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.w(Countly.TAG, "Can't change Device ID if no consent is given");
            }
            return;
        }

        DeviceId currentDeviceId = connectionQueue_.getDeviceId();

        if(currentDeviceId.temporaryIdModeEnabled() && (deviceId != null && deviceId.equals(DeviceId.temporaryCountlyDeviceId))){
            // we already are in temporary mode and we want to set temporary mode
            // in this case we just ignore the request since nothing has to be done
            return;
        }

        if(currentDeviceId.temporaryIdModeEnabled() || connectionQueue_.queueContainsTemporaryIdItems()){
            // we are about to exit temporary ID mode
            // because of the previous check, we know that the new type is a different one
            // we just call our method for exiting it
            // we don't end the session, we just update the device ID and connection queue
            exitTemporaryIdMode(type, deviceId);
        }


        // we are either making a simple ID change or entering temporary mode
        // in both cases we act the same as the temporary ID requests will be updated with the final ID later

        //force flush events so that they are associated correctly
        sendEventsForced();

        connectionQueue_.endSession(roundedSecondsSinceLastSessionDurationUpdate(), currentDeviceId.getId());
        currentDeviceId.changeToId(context_, connectionQueue_.getCountlyStore(), type, deviceId);
        connectionQueue_.beginSession();

        //update remote config_ values if automatic update is enabled
        remoteConfigClearValues();
        if (remoteConfigAutomaticUpdateEnabled && anyConsentGiven()) {
            RemoteConfig.updateRemoteConfigValues(context_, null, null, connectionQueue_, false, null);
        }

        //clear automated star rating session values because now we have a new user
        CountlyStarRating.clearAutomaticStarRatingSessionCount(context_);
    }

    /**
     * Changes current device id to the one specified in parameter. Merges user profile with new id
     * (if any) with old profile.
     * @param deviceId new device id
     */
    public void changeDeviceId(String deviceId) {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Calling [changeDeviceId] only with ID");
        }
        if (!isInitialized()) {
            throw new IllegalStateException("init must be called before changeDeviceId");
        }
        //if (activityCount_ == 0) {
//            throw new IllegalStateException("must call onStart before changeDeviceId");
//        }
        if (deviceId == null || "".equals(deviceId)) {
            throw new IllegalStateException("deviceId cannot be null or empty");
        }

        if(!anyConsentGiven()){
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.w(Countly.TAG, "Can't change Device ID if no consent is given");
            }
            return;
        }

        if(connectionQueue_.getDeviceId().temporaryIdModeEnabled() || connectionQueue_.queueContainsTemporaryIdItems()){
            //if we are in temporary ID mode or
            //at some moment have enabled temporary mode

            if(deviceId.equals(DeviceId.temporaryCountlyDeviceId)){
                //if we want to enter temporary ID mode
                //just exit, nothing to do

                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.w(Countly.TAG, "[changeDeviceId] About to enter temporary ID mode when already in it");
                }

                return;
            }

            // if a developer supplied ID is provided
            //we just exit this mode and set the id to the provided one
            exitTemporaryIdMode(DeviceId.Type.DEVELOPER_SUPPLIED, deviceId);
        } else {
            //we are not in temporary mode, nothing special happens
            // we are either making a simple ID change or entering temporary mode
            // in both cases we act the same as the temporary ID requests will be updated with the final ID later

            connectionQueue_.changeDeviceId(deviceId, roundedSecondsSinceLastSessionDurationUpdate());

            //update remote config_ values if automatic update is enabled
            remoteConfigClearValues();
            if (remoteConfigAutomaticUpdateEnabled && anyConsentGiven()) {
                //request should be delayed, because of the delayed server merge
                RemoteConfig.updateRemoteConfigValues(context_, null, null, connectionQueue_, true, null);
            }
        }
    }

    private void exitTemporaryIdMode(DeviceId.Type type, String deviceId){
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Calling exitTemporaryIdMode");
        }

        if (!isInitialized()) {
            throw new IllegalStateException("init must be called before exitTemporaryIdMode");
        }

        //start by changing stored ID
        connectionQueue_.getDeviceId().changeToId(context_, connectionQueue_.getCountlyStore(), type, deviceId);

        //update stored request for ID change to use this new ID
        String[] storedRequests = connectionQueue_.getCountlyStore().connections();
        String temporaryIdTag = "&device_id=" + DeviceId.temporaryCountlyDeviceId;
        String newIdTag = "&device_id=" + deviceId;

        boolean foundOne = false;
        for(int a = 0 ; a < storedRequests.length ; a++){
            if(storedRequests[a].contains(temporaryIdTag)){
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.d(Countly.TAG, "[exitTemporaryIdMode] Found a tag to replace in: [" + storedRequests[a] + "]");
                }
                storedRequests[a] = storedRequests[a].replace(temporaryIdTag, newIdTag);
                foundOne = true;
            }
        }

        if(foundOne){
            connectionQueue_.getCountlyStore().replaceConnections(storedRequests);
        }

        //update remote config_ values if automatic update is enabled
        remoteConfigClearValues();
        if (remoteConfigAutomaticUpdateEnabled && anyConsentGiven()) {
            RemoteConfig.updateRemoteConfigValues(context_, null, null, connectionQueue_, false, null);
        }

        doStoredRequests();
    }

    /**
     * Records a custom event with no segmentation values, a count of one and a sum of zero.
     * @param key name of the custom event, required, must not be the empty string
     * @throws IllegalStateException if Countly SDK has not been initialized
     * @throws IllegalArgumentException if key is null or empty
     */
    public void recordEvent(final String key) {
        recordEvent(key, null, 1, 0);
    }

    /**
     * Records a custom event with no segmentation values, the specified count, and a sum of zero.
     * @param key name of the custom event, required, must not be the empty string
     * @param count count to associate with the event, should be more than zero
     * @throws IllegalStateException if Countly SDK has not been initialized
     * @throws IllegalArgumentException if key is null or empty
     */
    public void recordEvent(final String key, final int count) {
        recordEvent(key, null, count, 0);
    }

    /**
     * Records a custom event with no segmentation values, and the specified count and sum.
     * @param key name of the custom event, required, must not be the empty string
     * @param count count to associate with the event, should be more than zero
     * @param sum sum to associate with the event
     * @throws IllegalStateException if Countly SDK has not been initialized
     * @throws IllegalArgumentException if key is null or empty
     */
    public void recordEvent(final String key, final int count, final double sum) {
        recordEvent(key, null, count, sum);
    }

    /**
     * Records a custom event with the specified segmentation values and count, and a sum of zero.
     * @param key name of the custom event, required, must not be the empty string
     * @param segmentation segmentation dictionary to associate with the event, can be null
     * @param count count to associate with the event, should be more than zero
     * @throws IllegalStateException if Countly SDK has not been initialized
     * @throws IllegalArgumentException if key is null or empty
     */
    public void recordEvent(final String key, final Map<String, String> segmentation, final int count) {
        recordEvent(key, segmentation, count, 0);
    }

    /**
     * Records a custom event with the specified values.
     * @param key name of the custom event, required, must not be the empty string
     * @param segmentation segmentation dictionary to associate with the event, can be null
     * @param count count to associate with the event, should be more than zero
     * @param sum sum to associate with the event
     * @throws IllegalStateException if Countly SDK has not been initialized
     * @throws IllegalArgumentException if key is null or empty, count is less than 1, or if
     *                                  segmentation contains null or empty keys or values
     */
    public synchronized void recordEvent(final String key, final Map<String, String> segmentation, final int count, final double sum) {
        recordEvent(key, segmentation, count, sum, 0);
    }

    /**
     * Records a custom event with the specified values.
     * @param key name of the custom event, required, must not be the empty string
     * @param segmentation segmentation dictionary to associate with the event, can be null
     * @param count count to associate with the event, should be more than zero
     * @param sum sum to associate with the event
     * @param dur duration of an event
     * @throws IllegalStateException if Countly SDK has not been initialized
     * @throws IllegalArgumentException if key is null or empty, count is less than 1, or if
     *                                  segmentation contains null or empty keys or values
     */
    public synchronized void recordEvent(final String key, final Map<String, String> segmentation, final int count, final double sum, final double dur){
        recordEvent(key, segmentation, null, null, count, sum, dur);
    }

    /**
     * Records a custom event with the specified values.
     * @param key name of the custom event, required, must not be the empty string
     * @param segmentation segmentation dictionary to associate with the event, can be null
     * @param count count to associate with the event, should be more than zero
     * @param sum sum to associate with the event
     * @param dur duration of an event
     * @throws IllegalStateException if Countly SDK has not been initialized
     * @throws IllegalArgumentException if key is null or empty, count is less than 1, or if
     *                                  segmentation contains null or empty keys or values
     */
    public synchronized void recordEvent(final String key, final Map<String, String> segmentation, final Map<String, Integer> segmentationInt, final Map<String, Double> segmentationDouble, final int count, final double sum, final double dur) {
        if (!isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before recordEvent");
        }
        if (key == null || key.length() == 0) {
            throw new IllegalArgumentException("Valid Countly event key is required");
        }
        if (count < 1) {
            throw new IllegalArgumentException("Countly event count should be greater than zero");
        }

        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Recording event with key: [" + key + "]");
        }

        if (segmentation != null) {
            for (String k : segmentation.keySet()) {
                if (k == null || k.length() == 0) {
                    throw new IllegalArgumentException("Countly event segmentation key cannot be null or empty");
                }
                if (segmentation.get(k) == null || segmentation.get(k).length() == 0) {
                    throw new IllegalArgumentException("Countly event segmentation value cannot be null or empty");
                }
            }
        }

        if (segmentationInt != null) {
            for (String k : segmentationInt.keySet()) {
                if (k == null || k.length() == 0) {
                    throw new IllegalArgumentException("Countly event segmentation key cannot be null or empty");
                }
                if (segmentationInt.get(k) == null) {
                    throw new IllegalArgumentException("Countly event segmentation value cannot be null");
                }
            }
        }

        if (segmentationDouble != null) {
            for (String k : segmentationDouble.keySet()) {
                if (k == null || k.length() == 0) {
                    throw new IllegalArgumentException("Countly event segmentation key cannot be null or empty");
                }
                if (segmentationDouble.get(k) == null) {
                    throw new IllegalArgumentException("Countly event segmentation value cannot be null");
                }
            }
        }

        switch (key) {
            case STAR_RATING_EVENT_KEY:
                if (Countly.sharedInstance().getConsent(CountlyFeatureNames.starRating)) {
                    eventQueue_.recordEvent(key, segmentation, segmentationInt, segmentationDouble, count, sum, dur);
                    sendEventsForced();
                }
                break;
            case VIEW_EVENT_KEY:
                if (Countly.sharedInstance().getConsent(CountlyFeatureNames.views)) {
                    eventQueue_.recordEvent(key, segmentation, segmentationInt, segmentationDouble, count, sum, dur);
                    sendEventsForced();
                }
                break;
            default:
                if (Countly.sharedInstance().getConsent(CountlyFeatureNames.events)) {
                    eventQueue_.recordEvent(key, segmentation, segmentationInt, segmentationDouble, count, sum, dur);
                    sendEventsIfNeeded();
                }
                break;
        }
    }

    /**
     * Enable or disable automatic view tracking
     * @param enable boolean for the state of automatic view tracking
     * @deprecated use CountlyConfig during init to set this
     * @return Returns link to Countly for call chaining
     */
    public synchronized Countly setViewTracking(boolean enable){
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Enabling automatic view tracking");
        }
        autoViewTracker = enable;
        return this;
    }

    /**
     * Check state of automatic view tracking
     * @return boolean - true if enabled, false if disabled
     */
    public synchronized boolean isViewTrackingEnabled(){
        return autoViewTracker;
    }

    /**
     *  Record a view manually, without automatic tracking
     * or track view that is not automatically tracked
     * like fragment, Message box or transparent Activity
     * @param viewName String - name of the view
     * @return Returns link to Countly for call chaining
     * @deprecated
     */
    public synchronized Countly recordView(String viewName) {
        return recordView(viewName, null);
    }

    /**
     *  Record a view manually, without automatic tracking
     * or track view that is not automatically tracked
     * like fragment, Message box or transparent Activity
     * @param viewName String - name of the view
     * @param viewSegmentation Map<String, Object> - segmentation that will be added to the view, set 'null' if none should be added
     * @return Returns link to Countly for call chaining
     */
    public synchronized Countly recordView(String viewName, Map<String, Object> viewSegmentation) {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            int segmCount = 0;
            if (viewSegmentation != null) {
                segmCount = viewSegmentation.size();
            }
            Log.d(Countly.TAG, "Recording view with name: [" + viewName + "], previous view:[" + lastView + "] view segment count:[" + segmCount + "]");
        }

        reportViewDuration();
        lastView = viewName;
        lastViewStart = UtilsTime.currentTimestampSeconds();
        HashMap<String, String> segmentsString = new HashMap<>();
        segmentsString.put("name", viewName);
        segmentsString.put("visit", "1");
        segmentsString.put("segment", "Android");
        if(firstView) {
            firstView = false;
            segmentsString.put("start", "1");
        }

        HashMap<String, Integer> segmentsInt = null;
        HashMap<String, Double> segmentsDouble = null;

        if(viewSegmentation != null){
            segmentsInt = new HashMap<>();
            segmentsDouble = new HashMap<>();

            fillInSegmentation(viewSegmentation, segmentsString, segmentsInt, segmentsDouble);
        }

        recordEvent(VIEW_EVENT_KEY, segmentsString, segmentsInt, segmentsDouble, 1, 0, 0);
        return this;
    }

    /**
     * Sets information about user. Possible keys are:
     * <ul>
     * <li>
     * name - (String) providing user's full name
     * </li>
     * <li>
     * username - (String) providing user's nickname
     * </li>
     * <li>
     * email - (String) providing user's email address
     * </li>
     * <li>
     * organization - (String) providing user's organization's name where user works
     * </li>
     * <li>
     * phone - (String) providing user's phone number
     * </li>
     * <li>
     * picture - (String) providing WWW URL to user's avatar or profile picture
     * </li>
     * <li>
     * picturePath - (String) providing local path to user's avatar or profile picture
     * </li>
     * <li>
     * gender - (String) providing user's gender as M for male and F for female
     * </li>
     * <li>
     * byear - (int) providing user's year of birth as integer
     * </li>
     * </ul>
     * @param data Map&lt;String, String&gt; with user data
     * @deprecated use {@link UserData#setUserData(Map)} to set data and {@link UserData#save()} to send it to server.
     */
    public synchronized Countly setUserData(Map<String, String> data) {
        return setUserData(data, null);
    }

    /**
     * Sets information about user with custom properties.
     * In custom properties you can provide any string key values to be stored with user
     * Possible keys are:
     * <ul>
     * <li>
     * name - (String) providing user's full name
     * </li>
     * <li>
     * username - (String) providing user's nickname
     * </li>
     * <li>
     * email - (String) providing user's email address
     * </li>
     * <li>
     * organization - (String) providing user's organization's name where user works
     * </li>
     * <li>
     * phone - (String) providing user's phone number
     * </li>
     * <li>
     * picture - (String) providing WWW URL to user's avatar or profile picture
     * </li>
     * <li>
     * picturePath - (String) providing local path to user's avatar or profile picture
     * </li>
     * <li>
     * gender - (String) providing user's gender as M for male and F for female
     * </li>
     * <li>
     * byear - (int) providing user's year of birth as integer
     * </li>
     * </ul>
     * @param data Map&lt;String, String&gt; with user data
     * @param customdata Map&lt;String, String&gt; with custom key values for this user
     * @deprecated use {@link UserData#setUserData(Map, Map)} to set data and {@link UserData#save()}  to send it to server.
     */
    public synchronized Countly setUserData(Map<String, String> data, Map<String, String> customdata) {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Setting user data");
        }
        UserData.setData(data);
        if(customdata != null)
            UserData.setCustomData(customdata);
        connectionQueue_.sendUserData();
        UserData.clear();
        return this;
    }

    /**
     * Sets custom properties.
     * In custom properties you can provide any string key values to be stored with user
     * @param customdata Map&lt;String, String&gt; with custom key values for this user
     * @deprecated use {@link UserData#setCustomUserData(Map)} to set data and {@link UserData#save()} to send it to server.
     */
    public synchronized Countly setCustomUserData(Map<String, String> customdata) {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Setting custom user data");
        }
        if(customdata != null)
            UserData.setCustomData(customdata);
        connectionQueue_.sendUserData();
        UserData.clear();
        return this;
    }

    /**
     * Disable sending of location data
     * @return Returns link to Countly for call chaining
     */
    public synchronized Countly disableLocation() {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Disabling location");
        }

        if(!getConsent(CountlyFeatureNames.location)){
            //can't send disable location request if no consent given
            return this;
        }

        resetLocationValues();
        connectionQueue_.getCountlyStore().setLocationDisabled(true);
        connectionQueue_.sendLocation();

        return this;
    }

    private synchronized void resetLocationValues(){
        connectionQueue_.getCountlyStore().setLocationCountryCode("");
        connectionQueue_.getCountlyStore().setLocationCity("");
        connectionQueue_.getCountlyStore().setLocation("");
        connectionQueue_.getCountlyStore().setLocationIpAddress("");
    }

    /**
     * Set location parameters. If they are set before begin_session, they will be sent as part of it.
     * If they are set after, then they will be sent as a separate request.
     * If this is called after disabling location, it will enable it.
     * @param country_code ISO Country code for the user's country
     * @param city Name of the user's city
     * @param location comma separate lat and lng values. For example, "56.42345,123.45325"
     * @return Returns link to Countly for call chaining
     */
    public synchronized Countly setLocation(String country_code, String city, String location, String ipAddress){
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Setting location parameters");
        }

        if(!getConsent(CountlyFeatureNames.location)){
            return this;
        }

        if(country_code != null){
            connectionQueue_.getCountlyStore().setLocationCountryCode(country_code);
        }

        if(city != null){
            connectionQueue_.getCountlyStore().setLocationCity(city);
        }

        if(location != null){
            connectionQueue_.getCountlyStore().setLocation(location);
        }

        if(ipAddress != null){
            connectionQueue_.getCountlyStore().setLocationIpAddress(ipAddress);
        }

        if((country_code == null && city != null) || (city == null && country_code != null)) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.w(Countly.TAG, "In \"setLocation\" both city and country code need to be set at the same time to be sent");
            }
        }

        if(country_code != null || city != null || location != null || ipAddress != null){
            connectionQueue_.getCountlyStore().setLocationDisabled(false);
        }


        if(isBeginSessionSent || !Countly.sharedInstance().getConsent(Countly.CountlyFeatureNames.sessions)){
            //send as a separate request if either begin session was already send and we missed our first opportunity
            //or if consent for sessions is not given and our only option to send this is as a separate request
            connectionQueue_.sendLocation();
        } else {
            //will be sent a part of begin session
        }

        return this;
    }

    /**
     * Sets custom segments to be reported with crash reports
     * In custom segments you can provide any string key values to segments crashes by
     * @param segments Map&lt;String, String&gt; key segments and their values
     * @return Returns link to Countly for call chaining
     */
    public synchronized Countly setCustomCrashSegments(Map<String, String> segments) {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Setting custom crash segments");
        }

        if(!getConsent(CountlyFeatureNames.crashes)){
            return this;
        }

        if(segments != null) {
            CrashDetails.setCustomSegments(segments);
        }
        return this;
    }

    /**
     * Add crash breadcrumb like log record to the log that will be send together with crash report
     * @param record String a bread crumb for the crash report
     * @return Returns link to Countly for call chaining
     * @deprecated use `addCrashBreadcrumb`
     */
    public synchronized Countly addCrashLog(String record) {
        return addCrashBreadcrumb(record);
    }

    /**
     * Add crash breadcrumb like log record to the log that will be send together with crash report
     * @param record String a bread crumb for the crash report
     * @return Returns link to Countly for call chaining
     */
    public synchronized Countly addCrashBreadcrumb(String record) {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Adding crash breadcrumb");
        }

        if(!getConsent(CountlyFeatureNames.crashes)){
            return this;
        }

        if(record == null || record.isEmpty()) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.d(Countly.TAG, "Can't add a null or empty crash breadcrumb");
            }
            return this;
        }

        CrashDetails.addLog(record);
        return this;
    }

    /**
     * Called during init to check if there are any crash dumps saved
     * @param context android context
     */
    protected synchronized void checkForNativeCrashDumps(Context context){
        Log.d(TAG, "Checking for native crash dumps");

        String basePath = context.getCacheDir().getAbsolutePath();
        String finalPath = basePath + File.separator + countlyFolderName + File.separator + countlyNativeCrashFolderName;

        File folder = new File(finalPath);
        if (folder.exists()) {
            Log.d(TAG, "Native crash folder exists, checking for dumps");

            File[] dumpFiles = folder.listFiles();
            Log.d(TAG,"Crash dump folder contains [" + dumpFiles.length + "] files");
            for (File dumpFile : dumpFiles) {
                //record crash
                recordNativeException(dumpFile);

                //delete dump file
                dumpFile.delete();
            }
        } else {
            Log.d(TAG, "Native crash folder does not exist");
        }
    }

    protected synchronized void recordNativeException(File dumpFile){
        Log.d(TAG, "Recording native crash dump: [" + dumpFile.getName() + "]");

        //check for consent
        if(!getConsent(CountlyFeatureNames.crashes)){
            return;
        }

        //read bytes
        int size = (int)dumpFile.length();
        byte[] bytes = new byte[size];

        try {
            BufferedInputStream buf = new BufferedInputStream(new FileInputStream(dumpFile));
            buf.read(bytes, 0, bytes.length);
            buf.close();
        } catch (Exception e) {
            Log.e(TAG, "Failed to read dump file bytes");
            e.printStackTrace();
            return;
        }

        //convert to base64
        String dumpString = Base64.encodeToString(bytes, Base64.NO_WRAP);

        //record crash
        connectionQueue_.sendCrashReport(dumpString, false, true);
    }

    /**
     * Log handled exception to report it to server as non fatal crash
     * @param exception Exception to log
     * @deprecated Use recordHandledException
     * @return Returns link to Countly for call chaining
     */
    public synchronized Countly logException(Exception exception) {
        return recordException(exception, true);
    }

    /**
     * Log handled exception to report it to server as non fatal crash
     * @param exception Exception to log
     * @return Returns link to Countly for call chaining
     */
    public synchronized Countly recordHandledException(Exception exception) {
        return recordException(exception, true);
    }

    /**
     * Log handled exception to report it to server as non fatal crash
     * @param exception Throwable to log
     * @return Returns link to Countly for call chaining
     */
    public synchronized Countly recordHandledException(Throwable exception) {
        return recordException(exception, true);
    }

    /**
     * Log unhandled exception to report it to server as fatal crash
     * @param exception Exception to log
     * @return Returns link to Countly for call chaining
     */
    public synchronized Countly recordUnhandledException(Exception exception) {
        return recordException(exception, false);
    }

    /**
     * Log unhandled exception to report it to server as fatal crash
     * @param exception Throwable to log
     * @return Returns link to Countly for call chaining
     */
    public synchronized Countly recordUnhandledException(Throwable exception) {
        return recordException(exception, false);
    }

    /**
     * Common call for handling exceptions
     * @param exception Exception to log
     * @param itIsHandled If the exception is handled or not (fatal)
     * @return Returns link to Countly for call chaining
     */
    private synchronized Countly recordException(Throwable exception, boolean itIsHandled) {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Logging exception, handled:[" + itIsHandled + "]");
        }

        if(!getConsent(CountlyFeatureNames.crashes)){
            return this;
        }

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        exception.printStackTrace(pw);
        String exceptionString = sw.toString();

        if(crashFilterCheck(crashRegexFiltersCompiled, exceptionString)){
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.d(Countly.TAG, "Crash filter found a match, exception will be ignored, [" + exceptionString.substring(0, Math.min(exceptionString.length(), 60)) + "]");
            }
        } else {
            connectionQueue_.sendCrashReport(exceptionString, itIsHandled, false);
        }
        return this;
    }

    /**
     * Enable crash reporting to send unhandled crash reports to server
     * @deprecated use CountlyConfig during init to set this
     * @return Returns link to Countly for call chaining
     */
    public synchronized Countly enableCrashReporting() {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Enabling unhandled crash reporting");
        }
        //get default handler
        final Thread.UncaughtExceptionHandler oldHandler = Thread.getDefaultUncaughtExceptionHandler();

        Thread.UncaughtExceptionHandler handler = new Thread.UncaughtExceptionHandler() {

            @Override
            public void uncaughtException(Thread t, Throwable e) {
                if(getConsent(CountlyFeatureNames.crashes)){
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    e.printStackTrace(pw);

                    Countly.sharedInstance().connectionQueue_.sendCrashReport(sw.toString(), false, false);
                }

                //if there was another handler before
                if(oldHandler != null){
                    //notify it also
                    oldHandler.uncaughtException(t,e);
                }
            }
        };

        Thread.setDefaultUncaughtExceptionHandler(handler);
        return this;
    }

    /**
     * Start timed event with a specified key
     * @param key name of the custom event, required, must not be the empty string or null
     * @return true if no event with this key existed before and event is started, false otherwise
     */
    public synchronized boolean startEvent(final String key) {
        if (!isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before recordEvent");
        }
        if (key == null || key.length() == 0) {
            throw new IllegalArgumentException("Valid Countly event key is required");
        }
        if (timedEvents.containsKey(key)) {
            return false;
        }
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Starting event: [" + key + "]");
        }
        timedEvents.put(key, new Event(key));
        return true;
    }

    /**
     * End timed event with a specified key
     * @param key name of the custom event, required, must not be the empty string or null
     * @return true if event with this key has been previously started, false otherwise
     */
    public synchronized boolean endEvent(final String key) {
        return endEvent(key, null, 1, 0);
    }

    /**
     * End timed event with a specified key
     * @param key name of the custom event, required, must not be the empty string
     * @param segmentation segmentation dictionary to associate with the event, can be null
     * @param count count to associate with the event, should be more than zero
     * @param sum sum to associate with the event
     * @throws IllegalStateException if Countly SDK has not been initialized
     * @throws IllegalArgumentException if key is null or empty, count is less than 1, or if
     *                                  segmentation contains null or empty keys or values
     * @return true if event with this key has been previously started, false otherwise
     */
    public synchronized boolean endEvent(final String key, final Map<String, String> segmentation, final int count, final double sum) {
        return endEvent(key, segmentation, null, null, count, sum);
    }
    /**
     * End timed event with a specified key
     * @param key name of the custom event, required, must not be the empty string
     * @param segmentation segmentation dictionary to associate with the event, can be null
     * @param count count to associate with the event, should be more than zero
     * @param sum sum to associate with the event
     * @throws IllegalStateException if Countly SDK has not been initialized
     * @throws IllegalArgumentException if key is null or empty, count is less than 1, or if
     *                                  segmentation contains null or empty keys or values
     * @return true if event with this key has been previously started, false otherwise
     */
    public synchronized boolean endEvent(final String key, final Map<String, String> segmentation, final Map<String, Integer> segmentationInt, final Map<String, Double> segmentationDouble, final int count, final double sum) {
        Event event = timedEvents.remove(key);

        if (event != null) {
            if(!getConsent(CountlyFeatureNames.events)) {
                return true;
            }

            if (!isInitialized()) {
                throw new IllegalStateException("Countly.sharedInstance().init must be called before recordEvent");
            }
            if (key == null || key.length() == 0) {
                throw new IllegalArgumentException("Valid Countly event key is required");
            }
            if (count < 1) {
                throw new IllegalArgumentException("Countly event count should be greater than zero");
            }
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.d(Countly.TAG, "Ending event: [" + key + "]");
            }

            if (segmentation != null) {
                for (String k : segmentation.keySet()) {
                    if (k == null || k.length() == 0) {
                        throw new IllegalArgumentException("Countly event segmentation key cannot be null or empty");
                    }
                    if (segmentation.get(k) == null || segmentation.get(k).length() == 0) {
                        throw new IllegalArgumentException("Countly event segmentation value cannot be null or empty");
                    }
                }
            }

            if (segmentationInt != null) {
                for (String k : segmentationInt.keySet()) {
                    if (k == null || k.length() == 0) {
                        throw new IllegalArgumentException("Countly event segmentation key cannot be null or empty");
                    }
                    if (segmentationInt.get(k) == null) {
                        throw new IllegalArgumentException("Countly event segmentation value cannot be null");
                    }
                }
            }

            if (segmentationDouble != null) {
                for (String k : segmentationDouble.keySet()) {
                    if (k == null || k.length() == 0) {
                        throw new IllegalArgumentException("Countly event segmentation key cannot be null or empty");
                    }
                    if (segmentationDouble.get(k) == null) {
                        throw new IllegalArgumentException("Countly event segmentation value cannot be null");
                    }
                }
            }

            long currentTimestamp = UtilsTime.currentTimestampMs();

            event.segmentation = segmentation;
            event.segmentationDouble = segmentationDouble;
            event.segmentationInt = segmentationInt;
            event.dur = (currentTimestamp - event.timestamp) / 1000.0;
            event.count = count;
            event.sum = sum;

            eventQueue_.recordEvent(event);
            sendEventsIfNeeded();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Cancel timed event with a specified key
     * @return true if event with this key has been previously started, false otherwise
     **/
    public synchronized boolean cancelEvent(final String key) {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Calling cancelEvent");
        }

        if (!isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before cancelEvent");
        }

        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Canceling event: [" + key + "]");
        }

        Event event = timedEvents.remove(key);

        return event != null;
    }

    /**
     * Disable periodic session time updates.
     * By default, Countly will send a request to the server each 30 seconds with a small update
     * containing session duration time. This method allows you to disable such behavior.
     * Note that event updates will still be sent every 10 events or 30 seconds after event recording.
     * @param disable whether or not to disable session time updates
     * @return Countly instance for easy method chaining
     */
    public synchronized Countly setDisableUpdateSessionRequests(final boolean disable) {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Disabling periodic session time updates");
        }
        disableUpdateSessionRequests_ = disable;
        return this;
    }

    /**
     * Sets whether debug logging is turned on or off. Logging is disabled by default.
     * @param enableLogging true to enable logging, false to disable logging
     * @deprecated use CountlyConfig during init to set this
     * @return Countly instance for easy method chaining
     */
    public synchronized Countly setLoggingEnabled(final boolean enableLogging) {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Enabling logging");
        }
        enableLogging_ = enableLogging;
        return this;
    }

    public synchronized boolean isLoggingEnabled() {
        return enableLogging_;
    }

    /**
     *
     * @param salt
     * @deprecated use CountlyConfig (setParameterTamperingProtectionSalt) during init to set this
     * @return
     */
    public synchronized Countly enableParameterTamperingProtection(String salt) {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Enabling tamper protection");
        }

        enableParameterTamperingProtectionInternal(salt);

        return this;
    }

    /**
     * Use by both the external call and config call
     * @param salt
     */
    private synchronized void enableParameterTamperingProtectionInternal(String salt){
        ConnectionProcessor.salt = salt;
    }

    /**
     * Returns if the countly sdk onStart function has been called at least once
     * @return true - yes, it has, false - no it has not
     */
    public synchronized boolean hasBeenCalledOnStart() {
        return calledAtLeastOnceOnStart;
    }

    public synchronized Countly setEventQueueSizeToSend(int size) {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Setting event queue size: [" + size + "]");
        }
        EVENT_QUEUE_SIZE_THRESHOLD = size;
        return this;
    }

    private boolean appLaunchDeepLink = true;

    public static void onCreate(Activity activity) {
        Intent launchIntent = activity.getPackageManager().getLaunchIntentForPackage(activity.getPackageName());

        if (sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Activity created: " + activity.getClass().getName() + " ( main is " + launchIntent.getComponent().getClassName() + ")");
        }

        Intent intent = activity.getIntent();
        if (intent != null) {
            Uri data = intent.getData();
            if (data != null) {
                if (sharedInstance().isLoggingEnabled()) {
                    Log.d(Countly.TAG, "Data in activity created intent: " + data + " (appLaunchDeepLink " + sharedInstance().appLaunchDeepLink + ") " );
                }
                if (sharedInstance().appLaunchDeepLink) {
                    DeviceInfo.deepLink = data.toString();
                }
            }
        }
    }

    /**
     * Reports duration of last view
     */
    private void reportViewDuration() {
        if (sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "View [" + lastView + "] is getting closed, reporting duration: [" + (UtilsTime.currentTimestampSeconds() - lastViewStart) + "], current timestamp: [" + UtilsTime.currentTimestampSeconds() + "], last views start: [" + lastViewStart + "]");
        }

        if (lastView != null && lastViewStart <= 0) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.e(Countly.TAG, "Last view start value is not normal: [" + lastViewStart + "]");
            }
        }

        if (!getConsent(CountlyFeatureNames.views)) {
            return;
        }

        //only record view if the view name is not null and if it has a reasonable duration
        //if the lastViewStart is equal to 0, the duration would be set to the current timestamp
        //and therefore will be ignored
        if (lastView != null && lastViewStart > 0) {
            HashMap<String, String> segments = new HashMap<>();
            segments.put("name", lastView);
            segments.put("dur", String.valueOf(UtilsTime.currentTimestampSeconds() - lastViewStart));
            segments.put("segment", "Android");
            recordEvent(VIEW_EVENT_KEY, segments, 1);
            lastView = null;
            lastViewStart = 0;
        }
    }

    /**
     * Submits all of the locally queued events to the server if there are more than 10 of them.
     */
    protected void sendEventsIfNeeded() {
        if (eventQueue_.size() >= EVENT_QUEUE_SIZE_THRESHOLD) {
            connectionQueue_.recordEvents(eventQueue_.events());
        }
    }

    /**
     * Immediately sends all stored events
     */
    protected void sendEventsForced() {
        connectionQueue_.recordEvents(eventQueue_.events());
    }

    /**
     * Called every 60 seconds to send a session heartbeat to the server. Does nothing if there
     * is not an active application session.
     */
    synchronized void onTimer() {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.v(Countly.TAG, "[onTimer] Calling heartbeat, Activity count:[" + activityCount_ + "]");
        }

        final boolean hasActiveSession = activityCount_ > 0;
        if (hasActiveSession) {
            if (!disableUpdateSessionRequests_) {
                connectionQueue_.updateSession(roundedSecondsSinceLastSessionDurationUpdate());
            }
            if (eventQueue_.size() > 0) {
                connectionQueue_.recordEvents(eventQueue_.events());
            }
        }

        if(isInitialized()){
            connectionQueue_.tick();
        }
    }

    /**
     * Calculates the unsent session duration in seconds, rounded to the nearest int.
     */
    int roundedSecondsSinceLastSessionDurationUpdate() {
        final long currentTimestampInNanoseconds = System.nanoTime();
        final long unsentSessionLengthInNanoseconds = currentTimestampInNanoseconds - prevSessionDurationStartTime_;
        prevSessionDurationStartTime_ = currentTimestampInNanoseconds;
        return (int) Math.round(unsentSessionLengthInNanoseconds / 1000000000.0d);
    }

    /**
     * Allows public key pinning.
     * Supply list of SSL certificates (base64-encoded strings between "-----BEGIN CERTIFICATE-----" and "-----END CERTIFICATE-----" without end-of-line)
     * along with server URL starting with "https://". Countly will only accept connections to the server
     * if public key of SSL certificate provided by the server matches one provided to this method or by {@link #enableCertificatePinning(List)}.
     * @param certificates List of SSL public keys
     * @return Countly instance
     */
    public static Countly enablePublicKeyPinning(List<String> certificates) {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.i(Countly.TAG, "Enabling public key pinning");
        }
        publicKeyPinCertificates = certificates;
        return Countly.sharedInstance();
    }

    /**
     * Allows certificate pinning.
     * Supply list of SSL certificates (base64-encoded strings between "-----BEGIN CERTIFICATE-----" and "-----END CERTIFICATE-----" without end-of-line)
     * along with server URL starting with "https://". Countly will only accept connections to the server
     * if certificate provided by the server matches one provided to this method or by {@link #enablePublicKeyPinning(List)}.
     * @param certificates List of SSL certificates
     * @return Countly instance
     */
    public static Countly enableCertificatePinning(List<String> certificates) {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.i(Countly.TAG, "Enabling certificate pinning");
        }
        certificatePinCertificates = certificates;
        return Countly.sharedInstance();
    }

    /**
     * Shows the star rating dialog
     * @param activity the activity that will own the dialog
     * @param callback callback for the star rating dialog "rate" and "dismiss" events
     */
    public void showStarRating(Activity activity, CountlyStarRating.RatingCallback callback){
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Showing star rating");
        }

        if(!getConsent(CountlyFeatureNames.starRating)) {
            return;
        }

        CountlyStarRating.showStarRating(activity, callback);
    }

    /**
     * Set's the text's for the different fields in the star rating dialog. Set value null if for some field you want to keep the old value
     * @param starRatingTextTitle dialog's title text
     * @param starRatingTextMessage dialog's message text
     * @param starRatingTextDismiss dialog's dismiss buttons text
     */
    public synchronized Countly setStarRatingDialogTexts(String starRatingTextTitle, String starRatingTextMessage, String starRatingTextDismiss) {
        if(context_ == null) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.e(Countly.TAG, "Can't call this function before init has been called");
                return this;
            }
        }

        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Setting star rating texts");
        }

        CountlyStarRating.setStarRatingInitConfig(context_, -1, starRatingTextTitle, starRatingTextMessage, starRatingTextDismiss);

        return this;
    }

    /**
     * Set if the star rating should be shown automatically
     * @param IsShownAutomatically set it true if you want to show the app star rating dialog automatically for each new version after the specified session amount
     */
    public synchronized Countly setIfStarRatingShownAutomatically(boolean IsShownAutomatically) {
        if(context_ == null) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.e(Countly.TAG, "Can't call this function before init has been called");
                return this;
            }
        }

        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Setting to show star rating automatically: [" + IsShownAutomatically + "]");
        }

        CountlyStarRating.setShowDialogAutomatically(context_, IsShownAutomatically);

        return this;
    }

    /**
     * Set if the star rating is shown only once per app lifetime
     * @param disableAsking set true if you want to disable asking the app rating for each new app version (show it only once per apps lifetime)
     */
    public synchronized Countly setStarRatingDisableAskingForEachAppVersion(boolean disableAsking) {
        if(context_ == null) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.e(Countly.TAG, "Can't call this function before init has been called");
                return this;
            }
        }

        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Setting to disable showing of star rating for each app version:[" + disableAsking + "]");
        }

        CountlyStarRating.setStarRatingDisableAskingForEachAppVersion(context_, disableAsking);

        return this;
    }

    /**
     * Set after how many sessions the automatic star rating will be shown for each app version
     * @param limit app session amount for the limit
     * @return Returns link to Countly for call chaining
     */
    public synchronized Countly setAutomaticStarRatingSessionLimit(int limit) {
        if(context_ == null) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.e(Countly.TAG, "Can't call this function before init has been called");
                return this;
            }
        }

        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Setting automatic star rating session limit: [" + limit + "]");
        }
        CountlyStarRating.setStarRatingInitConfig(context_, limit, null, null, null);

        return this;
    }

    /**
     * Returns the session limit set for automatic star rating
     */
    public int getAutomaticStarRatingSessionLimit(){
        if(context_ == null) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.e(Countly.TAG, "Can't call this function before init has been called");
                return -1;
            }
        }

        int sessionLimit = CountlyStarRating.getAutomaticStarRatingSessionLimit(context_);

        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Getting automatic star rating session limit: [" + sessionLimit + "]");
        }

        return sessionLimit;
    }

    /**
     * Returns how many sessions has star rating counted internally for the current apps version
     */
    public int getStarRatingsCurrentVersionsSessionCount(){
        if(context_ == null) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.e(Countly.TAG, "Can't call this function before init has been called");
                return -1;
            }
        }

        int sessionCount = CountlyStarRating.getCurrentVersionsSessionCount(context_);

        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Getting star rating current version session count: [" + sessionCount + "]");
        }

        return sessionCount;
    }

    /**
     * Set the automatic star rating session count back to 0
     */
    public void clearAutomaticStarRatingSessionCount(){
        if(context_ == null) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.e(Countly.TAG, "Can't call this function before init has been called");
                return;
            }
        }

        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Clearing star rating session count");
        }

        CountlyStarRating.clearAutomaticStarRatingSessionCount(context_);
    }

    /**
     * Set if the star rating dialog is cancellable
     * @param isCancellable set this true if it should be cancellable
     */
    public synchronized Countly setIfStarRatingDialogIsCancellable(boolean isCancellable){
        if(context_ == null) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.e(Countly.TAG, "Can't call this function before init has been called");
                return this;
            }
        }

        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Setting if star rating is cancellable: [" + isCancellable + "]");
        }

        CountlyStarRating.setIfRatingDialogIsCancellable(context_, isCancellable);

        return this;
    }

    /**
     * Set the override for forcing to use HTTP POST for all connections to the server
     * @param isItForced the flag for the new status, set "true" if you want it to be forced
     * @deprecated use CountlyConfig during init to set this
     */
    public synchronized Countly setHttpPostForced(boolean isItForced) {

        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Setting if HTTP POST is forced: [" + isItForced + "]");
        }

        isHttpPostForced = isItForced;
        return this;
    }

    /**
     * Get the status of the override for HTTP POST
     * @return return "true" if HTTP POST ir forced
     */
    public boolean isHttpPostForced() {
        return isHttpPostForced;
    }

    private void checkIfDeviceIsAppCrawler(){
        String deviceName = DeviceInfo.getDevice();

        for(int a = 0 ; a < appCrawlerNames.size() ; a++) {
            if(deviceName.equals(appCrawlerNames.get(a))){
                deviceIsAppCrawler = true;
                return;
            }
        }
    }

    /**
     * Set if Countly SDK should ignore app crawlers
     * @param shouldIgnore if crawlers should be ignored
     */
    public synchronized Countly setShouldIgnoreCrawlers(boolean shouldIgnore){
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Setting if should ignore app crawlers: [" + shouldIgnore + "]");
        }
        shouldIgnoreCrawlers = shouldIgnore;
        return this;
    }

    /**
     * Add app crawler device name to the list of names that should be ignored
     * @param crawlerName the name to be ignored
     */
    public void addAppCrawlerName(String crawlerName) {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Adding app crawler name: [" + crawlerName + "]");
        }
        if(crawlerName != null && !crawlerName.isEmpty()) {
            appCrawlerNames.add(crawlerName);
        }
    }

    /**
     * Return if current device is detected as a app crawler
     * @return returns if devices is detected as a app crawler
     */
    public boolean isDeviceAppCrawler() {
        return deviceIsAppCrawler;
    }

    /**
     * Return if the countly sdk should ignore app crawlers
     */
    public boolean ifShouldIgnoreCrawlers(){
        return shouldIgnoreCrawlers;
    }

    /**
     * Returns the device id used by countly for this device
     * @return device ID
     */
    public synchronized String getDeviceID() {
        if(!isInitialized()) {
            throw new IllegalStateException("init must be called before getDeviceID");
        }
        return connectionQueue_.getDeviceId().getId();
    }

    /**
     * Returns the type of the device ID used by countly for this device.
     * @return device ID type
     */
    public synchronized DeviceId.Type getDeviceIDType(){
        if(!isInitialized()) {
            throw new IllegalStateException("init must be called before getDeviceID");
        }

        return connectionQueue_.getDeviceId().getType();
    }

    /**
     * @deprecated use CountlyConfig during init to set this
     * @param shouldAddMetadata
     * @return
     */
    public synchronized Countly setPushIntentAddMetadata(boolean shouldAddMetadata) {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Setting if adding metadata to push intents: [" + shouldAddMetadata + "]");
        }
        addMetadataToPushIntents = shouldAddMetadata;
        return this;
    }

    /**
     * Set if automatic activity tracking should use short names
     * @deprecated use CountlyConfig during init to set this
     * @param shouldUseShortName set true if you want short names
     */
    public synchronized Countly setAutoTrackingUseShortName(boolean shouldUseShortName) {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Setting if automatic view tracking should use short names: [" + shouldUseShortName + "]");
        }
        automaticTrackingShouldUseShortName = shouldUseShortName;
        return this;
    }

    /**
     * Set if attribution should be enabled
     * @param shouldEnableAttribution set true if you want to enable it, set false if you want to disable it
     */
    public synchronized Countly setEnableAttribution(boolean shouldEnableAttribution) {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Setting if attribution should be enabled");
        }
        isAttributionEnabled = shouldEnableAttribution;
        return this;
    }

    /**
     * @deprecated use CountlyConfig during init to set this
     * @param shouldRequireConsent
     * @return
     */
    public synchronized Countly setRequiresConsent(boolean shouldRequireConsent){
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Setting if consent should be required, [" + shouldRequireConsent + "]");
        }
        requiresConsent = shouldRequireConsent;
        return this;
    }

    /**
     * Initiate all things related to consent
     */
    private void initConsent(){
        //groupedFeatures.put("activity", new String[]{CountlyFeatureNames.sessions, CountlyFeatureNames.events, CountlyFeatureNames.views});
        //groupedFeatures.put("interaction", new String[]{CountlyFeatureNames.sessions, CountlyFeatureNames.events, CountlyFeatureNames.views});
    }

    /**
     * Special things needed to be done during setting push consent
     * @param consentValue The value of push consent
     */
    private void doPushConsentSpecialAction(boolean consentValue){
        if(isLoggingEnabled()) {
            Log.d(TAG, "Doing push consent special action: [" + consentValue + "]");
        }
        connectionQueue_.getCountlyStore().setConsentPush(consentValue);
    }

    /**
     * Actions needed to be done for the consent related location erasure
     */
    private void doLocationConsentSpecialErasure(){
        resetLocationValues();
        connectionQueue_.sendLocation();
    }

    /**
     * Check if the given name is a valid feature name
     * @param name the name of the feature to be tested if it is valid
     * @return returns true if value is contained in feature name array
     */
    private boolean isValidFeatureName(String name){
        for(String fName:validFeatureNames){
            if(fName.equals(name)){
                return true;
            }
        }
        return false;
    }

    /**
     * Prepare features into json format
     * @param features the names of features that are about to be changed
     * @param consentValue the value for the new consent
     * @return provided consent changes in json format
     */
    private String formatConsentChanges(String [] features, boolean consentValue){
        StringBuilder preparedConsent = new StringBuilder();
        preparedConsent.append("{");

        for(int a = 0 ; a < features.length ; a++){
            if(a != 0){
                preparedConsent.append(",");
            }
            preparedConsent.append('"');
            preparedConsent.append(features[a]);
            preparedConsent.append('"');
            preparedConsent.append(':');
            preparedConsent.append(consentValue);
        }

        preparedConsent.append("}");

        return preparedConsent.toString();
    }

    /**
     * Group multiple features into a feature group
     * @param groupName name of the consent group
     * @param features array of feature to be added to the consent group
     * @return Returns link to Countly for call chaining
     */
    public synchronized Countly createFeatureGroup(String groupName, String[] features){
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Creating a feature group with the name: [" + groupName + "]");
        }

        groupedFeatures.put(groupName, features);
        return this;
    }

     /**
     * Set the consent of a feature group
     * @param groupName name of the consent group
     * @param isConsentGiven the value that should be set for this consent group
     * @return Returns link to Countly for call chaining
     */
    public synchronized Countly setConsentFeatureGroup(String groupName, boolean isConsentGiven){
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Setting consent for feature group named: [" + groupName + "] with value: [" + isConsentGiven + "]");
        }

        if(!groupedFeatures.containsKey(groupName)){
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.d(Countly.TAG, "Trying to set consent for a unknown feature group: [" + groupName + "]");
            }

            return this;
        }

        setConsent(groupedFeatures.get(groupName), isConsentGiven);

        return this;
    }

    /**
     * Set the consent of a feature
     * @param featureNames feature names for which consent should be changed
     * @param isConsentGiven the consent value that should be set
     * @return Returns link to Countly for call chaining
     */
    public synchronized Countly setConsent(String[] featureNames, boolean isConsentGiven){
        final boolean isInit = isInitialized();//is the SDK initialized

        if(!requiresConsent){
            //if consent is not required, ignore all calls to it
            return this;
        }

        boolean previousSessionsConsent = false;
        if(featureConsentValues.containsKey(CountlyFeatureNames.sessions)){
            previousSessionsConsent = featureConsentValues.get(CountlyFeatureNames.sessions);
        }

        boolean previousLocationConsent = false;
        if(featureConsentValues.containsKey(CountlyFeatureNames.location)){
            previousLocationConsent = featureConsentValues.get(CountlyFeatureNames.location);
        }

        boolean currentSessionConsent = previousSessionsConsent;

        for(String featureName:featureNames) {
            if (Countly.sharedInstance() != null && Countly.sharedInstance().isLoggingEnabled()) {
                Log.d(Countly.TAG, "Setting consent for feature named: [" + featureName + "] with value: [" + isConsentGiven + "]");
            }

            if (!isValidFeatureName(featureName)) {
                Log.d(Countly.TAG, "Given feature: [" + featureName + "] is not a valid name, ignoring it");
                continue;
            }


            featureConsentValues.put(featureName, isConsentGiven);

            //special actions for each feature
            switch (featureName){
                case CountlyFeatureNames.push:
                    if(isInit) {
                        //if the SDK is already initialized, do the special action now
                        doPushConsentSpecialAction(isConsentGiven);
                    } else {
                        //do the special action later
                        delayedPushConsent = isConsentGiven;
                    }
                    break;
                case CountlyFeatureNames.sessions:
                    currentSessionConsent = isConsentGiven;
                    break;
                case CountlyFeatureNames.location:
                    if(previousLocationConsent && !isConsentGiven){
                        //if consent is about to be removed
                        if(isInit){
                            doLocationConsentSpecialErasure();
                        } else {
                            delayedLocationErasure = true;
                        }
                    }
                    break;
            }
        }

        String formattedChanges = formatConsentChanges(featureNames, isConsentGiven);

        if(isInit && (collectedConsentChanges.size() == 0)){
            //if countly is initialized and collected changes are already sent, send consent now
            connectionQueue_.sendConsentChanges(formattedChanges);

            context_.sendBroadcast(new Intent(CONSENT_BROADCAST));

            //if consent has changed and it was set to true
            if((previousSessionsConsent != currentSessionConsent) && currentSessionConsent){
                //if consent was given, we need to begin the session
                if(isBeginSessionSent){
                    //if the first timing for a beginSession call was missed, send it again
                    onStartHelper();
                }
            }
        } else {
            // if countly is not initialized, collect and send it after it is

            collectedConsentChanges.add(formattedChanges);
        }

        return this;
    }

    /**
     * Give the consent to a feature
     * @param featureNames the names of features for which consent should be given
     * @return Returns link to Countly for call chaining
     */
    public synchronized Countly giveConsent(String[] featureNames){
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Giving consent for features named: [" + Arrays.toString(featureNames) + "]");
        }
        setConsent(featureNames, true);

        return this;
    }

    /**
     * Gives consent for all features
     * @return Returns link to Countly for call chaining
     */
    public synchronized Countly giveConsentAll(){
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Giving consent for all features");
        }

        giveConsent(validFeatureNames);

        return this;
    }

    /**
     * Remove the consent of a feature
     * @param featureNames the names of features for which consent should be removed
     * @return Returns link to Countly for call chaining
     */
    public synchronized Countly removeConsent(String[] featureNames){
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Removing consent for features named: [" + Arrays.toString(featureNames) + "]");
        }

        setConsent(featureNames, false);

        return this;
    }

    /**
     * Remove consent for all features
     * @return Returns link to Countly for call chaining
     */
    public synchronized Countly removeConsentAll(){
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Removing consent for all features");
        }

        removeConsent(validFeatureNames);

        return this;
    }


    /**
     * Get the current consent state of a feature
     * @param featureName the name of a feature for which consent should be checked
     * @return the consent value
     */
    public synchronized boolean getConsent(String featureName){
        if(!requiresConsent){
            //return true silently
            return true;
        }

        Boolean returnValue = featureConsentValues.get(featureName);

        if(returnValue == null) {
            if(featureName.equals(CountlyFeatureNames.push)){
                //if the feature is 'push", set it with the value from preferences

                boolean storedConsent = connectionQueue_.getCountlyStore().getConsentPush();

                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.d(Countly.TAG, "Push consent has not been set this session. Setting the value found stored in preferences:[" + storedConsent + "]");
                }

                featureConsentValues.put(featureName, storedConsent);

                returnValue = storedConsent;
            } else {
                returnValue = false;
            }
        }

        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Returning consent for feature named: [" + featureName + "] [" + returnValue + "]");
        }

        return returnValue;
    }

    /**
     * Print the consent values of all features
     * @return Returns link to Countly for call chaining
     */
    public synchronized Countly checkAllConsent(){
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Checking and printing consent for All features");
        }

        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Is consent required? [" + requiresConsent + "]");
        }

        //make sure push consent has been added to the feature map
        getConsent(CountlyFeatureNames.push);

        StringBuilder sb = new StringBuilder();

        for(String key:featureConsentValues.keySet()) {
            sb.append("Feature named [").append(key).append("], consent value: [").append(featureConsentValues.get(key)).append("]\n");
        }

        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, sb.toString());
        }

        return this;
    }

    /**
     * Returns true if any consent has been given
     * @return true - any consent has been given, false - no consent has been given
     */
    protected boolean anyConsentGiven(){
        if (!requiresConsent){
            //no consent required - all consent given
            return true;
        }

        for(String key:featureConsentValues.keySet()) {
            if(featureConsentValues.get(key)){
                return true;
            }
        }
        return false;
    }

    /**
     * Show the rating dialog to the user
     * @param widgetId ID that identifies this dialog
     * @return
     */
    public synchronized Countly showFeedbackPopup(final String widgetId, final String closeButtonText, final Activity activity, final CountlyStarRating.FeedbackRatingCallback callback){
        if (!isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before showFeedbackPopup");
        }

        CountlyStarRating.showFeedbackPopup(widgetId, closeButtonText, activity, this, connectionQueue_, callback);

        return this;
    }

    /**
     * If enable, will automatically download newest remote config_ values on init.
     * @deprecated use CountlyConfig during init to set this
     * @param enabled set true for enabling it
     * @param callback callback called after the update was done
     * @return
     */
    public synchronized Countly setRemoteConfigAutomaticDownload(boolean enabled, RemoteConfig.RemoteConfigCallback callback){
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Setting if remote config_ Automatic download will be enabled, " + enabled);
        }

        remoteConfigAutomaticUpdateEnabled = enabled;
        remoteConfigInitCallback = callback;
        return this;
    }

    /**
     * Manually update remote config_ values
     * @param callback
     */
    public void remoteConfigUpdate(RemoteConfig.RemoteConfigCallback callback){
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Manually calling to updateRemoteConfig");
        }
        if (!isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before remoteConfigUpdate");
        }
        if(!anyConsentGiven()){ return; }
        RemoteConfig.updateRemoteConfigValues(context_, null, null, connectionQueue_, false, callback);
    }

    /**
     * Manual remote config_ update call. Will only update the keys provided.
     * @param keysToInclude
     * @param callback
     */
    public void updateRemoteConfigForKeysOnly(String[] keysToInclude, RemoteConfig.RemoteConfigCallback callback){
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Manually calling to updateRemoteConfig with include keys");
        }
        if (!isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before updateRemoteConfigForKeysOnly");
        }
        if(!anyConsentGiven()){
            if(callback != null){ callback.callback("No consent given"); }
            return;
        }
        if (keysToInclude == null && Countly.sharedInstance().isLoggingEnabled()) { Log.w(Countly.TAG,"updateRemoteConfigExceptKeys passed 'keys to include' array is null"); }
        RemoteConfig.updateRemoteConfigValues(context_, keysToInclude, null, connectionQueue_, false, callback);
    }

    /**
     * Manual remote config_ update call. Will update all keys except the ones provided
     * @param keysToExclude
     * @param callback
     */
    public void updateRemoteConfigExceptKeys(String[] keysToExclude, RemoteConfig.RemoteConfigCallback callback) {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Manually calling to updateRemoteConfig with exclude keys");
        }
        if (!isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before updateRemoteConfigExceptKeys");
        }
        if(!anyConsentGiven()){
            if(callback != null){ callback.callback("No consent given"); }
            return;
        }
        if (keysToExclude == null && Countly.sharedInstance().isLoggingEnabled()) { Log.w(Countly.TAG,"updateRemoteConfigExceptKeys passed 'keys to ignore' array is null"); }
        RemoteConfig.updateRemoteConfigValues(context_, null, keysToExclude, connectionQueue_, false, callback);
    }

    /**
     * Get the stored value for the provided remote config_ key
     * @param key
     * @return
     */
    public Object getRemoteConfigValueForKey(String key){
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Calling remoteConfigValueForKey");
        }
        if (!isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before remoteConfigValueForKey");
        }
        if(!anyConsentGiven()) { return null; }

        return RemoteConfig.getValue(key, context_);
    }

    /**
     * Clear all stored remote config_ values
     */
    public void remoteConfigClearValues(){
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Calling remoteConfigClearValues");
        }
        if (!isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before remoteConfigClearValues");
        }

        RemoteConfig.clearValueStore(context_);
    }

    /**
     * Allows you to add custom header key/value pairs to each request
     * @deprecated use CountlyConfig during init to set this
     */
    public void addCustomNetworkRequestHeaders(Map<String, String> headerValues){
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Calling addCustomNetworkRequestHeaders");
        }
        requestHeaderCustomValues = headerValues;
        if(connectionQueue_ != null){
            connectionQueue_.setRequestHeaderCustomValues(requestHeaderCustomValues);
        }
    }

    /**
     * Deletes all stored requests to server.
     * This includes events, crashes, views, sessions, etc
     * Call only if you don't need that information
     */
    public void flushRequestQueues(){
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Calling flushRequestQueues");
        }

        if (!isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before flushRequestQueues");
        }

        CountlyStore store = connectionQueue_.getCountlyStore();

        int count = 0;

        while (true) {
            final String[] storedEvents = store.connections();
            if (storedEvents == null || storedEvents.length == 0) {
                // currently no data to send, we are done for now
                break;
            }
            //remove stored data
            store.removeConnection(storedEvents[0]);
            count++;
        }

        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "flushRequestQueues removed [" + count + "] requests");
        }
    }

    /**
     * Countly will attempt to fulfill all stored requests on demand
     */
    public void doStoredRequests(){
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Calling doStoredRequests");
        }

        if (!isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before doStoredRequests");
        }

        connectionQueue_.tick();
    }

    public Countly enableTemporaryIdMode(){
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Calling enableTemporaryIdMode");
        }

        changeDeviceId(DeviceId.temporaryCountlyDeviceId);

        return this;
    }

    /**
     * Call to set regex filters that will be used for crash filtering
     * Set null to disable it
     */
    public Countly setCrashFilters(String[] regexFilters){
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Calling setCrashFilters");
        }

        if (!isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before setCrashFilters");
        }

        setCrashFiltersInternal(regexFilters);

        return this;
    }

    private void setCrashFiltersInternal(String[] regexFilters){
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Calling setCrashFiltersInternal");

            if(regexFilters == null){
                Log.d(Countly.TAG, "Provided crash regex filter is null");
            } else {
                Log.d(Countly.TAG, "Setting the following crash regex filters:");
                for (int a = 0; a < regexFilters.length; a++) {
                    Log.d(Countly.TAG, (a + 1) + ") [" + regexFilters[a] + "]");
                }
            }
        }

        crashRegexFilters = regexFilters;

        if(regexFilters == null){
            crashRegexFiltersCompiled = null;
        } else {
            crashRegexFiltersCompiled = new Pattern[crashRegexFilters.length];

            for (int a = 0; a < regexFilters.length; a++) {
                crashRegexFiltersCompiled[a] = Pattern.compile(crashRegexFilters[a], Pattern.DOTALL);
            }
        }
    }

    /**
     * A way to validate created filters
     * @param regexFilters filters you want to validate
     * @param sampleCrash sample crashes you are worrying about
     * @return
     */
    public boolean[] crashFilterTest(String[] regexFilters, String[] sampleCrash){
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Calling crashFilterTest");
        }

        Pattern[] filters = new Pattern[regexFilters.length];

        for(int a = 0 ; a < regexFilters.length ; a++){
            filters[a] = Pattern.compile(regexFilters[a], Pattern.DOTALL);
        }

        boolean[] res = new boolean[sampleCrash.length];

        for(int a = 0 ; a < res.length ; a++){
            res[a] = crashFilterCheck(filters, sampleCrash[a]);
        }

        return res;
    }

    /**
     * Call to check if crash matches one of the filters
     * If it does, the crash should be ignored
     * @param regexFilters
     * @param crash
     * @return true if a match was found
     */
    private boolean crashFilterCheck(Pattern[] regexFilters, String crash){
        if (Countly.sharedInstance().isLoggingEnabled()) {
            int filterCount = 0;
            if(regexFilters != null){
                filterCount = regexFilters.length;
            }
            Log.d(Countly.TAG, "Calling crashFilterCheck, filter count:[" + filterCount + "]");
        }

        if(regexFilters == null){
            //no filter set, nothing to compare against
            return false;
        }

        for (Pattern regexFilter : regexFilters) {
            Matcher m = regexFilter.matcher(crash);
            if (m.matches()) {
                return true;
            }
        }
        return false;
    }

    public Countly setAutomaticViewSegmentation(Map<String, Object> segmentation){
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Calling setAutomaticViewSegmentation");
        }

        if (!isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before setAutomaticViewSegmentation");
        }

        setAutomaticViewSegmentationInternal(segmentation);

        return this;
    }

    private void setAutomaticViewSegmentationInternal(Map<String, Object> segmentation){
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "Calling setAutomaticViewSegmentationInternal");
        }

        if(segmentation != null){
            if(!Countly.checkSegmentationTypes(segmentation)){
                //found a unsupported type, throw exception

                throw new IllegalStateException("Provided a unsupported type for automatic View Segmentation");
            }
        }

        automaticViewSegmentation = segmentation;
    }

    protected static boolean checkSegmentationTypes(Map<String, Object> segmentation){
        if (segmentation == null) {
            throw new IllegalStateException("[checkSegmentationTypes] provided segmentations can't be null!");
        }

        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "[checkSegmentationTypes] Calling checkSegmentationTypes, size:[" + segmentation.size() + "]");
        }

        for (Map.Entry<String, Object> pair : segmentation.entrySet()) {
            String key = pair.getKey();

            if(key == null || key.isEmpty()){
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.d(Countly.TAG, "[checkSegmentationTypes], provided segment with either 'null' or empty string key");
                }
                throw new IllegalStateException("provided segment with either 'null' or empty string key");
            }

            Object value = pair.getValue();

            if(value instanceof Integer){
                //expected
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.v(Countly.TAG, "[checkSegmentationTypes] found INTEGER with key:[" + key + "], value:[" + value + "]");
                }
            } else if(value instanceof Double) {
                //expected
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.v(Countly.TAG, "[checkSegmentationTypes] found DOUBLE with key:[" + key + "], value:[" + value + "]");
                }
            } else if(value instanceof String) {
                //expected
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.v(Countly.TAG, "[checkSegmentationTypes] found STRING with key:[" + key + "], value:[" + value + "]");
                }
            } else {
                //should not get here, it means that the user provided a unsupported type

                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.e(Countly.TAG, "[checkSegmentationTypes] provided unsupported segmentation type:[" + value.getClass().getCanonicalName() + "] with key:[" + key + "], returning [false]");
                }

                return false;
            }
        }

        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "[checkSegmentationTypes] returning [true]");
        }
        return true;
    }

    /**
     * Used for quickly sorting segments into their respective data type
     * @param allSegm
     * @param segmStr
     * @param segmInt
     * @param segmDouble
     */
    protected static synchronized void fillInSegmentation(Map<String, Object> allSegm, HashMap<String, String> segmStr, HashMap<String, Integer> segmInt, HashMap<String, Double> segmDouble) {
        for (Map.Entry<String, Object> pair : allSegm.entrySet()) {
            String key = pair.getKey();
            Object value = pair.getValue();

            if (value instanceof Integer) {
                segmInt.put(key, (Integer) value);
            } else if (value instanceof Double) {
                segmDouble.put(key, (Double) value);
            } else if (value instanceof String) {
                segmStr.put(key, (String) value);
            }
        }
    }

    // for unit testing
    ConnectionQueue getConnectionQueue() { return connectionQueue_; }
    void setConnectionQueue(final ConnectionQueue connectionQueue) { connectionQueue_ = connectionQueue; }
    ExecutorService getTimerService() { return timerService_; }
    EventQueue getEventQueue() { return eventQueue_; }
    void setEventQueue(final EventQueue eventQueue) { eventQueue_ = eventQueue; }
    long getPrevSessionDurationStartTime() { return prevSessionDurationStartTime_; }
    void setPrevSessionDurationStartTime(final long prevSessionDurationStartTime) { prevSessionDurationStartTime_ = prevSessionDurationStartTime; }
    int getActivityCount() { return activityCount_; }
    synchronized boolean getDisableUpdateSessionRequests() { return disableUpdateSessionRequests_; }

    @SuppressWarnings("InfiniteRecursion")
    public void stackOverflow() {
        this.stackOverflow();
    }

    @SuppressWarnings("ConstantConditions")
    public synchronized Countly crashTest(int crashNumber) {

        if (crashNumber == 1){
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.d(Countly.TAG, "Running crashTest 1");
            }

            stackOverflow();

        }else if (crashNumber == 2){

            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.d(Countly.TAG, "Running crashTest 2");
            }

            //noinspection UnusedAssignment,divzero
            @SuppressWarnings("NumericOverflow") int test = 10/0;

        }else if (crashNumber == 3){

            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.d(Countly.TAG, "Running crashTest 3");
            }

            Object[] o = null;
            //noinspection InfiniteLoopStatement
            while (true) { o = new Object[] { o }; }


        }else if (crashNumber == 4){

            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.d(Countly.TAG, "Running crashTest 4");
            }

            throw new RuntimeException("This is a crash");
        }
        else{
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.d(Countly.TAG, "Running crashTest 5");
            }

            String test = null;
            //noinspection ResultOfMethodCallIgnored
            test.charAt(1);
        }
        return Countly.sharedInstance();
    }
}
