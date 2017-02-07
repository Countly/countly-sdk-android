package ly.count.android.sdk.internal;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;

import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ly.count.android.sdk.Config;

/**
 * Core class of Countly SDK.
 * Contract:
 * <ul>
 *     <li>All high-level non-module-related logic.</li>
 *     <li>All interactions with Android platform.</li>
 *     <li>Cannot be started twice, can be not stopped due to a crash.</li>
 * </ul>
 *
 */
public class Core {
    /**
     * Current instance of Core
     */
    static Core instance;

    /**
     * Mappings of {@link Config.Feature} to {@link Module} class.
     * Changed by using {@link #setModuleMapping(Config.Feature, Class)}.
     */
    private static final Map<Config.Feature, Class<? extends Module>> moduleMappings = new HashMap<>();
    static {
//        setModuleMapping(Config.Feature.Analytics, ModuleSessions.class);
    }

    /**
     * Change default mapping of {@link Config.Feature} to {@link Module} class.
     *
     * @param feature feature to map
     * @param cls {@link Module} class to use for this feature
     */
    public static void setModuleMapping(Config.Feature feature, Class<? extends Module> cls) {
        moduleMappings.put(feature, cls);
    }

    /**
     * Core instance config
     */
    private final InternalConfig config;

    /**
     * Core instance list of sessions created
     */
    private final List<SessionImpl> sessions;

    /**
     * List of {@link Module} instances built based on {@link #config}
     */
    private final List<Module> modules;

    /**
     * The only Core constructor, rewrites {@link #instance}
     *
     * @param config Countly configuration
     * @throws IllegalArgumentException in case {@code config} is inconsistent
     * for some of {@link Module}s required for {@code config}.
     */
    public Core (Config config) throws IllegalArgumentException {
        try {
            this.config = new InternalConfig(config);
            this.modules = buildModules();
            this.sessions = new ArrayList<>();
            instance = this;
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Create instances of {@link Module}s required by {@link #config}.
     * Uses {@link #moduleMappings} for Feature - Class&lt;Module&gt; mapping to enable
     * overriding by app developer.
     *
     * @return {@link List} of {@link Module} instances defined by {@link #config}
     * @throws IllegalArgumentException in case some {@link Module} finds {@link #config} inconsistent.
     * @throws IllegalStateException when this module is run second time on the same {@code Core} instance.
     */
    private List<Module> buildModules() throws IllegalArgumentException, IllegalStateException {
        if (modules == null) {
            List<Module> modules = new ArrayList<>();

            if (config.getLoggingLevel() != Config.LoggingLevel.OFF) {
                modules.add(new Log());
            }

            if (!config.isProgrammaticSessionsControl()) {
                modules.add(new ModuleSessions());
            }

            for (Config.Feature f : config.getFeatures()) {
                Class<? extends Module> cls = moduleMappings.get(f);
                if (cls == null) {
                    Log.wtf("No module class for feature " + f);
                } else {
                    Module module = instantiateModule(moduleMappings.get(f));
                    if (module != null) {
                        modules.add(module);
                    }
                }
            }

            return modules;
        } else {
            throw new IllegalStateException("Modules can be built only once per InternalConfig instance");
        }
    }

    /**
     * Create {@link Module} by executing its default constructor.
     *
     * @param cls class of {@link Module}
     * @return {@link Module} instance or null in case of error
     */
    private Module instantiateModule(Class<? extends Module> cls) {
        try {
            return (Module)cls.getConstructors()[0].newInstance();
        } catch (InstantiationException e) {
            Log.wtf("Module cannot be instantiated", e);
        } catch (IllegalAccessException e) {
            Log.wtf("Module constructor cannot be accessed", e);
        } catch (InvocationTargetException e) {
            Log.wtf("Module constructor cannot be invoked", e);
        }
        return null;
    }
    /**
     * Add session to the list.
     * @return {@link SessionImpl} just created
     */
    SessionImpl sessionAdd(){
        sessions.add(new SessionImpl());
        return sessions.get(sessions.size() - 1);
    }

    /**
     * Remove session from the list.
     *
     * @param session {@link SessionImpl} to remove
     * @return {@link SessionImpl} instance in case next session already created
     */
    SessionImpl sessionRemove(SessionImpl session){
        if (sessions.contains(session)) {
            sessions.remove(session);
            if (sessions.size() > 0) {
                SessionImpl next = sessions.get(0);
                if (next.began == null) {
                    next.begin(session.ended + Math.round(SessionImpl.SECOND));
                } else if (next.began < session.ended){
                    next.began = session.ended + Math.round(SessionImpl.SECOND);
                }
                return next;
            }
        }
        return null;
    }

    /**
     * Current leading {@link ly.count.android.sdk.Session}
     *
     * @return leading session
     */
    SessionImpl sessionLeading(){
        return sessions.size() > 0 ? sessions.get(0) : null;
    }

    /**
     * Begin session and notify all {@link Module} instances
     *
     * @param session session to begin
     * @return supplied session for method chaining
     */
    SessionImpl sessionBegin(SessionImpl session){
        session.begin();
        for (Module m : Core.instance.modules) {
            m.onSessionBegan(session);
        }
        return session;
    }

    /**
     * End session and notify all {@link Module} instances
     *
     * @param session session to end
     * @return supplied session for method chaining
     */
    SessionImpl sessionEnd(SessionImpl session){
        session.end();
        for (Module m : Core.instance.modules) {
            m.onSessionEnded(session);
        }
        return session;
    }

    /**
     * Lifecycle methods. For Android versions later or equal to Ice Cream Sandwich, developer
     * doesn't need to call those from each activity. In case app supports earlier version,
     * it's developer responsibility. In any case, for API 14+ Countly ignores dev calls.
     */
    public void onApplicationCreated(Application application) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(Activity activity, Bundle bundle) {
                    Core.this.onActivityCreatedInternal(activity, bundle);
                }

                @Override
                public void onActivityStarted(Activity activity) {
                    Core.this.onActivityStartedInternal(activity);
                }

                @Override
                public void onActivityResumed(Activity activity) {
                    Core.this.onActivityResumedInternal(activity);
                }

                @Override
                public void onActivityPaused(Activity activity) {
                    Core.this.onActivityPausedInternal(activity);
                }

                @Override
                public void onActivityStopped(Activity activity) {
                    Core.this.onActivityStoppedInternal(activity);
                }

                @Override
                public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
                    Core.this.onActivitySaveInstanceStateInternal(activity, bundle);
                }

                @Override
                public void onActivityDestroyed(Activity activity) {
                    Core.this.onActivityDestroyedInternal(activity);
                }
            });
            application.registerComponentCallbacks(new ComponentCallbacks2() {
                @Override
                public void onTrimMemory(int i) {
                    Core.this.onApplicationTrimMemoryInternal(i);
                }

                @Override
                public void onConfigurationChanged(Configuration configuration) {
                }

                @Override
                public void onLowMemory() {
                }
            });
        }

        ContextImpl context = new ContextImpl(application);
        for (Module m : modules) {
            m.onApplicationCreated(context);
        }
        context.expire();
    }

    public void onApplicationTrimMemory(int level) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            this.onApplicationTrimMemoryInternal(level);
        }
    }

    public void onActivityCreated(Activity activity, Bundle bundle) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            this.onActivityCreatedInternal(activity, bundle);
        }
    }

    public void onActivityStarted(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            this.onActivityStartedInternal(activity);
        }
    }

    public void onActivityResumed(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            this.onActivityResumedInternal(activity);
        }
    }

    public void onActivityPaused(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            this.onActivityPausedInternal(activity);
        }
    }

    public void onActivityStopped(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            this.onActivityStoppedInternal(activity);
        }
    }

    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            this.onActivitySaveInstanceStateInternal(activity, bundle);
        }
    }

    public void onActivityDestroyed(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
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
