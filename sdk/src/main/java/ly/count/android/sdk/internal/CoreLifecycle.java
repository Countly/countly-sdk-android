package ly.count.android.sdk.internal;

import android.app.Activity;
import android.app.Application;
import android.content.*;
import android.content.Context;
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

    /**
     * TODO: check service case and change to Application if possible
     */
    protected android.content.Context longLivingContext;

    public static void sendToService(Context context, int code, Map<String, byte[]> params) {
        Intent intent = new Intent(context, CountlyService.class);
        intent.putExtra(CountlyService.CMD, code);
        if (params != null) {
            for (String key : params.keySet()) {
                intent.putExtra(key, params.get(key));
            }
        }
        context.startService(intent);
    }

    /**
     * Lifecycle methods. For Android versions later or equal to Ice Cream Sandwich, developer
     * doesn't need to call those from each activity. In case app supports earlier version,
     * it's developer responsibility. In any case, for API 14+ Countly ignores dev calls.
     */
    public void onContextAcquired(Application application) {
        Log.d("Application created");
        longLivingContext = application.getApplicationContext();

        Storage.push(this.config);
        CoreLifecycle.sendToService(application, CountlyService.CMD_START, null);

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
                }

                @Override
                public void onLowMemory() {
                    Log.d("[Lifecycle] Low memory");
                }
            });
        }

        ContextImpl context = new ContextImpl(application);
        for (Module m : modules) {
            m.onContextAcquired(context);
        }
        context.expire();
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

    private void onApplicationTrimMemoryInternal(int level) {
        // TODO: think about recording crash report
    }

    private void onActivityCreatedInternal(Activity activity, Bundle bundle) {
        ContextImpl context = new ContextImpl(activity, bundle);
        for (Module m : modules) {
            m.onActivityCreated(context);
        }
        context.expire();
    }

    private void onActivityStartedInternal(Activity activity) {
        ContextImpl context = new ContextImpl(activity, null);
        for (Module m : modules) {
            m.onActivityStarted(context);
        }
        context.expire();
    }

    private void onActivityResumedInternal(Activity activity) {
        ContextImpl context = new ContextImpl(activity, null);
        for (Module m : modules) {
            m.onActivityResumed(context);
        }
        context.expire();
    }

    private void onActivityPausedInternal(Activity activity) {
        ContextImpl context = new ContextImpl(activity, null);
        for (Module m : modules) {
            m.onActivityCreated(context);
        }
        context.expire();
    }

    private void onActivityStoppedInternal(Activity activity) {
        ContextImpl context = new ContextImpl(activity, null);
        for (Module m : modules) {
            m.onActivityStopped(context);
        }
        context.expire();
    }

    private void onActivitySaveInstanceStateInternal(Activity activity, Bundle bundle) {
        ContextImpl context = new ContextImpl(activity, bundle);
        for (Module m : modules) {
            m.onActivitySaveInstanceState(context);
        }
        context.expire();
    }

    private void onActivityDestroyedInternal(Activity activity) {
        ContextImpl context = new ContextImpl(activity, null);
        for (Module m : modules) {
            m.onActivityDestroyed(context);
        }
        context.expire();
    }
}
