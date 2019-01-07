package ly.count.sdk.internal;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import ly.count.sdk.ConfigCore;

/**
 * {@link Module}-related methods of {@link SDKInterface}
 */

public abstract class SDKModules implements SDKInterface {
    private static final Log.Module L = Log.module("SDKModules");
    private static Module testDummyModule = null;

    /**
     * All known mappings of {@code ConfigCore.Feature} to {@link Module} class.
     */
    private static final Map<Integer, Class<? extends Module>> DEFAULT_MAPPINGS = new HashMap<>();

    protected static void registerDefaultModuleMapping(int feature, Class<? extends Module> cls) {
        DEFAULT_MAPPINGS.put(feature, cls);
    }

    static {
        registerDefaultModuleMapping(CoreFeature.DeviceId.getIndex(), ModuleDeviceId.class);
        registerDefaultModuleMapping(CoreFeature.Requests.getIndex(), ModuleRequests.class);
        registerDefaultModuleMapping(CoreFeature.Logs.getIndex(), Log.class);
        registerDefaultModuleMapping(CoreFeature.Views.getIndex(), ModuleViews.class);
        registerDefaultModuleMapping(CoreFeature.Sessions.getIndex(), ModuleSessions.class);
    }

    public interface Modulator {
        void run(int feature, Module module);
    }

    /**
     * Selected by config map of module mappings
     */
    private static final Map<Integer, Class<? extends Module>> moduleMappings = new HashMap<>();

    protected static void registerModuleMapping(int feature, Class<? extends Module> cls) {
        if (cls != null) {
            moduleMappings.put(feature, cls);
        }
    }

    // TreeMap to keep modules sorted by their feature indexes
    protected Map<Integer, Module> modules;

    /**
     * Check if consent has been given for a feature
     *
     * @param feat feature to test against, pass null to test if any consent given
     * @return {@code true} if consent has been given
     */
    public boolean isTracking(Integer feat) {
        return modules != null && modules.containsKey(feat);
    }

    @Override
    public void init(Ctx ctx) {
        prepareMappings(ctx);
    }

    @Override
    public void stop(final Ctx ctx, final boolean clear) {
        eachModule(new Modulator() {
            @Override
            public void run(int feature, Module module) {
                try {
                    module.stop(ctx, clear);
                    Utils.reflectiveSetField(module, "active", false);
                } catch (Throwable e) {
                    L.wtf("Exception while stopping " + module.getClass(), e);
                }
            }
        });
        modules.clear();
        moduleMappings.clear();
    }

    /**
     * Callback to add consents to the list
     *
     * @param consent consents to add
     */
    public void onConsent(Ctx ctx, int consent) {
        for (Integer feature : moduleMappings.keySet()) {
            if (ctx.getConfig().isFeatureEnabled(feature) && (feature & consent) > 0 && !modules.containsKey(feature)) {
                Class<? extends Module> cls = moduleMappings.get(feature);
                if (cls == null) {
                    Log.i("No module mapping for feature " + feature);
                    continue;
                }

                Module module = instantiateModule(cls);
                if (module == null) {
                    Log.wtf("Cannot instantiate module " + feature);
                } else {
                    module.init(ctx.getConfig());
                    module.onContextAcquired(ctx);
                    modules.put(feature, module);
                }
            }
        }
    }

    /**
     * Callback to remove consents from the list
     *
     * @param noConsent consents to remove
     */
    public void onConsentRemoval(Ctx ctx, int noConsent) {
        for (Integer feature : moduleMappings.keySet()) {
            if ((feature & noConsent) > 0 && modules.containsKey(feature)) {
                Module module = module(feature);
                if (module != null) {
                    module.stop(ctx, true);
                    modules.remove(feature);
                }
            }
        }
    }

    /**
     * Create instances of {@link Module}s required by {@link #config}.
     * Uses {@link #moduleMappings} for {@code ConfigCore.Feature} / {@link CoreFeature}
     * - Class&lt;Module&gt; mapping to enable overriding by app developer.
     *
     * @param ctx {@link Ctx} object containing config with mapping overrides
     * @throws IllegalArgumentException in case some {@link Module} finds {@link #config} inconsistent.
     * @throws IllegalStateException when this module is run second time on the same {@code Core} instance.
     */
    protected void prepareMappings(Ctx ctx) throws IllegalStateException {
        if (modules.size() > 0) {
            throw new IllegalStateException("Modules can only be built once");
        }

        moduleMappings.clear();
        moduleMappings.putAll(DEFAULT_MAPPINGS);

        for (int feature : ctx.getConfig().getModuleOverrides()) {
            registerModuleMapping(feature, ctx.getConfig().getModuleOverride(feature));
        }
    }


    /**
     * Create instances of {@link Module}s required by {@link #config}.
     * Uses {@link #moduleMappings} for {@code ConfigCore.Feature} / {@link CoreFeature}
     * - Class&lt;Module&gt; mapping to enable overriding by app developer.
     *
     * @param ctx {@link Ctx} object
     * @param features consents bitmask to check against
     * @throws IllegalArgumentException in case some {@link Module} finds {@link #config} inconsistent.
     * @throws IllegalStateException when this module is run second time on the same {@code Core} instance.
     */
    protected void buildModules(Ctx ctx, int features) throws IllegalArgumentException, IllegalStateException {
        // override module mappings in native/Android parts, overriding by ConfigCore ones if necessary

        if (modules.size() > 0) {
            throw new IllegalStateException("Modules can only be built once");
        }

        if (ctx.getConfig().getLoggingLevel() != ConfigCore.LoggingLevel.OFF) {
            modules.put(CoreFeature.Logs.getIndex(), instantiateModule(moduleMappings.get(CoreFeature.Logs.getIndex())));
        }

        // standard required internal features
        modules.put(-1, instantiateModule(moduleMappings.get(CoreFeature.Requests.getIndex())));
        modules.put(-2, instantiateModule(moduleMappings.get(CoreFeature.DeviceId.getIndex())));

        for (int feature : moduleMappings.keySet()) {
            Class<? extends Module> cls = moduleMappings.get(feature);
            if (cls != null && (features & feature) > 0) {
                Module m = instantiateModule(cls);
                if (m != null) {
                    modules.put(feature, m);
                }
            }
        }
    }

    /**
     * Create {@link Module} by executing its default constructor.
     *
     * @param cls class of {@link Module}
     * @return {@link Module} instance or null in case of error
     */
    private static Module instantiateModule(Class<? extends Module> cls) {
        try {
            return (Module)cls.getConstructors()[0].newInstance();
        } catch (InstantiationException e) {
            L.wtf("Module cannot be instantiated", e);
        } catch (IllegalAccessException e) {
            L.wtf("Module constructor cannot be accessed", e);
        } catch (InvocationTargetException e) {
            L.wtf("Module constructor cannot be invoked", e);
        } catch (IllegalArgumentException e) {
            try {
                return (Module)cls.getConstructors()[0].newInstance((Object)null);
            } catch (InstantiationException e1) {
                L.wtf("Module cannot be instantiated", e);
            } catch (IllegalAccessException e1) {
                L.wtf("Module constructor cannot be accessed", e);
            } catch (InvocationTargetException e1) {
                L.wtf("Module constructor cannot be invoked", e);
            }
        }
        return null;
    }

    /**
     * Return module instance by {@code ConfigCore.Feature}
     *
     * @param feature to get a {@link Module} instance for
     * @return {@link Module} instance or null if no such module is instantiated
     */
    protected Module module(int feature) {
        return module(moduleMappings.get(feature));
    }

    /**
     * Return module instance by {@link Module} class
     *
     * @param cls class to get a {@link Module} instance for
     * @return {@link Module} instance or null if no such module is instantiated
     */
    @SuppressWarnings("unchecked")
    public  <T extends Module> T module(Class<T> cls) {
        for (Module module: modules.values()) {
            if (module.getClass().isAssignableFrom(cls)) {
                return (T) module;
            }
        }
        return null;
    }

    protected void eachModule(Modulator modulator) {
        for (Integer feature: modules.keySet()) {
            modulator.run(feature, modules.get(feature));
        }
    }

    @Override
    public SessionImpl onSessionBegan(Ctx ctx, SessionImpl session){
        for (Module m : modules.values()) {
            m.onSessionBegan(session, ctx);
        }
        return session;
    }

    @Override
    public SessionImpl onSessionEnded(Ctx ctx, SessionImpl session){
        for (Module m : modules.values()) {
            m.onSessionEnded(session, ctx);
        }
        ModuleSessions sessions = (ModuleSessions) module(CoreFeature.Sessions.getIndex());
        if (sessions != null) {
            sessions.forgetSession();
        }
        return session;
    }

    @Override
    public SessionImpl getSession() {
        ModuleSessions sessions = (ModuleSessions) module(CoreFeature.Sessions.getIndex());
        if (sessions != null) {
            return sessions.getSession();
        }
        return null;
    }

    @Override
    public SessionImpl session(Ctx ctx, Long id) {
        ModuleSessions sessions = (ModuleSessions) module(CoreFeature.Sessions.getIndex());
        if (sessions != null) {
            return sessions.session(ctx, id);
        }
        return null;
    }

    interface ModuleCallback {
        void call(Module module);
    }
}
