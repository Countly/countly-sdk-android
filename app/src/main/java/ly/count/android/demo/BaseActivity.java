package ly.count.android.demo;

import android.app.Activity;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.annotation.Nullable;

import ly.count.android.sdk.Countly;

/**
 * Example of how to easily make Countly SDK aware of {@link Activity}ies in your app.
 * Just subclass all your activities from this class or add the same calls to your superclass.
 *
 * NOTE: This only needed if your app supports API levels below 14. For API 14+ Countly
 * uses {@link android.app.Application.ActivityLifecycleCallbacks}.
 */
public class BaseActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Countly.onActivityCreated(this, savedInstanceState);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState, @Nullable PersistableBundle persistentState) {
        super.onCreate(savedInstanceState, persistentState);
        Countly.onActivityCreated(this, savedInstanceState);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Countly.onActivityStarted(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Countly.onActivityStopped(this);
    }
}
