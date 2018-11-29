package ly.count.sdk.android.internal;

import android.app.Activity;
import android.app.Application;
import android.content.*;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

import ly.count.sdk.internal.Byteable;
import ly.count.sdk.internal.InternalConfig;
import ly.count.sdk.internal.Log;
import ly.count.sdk.internal.Module;
import ly.count.sdk.internal.SDKCore;
import ly.count.sdk.internal.Storage;

/**
 * Application lifecycle-related methods of {@link Core}
 */

public abstract class SDKLifecycle extends SDKCore {
    private static final Log.Module L = Log.module("SDKLifecycle");

    /**
     * Current instance of Core
     */
    static Core instance;

    /**
     * Core instance config
     */
    protected InternalConfig config;

    /**
     * List of {@link Module} instances built based on {@link #config}
     */
    protected final List<Module> modules = new ArrayList<>();

    protected SDKLifecycle(InternalConfig config) {
        super(config);
    }

    CtxImpl ctx (Context context) {
        return new CtxImpl(this, config, context);
    }

    /**
     * Lifecycle methods. For Android versions later or equal to Ice Cream Sandwich, developer
     * doesn't need to call those from each activity. In case app supports earlier version,
     * it's developer responsibility. In any case, for API 14+ Countly ignores dev calls.
     */
    protected void onContextAcquired(final Application application) {
        L.d("Application created");

        Ctx ctx = new CtxImpl(this, config(), application);

        Storage.push(ctx, this.config);
        onSignal(ctx, Signal.Start.getIndex(), null);

        this.config.setLimited(false);

        if (Utils.API(14)) {
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

        for (Module m : modules) {
            m.onContextAcquired(ctx);
        }
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

    private void onConfigurationChangedInternal(Application application, Configuration configuration) {
        CtxImpl ctx = new CtxImpl(this, config(), application);
        for (Module m : modules) {
            m.onConfigurationChanged(ctx);
        }
    }

    private void onActivityCreatedInternal(Activity activity, Bundle bundle) {
        CtxImpl ctx = new CtxImpl(this, config(), activity, bundle);
        for (Module m : modules) {
            m.onActivityCreated(ctx);
        }
    }

    private void onActivityStartedInternal(Activity activity) {
        CtxImpl ctx = new CtxImpl(this, config(), activity, null);
        for (Module m : modules) {
            m.onActivityStarted(ctx);
        }
    }

    private void onActivityResumedInternal(Activity activity) {
        CtxImpl ctx = new CtxImpl(this, config(), activity, null);
        for (Module m : modules) {
            m.onActivityResumed(ctx);
        }
    }

    private void onActivityPausedInternal(Activity activity) {
        CtxImpl ctx = new CtxImpl(this, config(), activity, null);
        for (Module m : modules) {
            m.onActivityCreated(ctx);
        }
    }

    private void onActivityStoppedInternal(Activity activity) {
        CtxImpl ctx = new CtxImpl(this, config(), activity, null);
        for (Module m : modules) {
            m.onActivityStopped(ctx);
        }
    }

    private void onActivitySaveInstanceStateInternal(Activity activity, Bundle bundle) {
        CtxImpl ctx = new CtxImpl(this, config(), activity, bundle);
        for (Module m : modules) {
            m.onActivitySaveInstanceState(ctx);
        }
    }

    private void onActivityDestroyedInternal(Activity activity) {
        CtxImpl ctx = new CtxImpl(this, config(), activity, null);
        for (Module m : modules) {
            m.onActivityDestroyed(ctx);
        }
    }

    @Override
    public void onSignal(ly.count.sdk.internal.Ctx ctx, int id, Byteable param1, Byteable param2) {
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
    public void onSignal(ly.count.sdk.internal.Ctx ctx, int id, String param) {
        Intent intent = new Intent((Context) ctx.getContext(), CountlyService.class);
        intent.putExtra(CountlyService.CMD, id);
        if (Utils.isNotEmpty(param)) {
            intent.putExtra(CountlyService.PARAM_1, param);
        }
        ((Context)ctx.getContext()).startService(intent);
    }

}
