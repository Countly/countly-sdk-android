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
import android.content.res.Configuration;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * This class is the public API for the Countly Android SDK.
 * Get more details <a href="https://github.com/Countly/countly-sdk-android">here</a>.
 */
public class Countly {

    private final String DEFAULT_COUNTLY_SDK_VERSION_STRING = "21.11.0";

    /**
     * Used as request meta data on every request
     */
    private final String DEFAULT_COUNTLY_SDK_NAME = "java-native-android";

    /**
     * Current version of the Count.ly Android SDK as a displayable string.
     */
    public String COUNTLY_SDK_VERSION_STRING = DEFAULT_COUNTLY_SDK_VERSION_STRING;

    /**
     * Used as request meta data on every request
     */
    public String COUNTLY_SDK_NAME = DEFAULT_COUNTLY_SDK_NAME;

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
     * Countly internal logger
     * Should not be used outside of the SDK
     * No guarantees of not breaking functionality
     * Exposed only for the SDK push implementation
     */
    public ModuleLog L = new ModuleLog();

    /**
     * Broadcast sent when consent set is changed
     */
    public static final String CONSENT_BROADCAST = "ly.count.android.sdk.Countly.CONSENT_BROADCAST";

    /**
     * Determines how many custom events can be queued locally before
     * an attempt is made to submit them to a Count.ly server.
     */
    static int EVENT_QUEUE_SIZE_THRESHOLD = 100;

    /**
     * Maximum amount of requests allowed in the request queue
     */
    int maxRequestQueueSize = 1000;

    /**
     * How often onTimer() is called. This is the default value.
     */
    private static final long TIMER_DELAY_IN_SECONDS = 60;

    protected static String[] publicKeyPinCertificates;
    protected static String[] certificatePinCertificates;

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

    /**
     * Enum used in Countly.initMessaging() method which controls what kind of
     * messaging provider is in use in current app installation.
     */
    public enum CountlyMessagingProvider {
        FCM,    // Firebase
        HMS,    // Huawei
    }

    // see http://stackoverflow.com/questions/7048198/thread-safe-singletons-in-java
    private static class SingletonHolder {
        @SuppressLint("StaticFieldLeak")
        static final Countly instance = new Countly();
    }

    ConnectionQueue connectionQueue_;
    private final ScheduledExecutorService timerService_;
    private ScheduledFuture<?> timerFuture = null;
    private int activityCount_;
    boolean disableUpdateSessionRequests_ = false;//todo, move to module after 'setDisableUpdateSessionRequests' is removed

    boolean sdkIsInitialised = false;

    BaseInfoProvider baseInfoProvider;
    RequestQueueProvider requestQueueProvider;

    //w - warnings
    //e - errors
    //i - user accessible calls and important SDK internals
    //d - regular SDK internals
    //v - spammy SDK internals
    private boolean enableLogging_;
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
    ModuleLocation moduleLocation = null;
    ModuleFeedback moduleFeedback = null;
    ModuleRequestQueue moduleRequestQueue = null;
    ModuleAttribution moduleAttribution = null;
    ModuleUserProfile moduleUserProfile = null;

    //reference to countly store
    CountlyStore countlyStore;

    /**
     * user data access
     * @deprecated This user data access method will be removed. Use 'Countly.sharedInstance().userProfile();' to access the required functionality.
     */
    public static UserData userData;

    //overrides
    boolean isHttpPostForced = false;//when true, all data sent to the server will be sent using HTTP POST

    //push related
    private boolean addMetadataToPushIntents = false;// a flag that indicates if metadata should be added to push notification intents

    //internal flags
    private boolean calledAtLeastOnceOnStart = false;//flag for if the onStart function has been called at least once

    //attribution
    protected boolean isAttributionEnabled = true;

    protected boolean isBeginSessionSent = false;

    //custom request header fields
    Map<String, String> requestHeaderCustomValues;

    static long applicationStart = System.currentTimeMillis();

    String[] locationFallback;//temporary used until location can't be set before init

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
        public static final String apm = "apm";
        public static final String feedback = "feedback";
        public static final String remoteConfig = "remote-config";
        //public static final String accessoryDevices = "accessory-devices";
    }

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
        timerService_ = Executors.newSingleThreadScheduledExecutor();
        staticInit();
    }

    private void staticInit() {
        connectionQueue_ = new ConnectionQueue();
        Countly.userData = new UserData(connectionQueue_);
        startTimerService(timerService_, timerFuture, TIMER_DELAY_IN_SECONDS);
    }

    private void startTimerService(ScheduledExecutorService service, ScheduledFuture<?> previousTimer, long timerDelay) {
        if (previousTimer != null && !previousTimer.isCancelled()) {
            previousTimer.cancel(false);
        }

        //minimum delay of 1 second
        //maximum delay if 10 minutes
        if (timerDelay < 1) {
            timerDelay = 1;
        } else if (timerDelay > 600) {
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
     * To initialise the SDK, you must pass a CountlyConfig object that contains
     * all the necessary information for setting up the SDK
     *
     * @param config contains all needed information to init SDK
     */
    public synchronized Countly init(CountlyConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Can't init SDK with 'null' config");
        }

        //enable logging
        if (config.loggingEnabled) {
            //enable logging before any potential logging calls
            setLoggingEnabled(true);
        }

        L.SetListener(config.providedLogCallback);

        L.d("[Init] Initializing Countly [" + COUNTLY_SDK_NAME + "] SDK version [" + COUNTLY_SDK_VERSION_STRING + "]");

        if (config.context == null) {
            if (config.application != null) {
                L.d("[Init] No explicit context provided. Using context from the provided application class");
                config.context = config.application;
            } else {
                throw new IllegalArgumentException("valid context is required in Countly init, but was provided 'null'");
            }
        } else {
            L.d("[Init] Using explicitly provided context");
        }

        if (!UtilsNetworking.isValidURL(config.serverURL)) {
            throw new IllegalArgumentException("valid serverURL is required");
        }

        if (config.serverURL.charAt(config.serverURL.length() - 1) == '/') {
            L.v("[Init] Removing trailing '/' from provided server url");
            config.serverURL = config.serverURL.substring(0, config.serverURL.length() - 1);//removing trailing '/' from server url
        }

        if (config.appKey == null || config.appKey.length() == 0) {
            throw new IllegalArgumentException("valid appKey is required, but was provided either 'null' or empty String");
        }

        if (config.application == null) {
            L.w("[Init] Initialising the SDK without providing the application class is deprecated");
        }

        if (config.deviceID != null && config.deviceID.length() == 0) {
            //device ID is provided but it's a empty string
            throw new IllegalArgumentException("valid deviceID is required, but was provided as empty String");
        }

        if (config.idMode == DeviceIdType.ADVERTISING_ID) {
            L.w("The use of 'ADVERTISING_ID' as device ID generation strategy is deprecated. It will be replaced with 'OPEN_UDID'.");
            config.idMode = DeviceIdType.OPEN_UDID;
        }

        if (config.idMode == DeviceIdType.TEMPORARY_ID) {
            throw new IllegalArgumentException("Temporary_ID type can't be provided during init");
        }

        if (config.deviceID == null && config.idMode == null) {
            //device ID was not provided and no preferred mode specified. Choosing default
            config.idMode = DeviceIdType.OPEN_UDID;
        }

        if (config.idMode == DeviceIdType.DEVELOPER_SUPPLIED && config.deviceID == null) {
            throw new IllegalArgumentException("Valid device ID has to be provided with the Developer_Supplied device ID type");
        }

        if (isLoggingEnabled()) {
            String halfAppKey = config.appKey.substring(0, config.appKey.length() / 2);
            L.d("[Init] SDK initialised with the URL:[" + config.serverURL + "] and first half of the appKey:[" + halfAppKey + "]");
        }

        if (sdkIsInitialised && (!baseInfoProvider.getServerURL().equals(config.serverURL) ||
            !baseInfoProvider.getAppKey().equals(config.appKey) ||
            !DeviceId.deviceIDEqualsNullSafe(config.deviceID, config.idMode, connectionQueue_.getDeviceId()))) {
            //not sure if this needed
            L.e("Countly cannot be reinitialized with different values");
            return this;
        }

        if (L.logEnabled()) {
            L.i("[Init] Checking init parameters");

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
            if (contextSuperClass != null) {
                contextText += ", it's superclass: [" + contextSuperClass.getSimpleName() + "]";
            }

            L.i(contextText);
        }

        //set internal context, it's allowed to be changed on the second init call
        context_ = config.context.getApplicationContext();

        // if we get here and eventQueue_ != null, init is being called again with the same values,
        // so there is nothing to do, because we are already initialized with those values
        if (!sdkIsInitialised) {
            L.d("[Init] About to init internal systems");

            config_ = config;

            if (config.sessionUpdateTimerDelay != null) {
                //if we need to change the timer delay, do that first
                L.d("[Init] Setting custom session update timer delay, [" + config.sessionUpdateTimerDelay + "]");
                startTimerService(timerService_, timerFuture, config.sessionUpdateTimerDelay);
            }

            //set or create the CountlyStore
            if (config.countlyStore != null) {
                //we are running a test and using a mock object
                countlyStore = config.countlyStore;
            } else {
                countlyStore = new CountlyStore(config.context, L);
                config.setCountlyStore(countlyStore);
            }

            if(config.maxRequestQueueSize < 1) {
                L.e("[Init] provided request queue size is less than 1. Replacing it with 1.");
                config.maxRequestQueueSize = 1;
            }
            countlyStore.setLimits(config.maxRequestQueueSize);

            if (config.storageProvider == null) {
                // outside of tests this should be null
                config.storageProvider = config.countlyStore;
            } else {
                L.d("[Init] Custom event storage provider was provided");
            }

            if (config.eventQueueProvider == null) {
                config.eventQueueProvider = countlyStore;
            } else {
                L.d("[Init] Custom event queue provider was provided");
            }

            if (config.requestQueueProvider == null) {
                config.requestQueueProvider = connectionQueue_;
            } else {
                L.d("[Init] Custom request queue provider was provided");
            }

            //check legacy access methods
            if (locationFallback != null && config.locationCountyCode == null && config.locationCity == null && config.locationLocation == null && config.locationIpAddress == null) {
                //if the fallback was set and config did not contain any location, use the fallback info
                // { country_code, city, gpsCoordinates, ipAddress };
                config.locationCountyCode = locationFallback[0];
                config.locationCity = locationFallback[1];
                config.locationLocation = locationFallback[2];
                config.locationIpAddress = locationFallback[3];
            }

            //perform data migration if needed
            MigrationHelper mHelper = new MigrationHelper(config.storageProvider, L);
            mHelper.doWork();

            //initialise modules
            moduleRequestQueue = new ModuleRequestQueue(this, config);
            moduleConsent = new ModuleConsent(this, config);
            moduleDeviceId = new ModuleDeviceId(this, config);
            moduleCrash = new ModuleCrash(this, config);
            moduleEvents = new ModuleEvents(this, config);
            moduleViews = new ModuleViews(this, config);
            moduleRatings = new ModuleRatings(this, config);
            moduleSessions = new ModuleSessions(this, config);
            moduleRemoteConfig = new ModuleRemoteConfig(this, config);
            moduleAPM = new ModuleAPM(this, config);
            moduleLocation = new ModuleLocation(this, config);
            moduleFeedback = new ModuleFeedback(this, config);
            moduleAttribution = new ModuleAttribution(this, config);
            moduleUserProfile = new ModuleUserProfile(this, config);

            modules.clear();
            modules.add(moduleRequestQueue);
            modules.add(moduleConsent);
            modules.add(moduleDeviceId);
            modules.add(moduleCrash);
            modules.add(moduleEvents);
            modules.add(moduleViews);
            modules.add(moduleRatings);
            modules.add(moduleSessions);
            modules.add(moduleRemoteConfig);
            modules.add(moduleAPM);
            modules.add(moduleLocation);
            modules.add(moduleFeedback);
            modules.add(moduleAttribution);
            modules.add(moduleUserProfile);

            //add missing providers
            moduleRequestQueue.consentProvider = config.consentProvider;
            moduleRequestQueue.deviceIdProvider = config.deviceIdProvider;
            moduleConsent.eventProvider = config.eventProvider;
            moduleConsent.deviceIdProvider = config.deviceIdProvider;
            moduleDeviceId.eventProvider = config.eventProvider;
            moduleCrash.eventProvider = config.eventProvider;

            baseInfoProvider = config.baseInfoProvider;
            requestQueueProvider = config.requestQueueProvider;

            L.i("[Init] Finished initialising modules");

            //init other things
            L.d("[Init] Currently cached advertising ID [" + countlyStore.getCachedAdvertisingId() + "]");
            AdvertisingIdAdapter.cacheAdvertisingID(config.context, countlyStore);

            if (config.customNetworkRequestHeaders != null) {
                L.i("[Countly] Calling addCustomNetworkRequestHeaders");
                requestHeaderCustomValues = config.customNetworkRequestHeaders;
                if (connectionQueue_ != null) {
                    connectionQueue_.setRequestHeaderCustomValues(requestHeaderCustomValues);
                }
            }

            if (config.httpPostForced) {
                L.d("[Init] Setting HTTP POST to be forced");
                isHttpPostForced = config.httpPostForced;
            }

            if (config.tamperingProtectionSalt != null) {
                L.d("[Init] Enabling tamper protection");
                ConnectionProcessor.salt = config.tamperingProtectionSalt;
            }

            if (config.pushIntentAddMetadata) {
                L.d("[Init] Enabling push intent metadata");
                addMetadataToPushIntents = config.pushIntentAddMetadata;
            }

            if (config.eventQueueSizeThreshold != null) {
                L.d("[Init] Setting event queue size: [" + config.eventQueueSizeThreshold + "]");

                if (config.eventQueueSizeThreshold < 1) {
                    L.d("[Init] queue size can't be less than zero");
                    config.eventQueueSizeThreshold = 1;
                }

                EVENT_QUEUE_SIZE_THRESHOLD = config.eventQueueSizeThreshold;
            }

            if (config.publicKeyPinningCertificates != null) {
                sharedInstance().L.i("[Init] Enabling public key pinning");
                publicKeyPinCertificates = config.publicKeyPinningCertificates;
            }

            if (config.certificatePinningCertificates != null) {
                Countly.sharedInstance().L.i("[Init] Enabling certificate pinning");
                certificatePinCertificates = config.certificatePinningCertificates;
            }

            if (config.enableAttribution != null) {
                L.d("[Init] Enabling attribution");
                isAttributionEnabled = config.enableAttribution;
            }

            //initialize networking queues
            connectionQueue_.L = L;
            connectionQueue_.consentProvider = moduleConsent;
            connectionQueue_.setStorageProvider(config.storageProvider);
            connectionQueue_.setupSSLContext();
            connectionQueue_.setBaseInfoProvider(config.baseInfoProvider);
            connectionQueue_.setDeviceId(config.deviceIdProvider.getDeviceIdInstance());
            connectionQueue_.setRequestHeaderCustomValues(requestHeaderCustomValues);
            connectionQueue_.setMetricOverride(config.metricOverride);
            connectionQueue_.setContext(context_);

            sdkIsInitialised = true;
            //AFTER THIS POINT THE SDK IS COUNTED AS INITIALISED

            //set global application listeners
            if (config.application != null) {
                config.application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                    @Override
                    public void onActivityCreated(Activity activity, Bundle bundle) {
                        if (L.logEnabled()) {
                            L.d("[Countly] onActivityCreated, " + activity.getClass().getSimpleName());
                        }
                        for (ModuleBase module : modules) {
                            module.callbackOnActivityCreated(activity);
                        }
                    }

                    @Override
                    public void onActivityStarted(Activity activity) {
                        if (L.logEnabled()) {
                            L.d("[Countly] onActivityStarted, " + activity.getClass().getSimpleName());
                        }
                        for (ModuleBase module : modules) {
                            module.callbackOnActivityStarted(activity);
                        }
                    }

                    @Override
                    public void onActivityResumed(Activity activity) {
                        if (L.logEnabled()) {
                            L.d("[Countly] onActivityResumed, " + activity.getClass().getSimpleName());
                        }
                        for (ModuleBase module : modules) {
                            module.callbackOnActivityResumed(activity);
                        }
                    }

                    @Override
                    public void onActivityPaused(Activity activity) {
                        if (L.logEnabled()) {
                            L.d("[Countly] onActivityPaused, " + activity.getClass().getSimpleName());
                        }
                        for (ModuleBase module : modules) {
                            module.callbackOnActivityPaused(activity);
                        }
                    }

                    @Override
                    public void onActivityStopped(Activity activity) {
                        if (L.logEnabled()) {
                            L.d("[Countly] onActivityStopped, " + activity.getClass().getSimpleName());
                        }
                        for (ModuleBase module : modules) {
                            module.callbackOnActivityStopped(activity);
                        }
                    }

                    @Override
                    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
                        if (L.logEnabled()) {
                            L.d("[Countly] onActivitySaveInstanceState, " + activity.getClass().getSimpleName());
                        }
                        for (ModuleBase module : modules) {
                            module.callbackOnActivitySaveInstanceState(activity);
                        }
                    }

                    @Override
                    public void onActivityDestroyed(Activity activity) {
                        if (L.logEnabled()) {
                            L.d("[Countly] onActivityDestroyed, " + activity.getClass().getSimpleName());
                        }
                        for (ModuleBase module : modules) {
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

            for (ModuleBase module : modules) {
                module.initFinished(config);
            }

            L.i("[Init] Finished initialising SDK");
        } else {
            //if this is not the first time we are calling init
            L.i("[Init] Getting in the 'else' block");

            // context is allowed to be changed on the second init call
            connectionQueue_.setContext(context_);
        }

        return this;
    }

    /**
     * Checks whether Countly.init has been already called.
     *
     * @return true if Countly is ready to use
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public synchronized boolean isInitialized() {
        return sdkIsInitialised;
    }

    /**
     * Immediately disables session &amp; event tracking and clears any stored session &amp; event data.
     * This API is useful if your app has a tracking opt-out switch, and you want to immediately
     * disable tracking when a user opts out.
     *
     * This will destroy all stored data
     */
    public synchronized void halt() {
        L.i("Halting Countly!");
        sdkIsInitialised = false;
        L.SetListener(null);

        if (connectionQueue_ != null) {
            if(countlyStore != null) {
                countlyStore.clear();
            }
            connectionQueue_.setContext(null);
            connectionQueue_ = null;
        }

        activityCount_ = 0;

        for (ModuleBase module : modules) {
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
        moduleLocation = null;
        moduleFeedback = null;
        moduleRequestQueue = null;

        COUNTLY_SDK_VERSION_STRING = DEFAULT_COUNTLY_SDK_VERSION_STRING;
        COUNTLY_SDK_NAME = DEFAULT_COUNTLY_SDK_NAME;

        staticInit();
    }

    synchronized void notifyDeviceIdChange() {
        L.d("Notifying modules that device ID changed");

        for (ModuleBase module : modules) {
            module.deviceIdChanged();
        }
    }

    /**
     * Tells the Countly SDK that an Activity has started. Since Android does not have an
     * easy way to determine when an application instance starts and stops, you must call this
     * method from every one of your Activity's onStart methods for accurate application
     * session tracking.
     */
    public synchronized void onStart(Activity activity) {
        if (L.logEnabled()) {
            String activityName = "NULL ACTIVITY PROVIDED";
            if (activity != null) {
                activityName = activity.getClass().getSimpleName();
            }
            L.d("Countly onStart called, name:[" + activityName + "], [" + activityCount_ + "] -> [" + (activityCount_ + 1) + "] activities now open");
        }

        if (!isInitialized()) {
            L.e("init must be called before onStart");
            return;
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
        L.d("Checking referrer: " + referrer);
        if (referrer != null) {
            connectionQueue_.sendReferrerData(referrer);
            ReferrerReceiver.deleteReferrer(context_);
        }

        CrashDetails.inForeground();

        for (ModuleBase module : modules) {
            module.onActivityStarted(activity);
        }

        calledAtLeastOnceOnStart = true;
    }

    /**
     * Tells the Countly SDK that an Activity has stopped. Since Android does not have an
     * easy way to determine when an application instance starts and stops, you must call this
     * method from every one of your Activity's onStop methods for accurate application
     * session tracking.
     * unbalanced calls to onStart/onStop are detected
     */
    public synchronized void onStop() {
        L.d("Countly onStop called, [" + activityCount_ + "] -> [" + (activityCount_ - 1) + "] activities now open");

        if (!isInitialized()) {
            L.e("init must be called before onStop");
            return;
        }
        if (activityCount_ == 0) {
            L.e("must call onStart before onStop");
            return;
        }

        --activityCount_;
        if (activityCount_ == 0 && !moduleSessions.manualSessionControlEnabled) {
            // if we don't use manual session control
            // Called when final Activity is stopped.
            // Sends an end session event to the server, also sends any unsent custom events.
            moduleSessions.endSessionInternal(null);
        }

        CrashDetails.inBackground();

        for (ModuleBase module : modules) {
            module.onActivityStopped();
        }
    }

    public synchronized void onConfigurationChanged(Configuration newConfig) {
        L.d("Calling [onConfigurationChanged]");
        if (!isInitialized()) {
            L.e("init must be called before onConfigurationChanged");
            return;
        }

        for (ModuleBase module : modules) {
            module.onConfigurationChanged(newConfig);
        }
    }

    /**
     * @deprecated the usage of this callback is deprecated and it will be removed
     */
    public static void onCreate(Activity activity) {

    }

    /**
     * Called every 60 seconds to send a session heartbeat to the server. Does nothing if there
     * is not an active application session.
     */
    synchronized void onTimer() {
        L.v("[onTimer] Calling heartbeat, Activity count:[" + activityCount_ + "]");

        if (isInitialized()) {
            final boolean hasActiveSession = activityCount_ > 0;
            if (hasActiveSession) {
                if (!moduleSessions.manualSessionControlEnabled) {
                    moduleSessions.updateSessionInternal();
                }
            }

            //on every timer tick we collect all events and attempt to send requests
            moduleRequestQueue.sendEventsIfNeeded(true);
            requestQueueProvider.tick();
        }
    }

    /**
     * DON'T USE THIS!!!!
     */
    public void onRegistrationId(String registrationId, CountlyMessagingMode mode) {
        onRegistrationId(registrationId, mode, CountlyMessagingProvider.FCM);
    }

    /**
     * DON'T USE THIS!!!!
     */
    public void onRegistrationId(String registrationId, CountlyMessagingMode mode, CountlyMessagingProvider provider) {
        //if this call is done by CountlyPush, it is assumed that the SDK is already initialised
        if (!config_.consentProvider.getConsent(CountlyFeatureNames.push)) {
            return;
        }

        connectionQueue_.tokenSession(registrationId, mode, provider);
    }

    /**
     * Changes current device id type to the one specified in parameter. Closes current session and
     * reopens new one with new id. Doesn't merge user profiles on the server
     *
     * @param type Device ID type to change to
     * @param deviceId Optional device ID for a case when type = DEVELOPER_SPECIFIED
     * @deprecated use 'Countly.sharedInstance().deviceId().changeWithoutMerge("newDeviceId");'. Changing deviceId to an ID that is not developer supplied is deprecated.
     */
    public void changeDeviceIdWithoutMerge(DeviceId.Type type, String deviceId) {
        L.d("Calling [changeDeviceIdWithoutMerge] with type and ID");

        if (!isInitialized()) {
            L.e("init must be called before changeDeviceIdWithoutMerge");
            return;
        }

        moduleDeviceId.changeDeviceIdWithoutMerge(ModuleDeviceId.fromOldDeviceIdToNew(type), deviceId);
    }

    /**
     * Changes current device id to the one specified in parameter. Merges user profile with new id
     * (if any) with old profile.
     *
     * @param deviceId new device id
     * @deprecated Use 'Countly.sharedInstance().deviceId().changeWithMerge("newDeviceId");'
     */
    public void changeDeviceIdWithMerge(String deviceId) {
        L.d("Calling [changeDeviceIdWithMerge] only with ID");
        if (!isInitialized()) {
            L.e("init must be called before changeDeviceIdWithMerge");
            return;
        }

        deviceId().changeWithMerge(deviceId);
    }

    /**
     * Returns the device id used by countly for this device
     *
     * @return device ID
     * @deprecated Use 'Countly.sharedInstance().deviceId().getID();'
     */
    public synchronized String getDeviceID() {
        if (!isInitialized()) {
            L.e("init must be called before getDeviceID");
            return null;
        }

        L.d("[Countly] Calling 'getDeviceID'");
        return deviceId().getID();
    }

    /**
     * Returns the type of the device ID used by countly for this device.
     *
     * @return device ID type
     * @deprecated Use 'Countly.sharedInstance().deviceId().getType();'
     */
    public synchronized DeviceId.Type getDeviceIDType() {
        if (!isInitialized()) {
            L.e("init must be called before getDeviceID");
            return null;
        }

        L.d("[Countly] Calling 'getDeviceIDType'");
        return ModuleDeviceId.fromNewDeviceIdToOld(deviceId().getType());
    }

    /**
     * Go into temporary device ID mode
     *
     * @return
     * @deprecated Use 'Countly.sharedInstance().deviceId().enableTemporaryIdMode();'
     */
    public Countly enableTemporaryIdMode() {
        L.i("[Countly] Calling enableTemporaryIdMode");

        if (!isInitialized()) {
            L.e("Countly.sharedInstance().init must be called before enableTemporaryIdMode");
            return this;
        }
        deviceId().enableTemporaryIdMode();

        return this;
    }

    /**
     * Disable sending of location data
     *
     * @return Returns link to Countly for call chaining
     * @deprecated Use 'Countly.sharedInstance().location().disableLocation()'
     */
    public synchronized Countly disableLocation() {
        L.d("Disabling location");
        if (!isInitialized()) {
            L.w("The use of 'disableLocation' before init is deprecated, use CountlyConfig instead of this");
            return this;
        }

        location().disableLocation();

        return this;
    }

    /**
     * Set location parameters. If they are set before begin_session, they will be sent as part of it.
     * If they are set after, then they will be sent as a separate request.
     * If this is called after disabling location, it will enable it.
     *
     * @param country_code ISO Country code for the user's country
     * @param city Name of the user's city
     * @param gpsCoordinates comma separate lat and lng values. For example, "56.42345,123.45325"
     * @return Returns link to Countly for call chaining
     * @deprecated Use 'Countly.sharedInstance().location().setLocation()'
     */
    public synchronized Countly setLocation(String country_code, String city, String gpsCoordinates, String ipAddress) {
        L.d("Setting location parameters, cc[" + country_code + "] cy[" + city + "] gps[" + gpsCoordinates + "] ip[" + ipAddress + "]");

        if (!isInitialized()) {
            L.w("The use of 'setLocation' before init is deprecated, use CountlyConfig instead of this");
            return this;
        }

        if (isInitialized()) {
            location().setLocation(country_code, city, gpsCoordinates, ipAddress);
        } else {
            //use fallback
            locationFallback = new String[] { country_code, city, gpsCoordinates, ipAddress };
        }

        return this;
    }

    void setLoggingEnabled(final boolean enableLogging) {
        enableLogging_ = enableLogging;
        L.d("Enabling logging");
    }

    /**
     * Check if logging has been enabled internally in the SDK
     *
     * @return true means "yes"
     */
    public synchronized boolean isLoggingEnabled() {
        return enableLogging_;
    }

    /**
     * Returns if the countly sdk onStart function has been called at least once
     *
     * @return true - yes, it has, false - no it has not
     */
    public synchronized boolean hasBeenCalledOnStart() {
        return calledAtLeastOnceOnStart;
    }

    /**
     * Get the status of the override for HTTP POST
     *
     * @return return "true" if HTTP POST ir forced
     * @deprecated Change your current implementation to use "Countly.sharedInstance().requestQueue().isHttpPostForced()"
     */
    public boolean isHttpPostForced() {
        if (!isInitialized()) {
            L.e("init must be called before isHttpPostForced");
            return false;
        }
        return moduleRequestQueue.requestQueueInterface.isHttpPostForced();
    }

    /**
     * Return if current device is detected as a app crawler
     *
     * @return returns if devices is detected as a app crawler
     * @deprecated Change your current implementation to use "Countly.sharedInstance().requestQueue().isDeviceAppCrawler()"
     */
    public boolean isDeviceAppCrawler() {
        if (!isInitialized()) {
            L.e("init must be called before isDeviceAppCrawler");
            return false;
        }
        return moduleRequestQueue.requestQueueInterface.isDeviceAppCrawler();
    }

    /**
     * Return if the countly sdk should ignore app crawlers
     *
     * @deprecated Change your current implementation to use "Countly.sharedInstance().requestQueue().ifShouldIgnoreCrawlers()"
     */
    public boolean ifShouldIgnoreCrawlers() {
        if (!isInitialized()) {
            L.e("init must be called before ifShouldIgnoreCrawlers");
            return false;
        }
        return moduleRequestQueue.requestQueueInterface.ifShouldIgnoreCrawlers();
    }

    /**
     * Deletes all stored requests to server.
     * This includes events, crashes, views, sessions, etc
     * Call only if you don't need that information
     *
     * @deprecated Change your current implementation to use "Countly.sharedInstance().requestQueue().flushQueues()"
     */
    public void flushRequestQueues() {
        L.i("[Countly] Calling flushRequestQueues");

        if (!isInitialized()) {
            L.e("Countly.sharedInstance().init must be called before flushRequestQueues");
            return;
        }

        moduleRequestQueue.requestQueueInterface.flushQueues();
    }

    /**
     * Combine all events in event queue into a request and
     * attempt to process stored requests on demand
     *
     * @deprecated Change your current implementation to use "Countly.sharedInstance().requestQueue().attemptToSendStoredRequests()"
     */
    public void doStoredRequests() {
        L.i("[Countly] Calling doStoredRequests");

        if (!isInitialized()) {
            L.e("Countly.sharedInstance().init must be called before doStoredRequests");
            return;
        }

        moduleRequestQueue.requestQueueInterface.attemptToSendStoredRequests();
    }

    /**
     * Go through the request queue and replace the appKey of all requests with the current appKey
     *
     * @deprecated Change your current implementation to use "Countly.sharedInstance().requestQueue().overwriteAppKeys()"
     */
    public void requestQueueOverwriteAppKeys() {
        L.i("[Countly] Calling requestQueueOverwriteAppKeys");

        if (!isInitialized()) {
            L.e("[Countly] Countly.sharedInstance().init must be called before requestQueueOverwriteAppKeys");
            return;
        }

        requestQueue().overwriteAppKeys();
    }

    /**
     * Go through the request queue and delete all requests that don't have the current application key
     *
     * @deprecated Change your current implementation to use "Countly.sharedInstance().requestQueue().eraseWrongAppKeyRequests()"
     */
    public void requestQueueEraseAppKeysRequests() {
        L.i("[Countly] Calling requestQueueEraseAppKeysRequests");

        if (!isInitialized()) {
            L.e("[Countly] Countly.sharedInstance().init must be called before requestQueueEraseAppKeysRequests");
            return;
        }

        requestQueue().eraseWrongAppKeyRequests();
    }

    public ModuleCrash.Crashes crashes() {
        if (!isInitialized()) {
            L.e("Countly.sharedInstance().init must be called before accessing crashes");
            return null;
        }

        return moduleCrash.crashesInterface;
    }

    public ModuleEvents.Events events() {
        if (!isInitialized()) {
            L.e("Countly.sharedInstance().init must be called before accessing events");
            return null;
        }

        return moduleEvents.eventsInterface;
    }

    public ModuleViews.Views views() {
        if (!isInitialized()) {
            L.e("Countly.sharedInstance().init must be called before accessing views");
            return null;
        }

        return moduleViews.viewsInterface;
    }

    public ModuleRatings.Ratings ratings() {
        if (!isInitialized()) {
            L.e("Countly.sharedInstance().init must be called before accessing ratings");
            return null;
        }

        return moduleRatings.ratingsInterface;
    }

    public ModuleSessions.Sessions sessions() {
        if (!isInitialized()) {
            L.e("Countly.sharedInstance().init must be called before accessing sessions");
            return null;
        }

        return moduleSessions.sessionInterface;
    }

    public ModuleRemoteConfig.RemoteConfig remoteConfig() {
        if (!isInitialized()) {
            L.e("Countly.sharedInstance().init must be called before accessing remote config");
            return null;
        }

        return moduleRemoteConfig.remoteConfigInterface;
    }

    public ModuleAPM.Apm apm() {
        if (!isInitialized()) {
            L.e("Countly.sharedInstance().init must be called before accessing apm");
            return null;
        }

        return moduleAPM.apmInterface;
    }

    public ModuleConsent.Consent consent() {
        if (!isInitialized()) {
            L.e("Countly.sharedInstance().init must be called before accessing consent");
            return null;
        }

        return moduleConsent.consentInterface;
    }

    public ModuleLocation.Location location() {
        if (!isInitialized()) {
            L.e("Countly.sharedInstance().init must be called before accessing location");
            return null;
        }

        return moduleLocation.locationInterface;
    }

    public ModuleFeedback.Feedback feedback() {
        if (!isInitialized()) {
            L.e("Countly.sharedInstance().init must be called before accessing feedback");
            return null;
        }

        return moduleFeedback.feedbackInterface;
    }

    public ModuleRequestQueue.RequestQueue requestQueue() {
        if (!isInitialized()) {
            L.e("Countly.sharedInstance().init must be called before accessing request queue");
            return null;
        }

        return moduleRequestQueue.requestQueueInterface;
    }

    public ModuleAttribution.Attribution attribution() {
        if (!isInitialized()) {
            L.e("Countly.sharedInstance().init must be called before accessing attribution");
            return null;
        }

        return moduleAttribution.attributionInterface;
    }

    public ModuleDeviceId.DeviceId deviceId() {
        if (!isInitialized()) {
            L.e("Countly.sharedInstance().init must be called before accessing deviceId");
            return null;
        }

        return moduleDeviceId.deviceIdInterface;
    }

    public ModuleUserProfile.UserProfile userProfile() {
        if (!isInitialized()) {
            L.e("Countly.sharedInstance().init must be called before accessing user profile");
            return null;
        }

        return moduleUserProfile.userProfileInterface;
    }

    public static void applicationOnCreate() {
    }

    // for unit testing
    ConnectionQueue getConnectionQueue() {
        return connectionQueue_;
    }

    ExecutorService getTimerService() {
        return timerService_;
    }

    long getPrevSessionDurationStartTime() {
        return moduleSessions.prevSessionDurationStartTime_;
    }

    void setPrevSessionDurationStartTime(final long prevSessionDurationStartTime) {
        moduleSessions.prevSessionDurationStartTime_ = prevSessionDurationStartTime;
    }

    int getActivityCount() {
        return activityCount_;
    }

    synchronized boolean getDisableUpdateSessionRequests() {
        return disableUpdateSessionRequests_;
    }
}
