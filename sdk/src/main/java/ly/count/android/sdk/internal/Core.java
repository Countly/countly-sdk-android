package ly.count.android.sdk.internal;

import android.app.Activity;
import android.app.Application;
import android.content.*;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.nio.channels.FileLock;
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
     * TODO: check service case and change to Application if possible
     */
    private Context longLivingContext;

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
    private InternalConfig config;

    /**
     * Core instance list of sessions created
     */
    private final List<SessionImpl> sessions = new ArrayList<>();

    /**
     * List of {@link Module} instances built based on {@link #config}
     */
    private final List<Module> modules = new ArrayList<>();

    /**
     * The only Core constructor
     *
     * for some of {@link Module}s required for {@code config}.
     */
    public Core () {
        instance = this;
    }

    /**
     * Init Core instance according to config supplied. In case config is null, Core reads it
     * from storage.
     *
     * @param config Countly configuration
     * @param context Initialization context, can be replaced later
     * @return true if initialized, false if no config found and value in parameter was null
     * @throws IllegalArgumentException in case {@code config} is inconsistent
     */
    public boolean init(Config config, Context context) throws IllegalArgumentException {
        try {
            if (config == null) {
                longLivingContext = context;
                this.config = loadConfig();
                if (this.config == null) {
                    return false;
                }
            } else {
                this.config = config instanceof InternalConfig ? (InternalConfig)config : new InternalConfig(config);
            }
            this.buildModules();

            for (Module module : modules) {
                module.init(this.config);
            }
            return true;
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Create instances of {@link Module}s required by {@link #config}.
     * Uses {@link #moduleMappings} for Feature - Class&lt;Module&gt; mapping to enable
     * overriding by app developer.
     *
     * @throws IllegalArgumentException in case some {@link Module} finds {@link #config} inconsistent.
     * @throws IllegalStateException when this module is run second time on the same {@code Core} instance.
     */
    private void buildModules() throws IllegalArgumentException, IllegalStateException {
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
                    next.begin(session.ended + Math.round(Device.NS_IN_SECOND));
                } else if (next.began < session.ended){
                    next.began = session.ended + Math.round(Device.NS_IN_SECOND);
                }
                if (next.updated != null) {
                    next.updated = null;
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
        ContextImpl context = new ContextImpl(longLivingContext);
        for (Module m : Core.instance.modules) {
            m.onSessionBegan(session, context);
        }
        context.expire();
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
        ContextImpl context = new ContextImpl(longLivingContext);
        for (Module m : Core.instance.modules) {
            m.onSessionEnded(session, context);
        }
        context.expire();
        return session;
    }

    /**
     * Initialization for cases when Countly needs to start up implicitly
     * @param context Context instance to store
     */
    public void onContextCreated(Context context) {
        longLivingContext = context.getApplicationContext();

        Storage.push(this.config);
        sendToService(CountlyService.CMD_START);

        this.config.setLimited(true);
        ContextImpl ctx = new ContextImpl(context);
        for (Module m : modules) {
            m.onContextAcquired(ctx);
        }
        ctx.expire();
    }

    public static void sendToService(int code) {
        Intent intent = new Intent(instance.longLivingContext, CountlyService.class);
        intent.putExtra(CountlyService.CMD, code);
        instance.longLivingContext.startService(intent);
    }

    interface ModuleCallback {
        void call(Module module);
    }

    /**
     * Call the callback for each module initialized
     *
     * @see ModuleCallback
     * @param callback callback to call
     */
    void withEachModule(ModuleCallback callback) {
        this.config.setLimited(true);
        ContextImpl ctx = new ContextImpl(longLivingContext);
        for (Module m : modules) {
            callback.call(m);
        }
        ctx.expire();
    }

    /**
     * Lifecycle methods. For Android versions later or equal to Ice Cream Sandwich, developer
     * doesn't need to call those from each activity. In case app supports earlier version,
     * it's developer responsibility. In any case, for API 14+ Countly ignores dev calls.
     */
    public void onApplicationCreated(Application application) {
        longLivingContext = application.getApplicationContext();

        Storage.push(this.config);
        sendToService(CountlyService.CMD_START);

        this.config.setLimited(false);

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
            m.onContextAcquired(context);
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

    private static final String FILE_NAME_PREFIX = "[CLY]";
    private static final String FILE_NAME_SEPARATOR = "_";

    private static String getName(String ...names) {
        if (names == null || names.length == 0 || Utils.isEmpty(names[0])) {
            return FILE_NAME_PREFIX;
        } else {
            String prefix = FILE_NAME_PREFIX;
            for (String name : names) {
                prefix += FILE_NAME_SEPARATOR + name;
            }
            return prefix;
        }
    }

    private static String extractName(String filename, String prefix) {
        if (filename.indexOf(prefix) == 0) {
            return filename.substring(prefix.length());
        } else {
            return null;
        }
    }

    int purgeInternalStorage(String prefix) {
        return purgeInternalStorage(instance.longLivingContext, prefix);
    }

    static int purgeInternalStorage(Context context, String prefix) {
        prefix = getName(prefix) + FILE_NAME_SEPARATOR;

        int deleted = 0;

        String[] files = context.fileList();
        for (String file : files) {
            if (file.startsWith(prefix)) {
                if (context.deleteFile(file)) {
                    deleted++;
                }
            }
        }

        return deleted;
    }

    List<String> listDataInInternalStorage(String prefix, int slice) {
        return listDataInInternalStorage(instance.longLivingContext, prefix, slice);
    }

    static List<String> listDataInInternalStorage(Context context, String prefix, int slice) {
        prefix = getName(prefix) + FILE_NAME_SEPARATOR;

        List<String> list = new ArrayList<>();
        String[] files = context.fileList();

        int max = slice == 0 ? Integer.MAX_VALUE : Math.abs(slice);
        for (int i = 0; i < files.length; i++) {
            int idx = slice >= 0 ? i : files.length - 1 - i;
            String file = files[idx];
            if (file.startsWith(prefix)) {
                list.add(file.substring(prefix.length()));
                if (list.size() >= max) {
                    break;
                }
            }
        }
        return list;
    }

    boolean pushDataToInternalStorage(String prefix, String name, byte[] data) {
        return pushDataToInternalStorage(instance.longLivingContext, prefix, name, data);
    }

    static boolean pushDataToInternalStorage(Context context, String prefix, String name, byte[] data) {
        String filename = getName(prefix, name);

        FileOutputStream stream = null;
        FileLock lock = null;
        try {
            stream = context.openFileOutput(filename, Context.MODE_PRIVATE);
            lock = stream.getChannel().tryLock();
            if (lock == null) {
                return false;
            }
            stream.write(data);
            stream.close();
            return true;
        } catch (IOException e) {
            System.out.println(e);
            Log.wtf("Cannot write data to " + name, e);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    Log.wtf("Couldn't close output stream for " + name, e);
                }
            }
            if (lock != null && lock.isValid()) {
                try {
                    lock.release();
                } catch (IOException e) {
                    Log.wtf("Couldn't release lock for " + name, e);
                }
            }
        }
        return false;
    }

    boolean removeDataFromInternalStorage(String prefix, String name) {
        return removeDataFromInternalStorage(instance.longLivingContext, prefix, name);
    }

    static boolean removeDataFromInternalStorage(Context context, String prefix, String name) {
        return context.deleteFile(getName(prefix, name));
    }

    byte[] popDataFromInternalStorage(String prefix, String name) {
        return popDataFromInternalStorage(instance.longLivingContext, prefix, name);
    }

    static byte[] popDataFromInternalStorage(Context context, String prefix, String name) {
        byte[] data = readDataFromInternalStorage(context, prefix, name);
        if (data != null) {
            context.deleteFile(getName(prefix, name));
        }
        return data;
    }

    byte[] readDataFromInternalStorage(String prefix, String name) {
        return readDataFromInternalStorage(instance.longLivingContext, prefix, name);
    }

    static byte[] readDataFromInternalStorage(Context context, String prefix, String name) {
        String filename = getName(prefix, name);

        ByteArrayOutputStream buffer = null;
        FileInputStream stream = null;

        try {
            buffer = new ByteArrayOutputStream();
            stream = context.openFileInput(filename);

            int read;
            byte data[] = new byte[4096];
            while((read = stream.read(data, 0, data.length)) != -1){
                buffer.write(data, 0, read);
            }

            stream.close();

            data = buffer.toByteArray();

            return data;

        } catch (FileNotFoundException e) {
            return null;
        } catch (IOException e) {
            Log.wtf("Error while reading file " + name, e);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    Log.wtf("Couldn't close input stream for " + name, e);
                }
            }
            if (buffer != null) {
                try {
                    buffer.close();
                } catch (IOException e) {
                    Log.wtf("Cannot happen", e);
                }
            }
        }

        return null;
    }

    Object[] readOneFromInternalStorage(String prefix, boolean asc) {
        return readOneFromInternalStorage(instance.longLivingContext, prefix, asc);

    }

    static Object[] readOneFromInternalStorage(Context context, String prefix, boolean asc) {
        String start = getName(prefix);
        String fileStart = start + FILE_NAME_SEPARATOR;

        String[] files = context.fileList();

        for (int i = 0; i < files.length; i++) {
            int idx = asc ? i : files.length - 1 - i;
            String file = files[idx];
            if (file.startsWith(fileStart)) {
                Object[] arr = new Object[2];
                arr[0] = extractName(file, fileStart);
                arr[1] = readDataFromInternalStorage(context, prefix, extractName(file, fileStart));
                return arr;
            }
        }

        return null;
    }

    private InternalConfig loadConfig() {
        try {
            return Storage.read(new InternalConfig());
        } catch (MalformedURLException e) {
            Log.wtf("Cannot happen");
            return null;
        }
    }

//    <service android:name=".MyInstanceIDService" android:exported="false">
//    <intent-filter>
//    <action android:name="com.google.android.gms.iid.InstanceID"/>
//    </intent-filter>
//    </service>
//    public static class MyInstanceIDService extends InstanceIDListenerService {
//        public void onTokenRefresh() {
//            refreshAllTokens();
//        }
//
//        private void refreshAllTokens() {
//            // assuming you have defined TokenList as
//            // some generalized store for your tokens
//            ArrayList<TokenList> tokenList = TokensList.get();
//            InstanceID iid = InstanceID.getInstance(this);
//            for(tokenItem : tokenList) {
//                  ModuleDeviceId.onRefresh
//                tokenItem.token =
//                        iid.getToken(tokenItem.authorizedEntity,tokenItem.scope,tokenItem.options);
//                // send this tokenItem.token to your server
//            }
//        }
//    };
}
