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

        if (sessionIsRunning()) {
            L.w("[ModuleSessions] A session is already running, this 'beginSessionInternal' will be ignored");
            healthTracker.logSessionStartedWhileRunning();
            return;
        }

        //prepare metrics
        String preparedMetrics = deviceInfo.getMetrics(_cly.context_, metricOverride, L);
        sessionRunning = true;
        prevSessionDurationStartTime_ = System.currentTimeMillis();
        requestQueueProvider.beginSession(_cly.moduleLocation.locationDisabled, _cly.moduleLocation.locationCountryCode, _cly.moduleLocation.locationCity, _cly.moduleLocation.locationGpsCoordinates, _cly.moduleLocation.locationIpAddress, preparedMetrics);

        if (_cly.moduleViews.trackOrientationChanges) {
            _cly.moduleViews.updateOrientation(_cly.context_.getResources().getConfiguration().orientation, true);
        }
    }

    void updateSessionInternal() {
        L.d("[ModuleSessions] 'updateSessionInternal'");

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.sessions)) {
            return;
        }

        if (!sessionIsRunning()) {
            L.w("[ModuleSessions] No session is running, this 'updateSessionInternal' will be ignored");
            healthTracker.logSessionUpdatedWhileNotRunning();
            return;
        }

        if (!_cly.disableUpdateSessionRequests_) {
            requestQueueProvider.updateSession(roundedSecondsSinceLastSessionDurationUpdate());
        }
    }

    void endSessionInternal(boolean checkConsent) {
        L.d("[ModuleSessions] endSessionInternal, checkConsent:[" + checkConsent + "]");

        if (checkConsent && !consentProvider.getConsent(Countly.CountlyFeatureNames.sessions)) {
            return;
        }

        if (!sessionIsRunning()) {
            L.w("[ModuleSessions] No session is running, this 'endSessionInternal' will be ignored");
            healthTracker.logSessionEndedWhileNotRunning();
            return;
        }

        _cly.moduleRequestQueue.sendEventsIfNeeded(true);

        _cly.moduleUserProfile.saveInternal();

        requestQueueProvider.endSession(roundedSecondsSinceLastSessionDurationUpdate());
        sessionRunning = false;

        _cly.moduleViews.resetFirstView();//todo these scenarios need to be tested and validated
    }

    void endSessionInternal() {
        endSessionInternal(true);
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
                //if consent was just given and manual sessions sessions are not enabled, start a session if we are in the foreground
                if (!manualSessionControlEnabled && _cly.config_.lifecycleObserver.LifeCycleAtleastStarted()) {
                    beginSessionInternal();
                }
            } else {
                L.d("[ModuleSessions] Ending session due to consent change");
                if (!_cly.isBeginSessionSent) {
                    //if session consent was removed and first begins session was not sent
                    //that means that we might not have sent the initially given location information
                    _cly.moduleLocation.sendCurrentLocationIfValid();
                }

                if (sessionIsRunning()) {
                    endSessionInternal(false);
                } else {
                    _cly.moduleViews.resetFirstView();
                }
            }
        }
    }

    @Override
    void initFinished(@NonNull CountlyConfig config) {
        if (!manualSessionControlEnabled && _cly.config_.lifecycleObserver.LifeCycleAtleastStarted()) {
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
        if (!manualSessionControlEnabled && withoutMerge && _cly.config_.lifecycleObserver.LifeCycleAtleastStarted()) {
            L.d("[ModuleSessions] deviceIdChanged, automatic session control enabled and device id changed without merge, starting a new session");
            beginSessionInternal();
        }
    }

    public class Sessions {
        public void beginSession() {
            synchronized (_cly) {
                L.i("[Sessions] Calling 'beginSession', manual session control enabled:[" + manualSessionControlEnabled + "]");

                if (!manualSessionControlEnabled) {
                    L.w("[Sessions] 'beginSession' will be ignored since manual session control is not enabled");
                    return;
                }

                beginSessionInternal();
            }
        }

        public void updateSession() {
            synchronized (_cly) {
                L.i("[Sessions] Calling 'updateSession', manual session control enabled:[" + manualSessionControlEnabled + "]");

                if (!manualSessionControlEnabled) {
                    L.w("[Sessions] 'updateSession' will be ignored since manual session control is not enabled");
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
                L.i("[Sessions] Calling 'endSession', manual session control enabled:[" + manualSessionControlEnabled + "]");

                if (!manualSessionControlEnabled) {
                    L.w("[Sessions] 'endSession' will be ignored since manual session control is not enabled");
                    return;
                }

                endSessionInternal();
            }
        }
    }
}
