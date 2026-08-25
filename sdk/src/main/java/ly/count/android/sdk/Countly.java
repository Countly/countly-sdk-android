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
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ProcessLifecycleOwner;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    private final String DEFAULT_COUNTLY_SDK_VERSION_STRING = "26.1.5";
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

    // Certificate/public-key pinning material now lives per-instance on the owning ConnectionQueue
    // (see ConnectionQueue#publicKeyPinCertificates). Keeping it static made two instances pointed
    // at different servers share one pinning set (last-init-wins) — a correctness and security hazard.

    interface LifecycleObserver {
        boolean LifeCycleAtleastStarted();
    }

    /**
     * Whether the process is at least started, asked through this instance's config.
     * <p>
     * Null-safe on purpose: init derives the observer onto the CountlyConfig, and a config shared by two
     * instances is briefly without one while the second init restores the developer's values. A live sibling
     * instance can reach the foreground check in that window - through a consent change or a device-id change -
     * and must not crash the caller's thread over it. Treated as "not started" when unknown, which is the
     * conservative answer: it suppresses an automatic session rather than opening a spurious one.
     */
    boolean lifeCycleAtleastStarted() {
        if (config_ == null || config_.lifecycleObserver == null) {
            L.d("[Countly] lifeCycleAtleastStarted, no lifecycle observer available yet, treating the app as not started");
            return false;
        }
        return config_.lifecycleObserver.LifeCycleAtleastStarted();
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

    /**
     * Reserved name of the default (shared) instance returned by {@link #sharedInstance()}, and the name it is
     * listed under by {@link #listInstances()}. The {@code [CLY]_} prefix is the SDK's internal-key convention,
     * so it will not collide with a customer app key or instance name.
     * <p>
     * Public because {@code listInstances()} returns it: without the constant a caller would have to hardcode
     * the literal to tell the default instance apart from a named one.
     */
    public static final String DEFAULT_NAME = "[CLY]_default_instance";

    // Registry of live Countly instances keyed by instance name (default instance under
    // DEFAULT_NAME). Static for process-wide access exactly like the previous singleton; instances
    // live for the process lifetime - halt() resets an instance's state but keeps the object
    // registered, so repeated sharedInstance()/instance(name) calls return a stable object.
    @SuppressLint("StaticFieldLeak")
    static final Map<String, Countly> instances_ = new ConcurrentHashMap<>();

    //Serialises creation in instance(name) against removal in removeInstance(name), so a name is never
    //created and removed at the same time. Plain reads go straight to the concurrent map.
    private static final Object instancesLock_ = new Object();

    // Test support only (default OFF, never enabled in production): instrumented tests create many
    // detached "new Countly().init(...)" instances but usually halt only the singleton, so each
    // detached instance keeps its session-update timer running and leaks 'onTimer' ticks into the
    // shared store during later tests. When tracking is enabled, each initialized instance is
    // recorded here so the test runner can halt whatever a test left behind between tests. Guarded by
    // 'instanceTrackingForTests' so there is zero cost and no behavior change for normal apps.
    static volatile boolean instanceTrackingForTests = false;
    static final java.util.List<Countly> trackedInstancesForTests = new java.util.concurrent.CopyOnWriteArrayList<>();

    // Test support only (null in production): overrides the process foreground/background detection.
    // Instrumented tests share one process, and ProcessLifecycleOwner keeps reporting "started" for a
    // while after a prior test's Activity stops (its ~700ms debounce), so a later test that inits
    // without injecting its own lifecycle observer would non-deterministically be seen as foreground
    // and auto-begin a session. The test runner resets this to a deterministic default between tests;
    // a test that needs foreground still injects its own CountlyConfig.lifecycleObserver, which is
    // consulted directly and bypasses this override.
    static volatile Boolean lifecycleStateOverrideForTests = null;

    /** Halts every tracked instance still initialized and clears the registry. Test support only. */
    static void haltTrackedInstances() {
        for (Countly c : trackedInstancesForTests) {
            if (c != null && c.isInitialized()) {
                try {
                    c.halt();
                } catch (Throwable ignored) {
                    // a best-effort cleanup between tests must never fail the run
                }
            }
        }
        trackedInstancesForTests.clear();
    }

    ConnectionQueue connectionQueue_;
    private ScheduledExecutorService timerService_;
    private ScheduledFuture<?> timerFuture = null;
    private int activityCount_;
    boolean disableUpdateSessionRequests_ = false;//todo, move to module after 'setDisableUpdateSessionRequests' is removed

    //volatile: written under the instance monitor (init/tearDown) but read lock-free from other
    //threads - the network executor's tick() guard, push token callbacks, the lifecycle dispatch
    //gates, and app threads following the documented getInstance(name) + isInitialized() pattern.
    //Without it a reader has no happens-before edge with init and could see true while the module
    //fields are still being published.
    volatile boolean sdkIsInitialised = false;

    BaseInfoProvider baseInfoProvider;
    RequestQueueProvider requestQueueProvider;

    //w - warnings
    //e - errors
    //i - user accessible calls and important SDK internals
    //d - regular SDK internals
    //v - spammy SDK internals
    private boolean enableLogging_;
    // when true, console logging is kept off because the host app is a production build
    // and the SDK was configured to disable logging in production
    private boolean loggingForcedOffForProduction = false;
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

    //This instance's DeviceInfo. Every init() overwrites config.deviceInfo, so reading it back off a
    //reused config would hand this instance another instance's foreground/background state.
    DeviceInfo deviceInfo_;

    // Storage namespace suffix for this instance's persisted files (main store + legacy OpenUDID).
    // Empty for the default instance -> legacy file names (backward compatible); derived from the
    // instance name for named instances so their storage is fully isolated.
    String storageNamespace_ = "";

    // This instance's internal limits, seeded at init from the developer's config and then resolved
    // further by this instance's server behaviour settings. They live here rather than on the
    // CountlyConfig because ~30 call sites read them on every recorded event, view, crash and user
    // property, and two instances may legitimately be configured from one config object - sharing the
    // config's nested limits object would let one instance's /o/sdk response silently retruncate the
    // other instance's data.
    final ConfigSdkInternalLimits sdkInternalLimits_ = new ConfigSdkInternalLimits();

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

    // for executor choice of immediate requests
    boolean useSerialExecutorInternal = false;

    //fields for tracking push token debounce
    final static long lastRegistrationCallDebounceDuration = 60 * 1000;//60seconds
    long lastRegistrationCallTs = 0;
    String lastRegistrationCallID = null;
    CountlyMessagingProvider lastRegistrationCallProvider = null;

    boolean applicationClassProvided = false;

    // The name this instance is registered under in the process-wide registry. Authoritative for
    // storage namespacing: DEFAULT_NAME -> legacy files; any other name -> isolated, suffixed files.
    String instanceName_ = DEFAULT_NAME;

    // True once the registry has handed this object out under instanceName_. A detached "new Countly()" is
    // never registered, which is exactly why the stale-handle check in init has to consult this and not just
    // compare against instances_ - every detached instance carries the DEFAULT_NAME default.
    private boolean wasRegistered_ = false;

    // Set as the FIRST act of teardown, before anything is nulled. Android delivers lifecycle callbacks on
    // the main thread while halt()/removeInstance() can run on any thread, so an event can already be in
    // flight when teardown starts; this gate makes every dispatch entry point no-op for a dying instance.
    // Volatile: written by the tearing-down thread, read by the main thread.
    private volatile boolean tearingDown = false;

    // Process-global lifecycle/component callbacks are registered on the Application per init().
    // We keep references so halt() can unregister them; otherwise every init/halt cycle leaks a
    // callback bound to a dead instance that keeps receiving Activity/config events - a real hazard
    // once multiple instances come and go in one process.
    // Lifecycle callbacks are no longer registered per instance: CountlyLifecycleDispatcher holds the one
    // process-wide registration and this instance simply adds/removes itself from its list. That removes both
    // the N-registrations-for-N-instances problem and the per-init re-registration leak.

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
        public static final String metrics = "metrics";
        //public static final String accessoryDevices = "accessory-devices";
    }

    /**
     * Returns the default (shared) Countly instance. Existing single-instance integrations use this
     * method and are unaffected by multi-instance support - the default instance keeps the legacy
     * storage location and behavior.
     */
    public static Countly sharedInstance() {
        return instance(DEFAULT_NAME);
    }

    /**
     * Returns the Countly instance registered under the given name, creating it (uninitialized) if it
     * does not yet exist. The {@code name} argument is the sole identity of the instance: it is what
     * isolates the instance's storage (request queue, event queue, device id, configuration) from
     * every other instance. Any stable string works; passing your app key as the name is the natural
     * choice for one instance per Countly application. A null or empty name returns the default
     * (shared) instance. The returned instance is not initialized - call {@code init(config)} on it.
     * <p>
     * <b>A named instance starts from empty storage.</b> It does not inherit anything from
     * {@link #sharedInstance()}: it generates its own device id, so the server sees a new user, and it can not
     * send what the default instance has queued. So do not move an existing integration onto a named instance
     * to reuse its app key as the name - that re-identifies every install and abandons whatever the default
     * instance had not sent yet. Keep {@code sharedInstance()} as the primary and add named instances
     * alongside it for the app keys that are genuinely new.
     * <p>
     * <b>Push notifications and native crash dumps are not multi-instance.</b> Both are process-wide and
     * owned by the default (shared) instance: {@code CountlyPush} has one static registration, one token
     * and one shared preferences file, and sdk-native writes minidumps to one fixed directory. A named
     * instance therefore touches neither - its push consent changes, push tokens, push clicks and native
     * dumps are all left to the default instance. Initialise push on {@link #sharedInstance()}.
     * <p>
     * <b>Each initialised instance has a real resource footprint</b>: its own request-queue executor,
     * backoff scheduler and session-heartbeat timer (roughly three threads), its own SharedPreferences
     * files, and a 60-second heartbeat while in the foreground. A handful of instances is fine; designs
     * that mint instances from unbounded dynamic names should reuse a small fixed set instead, and
     * {@link #removeInstance(String)} what they no longer need.
     *
     * @param name the instance name (sole identity of the instance)
     * @return the (possibly newly created, uninitialized) Countly instance registered under that name
     */
    public static Countly instance(String name) {
        final String key = (name == null || name.isEmpty()) ? DEFAULT_NAME : name;

        //Deliberately NOT Map.computeIfAbsent: that is API 24, this SDK is minSdk 21 and does not enable
        //core library desugaring, so it would throw NoSuchMethodError on API 21-23 - on the very first
        //sharedInstance() call. Double-checked locking uses only pre-24 APIs and, unlike putIfAbsent,
        //never constructs a losing duplicate (the constructor starts a scheduled-executor thread).
        Countly existing = instances_.get(key);
        if (existing != null) {
            return existing;
        }

        synchronized (instancesLock_) {
            existing = instances_.get(key);
            if (existing != null) {
                return existing;
            }
            Countly c = new Countly();
            c.instanceName_ = key;
            // Give named instances a distinct logcat tag so their console output is attributable; the
            // default instance keeps the plain "Countly" tag for backward compatibility.
            if (!DEFAULT_NAME.equals(key)) {
                c.L.setTag(TAG + "-" + key);
            }
            c.wasRegistered_ = true;
            instances_.put(key, c);
            return c;
        }
    }

    /**
     * Logs a registry-level message without going through {@link #sharedInstance()}, which would create
     * and register a default instance just to log. Prefers the default instance's logger when that object
     * already exists, so the message also reaches that instance's log listener and health tracker.
     * <p>
     * The logcat fallback is gated on some instance actually having console logging on: raw
     * {@code android.util.Log} bypasses {@link CountlyConfig#setLoggingEnabled(boolean)} and
     * {@link CountlyConfig#disableSDKLoggingInProduction()}, and these messages carry an instance name,
     * which is commonly the app key - it must not surface in a build that asked for no logging.
     */
    private static void logWithoutCreatingDefault(String message, boolean warning) {
        Countly existingDefault = instances_.get(DEFAULT_NAME);
        if (existingDefault != null && existingDefault.L.logEnabled()) {
            //the default instance's logger can actually deliver this, so let it - the message reaches that
            //instance's log listener and health tracker
            if (warning) {
                existingDefault.L.w(message);
            } else {
                existingDefault.L.d(message);
            }
            return;
        }

        //Otherwise fall through rather than returning: a default instance exists in the registry as soon as
        //anything calls sharedInstance() (a push broadcast is enough), and if it was never initialised its
        //logger is unarmed - so returning there would drop registry diagnostics even when a named instance
        //has logging on. Match the SDK's own logEnabled(), which counts a log listener too, not just console.
        for (Countly c : instances_.values()) {
            if (c.L.logEnabled()) {
                if (warning) {
                    c.L.w(message);
                } else {
                    c.L.d(message);
                }
                return;
            }
        }

        boolean consoleLoggingWanted = false;
        for (Countly c : instances_.values()) {
            if (c.L.loggingEnabled) {
                consoleLoggingWanted = true;
                break;
            }
        }

        if (!consoleLoggingWanted) {
            return;
        }

        if (warning) {
            Log.w(TAG, message);
        } else {
            Log.d(TAG, message);
        }
    }

    /**
     * Returns the Countly instance registered under the given name, or null if no such instance has
     * been created yet. Unlike {@link #instance(String)} this never creates a new instance. A null or
     * empty name refers to the default (shared) instance.
     *
     * @param name the instance name
     * @return the existing instance, or null if none is registered under that name
     */
    public static Countly getInstance(String name) {
        final String key = (name == null || name.isEmpty()) ? DEFAULT_NAME : name;
        return instances_.get(key);
    }

    /**
     * Returns the names of all currently registered instances, including the default (shared) instance,
     * which is listed under its reserved name {@link #DEFAULT_NAME}. An instance is registered from the
     * moment {@code sharedInstance()} or {@code instance(name)} first hands it out, whether or not it has
     * been initialised - use {@link #getInstance(String)} plus {@link #isInitialized()} to tell those apart.
     *
     * @return a snapshot list of registered instance names
     */
    public static List<String> listInstances() {
        return new ArrayList<>(instances_.keySet());
    }

    /**
     * Halts every registered instance, including the default (shared) one.
     * <p>
     * <b>This destroys stored data.</b> Each instance is reset exactly as {@link #halt()} resets it, which
     * erases that instance's persisted state: its device ID and ID type (including the generated-UUID
     * cache), its consent, its queued requests and events, its cached remote-config values and its schema
     * version - and, for the default instance, the process-wide push preferences (push consent and cached
     * push data) as well. Anything recorded but not yet sent is gone, and the next session starts as a
     * new user.
     * <p>
     * <b>Only instances initialised in this process run have storage to clear.</b> An instance that is
     * registered but was never initialised (obtained but not yet {@code init()}-ed, or freshly re-obtained
     * after {@link #removeInstance(String)} or a process restart) has no store object, so its persisted
     * files from earlier runs are left untouched - erasing those requires initialising that name first and
     * then halting it.
     * <p>
     * The instances remain registered, so a later {@code instance(name)} or {@code sharedInstance()} returns
     * the same (now halted) object, ready to be initialised again. To stop an instance without discarding
     * its data, use {@link #removeInstance(String)}, which keeps everything on disk.
     */
    public static void haltAllInstances() {
        for (Countly c : instances_.values()) {
            try {
                c.halt();
            } catch (Throwable t) {
                //one instance failing to halt must not leave the remaining ones running: this is a
                //process-wide reset, so it has to be all-or-as-much-as-possible rather than stopping at the
                //first failure
                c.L.e("[Countly] haltAllInstances, failed to halt an instance, continuing with the rest, [" + t + "]");
            }
        }
    }

    /**
     * Halts the named instance and removes it from the process-wide registry. Unlike {@link #halt()}
     * (which resets an instance but keeps it registered so it can be initialised again), this
     * additionally deregisters the object: afterwards {@link #getInstance(String)} returns null for
     * that name and {@link #instance(String)} creates a fresh, uninitialized instance. Use this to
     * reclaim an instance you no longer need - without it the registry retains every instance ever
     * created for the process lifetime, which matters if instances are keyed by dynamic (unbounded)
     * names.
     * <p>
     * The default (shared) instance can not be removed: it must remain a stable object for
     * {@link #sharedInstance()}, so a null, empty, or default name is a no-op (warned, not silent).
     * Any reference a caller still holds to the removed instance becomes detached (halted and no
     * longer registered); obtain a fresh handle via {@link #instance(String)} instead.
     * <p>
     * The instance's stored data is <b>kept</b>: its queued requests and events, device ID, consent state
     * and cached remote-config values stay on disk, so nothing recorded but not yet sent is lost, and
     * initialising that name again resumes from where it left off. Removing frees the in-memory instance,
     * not its storage - so if you key instances by short-lived, dynamic names, their files accumulate.
     * Call {@link #halt()} on the instance first when you want its data erased as well.
     * <p>
     * Treat removal as an exclusive operation on that name: code still using the name on other threads
     * should be quiesced first, because {@code instance(name)} after removal hands out a fresh,
     * uninitialised object whose module accessors return null until it is initialised.
     *
     * @param name the instance name to stop and deregister
     */
    public static void removeInstance(String name) {
        final String key = (name == null || name.isEmpty()) ? DEFAULT_NAME : name;
        if (DEFAULT_NAME.equals(key)) {
            logWithoutCreatingDefault("[Countly] removeInstance, the default (shared) instance can not be removed; use halt() to reset it. Ignoring.", true);
            return;
        }
        Countly c;
        //Deregister under the same lock instance(name) creates under, so a concurrent creation either
        //completes before this removal or begins after it - a brand new instance can never be removed
        //while its creator is still inside instance(). Remove before halting so that creation hands back
        //a fresh object rather than the one being torn down.
        synchronized (instancesLock_) {
            c = instances_.remove(key);
        }

        if (c == null) {
            logWithoutCreatingDefault("[Countly] removeInstance, no instance registered under [" + key + "], nothing to remove", false);
            return;
        }
        //Stop outside the lock: teardown does real work (timers, callbacks, threads) and must not block
        //instance() creation. The removed object becomes GC-eligible once the caller drops its handle,
        //unless ModuleCrash#halt could not unlink from the process-global handler chain.
        //Deliberately NOT halt(): deregistering an instance must not throw away data it recorded but has
        //not sent yet. Callers who want the data gone call halt() on the instance first.
        c.L.i("[Countly] removeInstance, stopping and deregistering instance [" + key + "], stored data is kept");
        c.stopWithoutClearingData();
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

        //determine whether console logging must stay off for production builds before any logging call
        loggingForcedOffForProduction = shouldForceLoggingOffForProduction(config);

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

        //resolve this instance's storage namespace from the name it is registered under - the name
        //passed to instance(name) is the sole identity of an instance, the config plays no part in it
        //(CountlyConfig.setInstanceName was removed before release for exactly that reason). The
        //default (shared) instance keeps the legacy, un-namespaced files for backward compatibility;
        //a named instance gets an isolated, sanitized suffix so its queues, device id, and config
        //never collide with another instance's storage.
        if (DEFAULT_NAME.equals(instanceName_)) {
            storageNamespace_ = "";
        } else {
            storageNamespace_ = CountlyStore.sanitizeNamespace(instanceName_);
        }

        //A CountlyConfig may be shared by several instances. What used to make that unsafe was the SDK
        //writing its own resolved state back onto the object: the internal limits now live per instance
        //(see sdkInternalLimits_), and DerivedFieldSnapshot resets the objects init derives, so each init
        //starts from what the developer configured rather than from the previous instance's leftovers.
        if (config.initialisedForNamespace != null && !storageNamespace_.equals(config.initialisedForNamespace)) {
            L.w("[Init] This CountlyConfig was already used to initialise another instance. Each instance keeps its own storage, device id and internal limits, but the values you set on this object apply to every instance built from it, and the settings this instance resolves from the server are written onto it. Prefer a fresh CountlyConfig per instance.");
        }

        //A handle whose name was deregistered by removeInstance() is still a fully functional object with the
        //same instanceName_, so re-initialising it would build a second store and queue over the namespace a
        //freshly obtained instance(name) is already using - two sessions, two timers, and two writers
        //read-modify-writing one request queue. Refuse instead: the caller must take a fresh handle.
        Countly registered = instances_.get(instanceName_);
        if (wasRegistered_ && registered != this) {
            L.e("[Init] This handle for instance [" + instanceName_ + "] was removed from the registry and can not be initialised again; another object is registered under that name. Obtain a fresh handle with Countly.instance(name).");
            return this;
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

            // should be here for sbs and hc
            useSerialExecutorInternal = config.useSerialExecutor;

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

            //Take this instance's own copy of the resolved limits. From here on the SDK reads and writes
            //sdkInternalLimits_, never config.sdkInternalLimits, so the developer's config object is left
            //alone and a second instance built from the same config gets its own limits. ModuleConfiguration
            //is constructed below and layers the server behaviour settings on top of this copy.
            sdkInternalLimits_.copyFrom(config.sdkInternalLimits);

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

            //init() and the module constructors write their results back onto the config: the store, the
            //queues, every provider back-reference, the DeviceInfo, the temporary-device-id sentinel and
            //the server-resolved settings. Re-initialising this instance must therefore start from what
            //the developer configured, not from the previous init's leftovers - halt() throws away the
            //ConnectionQueue, so a cached requestQueueProvider would write through a torn-down queue.
            //This also runs when a config is shared across instances, which is supported: every init starts
            //from the values the developer set rather than from the previous instance's leftovers.
            //Sharing one CountlyConfig across instances is supported, and init mutates that shared object while
            //deriving the store, the queues and the providers. init is synchronized on THIS Countly, not on the
            //config, so two instances initialising on two threads would interleave those writes and could adopt
            //each other's store. Serialise the whole derived-field region on the config itself. The config
            //monitor is always taken after the instance monitor and never the other way round, so this cannot
            //invert a lock order.
            synchronized (config) {
            if (config.derivedFieldSnapshot == null) {
                config.derivedFieldSnapshot = new CountlyConfig.DerivedFieldSnapshot(config);
            } else {
                config.derivedFieldSnapshot.restoreOnto(config);
            }
            config.initialisedForNamespace = storageNamespace_;

            //set or create the CountlyStore
            if (config.countlyStore != null) {
                //we are running a test and using a mock object
                countlyStore = config.countlyStore;
            } else {
                countlyStore = new CountlyStore(config.context, L, config.explicitStorageModeEnabled, storageNamespace_);
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

            if (config.disableGradualRequestCleaner) {
                L.d("[Init] Disabling gradual request queue cleaning. Overflow will be removed in one pass.");
                countlyStore.setDisableGradualRequestCleaner(true);
            }

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
                        ImmediateRequestMaker maker = new ImmediateRequestMaker();
                        maker.useSerialExecutor = useSerialExecutorInternal;
                        return maker;
                    }

                    @Override public ImmediateRequestI CreatePreflightRequestMaker() {
                        PreflightRequestMaker maker = new PreflightRequestMaker();
                        maker.useSerialExecutor = useSerialExecutorInternal;
                        return maker;
                    }
                };
            }

            //captured before the default observer is derived below: an explicitly injected observer
            //(tests, embedders with their own lifecycle source) must stay authoritative for the
            //foreground seed too, ahead of the dispatcher's exact count
            final boolean lifecycleObserverInjected = config.lifecycleObserver != null;
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
            deviceInfo_ = new DeviceInfo(config.metricProviderOverride);
            deviceInfo_.L = L;
            config.deviceInfo = deviceInfo_;

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
                migrationParams.put(MigrationHelper.key_from_0_to_1_custom_id_value, config.deviceID);
                migrationParams.put(MigrationHelper.key_from_0_to_1_temp_id_enabled, config.temporaryDeviceIdEnabled);

                MigrationHelper mHelper = new MigrationHelper(config.storageProvider, L, context_, storageNamespace_.isEmpty());
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
            //these two are constructed before ModuleDeviceId exists, so their own field is still null;
            //fill it in here rather than have them read the shared config at request time
            moduleConfiguration.deviceIdProvider = config.deviceIdProvider;
            moduleHealthCheck.deviceIdProvider = config.deviceIdProvider;
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
                //Defensive copy: the config stores the caller's map by reference, and
                //addCustomNetworkRequestHeaders mutates this field in place. Two instances configured
                //from one map would otherwise share it, so adding an Authorization header to one
                //instance would send it to the other instance's server too.
                //It does NOT make the map thread-safe: addCustomNetworkRequestHeaders still mutates this same
                //map in place while ConnectionProcessor iterates it on the network executor. That race is
                //pre-existing and unchanged here.
                //The trade-off is a behaviour change: until 26.1.5 the SDK held the caller's own map and
                //re-read it before every request, so mutating it after init changed later requests. It no
                //longer does, so say so out loud rather than letting an app silently keep sending a stale
                //header (a rotated auth token being the case that matters).
                requestHeaderCustomValues = new HashMap<>(config.customNetworkRequestHeaders);
                L.i("[Countly] init, custom network request headers are copied at init: later changes to the map you passed to CountlyConfig will NOT be picked up. Use Countly.requestQueue().addCustomNetworkRequestHeaders(...) to change them while the SDK is running");

                connectionQueue_.setRequestHeaderCustomValues(requestHeaderCustomValues);
            }

            if (config.httpPostForced) {
                L.d("[Init] Setting HTTP POST to be forced");
                isHttpPostForced = config.httpPostForced;
            }

            if (config.tamperingProtectionSalt != null) {
                L.d("[Init] Enabling tamper protection");
            }

            //resolved value, not the config's: ModuleConfiguration has already layered the stored server
            //behaviour settings on top of what the developer configured
            if (moduleConfiguration.currentVDropAgeHours > 0) {
                L.d("[Init] Enabling drop older request threshold");
                countlyStore.setRequestAgeLimit(moduleConfiguration.currentVDropAgeHours);
            }

            if (config.pushIntentAddMetadata) {
                L.d("[Init] Enabling push intent metadata");
                addMetadataToPushIntents = config.pushIntentAddMetadata;
            }

            //resolved value, not the config's - see the drop-age comment above
            if (moduleConfiguration.currentVEventQueueSizeThreshold != null) {
                L.d("[Init] Setting event queue size: [" + moduleConfiguration.currentVEventQueueSizeThreshold + "]");

                if (moduleConfiguration.currentVEventQueueSizeThreshold < 1) {
                    L.d("[Init] queue size can't be less than zero");
                    moduleConfiguration.currentVEventQueueSizeThreshold = 1;
                }

                EVENT_QUEUE_SIZE_THRESHOLD = moduleConfiguration.currentVEventQueueSizeThreshold;
            }

            if (config.publicKeyPinningCertificates != null) {
                L.i("[Init] Enabling public key pinning");
                connectionQueue_.publicKeyPinCertificates = config.publicKeyPinningCertificates;
            }

            if (config.certificatePinningCertificates != null) {
                L.i("[Init] Enabling certificate pinning");
                connectionQueue_.certificatePinCertificates = config.certificatePinningCertificates;
            }

            //initialize networking queues
            connectionQueue_.cly = this;
            connectionQueue_.L = L;
            connectionQueue_.healthTracker = config.healthTracker;
            connectionQueue_.configProvider = config.configProvider;
            connectionQueue_.consentProvider = moduleConsent;
            connectionQueue_.moduleRequestQueue = moduleRequestQueue;
            connectionQueue_.deviceInfo = config.deviceInfo;
            connectionQueue_.pcc = config.pcc;
            connectionQueue_.setStorageProvider(config.storageProvider);
            connectionQueue_.setupSSLSocketFactory(config.customSSLSocketFactory);
            connectionQueue_.setBaseInfoProvider(config.baseInfoProvider);
            connectionQueue_.setDeviceId(config.deviceIdProvider);
            connectionQueue_.setRequestHeaderCustomValues(requestHeaderCustomValues);
            connectionQueue_.setMetricOverride(config.metricOverride);
            connectionQueue_.setContext(context_);
            final String requestSaltSnapshot = config.tamperingProtectionSalt;
            connectionQueue_.requestInfoProvider = new RequestInfoProvider() {
                //These are called from the network thread, outside any try block, for every request the
                //ConnectionProcessor drains. requestQueue() returns null as soon as sdkIsInitialised is
                //cleared, and teardown clears it while a processor is still finishing - the teardown flush
                //deliberately puts one in flight - so read the modules directly and fall back to the
                //configured values instead of throwing out of run() and stopping the drain.
                @Override public boolean isHttpPostForced() {
                    return moduleRequestQueue != null ? moduleRequestQueue.isHttpPostForcedInternal() : isHttpPostForced;
                }

                @Override public boolean isDeviceAppCrawler() {
                    //false when the module is gone: never DROP a queued request on the way out
                    return moduleRequestQueue != null && moduleRequestQueue.isDeviceAppCrawlerInternal();
                }

                @Override public boolean ifShouldIgnoreCrawlers() {
                    //true matches the field's own default
                    return moduleRequestQueue == null || moduleRequestQueue.ifShouldIgnoreCrawlersInternal();
                }

                @Override public int getRequestDropAgeHours() {
                    //read live on every send, so it must be this instance's resolved value and not a config
                    //field another instance may also be resolving into
                    return moduleConfiguration != null ? moduleConfiguration.currentVDropAgeHours : config.dropAgeHours;
                }

                @Override public String getRequestSalt() {
                    //frozen at init like the app key and server URL, and unlike the live reads above:
                    //those return values the SDK itself resolves per instance, while the salt is only
                    //ever set by the developer - reading it live off a (shareable) config would let a
                    //mutation made for another instance silently re-salt this instance's requests and
                    //stall its queue against a salt-enforcing server
                    return requestSaltSnapshot;
                }
            };

            //Cleared before the SDK counts as initialised, and unconditionally: the lifecycle gate reads both
            //flags, so a re-init must not leave a stale tearingDown behind even for an instance that has no
            //Application and therefore never joins the dispatcher.
            tearingDown = false;
            sdkIsInitialised = true;
            //AFTER THIS POINT THE SDK IS COUNTED AS INITIALISED

            if (instanceTrackingForTests) {
                // Test support only: remember this instance so the test runner can halt it (and stop
                // its leaked session-update timer) after the test that created it finishes.
                trackedInstancesForTests.add(this);
            }
            //set global application listeners
            int exactStartedActivityCount = -1;
            if (config.application != null) {
                //One process-wide registration, owned by CountlyLifecycleDispatcher, instead of a fresh
                //ActivityLifecycleCallbacks per instance. Registration is idempotent: CountlyInitProvider
                //normally does it before Application.onCreate, and this call covers an app that removed the
                //provider from its manifest.
                L.d("[Countly] Registering with the process-wide lifecycle dispatcher");
                CountlyLifecycleDispatcher.getInstance().register(config.application);
                //the returned snapshot is atomic with joining the dispatcher: this instance receives
                //exactly the events after the snapshot, so seeding from it can neither miss nor
                //double-count an activity start that races init
                exactStartedActivityCount = CountlyLifecycleDispatcher.getInstance().addInstance(this);
            } else {
                L.d("[Countly] Global activity listeners not registred due to no Application class");
                if (moduleSessions != null && moduleSessions.automaticSessionTrackingEnabled() && lifeCycleAtleastStarted()) {
                    //scoped to foreground init, which is the moment a session auto-begins (initFinished
                    //below): without the Application class the SDK never sees activity stops, so that
                    //session sends updates forever - background time included - unless the app calls
                    //onStop() itself. Loud, because the resulting damage (inflated session durations) is
                    //silent and server-side. A background init auto-begins nothing and stays quiet here.
                    L.w("[Countly] Automatic session tracking is enabled, the app is in the foreground, and no Application class was provided: a session will begin now, but the SDK cannot observe the activity lifecycle, so it will only end if onStop() is called manually. Provide the Application class on the config, or use config.enableManualSessionControl().");
                }
            }

            //foreground-seed precedence mirrors lifecycleStateAtLeastStartedInternal: an injected
            //observer and the test override are authoritative sources and must also drive the seed,
            //otherwise an instance would seed itself "foreground" from the dispatcher count while
            //every other foreground decision (auto session begin, timer heartbeat) says "background"
            if (exactStartedActivityCount >= 0 && !lifecycleObserverInjected && lifecycleStateOverrideForTests == null) {
                //the dispatcher was registered before the first activity (CountlyInitProvider), so this
                //is the exact number of currently started activities - unlike ProcessLifecycleOwner,
                //whose ~700ms stop-debounce can report "foreground" right after the app left it and
                //seed a phantom count that never drains (a session that never ends)
                L.d("[Countly] Seeding the activity counter from the lifecycle dispatcher: [" + exactStartedActivityCount + "] activities are started.");
                activityCount_ = exactStartedActivityCount;
                if (activityCount_ > 0) {
                    deviceInfo_.inForeground();
                }
            } else if (lifeCycleAtleastStarted()) {
                //no trustworthy exact count (no Application class, provider stripped from the
                //manifest) or an authoritative observer/override is present - use the observer chain
                L.d("[Countly] SDK detects that the app is in the foreground. Increasing the activity counter and setting the foreground state.");
                activityCount_++;
                deviceInfo_.inForeground();
            }

            // Seed modules with the current activity if the app is already in the foreground.
            // This handles frameworks (Flutter, React Native) and single-activity apps where
            // the host activity is already started before the SDK registers its lifecycle callbacks.
            // Priority: explicit initialActivity from config, then ContentProvider-tracked activity.
            Activity seedActivity = null;
            if (config.initialActivity != null && !config.initialActivity.isFinishing()) {
                seedActivity = config.initialActivity;
            } else {
                Activity holderActivity = CountlyActivityHolder.getInstance().getActivity();
                if (holderActivity != null && !holderActivity.isFinishing()) {
                    seedActivity = holderActivity;
                }
            }
            //cleared unconditionally, not just on the seeded path: a finishing activity left on the
            //config would be pinned by it for as long as the config lives, and a later init (of this or
            //another instance) must never re-seed a possibly destroyed activity
            config.initialActivity = null;

            if (seedActivity != null) {
                L.d("[Countly] Seeding modules with initial activity: [" + seedActivity.getClass().getSimpleName() + "]");
                for (ModuleBase module : modules) {
                    module.onInitialActivitySeeded(seedActivity);
                }
            }

            L.i("[Init] About to call module 'initFinished'");

            for (ModuleBase module : modules) {
                module.initFinished(config);
            }

            //Record what the SDK ended up writing onto the config. A later init of this instance (or of
            //another instance built from the same config) resets a value only if it still holds this, so the
            //SDK's own write-backs are undone while anything the developer changed in between is honoured.
            config.derivedFieldSnapshot.captureApplied(config);

            L.i("[Init] Finished initialising SDK");
            }
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
        if (lifecycleStateOverrideForTests != null) {
            return lifecycleStateOverrideForTests;
        }
        //the dispatcher's started-activity count is exact when CountlyInitProvider registered it
        //before the first activity - prefer it over ProcessLifecycleOwner, whose ~700ms
        //stop-debounce keeps reporting "foreground" for a while after the app left it
        CountlyLifecycleDispatcher dispatcher = CountlyLifecycleDispatcher.getInstance();
        if (dispatcher.hasExactActivityCount()) {
            return dispatcher.getStartedActivityCount() > 0;
        }
        return ProcessLifecycleOwner.get().getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
    }

    //synchronized: unlike the lifecycle events, this is NOT routed through the dispatcher's tearingDown
    //gate - the /o/sdk response lands via an async callback with no cancellation handle, so it can run
    //concurrently with tearDown() on another thread. The null-guard below only covers the sequential
    //case; without the instance monitor a teardown could shut the timer down between the guard and
    //startTimerService (RejectedExecutionException on a shut-down executor), null moduleConfiguration
    //under the dereferences below, or clear the modules list mid-iteration. Lock order stays
    //instance -> config: the only synchronous caller (init) already holds the instance monitor. No
    //wait-while-holding is introduced: tearDown only shuts the timer down, the drain is outside it.
    synchronized void onSdkConfigurationChanged(@NonNull CountlyConfig config) {
        L.i("[Countly] onSdkConfigurationChanged");

        if (config_ == null) {
            L.e("[Countly] onSdkConfigurationChanged, config is null");
            return;
        }

        //Nothing ever nulls config_, but tearDown nulls moduleConfiguration - and the resolved settings below
        //are read off it, and it can land after removeInstance()/halt() and would otherwise dereference null
        //and crash the host app.
        if (moduleConfiguration == null) {
            L.d("[Countly] onSdkConfigurationChanged, this instance was torn down before the response arrived, ignoring it");
            return;
        }

        //Read the settings this instance resolved, not the config: the config may be shared with another
        //instance and is no longer written to by the SDK.
        setLoggingEnabled(moduleConfiguration.currentVLoggingEnabled);

        long timerDelay = TIMER_DELAY_IN_SECONDS;
        if (moduleConfiguration.currentVSessionUpdateTimerDelay != null) {
            timerDelay = moduleConfiguration.currentVSessionUpdateTimerDelay;
        }

        startTimerService(timerService_, timerFuture, timerDelay);

        moduleConfiguration.currentVMaxRequestQueueSize = Math.max(moduleConfiguration.currentVMaxRequestQueueSize, 1);
        countlyStore.setLimits(moduleConfiguration.currentVMaxRequestQueueSize);

        moduleConfiguration.currentVDropAgeHours = Math.max(moduleConfiguration.currentVDropAgeHours, 0);
        if (moduleConfiguration.currentVDropAgeHours > 0) {
            countlyStore.setRequestAgeLimit(moduleConfiguration.currentVDropAgeHours);
        }

        if (moduleConfiguration.currentVEventQueueSizeThreshold != null) {
            moduleConfiguration.currentVEventQueueSizeThreshold = Math.max(moduleConfiguration.currentVEventQueueSizeThreshold, 1);
            EVENT_QUEUE_SIZE_THRESHOLD = moduleConfiguration.currentVEventQueueSizeThreshold;
        }

        // Have a look at the SDK limit values. These are this instance's own limits, which
        // ModuleConfiguration has just written the server-resolved values into - the config object the
        // developer handed us is never touched, so a config shared by two instances stays clean.
        sdkInternalLimits_.clampToMinimums();

        for (ModuleBase module : modules) {
            module.onSdkConfigurationChanged(config);
        }
    }

    /**
     * Immediately disables session and event tracking and clears any stored session and event data.
     * Testing Purposes Only!
     *
     * This will destroy all stored data, including the device ID and its generated-UUID cache, so the
     * next init starts as a new user. Only an instance that was initialised in this process run has a
     * store to clear - called before init, this resets the object but leaves earlier runs' files on disk.
     */
    public void halt() {
        //NOT synchronized: the quiesce below hops through the main looper, and the main thread may be inside
        //a lifecycle dispatch at that moment. Holding this instance's monitor while waiting for that hop
        //would deadlock until the timeout. tearDown is itself synchronized, so the critical section is still
        //serialised - only the quiesce and the timer drain sit outside it, which is the whole point.
        unsubscribeFromLifecycleBeforeTeardown();
        ScheduledExecutorService timerToDrain = tearDown(true);
        awaitTimerServiceTermination(timerToDrain);
    }

    /**
     * Stops this instance without touching its stored data: session and event tracking are disabled, the
     * timer and worker threads are released, the process-global callbacks are unregistered, and the
     * modules are torn down - but the request queue, event queue, device ID and consent state stay on
     * disk, so a later {@code init(config)} on this instance picks up exactly where it left off and
     * anything not yet sent to the server is still sent.
     * <p>
     * This is what {@link #removeInstance(String)} uses. {@link #halt()} is the same teardown plus a wipe.
     */
    void stopWithoutClearingData() {
        //see halt() for why this is not synchronized
        unsubscribeFromLifecycleBeforeTeardown();
        ScheduledExecutorService timerToDrain = tearDown(false);
        awaitTimerServiceTermination(timerToDrain);
    }

    /**
     * Drops this instance's lifecycle subscription and waits until no lifecycle event can be in flight toward
     * it. MUST run before {@link #tearDown} takes the instance monitor - see
     * {@code CountlyLifecycleDispatcher#removeInstanceAndQuiesce}.
     */
    private void unsubscribeFromLifecycleBeforeTeardown() {
        tearingDown = true;
        CountlyLifecycleDispatcher.getInstance().removeInstanceAndQuiesce(this, L);
    }

    /**
     * Drains an already-shut-down timer service, WITHOUT the instance monitor. That is what makes the wait
     * safe: a tick blocked on the monitor can acquire it, see the torn-down state, no-op and let the await
     * finish. Inside the monitor the same wait was a self-deadlock that burned its full timeout, because the
     * tick it waited for could not start until teardown returned - and entering a synchronized block is not
     * interruptible, so shutdownNow() could not break it either.
     */
    private void awaitTimerServiceTermination(@Nullable ScheduledExecutorService service) {
        if (service == null) {
            return;
        }
        try {
            if (!service.awaitTermination(1, TimeUnit.SECONDS)) {
                service.shutdownNow();
                if (!service.awaitTermination(1, TimeUnit.SECONDS)) {
                    L.e("[Countly] awaitTimerServiceTermination, the global timer must be locked");
                }
            }
        } catch (Throwable t) {
            L.e("[Countly] awaitTimerServiceTermination, error while stopping the global timer " + t);
        }
    }

    /**
     * Gets everything that only exists in memory into the store, so a teardown that promises to keep this
     * instance's data actually keeps it. Each step is guarded and wrapped: a teardown must complete even if
     * one of these fails, otherwise the instance is left half torn down.
     */
    private void flushInFlightStateBeforeTeardown() {
        //Views FIRST. Stopping a view records its duration into the EVENT queue, and ending the session is
        //what drains that queue into the request queue (ModuleSessions#endSessionInternal calls
        //sendEventsIfNeeded). Ending the session first would leave every view-end event stranded in the event
        //queue with nothing left to flush it.
        try {
            if (moduleViews != null) {
                L.d("[Countly] tearDown, stopping open views so their durations are recorded");
                moduleViews.stopAllViewsInternal(null);
            }
        } catch (Throwable t) {
            L.w("[Countly] tearDown, failed to stop the open views, [" + t + "]");
        }

        try {
            if (moduleSessions != null && moduleSessions.sessionRunning) {
                L.d("[Countly] tearDown, ending the open session so it is not left open");
                //the consent-checking variant, so a teardown never sends what the app did not agree to
                moduleSessions.endSessionInternal();
            }
        } catch (Throwable t) {
            L.w("[Countly] tearDown, failed to end the open session, [" + t + "]");
        }

        try {
            //endSessionInternal already saves the profile when a session was running; this covers the case
            //where none was. saveInternal is a no-op when there is nothing pending.
            if (moduleUserProfile != null) {
                L.d("[Countly] tearDown, saving pending user profile changes");
                moduleUserProfile.saveInternal();
            }
        } catch (Throwable t) {
            L.w("[Countly] tearDown, failed to save the pending user profile changes, [" + t + "]");
        }

        try {
            //In explicit storage mode the request and event queues live only in memory until something asks
            //for them to be persisted, and after this teardown nothing can: requestQueue() returns null once
            //the instance is no longer initialised. This runs LAST so it captures everything the flush above
            //queued. A request the in-flight processor acknowledges after this point stays in the persisted
            //queue and is retried on the next init - a duplicate is better than a silent loss, and the server
            //deduplicates on the request id.
            if (countlyStore != null && config_ != null && config_.explicitStorageModeEnabled) {
                L.d("[Countly] tearDown, writing the explicit-storage-mode caches to persistence");
                countlyStore.esWriteCacheToStorage(null);
            }
        } catch (Throwable t) {
            L.w("[Countly] tearDown, failed to persist the explicit storage mode caches, [" + t + "]");
        }
    }

    /**
     * @param clearStoredData whether to also erase this instance's persisted data. The teardown itself is
     * identical either way; only {@link #halt()} destroys data.
     */
    private synchronized ScheduledExecutorService tearDown(boolean clearStoredData) {
        //Lifecycle events were already stopped AND quiesced by the caller, outside this monitor, so by now no
        //dispatch can be in flight toward this instance. The flag is set there too; it stays as the backstop
        //for what quiesce cannot cover - a late registration (provider stripped from the manifest) and a
        //wedged main looper that never ran the drain hop.
        tearingDown = true;

        L.i("Halting Countly!" + (clearStoredData ? " Stored data will be cleared." : " Stored data is kept."));

        //When the data is being kept (removeInstance), flush what is still only in memory BEFORE anything is
        //torn down: the module halts below only clear flags, so an open session would never get its
        //end_session, open views would lose their duration, and pending profile edits would be dropped -
        //and a later init of this instance would then send a second begin_session with no end in between.
        //Skipped for halt(), which is about to erase the store anyway.
        if (!clearStoredData) {
            flushInFlightStateBeforeTeardown();
        }

        sdkIsInitialised = false;
        L.SetListener(null);

        //shut the timer down without waiting; the drain happens in the caller's epilogue, after this monitor
        //is released. A tick that already started blocks here, then no-ops - onTimer rechecks isInitialized().
        ScheduledExecutorService timerToDrain = timerService_;
        if (timerToDrain != null) {
            L.i("[Countly] tearDown, stopping the global timer");
            timerToDrain.shutdown();
        }

        if (connectionQueue_ != null) {
            if (clearStoredData && countlyStore != null) {
                countlyStore.clear();
            }
            connectionQueue_.setContext(null);
            //init builds a fresh ConnectionQueue, so release this one's worker threads instead of
            //stranding them; they are non-daemon and would otherwise outlive every halt/init cycle.
            connectionQueue_.shutdownExecutors();
            connectionQueue_ = null;
        }

        activityCount_ = 0;

        for (ModuleBase module : modules) {
            module.halt();
        }
        modules.clear();

        //A dispatch that passed the tearingDown gate a moment before this method set it can still be running
        //on the main thread while these fields go null, and modules reach each other through _cly. That is
        //what crashed a CI run (ModuleSessions.endSessionInternal -> _cly.moduleViews.resetFirstView()).
        //Rather than lock - teardown cannot wait for in-flight dispatches while holding this monitor, because
        //onConfigurationChangedInternal is synchronized on the same instance and would deadlock against a
        //main thread already blocked on it - every cross-module read now snapshots the sibling into a local
        //and checks it, so nulling below cannot produce an NPE. If a new one is added, snapshot it too:
        //`if (_cly.moduleX != null) { _cly.moduleX.y(); }` is NOT enough, the field can go null between the
        //check and the use.
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

        // Reset configuration values that may have been changed during runtime
        loggingForcedOffForProduction = false;
        EVENT_QUEUE_SIZE_THRESHOLD = 100;

        COUNTLY_SDK_VERSION_STRING = DEFAULT_COUNTLY_SDK_VERSION_STRING;
        COUNTLY_SDK_NAME = DEFAULT_COUNTLY_SDK_NAME;

        connectionQueue_ = new ConnectionQueue();
        timerService_ = Executors.newSingleThreadScheduledExecutor();

        return timerToDrain;
    }

    synchronized void notifyDeviceIdChange(boolean withoutMerge) {
        L.d("Notifying modules that device ID changed");

        for (ModuleBase module : modules) {
            module.deviceIdChanged(withoutMerge);
        }
    }

    /**
     * Lifecycle dispatch entry points, called by {@link CountlyLifecycleDispatcher} on the main thread.
     * <p>
     * Each one gates on {@code tearingDown} and on the SDK being initialised, so an event that arrives while
     * this instance is being destroyed is dropped rather than reaching half-nulled state. That is the whole
     * point of the gate: the alternative was an NPE escaping {@code Activity.onStop}, which Android turns
     * into a host-app crash.
     */
    //No module consumes these four callbacks - the module loops were already commented out before the
    //dispatcher existed. They are kept as log-only so the SDK's log output, which support reads off
    //customer devices, is byte-identical to what the per-instance callbacks produced.
    void dispatchActivityCreated(@NonNull Activity activity) {
        if (tearingDown || !sdkIsInitialised) {
            return;
        }
        if (L.logEnabled()) {
            L.d("[Countly] onActivityCreated, " + activity.getClass().getSimpleName());
        }
    }

    void dispatchActivityPaused(@NonNull Activity activity) {
        if (tearingDown || !sdkIsInitialised) {
            return;
        }
        if (L.logEnabled()) {
            L.d("[Countly] onActivityPaused, " + activity.getClass().getSimpleName());
        }
    }

    void dispatchActivitySaveInstanceState(@NonNull Activity activity) {
        if (tearingDown || !sdkIsInitialised) {
            return;
        }
        if (L.logEnabled()) {
            L.d("[Countly] onActivitySaveInstanceState, " + activity.getClass().getSimpleName());
        }
    }

    void dispatchLowMemory() {
        if (tearingDown || !sdkIsInitialised) {
            return;
        }
        L.d("[Countly] ComponentCallbacks, onLowMemory");
    }

    void dispatchActivityStarted(@NonNull Activity activity) {
        if (tearingDown || !sdkIsInitialised) {
            return;
        }
        if (L.logEnabled()) {
            L.d("[Countly] onActivityStarted, " + activity.getClass().getSimpleName());
        }
        onStartInternal(activity);
    }

    void dispatchActivityResumed(@NonNull Activity activity) {
        if (tearingDown || !sdkIsInitialised) {
            return;
        }
        if (L.logEnabled()) {
            L.d("[Countly] onActivityResumed, " + activity.getClass().getSimpleName());
        }
        //Hardcoded, in the order init adds the modules to `modules` - see the note on
        //ModuleBase#callbackOnActivityResumed
        if (moduleRatings != null) {
            moduleRatings.callbackOnActivityResumed(activity);
        }
        if (moduleAPM != null) {
            moduleAPM.callbackOnActivityResumed(activity);
        }
    }

    void dispatchActivityStopped(@NonNull Activity activity) {
        if (tearingDown || !sdkIsInitialised) {
            return;
        }
        if (L.logEnabled()) {
            L.d("[Countly] onActivityStopped, " + activity.getClass().getSimpleName());
        }
        onStopInternal();
        //hardcoded on purpose - see the note on ModuleBase#callbackOnActivityStopped
        if (moduleAPM != null) {
            moduleAPM.callbackOnActivityStopped(activity);
        }
    }

    void dispatchActivityDestroyed(@NonNull Activity activity) {
        if (tearingDown || !sdkIsInitialised) {
            return;
        }
        if (L.logEnabled()) {
            L.d("[Countly] onActivityDestroyed, " + activity.getClass().getSimpleName());
        }
        //Hardcoded, in the order init adds the modules to `modules` - see the note on
        //ModuleBase#onActivityDestroyed
        if (moduleFeedback != null) {
            moduleFeedback.onActivityDestroyed(activity);
        }
        if (moduleContent != null) {
            moduleContent.onActivityDestroyed(activity);
        }
    }

    void dispatchConfigurationChanged(@NonNull Configuration configuration) {
        if (tearingDown || !sdkIsInitialised) {
            return;
        }
        L.d("[Countly] ComponentCallbacks, onConfigurationChanged");
        onConfigurationChangedInternal(configuration);
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
        if (activityCount_ == 1) {
            // start the timer in the first activity
            if (moduleConfiguration != null) {
                moduleConfiguration.fetchIfTimeIsUpForFetchingServerConfig();
            }
            //if we open the first activity
            //and automatic session tracking is active (resolved through the SBS precedence chain),
            //begin a session
            if (moduleSessions != null && moduleSessions.automaticSessionTrackingEnabled()) {
                moduleSessions.beginSessionInternal();
            }
        }

        deviceInfo_.inForeground();

        //Hardcoded rather than iterating the mutable modules list: teardown clears that list from another
        //thread, which used to mean a ConcurrentModificationException or an NPE on the main thread.
        //Adding a module that overrides this hook means wiring it in here - see ModuleBase#onActivityStarted.
        if (moduleViews != null) {
            moduleViews.onActivityStarted(activity, activityCount_);
        }
        if (moduleAPM != null) {
            moduleAPM.onActivityStarted(activity, activityCount_);
        }
        if (moduleFeedback != null) {
            moduleFeedback.onActivityStarted(activity, activityCount_);
        }
        if (moduleContent != null) {
            moduleContent.onActivityStarted(activity, activityCount_);
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
        if (activityCount_ == 0 && moduleSessions != null && moduleSessions.automaticSessionTrackingEnabled()) {
            // if automatic session tracking is active
            // Called when final Activity is stopped.
            // Sends an end session event to the server, also sends any unsent custom events.
            moduleSessions.endSessionInternal();
        }

        deviceInfo_.inBackground();

        //Hardcoded - see ModuleBase#onActivityStopped
        if (moduleViews != null) {
            moduleViews.onActivityStopped(activityCount_);
        }
        if (moduleFeedback != null) {
            moduleFeedback.onActivityStopped(activityCount_);
        }
        if (moduleContent != null) {
            moduleContent.onActivityStopped(activityCount_);
        }
        if (moduleHealthCheck != null) {
            moduleHealthCheck.onActivityStopped(activityCount_);
        }
    }

    public synchronized void onConfigurationChangedInternal(Configuration newConfig) {
        L.i("Calling [onConfigurationChangedInternal]");

        //Hardcoded - see ModuleBase#onConfigurationChanged
        if (moduleViews != null) {
            moduleViews.onConfigurationChanged(newConfig);
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
            if (appIsInForeground && moduleSessions.automaticSessionTrackingEnabled()) {
                //if we have automatic session control and we are in the foreground, record an update
                moduleSessions.updateSessionInternal();
            } else if (!moduleSessions.automaticSessionTrackingEnabled() && moduleSessions.manualSessionControlHybridModeEnabled && moduleSessions.sessionIsRunning()) {
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
        //CountlyPush assumes the SDK is already initialised, but it is driven by an OS callback that can
        //arrive at any time - so check instead of dereferencing a config that may not exist yet.
        //Locals, not the fields: this runs on the push provider's thread, and a concurrent
        //halt()/removeInstance() nulls these fields between any check here and their use below - the
        //locals make the check-then-use atomic without taking the instance monitor on an OS callback.
        ModuleConsent consent = moduleConsent;
        ConnectionQueue queue = connectionQueue_;
        if (!isInitialized() || consent == null || queue == null) {
            L.w("[onRegistrationId] Calling this before the SDK is initialized.");
            return;
        }

        //read consent off this instance's own module, never off the config object (which a developer may
        //have handed to another instance)
        if (!consent.getConsent(CountlyFeatureNames.push)) {
            return;
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

        queue.tokenSession(registrationId, provider);
    }

    public void setLoggingEnabled(final boolean enableLogging) {
        if (enableLogging && loggingForcedOffForProduction) {
            //logging is suppressed for production builds, keep console output off
            enableLogging_ = false;
            L.setLoggingEnabled(false);
            return;
        }
        enableLogging_ = enableLogging;
        //mirror the resolved flag into this instance's logger so console output is gated per-instance
        L.setLoggingEnabled(enableLogging_);
        L.d("Enabling logging");
    }

    /**
     * Decide whether the SDK must keep console logging off because the host app is a
     * production (non-debuggable) build and logging-in-production was disabled in config.
     *
     * @param config the provided init configuration
     * @return true if console logging must be forced off
     */
    private boolean shouldForceLoggingOffForProduction(@NonNull CountlyConfig config) {
        if (!config.disableSDKLoggingInProduction) {
            return false;
        }

        Context context = config.context != null ? config.context : config.application;
        if (context == null) {
            //without a context we can not tell the build type, so do not force anything off
            return false;
        }

        return !Utils.isAppInDebuggableMode(context);
    }

    /**
     * To add new header key/value pairs or override existing ones.
     * A null or empty map is ignored. Null or empty keys, as well as null values, are ignored.
     * Subsequent requests (including those created after overriding) will contain the updated header set.
     *
     * @param customHeaderValues map of header key/value pairs to add/override
     * @return Returns the same Countly instance for convenient chaining
     */
    /* package */
    synchronized void addCustomNetworkRequestHeaders(Map<String, String> customHeaderValues) {
        if (!isInitialized()) {
            L.e("[addCustomNetworkRequestHeaders] SDK must be initialised before calling this method");
            return;
        }

        if (customHeaderValues == null || customHeaderValues.isEmpty()) {
            L.d("[addCustomNetworkRequestHeaders] Provided map was null or empty, ignoring");
            return;
        }

        if (requestHeaderCustomValues == null) {
            requestHeaderCustomValues = new HashMap<>();
        }

        int added = 0;
        int overridden = 0;
        for (Map.Entry<String, String> entry : customHeaderValues.entrySet()) {
            String k = entry.getKey();
            String v = entry.getValue();
            if (k == null || k.isEmpty() || v == null) {
                continue; // skip invalid entries
            }
            if (requestHeaderCustomValues.containsKey(k)) {
                overridden++;
            } else {
                added++;
            }
            requestHeaderCustomValues.put(k, v);
        }

        connectionQueue_.setRequestHeaderCustomValues(requestHeaderCustomValues);
        L.i("[addCustomNetworkRequestHeaders] Added:" + added + " Overridden:" + overridden + " TotalNow:" + requestHeaderCustomValues.size());
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
