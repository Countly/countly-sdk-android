package ly.count.sdk.android.internal;

import ly.count.sdk.android.Config;
import ly.count.sdk.internal.CoreFeature;
import ly.count.sdk.internal.InternalConfig;
import ly.count.sdk.internal.Log;
import ly.count.sdk.internal.Module;
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

    protected SDK(InternalConfig config) {
        super(config);
        instance = this;
    }

    @Override
    protected void prepareMappings(ly.count.sdk.Config config) throws IllegalStateException {
        super.prepareMappings(config);

        for (Config.Feature feature : Config.Feature.values()) {
            Class<? extends Module> override = config.getModuleOverride(feature.getIndex());
            if (override != null) {
                registerModuleMapping(feature.getIndex(), override);
            }
        }
    }

    @Override
    public void onRequest(ly.count.sdk.internal.Ctx ctx, Request request) {
        onSignal(ctx, Signal.Ping.getIndex(), null);
    }

}
