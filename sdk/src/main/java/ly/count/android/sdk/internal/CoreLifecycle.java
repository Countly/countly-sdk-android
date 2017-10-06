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
                    Log.wtf("Unsupported type for service intent: " + value);
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
        Log.d("Application created");

        Context ctx = new ContextImpl(application);

        Storage.push(ctx, this.config);
        CoreLifecycle.sendToService(ctx, CountlyService.CMD_START, null);

        this.config.setLimited(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(Activity activity, Bundle bundle) {
                    Log.d("[Lifecycle] Activity created: " + activity.getClass().getSimpleName());
                    CoreLifecycle.this.onActivityCreatedInternal(activity, bundle);
                }

                @Override
                public void onActivityStarted(Activity activity) {
                    Log.d("[Lifecycle] Activity started: " + activity.getClass().getSimpleName());
                    CoreLifecycle.this.onActivityStartedInternal(activity);
                }

                @Override
                public void onActivityResumed(Activity activity) {
                    Log.d("[Lifecycle] Activity resumed: " + activity.getClass().getSimpleName());
                    CoreLifecycle.this.onActivityResumedInternal(activity);
                }

                @Override
                public void onActivityPaused(Activity activity) {
                    Log.d("[Lifecycle] Activity paused: " + activity.getClass().getSimpleName());
                    CoreLifecycle.this.onActivityPausedInternal(activity);
                }

                @Override
                public void onActivityStopped(Activity activity) {
                    Log.d("[Lifecycle] Activity stopped: " + activity.getClass().getSimpleName());
                    CoreLifecycle.this.onActivityStoppedInternal(activity);
                }

                @Override
                public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
                    Log.d("[Lifecycle] Activity save state: " + activity.getClass().getSimpleName());
                    CoreLifecycle.this.onActivitySaveInstanceStateInternal(activity, bundle);
                }

                @Override
                public void onActivityDestroyed(Activity activity) {
                    Log.d("[Lifecycle] Activity destroyed: " + activity.getClass().getSimpleName());
                    CoreLifecycle.this.onActivityDestroyedInternal(activity);
                }
            });
            application.registerComponentCallbacks(new ComponentCallbacks2() {
                @Override
                public void onTrimMemory(int i) {
                    Log.d("[Lifecycle] Trim memory " + i);
                    CoreLifecycle.this.onApplicationTrimMemoryInternal(i);
                }

                @Override
                public void onConfigurationChanged(Configuration configuration) {
                    // TODO: Operator, screen, etc
                    Log.d("[Lifecycle] Configuration changed: " + configuration.toString());
                    CoreLifecycle.this.onConfigurationChangedInternal(application, configuration);
                }

                @Override
                public void onLowMemory() {
                    Log.d("[Lifecycle] Low memory");
                }
            });
        }

        for (Module m : modules) {
            m.onContextAcquired(ctx);
        }
    }

    public void onApplicationTrimMemory(int level) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            Log.d("[Callback] Trim memory " + level);
            this.onApplicationTrimMemoryInternal(level);
        }
    }

    public void onActivityCreated(Activity activity, Bundle bundle) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            Log.d("[Callback] Activity created: " + activity.getClass().getSimpleName());
            this.onActivityCreatedInternal(activity, bundle);
        }
    }

    public void onActivityStarted(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            Log.d("[Callback] Activity started: " + activity.getClass().getSimpleName());
            this.onActivityStartedInternal(activity);
        }
    }

    public void onActivityResumed(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            Log.d("[Callback] Activity resumed: " + activity.getClass().getSimpleName());
            this.onActivityResumedInternal(activity);
        }
    }

    public void onActivityPaused(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            Log.d("[Callback] Activity paused: " + activity.getClass().getSimpleName());
            this.onActivityPausedInternal(activity);
        }
    }

    public void onActivityStopped(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            Log.d("[Callback] Activity stopped: " + activity.getClass().getSimpleName());
            this.onActivityStoppedInternal(activity);
        }
    }

    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            Log.d("[Callback] Activity save state: " + activity.getClass().getSimpleName());
            this.onActivitySaveInstanceStateInternal(activity, bundle);
        }
    }

    public void onActivityDestroyed(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            Log.d("[Callback] Activity destroyed: " + activity.getClass().getSimpleName());
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
