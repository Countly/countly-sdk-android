package ly.count.sdk.android;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;

import ly.count.sdk.Countly;
import ly.count.sdk.android.internal.Core;
import ly.count.sdk.android.internal.CtxImpl;
import ly.count.sdk.android.internal.SDK;
import ly.count.sdk.internal.Log;
import ly.count.sdk.Config;

/**
 * Lifecycle-related methods.
 */

public class CountlyLifecycle extends Cly {
    /**
     * Ctx stored for legacy methods.
     */
    @Deprecated
    protected Context legacyContext = null;

    protected static final Log.Module L = Log.module("Countly");
    protected static Countly instance;
    protected final Core core;

    public CountlyLifecycle(Core core) {
        this.core = core;
    }

    /**
     * Initialize Countly.
     * To be called from {@link Application#onCreate()}.
     *
     * @param application Application instance
     * @param config configuration object
     */
    public static void init (final Application application, final Config config) {
        Cly.init(new CtxImpl(new SDK(), config, application));
        if (config == null) {
            L.wtf("Config cannot be null");
        } else {
            if (instance != null) {
                L.wtf("Countly shouldn't be initialized twice. Please either use Countly.isInitialized() to check status or call Countly.stop() before second Countly.init().");
                stop(application, false);
            }

            Core core = Core.init(config, application);
            if (core == null) {
                // TODO: inconsistent state, couldn't init, TBD
                return;
            }
            instance = new Countly(core);
            instance.legacyContext = application;
        }
    }

    /**
     * Stop Countly SDK. Stops all tasks and releases resources.
     * Waits for some tasks to complete, might block for some time.
     * Also clears all the data if called with {@code clearData = true}.
     *
     * @param context Ctx to run in
     * @param clearData whether to clear all Countly data or not
     */
    public static void stop (final Context context, boolean clearData) {
        if (instance != null) {
            instance.core.stop(context, clearData);
            instance = null;
        } else {
            Log.wtf("Countly isn't initialized to stop it");
        }
    }

    /**
     * Returns whether Countly SDK has been already initialized or not.
     *
     * @return true if already initialized
     */
    public static boolean isInitialized() { return instance != null; }

    /**
     * Returns whether Countly SDK has been given consent to record data for a particular {@link Config.Feature} or not.
     *
     * @return true if consent has been given
     */
    public static boolean isTracking(Config.Feature feature) { return isInitialized() && instance.core.isTracking(feature); }

    /**
     * Activity callback to be called for apps which support API levels below 14
     *
     * @param activity Activity instance
     * @param bundle Bundle instance
     */
    public static void onActivityCreated(Activity activity, Bundle bundle) {
        if (!isInitialized()) {
            Log.wtf("Countly isn't initialized yet");
        } else {
            // TODO: deep links
            instance.core.onActivityCreated(activity, bundle);
        }
    }

    /**
     * Activity callback to be called for apps which support API levels below 14
     *
     * @deprecated since 18.X, use {@link #onActivityCreated(Activity, Bundle)} instead
     * @param activity Activity instance
     */
    @Deprecated
    public static void onCreate(Activity activity) {
        onActivityCreated(activity, null);
    }

    /**
     * Activity callback to be called for apps which support API levels below 14
     *
     * @param activity Activity instance
     */
    public static void onActivityStarted(Activity activity) {
        if (!isInitialized()) {
            Log.wtf("Countly isn't initialized yet");
        } else {
            instance.core.onActivityStarted(activity);
        }
    }

    /**
     * Activity callback to be called for apps which support API levels below 14
     *
     * @deprecated since 18.X, use {@link #onActivityStarted(Activity)}} instead
     * @param activity Activity instance
     */
    @Deprecated
    public static void onStart(Activity activity) {
        onActivityStarted(activity);
    }

    /**
     * Activity callback to be called for apps which support API levels below 14
     *
     * @param activity Activity instance
     */
    public static void onActivityStopped(Activity activity) {
        if (!isInitialized()) {
            Log.wtf("Countly isn't initialized yet");
        } else {
            instance.core.onActivityStarted(activity);
        }
    }

    /**
     * Activity callback to be called for apps which support API levels below 14
     *
     * @deprecated since 18.X, use {@link #onActivityStopped(Activity)}} instead
     * @param activity Activity instance
     */
    @Deprecated
    public static void onStop(Activity activity) {
        onActivityStopped(activity);
    }
}
