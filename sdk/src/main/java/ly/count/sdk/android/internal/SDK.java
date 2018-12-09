package ly.count.sdk.android.internal;

import android.os.Handler;

import ly.count.sdk.android.Config;
import ly.count.sdk.internal.CoreFeature;
import ly.count.sdk.internal.Log;
import ly.count.sdk.internal.ModuleViews;
import ly.count.sdk.internal.Request;

public class SDK extends SDKStorage {
    private static final Log.Module L = Log.module("SDK");

    static SDK instance;

    static {
        // overriding core ones
        registerDefaultModuleMapping(CoreFeature.DeviceId.getIndex(), ModuleDeviceId.class);
        registerDefaultModuleMapping(Config.Feature.CrashReporting.getIndex(), ModuleCrash.class);

        // adding Android-only features
        registerDefaultModuleMapping(Config.Feature.Attribution.getIndex(), ModuleAttribution.class);
        registerDefaultModuleMapping(Config.Feature.Push.getIndex(), ModulePush.class);
        registerDefaultModuleMapping(Config.Feature.Views.getIndex(), ModuleViews.class);
    }

    private Handler handler;

    public SDK() {
        super();
        SDK.instance = this;
    }

    @Override
    public void init(ly.count.sdk.internal.Ctx ctx) {
        handler = new Handler(((Ctx)ctx).getApplication().getMainLooper());
        super.init(ctx);
    }

    @Override
    public void stop(ly.count.sdk.internal.Ctx ctx, boolean clear) {
        super.stop(ctx, clear);
        instance = null;
        handler = null;
    }

    @Override
    public void onRequest(ly.count.sdk.internal.Ctx ctx, Request request) {
        onSignal(ctx, Signal.Ping.getIndex(), null);
    }

    public void postToMainThread(Runnable ticker) {
        handler.post(ticker);
    }
}
