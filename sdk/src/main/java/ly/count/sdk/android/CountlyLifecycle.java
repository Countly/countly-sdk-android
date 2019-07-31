package ly.count.sdk.android;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;

import ly.count.sdk.Cly;
import ly.count.sdk.android.internal.CtxImpl;
import ly.count.sdk.android.internal.SDK;
import ly.count.sdk.internal.InternalConfig;
import ly.count.sdk.internal.Log;

/**
 * Lifecycle-related methods.
 */

public abstract class CountlyLifecycle extends Cly {
    /**
     * Ctx stored for legacy methods.
     */
    @Deprecated
    protected Context legacyContext = null;

    protected static final Log.Module L = Log.module("Countly");

    protected CountlyLifecycle() {
        super();
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
        } else if (application == null) {
            L.wtf("Application cannot be null");
        } else {
            if (cly != null) {
                L.wtf("Countly shouldn't be initialized twice. Please either use Countly.isInitialized() to check status or call Countly.stop() before second Countly.init().");
                stop(application, false);
            }

            SDK sdk = new SDK();
            sdk.init(new CtxImpl(sdk, new InternalConfig(config), application));

            // config has been changed, thus recreating ctx
            cly = new Countly(sdk, new CtxImpl(sdk, sdk.config(), application));
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
        if (cly != null) {
            ((Countly)cly).sdk.stop(new CtxImpl(((Countly)cly).sdk, ((Countly)cly).sdk.config(), context), clearData);
            cly = null;
        } else {
            Log.wtf("Countly isn't initialized to stop it");
        }
    }

    /**
     * Returns whether Countly SDK has been already initialized or not.
     *
     * @return true if already initialized
     */
    public static boolean isInitialized() { return cly != null; }

    /**
     * Returns whether Countly SDK has been given consent to record data for a particular {@link Config.Feature} or not.
     *
     * @return true if consent has been given
     */
    public static boolean isTracking(Config.Feature feature) { return isInitialized() && ((Countly)cly).sdk.isTracking(feature.getIndex()); }

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
            ((Countly)cly).sdk.onActivityCreated(activity, bundle);
        }
    }

    /**
     * Activity callback to be called for apps which support API levels below 14
     *
     * @deprecated since 19.0, use {@link #onActivityCreated(Activity, Bundle)} instead
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
            ((Countly)cly).sdk.onActivityStarted(activity);
        }
    }

    /**
     * Activity callback to be called for apps which support API levels below 14
     *
     * @deprecated since 19.0, use {@link #onActivityStarted(Activity)}} instead
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
            ((Countly)cly).sdk.onActivityStarted(activity);
        }
    }

    /**
     * Activity callback to be called for apps which support API levels below 14
     *
     * @deprecated since 19.0, use {@link #onActivityStopped(Activity)}} instead
     * @param activity Activity instance
     */
    @Deprecated
    public static void onStop(Activity activity) {
        onActivityStopped(activity);
    }
}
