package ly.count.android.demo;

import android.app.Activity;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.annotation.Nullable;

import ly.count.android.sdk.CountlyNeo;

public class BaseActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CountlyNeo.onActivityCreated(this, savedInstanceState);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState, @Nullable PersistableBundle persistentState) {
        super.onCreate(savedInstanceState, persistentState);
        CountlyNeo.onActivityCreated(this, savedInstanceState);
    }

    @Override
    protected void onStart() {
        super.onStart();
        CountlyNeo.onActivityStarted(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        CountlyNeo.onActivityStopped(this);
    }
}
