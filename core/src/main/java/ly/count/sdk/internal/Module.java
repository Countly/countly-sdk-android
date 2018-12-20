package ly.count.sdk.internal;

import org.json.JSONObject;

import java.util.Set;

import ly.count.sdk.ConfigCore;
import ly.count.sdk.Session;

/**
 * Module of Countly SDK.
 * Contract:
 * <ul>
 *     <li>Module instances must provide empty constructor with no parameters.</li>
 *     <li>Module instance can be accessed only through this interface (static methods allowed for encapsulation).</li>
 *     <li>Module instance encapsulates all module-specific logic inside.</li>
 *     <li>Module cannot acquire instance or call another Module.</li>
 * </ul>
 */
public interface Module {
    /**
     * All initialization must be done in this method, not constructor.
     * This method is guaranteed to be run right after constructor with no module-related actions in between.
     *
     * @param config Countly configuration object: can be stored locally if needed.
     * @throws IllegalArgumentException in case supplied {@link InternalConfig} is not consistent.
     * @throws IllegalStateException if some required for this module platform feature is not available on this platform.
     */
    void init (InternalConfig config) throws IllegalArgumentException, IllegalStateException;

    /**
     * App user decided to opt out from analytics or developer changed important preferences.
     * Clear all module-related data, close any resources and prepare to start from clean sheet.
     * This method is guaranteed to be the latest method call to this module instance.
     * <ul>
     *     <li>Stop all tasks, clear all context references.</li>
     *     <li>Not a single function call can be fired from this object after the method returns.</li>
     *     <li>Remove all module-related {@link Storable} files if {@code clear} is {@code true}</li>
     * </ul>
     *
     * @param ctx {@link Ctx} to run in
     * @param clear {@code true} if module must clear it's data files, {@code false} otherwise
     */
    void stop(Ctx ctx, boolean clear);

    /**
     * A method to be used by module itself to determine if it was initialized by {@link #init(InternalConfig)}
     * and haven't been stopped yet by {@link #stop(Ctx, boolean)}.
     *
     * @return {@code true} if module is allowed to continue to run, {@code false} otherwise
     */
    boolean isActive();

    /**
     * SDK got a first context. Called only in main mode (from {@code Application#onCreate()})
     *
     * @param ctx {@link Ctx} with application instance
     */
    void onContextAcquired(Ctx ctx);

    /**
     * SDK got a first context. Called only in {@link InternalConfig#limited} mode,
     * that is from {@code CountlyService} or {@code android.content.BroadcastReceiver}.
     *
     * @param ctx {@link Ctx} with application context instance
     */
    void onLimitedContextAcquired(Ctx ctx);

    /**
     * Device ID has been acquired from device id provider.
     * Can be invoked multiple times throughout Module lifecycle.
     * Parameters can be instance equal (==), meaning that id haven't changed.
     *
     * @param ctx Ctx to run in
     * @param deviceId deviceId valid from now on
     * @param oldDeviceId deviceId valid previously if any
     */
    void onDeviceId(Ctx ctx, ConfigCore.DID deviceId, ConfigCore.DID oldDeviceId);

    /**
     * Activity is being created.
     *
     * @param ctx {@link Ctx} with activity set
     */
    void onActivityCreated (Ctx ctx);

    /**
     * Activity is being launched.
     * @param ctx {@link Ctx} with activity set
     */
    void onActivityStarted (Ctx ctx);

    /**
     * Activity is being resumed.
     *
     * @param ctx {@link Ctx} with activity set
     */
    void onActivityResumed (Ctx ctx);

    /**
     * Activity is being paused.
     *
     * @param ctx {@link Ctx} with activity set
     */
    void onActivityPaused (Ctx ctx);

    /**
     * Activity is being stopped.
     *
     * @param ctx {@link Ctx} with activity set
     */
    void onActivityStopped (Ctx ctx);

    /**
     * Activity is saving state.
     *
     * @param ctx {@link Ctx} with activity set
     */
    void onActivitySaveInstanceState(Ctx ctx);

    /**
     * Activity is being destroyed.
     *
     * @param ctx {@link Ctx} with activity set
     */
    void onActivityDestroyed (Ctx ctx);

    /**
     * Session is started.
     *
     * @param session session which began
     */
    void onSessionBegan(Session session, Ctx ctx);

    /**
     * Session is ended.
     *
     * @param session session which ended
     */
    void onSessionEnded (Session session, Ctx ctx);

    /**
     * User object has been changed.
     *
     * @param ctx
     * @param changes object with all the changes going to be sent to the server
     * @param cohortsAdded set of cohorts this user has just been added to
     * @param cohortsRemoved set of cohorts this user has just been removed from
     */
    void onUserChanged(Ctx ctx, JSONObject changes, Set<String> cohortsAdded, Set<String> cohortsRemoved);

    /**
     * This method is called only on owning module only if module marks request as owned ({@link Request#own(Class)}.
     * Gives a module another chance to modify request before sending. Being run in {@code CountlyService}.
     *
     * @param request request to check
     * @return {@code true} if ok to send now, {@code false} if not ok to (remove request
     * from queue), {@code null} if cannot decide yet
     */
    Boolean onRequest (Request request);

    /**
     * Called when {@code android.content.res.Configuration} changes.
     *
     * @param ctx {@link Ctx} with only context set
     */
    void onConfigurationChanged(Ctx ctx);

    /**
     * @return Module feature index if any
     */
    Integer getFeature();
}
