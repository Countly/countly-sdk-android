package ly.count.sdk.android.internal;

import android.app.Activity;
import android.app.Application;
import android.content.*;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;

import ly.count.sdk.internal.Byteable;
import ly.count.sdk.internal.CtxCore;
import ly.count.sdk.internal.InternalConfig;
import ly.count.sdk.internal.Log;
import ly.count.sdk.internal.Module;
import ly.count.sdk.internal.ModuleRequests;
import ly.count.sdk.internal.Request;
import ly.count.sdk.internal.SDKCore;
import ly.count.sdk.internal.SDKInterface;
import ly.count.sdk.internal.Storage;

/**
 * Application lifecycle-related methods of {@link SDKInterface}
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

    CtxImpl ctx (Context context) {
        return new CtxImpl(this, config, context);
    }

    @Override
    public void stop(CtxCore ctx, boolean clear) {
        super.stop(ctx, clear);
        config = null;
    }

    /**
     * Lifecycle methods. For Android versions later or equal to Ice Cream Sandwich, developer
     * doesn't need to call those from each activity. In case app supports earlier version,
     * it's developer responsibility. In any case, for API 14+ Countly ignores dev calls.
     */
    @Override
    protected void onContextAcquired(final CtxCore ctx) {
        L.d("Application created");

        final Application application;

        if(((Ctx) ctx).getApplication() != null) {
            application = ((Ctx) ctx).getApplication();
        } else {
            Activity act = ((Ctx) ctx).getActivity();
            if(act != null) {
                application = act.getApplication();
            } else {
                application = null;
            }
        }

        onSignal(ctx, Signal.Start.getIndex(), null);

        if (Utils.API(14)) {
            if(application != null) {
                //there can be occasions where this is not set
                application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                    @Override
                    public void onActivityCreated(Activity activity, Bundle bundle) {
                        L.d("Activity created: " + activity.getClass().getSimpleName());
                        SDKLifecycle.this.onActivityCreatedInternal(activity, bundle);
                    }

                    @Override
                    public void onActivityStarted(Activity activity) {
                        L.d("Activity started: " + activity.getClass().getSimpleName());
                        SDKLifecycle.this.onActivityStartedInternal(activity);
                    }

                    @Override
                    public void onActivityResumed(Activity activity) {
                        L.d("Activity resumed: " + activity.getClass().getSimpleName());
                        SDKLifecycle.this.onActivityResumedInternal(activity);
                    }

                    @Override
                    public void onActivityPaused(Activity activity) {
                        L.d("Activity paused: " + activity.getClass().getSimpleName());
                        SDKLifecycle.this.onActivityPausedInternal(activity);
                    }

                    @Override
                    public void onActivityStopped(Activity activity) {
                        L.d("Activity stopped: " + activity.getClass().getSimpleName());
                        SDKLifecycle.this.onActivityStoppedInternal(activity);
                    }

                    @Override
                    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
                        L.d("Activity save state: " + activity.getClass().getSimpleName());
                        SDKLifecycle.this.onActivitySaveInstanceStateInternal(activity, bundle);
                    }

                    @Override
                    public void onActivityDestroyed(Activity activity) {
                        L.d("Activity destroyed: " + activity.getClass().getSimpleName());
                        SDKLifecycle.this.onActivityDestroyedInternal(activity);
                    }
                });
                application.registerComponentCallbacks(new ComponentCallbacks2() {
                    @Override
                    public void onTrimMemory(int i) {
                        L.d("Trim memory " + i);
                        SDKLifecycle.this.onApplicationTrimMemoryInternal(i);
                    }

                    @Override
                    public void onConfigurationChanged(Configuration configuration) {
                        // TODO: Operator, screen, etc
                        L.d("Configuration changed: " + configuration.toString());
                        SDKLifecycle.this.onConfigurationChangedInternal(application, configuration);
                    }

                    @Override
                    public void onLowMemory() {
                        L.d("Low memory");
                    }
                });
            }
        }

        eachModule(new Modulator() {
            @Override
            public void run(int feature, Module module) {
                module.onContextAcquired(ctx);

            }
        });
    }

    public void onApplicationTrimMemory(int level) {
        if (!Utils.API(14)) {
            L.d("[Callback] Trim memory " + level);
            this.onApplicationTrimMemoryInternal(level);
        }
    }

    public void onActivityCreated(Activity activity, Bundle bundle) {
        if (!Utils.API(14)) {
            L.d("[Callback] Activity created: " + activity.getClass().getSimpleName());
            this.onActivityCreatedInternal(activity, bundle);
        }
    }

    public void onActivityStarted(Activity activity) {
        if (!Utils.API(14)) {
            L.d("[Callback] Activity started: " + activity.getClass().getSimpleName());
            this.onActivityStartedInternal(activity);
        }
    }

    public void onActivityResumed(Activity activity) {
        if (!Utils.API(14)) {
            L.d("[Callback] Activity resumed: " + activity.getClass().getSimpleName());
            this.onActivityResumedInternal(activity);
        }
    }

    public void onActivityPaused(Activity activity) {
        if (!Utils.API(14)) {
            L.d("[Callback] Activity paused: " + activity.getClass().getSimpleName());
            this.onActivityPausedInternal(activity);
        }
    }

    public void onActivityStopped(Activity activity) {
        if (!Utils.API(14)) {
            L.d("[Callback] Activity stopped: " + activity.getClass().getSimpleName());
            this.onActivityStoppedInternal(activity);
        }
    }

    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
        if (!Utils.API(14)) {
            L.d("[Callback] Activity save state: " + activity.getClass().getSimpleName());
            this.onActivitySaveInstanceStateInternal(activity, bundle);
        }
    }

    public void onActivityDestroyed(Activity activity) {
        if (!Utils.API(14)) {
            L.d("[Callback] Activity destroyed: " + activity.getClass().getSimpleName());
            this.onActivityDestroyedInternal(activity);
        }
    }

    public void onConfigurationChanged(Application application, Configuration configuration) {
        if (!Utils.API(Build.VERSION_CODES.ICE_CREAM_SANDWICH)) {
            this.onConfigurationChangedInternal(application, configuration);
        }
    }

    private void onApplicationTrimMemoryInternal(int level) {
        // TODO: think about recording crash report
    }

    // TODO: think about this
    private void onConfigurationChangedInternal(Application application, Configuration configuration) {
        final Ctx ctx = ctx(application.getApplicationContext());
        eachModule(new Modulator() {
            @Override
            public void run(int feature, Module module) {
                module.onConfigurationChanged(ctx);
            }
        });
    }

    private void onActivityCreatedInternal(Activity activity, Bundle bundle) {
        final CtxImpl ctx = new CtxImpl(this, config(), activity, bundle);
        eachModule(new Modulator() {
            @Override
            public void run(int feature, Module module) {
                module.onActivityCreated(ctx);
            }
        });
    }

    private void onActivityStartedInternal(Activity activity) {
        final CtxImpl ctx = new CtxImpl(this, config(), activity, null);
        eachModule(new Modulator() {
            @Override
            public void run(int feature, Module module) {
                module.onActivityStarted(ctx);
            }
        });
    }

    private void onActivityResumedInternal(Activity activity) {
        final CtxImpl ctx = new CtxImpl(this, config(), activity, null);
        eachModule(new Modulator() {
            @Override
            public void run(int feature, Module module) {
                module.onActivityResumed(ctx);
            }
        });
    }

    private void onActivityPausedInternal(Activity activity) {
        final CtxImpl ctx = new CtxImpl(this, config(), activity, null);
        eachModule(new Modulator() {
            @Override
            public void run(int feature, Module module) {
                module.onActivityCreated(ctx);
            }
        });
    }

    private void onActivityStoppedInternal(Activity activity) {
        final CtxImpl ctx = new CtxImpl(this, config(), activity, null);
        eachModule(new Modulator() {
            @Override
            public void run(int feature, Module module) {
                module.onActivityStopped(ctx);
            }
        });
    }

    private void onActivitySaveInstanceStateInternal(Activity activity, Bundle bundle) {
        final CtxImpl ctx = new CtxImpl(this, config(), activity, bundle);
        eachModule(new Modulator() {
            @Override
            public void run(int feature, Module module) {
                module.onActivitySaveInstanceState(ctx);
            }
        });
    }

    private void onActivityDestroyedInternal(Activity activity) {
        final CtxImpl ctx = new CtxImpl(this, config(), activity, null);
        eachModule(new Modulator() {
            @Override
            public void run(int feature, Module module) {
                module.onActivityDestroyed(ctx);
            }
        });
    }

    @Override
    public void onSignal(CtxCore ctx, int id, Byteable param1, Byteable param2) {
        Intent intent = new Intent((Context) ctx.getContext(), CountlyService.class);
        intent.putExtra(CountlyService.CMD, id);
        if (param1 != null) {
            intent.putExtra(CountlyService.PARAM_1, param1.store());
        }
        if (param2 != null) {
            intent.putExtra(CountlyService.PARAM_2, param2.store());
        }
        ((Context)ctx.getContext()).startService(intent);
    }

    @Override
    public void onSignal(CtxCore ctx, int id, String param) {
        if (ctx.getConfig().isDefaultNetworking()) {
            if (id == Signal.Crash.getIndex()) {
                try {
                    CrashImpl crash = new CrashImpl(Long.parseLong(param));
                    crash = Storage.read(ctx, crash);
                    if (crash == null) {
                        L.e("Cannot read crash from storage, skipping");
                        return;
                    }

                    Request request = ModuleRequests.nonSessionRequest(ctx);
                    ModuleCrash.putCrashIntoParams(crash, request.params);
                    if (Storage.push(ctx, request)) {
                        L.i("Added request " + request.storageId() + " instead of crash " + crash.storageId());
                        Boolean success = Storage.remove(ctx, crash);
                        L.d("crash " + id + " removal result is " + success);
                    } else {
                        L.e("Couldn't write request " + request.storageId() + " instead of crash " + crash.storageId());
                    }
                } catch (Throwable t) {
                    L.wtf("Error when making a request out of a crash", t);
                }
            }
            networking.check(ctx);
        } else {
            Intent intent = new Intent((Context) ctx.getContext(), CountlyService.class);
            intent.putExtra(CountlyService.CMD, id);
            if (Utils.isNotEmpty(param)) {
                intent.putExtra(CountlyService.PARAM_1, param);
            }
            ((Context)ctx.getContext()).startService(intent);
        }
    }

}
