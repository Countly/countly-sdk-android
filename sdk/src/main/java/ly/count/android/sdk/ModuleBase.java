package ly.count.android.sdk;

import android.app.Activity;
import android.content.res.Configuration;

abstract class ModuleBase {
    final Countly _cly;

    ModuleBase(Countly cly) {
        _cly = cly;
    }

    void halt() {
        throw new UnsupportedOperationException();
    }

    /**
     * Called manually by a countly call from the developer
     *
     * @param newConfig
     */
    void onConfigurationChanged(Configuration newConfig) {
    }

    /**
     * Called manually by a countly call from the developer
     */
    void onActivityStarted(Activity activity) {
    }

    /**
     * Called manually by a countly call from the developer
     */
    void onActivityStopped() {
    }

    void callbackOnActivityCreated(Activity activity) {
    }

    void callbackOnActivityStarted(Activity activity) {
    }

    void callbackOnActivityResumed(Activity activity) {
    }

    void callbackOnActivityPaused(Activity activity) {
    }

    void callbackOnActivityStopped(Activity activity) {
    }

    void callbackOnActivitySaveInstanceState(Activity activity) {
    }

    void callbackOnActivityDestroyed(Activity activity) {
    }

    void deviceIdChanged() {
    }

    void initFinished(CountlyConfig config) {
    }
}
