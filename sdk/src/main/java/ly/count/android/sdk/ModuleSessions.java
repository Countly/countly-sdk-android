package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.List;
import java.util.Map;

public class ModuleSessions extends ModuleBase {
    boolean manualSessionControlEnabled = false;
    boolean manualSessionControlHybridModeEnabled = false;
    long prevSessionDurationStartTime_ = System.nanoTime();
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
        prevSessionDurationStartTime_ = System.nanoTime();
        requestQueueProvider.beginSession(_cly.moduleLocation.locationDisabled, _cly.moduleLocation.locationCountryCode, _cly.moduleLocation.locationCity, _cly.moduleLocation.locationGpsCoordinates, _cly.moduleLocation.locationIpAddress, preparedMetrics);
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

    /**
     * @param deviceIdOverride used when switching deviceID to a different one and ending the previous session
     */
    void endSessionInternal(String deviceIdOverride) {
        L.d("[ModuleSessions] 'endSessionInternal'");

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.sessions)) {
            return;
        }

        if (!sessionIsRunning()) {
            L.w("[ModuleSessions] No session is running, this 'endSessionInternal' will be ignored");
            healthTracker.logSessionEndedWhileNotRunning();
            return;
        }

        _cly.moduleRequestQueue.sendEventsIfNeeded(true);

        requestQueueProvider.endSession(roundedSecondsSinceLastSessionDurationUpdate(), deviceIdOverride);
        sessionRunning = false;

        _cly.moduleViews.resetFirstView();//todo these scenarios need to be tested and validated
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
        final long currentTimestampInNanoseconds = System.nanoTime();
        final long unsentSessionLengthInNanoseconds = currentTimestampInNanoseconds - prevSessionDurationStartTime_;
        prevSessionDurationStartTime_ = currentTimestampInNanoseconds;
        int seconds = (int) Math.round(unsentSessionLengthInNanoseconds / 1_000_000_000.0d);

        L.d("[ModuleSessions] roundedSecondsSinceLastSessionDurationUpdate, psds_:[" + prevSessionDurationStartTime_ + "], ctin:[" + currentTimestampInNanoseconds + "], uslin:[" + unsentSessionLengthInNanoseconds + "], uslin_s:[" + seconds + "]");
        return seconds;
    }

    @Override
    void onConsentChanged(@NonNull final List<String> consentChangeDelta, final boolean newConsent, @NonNull final ModuleConsent.ConsentChangeSource changeSource) {
        if (consentChangeDelta.contains(Countly.CountlyFeatureNames.sessions)) {
            if (newConsent) {
                //if consent was just given and manual sessions sessions are not enabled, start a session if we are in the foreground
                if (!manualSessionControlEnabled && _cly.config_.lifecycleObserver.LifeCycleAtleastStarted()) {
                    beginSessionInternal();
                }
            } else {
                if (!_cly.isBeginSessionSent) {
                    //if session consent was removed and first begins session was not sent
                    //that means that we might not have sent the initially given location information

                    _cly.moduleLocation.sendCurrentLocationIfValid();
                }

                //if a session was running (manual or automatic), stop it
                if (sessionIsRunning()) {
                    endSessionInternal(null);
                } else {
                    //reset the first view counter even if there was no session
                    _cly.moduleViews.resetFirstView();//todo these scenarios need to be tested and validated
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

                endSessionInternal(null);
            }
        }
    }
}
