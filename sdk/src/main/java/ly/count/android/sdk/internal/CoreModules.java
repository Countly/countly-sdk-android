package ly.count.android.sdk.internal;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ly.count.android.sdk.Config;

/**
 * {@link Module}-related methods of {@link Core}
 */

public class CoreModules extends CoreStorage {
    private static final Log.Module L = Log.module("CoreModules");
    private static Module testDummyModule = null;

    /**
     * Default mappings of {@link Config.Feature} to {@link Module} class.
     */
    private static final Map<Config.Feat, Class<? extends Module>> DEFAULT_MAPPINGS = new HashMap<>();
    static {
        DEFAULT_MAPPINGS.put(Config.InternalFeature.DeviceId, ModuleDeviceId.class);
        DEFAULT_MAPPINGS.put(Config.InternalFeature.Requests, ModuleRequests.class);
        DEFAULT_MAPPINGS.put(Config.InternalFeature.Logs, Log.class);
        DEFAULT_MAPPINGS.put(Config.Feature.AutoSessionTracking, ModuleSessions.class);
        DEFAULT_MAPPINGS.put(Config.Feature.Crash, ModuleCrash.class);
        DEFAULT_MAPPINGS.put(Config.Feature.Attribution, ModuleAttribution.class);
        DEFAULT_MAPPINGS.put(Config.Feature.Push, ModulePush.class);
        DEFAULT_MAPPINGS.put(Config.Feature.AutoViewTracking, ModuleViews.class);
    }

    private final Map<Config.Feat, Class<? extends Module>> moduleMappings = new HashMap<>();

    /**
     * Create instances of {@link Module}s required by {@link #config}.
     * Uses {@link #moduleMappings} for {@link ly.count.android.sdk.Config.Feature} / {@link ly.count.android.sdk.Config.InternalFeature}
     * - Class&lt;Module&gt; mapping to enable overriding by app developer.
     *
     * @throws IllegalArgumentException in case some {@link Module} finds {@link #config} inconsistent.
     * @throws IllegalStateException when this module is run second time on the same {@code Core} instance.
     */
    protected void buildModules() throws IllegalArgumentException, IllegalStateException {
        if (modules.size() > 0){
            throw new IllegalStateException("Modules can be built only once per InternalConfig instance");
        }

        moduleMappings.clear();
        moduleMappings.putAll(DEFAULT_MAPPINGS);

        for (Config.Feature feature : Config.Feature.values()) {
            Class<? extends Module> override = config.getModuleOverride(feature);
            if (override != null) {
                moduleMappings.put(feature, override);
            }
        }

        // local map for easy ordering
        Map<Config.Feat, Class<? extends Module>> mappings = new HashMap<>(moduleMappings);

        // local set for easy order manipulation
        Set<Config.Feature> features = new HashSet<>(config.getFeatures());

        // standard required internal features
        modules.add(instantiateModule(mappings.remove(Config.InternalFeature.Requests)));
        modules.add(instantiateModule(mappings.remove(Config.InternalFeature.DeviceId)));

        if (config.getLoggingLevel() != Config.LoggingLevel.OFF) {
            modules.add(instantiateModule(mappings.remove(Config.InternalFeature.Logs)));
        }

        // make sure ModuleSessions goes first
        if (features.contains(Config.Feature.AutoSessionTracking)) {
            features.remove(Config.Feature.AutoSessionTracking);
            modules.add(instantiateModule(mappings.remove(Config.Feature.AutoSessionTracking)));
        }

        // dummy module for tests if any
        if (testDummyModule != null) {
            modules.add(testDummyModule);
        }

        // now adding all other features irrespective of order
        for (Config.Feature f : features) {
            Class<? extends Module> cls = mappings.get(f);
            if (cls == null) {
                L.wtf("No module class for feature " + f);
            } else {
                modules.add(instantiateModule(mappings.get(f)));
            }
        }

        // in case we couldn't instantiate some module, there will be null
        modules.remove(null);
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
     * Return module instance by {@link Config.Feature}
     *
     * @param feature to get a {@link Module} instance for
     * @return {@link Module} instance or null if no such module is instantiated
     */
    protected Module module(Config.Feat feature) {
        return module(moduleMappings.get(feature));
    }

    /**
     * Return module instance by {@link Module} class
     *
     * @param cls class to get a {@link Module} instance for
     * @return {@link Module} instance or null if no such module is instantiated
     */
    @SuppressWarnings("unchecked")
    protected <T extends Module> T module(Class<T> cls) {
        for (Module module: modules) {
            if (module.getClass().isAssignableFrom(cls)) {
                return (T) module;
            }
        }
        return null;
    }

    interface ModuleCallback {
        void call(Module module);
    }
}
