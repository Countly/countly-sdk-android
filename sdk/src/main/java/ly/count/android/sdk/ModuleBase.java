package ly.count.android.sdk;

import android.app.Activity;
import android.content.res.Configuration;

abstract class ModuleBase {
    Countly _cly;

    ModuleBase(Countly cly){
        _cly = cly;
    }

    void halt(){
        throw new UnsupportedOperationException();
    }

    void onConfigurationChanged(Configuration newConfig) {
    }

    void onActivityStarted(Activity activity) {
    }

    void onActivityStopped() {
    }
}
