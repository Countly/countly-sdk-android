package ly.count.android.sdk.internal;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ly.count.android.sdk.Config;

/**
 * {@link Module}-related methods of {@link Core}
 */

public class CoreModules extends CoreStorage {
    /**
     * Mappings of {@link Config.Feature} to {@link Module} class.
     * Changed by using {@link #setModuleMapping(Config.Feature, Class)}.
     */
    protected static final Map<Config.Feature, Class<? extends Module>> moduleMappings = new HashMap<>();

    static {
        setModuleMapping(Config.Feature.Attribution, ModuleAttribution.class);
        setModuleMapping(Config.Feature.Push, ModulePush.class);
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
     * Create instances of {@link Module}s required by {@link #config}.
     * Uses {@link #moduleMappings} for Feature - Class&lt;Module&gt; mapping to enable
     * overriding by app developer.
     *
     * @throws IllegalArgumentException in case some {@link Module} finds {@link #config} inconsistent.
     * @throws IllegalStateException when this module is run second time on the same {@code Core} instance.
     */
    protected void buildModules() throws IllegalArgumentException, IllegalStateException {
        if (modules.size() > 0){
            throw new IllegalStateException("Modules can be built only once per InternalConfig instance");
        }

        modules.add(new ModuleRequests());
        modules.add(new ModuleDeviceId());

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
     * Return module instance by {@link Config.Feature}
     *
     * @param feature to get a {@link Module} instance for
     * @return {@link Module} instance or null if no such module is instantiated
     */
    protected Module module(Config.Feature feature) {
        return module(moduleMappings.get(feature));
    }

    /**
     * Return module instance by {@link Module} class
     *
     * @param cls class to get a {@link Module} instance for
     * @return {@link Module} instance or null if no such module is instantiated
     */
    protected Module module(Class<? extends Module> cls) {
        for (Module module: modules) {
            if (module.getClass().isAssignableFrom(cls)) {
                return module;
            }
        }
        return null;
    }

    interface ModuleCallback {
        void call(Module module);
    }
}
