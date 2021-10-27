package ly.count.android.sdk;

import android.app.Activity;
import android.content.res.Configuration;
import androidx.annotation.NonNull;

abstract class ModuleBase {
    final Countly _cly;
    ModuleLog L;
    ConsentProvider consentProvider;
    StorageProvider storageProvider;
    EventProvider eventProvider;
    RequestQueueProvider requestQueueProvider;
    DeviceIdProvider deviceIdProvider;
    BaseInfoProvider baseInfoProvider;

    ModuleBase(@NonNull Countly cly, @NonNull CountlyConfig config) {
        _cly = cly;
        L = cly.L;
        consentProvider = config.consentProvider;
        storageProvider = config.storageProvider;
        eventProvider = config.eventProvider;
        requestQueueProvider = config.requestQueueProvider;
        deviceIdProvider = config.deviceIdProvider;
        baseInfoProvider = config.baseInfoProvider;
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
