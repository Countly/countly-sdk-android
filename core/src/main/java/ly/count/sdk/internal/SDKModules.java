package ly.count.sdk.internal;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import ly.count.sdk.ConfigCore;
import ly.count.sdk.Session;

/**
 * {@link Module}-related methods of {@link SDKInterface}
 */

public abstract class SDKModules implements SDKInterface {
    private static final Log.Module L = Log.module("SDKModules");
    private static Module testDummyModule = null;//set during testing when trying to check the SDK's lifecycle

    /**
     * All known mappings of {@code ConfigCore.Feature} to {@link Module} class.
     */
    private static final Map<Integer, Class<? extends Module>> DEFAULT_MAPPINGS = new HashMap<>();

    protected static void registerDefaultModuleMapping(int feature, Class<? extends Module> cls) {
        DEFAULT_MAPPINGS.put(feature, cls);
    }

    static {
        registerDefaultModuleMapping(CoreFeature.DeviceId.getIndex(), ModuleDeviceIdCore.class);
        registerDefaultModuleMapping(CoreFeature.Requests.getIndex(), ModuleRequests.class);
        registerDefaultModuleMapping(CoreFeature.Logs.getIndex(), Log.class);
        registerDefaultModuleMapping(CoreFeature.Views.getIndex(), ModuleViews.class);
        registerDefaultModuleMapping(CoreFeature.Sessions.getIndex(), ModuleSessions.class);
    }

    public interface Modulator {
        void run(int feature, Module module);
    }

    /**
     * Currently enabled features with consents
     */
    protected int consents = 0;

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
    public void init(CtxCore ctx) {
        prepareMappings(ctx);
    }

    @Override
    public void stop(final CtxCore ctx, final boolean clear) {
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

    private boolean addingConsent(int adding, CoreFeature feature) {
        return (consents & feature.getIndex()) == 0 && (adding & feature.getIndex()) > 0;
    }

    private boolean removingConsent(int removing, CoreFeature feature) {
        return (consents & feature.getIndex()) == feature.getIndex() && (removing & feature.getIndex()) == feature.getIndex();
    }

    /**
     * Callback to add consents to the list
     *
     * @param consent consents to add
     */
    public void onConsent(CtxCore ctx, int consent) {
        if (!config().requiresConsent()) {
            Log.wtf("onConsent() shouldn't be called when Config.requiresConsent() is false");
            return;
        }

        if (addingConsent(consent, CoreFeature.Sessions)) {
            SessionImpl session = module(ModuleSessions.class).getSession();
            if (session != null) {
                session.end();
            }

            consents = consents | (consent & ctx.getConfig().getFeatures());

            module(ModuleSessions.class).session(ctx, null).begin();
        }

        consents = consents | (consent & ctx.getConfig().getFeatures());

        for (Integer feature : moduleMappings.keySet()) {
            Module existing = module(moduleMappings.get(feature));
            if (SDKCore.enabled(feature) && existing == null) {
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
    public void onConsentRemoval(CtxCore ctx, int noConsent) {
        if (!config().requiresConsent()) {
            Log.wtf("onConsentRemoval() shouldn't be called when Config.requiresConsent() is false");
            return;
        }

        if (removingConsent(noConsent, CoreFeature.Sessions)) {
            SessionImpl session = module(ModuleSessions.class).getSession();
            if (session != null) {
                session.end();
            }
        }

        if (removingConsent(noConsent, CoreFeature.Location)) {
            user().edit().optOutFromLocationServices();
        }

        consents = consents & ~noConsent;

        for (Integer feature : moduleMappings.keySet()) {
            Module existing = module(moduleMappings.get(feature));
            if (feature != CoreFeature.Sessions.getIndex() && existing != null) {
                existing.stop(ctx, true);
                modules.remove(feature);
            }
        }
    }

    /**
     * Create instances of {@link Module}s required by {@link #config}.
     * Uses {@link #moduleMappings} for {@code ConfigCore.Feature} / {@link CoreFeature}
     * - Class&lt;Module&gt; mapping to enable overriding by app developer.
     *
     * @param ctx {@link CtxCore} object containing config with mapping overrides
     * @throws IllegalArgumentException in case some {@link Module} finds {@link #config} inconsistent.
     * @throws IllegalStateException when this module is run second time on the same {@code Core} instance.
     */
    protected void prepareMappings(CtxCore ctx) throws IllegalStateException {
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
     * @param ctx {@link CtxCore} object
     * @param features consents bitmask to check against
     * @throws IllegalArgumentException in case some {@link Module} finds {@link #config} inconsistent.
     * @throws IllegalStateException when this module is run second time on the same {@code Core} instance.
     */
    protected void buildModules(CtxCore ctx, int features) throws IllegalArgumentException, IllegalStateException {
        // override module mappings in native/Android parts, overriding by ConfigCore ones if necessary

        if (modules.size() > 0) {
            throw new IllegalStateException("Modules can only be built once");
        }

        if (ctx.getConfig().getLoggingLevel() != ConfigCore.LoggingLevel.OFF) {
            modules.put(-10, instantiateModule(moduleMappings.get(CoreFeature.Logs.getIndex())));
        }

        // standard required internal features
        modules.put(-3, instantiateModule(moduleMappings.get(CoreFeature.DeviceId.getIndex())));
        modules.put(-2, instantiateModule(moduleMappings.get(CoreFeature.Requests.getIndex())));
        modules.put(CoreFeature.Sessions.getIndex(), instantiateModule(moduleMappings.get(CoreFeature.Sessions.getIndex())));

        if (ctx.getConfig().requiresConsent()) {
            consents = 0;
        } else {
            consents = ctx.getConfig().getFeatures();
        }

        if (!ctx.getConfig().requiresConsent()) {
            for (int feature : moduleMappings.keySet()) {
                Class<? extends Module> cls = moduleMappings.get(feature);
                if (cls == null) {
                    continue;
                }
                Module existing = module(cls);
                if ((features & feature) > 0 && existing == null) {
                    Module m = instantiateModule(cls);
                    if (m != null) {
                        modules.put(feature, m);
                    }
                }
            }
        }

        // dummy module for tests if any
        if (testDummyModule != null) {
            modules.put(CoreFeature.TestDummy.getIndex(), testDummyModule);
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
    public SessionImpl onSessionBegan(CtxCore ctx, SessionImpl session){
        for (Module m : modules.values()) {
            m.onSessionBegan(session, ctx);
        }
        return session;
    }

    @Override
    public SessionImpl onSessionEnded(CtxCore ctx, SessionImpl session){
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
    public SessionImpl session(CtxCore ctx, Long id) {
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
