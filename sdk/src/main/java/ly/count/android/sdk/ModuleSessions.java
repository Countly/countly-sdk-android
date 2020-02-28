package ly.count.android.sdk;

import android.util.Log;

public class ModuleSessions extends ModuleBase {

    final Sessions sessionInterface;
    boolean manualSessionControlEnabled = false;

    ModuleSessions(Countly cly, CountlyConfig config) {
        super(cly);

        manualSessionControlEnabled = config.manualSessionControlEnabled;
        _cly.disableUpdateSessionRequests_ = config.disableUpdateSessionRequests;

        sessionInterface = new Sessions();
    }

    void startSessionInternal() {

    }

    void stopSessionInternal() {

    }

    @Override
    void halt() {
    }

    public class Sessions {
        public synchronized void manualStart() {
            if (!_cly.isInitialized()) {
                throw new IllegalStateException("Countly.sharedInstance().init must be called before manualStart");
            }

            if (!manualSessionControlEnabled) {
                if (_cly.isLoggingEnabled()) {
                    Log.w(Countly.TAG, "[Sessions] 'manualStart' will be ignored since manual session control is not enabled");
                    return;
                }
            }
        }

        public synchronized void manualStop() {
            if (!_cly.isInitialized()) {
                throw new IllegalStateException("Countly.sharedInstance().init must be called before manualStop");
            }

            if (!manualSessionControlEnabled) {
                if (_cly.isLoggingEnabled()) {
                    Log.w(Countly.TAG, "[Sessions] 'manualStop' will be ignored since manual session control is not enabled");
                    return;
                }
            }
        }
    }
}
