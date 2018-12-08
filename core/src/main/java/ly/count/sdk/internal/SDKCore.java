package ly.count.sdk.internal;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Future;

import ly.count.sdk.Config;

public abstract class SDKCore extends SDKModules {
    private static final Log.Module L = Log.module("SDKCore");

    protected static SDKCore instance;

    private UserImpl user;
    private InternalConfig config;
    protected Networking networking;

    public enum Signal {
        DID(1),
        Crash(2),
        Ping(3),
        Start(10);

        private final int index;

        Signal(int index){ this.index = index; }

        public int getIndex(){ return index; }
    }

    protected SDKCore() {
        this.modules = new TreeMap<>();
        instance = this;
    }

    public void init(final Ctx ctx) {
        L.i("Initializing Countly in " + (ctx.getConfig().isLimited() ? "limited" : "full") + " mode");

        InternalConfig loaded = null;
        try {
            loaded = Storage.read(ctx, new InternalConfig());
        } catch (IllegalArgumentException e) {
            L.wtf("Cannot happen", e);
        }

        if (loaded == null) {
            config = ctx.getConfig();
        } else {
            config = loaded;
            config.setFrom(ctx.getConfig());
        }

        super.init(ctx);

        // ModuleSessions is always enabled, even without consent
        int consents = ctx.getConfig().getFeatures() | CoreFeature.Sessions.getIndex();
        // build modules
        buildModules(ctx, consents);

        final List<Integer> failed = new ArrayList<>();
        eachModule(new Modulator() {
            @Override
            public void run(int feature, Module module) {
                try {
                    module.init(config);
                    Utils.reflectiveSetField(module, "active", true);
                } catch (IllegalArgumentException | IllegalStateException e) {
                    L.e("Error during module initialization", e);
                    if (config.isTestModeEnabled()) {
                        throw e;
                    } else {
                        failed.add(feature);
                    }
                }
            }
        });

        for (Integer feature : failed) {
            modules.remove(feature);
        }

        if (config.isLimited()) {
            onLimitedContextAcquired(ctx);
        } else {
            try {
                user = Storage.read(ctx, new UserImpl(ctx));
                if (user == null) {
                    user = new UserImpl(ctx);
                }
            } catch (Throwable e) {
                L.wtf("Cannot happen", e);
                user = new UserImpl(ctx);
            }

            onContextAcquired(ctx);
        }

        if (config.isDefaultNetworking()) {
            networking = new DefaultNetworking();
            networking.init(ctx);
            networking.check(ctx);
        }
    }

    protected void onLimitedContextAcquired(final Ctx ctx) {
        eachModule(new Modulator() {
            @Override
            public void run(int feature, Module module) {
                module.onLimitedContextAcquired(ctx);
            }
        });
    }

    protected void onContextAcquired(final Ctx ctx) {
        eachModule(new Modulator() {
            @Override
            public void run(int feature, Module module) {
                module.onContextAcquired(ctx);
            }
        });
    }

    public void stop(final Ctx ctx, final boolean clear) {
        if (instance == null) {
            return;
        }

        if (networking != null) {
            networking.stop(ctx);
        }

        L.i("Stopping Countly SDK" + (clear ? " and clearing all data" : ""));
        super.stop(ctx, clear);

        user = null;
        config = null;
        instance = null;
    }


    @Override
    public UserImpl user() {
        return user;
    }

    TimedEvents timedEvents() {
        return ((ModuleSessions)module(CoreFeature.Sessions.getIndex())).timedEvents();
    }

    @Override
    public InternalConfig config() {
        return config;
    }

    @Override
    public void onCrash(Ctx ctx, Throwable t, boolean fatal, String name, Map<String, String> segments, String[] logs) {
        ModuleCrash module = (ModuleCrash) module(CoreFeature.CrashReporting.getIndex());
        if (module != null) {
            module.onCrash(ctx, t, fatal, name, segments, logs);
        }
    }

    @Override
    public void onUserChanged(final Ctx ctx, final JSONObject changes, final Set<String> cohortsAdded, final Set<String> cohortsRemoved) {
        eachModule(new Modulator() {
            @Override
            public void run(int feature, Module module) {
                module.onUserChanged(ctx, changes, cohortsAdded, cohortsRemoved);
            }
        });
    }

    @Override
    public void onDeviceId(Ctx ctx, Config.DID id, Config.DID old) {
        L.d((config.isLimited() ? "limited" : "non-limited") + " onDeviceId " + id + ", old " + old);

        if (config.isLimited()) {
            if (id != null && (!id.equals(old) || !id.equals(config.getDeviceId(id.realm)))) {
                config.setDeviceId(id);
                L.d("0");
            } else if (id == null && old != null) {
                config.removeDeviceId(old);
                L.d("1");
            }
        } else {
            if (id != null && (!id.equals(old) || !id.equals(config.getDeviceId(id.realm)))) {
                config.setDeviceId(id);
                Storage.push(ctx, instance.config);
                L.d("2");
            } else if (id == null && old != null) {
                L.d("3");
                if (config.removeDeviceId(old)) {
                    Storage.push(ctx, config);
                    L.d("4");
                }
            }
        }

        for (Module module : modules.values()) {
            module.onDeviceId(ctx, id, old);
        }

        if (config.isLimited()) {
            config = Storage.read(ctx, new InternalConfig());
            if (config == null) {
                L.wtf("Config reload gave null instance");
            } else {
                config.setLimited(true);
            }
            user = Storage.read(ctx, new UserImpl(ctx));
            if (user == null) {
                user = new UserImpl(ctx);
            }
        }

        if (!config.isLimited() && id != null && id.realm == Config.DID.REALM_DID) {
            user.id = id.id;
            L.d("5");
        }
    }


    public Future<Config.DID> acquireId(final Ctx ctx, final Config.DID holder, final boolean fallbackAllowed, final Tasks.Callback<Config.DID> callback) {
        return ((ModuleDeviceId)module(CoreFeature.DeviceId.getIndex())).acquireId(ctx, holder, fallbackAllowed, callback);
    }

    public void login(Ctx ctx, String id) {
        ((ModuleDeviceId)module(CoreFeature.DeviceId.getIndex())).login(ctx, id);
    }

    public void logout(Ctx ctx) {
        ((ModuleDeviceId)module(CoreFeature.DeviceId.getIndex())).logout(ctx);
    }

    public void resetId(Ctx ctx, String id) {
        ((ModuleDeviceId)module(CoreFeature.DeviceId.getIndex())).resetId(ctx, id);
    }

    public static boolean enabled(int feature) {
        return instance.config.isFeatureEnabled(feature);
    }

    public static boolean enabled(CoreFeature feature) {
        return enabled(feature.getIndex());
    }

    public Boolean isRequestReady(Request request) {
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
}
