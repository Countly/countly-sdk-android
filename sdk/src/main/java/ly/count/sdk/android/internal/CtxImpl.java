package ly.count.sdk.android.internal;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import ly.count.sdk.internal.InternalConfig;
import ly.count.sdk.internal.Log;
import ly.count.sdk.internal.SDKInterface;

/**
 * {@link Ctx} implementation
 */
public class CtxImpl implements Ctx {
    private static final Log.Module L = Log.module("CtxImpl");
    private SDKInterface sdk;
    private InternalConfig config;
    private Application application;
    private Activity activity;
    private Bundle bundle;
    private android.content.Context context;
    private int level;

    private boolean expired = false;

    public CtxImpl(SDKInterface sdk, InternalConfig config, Application application) {
        this.sdk = sdk;
        this.config = config;
        this.application = application;
    }

    public CtxImpl(SDKInterface sdk, InternalConfig config, Activity activity) {
        this.sdk = sdk;
        this.config = config;
        this.activity = activity;
        this.bundle = null;
    }

    public CtxImpl(SDKInterface sdk, InternalConfig config, Activity activity, Bundle bundle) {
        this.sdk = sdk;
        this.config = config;
        this.activity = activity;
        this.bundle = bundle;
    }

    public CtxImpl(SDKInterface sdk, InternalConfig config, android.content.Context context) {
        this.sdk = sdk;
        this.config = config;
        this.context = context;
    }

    @Override
    public Application getApplication() {
        if (expired) {
            L.wtf("Ctx is expired");
        }
        return application;
    }

    @Override
    public Activity getActivity() {
        if (expired) {
            L.wtf("Ctx is expired");
        }
        return activity;
    }

    @Override
    public android.content.Context getContext() {
        if (expired) {
            L.wtf("Ctx is expired");
        }
        android.content.Context ret = context != null ? context : activity != null ? activity : application;
        return ret;
    }

    @Override
    public InternalConfig getConfig() {
        return config;
    }

    @Override
    public SDKInterface getSDK() {
        return sdk;
    }

    @Override
    public boolean isExpired() {
        return expired;
    }

    public void expire() {
        config = null;
        context = getContext().getApplicationContext();
        application = null;
        activity = null;
        bundle = null;
    }
}
