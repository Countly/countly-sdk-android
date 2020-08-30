package ly.count.android.sdk;

import android.app.Activity;
import android.content.res.Configuration;
import androidx.annotation.CallSuper;

abstract class ModuleBase {
    Countly _cly;

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

    private <F> void checkFragment(F fragment) {
        if (!(fragment instanceof android.app.Fragment) && !(fragment instanceof androidx.fragment.app.Fragment)) {
            throw new IllegalArgumentException("fragment must be an android.app.Fragment or an androidx.fragment.app.Fragment");
        }
    }

    @CallSuper
    <F> void onFragmentStarted(F fragment) {
        checkFragment(fragment);
    }

    @CallSuper
    <F> void onFragmentStopped(F fragment) {
        checkFragment(fragment);
    }

    void onActivityCreated(Activity activity) {
    }

    void onActivityStarted(Activity activity) {
    }

    void onActivityResumed(Activity activity) {
    }

    void onActivityPaused(Activity activity) {
    }

    void onActivityStopped(Activity activity) {
    }

    void onActivityDestroyed(Activity activity) {
    }

    void deviceIdChanged() {
    }

    void initFinished(CountlyConfig config) {
    }
}
