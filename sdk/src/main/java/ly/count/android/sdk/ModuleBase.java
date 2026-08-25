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
    /*
     * WIRE A NEW OVERRIDE INTO THE DISPATCHER - this applies to every lifecycle hook in this class, not
     * just to onConfigurationChanged.
     *
     * Lifecycle hooks are no longer delivered by iterating Countly's `modules` list. Android delivers them
     * on the main thread while a teardown on another thread clears that list and nulls the module fields,
     * which killed a CI run with an NPE escaping Activity.onStop - Android turns that into a host-app
     * crash. CountlyLifecycleDispatcher therefore holds one process-wide registration, and the dispatch
     * path calls a fixed set of modules directly from Countly#dispatchActivity* and the
     * onStart/onStop/onConfigurationChanged internals.
     *
     * So overriding one of these methods does nothing on its own. Add the module to the call site too, at
     * the position init adds it to `modules`: the loops these calls replaced ran in that order and side
     * effects between modules depend on it. ModuleLifecycleDispatchTests derives both the set and the
     * order from a live instance's `modules` list and fails on a missing, stale, or reordered call, so
     * this rule is enforced rather than hoped for.
     */
    void onConfigurationChanged(Configuration newConfig) {
    }

    /**
     * Called manually by a countly call from the developer
     */
    /* Overriding this is not enough: it must also be wired into the dispatch call site, in modules-list
     * order. See the note above onConfigurationChanged. */
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
    /* Overriding this is not enough: it must also be wired into the dispatch call site, in modules-list
     * order. See the note above onConfigurationChanged. */
    void onActivityStopped(int updatedActivityCount) {
    }

    /**
     * Called when an Activity is destroyed. Modules that hold Activity references must
     * clear them here (using identity comparison) to prevent leaking destroyed activities
     * through the Countly singleton.
     */
    /* Overriding this is not enough: it must also be wired into the dispatch call site, in modules-list
     * order. See the note above onConfigurationChanged. */
    void onActivityDestroyed(@NonNull Activity activity) {
    }

    //void callbackOnActivityCreated(Activity activity) {
    //}
    //
    //void callbackOnActivityStarted(Activity activity) {
    //}
    //
    /* Overriding this is not enough: it must also be wired into the dispatch call site, in modules-list
     * order. See the note above onConfigurationChanged. */
    void callbackOnActivityResumed(Activity activity) {
    }

    //
    //void callbackOnActivityPaused(Activity activity) {
    //}
    //
    /* Overriding this is not enough: it must also be wired into the dispatch call site, in modules-list
     * order. See the note above onConfigurationChanged. */
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
