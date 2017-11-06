package ly.count.android.sdk;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;

import ly.count.android.sdk.internal.Core;
import ly.count.android.sdk.internal.Log;

/**
 * Created by artem on 07/10/2017.
 */

public class CountlyLifecycle {
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
        }
    }

    /**
     * Stop Countly SDK. Stops all tasks and releases resources.
     * Waits for some tasks to complete, might block for some time.
     * Also clears all the data if called with {@code clearData = true}.
     *
     * @param context Context to run in
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
