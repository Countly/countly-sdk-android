package ly.count.android.sdk.internal;

import android.annotation.SuppressLint;
import android.app.Service;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.provider.Settings;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import ly.count.android.sdk.Config;
import ly.count.android.sdk.CountlyPush;

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
public class Core extends CoreModules {
    private static final Log.Module L = Log.module("Core");

    /**
     * Handler for main thread
     */
    static Handler handler = null;

    /**
     * Current user profile
     */
    private UserImpl user;

    /**
     * Core instance list of sessions created
     */
    private final List<SessionImpl> sessions = new ArrayList<>();

    /**
     * The only Core constructor
     *
     * for some of {@link Module}s required for {@code config}.
     */
    public Core () {
        instance = this;
    }

    static void deinit () {
        Log.deinit();
        instance = null;
    }

    public static Core init(Config config, android.app.Application application) throws IllegalArgumentException {
        return init(config, application, Utils.contains(Device.getProcessName(application), ":countly"));
    }

    /**
     * Init Core instance according to config supplied. In case config is null, Core reads it
     * from storage.
     *
     * @param config Countly configuration
     * @param application Initialization context, can be replaced later
     * @param limited whether to init in passive mode (we're in 2-nd {@link CountlyService} process)
     * @return true if initialized, false if no config found and value in parameter was null
     * @throws IllegalArgumentException in case {@code config} is inconsistent
     * @throws IllegalStateException in case {@code config} is inconsistent
     */
    public static Core init(Config config, android.app.Application application, boolean limited) throws IllegalArgumentException {
        if (instance == null) {
            try {
                instance = new Core();
                Context ctx = new ContextImpl(application);
                if (handler == null) {
                    handler = new Handler(application.getMainLooper());
                }
                instance.config = instance.loadConfig(ctx);
                if (instance.config == null) {
                    if (config != null) {
                        instance.config = config instanceof InternalConfig ? (InternalConfig)config : new InternalConfig(config);
                    } else {
                        instance = null;
                        return null;
                    }
                } else if (config != null) {
                    instance.config.setFrom(config);
                }
                instance.config.setLimited(limited);
                instance.buildModules();

                List<Module> failed = new ArrayList<>();
                for (Module module : instance.modules) {
                    try {
                        module.init(instance.config);
                        Utils.reflectiveSetField(module, "active", true);
                    } catch (IllegalArgumentException | IllegalStateException e) {
                        if (instance.config.isTestModeEnabled()) {
                            throw e;
                        } else {
                            failed.add(module);
                        }
                    }
                }
                instance.modules.removeAll(failed);

                instance.user = instance.loadUser(ctx);
                if (instance.user == null) {
                    instance.user = new UserImpl(ctx);
                }

                if (instance.config.isLimited()) {
                    instance.onLimitedContextAcquired(application);
                } else {
                    instance.onContextAcquired(application);
                }

                return instance;
            } catch (MalformedURLException e) {
                throw new IllegalStateException(e);
            }
        }
        return instance;
    }

    /**
     * Check whether Core is initialized already
     * @return config instance if initialized, null otherwise
     */
    static InternalConfig initialized() {
        return instance == null ? null : instance.config;
    }

    /**
     * Stop SDK and possibly clear all SDK data.
     * Once stopped, Countly must be reinitialized using {@link #init(Config, android.content.Context)} again in order
     * to be used.
     *
     * @param context Context to run in
     * @param clear whether to clear SDK data or not
     */
    public void stop(android.content.Context context, boolean clear) {
        L.i("Stopping Countly SDK" + (clear ? " and clearing all data" : ""));

        ContextImpl ctx = new ContextImpl(context);

        for (Module module : modules) {
            try {
                module.stop(ctx, clear);
                Utils.reflectiveSetField(module, "active", false);
            } catch (Throwable e) {
                L.wtf("Exception while stopping " + module.getClass(), e);
            }
        }
        modules.clear();

        if (sessions.size() > 0 && !clear) {
            for (SessionImpl session : sessions) {
                session.end();
            }
        }
        sessions.clear();

        Storage.await();

        if (clear) {
            purgeInternalStorage(ctx, null);
        }

        ctx.expire();

        handler = null;
        user = null;

        deinit();
    }

    /**
     * Current user profile
     *
     * @return user instance
     */
    public UserImpl user() {
        return user;
    }

    /**
     * Add session to the list.
     * @return {@link SessionImpl} just created
     */
    SessionImpl sessionAdd(Context ctx){
        sessions.add(new SessionImpl(ctx));
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
                } else if (next.began < session.ended) {
                    L.w("Sessions are overlapping. Next session starts before previous one ended.");
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
    public SessionImpl sessionLeading(){
        return sessions.size() > 0 ? sessions.get(0) : null;
    }

    /**
     * Current leading {@link ly.count.android.sdk.Session} or new {@link ly.count.android.sdk.Session}
     * if no leading one exists.
     *
     * @return leading or new session object
     */
    public SessionImpl sessionLeadingOrNew(Context ctx){
        return sessions.size() > 0 ? sessions.get(0) : sessionAdd(ctx);
    }

    /**
     * Current leading {@link ly.count.android.sdk.Session} or new {@link ly.count.android.sdk.Session}
     * if no leading one exists. Android context version.
     *
     * @return leading or new session object
     */
    public SessionImpl sessionLeadingOrNew(android.content.Context context){
        return sessions.size() > 0 ? sessions.get(0) : sessionAdd(new ContextImpl(context.getApplicationContext()));
    }

    /**
     * Notify all {@link Module} instances about new session has just been started
     *
     * @param session session to begin
     * @return supplied session for method chaining
     */
    SessionImpl onSessionBegan(Context ctx, SessionImpl session){
        for (Module m : instance.modules) {
            m.onSessionBegan(session, ctx);
        }
        return session;
    }

    /**
     * Notify all {@link Module} instances session was ended
     *
     * @param session session to end
     * @return supplied session for method chaining
     */
    SessionImpl onSessionEnded(Context ctx, SessionImpl session){
        for (Module m : instance.modules) {
            m.onSessionEnded(session, ctx);
        }
        return session;
    }

    /**
     * Initialization for cases when Countly needs to start up implicitly
     * @param context Context to run in
     */
    private void onLimitedContextAcquired(android.content.Context context) {
        ContextImpl ctx = new ContextImpl(context);

        if (!(context instanceof CountlyService)) {
            sendToService(ctx, CountlyService.CMD_START, null);
        }

        this.config.setLimited(true);
        for (Module m : modules) {
            m.onLimitedContextAcquired(ctx);
        }
    }

    /**
     * Notify modules about {@link ly.count.android.sdk.Config.DID} change: adding, change or removal
     * and store {@link InternalConfig} changes if needed
     *
     * @param ctx Context to run in
     * @param id new id of specified {@link ly.count.android.sdk.Config.DID#realm} or null if removing it
     * @param old old id of specified {@link ly.count.android.sdk.Config.DID#realm} or null if there were no id with such realm
     */
    public static void onDeviceId(Context ctx, Config.DID id, Config.DID old) {
        L.d((instance == null || instance.config == null ? "null" : instance.config.isLimited() ? "limited" : "non-limited") + " onDeviceId " + id + ", old " + old);
        if (instance == null || instance.config == null) {
            L.wtf("SDK not initialized when setting device id");
            return;
        }
        if (instance.config.isLimited()) {
            if (id != null && (!id.equals(old) || !id.equals(instance.config.getDeviceId(id.realm)))) {
                instance.config.setDeviceId(id);
                L.d("0");
            } else if (id == null && old != null) {
                instance.config.removeDeviceId(old);
                L.d("1");
            }
        } else {
            if (id != null && (!id.equals(old) || !id.equals(instance.config.getDeviceId(id.realm)))) {
                instance.config.setDeviceId(id);
                Storage.push(ctx, instance.config);
                L.d("2");
            } else if (id == null && old != null) {
                L.d("3");
                if (instance.config.removeDeviceId(old)) {
                    Storage.push(ctx, instance.config);
                    L.d("4");
                }
            }
        }

        for (Module module : instance.modules) {
            module.onDeviceId(ctx, id, old);
        }

        if (instance.config.isLimited()) {
            instance.config = instance.loadConfig(ctx);
            if (instance.config == null) {
                L.wtf("Config reload gave null instance");
            } else {
                instance.config.setLimited(true);
            }
            instance.user = instance.loadUser(ctx);
        }

        if (!instance.config.isLimited() && id != null && id.realm == Config.DeviceIdRealm.DEVICE_ID) {
            instance.user.id = id.id;
            L.d("5");
        }
    }

    public static String generateOpenUDID(Context ctx) {
        @SuppressLint("HardwareIds")
        String id = Settings.Secure.getString(ctx.getContext().getContentResolver(), Settings.Secure.ANDROID_ID);

        // if ANDROID_ID is null, or it's equals to the GalaxyTab generic ANDROID_ID or bad, generates a new one
        if (id == null || id.equals("9774d56d682e549c") || id.length() < 15) {
            final SecureRandom random = new SecureRandom();
            id = new BigInteger(64, random).toString(16);
        }

        return id;
    }

    private InternalConfig loadConfig(Context ctx) {
        try {
            return Storage.read(ctx, new InternalConfig());
        } catch (MalformedURLException e) {
            L.wtf("Cannot happen");
            return null;
        }
    }

    private UserImpl loadUser(Context ctx) {
        return Storage.read(ctx, new UserImpl(ctx));
    }

    // ------------------------ Specific module-related methods ------------------------------------

    /**
     * Acquire a specific {@link ly.count.android.sdk.Config.DID} for parameters
     * specified in {@code holder} ({@link ly.count.android.sdk.Config.DID#strategy},
     * {@link ly.count.android.sdk.Config.DID#realm}).
     *
     * @param holder {@link ly.count.android.sdk.Config.DID} instance with parameters
     * @param fallbackAllowed whether fallback to other {@link ly.count.android.sdk.Config.DID#strategy} is allowed or not
     * @param callback callback to run (in {@link ModuleDeviceId} thread) when done, can be null
     * @return Future which resolves to {@link ly.count.android.sdk.Config.DID} instance if succeeded or to null if not
     */
    Future<Config.DID> acquireId(Context ctx, final Config.DID holder, final boolean fallbackAllowed, final Tasks.Callback<Config.DID> callback) {
        return ((ModuleDeviceId)module(ModuleDeviceId.class)).acquireId(ctx, holder, fallbackAllowed, callback);
    }

    public void login(android.content.Context context, String id) {
        if (instance == null) {
            Log.wtf("Countly is not initialized");
        } else {
            Context ctx = new ContextImpl(context);
            instance.module(ModuleDeviceId.class).login(ctx, id);
        }
    }

    public void logout(android.content.Context context) {
        if (instance == null) {
            Log.wtf("Countly is not initialized");
        } else {
            Context ctx = new ContextImpl(context);
            instance.module(ModuleDeviceId.class).logout(ctx);
        }
    }

    /**
     * In case when {@link Request} is owned by some {@link Module}, checks whether the {@link Module}
     * is ready to send it or not.
     *
     * @param request request to check
     * @return {@code true} if ready to send, {@code false} if won't ever be ready, {@code null} if not decided yet
     */
    Boolean isRequestReady(Request request) {
        Class<? extends Module> cls = request.owner();
        if (cls == null) {
            return true;
        } else {
            Module module = module(cls);
            request.params.remove(Request.MODULE);
            if (module == null) {
                return true;
            } else {
                return module.onRequest(request);
            }
        }
    }

//    /**
//     * {@link CountlyPush#displayMessage(Service, Map)} protected logic
//     *
//     * @param service originating {@link Service}
//     * @param data map with message data
//     * @return {@code true} if message handled, {@code false} otherwise
//     */
//    public static boolean displayMessage(Service service, Map<String, String> data) {
//        InternalConfig config = Core.initForService(service);
//        if (config != null) {
//            ModulePush push = (ModulePush) instance.module(ModulePush.class);
//            if (push == null) {
//                L.wtf("No ModulePush found, won't process message");
//            } else {
//                return push.displayMessage(data);
//            }
//        } else {
//            L.wtf("No config found, won't process message");
//        }
//        return false;
//    }
//

    /**
     * Record crash report and send it to the server.
     *
     * @param ctx Context to run in
     * @param throwable Throwable to record
     * @param fatal {@code true} if Exception was bad (crashing app or some fatal malfunction), {@code false} otherwise
     * @param details optional details string
     */
    public static void onCrash(Context ctx, Throwable throwable, boolean fatal, String name, String details) {
        ModuleCrash module = (ModuleCrash) Core.instance.module(Config.Feature.Crash);
        if (module != null) {
            module.onCrash(ctx, throwable, fatal, name, details);
        }
    }

    /**
     * {@link CountlyPush#onTokenRefresh(Service, String)} protected logic
     *
     * @param service originating {@link Service}
     * @param token token string
     */
    public static void onPushTokenRefresh(Service service, String token) {
        L.d("onPushTokenRefresh " + token);
        if (instance == null) {
            L.wtf("Not initialized prior to onPushTokenRefresh");
            return;
        }
        Context ctx = new ContextImpl(service);
        if (instance.config != null && !instance.config.isLimited()) {
            if (Utils.isNotEmpty(token)) {
                Core.onDeviceId(ctx, new Config.DID(Config.DeviceIdRealm.FCM_TOKEN, Config.DeviceIdStrategy.INSTANCE_ID, token), instance.config.getDeviceId(Config.DeviceIdRealm.FCM_TOKEN));
            } else {
                Core.onDeviceId(ctx, null, instance.config.getDeviceId(Config.DeviceIdRealm.FCM_TOKEN));
            }
        }
    }

    /**
     * {@link CountlyPush#decodeMessage(Map)} protected logic
     *
     * @param data map with message data
     * @return {@link ly.count.android.sdk.CountlyPush.Message} object if {@code data} contains Countly message, {@code null} otherwise
     */
    public static CountlyPush.Message decodePushMessage(Map<String, String> data) {
        return ModulePush.decodeMessage(data);
    }

    /**
     * Just file download logic wrapped in async thread with callback on main thread.
     * TODO: move to Network
     *
     * @param message {@link ly.count.android.sdk.CountlyPush.Message} object to load media
     * @param callback callback to call after download completed
     */
    public static void downloadMedia(final CountlyPush.Message message, final Tasks.Callback<Bitmap> callback) {
        instance.downloadMediaData(message, callback);
    }

    void downloadMediaData(final CountlyPush.Message message, final Tasks.Callback<Bitmap> callback) {
        final Tasks tasks = new Tasks("download");

        tasks.run(new Tasks.Task<byte[]>(Tasks.ID_STRICT) {
            @Override
            public byte[] call() throws Exception {
                if (message.media() == null) {
                    return null;
                }
                HttpURLConnection connection = null;
                InputStream input = null;
                try {
                    connection = (HttpURLConnection) message.media().openConnection();
                    connection.setDoInput(true);
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(15000);
                    connection.connect();
                    input = connection.getInputStream();
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    byte[] buf = new byte[16384];
                    int read;
                    while ((read = input.read(buf, 0, buf.length)) != -1) {
                        bytes.write(buf, 0, read);
                    }
                    bytes.flush();
                    return bytes.toByteArray();
                } catch (Exception e) {
                    L.e("Cannot download message media", e);
                    return null;
                } finally {
                    if (input != null) {
                        try {
                            input.close();
                        } catch (IOException ignored) {}
                    }
                    if (connection != null) {
                        try {
                            connection.disconnect();
                        } catch (Throwable ignored) {}
                    }
                }
            }
        }, new Tasks.Callback<byte[]>() {
            @Override
            public void call(final byte[] param) throws Exception {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (param == null) {
                                callback.call(null);
                            } else {
                                callback.call(BitmapFactory.decodeByteArray(param, 0, param.length));
                            }
                            tasks.shutdown();
                        } catch (Exception e) {
                            L.e("Exception in message media download callback", e);
                        }
                    }
                });
            }
        });
    }

}
