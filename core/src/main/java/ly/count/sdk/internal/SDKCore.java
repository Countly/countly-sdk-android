package ly.count.sdk.internal;

import org.json.JSONObject;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Future;

import ly.count.sdk.Config;

public abstract class SDKCore extends SDKModules {
    private static final Log.Module L = Log.module("SDKCore");

    static SDKCore instance;

    private UserImpl user;
    private InternalConfig config;

    public enum Signal {
        DID(1),
        Crash(2),
        Ping(3),
        Start(10);

        private final int index;

        Signal(int index){ this.index = index; }

        public int getIndex(){ return index; }
    }

    protected SDKCore(InternalConfig config) {
        this.config = config;
        this.modules = new TreeMap<>();
        instance = this;
    }

    @Override
    public UserImpl user() {
        return user;
    }

    @Override
    public InternalConfig config() {
        return config;
    }

    @Override
    public void onCrash(Ctx ctx, Throwable t, boolean fatal, String name, Map<String, String> segments, String[] logs) {

    }

    @Override
    public void onUserChanged(Ctx ctx, JSONObject changes, Set<String> cohortsAdded, Set<String> cohortsRemoved) {

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
}
