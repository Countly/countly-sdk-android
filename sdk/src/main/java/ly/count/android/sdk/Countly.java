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
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ProcessLifecycleOwner;
import java.util.ArrayList;
import java.util.HashMap;
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

    private final String DEFAULT_COUNTLY_SDK_VERSION_STRING = "25.4.1";
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
    int EVENT_QUEUE_SIZE_THRESHOLD = 100;

    /**
     * How often onTimer() is called. This is the default value.
     */
    protected static final long TIMER_DELAY_IN_SECONDS = 60;

    protected static String[] publicKeyPinCertificates;
    protected static String[] certificatePinCertificates;

    interface LifecycleObserver {
        boolean LifeCycleAtleastStarted();
    }

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

    //SDK limit defaults
    static final int maxKeyLengthDefault = 128;
    static final int maxValueSizeDefault = 256;
    static final int maxSegmentationValuesDefault = 100;
    static final int maxBreadcrumbCountDefault = 100;
    static final int maxStackTraceLinesPerThreadDefault = 30;
    static final int maxStackTraceLineLengthDefault = 200;
    static final int maxStackTraceThreadCountDefault = 50;

    // see http://stackoverflow.com/questions/7048198/thread-safe-singletons-in-java
    private static class SingletonHolder {
        @SuppressLint("StaticFieldLeak")
        static final Countly instance = new Countly();
    }

    ConnectionQueue connectionQueue_;
    private ScheduledExecutorService timerService_;
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
    ModuleConfiguration moduleConfiguration = null;
    ModuleHealthCheck moduleHealthCheck = null;
    ModuleContent moduleContent = null;

    //reference to countly store
    CountlyStore countlyStore;

    //overrides
    boolean isHttpPostForced = false;//when true, all data sent to the server will be sent using HTTP POST

    //push related
    private boolean addMetadataToPushIntents = false;// a flag that indicates if metadata should be added to push notification intents

    //internal flags
    private boolean calledAtLeastOnceOnStart = false;//flag for if the onStart function has been called at least once

    protected boolean isBeginSessionSent = false;

    //custom request header fields
    Map<String, String> requestHeaderCustomValues;

    static long applicationStart = System.currentTimeMillis();

    String[] locationFallback;//temporary used until location can't be set before init

    protected CountlyConfig config_ = null;

    //fields for tracking push token debounce
    final static long lastRegistrationCallDebounceDuration = 60 * 1000;//60seconds
    long lastRegistrationCallTs = 0;
    String lastRegistrationCallID = null;
    CountlyMessagingProvider lastRegistrationCallProvider = null;

    boolean applicationClassProvided = false;

    public static class CountlyFeatureNames {
        public static final String sessions = "sessions";
        public static final String events = "events";
        public static final String views = "views";
        public static final String scrolls = "scrolls";
        public static final String clicks = "clicks";
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
        public static final String content = "content";
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
        connectionQueue_ = new ConnectionQueue();
        timerService_ = Executors.newSingleThreadScheduledExecutor();
    }

    private void startTimerService(ScheduledExecutorService service, ScheduledFuture<?> previousTimer, long timerDelay) {
        if (previousTimer != null && !previousTimer.isCancelled()) {
            previousTimer.cancel(false);
        }

        //minimum delay of 1 second
        if (timerDelay < 1) {
            timerDelay = 1;
        }

        timerFuture = service.scheduleWithFixedDelay(this::onTimer, timerDelay, timerDelay, TimeUnit.SECONDS);
    }

    /**
     * Must be called before other SDK methods can be used.
     * To initialise the SDK, you must pass a CountlyConfig object that contains
     * all the necessary information for setting up the SDK
     * Please prefer to use this on Application's onCreate method
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

        if (COUNTLY_SDK_NAME.equals(DEFAULT_COUNTLY_SDK_NAME) && COUNTLY_SDK_VERSION_STRING.equals(DEFAULT_COUNTLY_SDK_VERSION_STRING)) {
            L.d("[Init] Initializing Countly [" + COUNTLY_SDK_NAME + "] SDK version [" + COUNTLY_SDK_VERSION_STRING + "]");
        } else {
            L.d("[Init] Initializing Countly [" + COUNTLY_SDK_NAME + "] SDK version [" + COUNTLY_SDK_VERSION_STRING + "] default name[" + DEFAULT_COUNTLY_SDK_NAME + "] default version[" + DEFAULT_COUNTLY_SDK_VERSION_STRING + "]");
        }

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

        if (config.appKey == null || config.appKey.isEmpty()) {
            throw new IllegalArgumentException("valid appKey is required, but was provided either 'null' or empty String");
        }

        if (config.application == null) {
            L.w("[Init] Initialising the SDK without providing the application class. Some functionality will not work.");
        }
        applicationClassProvided = config.application != null;

        if (config.deviceID != null && config.deviceID.isEmpty()) {
            //device ID is provided but it's a empty string
            L.w("[Countly] init, Provided device ID is an empty string. It will be ignored. And a new one will be generated by the SDK.");
            // setting device id as null to trigger SDK device id generation
            config.setDeviceId(null);
        }

        L.d("[Init] SDK initialised with the URL:[" + config.serverURL + "] and the appKey:[" + config.appKey + "]");

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

            // Have a look at the SDK limit values
            if (config.sdkInternalLimits.maxKeyLength != null) {
                if (config.sdkInternalLimits.maxKeyLength < 1) {
                    config.sdkInternalLimits.maxKeyLength = 1;
                    L.w("[Init] provided 'maxKeyLength' is less than '1'. Setting it to '1'.");
                }
                L.i("[Init] provided 'maxKeyLength' override:[" + config.sdkInternalLimits.maxKeyLength + "]");
            } else {
                config.sdkInternalLimits.maxKeyLength = maxKeyLengthDefault;
            }

            if (config.sdkInternalLimits.maxValueSize != null) {
                if (config.sdkInternalLimits.maxValueSize < 1) {
                    config.sdkInternalLimits.maxValueSize = 1;
                    L.w("[Init] provided 'maxValueSize' is less than '1'. Setting it to '1'.");
                }
                L.i("[Init] provided 'maxValueSize' override:[" + config.sdkInternalLimits.maxValueSize + "]");
            } else {
                config.sdkInternalLimits.maxValueSize = maxValueSizeDefault;
            }

            if (config.sdkInternalLimits.maxSegmentationValues != null) {
                if (config.sdkInternalLimits.maxSegmentationValues < 1) {
                    config.sdkInternalLimits.maxSegmentationValues = 1;
                    L.w("[Init] provided 'maxSegmentationValues' is less than '1'. Setting it to '1'.");
                }
                L.i("[Init] provided 'maxSegmentationValues' override:[" + config.sdkInternalLimits.maxSegmentationValues + "]");
            } else {
                config.sdkInternalLimits.maxSegmentationValues = maxSegmentationValuesDefault;
            }

            if (config.sdkInternalLimits.maxBreadcrumbCount != null) {
                if (config.sdkInternalLimits.maxBreadcrumbCount < 1) {
                    config.sdkInternalLimits.maxBreadcrumbCount = 1;
                    L.w("[Init] provided 'maxBreadcrumbCount' is less than '1'. Setting it to '1'.");
                }
                L.i("[Init] provided 'maxBreadcrumbCount' override:[" + config.sdkInternalLimits.maxBreadcrumbCount + "]");
            } else {
                config.sdkInternalLimits.maxBreadcrumbCount = maxBreadcrumbCountDefault;
            }

            if (config.sdkInternalLimits.maxStackTraceLinesPerThread != null) {
                if (config.sdkInternalLimits.maxStackTraceLinesPerThread < 1) {
                    config.sdkInternalLimits.maxStackTraceLinesPerThread = 1;
                    L.w("[Init] provided 'maxStackTraceLinesPerThread' is less than '1'. Setting it to '1'.");
                }
                L.i("[Init] provided 'maxStackTraceLinesPerThread' override:[" + config.sdkInternalLimits.maxStackTraceLinesPerThread + "]");
            } else {
                config.sdkInternalLimits.maxStackTraceLinesPerThread = maxStackTraceLinesPerThreadDefault;
            }

            if (config.sdkInternalLimits.maxStackTraceLineLength != null) {
                if (config.sdkInternalLimits.maxStackTraceLineLength < 1) {
                    config.sdkInternalLimits.maxStackTraceLineLength = 1;
                    L.w("[Init] provided 'maxStackTraceLineLength' is less than '1'. Setting it to '1'.");
                }
                L.i("[Init] provided 'maxStackTraceLineLength' override:[" + config.sdkInternalLimits.maxStackTraceLineLength + "]");
            } else {
                config.sdkInternalLimits.maxStackTraceLineLength = maxStackTraceLineLengthDefault;
            }

            long timerDelay = TIMER_DELAY_IN_SECONDS;
            if (config.sessionUpdateTimerDelay != null) {
                //if we need to change the timer delay, do that first
                L.d("[Init] Setting custom session update timer delay, [" + config.sessionUpdateTimerDelay + "]");
                timerDelay = config.sessionUpdateTimerDelay;
            }
            startTimerService(timerService_, timerFuture, timerDelay);

            if (config.explicitStorageModeEnabled) {
                L.i("[Init] Explicit storage mode is being enabled");
            }

            //set or create the CountlyStore
            if (config.countlyStore != null) {
                //we are running a test and using a mock object
                countlyStore = config.countlyStore;
            } else {
                countlyStore = new CountlyStore(config.context, L, config.explicitStorageModeEnabled);
                config.setCountlyStore(countlyStore);
            }

            if (config.pcc != null) {
                L.i("[Init] Attaching a performance counter collector");
                countlyStore.pcc = config.pcc;
            }

            if (config.maxRequestQueueSize < 1) {
                L.e("[Init] provided request queue size is less than 1. Replacing it with 1.");
                config.maxRequestQueueSize = 1;
            }
            L.d("[Init] request queue size set to [" + config.maxRequestQueueSize + "]");
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

            if (config.safeViewIDGenerator == null) {
                //if we didn't override this for a test
                config.safeViewIDGenerator = new SafeIDGenerator() {
                    @NonNull @Override public String GenerateValue() {
                        return Utils.safeRandomVal();
                    }
                };
            }

            if (config.safeEventIDGenerator == null) {
                //if we didn't override this for a test
                config.safeEventIDGenerator = new SafeIDGenerator() {
                    @NonNull @Override public String GenerateValue() {
                        return Utils.safeRandomVal();
                    }
                };
            }

            if (config.immediateRequestGenerator == null) {
                config.immediateRequestGenerator = new ImmediateRequestGenerator() {
                    @Override public ImmediateRequestI CreateImmediateRequestMaker() {
                        return (new ImmediateRequestMaker());
                    }
                };
            }

            if (config.lifecycleObserver == null) {
                config.lifecycleObserver = new LifecycleObserver() {
                    @Override public boolean LifeCycleAtleastStarted() {
                        return lifecycleStateAtLeastStartedInternal();
                    }
                };
            }

            if (config.metricProviderOverride != null) {
                L.d("[Init] Custom metric provider was provided");
            }
            config.deviceInfo = new DeviceInfo(config.metricProviderOverride);

            if (config.tamperingProtectionSalt != null) {
                L.d("[Init] Parameter tampering protection salt set");
            }

            if (config.dropAgeHours < 0) {
                config.dropAgeHours = 0;
                L.d("[Init] Drop older requests threshold can not be negative. No threshold will be set.");
            }
            if (config.dropAgeHours > 0) {
                L.d("[Init] Drop older requests threshold set to:[" + config.dropAgeHours + "] hours");
            }

            if (connectionQueue_ == null) {
                L.e("[Init] SDK failed to initialize because the connection queue failed to be created");
                return this;
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
            try {
                Map<String, Object> migrationParams = new HashMap<>();
                migrationParams.put(MigrationHelper.key_from_0_to_1_custom_id_set, config.deviceID != null);

                MigrationHelper mHelper = new MigrationHelper(config.storageProvider, L, context_);
                mHelper.doWork(migrationParams);
            } catch (Exception ex) {
                L.e("[Init] SDK failed while performing data migration. SDK is not capable to initialize.");
                return this;
            }

            //initialise modules
            moduleHealthCheck = new ModuleHealthCheck(this, config);
            moduleConfiguration = new ModuleConfiguration(this, config);
            moduleRequestQueue = new ModuleRequestQueue(this, config);
            moduleConsent = new ModuleConsent(this, config);
            moduleDeviceId = new ModuleDeviceId(this, config);
            moduleCrash = new ModuleCrash(this, config);
            moduleEvents = new ModuleEvents(this, config);
            moduleUserProfile = new ModuleUserProfile(this, config);//this has to be set before the session module so that we can update remote config before sending anything session related
            moduleViews = new ModuleViews(this, config);
            moduleRatings = new ModuleRatings(this, config);
            moduleSessions = new ModuleSessions(this, config);
            moduleRemoteConfig = new ModuleRemoteConfig(this, config);
            moduleAPM = new ModuleAPM(this, config);
            moduleLocation = new ModuleLocation(this, config);
            moduleFeedback = new ModuleFeedback(this, config);
            moduleAttribution = new ModuleAttribution(this, config);
            moduleContent = new ModuleContent(this, config);

            modules.clear();
            modules.add(moduleConfiguration);
            modules.add(moduleRequestQueue);
            modules.add(moduleConsent);
            modules.add(moduleDeviceId);
            modules.add(moduleCrash);
            modules.add(moduleEvents);
            modules.add(moduleUserProfile);//this has to be set before the session module so that we can update remote config before sending anything session related
            modules.add(moduleViews);
            modules.add(moduleRatings);
            modules.add(moduleSessions);
            modules.add(moduleRemoteConfig);
            modules.add(moduleAPM);
            modules.add(moduleLocation);
            modules.add(moduleFeedback);
            modules.add(moduleAttribution);
            modules.add(moduleContent);

            modules.add(moduleHealthCheck);//set this at the end to detect any health issues with other modules before sending the report

            if (config.testModuleListener != null) {
                modules.add(config.testModuleListener);
            }

            //add missing providers
            moduleConfiguration.consentProvider = config.consentProvider;
            moduleRequestQueue.consentProvider = config.consentProvider;
            moduleHealthCheck.consentProvider = config.consentProvider;
            moduleRequestQueue.deviceIdProvider = config.deviceIdProvider;
            moduleConsent.eventProvider = config.eventProvider;
            moduleConsent.deviceIdProvider = config.deviceIdProvider;
            moduleDeviceId.eventProvider = config.eventProvider;
            moduleCrash.eventProvider = config.eventProvider;
            moduleEvents.viewIdProvider = config.viewIdProvider;

            baseInfoProvider = config.baseInfoProvider;
            requestQueueProvider = config.requestQueueProvider;
            L.setHealthChecker(config.healthTracker);

            L.i("[Init] Finished initialising modules");

            if (config.customNetworkRequestHeaders != null) {
                L.i("[Countly] Calling addCustomNetworkRequestHeaders");
                requestHeaderCustomValues = config.customNetworkRequestHeaders;

                connectionQueue_.setRequestHeaderCustomValues(requestHeaderCustomValues);
            }

            if (config.httpPostForced) {
                L.d("[Init] Setting HTTP POST to be forced");
                isHttpPostForced = config.httpPostForced;
            }

            if (config.tamperingProtectionSalt != null) {
                L.d("[Init] Enabling tamper protection");
            }

            if (config.dropAgeHours > 0) {
                L.d("[Init] Enabling drop older request threshold");
                countlyStore.setRequestAgeLimit(config.dropAgeHours);
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

            //initialize networking queues
            connectionQueue_.L = L;
            connectionQueue_.healthTracker = config.healthTracker;
            connectionQueue_.configProvider = config.configProvider;
            connectionQueue_.consentProvider = moduleConsent;
            connectionQueue_.moduleRequestQueue = moduleRequestQueue;
            connectionQueue_.deviceInfo = config.deviceInfo;
            connectionQueue_.pcc = config.pcc;
            connectionQueue_.setStorageProvider(config.storageProvider);
            connectionQueue_.setupSSLContext();
            connectionQueue_.setBaseInfoProvider(config.baseInfoProvider);
            connectionQueue_.setDeviceId(config.deviceIdProvider);
            connectionQueue_.setRequestHeaderCustomValues(requestHeaderCustomValues);
            connectionQueue_.setMetricOverride(config.metricOverride);
            connectionQueue_.setContext(context_);
            connectionQueue_.requestInfoProvider = new RequestInfoProvider() {
                @Override public boolean isHttpPostForced() {
                    return requestQueue().isHttpPostForced();
                }

                @Override public boolean isDeviceAppCrawler() {
                    return requestQueue().isDeviceAppCrawler();
                }

                @Override public boolean ifShouldIgnoreCrawlers() {
                    return requestQueue().ifShouldIgnoreCrawlers();
                }

                @Override public int getRequestDropAgeHours() {
                    return config.dropAgeHours;
                }

                @Override public String getRequestSalt() {
                    return config.tamperingProtectionSalt;
                }
            };

            sdkIsInitialised = true;
            //AFTER THIS POINT THE SDK IS COUNTED AS INITIALISED
            //set global application listeners
            if (config.application != null) {
                L.d("[Countly] Calling registerActivityLifecycleCallbacks");
                config.application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                    @Override
                    public void onActivityCreated(Activity activity, Bundle bundle) {
                        if (L.logEnabled()) {
                            L.d("[Countly] onActivityCreated, " + activity.getClass().getSimpleName());
                        }
                        //for (ModuleBase module : modules) {
                        //    module.callbackOnActivityCreated(activity);
                        //}
                    }

                    @Override
                    public void onActivityStarted(Activity activity) {
                        if (L.logEnabled()) {
                            L.d("[Countly] onActivityStarted, " + activity.getClass().getSimpleName());
                        }
                        onStartInternal(activity);
                        //for (ModuleBase module : modules) {
                        //    module.callbackOnActivityStarted(activity);
                        //}
                    }

                    @Override
                    public void onActivityResumed(Activity activity) {
                        if (L.logEnabled()) {
                            L.d("[Countly] onActivityResumed, " + activity.getClass().getSimpleName());
                        }
                        //for star rating
                        for (ModuleBase module : modules) {
                            module.callbackOnActivityResumed(activity);
                        }
                    }

                    @Override
                    public void onActivityPaused(Activity activity) {
                        if (L.logEnabled()) {
                            L.d("[Countly] onActivityPaused, " + activity.getClass().getSimpleName());
                        }
                        //for (ModuleBase module : modules) {
                        //    module.callbackOnActivityPaused(activity);
                        //}
                    }

                    @Override
                    public void onActivityStopped(Activity activity) {
                        if (L.logEnabled()) {
                            L.d("[Countly] onActivityStopped, " + activity.getClass().getSimpleName());
                        }
                        onStopInternal();
                        //for APM
                        for (ModuleBase module : modules) {
                            module.callbackOnActivityStopped(activity);
                        }
                    }

                    @Override
                    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
                        if (L.logEnabled()) {
                            L.d("[Countly] onActivitySaveInstanceState, " + activity.getClass().getSimpleName());
                        }
                        //for (ModuleBase module : modules) {
                        //    module.callbackOnActivitySaveInstanceState(activity);
                        //}
                    }

                    @Override
                    public void onActivityDestroyed(Activity activity) {
                        if (L.logEnabled()) {
                            L.d("[Countly] onActivityDestroyed, " + activity.getClass().getSimpleName());
                        }
                        //for (ModuleBase module : modules) {
                        //    module.callbackOnActivityDestroyed(activity);
                        //}
                    }
                });

                config.application.registerComponentCallbacks(new ComponentCallbacks() {
                    @Override
                    public void onConfigurationChanged(Configuration configuration) {
                        L.d("[Countly] ComponentCallbacks, onConfigurationChanged");
                        onConfigurationChangedInternal(configuration);
                    }

                    @Override
                    public void onLowMemory() {
                        L.d("[Countly] ComponentCallbacks, onLowMemory");
                    }
                });
            } else {
                L.d("[Countly] Global activity listeners not registred due to no Application class");
            }

            if (config_.lifecycleObserver.LifeCycleAtleastStarted()) {
                L.d("[Countly] SDK detects that the app is in the foreground. Increasing the activity counter and setting the foreground state.");
                activityCount_++;
                config.deviceInfo.inForeground();
            }

            L.i("[Init] About to call module 'initFinished'");

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
    public boolean isInitialized() {
        return sdkIsInitialised;
    }

    boolean lifecycleStateAtLeastStartedInternal() {
        return ProcessLifecycleOwner.get().getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
    }

    private void stopTimer() {
        L.i("[Countly] stopTimer, Stopping global timer");
        if (timerService_ != null) {
            try {
                timerService_.shutdown();
                if (!timerService_.awaitTermination(1, TimeUnit.SECONDS)) {
                    timerService_.shutdownNow();
                    if (!timerService_.awaitTermination(1, TimeUnit.SECONDS)) {
                        L.e("[Countly] stopTimer, Global timer must be locked");
                    }
                }
            } catch (Throwable t) {
                L.e("[Countly] stopTimer, Error while stopping global timer " + t);
            }
        }
    }

    void onSdkConfigurationChanged(@NonNull CountlyConfig config) {
        L.i("[Countly] onSdkConfigurationChanged");

        if (config_ == null) {
            L.e("[Countly] onSdkConfigurationChanged, config is null");
            return;
        }

        setLoggingEnabled(config.loggingEnabled);

        long timerDelay = TIMER_DELAY_IN_SECONDS;
        if (config.sessionUpdateTimerDelay != null) {
            timerDelay = config.sessionUpdateTimerDelay;
        }

        startTimerService(timerService_, timerFuture, timerDelay);

        config.maxRequestQueueSize = Math.max(config.maxRequestQueueSize, 1);
        countlyStore.setLimits(config.maxRequestQueueSize);

        config.dropAgeHours = Math.max(config.dropAgeHours, 0);
        if (config.dropAgeHours > 0) {
            countlyStore.setRequestAgeLimit(config.dropAgeHours);
        }

        config.eventQueueSizeThreshold = Math.max(config.eventQueueSizeThreshold, 1);
        EVENT_QUEUE_SIZE_THRESHOLD = config.eventQueueSizeThreshold;

        // Have a look at the SDK limit values
        if (config.sdkInternalLimits.maxKeyLength != null) {
            config.sdkInternalLimits.maxKeyLength = Math.max(config.sdkInternalLimits.maxKeyLength, 1);
        }

        if (config.sdkInternalLimits.maxValueSize != null) {
            config.sdkInternalLimits.maxValueSize = Math.max(config.sdkInternalLimits.maxValueSize, 1);
        }

        if (config.sdkInternalLimits.maxSegmentationValues != null) {
            config.sdkInternalLimits.maxSegmentationValues = Math.max(config.sdkInternalLimits.maxSegmentationValues, 1);
        }

        if (config.sdkInternalLimits.maxBreadcrumbCount != null) {
            config.sdkInternalLimits.maxBreadcrumbCount = Math.max(config.sdkInternalLimits.maxBreadcrumbCount, 1);
        }

        if (config.sdkInternalLimits.maxStackTraceLinesPerThread != null) {
            config.sdkInternalLimits.maxStackTraceLinesPerThread = Math.max(config.sdkInternalLimits.maxStackTraceLinesPerThread, 1);
        }
        if (config.sdkInternalLimits.maxStackTraceLineLength != null) {
            config.sdkInternalLimits.maxStackTraceLineLength = Math.max(config.sdkInternalLimits.maxStackTraceLineLength, 1);
        }

        for (ModuleBase module : modules) {
            module.onSdkConfigurationChanged(config);
        }
    }

    /**
     * Immediately disables session and event tracking and clears any stored session and event data.
     * Testing Purposes Only!
     *
     * This will destroy all stored data
     */
    public synchronized void halt() {
        L.i("Halting Countly!");
        sdkIsInitialised = false;
        L.SetListener(null);
        stopTimer();

        if (connectionQueue_ != null) {
            if (countlyStore != null) {
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
        moduleConfiguration = null;
        moduleHealthCheck = null;
        moduleContent = null;

        COUNTLY_SDK_VERSION_STRING = DEFAULT_COUNTLY_SDK_VERSION_STRING;
        COUNTLY_SDK_NAME = DEFAULT_COUNTLY_SDK_NAME;

        connectionQueue_ = new ConnectionQueue();
        timerService_ = Executors.newSingleThreadScheduledExecutor();
    }

    synchronized void notifyDeviceIdChange(boolean withoutMerge) {
        L.d("Notifying modules that device ID changed");

        for (ModuleBase module : modules) {
            module.deviceIdChanged(withoutMerge);
        }
    }

    void onStartInternal(Activity activity) {
        if (L.logEnabled()) {
            String activityName = "NULL ACTIVITY PROVIDED";
            if (activity != null) {
                activityName = activity.getClass().getSimpleName();
            }
            L.d("Countly onStartInternal called, name:[" + activityName + "], [" + activityCount_ + "] -> [" + (activityCount_ + 1) + "] activities now open");
        }

        ++activityCount_;
        if (activityCount_ == 1 && !moduleSessions.manualSessionControlEnabled) {
            //if we open the first activity
            //and we are not using manual session control,
            //begin a session

            moduleSessions.beginSessionInternal();
            moduleConfiguration.fetchIfTimeIsUpForFetchingServerConfig();
        }

        config_.deviceInfo.inForeground();

        for (ModuleBase module : modules) {
            module.onActivityStarted(activity, activityCount_);
        }

        calledAtLeastOnceOnStart = true;
    }

    void onStopInternal() {
        L.d("Countly onStopInternal called, [" + activityCount_ + "] -> [" + (activityCount_ - 1) + "] activities now open");

        if (activityCount_ == 0) {
            L.e("must call onStart before onStop");
            return;
        }

        --activityCount_;
        if (activityCount_ == 0 && !moduleSessions.manualSessionControlEnabled) {
            // if we don't use manual session control
            // Called when final Activity is stopped.
            // Sends an end session event to the server, also sends any unsent custom events.
            moduleSessions.endSessionInternal();
        }

        config_.deviceInfo.inBackground();

        for (ModuleBase module : modules) {
            module.onActivityStopped(activityCount_);
        }
    }

    public synchronized void onConfigurationChangedInternal(Configuration newConfig) {
        L.i("Calling [onConfigurationChangedInternal]");

        for (ModuleBase module : modules) {
            module.onConfigurationChanged(newConfig);
        }
    }

    /**
     * Tells the Countly SDK that an Activity has started. Since Android does not have an
     * easy way to determine when an application instance starts and stops, you must call this
     * method from every one of your Activity's onStart methods for accurate application
     * session tracking.
     */
    public synchronized void onStart(Activity activity) {
        if (!isInitialized()) {
            L.e("init must be called before onStart");
            return;
        }

        if (applicationClassProvided) {
            L.w("Manual calls to 'onStart' will be ignored since the application class ir provided. SDK will handle these callbacks automatically");
            return;
        }

        onStartInternal(activity);
    }

    /**
     * Tells the Countly SDK that an Activity has stopped. Since Android does not have an
     * easy way to determine when an application instance starts and stops, you must call this
     * method from every one of your Activity's onStop methods for accurate application
     * session tracking.
     * unbalanced calls to onStart/onStop are detected
     */
    public synchronized void onStop() {
        if (!isInitialized()) {
            L.e("init must be called before onStop");
            return;
        }

        if (applicationClassProvided) {
            L.w("Manual calls to 'onStart' will be ignored since the application class ir provided. SDK will handle these callbacks automatically");
            return;
        }

        onStopInternal();
    }

    public synchronized void onConfigurationChanged(Configuration newConfig) {
        if (!isInitialized()) {
            L.e("init must be called before onConfigurationChanged");
            return;
        }

        if (applicationClassProvided) {
            L.w("Manual calls to 'onConfigurationChanged' will be ignored since the application class ir provided. SDK will handle these callbacks automatically");
            return;
        }

        onConfigurationChangedInternal(newConfig);
    }

    /**
     * Called every 60 seconds to send a session heartbeat to the server. Does nothing if there
     * is not an active application session.
     */
    synchronized void onTimer() {
        L.v("[onTimer] Calling heartbeat, Activity count:[" + activityCount_ + "]");

        if (isInitialized()) {
            final boolean appIsInForeground = activityCount_ > 0;
            if (appIsInForeground && !moduleSessions.manualSessionControlEnabled) {
                //if we have automatic session control and we are in the foreground, record an update
                moduleSessions.updateSessionInternal();
            } else if (moduleSessions.manualSessionControlEnabled && moduleSessions.manualSessionControlHybridModeEnabled && moduleSessions.sessionIsRunning()) {
                // if we are in manual session control mode with hybrid sessions enabled (SDK takes care of update requests) and there is a session running,
                // let's create the update request
                moduleSessions.updateSessionInternal();
            }

            //on every timer tick we collect all events and attempt to send requests
            moduleRequestQueue.sendEventsIfNeeded(true);

            //on every timer tick we save the user profile if it was changed
            moduleUserProfile.saveInternal();

            requestQueueProvider.tick();
        }
    }

    /**
     * DON'T USE THIS!!!!
     */
    public void onRegistrationId(String registrationId, CountlyMessagingProvider provider) {
        //if this call is done by CountlyPush, it is assumed that the SDK is already initialised
        if (!config_.consentProvider.getConsent(CountlyFeatureNames.push)) {
            return;
        }

        if (!isInitialized()) {
            L.w("[onRegistrationId] Calling this before the SDK is initialized.");
        }

        //debouncing the call

        long currentTs = UtilsTime.currentTimestampMs();
        long timeDelta = currentTs - lastRegistrationCallTs;

        if (lastRegistrationCallID != null && lastRegistrationCallID.equals(registrationId) &&
            lastRegistrationCallProvider != null && lastRegistrationCallProvider == provider &&
            timeDelta < lastRegistrationCallDebounceDuration) {
            // if the values match and we are trying to resend them withing the debounce duration, ignore them
            L.w("[onRegistrationId] Calling this with the same values within the debounce interval. elapsedT:[" + timeDelta + "] ms");
            return;
        }

        lastRegistrationCallTs = currentTs;
        lastRegistrationCallID = registrationId;
        lastRegistrationCallProvider = provider;

        connectionQueue_.tokenSession(registrationId, provider);
    }

    public void setLoggingEnabled(final boolean enableLogging) {
        enableLogging_ = enableLogging;
        L.d("Enabling logging");
    }

    /**
     * Check if logging has been enabled internally in the SDK
     *
     * @return true means "yes"
     */
    public boolean isLoggingEnabled() {
        return enableLogging_;
    }

    /**
     * Returns if the countly sdk onStart function has been called at least once
     *
     * @return true - yes, it has, false - no it has not
     * @deprecated This will be removed
     */
    public boolean hasBeenCalledOnStart() {
        return calledAtLeastOnceOnStart;
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

    /**
     * Content feature interface
     *
     * @return content module
     * @apiNote This is an EXPERIMENTAL feature, and it can have breaking changes
     */
    public ModuleContent.Content contents() {
        if (!isInitialized()) {
            L.e("Countly.sharedInstance().init must be called before accessing content");
            return null;
        }

        return moduleContent.contentInterface;
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
