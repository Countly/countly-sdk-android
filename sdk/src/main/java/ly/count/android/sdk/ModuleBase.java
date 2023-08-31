package ly.count.android.sdk;

import android.app.Activity;
import android.content.res.Configuration;
import androidx.annotation.NonNull;
import java.util.List;

abstract class ModuleBase {
    final Countly _cly;
    ModuleLog L;
    ConsentProvider consentProvider;
    StorageProvider storageProvider;
    EventProvider eventProvider;
    RequestQueueProvider requestQueueProvider;
    DeviceIdProvider deviceIdProvider;
    BaseInfoProvider baseInfoProvider;
    ViewIdProvider viewIdProvider;
    ConfigurationProvider configProvider;

    HealthTracker healthTracker;

    DeviceInfo deviceInfo;

    ModuleBase(@NonNull Countly cly, @NonNull CountlyConfig config) {
        _cly = cly;
        L = cly.L;
        consentProvider = config.consentProvider;
        storageProvider = config.storageProvider;
        eventProvider = config.eventProvider;
        requestQueueProvider = config.requestQueueProvider;
        deviceIdProvider = config.deviceIdProvider;
        baseInfoProvider = config.baseInfoProvider;
        viewIdProvider = config.viewIdProvider;
        configProvider = config.configProvider;
        healthTracker = config.healthTracker;

        deviceInfo = config.deviceInfo;
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
    void onActivityStopped(int updatedActivityCount) {
    }

    //void callbackOnActivityCreated(Activity activity) {
    //}
    //
    //void callbackOnActivityStarted(Activity activity) {
    //}
    //
    void callbackOnActivityResumed(Activity activity) {
    }

    //
    //void callbackOnActivityPaused(Activity activity) {
    //}
    //
    void callbackOnActivityStopped(Activity activity) {
    }
    //
    //void callbackOnActivitySaveInstanceState(Activity activity) {
    //}
    //
    //void callbackOnActivityDestroyed(Activity activity) {
    //}

    //notify the SDK modules that the device ID has changed
    void deviceIdChanged() {
    }

    //notify the SDK modules that consent was updated
    void onConsentChanged(@NonNull final List<String> consentChangeDelta, final boolean newConsent, @NonNull final ModuleConsent.ConsentChangeSource changeSource) {
    }

    //notify the SDK modules that internal configuration was updated
    void sdkConfigurationChanged() {

    }

    void initFinished(@NonNull CountlyConfig config) {
    }
}
