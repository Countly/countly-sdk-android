package ly.count.sdk.java.internal;

import java.io.File;

import ly.count.sdk.internal.Byteable;
import ly.count.sdk.internal.CtxCore;
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
    public void onSignal(CtxCore ctx, int id, Byteable param1, Byteable param2) {
        //throw new IllegalStateException("Not supported in Java");
        L.d("SDK lifecycle, onSignal #1, id:[" + id + "], param1:[" + param1 + "], param2:[" + param2 + "]");
        networking.check(ctx);
    }

    @Override
    public void onSignal(ly.count.sdk.internal.CtxCore ctx, int id, String param) {
        L.d("SDK lifecycle, onSignal #2, id:[" + id + "], param:[" + param + "]");
        if (id == Signal.Ping.getIndex()){
            networking.check(ctx);
        }
    }

}
