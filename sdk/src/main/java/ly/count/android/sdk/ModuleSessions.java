package ly.count.android.sdk;

import android.util.Log;

public class ModuleSessions extends ModuleBase {

    boolean manualSessionControlEnabled = false;
    long prevSessionDurationStartTime_ = 0;

    final Sessions sessionInterface;

    ModuleSessions(Countly cly, CountlyConfig config) {
        super(cly);

        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleSessions] Initialising");
        }

        manualSessionControlEnabled = config.manualSessionControlEnabled;
        _cly.disableUpdateSessionRequests_ = config.disableUpdateSessionRequests;

        sessionInterface = new Sessions();
    }

    void beginSessionInternal() {
        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleSessions] 'beginSessionInternal'");
        }

        prevSessionDurationStartTime_ = System.nanoTime();
        _cly.connectionQueue_.beginSession();
    }

    void updateSessionInternal() {
        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleSessions] 'updateSessionInternal'");
        }

        if (!_cly.disableUpdateSessionRequests_) {
            _cly.connectionQueue_.updateSession(roundedSecondsSinceLastSessionDurationUpdate());
        }
    }

    /**
     *
     * @param deviceIdOverride used when switching deviceID to a different one and ending the previous session
     */
    void endSessionInternal(String deviceIdOverride) {
        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleSessions] 'endSessionInternal'");
        }

        _cly.connectionQueue_.endSession(roundedSecondsSinceLastSessionDurationUpdate(), deviceIdOverride);
        prevSessionDurationStartTime_ = 0;

        _cly.sendEventsIfExist();
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
        public synchronized void beginSession() {
            if (!_cly.isInitialized()) {
                throw new IllegalStateException("Countly.sharedInstance().init must be called before beginSession");
            }

            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[Sessions] Calling 'beginSession', manual session control enabled:[" + manualSessionControlEnabled + "]");
            }

            if (!manualSessionControlEnabled) {
                if (_cly.isLoggingEnabled()) {
                    Log.w(Countly.TAG, "[Sessions] 'beginSession' will be ignored since manual session control is not enabled");
                    return;
                }
            }

            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[Sessions] Calling 'beginSession'");
            }

            beginSessionInternal();
        }

        public synchronized void updateSession() {
            if (!_cly.isInitialized()) {
                throw new IllegalStateException("Countly.sharedInstance().init must be called before updateSession");
            }

            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[Sessions] Calling 'updateSession', manual session control enabled:[" + manualSessionControlEnabled + "]");
            }

            if (!manualSessionControlEnabled) {
                if (_cly.isLoggingEnabled()) {
                    Log.w(Countly.TAG, "[Sessions] 'updateSession' will be ignored since manual session control is not enabled");
                    return;
                }
            }

            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[Sessions] Calling 'updateSession'");
            }

            updateSessionInternal();
        }

        public synchronized void endSession() {
            if (!_cly.isInitialized()) {
                throw new IllegalStateException("Countly.sharedInstance().init must be called before endSession");
            }

            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[Sessions] Calling 'endSession', manual session control enabled:[" + manualSessionControlEnabled + "]");
            }

            if (!manualSessionControlEnabled) {
                if (_cly.isLoggingEnabled()) {
                    Log.w(Countly.TAG, "[Sessions] 'endSession' will be ignored since manual session control is not enabled");
                    return;
                }
            }

            endSessionInternal(null);
        }
    }
}
