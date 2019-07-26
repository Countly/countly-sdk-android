package ly.count.sdk.java.internal;

import java.io.File;

import ly.count.sdk.internal.Byteable;
import ly.count.sdk.internal.InternalConfig;
import ly.count.sdk.internal.Log;
import ly.count.sdk.internal.Module;
import ly.count.sdk.internal.ModuleRequests;
import ly.count.sdk.internal.Request;
import ly.count.sdk.internal.SDKCore;
import ly.count.sdk.internal.Storage;

/**
 * Application lifecycle-related methods of {@link ly.count.sdk.internal.SDK}
 */

public abstract class SDKLifecycle extends SDKCore {
    private static final Log.Module L = Log.module("SDKLifecycle");

    /**
     * Core instance config
     */
    protected InternalConfig config;

    protected SDKLifecycle() {
        super();
    }

    //todo, not sure if this is really needed (AK, 2019.07.25)
//    CtxImpl ctx (File directory) {
//        return new CtxImpl(this, config, directory);
//    }

    @Override
    public void stop(ly.count.sdk.internal.CtxCore ctx, boolean clear) {
        super.stop(ctx, clear);
        config = null;
    }

    /**
     * Lifecycle methods. For Android versions later or equal to Ice Cream Sandwich, developer
     * doesn't need to call those from each activity. In case app supports earlier version,
     * it's developer responsibility. In any case, for API 14+ Countly ignores dev calls.
     */
    @Override
    protected void onContextAcquired(final ly.count.sdk.internal.CtxCore ctx) {
        L.d("Application created");

        eachModule(new Modulator() {
            @Override
            public void run(int feature, Module module) {
                module.onContextAcquired(ctx);

            }
        });
    }

    public void onActivityCreated(String activityName) {
        L.d("[Callback] Activity created: " + activityName);
        this.onActivityCreatedInternal();
    }

    public void onActivityStarted(String activityName) {
        L.d("[Callback] Activity started: " + activityName);
        this.onActivityStartedInternal();
    }

    public void onActivityResumed(String activityName) {
        L.d("[Callback] Activity resumed: " + activityName);
        this.onActivityResumedInternal();
    }

    public void onActivityPaused(String activityName) {
        L.d("[Callback] Activity paused: " + activityName);
        this.onActivityPausedInternal();
    }

    public void onActivityStopped(String activityName) {
        L.d("[Callback] Activity stopped: " + activityName);
        this.onActivityStoppedInternal();
    }

    public void onActivityDestroyed(String activityName) {
        L.d("[Callback] Activity destroyed: " + activityName);
        this.onActivityDestroyedInternal();
    }

    private void onActivityCreatedInternal() {
        final CtxImpl ctx = new CtxImpl(this, config(), null);
        eachModule(new Modulator() {
            @Override
            public void run(int feature, Module module) {
                module.onActivityCreated(ctx);
            }
        });
    }

    private void onActivityStartedInternal() {
        final CtxImpl ctx = new CtxImpl(this, config(), null);
        eachModule(new Modulator() {
            @Override
            public void run(int feature, Module module) {
                module.onActivityStarted(ctx);
            }
        });
    }

    private void onActivityResumedInternal() {
        final CtxImpl ctx = new CtxImpl(this, config(), null);
        eachModule(new Modulator() {
            @Override
            public void run(int feature, Module module) {
                module.onActivityResumed(ctx);
            }
        });
    }

    private void onActivityPausedInternal() {
        final CtxImpl ctx = new CtxImpl(this, config(), null);
        eachModule(new Modulator() {
            @Override
            public void run(int feature, Module module) {
                module.onActivityCreated(ctx);
            }
        });
    }

    private void onActivityStoppedInternal() {
        final CtxImpl ctx = new CtxImpl(this, config(), null);
        eachModule(new Modulator() {
            @Override
            public void run(int feature, Module module) {
                module.onActivityStopped(ctx);
            }
        });
    }

    private void onActivityDestroyedInternal() {
        final CtxImpl ctx = new CtxImpl(this, config(), null);
        eachModule(new Modulator() {
            @Override
            public void run(int feature, Module module) {
                module.onActivityDestroyed(ctx);
            }
        });
    }

    @Override
    public void onSignal(ly.count.sdk.internal.CtxCore ctx, int id, Byteable param1, Byteable param2) {
        //todo, Artem, what would be the best replacement for this? (AK, 2019.07.25)
//        Intent intent = new Intent((Context) ctx.getContext(), CountlyService.class);
//        intent.putExtra(CountlyService.CMD, id);
//        if (param1 != null) {
//            intent.putExtra(CountlyService.PARAM_1, param1.store());
//        }
//        if (param2 != null) {
//            intent.putExtra(CountlyService.PARAM_2, param2.store());
//        }
//        ((Context)ctx.getContext()).startService(intent);
    }

    @Override
    public void onSignal(ly.count.sdk.internal.CtxCore ctx, int id, String param) {
        //todo, Artem, what would be the best replacement for this? (AK, 2019.07.25)
//        if (ctx.getConfig().isDefaultNetworking()) {
//            if (id == Signal.Crash.getIndex()) {
//                try {
//                    CrashImpl crash = new CrashImpl(Long.parseLong(param));
//                    crash = Storage.read(ctx, crash);
//                    if (crash == null) {
//                        L.e("Cannot read crash from storage, skipping");
//                        return;
//                    }
//
//                    Request request = ModuleRequests.nonSessionRequest(ctx);
//                    ModuleCrash.putCrashIntoParams(crash, request.params);
//                    if (Storage.push(ctx, request)) {
//                        L.i("Added request " + request.storageId() + " instead of crash " + crash.storageId());
//                        Boolean success = Storage.remove(ctx, crash);
//                        L.d("crash " + id + " removal result is " + success);
//                    } else {
//                        L.e("Couldn't write request " + request.storageId() + " instead of crash " + crash.storageId());
//                    }
//                } catch (Throwable t) {
//                    L.wtf("Error when making a request out of a crash", t);
//                }
//            }
//            networking.check(ctx);
//        } else {
//            Intent intent = new Intent((Context) ctx.getContext(), CountlyService.class);
//            intent.putExtra(CountlyService.CMD, id);
//            if (Utils.isNotEmpty(param)) {
//                intent.putExtra(CountlyService.PARAM_1, param);
//            }
//            ((Context)ctx.getContext()).startService(intent);
//        }
    }

}
