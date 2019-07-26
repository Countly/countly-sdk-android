package ly.count.sdk.java.internal;

import ly.count.sdk.internal.CoreFeature;
import ly.count.sdk.internal.Log;
import ly.count.sdk.internal.ModuleViews;
import ly.count.sdk.internal.Request;
import ly.count.sdk.internal.SDKCore;
import ly.count.sdk.java.Config;

public class SDK extends SDKStorage {
    private static final Log.Module L = Log.module("SDK");

    static SDK instance;

    static {
        // overriding core ones
        registerDefaultModuleMapping(CoreFeature.DeviceId.getIndex(), ModuleDeviceId.class);
        //registerDefaultModuleMapping(Config.Feature.CrashReporting.getIndex(), ModuleCrash.class);//todo add back in the future

        // adding Android-only features
        //registerDefaultModuleMapping(Config.Feature.Attribution.getIndex(), ModuleAttribution.class);//todo add back in the future
        registerDefaultModuleMapping(Config.Feature.Views.getIndex(), ModuleViews.class);
    }

    private Thread mainThread;

    public SDK() {
        super();
        SDK.instance = this;

        // just initialize overridden instance
        //Device.dev.getOS();//todo, do it correctly
    }

    @Override
    public void init(ly.count.sdk.internal.CtxCore ctx) {
        //get main thread
        mainThread = Thread.currentThread();//todo, Artem please double check this (AK, 2019.07.25)

        super.init(ctx);
    }

    @Override
    public void stop(ly.count.sdk.internal.CtxCore ctx, boolean clear) {
        super.stop(ctx, clear);
        instance = null;
        mainThread = null;
    }

    @Override
    public void onRequest(ly.count.sdk.internal.CtxCore ctx, Request request) {
        onSignal(ctx, SDKCore.Signal.Ping.getIndex(), null);
    }

    public void postToMainThread(Runnable ticker) {
        //todo no idea how to best do this (AK, 2019.07.25)
    }

    public Thread mainThread() {
        return mainThread;
    }
}
