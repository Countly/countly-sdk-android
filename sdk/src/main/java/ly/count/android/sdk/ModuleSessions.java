package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.List;
import java.util.Map;

public class ModuleSessions extends ModuleBase {
    boolean manualSessionControlEnabled = false;
    boolean manualSessionControlHybridModeEnabled = false;
    long prevSessionDurationStartTime_ = System.currentTimeMillis();
    boolean sessionRunning = false;
    final Sessions sessionInterface;

    @Nullable
    Map<String, String> metricOverride = null;

    ModuleSessions(Countly cly, CountlyConfig config) {
        super(cly, config);
        L.v("[ModuleSessions] Initialising");

        metricOverride = config.metricOverride;

        manualSessionControlEnabled = config.manualSessionControlEnabled;
        if (manualSessionControlEnabled) {
            L.d("[ModuleSessions] Enabling manual session control");
        }

        manualSessionControlHybridModeEnabled = config.manualSessionControlHybridModeEnabled;
        if (manualSessionControlHybridModeEnabled) {
            L.d("[ModuleSessions] Enabling manual session control hybrid mode");
        }

        if (config.disableUpdateSessionRequests) {
            L.d("[ModuleSessions] Disabling periodic session time updates");
            _cly.disableUpdateSessionRequests_ = config.disableUpdateSessionRequests;
        }

        sessionInterface = new Sessions();
    }

    void beginSessionInternal() {
        L.d("[ModuleSessions] 'beginSessionInternal'");

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.sessions)) {
            return;
        }

        if (!configProvider.getSessionTrackingEnabled()) {
            return;
        }

        if (sessionIsRunning()) {
            L.w("[ModuleSessions] A session is already running, this 'beginSessionInternal' will be ignored");
            healthTracker.logSessionStartedWhileRunning();
            return;
        }

        //Sibling modules are reached through _cly and teardown nulls those fields, so each one is read once
        //into a local before it is used - reading the field twice could return null after the check. A module
        //that is already gone only costs its own step; the session itself still begins, because a
        //begin_session that never went out would leave a later end_session with nothing to close.
        ModuleUserProfile userProfile = _cly.moduleUserProfile;
        ModuleLocation location = _cly.moduleLocation;
        ModuleViews views = _cly.moduleViews;

        //prepare metrics
        String preparedMetrics = deviceInfo.getMetrics(_cly.context_, metricOverride, L);
        sessionRunning = true;
        prevSessionDurationStartTime_ = System.currentTimeMillis();
        if (userProfile != null) {
            userProfile.saveInternal();
        } else {
            L.w("[ModuleSessions] beginSessionInternal, the user profile module is gone, not saving pending profile changes");
        }

        if (location != null) {
            requestQueueProvider.beginSession(location.locationDisabled, location.locationCountryCode, location.locationCity, location.locationGpsCoordinates, location.locationIpAddress, preparedMetrics);
        } else {
            //ModuleLocation's own field defaults are exactly these, so this is "no location information"
            //rather than a guess - prepareLocationData appends nothing for them
            L.w("[ModuleSessions] beginSessionInternal, the location module is gone, beginning the session without location information");
            requestQueueProvider.beginSession(false, null, null, null, null, preparedMetrics);
        }

        if (views != null && views.trackOrientationChanges) {
            views.updateOrientation(_cly.context_.getResources().getConfiguration().orientation, true);
        }
    }

    void updateSessionInternal() {
        L.d("[ModuleSessions] 'updateSessionInternal'");

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.sessions)) {
            return;
        }

        if (!configProvider.getSessionTrackingEnabled()) {
            return;
        }

        if (!sessionIsRunning()) {
            L.w("[ModuleSessions] No session is running, this 'updateSessionInternal' will be ignored");
            healthTracker.logSessionUpdatedWhileNotRunning();
            return;
        }

        if (!_cly.disableUpdateSessionRequests_) {
            ModuleUserProfile userProfile = _cly.moduleUserProfile;
            if (userProfile != null) {
                userProfile.saveInternal();
            } else {
                L.w("[ModuleSessions] updateSessionInternal, the user profile module is gone, not saving pending profile changes");
            }

            requestQueueProvider.updateSession(roundedSecondsSinceLastSessionDurationUpdate());
        }
    }

    void endSessionInternal(boolean checkConsent) {
        L.d("[ModuleSessions] endSessionInternal, checkConsent:[" + checkConsent + "]");

        if (checkConsent && !consentProvider.getConsent(Countly.CountlyFeatureNames.sessions)) {
            return;
        }

        if (!configProvider.getSessionTrackingEnabled()) {
            return;
        }

        if (!sessionIsRunning()) {
            L.w("[ModuleSessions] No session is running, this 'endSessionInternal' will be ignored");
            healthTracker.logSessionEndedWhileNotRunning();
            return;
        }

        //resetFirstView below is the frame that killed a CI run: the main thread was inside this method when
        //a teardown on another thread nulled moduleViews, and the NPE escaped Activity.onStop. Each sibling is
        //snapshotted and skipped on its own so that end_session still goes out when one of them is already
        //gone - a session left open forever is worse than one that ends without its trailing bookkeeping.
        ModuleRequestQueue requestQueue = _cly.moduleRequestQueue;
        ModuleUserProfile userProfile = _cly.moduleUserProfile;
        ModuleViews views = _cly.moduleViews;

        if (requestQueue != null) {
            requestQueue.sendEventsIfNeeded(true);
        } else {
            L.w("[ModuleSessions] endSessionInternal, the request queue module is gone, not flushing events");
        }

        if (userProfile != null) {
            userProfile.saveInternal();
        } else {
            L.w("[ModuleSessions] endSessionInternal, the user profile module is gone, not saving pending profile changes");
        }

        requestQueueProvider.endSession(roundedSecondsSinceLastSessionDurationUpdate());
        sessionRunning = false;

        if (views != null) {
            views.resetFirstView();//todo these scenarios need to be tested and validated
        } else {
            L.w("[ModuleSessions] endSessionInternal, the views module is gone, not resetting the first view flag");
        }
    }

    void endSessionInternal() {
        endSessionInternal(true);
    }

    /**
     * Resolved "is automatic session tracking active" value. It is seeded from the developer config
     * ('!manualSessionControlEnabled') and can be overridden by the SBS layers, so the server takes
     * precedence over the developer's manual session control choice.
     */
    boolean automaticSessionTrackingEnabled() {
        return configProvider.getAutomaticSessionTrackingEnabled();
    }

    /**
     * If a session has been started and is still running
     *
     * @return
     */
    public boolean sessionIsRunning() {
        //if the start timestamp is set then assume that the session is running
        return sessionRunning;
    }

    /**
     * Calculates the unsent session duration in seconds, rounded to the nearest int.
     */
    int roundedSecondsSinceLastSessionDurationUpdate() {
        if (prevSessionDurationStartTime_ < 1) {
            L.e("[ModuleSessions] roundedSecondsSinceLastSessionDurationUpdate, called with prevSessionDurationStartTime_ being less than 1, returning 0, values was:[" + prevSessionDurationStartTime_ + "]");
            return 0;
        }
        final long currentTimestampInMilliseconds = System.currentTimeMillis();
        final long unsentSessionLengthInMilliseconds = currentTimestampInMilliseconds - prevSessionDurationStartTime_;
        prevSessionDurationStartTime_ = currentTimestampInMilliseconds;
        int seconds = (int) Math.round(unsentSessionLengthInMilliseconds / 1_000.0d);

        L.d("[ModuleSessions] roundedSecondsSinceLastSessionDurationUpdate, psds_:[" + prevSessionDurationStartTime_ + "], ctim:[" + currentTimestampInMilliseconds + "], uslim:[" + unsentSessionLengthInMilliseconds + "], uslim_s:[" + seconds + "]");
        return seconds;
    }

    @Override
    void onConsentChanged(@NonNull final List<String> consentChangeDelta, final boolean newConsent, @NonNull final ModuleConsent.ConsentChangeSource changeSource) {
        L.d("[ModuleSessions] onConsentChanged, consentChangeDelta:[" + consentChangeDelta + "], newConsent:[" + newConsent + "], changeSource:[" + changeSource + "]");
        if (consentChangeDelta.contains(Countly.CountlyFeatureNames.sessions)) {
            if (newConsent) {
                //if consent was just given and automatic session tracking is active, start a session if we are in the foreground
                if (automaticSessionTrackingEnabled() && _cly.lifeCycleAtleastStarted()) {
                    beginSessionInternal();
                }
            } else {
                L.d("[ModuleSessions] Ending session due to consent change");
                ModuleLocation location = _cly.moduleLocation;
                if (!_cly.isBeginSessionSent && location != null) {
                    //if session consent was removed and first begins session was not sent
                    //that means that we might not have sent the initially given location information
                    location.sendCurrentLocationIfValid();
                }

                if (sessionIsRunning()) {
                    endSessionInternal(false);
                } else {
                    ModuleViews views = _cly.moduleViews;
                    if (views != null) {
                        views.resetFirstView();
                    }
                }
            }
        }
    }

    @Override
    void initFinished(@NonNull CountlyConfig config) {
        if (automaticSessionTrackingEnabled() && _cly.lifeCycleAtleastStarted()) {
            //start a session if we initialized in the foreground
            beginSessionInternal();
        }
    }

    @Override
    void halt() {
        prevSessionDurationStartTime_ = 0;
        sessionRunning = false;
    }

    @Override
    void deviceIdChanged(boolean withoutMerge) {
        if (automaticSessionTrackingEnabled() && withoutMerge && _cly.lifeCycleAtleastStarted()) {
            L.d("[ModuleSessions] deviceIdChanged, automatic session control enabled and device id changed without merge, starting a new session");
            beginSessionInternal();
        }
    }

    public class Sessions {
        public void beginSession() {
            synchronized (_cly) {
                L.i("[Sessions] Calling 'beginSession', automatic session tracking active:[" + automaticSessionTrackingEnabled() + "]");

                if (automaticSessionTrackingEnabled()) {
                    L.w("[Sessions] 'beginSession' will be ignored since automatic session tracking is active");
                    return;
                }

                beginSessionInternal();
            }
        }

        public void updateSession() {
            synchronized (_cly) {
                L.i("[Sessions] Calling 'updateSession', automatic session tracking active:[" + automaticSessionTrackingEnabled() + "]");

                if (automaticSessionTrackingEnabled()) {
                    L.w("[Sessions] 'updateSession' will be ignored since automatic session tracking is active");
                    return;
                }

                if (manualSessionControlHybridModeEnabled) {
                    L.w("[Sessions] 'updateSession' will be ignored since manual session control hybrid mode is enabled");
                    return;
                }

                updateSessionInternal();
            }
        }

        public void endSession() {
            synchronized (_cly) {
                L.i("[Sessions] Calling 'endSession', automatic session tracking active:[" + automaticSessionTrackingEnabled() + "]");

                if (automaticSessionTrackingEnabled()) {
                    L.w("[Sessions] 'endSession' will be ignored since automatic session tracking is active");
                    return;
                }

                endSessionInternal();
            }
        }
    }
}
