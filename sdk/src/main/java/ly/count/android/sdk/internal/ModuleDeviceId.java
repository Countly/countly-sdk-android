package ly.count.android.sdk.internal;

import java.util.concurrent.Future;

import ly.count.android.sdk.Config;

/**
 * Main device id manipulation class.
 * Contract:
 * <ul>
 *     <li>Must be device id strategy agnostic.</li>
 *     <li>Must give developer an ability to override any id.</li>
 *     <li>Must be able to manage different branches of ids: at least Countly device id & push device token.</li>
 * </ul>
 */
public class ModuleDeviceId extends ModuleBase {
    private static final Log.Module L = Log.module("ModuleDeviceId");

    static final String ADVERTISING_ID_CLIENT_CLASS_NAME = "com.google.android.gms.ads.identifier.AdvertisingIdClient";
    static final String INSTANCE_ID_CLASS_NAME = "com.google.firebase.iid.FirebaseInstanceId";

    /**
     * InternalConfig instance for later use
     */
    private InternalConfig config;

    /**
     * Tasks instance for async execution
     */
    private Tasks tasks;

    @Override
    public void init(InternalConfig config) throws IllegalArgumentException {
        super.init(config);
        this.config = config;
        if (config.getDeviceIdStrategy() == Config.DeviceIdStrategy.ADVERTISING_ID && !Utils.reflectiveClassExists(ADVERTISING_ID_CLIENT_CLASS_NAME) && !config.isDeviceIdFallbackAllowed()) {
            throw new IllegalArgumentException("Cannot use ADVERTISING_ID device id strategy since there is no " + ADVERTISING_ID_CLIENT_CLASS_NAME + " in class path while device id fallback is not allowed");
        }
        if (config.getDeviceIdStrategy() == Config.DeviceIdStrategy.INSTANCE_ID && !Utils.reflectiveClassExists(INSTANCE_ID_CLASS_NAME) && !config.isDeviceIdFallbackAllowed()) {
            throw new IllegalArgumentException("Cannot use INSTANCE_ID device id strategy since there is no " + INSTANCE_ID_CLASS_NAME + " in class path while device id fallback is not allowed");
        }
    }

    /**
     * Regular logic of acquiring id of specified strategy and migration from legacy SDK.
     *
     * @param ctx Context
     */
    @Override
    public void onContextAcquired(final Context ctx) {
        if (config.getDeviceId() == null) {
            // either fresh install, or migration from legacy SDK

            String legacyDeviceId = Legacy.getOnce(ctx, Legacy.KEY_ID_ID);

            if (Utils.isEmpty(legacyDeviceId)) {
                // fresh install = no legacy id
                L.i("Acquiring device id");

                if (Utils.isNotEmpty(config.getCustomDeviceId())) {
                    // developer specified id on SDK init
                    Config.DID did = new Config.DID(Config.DeviceIdRealm.DEVICE_ID, Config.DeviceIdStrategy.CUSTOM_ID, config.getCustomDeviceId());
                    L.d("Got developer id" + did);
                    Core.onDeviceId(ctx, did, null);
                } else {
                    // regular flow - acquire id using specified strategy
                    acquireId(ctx, new Config.DID(Config.DeviceIdRealm.DEVICE_ID, config.getDeviceIdStrategy(), null), config.isDeviceIdFallbackAllowed(), new Tasks.Callback<Config.DID>() {
                        @Override
                        public void call(Config.DID id) throws Exception {
                            if (id != null) {
                                L.d("Got device id: " + id);
                                Core.onDeviceId(ctx, id, null);
                            } else {
                                L.i("No device id of strategy " + config.getDeviceIdStrategy() + " is available yet");
                            }
                        }
                    });
                }
            } else {
                L.d("Migrating device id " + legacyDeviceId);

                Config.DID did = new Config.DID(Config.DeviceIdRealm.DEVICE_ID, config.getDeviceIdStrategy(), legacyDeviceId);
                Core.onDeviceId(ctx, did, did);
            }
        } else {
            // second or next app launch, notify id is available
            Core.onDeviceId(ctx, config.getDeviceId(), config.getDeviceId());
        }
    }

    /**
     * Logging into app-specific account:
     * - reset device id and notify modules;
     * - send corresponding request to server.
     *
     * @param ctx ctx to run in
     * @param id device id to change to
     */
    public void login(Context ctx, String id) {
        if (Utils.isEmpty(id)) {
            L.wtf("Empty id passed to login method");
        } else {
            final Config.DID old = config.getDeviceId();
            config.setDeviceId(new Config.DID(Config.DeviceIdRealm.DEVICE_ID, Config.DeviceIdStrategy.CUSTOM_ID, id));
            Storage.push(ctx, config);

            if (old != null) {
                // have acquired an id already
//                ModuleRequests.sessionBegin()
            }

            // old session end & new session begin requests are supposed to happen here
            Core.onDeviceId(ctx, config.getDeviceId(), old);
        }
    }

    /**
     * Logging out from app-specific account and reverting back to previously used id if any:
     * - nullify device id and notify modules;
     * - send corresponding request to server.
     *
     * @param ctx context to run in
     */
    public void logout(Context ctx) {
        final Config.DID old = config.getDeviceId();
        config.setDeviceId(null);
        Storage.push(ctx, config);
        Core.onDeviceId(ctx, config.getDeviceId(), old);
    }

    Future<Config.DID> acquireId(final Context ctx, final Config.DID holder, final boolean fallbackAllowed, final Tasks.Callback<Config.DID> callback) {
        if (this.tasks == null) {
            this.tasks = new Tasks("deviceId");
        }

        return this.tasks.run(new Tasks.Task<Config.DID>(Tasks.ID_STRICT) {
            @Override
            public Config.DID call() throws Exception {
                return acquireIdSync(ctx, holder, fallbackAllowed);
            }
        }, callback);
    }

    /**
     * Synchronously gets id of the strategy supplied. In case strategy is not available, returns a fallback strategy.
     * In case strategy is available but id cannot be acquired right now, returns null.
     *
     * @param ctx Context to run in
     * @param holder DID object which holds strategy and possibly other info for id generation
     * @param fallbackAllowed whether to automatically fallback to any available alternative or not
     * @return {@link ly.count.android.sdk.Config.DID} instance with an id
     */
    public Config.DID acquireIdSync(final Context ctx, final Config.DID holder, final boolean fallbackAllowed) {

        switch (holder.strategy) {
            case OPEN_UDID:
                // Courtesy OpenUDID https://github.com/vieux/OpenUDID
                L.i("Generating OPEN_UDID");

                String id = Core.generateOpenUDID(ctx);

                return new Config.DID(holder.realm, Config.DeviceIdStrategy.OPEN_UDID, id);

            case ADVERTISING_ID:
                if (Utils.reflectiveClassExists(ADVERTISING_ID_CLIENT_CLASS_NAME)) {
                    L.i("Generating ADVERTISING_ID");
                    try {
                        Object info = Utils.reflectiveCallStrict(ADVERTISING_ID_CLIENT_CLASS_NAME, null, "getAdvertisingIdInfo", android.content.Context.class, ctx.getContext());
                        L.i("Got ADVERTISING_ID info");
                        if (info == null || info == Boolean.FALSE) {
                            L.d("AdvertisingIdClient.getAdvertisingIdInfo() returned " + info);
                            return fallbackAllowed ? acquireIdSync(ctx, new Config.DID(holder.realm, Config.DeviceIdStrategy.OPEN_UDID, null), true) : null;
                        } else {
                            L.d("calling getId");
                            Object idObj = Utils.reflectiveCall(info.getClass().getCanonicalName(), info, "getId");
                            L.d("AdvertisingIdClient.getAdvertisingIdInfo().getId() returned " + idObj);
                            if (idObj == null || !(idObj instanceof String)) {
                                return fallbackAllowed ? acquireIdSync(ctx, new Config.DID(holder.realm, Config.DeviceIdStrategy.OPEN_UDID, null), true) : null;
                            } else {
                                return new Config.DID(holder.realm, Config.DeviceIdStrategy.ADVERTISING_ID, (String) idObj);
                            }
                        }
                    } catch (Throwable t) {
                        if (t.getCause() != null && (t.getCause().getClass().toString().contains("GooglePlayServicesAvailabilityException")
                                || t.getCause().getClass().toString().contains("GooglePlayServicesRepairableException"))) {
                            // recoverable, let device ID be null, which will result in storing all requests locally
                            // and rerunning them whenever Advertising ID becomes available
                            L.i("Advertising ID cannot be determined yet");
                            return null;
                        } else if (t.getCause() != null && t.getCause().getClass().toString().contains("GooglePlayServicesNotAvailableException")) {
                            // non-recoverable, fallback to OpenUDID
                            L.i("Advertising ID cannot be determined because Play Services are not available");
                            return fallbackAllowed ? acquireIdSync(ctx, new Config.DID(holder.realm, Config.DeviceIdStrategy.OPEN_UDID, null), true) : null;
                        } else {
                            // unexpected
                            L.w("Couldn't get advertising ID", t);
                            return fallbackAllowed ? acquireIdSync(ctx, new Config.DID(holder.realm, Config.DeviceIdStrategy.OPEN_UDID, null), true) : null;
                        }
                    }
                }  else {
                    L.i("ADVERTISING_ID is not available " + (fallbackAllowed ? ", checking OPEN_UDID" : "fallback is not allowed"));
                    return fallbackAllowed ? acquireIdSync(ctx, new Config.DID(holder.realm, Config.DeviceIdStrategy.OPEN_UDID, null), true) : null;
                }

            case INSTANCE_ID:
                if (Utils.reflectiveClassExists(INSTANCE_ID_CLASS_NAME)) {
                    L.i("Generating INSTANCE_ID");
                    try {
                        Object instance = Utils.reflectiveCall(INSTANCE_ID_CLASS_NAME, null, "getInstance");
                        if (instance == null || instance == Boolean.FALSE) {
                            L.d("InstanceId.getInstance() returned " + instance);
                            return fallbackAllowed ? acquireIdSync(ctx, new Config.DID(holder.realm, Config.DeviceIdStrategy.OPEN_UDID, null), true) : null;
                        } else {
                            Object idObj;
                            if (holder.realm == Config.DeviceIdRealm.DEVICE_ID) {
                                idObj = Utils.reflectiveCall(instance.getClass().getName(), instance, "getId");
                            } else {
                                idObj = Utils.reflectiveCall(instance.getClass().getName(), instance, "getToken");
                            }
                            if (idObj == null || !(idObj instanceof String)) {
                                L.d("InstanceId.getInstance().getId() returned " + idObj);
                                return fallbackAllowed ? acquireIdSync(ctx, new Config.DID(holder.realm, Config.DeviceIdStrategy.OPEN_UDID, null), true) : null;
                            } else {
                                return new Config.DID(holder.realm, Config.DeviceIdStrategy.INSTANCE_ID, (String) idObj);
                            }
                        }
                    } catch (Throwable t) {
                        if (t.getCause() != null && t.getCause().getClass().toString().contains("GooglePlayServicesAvailabilityException")) {
                            // recoverable, let device ID be null, which will result in storing all requests locally
                            // and rerunning them whenever Advertising ID becomes available
                            L.i("Advertising ID cannot be determined yet");
                            return null;
                        } else if (t.getCause() != null && t.getCause().getClass().toString().contains("GooglePlayServicesNotAvailableException")) {
                            // non-recoverable, fallback to OpenUDID
                            L.i("Advertising ID cannot be determined because Play Services are not available");
                            return fallbackAllowed ? acquireIdSync(ctx, new Config.DID(holder.realm, Config.DeviceIdStrategy.OPEN_UDID, null), true) : null;
                        } else {
                            // unexpected
                            L.w("Couldn't get advertising ID", t);
                            return fallbackAllowed ? acquireIdSync(ctx, new Config.DID(holder.realm, Config.DeviceIdStrategy.OPEN_UDID, null), true) : null;
                        }
                    }
                }  else {
                    L.i("INSTANCE_ID is not available " + (fallbackAllowed ? ", checking OPEN_UDID" : "fallback is not allowed"));
                    return fallbackAllowed ? acquireIdSync(ctx, new Config.DID(holder.realm, Config.DeviceIdStrategy.OPEN_UDID, null), true) : null;
                }

        }

        return null;
    }

    public static class AdvIdInfo {
        public static String deviceId;
        public String getId() { return deviceId; }
    }

    public static class InstIdInstance {
        public static String deviceId;
        public String getId() { return deviceId; }
        public String getToken() { return deviceId; }
    }
}
