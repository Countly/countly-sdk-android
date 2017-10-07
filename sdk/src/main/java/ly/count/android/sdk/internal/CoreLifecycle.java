package ly.count.android.sdk.internal;

import android.app.Activity;
import android.app.Application;
import android.content.*;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Application lifecycle-related methods of {@link Core}
 */

public class CoreLifecycle {
    private static final Log.Module L = Log.module("CoreLifecycle");

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

    public static void sendToService(Context ctx, int code, Map<String, Object> params) {
        Intent intent = new Intent(ctx.getContext(), CountlyService.class);
        intent.putExtra(CountlyService.CMD, code);
        if (params != null) {
            for (String key : params.keySet()) {
                Object value = params.get(key);
                if (value instanceof byte[]) {
                    intent.putExtra(key, (byte[])value);
                } else if (value instanceof String) {
                    intent.putExtra(key, (String)value);
                } else if (value instanceof Long) {
                    intent.putExtra(key, (Long)value);
                } else {
                    L.wtf("Unsupported type for service intent: " + value);
                }
            }
        }
        ctx.getContext().startService(intent);
    }

    /**
     * Lifecycle methods. For Android versions later or equal to Ice Cream Sandwich, developer
     * doesn't need to call those from each activity. In case app supports earlier version,
     * it's developer responsibility. In any case, for API 14+ Countly ignores dev calls.
     */
    public void onContextAcquired(final Application application) {
        L.d("Application created");

        Context ctx = new ContextImpl(application);

        Storage.push(ctx, this.config);
        CoreLifecycle.sendToService(ctx, CountlyService.CMD_START, null);

        this.config.setLimited(false);

        if (Utils.API(14)) {
            application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(Activity activity, Bundle bundle) {
                    L.d("Activity created: " + activity.getClass().getSimpleName());
                    CoreLifecycle.this.onActivityCreatedInternal(activity, bundle);
                }

                @Override
                public void onActivityStarted(Activity activity) {
                    L.d("Activity started: " + activity.getClass().getSimpleName());
                    CoreLifecycle.this.onActivityStartedInternal(activity);
                }

                @Override
                public void onActivityResumed(Activity activity) {
                    L.d("Activity resumed: " + activity.getClass().getSimpleName());
                    CoreLifecycle.this.onActivityResumedInternal(activity);
                }

                @Override
                public void onActivityPaused(Activity activity) {
                    L.d("Activity paused: " + activity.getClass().getSimpleName());
                    CoreLifecycle.this.onActivityPausedInternal(activity);
                }

                @Override
                public void onActivityStopped(Activity activity) {
                    L.d("Activity stopped: " + activity.getClass().getSimpleName());
                    CoreLifecycle.this.onActivityStoppedInternal(activity);
                }

                @Override
                public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
                    L.d("Activity save state: " + activity.getClass().getSimpleName());
                    CoreLifecycle.this.onActivitySaveInstanceStateInternal(activity, bundle);
                }

                @Override
                public void onActivityDestroyed(Activity activity) {
                    L.d("Activity destroyed: " + activity.getClass().getSimpleName());
                    CoreLifecycle.this.onActivityDestroyedInternal(activity);
                }
            });
            application.registerComponentCallbacks(new ComponentCallbacks2() {
                @Override
                public void onTrimMemory(int i) {
                    L.d("Trim memory " + i);
                    CoreLifecycle.this.onApplicationTrimMemoryInternal(i);
                }

                @Override
                public void onConfigurationChanged(Configuration configuration) {
                    // TODO: Operator, screen, etc
                    L.d("Configuration changed: " + configuration.toString());
                    CoreLifecycle.this.onConfigurationChangedInternal(application, configuration);
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
        ContextImpl ctx = new ContextImpl(application);
        for (Module m : modules) {
            m.onConfigurationChanged(ctx);
        }
    }

    private void onActivityCreatedInternal(Activity activity, Bundle bundle) {
        ContextImpl ctx = new ContextImpl(activity, bundle);
        for (Module m : modules) {
            m.onActivityCreated(ctx);
        }
    }

    private void onActivityStartedInternal(Activity activity) {
        ContextImpl ctx = new ContextImpl(activity, null);
        for (Module m : modules) {
            m.onActivityStarted(ctx);
        }
    }

    private void onActivityResumedInternal(Activity activity) {
        ContextImpl ctx = new ContextImpl(activity, null);
        for (Module m : modules) {
            m.onActivityResumed(ctx);
        }
    }

    private void onActivityPausedInternal(Activity activity) {
        ContextImpl ctx = new ContextImpl(activity, null);
        for (Module m : modules) {
            m.onActivityCreated(ctx);
        }
    }

    private void onActivityStoppedInternal(Activity activity) {
        ContextImpl ctx = new ContextImpl(activity, null);
        for (Module m : modules) {
            m.onActivityStopped(ctx);
        }
    }

    private void onActivitySaveInstanceStateInternal(Activity activity, Bundle bundle) {
        ContextImpl ctx = new ContextImpl(activity, bundle);
        for (Module m : modules) {
            m.onActivitySaveInstanceState(ctx);
        }
    }

    private void onActivityDestroyedInternal(Activity activity) {
        ContextImpl ctx = new ContextImpl(activity, null);
        for (Module m : modules) {
            m.onActivityDestroyed(ctx);
        }
    }
}
