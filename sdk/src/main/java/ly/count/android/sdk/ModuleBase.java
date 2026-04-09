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

    PerformanceCounterCollector pcc;

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

        pcc = config.pcc;
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
    void onActivityStarted(Activity activity, int updatedActivityCount) {
    }

    /**
     * Called during init when the app is already in the foreground and an initial activity
     * was provided via CountlyConfig.setInitialActivity(). This only sets the activity
     * reference without triggering counters, sessions, or view tracking.
     */
    void onInitialActivitySeeded(@NonNull Activity activity) {
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
    void deviceIdChanged(boolean withoutMerge) {
    }

    //notify the SDK modules that consent was updated
    void onConsentChanged(@NonNull final List<String> consentChangeDelta, final boolean newConsent, @NonNull final ModuleConsent.ConsentChangeSource changeSource) {
    }

    void consentWillChange(@NonNull List<String> consentThatWillChange, final boolean isConsentGiven) {
    }

    //notify the SDK modules that internal configuration was updated
    void onSdkConfigurationChanged(@NonNull CountlyConfig config) {

    }

    void initFinished(@NonNull CountlyConfig config) {
    }
}
