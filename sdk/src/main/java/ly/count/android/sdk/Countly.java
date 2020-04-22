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
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * This class is the public API for the Countly Android SDK.
 * Get more details <a href="https://github.com/Countly/countly-sdk-android">here</a>.
 */
@SuppressWarnings("JavadocReference")
public class Countly {

    /**
     * Current version of the Count.ly Android SDK as a displayable string.
     */
    public static final String COUNTLY_SDK_VERSION_STRING = "20.04";
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

    ConnectionQueue connectionQueue_;
    private final ScheduledExecutorService timerService_;
    private ScheduledFuture<?> timerFuture = null;
    EventQueue eventQueue_;
    private int activityCount_;
    boolean disableUpdateSessionRequests_ = false;//todo, move to module after 'setDisableUpdateSessionRequests' is removed
    private boolean enableLogging_;
    private Countly.CountlyMessagingMode messagingMode_;
    Context context_;

    //Internal modules for functionality grouping
    List<ModuleBase> modules = new ArrayList<>();
    ModuleCrash moduleCrash = null;
    ModuleEvents moduleEvents = null;
    ModuleViews moduleViews = null;
    ModuleRatings moduleRatings = null;
    ModuleSessions moduleSessions = null;
    ModuleRemoteConfig moduleRemoteConfig = null;
    ModuleAPM moduleAPM = null;
    ModuleConsent moduleConsent = null;
    ModuleDeviceId moduleDeviceId = null;

    //user data access
    public static UserData userData;

    //view related things
    boolean autoViewTracker = false;//todo, move to module after "setViewTracking" is removed
    boolean automaticTrackingShouldUseShortName = false;//flag for using short names | todo, move to module after setter is removed

    //if set to true, it will automatically download remote configs on module startup
    boolean remoteConfigAutomaticUpdateEnabled = false;//todo, move to module after setter is removed
    RemoteConfigCallback remoteConfigInitCallback = null;//todo, move to module after setter is removed

    //overrides
    private boolean isHttpPostForced = false;//when true, all data sent to the server will be sent using HTTP POST

    //app crawlers
    private boolean shouldIgnoreCrawlers = true;//ignore app crawlers by default
    private boolean deviceIsAppCrawler = false;//by default assume that device is not a app crawler
    @SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
    private final List<String> appCrawlerNames = new ArrayList<>(Arrays.asList("Calypso AppCrawler"));//List against which device name is checked to determine if device is app crawler

    //push related
    private boolean addMetadataToPushIntents = false;// a flag that indicates if metadata should be added to push notification intents

    //internal flags
    private boolean calledAtLeastOnceOnStart = false;//flag for if the onStart function has been called at least once

    //attribution
    protected boolean isAttributionEnabled = true;

    protected boolean isBeginSessionSent = false;

    //custom request header fields
    Map<String, String> requestHeaderCustomValues;

    static long applicationStart = -1;

    //GDPR
    protected boolean requiresConsent = false;

    private final Map<String, Boolean> featureConsentValues = new HashMap<>();
    private final Map<String, String[]> groupedFeatures = new HashMap<>();
    private final List<String> collectedConsentChanges = new ArrayList<>();

    Boolean delayedPushConsent = null;//if this is set, consent for push has to be set before finishing init and sending push changes
    boolean delayedLocationErasure = false;//if location needs to be cleared at the end of init

    private boolean appLaunchDeepLink = true;

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
    protected final String[] validFeatureNames = new String[]{
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
        startTimerService(timerService_, timerFuture, TIMER_DELAY_IN_SECONDS);

        initConsent();
    }

    private void startTimerService(ScheduledExecutorService service, ScheduledFuture<?> previousTimer, long timerDelay) {
        if(previousTimer != null && !previousTimer.isCancelled()){
            previousTimer.cancel(false);
        }

        //minimum delay of 1 second
        //maximum delay if 10 minutes
        if(timerDelay < 1){
            timerDelay = 1;
        } else if(timerDelay > 600) {
            timerDelay = 600;
        }

        timerFuture = service.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                onTimer();
            }
        }, timerDelay, timerDelay, TimeUnit.SECONDS);
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
                                     int starRatingLimit, final CountlyStarRating.RatingCallback starRatingCallback, String starRatingTextTitle, String starRatingTextMessage, String starRatingTextDismiss) {
        CountlyConfig config = new CountlyConfig();
        config.setContext(context).setServerURL(serverURL).setAppKey(appKey).setDeviceId(deviceID)
                .setStarRatingTextTitle(starRatingTextTitle).setStarRatingTextMessage(starRatingTextMessage)
                .setStarRatingTextDismiss(starRatingTextDismiss)
                .setIdMode(idMode).setStarRatingSessionLimit(starRatingLimit).setStarRatingCallback(new StarRatingCallback() {
            @Override
            public void onRate(int rating) {
                if(starRatingCallback != null) {
                    starRatingCallback.onRate(rating);
                }
            }

            @Override
            public void onDismiss() {
                if(starRatingCallback != null) {
                    starRatingCallback.onDismiss();
                }
            }
        });
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

        if (isLoggingEnabled()) {
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
            if (isLoggingEnabled()) {
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

        if (isLoggingEnabled()) {
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

        //set internal context, it's allowed to be changed on the second init call
        context_ = config.context.getApplicationContext();

        // if we get here and eventQueue_ != null, init is being called again with the same values,
        // so there is nothing to do, because we are already initialized with those values
        if (eventQueue_ == null) {
            if (isLoggingEnabled()) {
                Log.d(Countly.TAG, "[Init] About to init internal systems");
            }

            config_ = config;

            if(config.sessionUpdateTimerDelay != null) {
                //if we need to change the timer delay, do that first
                startTimerService(timerService_, timerFuture, config.sessionUpdateTimerDelay);
            }

            final CountlyStore countlyStore;
            if(config.countlyStore != null){
                //we are running a test and using a mock object
                countlyStore = config.countlyStore;
            } else {
                countlyStore = new CountlyStore(config.context);
                config.setCountlyStore(countlyStore);
            }

            //initialise modules
            moduleDeviceId = new ModuleDeviceId(this, config);
            moduleCrash = new ModuleCrash(this, config);
            moduleEvents = new ModuleEvents(this, config);
            moduleViews = new ModuleViews(this, config);
            moduleRatings = new ModuleRatings(this, config);
            moduleSessions = new ModuleSessions(this, config);
            moduleRemoteConfig = new ModuleRemoteConfig(this, config);
            moduleConsent = new ModuleConsent(this, config);
            moduleAPM = new ModuleAPM(this, config);

            modules.clear();
            modules.add(moduleCrash);
            modules.add(moduleEvents);
            modules.add(moduleViews);
            modules.add(moduleRatings);
            modules.add(moduleSessions);
            modules.add(moduleRemoteConfig);
            modules.add(moduleConsent);
            modules.add(moduleAPM);
            modules.add(moduleDeviceId);

            //init other things
            addCustomNetworkRequestHeaders(config.customNetworkRequestHeaders);

            setPushIntentAddMetadata(config.pushIntentAddMetadata);

            setRemoteConfigAutomaticDownload(config.enableRemoteConfigAutomaticDownload, config.remoteConfigCallback);

            setHttpPostForced(config.httpPostForced);

            enableParameterTamperingProtectionInternal(config.tamperingProtectionSalt);

            if(config.eventQueueSizeThreshold != null){
                setEventQueueSizeToSend(config.eventQueueSizeThreshold);
            }

            if(config.publicKeyPinningCertificates != null){
                enablePublicKeyPinning(Arrays.asList(config.publicKeyPinningCertificates));
            }

            if(config.certificatePinningCertificates != null){
                enableCertificatePinning(Arrays.asList(config.certificatePinningCertificates));
            }

            if(config.enableAttribution != null){
                setEnableAttribution(config.enableAttribution);
            }

            //app crawler check
            shouldIgnoreCrawlers = config.shouldIgnoreAppCrawlers;
            if(config.appCrawlerNames != null){
                Collections.addAll(Arrays.asList(config.appCrawlerNames));
            }

            checkIfDeviceIsAppCrawler();

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

            if (isLoggingEnabled()) {
                Log.d(Countly.TAG, "[Init] Currently cached advertising ID [" + countlyStore.getCachedAdvertisingId() + "]");
            }
            AdvertisingIdAdapter.cacheAdvertisingID(config.context, countlyStore);

            deviceIdInstance.init(config.context, countlyStore, true);

            boolean temporaryDeviceIdWasEnabled = deviceIdInstance.temporaryIdModeEnabled();
            if (isLoggingEnabled()) {
                Log.d(Countly.TAG, "[Init] [TemporaryDeviceId] Previously was enabled: [" + temporaryDeviceIdWasEnabled + "]");
            }

            if(temporaryDeviceIdWasEnabled){
                //if we previously we're in temporary ID mode

                if(!config.temporaryDeviceIdEnabled || customIDWasProvided){
                    //if we don't set temporary device ID mode or
                    //a custom device ID is explicitly provided
                    //that means we have to exit temporary ID mode

                    if (isLoggingEnabled()) {
                        Log.d(Countly.TAG, "[Init] [TemporaryDeviceId] Decided we have to exit temporary device ID mode, mode enabled: [" + config.temporaryDeviceIdEnabled + "], custom Device ID Set: [" + customIDWasProvided + "]");
                    }
                } else {
                    //we continue to stay in temporary ID mode
                    //no changes need to happen

                    if (isLoggingEnabled()) {
                        Log.d(Countly.TAG, "[Init] [TemporaryDeviceId] Decided to stay in temporary ID mode");
                    }
                }
            } else {
                if(config.temporaryDeviceIdEnabled && config.deviceID == null){
                    //temporary device ID mode is enabled and
                    //no custom device ID is provided
                    //we can safely enter temporary device ID mode

                    if (isLoggingEnabled()) {
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
                if (isLoggingEnabled()) {
                    Log.d(Countly.TAG, "[Init] Trying to enter temporary ID mode");
                }
                //if we are doing temporary ID, make sure it is applied
                //if it's not, change ID to it
                if(!deviceIdInstance.temporaryIdModeEnabled()){
                    if (isLoggingEnabled()) {
                        Log.d(Countly.TAG, "[Init] Temporary ID mode was not enabled, entering it");
                    }
                    //temporary ID is not set
                    changeDeviceId(DeviceId.temporaryCountlyDeviceId);
                } else {
                    if (isLoggingEnabled()) {
                        Log.d(Countly.TAG, "[Init] Temporary ID mode was enabled previously, nothing to enter");
                    }
                }

            }

            //do star rating related things
            if(getConsent(CountlyFeatureNames.starRating)) {
                moduleRatings.registerAppSession(config.context, countlyStore, moduleRatings.starRatingCallback_);
            }

            //do location related things
            if(config.disableLocation) {
                disableLocation();
            } else {
                //if we are not disabling location, check for other set values
                if(config.locationIpAddress != null || config.locationLocation != null || config.locationCity != null || config.locationCountyCode != null) {
                    setLocation(config.locationCountyCode, config.locationCity, config.locationLocation, config.locationIpAddress);
                }
            }

            //update remote config_ values if automatic update is enabled and we are not in temporary id mode
            if(remoteConfigAutomaticUpdateEnabled && anyConsentGiven() && !doingTemporaryIdMode){
                if (isLoggingEnabled()) {
                    Log.d(Countly.TAG, "[Init] Automatically updating remote config values");
                }
                moduleRemoteConfig.updateRemoteConfigValues(null, null, connectionQueue_, false, remoteConfigInitCallback);
            }

            //set global application listeners
            if(config.application != null) {
                config.application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                    @Override
                    public void onActivityCreated(Activity activity, Bundle bundle) {
                        if (isLoggingEnabled()) {
                            String actName = activity.getClass().getSimpleName();
                            Log.d(Countly.TAG, "[Countly] onActivityCreated, " + actName);
                        }
                        for (ModuleBase module:modules) {
                            module.callbackOnActivityCreated(activity);
                        }
                    }

                    @Override
                    public void onActivityStarted(Activity activity) {
                        if (isLoggingEnabled()) {
                            String actName = activity.getClass().getSimpleName();
                            Log.d(Countly.TAG, "[Countly] onActivityStarted, " + actName);
                        }
                        for (ModuleBase module:modules) {
                            module.callbackOnActivityStarted(activity);
                        }
                    }

                    @Override
                    public void onActivityResumed(Activity activity) {
                        if (isLoggingEnabled()) {
                            String actName = activity.getClass().getSimpleName();
                            Log.d(Countly.TAG, "[Countly] onActivityResumed, " + actName);
                        }
                        for (ModuleBase module:modules) {
                            module.callbackOnActivityResumed(activity);
                        }
                    }

                    @Override
                    public void onActivityPaused(Activity activity) {
                        if (isLoggingEnabled()) {
                            String actName = activity.getClass().getSimpleName();
                            Log.d(Countly.TAG, "[Countly] onActivityPaused, " + actName);
                        }
                        for (ModuleBase module:modules) {
                            module.callbackOnActivityPaused(activity);
                        }
                    }

                    @Override
                    public void onActivityStopped(Activity activity) {
                        if (isLoggingEnabled()) {
                            String actName = activity.getClass().getSimpleName();
                            Log.d(Countly.TAG, "[Countly] onActivityStopped, " + actName);
                        }
                        for (ModuleBase module:modules) {
                            module.callbackOnActivityStopped(activity);
                        }
                    }

                    @Override
                    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
                        if (isLoggingEnabled()) {
                            String actName = activity.getClass().getSimpleName();
                            Log.d(Countly.TAG, "[Countly] onActivitySaveInstanceState, " + actName);
                        }
                        for (ModuleBase module:modules) {
                            module.callbackOnActivitySaveInstanceState(activity);
                        }
                    }

                    @Override
                    public void onActivityDestroyed(Activity activity) {
                        if (isLoggingEnabled()) {
                            String actName = activity.getClass().getSimpleName();
                            Log.d(Countly.TAG, "[Countly] onActivityDestroyed, " + actName);
                        }
                        for (ModuleBase module:modules) {
                            module.callbackOnActivityDestroyed(activity);
                        }
                    }
                });
/*
                config.application.registerComponentCallbacks(new ComponentCallbacks() {
                    @Override
                    public void onConfigurationChanged(Configuration configuration) {

                    }

                    @Override
                    public void onLowMemory() {

                    }
                });
 */
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

            if (isLoggingEnabled()) {
                Log.d(Countly.TAG, "[Init] Countly is initialized with the current consent state:");
                checkAllConsent();
            }
        }

        //check for previous native crash dumps
        if(config.checkForNativeCrashDumps){
            //flag so that this can be turned off during testing
            moduleCrash.checkForNativeCrashDumps(config.context);
        }


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
        if (isLoggingEnabled()) {
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
        activityCount_ = 0;

        for (ModuleBase module:modules) {
            module.halt();
        }
        modules.clear();

        moduleCrash = null;
        moduleViews = null;
        moduleEvents = null;
        moduleRatings = null;
        moduleSessions = null;
        moduleRemoteConfig = null;
        moduleConsent = null;
        moduleAPM = null;
        moduleDeviceId = null;
    }

    /**
     * Tells the Countly SDK that an Activity has started. Since Android does not have an
     * easy way to determine when an application instance starts and stops, you must call this
     * method from every one of your Activity's onStart methods for accurate application
     * session tracking.
     * @throws IllegalStateException if Countly SDK has not been initialized
     */
    public synchronized void onStart(Activity activity) {
        if (isLoggingEnabled()) {
            String activityName = "NULL ACTIVITY PROVIDED";
            if(activity != null){
                activityName = activity.getClass().getSimpleName();
            }
            Log.d(Countly.TAG, "Countly onStart called, name:[" + activityName + "], [" + activityCount_ + "] -> [" + (activityCount_ + 1) + "] activities now open");
        }

        appLaunchDeepLink = false;
        if (!isInitialized()) {
            throw new IllegalStateException("init must be called before onStart");
        }

        ++activityCount_;
        if (activityCount_ == 1 && !moduleSessions.manualSessionControlEnabled) {
            //if we open the first activity
            //and we are not using manual session control,
            //begin a session

            moduleSessions.beginSessionInternal();
        }

        //check if there is an install referrer data
        String referrer = ReferrerReceiver.getReferrer(context_);
        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Checking referrer: " + referrer);
        }
        if(referrer != null){
            connectionQueue_.sendReferrerData(referrer);
            ReferrerReceiver.deleteReferrer(context_);
        }

        CrashDetails.inForeground();

        for (ModuleBase module:modules) {
            module.onActivityStarted(activity);
        }

        calledAtLeastOnceOnStart = true;
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
        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Countly onStop called, [" + activityCount_ + "] -> [" + (activityCount_ - 1) + "] activities now open");
        }

        if (!isInitialized()) {
            throw new IllegalStateException("init must be called before onStop");
        }
        if (activityCount_ == 0) {
            throw new IllegalStateException("must call onStart before onStop");
        }

        --activityCount_;
        if (activityCount_ == 0 && !moduleSessions.manualSessionControlEnabled) {
            // if we don't use manual session control
            // Called when final Activity is stopped.
            // Sends an end session event to the server, also sends any unsent custom events.
            moduleSessions.endSessionInternal(null);
        }

        CrashDetails.inBackground();

        for (ModuleBase module:modules) {
            module.onActivityStopped();
        }
    }

    public synchronized void onConfigurationChanged(Configuration newConfig){
        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Calling [onConfigurationChanged]");
        }
        if (!isInitialized()) {
            throw new IllegalStateException("init must be called before onConfigurationChanged");
        }

        for (ModuleBase module:modules) {
            module.onConfigurationChanged(newConfig);
        }
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
    public void changeDeviceIdWithoutMerge(DeviceId.Type type, String deviceId) {
        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Calling [changeDeviceIdWithoutMerge] with type and ID");
        }

        if (!isInitialized()) {
            throw new IllegalStateException("init must be called before changeDeviceIdWithoutMerge");
        }

        moduleDeviceId.changeDeviceIdWithoutMerge(type, deviceId);
    }

    /**
     * Changes current device id to the one specified in parameter. Merges user profile with new id
     * (if any) with old profile.
     * @param deviceId new device id
     */
    public void changeDeviceIdWithMerge(String deviceId) {
        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Calling [changeDeviceIdWithMerge] only with ID");
        }
        if (!isInitialized()) {
            throw new IllegalStateException("init must be called before changeDeviceIdWithMerge");
        }

        moduleDeviceId.changeDeviceIdWithMerge(deviceId);
    }

    /**
     * Changes current device id type to the one specified in parameter. Closes current session and
     * reopens new one with new id. Doesn't merge user profiles on the server
     * @param type Device ID type to change to
     * @param deviceId Optional device ID for a case when type = DEVELOPER_SPECIFIED
     * @deprecated use 'changeDeviceIdWithoutMerge'
     */
    public void changeDeviceId(DeviceId.Type type, String deviceId) {
        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Calling [changeDeviceId] with type and ID");
        }

        if (!isInitialized()) {
            throw new IllegalStateException("init must be called before changeDeviceId");
        }

        moduleDeviceId.changeDeviceIdWithoutMerge(type, deviceId);
    }

    /**
     * Changes current device id to the one specified in parameter. Merges user profile with new id
     * (if any) with old profile.
     * @param deviceId new device id
     * @deprecated use 'changeDeviceIdWithMerge'
     */
    public void changeDeviceId(String deviceId) {
        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Calling [changeDeviceId] only with ID");
        }
        if (!isInitialized()) {
            throw new IllegalStateException("init must be called before changeDeviceId");
        }

        moduleDeviceId.changeDeviceIdWithMerge(deviceId);
    }

    /**
     * Records a custom event with no segmentation values, a count of one and a sum of zero.
     * @param key name of the custom event, required, must not be the empty string
     * @throws IllegalStateException if Countly SDK has not been initialized
     * @throws IllegalArgumentException if key is null or empty
     * @deprecated record events through 'Countly.sharedInstance().events()'
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
     * @deprecated record events through 'Countly.sharedInstance().events()'
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
     * @deprecated record events through 'Countly.sharedInstance().events()'
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
     * @deprecated record events through 'Countly.sharedInstance().events()'
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
     * @deprecated record events through 'Countly.sharedInstance().events()'
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
     * @deprecated record events through 'Countly.sharedInstance().events()'
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
     * @deprecated record events through 'Countly.sharedInstance().events()'
     */
    public synchronized void recordEvent(final String key, final Map<String, String> segmentation, final Map<String, Integer> segmentationInt, final Map<String, Double> segmentationDouble, final int count, final double sum, final double dur) {
        if (!isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before recordEvent");
        }

        Map<String, Object> segmentationGroup = new HashMap<>();
        if(segmentation != null) {
            segmentationGroup.putAll(segmentation);
        }

        if(segmentationInt != null) {
            segmentationGroup.putAll(segmentationInt);
        }

        if(segmentationDouble != null) {
            segmentationGroup.putAll(segmentationDouble);
        }

        events().recordEvent(key, segmentationGroup, count, sum, dur);
    }

    /**
     * Enable or disable automatic view tracking
     * @param enable boolean for the state of automatic view tracking
     * @deprecated use CountlyConfig during init to set this
     * @return Returns link to Countly for call chaining
     */
    public synchronized Countly setViewTracking(boolean enable){
        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Enabling automatic view tracking");
        }
        autoViewTracker = enable;
        return this;
    }

    /**
     * Check state of automatic view tracking
     * @return boolean - true if enabled, false if disabled
     * @deprecated use 'Countly.sharedInstance().views().isAutomaticViewTrackingEnabled()'
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
     * @deprecated use 'Countly.sharedInstance().views().recordView()'
     */
    public synchronized Countly recordView(String viewName) {
        if (!isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before recordView");
        }

        return recordView(viewName, null);
    }

    /**
     *  Record a view manually, without automatic tracking
     * or track view that is not automatically tracked
     * like fragment, Message box or transparent Activity
     * @param viewName String - name of the view
     * @param viewSegmentation Map<String, Object> - segmentation that will be added to the view, set 'null' if none should be added
     * @return Returns link to Countly for call chaining
     * @deprecated use 'Countly.sharedInstance().views().recordView()'
     */
    public synchronized Countly recordView(String viewName, Map<String, Object> viewSegmentation) {
        if (!isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before recordView");
        }

        return moduleViews.recordViewInternal(viewName, viewSegmentation);
    }

    /**
     * Disable sending of location data
     * @return Returns link to Countly for call chaining
     */
    public synchronized Countly disableLocation() {
        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Disabling location");
        }

        if (!isInitialized()) {
            if (isLoggingEnabled()) {
                Log.w(Countly.TAG, "The use of this before init is deprecated, use CountlyConfig instead of this");
            }
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
        connectionQueue_.getCountlyStore().setLocationGpsCoordinates("");
        connectionQueue_.getCountlyStore().setLocationIpAddress("");
    }

    /**
     * Set location parameters. If they are set before begin_session, they will be sent as part of it.
     * If they are set after, then they will be sent as a separate request.
     * If this is called after disabling location, it will enable it.
     * @param country_code ISO Country code for the user's country
     * @param city Name of the user's city
     * @param gpsCoordinates comma separate lat and lng values. For example, "56.42345,123.45325"
     * @return Returns link to Countly for call chaining
     */
    public synchronized Countly setLocation(String country_code, String city, String gpsCoordinates, String ipAddress){
        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Setting location parameters");
        }

        if (!isInitialized()) {
            if (isLoggingEnabled()) {
                Log.w(Countly.TAG, "The use of this before init is deprecated, use CountlyConfig instead of this");
            }
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

        if(gpsCoordinates != null){
            connectionQueue_.getCountlyStore().setLocationGpsCoordinates(gpsCoordinates);
        }

        if(ipAddress != null){
            connectionQueue_.getCountlyStore().setLocationIpAddress(ipAddress);
        }

        if((country_code == null && city != null) || (city == null && country_code != null)) {
            if (isLoggingEnabled()) {
                Log.w(Countly.TAG, "In \"setLocation\" both city and country code need to be set at the same time to be sent");
            }
        }

        if(country_code != null || city != null || gpsCoordinates != null || ipAddress != null){
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
     * @deprecated set this through CountlyConfig during init
     */
    public synchronized Countly setCustomCrashSegments(Map<String, String> segments) {
        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Calling setCustomCrashSegments");
        }

        if(segments == null) {
            return this;
        }

        Map<String, Object> segm = new HashMap<>();
        segm.putAll(segments);

        setCustomCrashSegmentsInternal(segm);

        return this;
    }

    /**
     * Sets custom segments to be reported with crash reports
     * In custom segments you can provide any string key values to segments crashes by
     * @param segments Map&lt;String, Object&gt; key segments and their values
     * todo move to module after 'setCustomCrashSegments' is removed
     */
    synchronized void setCustomCrashSegmentsInternal(Map<String, Object> segments) {
        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleCrash] Calling setCustomCrashSegmentsInternal");
        }

        if(!getConsent(Countly.CountlyFeatureNames.crashes)){
            return;
        }

        if(segments != null) {
            Utils.removeKeysFromMap(segments, ModuleEvents.reservedSegmentationKeys);
            Utils.removeUnsupportedDataTypes(segments);
            CrashDetails.setCustomSegments(segments);
        }
    }

    /**
     * Add crash breadcrumb like log record to the log that will be send together with crash report
     * @param record String a bread crumb for the crash report
     * @return Returns link to Countly for call chaining
     * @deprecated use crashes().addCrashBreadcrumb
     */
    public synchronized Countly addCrashBreadcrumb(String record) {
        return crashes().addCrashBreadcrumb(record);
    }

    /**
     * Log handled exception to report it to server as non fatal crash
     * @param exception Exception to log
     * @return Returns link to Countly for call chaining
     * @deprecated use crashes().recordHandledException
     */
    public synchronized Countly recordHandledException(Exception exception) {
        return moduleCrash.recordExceptionInternal(exception, true);
    }

    /**
     * Log handled exception to report it to server as non fatal crash
     * @param exception Throwable to log
     * @return Returns link to Countly for call chaining
     * @deprecated use crashes().recordHandledException
     */
    public synchronized Countly recordHandledException(Throwable exception) {
        return moduleCrash.recordExceptionInternal(exception, true);
    }

    /**
     * Log unhandled exception to report it to server as fatal crash
     * @param exception Exception to log
     * @return Returns link to Countly for call chaining
     * @deprecated use crashes().recordUnhandledException
     */
    public synchronized Countly recordUnhandledException(Exception exception) {
        return moduleCrash.recordExceptionInternal(exception, false);
    }

    /**
     * Log unhandled exception to report it to server as fatal crash
     * @param exception Throwable to log
     * @return Returns link to Countly for call chaining
     * @deprecated use crashes().recordUnhandledException
     */
    public synchronized Countly recordUnhandledException(Throwable exception) {
        return moduleCrash.recordExceptionInternal(exception, false);
    }

    /**
     * Enable crash reporting to send unhandled crash reports to server
     * @deprecated use CountlyConfig during init to set this
     * @return Returns link to Countly for call chaining
     */
    public synchronized Countly enableCrashReporting() {
        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Enabling unhandled crash reporting");
        }
        //get default handler
        final Thread.UncaughtExceptionHandler oldHandler = Thread.getDefaultUncaughtExceptionHandler();

        Thread.UncaughtExceptionHandler handler = new Thread.UncaughtExceptionHandler() {

            @Override
            public void uncaughtException(Thread t, Throwable e) {
                if (isLoggingEnabled()) {
                    Log.d(Countly.TAG, "Uncaught crash handler triggered");
                }
                if(getConsent(CountlyFeatureNames.crashes)){

                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    e.printStackTrace(pw);

                    //add other threads
                    if(moduleCrash.recordAllThreads) {
                        moduleCrash.addAllThreadInformationToCrash(pw);
                    }

                    String exceptionString = sw.toString();

                    //check if it passes the crash filter
                    if(!moduleCrash.crashFilterCheck(exceptionString)) {
                        Countly.sharedInstance().connectionQueue_.sendCrashReport(exceptionString, false, false);
                    }
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
     * @deprecated use events().startEvent
     */
    public synchronized boolean startEvent(final String key) {
        if (!isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before recordEvent");
        }

        return events().startEvent(key);
    }

    /**
     * End timed event with a specified key
     * @param key name of the custom event, required, must not be the empty string or null
     * @return true if event with this key has been previously started, false otherwise
     * @deprecated use events().endEvent
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
     * @deprecated use events().endEvent
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
     * @deprecated use events().endEvent
     */
    public synchronized boolean endEvent(final String key, final Map<String, String> segmentation, final Map<String, Integer> segmentationInt, final Map<String, Double> segmentationDouble, final int count, final double sum) {
        if (!isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before recordEvent");
        }

        Map<String, Object> segmentationGroup = new HashMap<>();
        if(segmentation != null) {
            segmentationGroup.putAll(segmentation);
        }

        if(segmentationInt != null) {
            segmentationGroup.putAll(segmentationInt);
        }

        if(segmentationDouble != null) {
            segmentationGroup.putAll(segmentationDouble);
        }

        return events().endEvent(key, segmentationGroup, count, sum);
    }

    /**
     * Disable periodic session time updates.
     * By default, Countly will send a request to the server each 30 seconds with a small update
     * containing session duration time. This method allows you to disable such behavior.
     * Note that event updates will still be sent every 10 events or 30 seconds after event recording.
     * @param disable whether or not to disable session time updates
     * @return Countly instance for easy method chaining
     * @deprecated set through countlyConfig
     */
    public synchronized Countly setDisableUpdateSessionRequests(final boolean disable) {
        if (isLoggingEnabled()) {
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
        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Enabling logging");
        }
        enableLogging_ = enableLogging;
        return this;
    }

    /**
     * Check if logging has been enabled internally in the SDK
     * @return true means "yes"
     */
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
        if (isLoggingEnabled()) {
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

    /**
     *
     * @param size
     * @return
     * @deprecated use countly config to set this
     */
    public synchronized Countly setEventQueueSizeToSend(int size) {
        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Setting event queue size: [" + size + "]");
        }

        if(size < 1){
            if (isLoggingEnabled()) {
                Log.d(Countly.TAG, "[setEventQueueSizeToSend] queue size can't be less than zero");
            }
            size = 1;
        }

        EVENT_QUEUE_SIZE_THRESHOLD = size;
        return this;
    }

    public static void onCreate(Activity activity) {
        Intent launchIntent = activity.getPackageManager().getLaunchIntentForPackage(activity.getPackageName());

        if (sharedInstance().isLoggingEnabled()) {

            String mainClassName = "[VALUE NULL]";
            if(launchIntent != null && launchIntent.getComponent() != null){
                mainClassName = launchIntent.getComponent().getClassName();
            }

            Log.d(Countly.TAG, "Activity created: " + activity.getClass().getName() + " ( main is " + mainClassName + ")");
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
     * Send events if any of them are stored
     */
    protected void sendEventsIfExist() {
        if (eventQueue_.size() > 0) {
            connectionQueue_.recordEvents(eventQueue_.events());
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
        if (isLoggingEnabled()) {
            Log.v(Countly.TAG, "[onTimer] Calling heartbeat, Activity count:[" + activityCount_ + "]");
        }

        if (isInitialized()) {
            final boolean hasActiveSession = activityCount_ > 0;
            if (hasActiveSession) {
                if(!moduleSessions.manualSessionControlEnabled) {
                    moduleSessions.updateSessionInternal();
                }

                if (eventQueue_.size() > 0) {
                    connectionQueue_.recordEvents(eventQueue_.events());
                }
            }

            connectionQueue_.tick();
        }
    }

    /**
     * Allows public key pinning.
     * Supply list of SSL certificates (base64-encoded strings between "-----BEGIN CERTIFICATE-----" and "-----END CERTIFICATE-----" without end-of-line)
     * along with server URL starting with "https://". Countly will only accept connections to the server
     * if public key of SSL certificate provided by the server matches one provided to this method or by {@link #enableCertificatePinning(List)}.
     * @param certificates List of SSL public keys
     * @return Countly instance
     * @deprecated set this through CountlyConfig
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
     * @deprecated set this through CountlyConfig
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
     * @deprecated call this trough 'Countly.sharedInstance().remoteConfig()'
     */
    public void showStarRating(Activity activity, final CountlyStarRating.RatingCallback callback){
        if(!isInitialized()) {
            if (isLoggingEnabled()) {
                Log.e(Countly.TAG, "Can't call this function before init has been called");
                return;
            }
        }

        if(callback == null) {
            ratings().showStarRating(activity, null);
        } else {
            ratings().showStarRating(activity, new StarRatingCallback() {
                @Override
                public void onRate(int rating) {
                    callback.onRate(rating);
                }

                @Override
                public void onDismiss() {
                    callback.onDismiss();
                }
            });
        }


    }

    /**
     * Set's the text's for the different fields in the star rating dialog. Set value null if for some field you want to keep the old value
     * @param starRatingTextTitle dialog's title text
     * @param starRatingTextMessage dialog's message text
     * @param starRatingTextDismiss dialog's dismiss buttons text
     * @deprecated use CountlyConfig during init to set this
     */
    public synchronized Countly setStarRatingDialogTexts(String starRatingTextTitle, String starRatingTextMessage, String starRatingTextDismiss) {
        if(!isInitialized()) {
            if (isLoggingEnabled()) {
                Log.e(Countly.TAG, "Can't call this function before init has been called");
                return this;
            }
        }

        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Setting star rating texts");
        }

        moduleRatings.setStarRatingInitConfig(connectionQueue_.getCountlyStore(), -1, starRatingTextTitle, starRatingTextMessage, starRatingTextDismiss);

        return this;
    }

    /**
     * Set if the star rating should be shown automatically
     * @param IsShownAutomatically set it true if you want to show the app star rating dialog automatically for each new version after the specified session amount
     * @deprecated use CountlyConfig during init to set this
     */
    public synchronized Countly setIfStarRatingShownAutomatically(boolean IsShownAutomatically) {
        if(!isInitialized()) {
            if (isLoggingEnabled()) {
                Log.e(Countly.TAG, "Can't call this function before init has been called");
                return this;
            }
        }

        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Setting to show star rating automatically: [" + IsShownAutomatically + "]");
        }

        moduleRatings.setShowDialogAutomatically(connectionQueue_.getCountlyStore(), IsShownAutomatically);

        return this;
    }

    /**
     * Set if the star rating is shown only once per app lifetime
     * @param disableAsking set true if you want to disable asking the app rating for each new app version (show it only once per apps lifetime)
     * @deprecated use CountlyConfig during init to set this
     */
    public synchronized Countly setStarRatingDisableAskingForEachAppVersion(boolean disableAsking) {
        if(!isInitialized()) {
            if (isLoggingEnabled()) {
                Log.e(Countly.TAG, "Can't call this function before init has been called");
                return this;
            }
        }

        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Setting to disable showing of star rating for each app version:[" + disableAsking + "]");
        }

        moduleRatings.setStarRatingDisableAskingForEachAppVersion(connectionQueue_.getCountlyStore(), disableAsking);

        return this;
    }

    /**
     * Set after how many sessions the automatic star rating will be shown for each app version
     * @param limit app session amount for the limit
     * @return Returns link to Countly for call chaining
     * @deprecated use CountlyConfig during init to set this
     */
    public synchronized Countly setAutomaticStarRatingSessionLimit(int limit) {
        if(!isInitialized()) {
            if (isLoggingEnabled()) {
                Log.e(Countly.TAG, "Can't call this function before init has been called");
                return this;
            }
        }

        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Setting automatic star rating session limit: [" + limit + "]");
        }
        moduleRatings.setStarRatingInitConfig(connectionQueue_.getCountlyStore(), limit, null, null, null);

        return this;
    }

    /**
     * Returns the session limit set for automatic star rating
     * @deprecated use 'Countly.sharedInstance().ratings().getAutomaticStarRatingSessionLimit()'
     */
    public int getAutomaticStarRatingSessionLimit(){
        if(!isInitialized()) {
            if (isLoggingEnabled()) {
                Log.e(Countly.TAG, "Can't call this function before init has been called");
                return -1;
            }
        }

        int sessionLimit = ModuleRatings.getAutomaticStarRatingSessionLimitInternal(connectionQueue_.getCountlyStore());

        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Getting automatic star rating session limit: [" + sessionLimit + "]");
        }

        return sessionLimit;
    }

    /**
     * Returns how many sessions has star rating counted internally for the current apps version
     * @deprecated use 'Countly.sharedInstance().ratings().getCurrentVersionsSessionCount()'
     */
    public int getStarRatingsCurrentVersionsSessionCount(){
        if(!isInitialized()) {
            if (isLoggingEnabled()) {
                Log.e(Countly.TAG, "Can't call this function before init has been called");
                return -1;
            }
        }

        return ratings().getCurrentVersionsSessionCount();
    }

    /**
     * Set the automatic star rating session count back to 0
     * @deprecated use 'Countly.sharedInstance().ratings().getCurrentVersionsSessionCount()' to get achieve this
     */
    public void clearAutomaticStarRatingSessionCount(){
        if(!isInitialized()) {
            if (isLoggingEnabled()) {
                Log.e(Countly.TAG, "Can't call this function before init has been called");
                return;
            }
        }

        ratings().clearAutomaticStarRatingSessionCount();
    }

    /**
     * Set if the star rating dialog is cancellable
     * @param isCancellable set this true if it should be cancellable
     * @deprecated use CountlyConfig during init to set this
     */
    public synchronized Countly setIfStarRatingDialogIsCancellable(boolean isCancellable){
        if(!isInitialized()) {
            if (isLoggingEnabled()) {
                Log.e(Countly.TAG, "Can't call this function before init has been called");
                return this;
            }
        }

        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Setting if star rating is cancellable: [" + isCancellable + "]");
        }

        moduleRatings.setIfRatingDialogIsCancellableInternal(connectionQueue_.getCountlyStore(), isCancellable);

        return this;
    }

    /**
     * Set the override for forcing to use HTTP POST for all connections to the server
     * @param isItForced the flag for the new status, set "true" if you want it to be forced
     * @deprecated use CountlyConfig during init to set this
     */
    public synchronized Countly setHttpPostForced(boolean isItForced) {
        if (isLoggingEnabled()) {
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
     * @deprecated use CountlyConfig to set this
     */
    public synchronized Countly setShouldIgnoreCrawlers(boolean shouldIgnore){
        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Setting if should ignore app crawlers: [" + shouldIgnore + "]");
        }
        shouldIgnoreCrawlers = shouldIgnore;
        return this;
    }

    /**
     * Add app crawler device name to the list of names that should be ignored
     * @param crawlerName the name to be ignored
     * @deprecated use CountlyConfig to set this
     */
    public void addAppCrawlerName(String crawlerName) {
        if (isLoggingEnabled()) {
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
        if(!isInitialized()) {
            throw new IllegalStateException("init must be called before ifShouldIgnoreCrawlers");
        }
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
        if (isLoggingEnabled()) {
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
        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Setting if automatic view tracking should use short names: [" + shouldUseShortName + "]");
        }
        automaticTrackingShouldUseShortName = shouldUseShortName;
        return this;
    }

    /**
     * Set if attribution should be enabled
     * @param shouldEnableAttribution set true if you want to enable it, set false if you want to disable it
     * @deprecated use CountlyConfig to set this
     */
    public synchronized Countly setEnableAttribution(boolean shouldEnableAttribution) {
        if (isLoggingEnabled()) {
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
        if (isLoggingEnabled()) {
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
     * @deprecated use 'Countly.sharedInstance().consent().createFeatureGroup'
     */
    public synchronized Countly createFeatureGroup(String groupName, String[] features){
        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Creating a feature group with the name: [" + groupName + "]");
        }

        if(isLoggingEnabled() && !isInitialized()){
            Log.w(Countly.TAG, "Calling this before initialising the SDK is deprecated!");
        }

        groupedFeatures.put(groupName, features);
        return this;
    }

     /**
     * Set the consent of a feature group
     * @param groupName name of the consent group
     * @param isConsentGiven the value that should be set for this consent group
     * @return Returns link to Countly for call chaining
     * @deprecated use 'Countly.sharedInstance().consent().setConsent'
     */
    public synchronized Countly setConsentFeatureGroup(String groupName, boolean isConsentGiven){
        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Setting consent for feature group named: [" + groupName + "] with value: [" + isConsentGiven + "]");
        }

        if(isLoggingEnabled() && !isInitialized()){
            Log.w(Countly.TAG, "Calling this before initialising the SDK is deprecated!");
        }

        if(!groupedFeatures.containsKey(groupName)){
            if (isLoggingEnabled()) {
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
     * @deprecated use 'Countly.sharedInstance().consent().setConsent' or set consent through CountlyConfig
     */
    public synchronized Countly setConsent(String[] featureNames, boolean isConsentGiven){
        if(isLoggingEnabled() && !isInitialized()){
            Log.w(Countly.TAG, "Calling this before initialising the SDK is deprecated!");
        }

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
            if (Countly.sharedInstance() != null && isLoggingEnabled()) {
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
                    if(!moduleSessions.manualSessionControlEnabled){
                        moduleSessions.beginSessionInternal();
                    }
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
     * @deprecated use 'Countly.sharedInstance().consent().giveConsent(featureNames)' or set consent through CountlyConfig
     */
    public synchronized Countly giveConsent(String[] featureNames){
        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Giving consent for features named: [" + Arrays.toString(featureNames) + "]");
        }

        if(isLoggingEnabled() && !isInitialized()){
            Log.w(Countly.TAG, "Calling this before initialising the SDK is deprecated!");
        }

        setConsent(featureNames, true);

        return this;
    }

    /**
     * Remove the consent of a feature
     * @param featureNames the names of features for which consent should be removed
     * @return Returns link to Countly for call chaining
     * @deprecated use 'Countly.sharedInstance().consent().removeConsent(featureNames)'
     */
    public synchronized Countly removeConsent(String[] featureNames){
        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Removing consent for features named: [" + Arrays.toString(featureNames) + "]");
        }

        if(isLoggingEnabled() && !isInitialized()){
            Log.w(Countly.TAG, "Calling this before initialising the SDK is deprecated!");
        }

        setConsent(featureNames, false);

        return this;
    }

    /**
     * Remove consent for all features
     * @return Returns link to Countly for call chaining
     * @deprecated use 'Countly.sharedInstance().consent().removeConsentAll()'
     */
    public synchronized Countly removeConsentAll(){
        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Removing consent for all features");
        }

        if(isLoggingEnabled() && !isInitialized()){
            Log.w(Countly.TAG, "Calling this before initialising the SDK is deprecated!");
        }

        removeConsent(validFeatureNames);

        return this;
    }


    /**
     * Get the current consent state of a feature
     * @param featureName the name of a feature for which consent should be checked
     * @return the consent value
     * @deprecated use 'Countly.sharedInstance().consent().getConsent(featureName)'
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

                if (isLoggingEnabled()) {
                    Log.d(Countly.TAG, "Push consent has not been set this session. Setting the value found stored in preferences:[" + storedConsent + "]");
                }

                featureConsentValues.put(featureName, storedConsent);

                returnValue = storedConsent;
            } else {
                returnValue = false;
            }
        }

        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Returning consent for feature named: [" + featureName + "] [" + returnValue + "]");
        }

        return returnValue;
    }

    /**
     * Print the consent values of all features
     * @return Returns link to Countly for call chaining
     * @deprecated use 'Countly.sharedInstance().consent().checkAllConsent()'
     */
    public synchronized Countly checkAllConsent() {
        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Checking and printing consent for All features");
        }

        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Is consent required? [" + requiresConsent + "]");
        }

        //make sure push consent has been added to the feature map
        getConsent(CountlyFeatureNames.push);

        StringBuilder sb = new StringBuilder();

        for(String key:featureConsentValues.keySet()) {
            sb.append("Feature named [").append(key).append("], consent value: [").append(featureConsentValues.get(key)).append("]\n");
        }

        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, sb.toString());
        }

        return this;
    }

    /**
     * Returns true if any consent has been given
     * @return true - any consent has been given, false - no consent has been given
     * todo move to module
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
     * @deprecated use 'Countly.sharedInstance().remoteConfig().showFeedbackPopup'
     */
    public synchronized Countly showFeedbackPopup(final String widgetId, final String closeButtonText, final Activity activity, final CountlyStarRating.FeedbackRatingCallback feedbackCallback){
        if (!isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before showFeedbackPopup");
        }

        if(feedbackCallback == null) {
            ratings().showFeedbackPopup(widgetId, closeButtonText, activity, null);
        } else {
            ratings().showFeedbackPopup(widgetId, closeButtonText, activity, new FeedbackRatingCallback() {
                @Override
                public void callback(String error) {
                    feedbackCallback.callback(error);
                }
            });
        }

        return this;
    }

    /**
     * If enable, will automatically download newest remote config_ values on init.
     * @deprecated use CountlyConfig during init to set this
     * @param enabled set true for enabling it
     * @param feedbackCallback callback called after the update was done
     * @return
     */
    public synchronized Countly setRemoteConfigAutomaticDownload(boolean enabled, final RemoteConfig.RemoteConfigCallback feedbackCallback){
        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Setting if remote config_ Automatic download will be enabled, " + enabled);
        }

        remoteConfigAutomaticUpdateEnabled = enabled;

        if(feedbackCallback != null) {
            remoteConfigInitCallback = new RemoteConfigCallback() {
                @Override
                public void callback(String error) {
                    feedbackCallback.callback(error);
                }
            };
        }
        return this;
    }

    /**
     * Manually update remote config_ values
     * @param providedCallback
     * @deprecated use 'Countly.sharedInstance().remoteConfig().update(callback)'
     */
    public void remoteConfigUpdate(final RemoteConfig.RemoteConfigCallback providedCallback) {
        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Manually calling to updateRemoteConfig");
        }
        if (!isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before remoteConfigUpdate");
        }

        if(providedCallback == null) {
            remoteConfig().update(null);
        } else {
            remoteConfig().update(new RemoteConfigCallback() {
                @Override
                public void callback(String error) {
                    providedCallback.callback(error);
                }
            });
        }
    }

    /**
     * Manual remote config_ update call. Will only update the keys provided.
     * @param keysToInclude
     * @param providedCallback
     * @deprecated use 'Countly.sharedInstance().remoteConfig().updateForKeysOnly(keys, callback)'
     */
    public void updateRemoteConfigForKeysOnly(String[] keysToInclude, final RemoteConfig.RemoteConfigCallback providedCallback){
        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Manually calling to updateRemoteConfig with include keys");
        }
        if (!isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before updateRemoteConfigForKeysOnly");
        }

        if(providedCallback == null) {
            remoteConfig().updateForKeysOnly(keysToInclude, null);
        } else {
            remoteConfig().updateForKeysOnly(keysToInclude, new RemoteConfigCallback() {
                @Override
                public void callback(String error) {
                    providedCallback.callback(error);
                }
            });
        }
    }

    /**
     * Manual remote config_ update call. Will update all keys except the ones provided
     * @param keysToExclude
     * @param providedCallback
     * @deprecated use 'Countly.sharedInstance().remoteConfig().updateExceptKeys(keys, callback)'
     */
    public void updateRemoteConfigExceptKeys(String[] keysToExclude, final RemoteConfig.RemoteConfigCallback providedCallback) {
        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Manually calling to updateRemoteConfig with exclude keys");
        }
        if (!isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before updateRemoteConfigExceptKeys");
        }

        if(providedCallback == null) {
            remoteConfig().updateExceptKeys(keysToExclude, null);
        } else {
            remoteConfig().updateExceptKeys(keysToExclude, new RemoteConfigCallback() {
                @Override
                public void callback(String error) {
                    providedCallback.callback(error);
                }
            });
        }
    }

    /**
     * Get the stored value for the provided remote config_ key
     * @param key
     * @return
     * @deprecated use 'Countly.sharedInstance().remoteConfig().getValueForKey(key)'
     */
    public Object getRemoteConfigValueForKey(String key){
        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Calling remoteConfigValueForKey");
        }
        if (!isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before remoteConfigValueForKey");
        }

        return remoteConfig().getValueForKey(key);
    }

    /**
     * Clear all stored remote config_ values
     * @deprecated use 'Countly.sharedInstance().remoteConfig().clearStoredValues();'
     */
    public void remoteConfigClearValues(){
        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Calling remoteConfigClearValues");
        }
        if (!isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before remoteConfigClearValues");
        }

        remoteConfig().clearStoredValues();
    }

    /**
     * Allows you to add custom header key/value pairs to each request
     * @deprecated use CountlyConfig during init to set this
     */
    public void addCustomNetworkRequestHeaders(Map<String, String> headerValues){
        if (isLoggingEnabled()) {
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
        if (isLoggingEnabled()) {
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

        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "flushRequestQueues removed [" + count + "] requests");
        }
    }

    /**
     * Countly will attempt to fulfill all stored requests on demand
     */
    public void doStoredRequests() {
        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Calling doStoredRequests");
        }

        if (!isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before doStoredRequests");
        }

        connectionQueue_.tick();
    }

    public Countly enableTemporaryIdMode() {
        if (isLoggingEnabled()) {
            Log.d(Countly.TAG, "Calling enableTemporaryIdMode");
        }

        if (!isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before enableTemporaryIdMode");
        }

        changeDeviceId(DeviceId.temporaryCountlyDeviceId);

        return this;
    }

    public ModuleCrash.Crashes crashes() {
        if (!isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before accessing crashes");
        }

        return moduleCrash.crashesInterface;
    }

    public ModuleEvents.Events events() {
        if (!isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before accessing events");
        }

        return moduleEvents.eventsInterface;
    }

    public ModuleViews.Views views() {
        if (!isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before accessing views");
        }

        return moduleViews.viewsInterface;
    }

    public ModuleRatings.Ratings ratings() {
        if (!isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before accessing ratings");
        }

        return moduleRatings.ratingsInterface;
    }

    public ModuleSessions.Sessions sessions(){
        if (!isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before accessing sessions");
        }

        return moduleSessions.sessionInterface;
    }

    public ModuleRemoteConfig.RemoteConfig remoteConfig() {
        if (!isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before accessing remote config");
        }

        return moduleRemoteConfig.remoteConfigInterface;
    }

    public ModuleAPM.Apm apm() {
        if (!isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before accessing apm");
        }

        return moduleAPM.apmInterface;
    }

    public ModuleConsent.Consent consent() {
        if (!isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before accessing consent");
        }

        return moduleConsent.consentInterface;
    }

    public static void applicationOnCreate() {
        applicationStart = UtilsTime.currentTimestampMs();
    }

    // for unit testing
    ConnectionQueue getConnectionQueue() { return connectionQueue_; }
    void setConnectionQueue(final ConnectionQueue connectionQueue) { connectionQueue_ = connectionQueue; }
    ExecutorService getTimerService() { return timerService_; }
    EventQueue getEventQueue() { return eventQueue_; }
    void setEventQueue(final EventQueue eventQueue) { eventQueue_ = eventQueue; }
    long getPrevSessionDurationStartTime() { return moduleSessions.prevSessionDurationStartTime_; }
    void setPrevSessionDurationStartTime(final long prevSessionDurationStartTime) { moduleSessions.prevSessionDurationStartTime_ = prevSessionDurationStartTime; }
    int getActivityCount() { return activityCount_; }
    synchronized boolean getDisableUpdateSessionRequests() { return disableUpdateSessionRequests_; }
}
