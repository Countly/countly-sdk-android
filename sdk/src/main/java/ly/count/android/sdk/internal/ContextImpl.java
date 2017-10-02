package ly.count.android.sdk.internal;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

/**
 * {@link Context} implementation
 */
public class ContextImpl implements Context {
    private Application application;
    private Activity activity;
    private Bundle bundle;
    private android.content.Context context;
    private int level;

    private boolean expired = false;

    public ContextImpl(Application application) {
        this.application = application;
    }

    public ContextImpl(Activity activity) {
        this.activity = activity;
        this.bundle = null;
    }

    public ContextImpl(Activity activity, Bundle bundle) {
        this.activity = activity;
        this.bundle = bundle;
    }

    public ContextImpl(android.content.Context context) {
        this.context = context;
    }

    @Override
    public Application getApplication() {
        if (expired) {
            Log.wtf("Context is expired");
        }
        return application;
    }

    @Override
    public Activity getActivity() {
        if (expired) {
            Log.wtf("Context is expired");
        }
        return activity;
    }

    @Override
    public android.content.Context getContext() {
        if (expired) {
            Log.wtf("Context is expired");
        }
        return context != null ? context : activity != null ? activity : application;
    }

    @Override
    public boolean isExpired() {
        return expired;
    }

    public void expire() {
        context = getContext().getApplicationContext();
        application = null;
        activity = null;
        bundle = null;
    }
}
