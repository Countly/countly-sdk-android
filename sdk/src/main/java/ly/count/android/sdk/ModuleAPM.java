package ly.count.android.sdk;

import android.util.Log;

public class ModuleAPM extends ModuleBase {

    Apm apmInterface = null;

    ModuleAPM(Countly cly, CountlyConfig config) {
        super(cly);

        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleAPM] Initialising");
        }

        apmInterface = new Apm();
    }

    @Override
    public void halt() {

    }

    public class Apm {

    }
}
