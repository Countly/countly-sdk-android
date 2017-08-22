package ly.count.android.sdk.internal;


import ly.count.android.sdk.Config;
import ly.count.android.sdk.Session;

/**
 * Module of Countly SDK.
 * Contract:
 * <ul>
 *     <li>Module instances must provide empty constructor with no parameters.</li>
 *     <li>Module class instance can be accessed only through this interface.</li>
 *     <li>Module class instance encapsulates all module-specific logic inside.</li>
 *     <li>Module cannot acquire instance or call another Module.</li>
 * </ul>
 */
interface Module {
    /**
     * All initialization must be done in this method, not constructor.
     * This method is guaranteed to be run right after constructor with no module-related actions in between.
     *
     * @param config Countly configuration object: can be stored locally if needed.
     * @throws IllegalArgumentException in case supplied {@link InternalConfig} is not consistent.
     */
    void init (InternalConfig config) throws IllegalArgumentException;

    /**
     * App user decided to opt out from analytics or developer changed important preferences.
     * Clear all module-related data, close any resources and prepare to start from clean sheet.
     * This method is guaranteed to be the latest method call to this module instance.
     *
     * @param config Countly configuration object, must not be stored.
     */
    void clear (InternalConfig config);

    /**
     * SDK got a first context. Either application or service has been started.
     *
     * @param context {@link Context} with application or context set
     */
    void onContextAcquired(Context context);

    /**
     * Device ID has been acquired from device id provider.
     * Can be invoked multiple times throughout Module lifecycle.
     * Parameters can be instance equal (==), meaning that id haven't changed.
     *
     * @param deviceId deviceId valid from now on
     * @param oldDeviceId deviceId valid previously if any
     */
    void onDeviceId(Config.DID deviceId, Config.DID oldDeviceId);

    /**
     * Activity is being created.
     *
     * @param context {@link Context} with activity set
     */
    void onActivityCreated (Context context);

    /**
     * Activity is being launched.
     * @param context {@link Context} with activity set
     */
    void onActivityStarted (Context context);

    /**
     * Activity is being resumed.
     *
     * @param context {@link Context} with activity set
     */
    void onActivityResumed (Context context);

    /**
     * Activity is being paused.
     *
     * @param context {@link Context} with activity set
     */
    void onActivityPaused (Context context);

    /**
     * Activity is being stopped.
     *
     * @param context {@link Context} with activity set
     */
    void onActivityStopped (Context context);

    /**
     * Activity is saving state.
     *
     * @param context {@link Context} with activity set
     */
    void onActivitySaveInstanceState(Context context);

    /**
     * Activity is being destroyed.
     *
     * @param context {@link Context} with activity set
     */
    void onActivityDestroyed (Context context);

    /**
     * Session is started.
     *
     * @param session session which began
     */
    void onSessionBegan(Session session, Context context);

    /**
     * Session is started.
     *
     * @param session session which ended
     */
    void onSessionEnded (Session session, Context context);
}
