package ly.count.sdk.java.internal;

import android.app.Application;
import android.content.Context;
import android.os.Handler;

import ly.count.sdk.android.Config;
import ly.count.sdk.internal.CoreFeature;
import ly.count.sdk.internal.Log;
import ly.count.sdk.internal.ModuleViews;
import ly.count.sdk.internal.Request;
import ly.count.sdk.internal.SDKCore;

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

        // just initialize overridden instance
        Device.dev.getOS();
    }

    @Override
    public void init(ly.count.sdk.internal.Ctx ctx) {
        Application app = ((Ctx)ctx).getApplication();

        if(app != null){
            handler = new Handler(app.getMainLooper());
        } else {
            Context context = ((Ctx)ctx).getContext();
            handler = new Handler(context.getMainLooper());
        }

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
        onSignal(ctx, SDKCore.Signal.Ping.getIndex(), null);
    }

    public void postToMainThread(Runnable ticker) {
        handler.post(ticker);
    }

    public Thread mainThread() {
        return handler.getLooper().getThread();
    }
}
