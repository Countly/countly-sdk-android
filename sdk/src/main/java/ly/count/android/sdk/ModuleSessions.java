package ly.count.android.sdk;

public class ModuleSessions extends ModuleBase {
    boolean manualSessionControlEnabled = false;
    long prevSessionDurationStartTime_ = 0;

    final Sessions sessionInterface;

    ModuleSessions(Countly cly, CountlyConfig config) {
        super(cly, config);
        L.v("[ModuleSessions] Initialising");

        manualSessionControlEnabled = config.manualSessionControlEnabled;

        if (config.disableUpdateSessionRequests) {
            L.d("[ModuleSessions] Disabling periodic session time updates");
            _cly.disableUpdateSessionRequests_ = config.disableUpdateSessionRequests;
        }

        sessionInterface = new Sessions();
    }

    void beginSessionInternal() {
        L.d("[ModuleSessions] 'beginSessionInternal'");

        prevSessionDurationStartTime_ = System.nanoTime();
        requestQueueProvider.beginSession(_cly.moduleLocation.locationDisabled, _cly.moduleLocation.locationCountryCode, _cly.moduleLocation.locationCity, _cly.moduleLocation.locationGpsCoordinates, _cly.moduleLocation.locationIpAddress);
    }

    void updateSessionInternal() {
        L.d("[ModuleSessions] 'updateSessionInternal'");

        if (!_cly.disableUpdateSessionRequests_) {
            requestQueueProvider.updateSession(roundedSecondsSinceLastSessionDurationUpdate());
        }
    }

    /**
     * @param deviceIdOverride used when switching deviceID to a different one and ending the previous session
     */
    void endSessionInternal(String deviceIdOverride) {
        L.d("[ModuleSessions] 'endSessionInternal'");
        _cly.moduleRequestQueue.sendEventsIfNeeded(true);

        requestQueueProvider.endSession(roundedSecondsSinceLastSessionDurationUpdate(), deviceIdOverride);
        prevSessionDurationStartTime_ = 0;
    }

    /**
     * Calculates the unsent session duration in seconds, rounded to the nearest int.
     */
    int roundedSecondsSinceLastSessionDurationUpdate() {
        final long currentTimestampInNanoseconds = System.nanoTime();
        final long unsentSessionLengthInNanoseconds = currentTimestampInNanoseconds - prevSessionDurationStartTime_;
        prevSessionDurationStartTime_ = currentTimestampInNanoseconds;
        return (int) Math.round(unsentSessionLengthInNanoseconds / 1000000000.0d);
    }

    @Override
    void halt() {
        prevSessionDurationStartTime_ = 0;
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

                L.i("[Sessions] Calling 'updateSession'");

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
