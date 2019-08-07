package ly.count.sdk.java.internal;

import ly.count.sdk.internal.Byteable;
import ly.count.sdk.internal.CtxCore;
import ly.count.sdk.internal.InternalConfig;
import ly.count.sdk.internal.Log;
import ly.count.sdk.internal.Module;
import ly.count.sdk.internal.ModuleCrash;
import ly.count.sdk.internal.ModuleRequests;
import ly.count.sdk.internal.Request;
import ly.count.sdk.internal.SDKCore;
import ly.count.sdk.internal.Storage;

/**
 * Application lifecycle-related methods of {@link SDK}
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
        if (id == Signal.DID.getIndex()) {
            networking.check(ctx);
        }
    }

    @Override
    public void onSignal(CtxCore ctx, int id, String param) {
        if (id == Signal.Ping.getIndex()){
            networking.check(ctx);
        } else if (id == Signal.Crash.getIndex()) {
            processCrash(ctx, Long.parseLong(param));
        }
    }

    private boolean processCrash(CtxCore ctx, Long id) {
        CrashImpl crash = new CrashImpl(id);
        crash = Storage.read(ctx, crash);
        if (crash == null) {
            L.e("Cannot read crash from storage, skipping");
            return false;
        }

        Request request = ModuleRequests.nonSessionRequest(ctx);
        ModuleCrash.putCrashIntoParams(crash, request.params);
        if (Storage.push(ctx, request)) {
            L.i("Added request " + request.storageId() + " instead of crash " + crash.storageId());
            networking.check(ctx);
            Boolean success = Storage.remove(ctx, crash);
            return success == null ? false : success;
        } else {
            L.e("Couldn't write request " + request.storageId() + " instead of crash " + crash.storageId());
            return false;
        }
    }

}
