package ly.count.sdk.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;

import ly.count.sdk.Config;

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

    /**
     * Tasks instance for async execution
     */
    private Tasks tasks;

    private static final class UUIDGenerator implements DeviceIdGenerator {
        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String generate(Ctx context, int realm) {
            return UUID.randomUUID().toString();
        }
    }

    private static final Map<Integer, DeviceIdGenerator> generators = new HashMap<>();
    static {
        registerGenerator(Config.DID.STRATEGY_UUID, new UUIDGenerator());
    }

    public static void registerGenerator(int index, DeviceIdGenerator generator) {
        generators.put(index, generator);
    }

    @Override
    public void init(InternalConfig config) throws IllegalArgumentException {
        super.init(config);

        DeviceIdGenerator generator = generators.get(config.getDeviceIdStrategy());
        if (generator == null) {
            Log.wtf("Device id strategy " + config.getDeviceIdStrategy() + " is not supported by SDK.");
        } else if (!generator.isAvailable()) {
            String str = "Device id strategy " + config.getDeviceIdStrategy() + " is not available. Make sure corresponding classes are in class path.";
            if (config.isDeviceIdFallbackAllowed()) {
                Log.w(str);
            } else {
                Log.wtf(str);
                return;
            }

            int index = config.getDeviceIdStrategy();
            boolean found = false;
            while (--index > 0) {
                generator = generators.get(index);
                if (generator.isAvailable()) {
                    Log.w("Will fall back to strategy " + index);
                    found = true;
                }
            }
            // UUID is always available though
            if (!found) {
                Log.wtf("No fallback device id generation strategy available, SDK won't function properly");
            }
        }
    }

    /**
     * Regular logic of acquiring id of specified strategy and migration from legacy SDK.
     *
     * @param ctx Ctx
     */
    @Override
    public void onContextAcquired(final Ctx ctx) {
        if (ctx.getConfig().getDeviceId() == null) {
            // either fresh install, or migration from legacy SDK

            L.i("Acquiring device id");

            if (Utils.isNotEmpty(ctx.getConfig().getCustomDeviceId())) {
                // developer specified id on SDK init
                Config.DID did = new Config.DID(Config.DID.REALM_DID, Config.DID.STRATEGY_CUSTOM, ctx.getConfig().getCustomDeviceId());
                L.d("Got developer id" + did);
                SDKCore.instance.onDeviceId(ctx, did, null);
            } else {
                // regular flow - acquire id using specified strategy
                acquireId(ctx, new Config.DID(Config.DID.REALM_DID, ctx.getConfig().getDeviceIdStrategy(), null), ctx.getConfig().isDeviceIdFallbackAllowed(), new Tasks.Callback<Config.DID>() {
                    @Override
                    public void call(Config.DID id) throws Exception {
                        if (id != null) {
                            L.d("Got device id: " + id);
                            SDKCore.instance.onDeviceId(ctx, id, null);
                        } else {
                            L.i("No device id of strategy " + ctx.getConfig().getDeviceIdStrategy() + " is available yet");
                        }
                    }
                });
            }
        } else {
            // second or next app launch, notify id is available
            SDKCore.instance.onDeviceId(ctx, ctx.getConfig().getDeviceId(), ctx.getConfig().getDeviceId());
        }
    }

    @Override
    public void onDeviceId(final Ctx ctx, final Config.DID deviceId, final Config.DID oldDeviceId) {
        if (ctx.getConfig().isLimited()) {
            return;
        }
        L.d("onDeviceId " + deviceId);

        SessionImpl session = SDKCore.instance.getSession();

        if (deviceId != null && oldDeviceId != null && deviceId.realm == Config.DID.REALM_DID && !deviceId.equals(oldDeviceId)) {
            // device id changed
            if (session != null && session.isActive()) {
                // end previous session
                L.d("Ending session because device id was changed from " + oldDeviceId.id);
                session.end(null, null, oldDeviceId.id);
            }

            // add device id change request
            Request request = ModuleRequests.nonSessionRequest(ctx);
            request.params.add(Params.PARAM_DEVICE_ID, deviceId.id).add(Params.PARAM_OLD_DEVICE_ID, oldDeviceId.id);
            ModuleRequests.pushAsync(ctx, request);

            sendDIDSignal(ctx, deviceId, oldDeviceId);

        } else if (deviceId == null && oldDeviceId != null && oldDeviceId.realm == Config.DID.REALM_DID) {
            // device id is unset
            if (session != null) {
                L.d("Ending session because device id was unset from " + oldDeviceId.id);
                session.end(null, null, oldDeviceId.id);
            }

            sendDIDSignal(ctx, null, oldDeviceId);

        } else if (deviceId != null && oldDeviceId == null && deviceId.realm == Config.DID.REALM_DID) {
            // device id just acquired
            if (this.tasks == null) {
                this.tasks = new Tasks("deviceId");
            }
            tasks.run(new Tasks.Task<Object>(0L) {
                @Override
                public Object call() throws Exception {
                    // put device_id parameter into existing requests
                    L.i("Adding device_id to previous requests");
                    boolean success = transformRequests(ctx, deviceId.id);
                    if (success) {
                        L.i("First transform: success");
                    } else {
                        L.w("First transform: failure");
                    }

                    // do it second time in case new requests were added during first attempt
                    success = transformRequests(ctx, deviceId.id);
                    if (!success) {
                        L.e("Failed to put device_id into existing requests, following behaviour for unhandled requests is undefined.");
                    } else {
                        L.i("Second transform: success");
                    }
                    sendDIDSignal(ctx, deviceId, null);
                    return null;
                }
            });
        }
    }

    @Override
    public Integer getFeature() {
        return CoreFeature.DeviceId.getIndex();
    }

    /**
     * Puts {@code "device_id"} parameter into all requests which don't have it yet
     *
     * @param ctx Ctx to run in
     * @param deviceId deviceId string
     * @return {@code true} if {@link Request}s changed successfully, {@code false} otherwise
     */
    private boolean transformRequests(final Ctx ctx, final String deviceId) {
        return Storage.transform(ctx, Request.getStoragePrefix(), new Transformer() {
            @Override
            public byte[] doTheJob(Long id, byte[] data) {
                Request request = new Request(id);
                if (request.restore(data) && !request.params.has(Params.PARAM_DEVICE_ID)) {
                    request.params.add(Params.PARAM_DEVICE_ID, deviceId);
                    return request.store();
                }
                return null;
            }
        });
    }

    /**
     * Just a wrapper around {@link SDK#onSignal(Ctx, int, Byteable, Byteable)}} for {@link ly.count.sdk.internal.SDKCore.Signal#DID} case
     *
     * @param ctx Ctx to run in
     * @param id new {@link Config.DID} if any
     * @param old old {@link Config.DID} if any
     */
    private void sendDIDSignal(Ctx ctx, Config.DID id, Config.DID old) {
        L.d("Sending device id signal: " + id + ", was " + old);
        SDKCore.instance.onSignal(ctx, SDKCore.Signal.DID.getIndex(), id, old);
    }

    /**
     * Logging into app-specific account:
     * - reset device id and notify modules;
     * - send corresponding request to server.
     *
     * @param ctx ctx to run in
     * @param id device id to change to
     */
    public void login(Ctx ctx, String id) {
        if (Utils.isEmpty(id)) {
            L.wtf("Empty id passed to login method");
        } else {
            final Config.DID old = ctx.getConfig().getDeviceId();
            ctx.getConfig().setDeviceId(new Config.DID(Config.DID.REALM_DID, Config.DID.STRATEGY_CUSTOM, id));
            Storage.push(ctx, ctx.getConfig());

            // old session end & new session begin requests are supposed to happen here
            SDKCore.instance.onDeviceId(ctx, ctx.getConfig().getDeviceId(), old);
        }
    }

    /**
     * Logging out from app-specific account and reverting back to previously used id if any:
     * - nullify device id and notify modules;
     * - send corresponding request to server.
     *
     * @param ctx context to run in
     */
    public void logout(final Ctx ctx) {
        final Config.DID old = ctx.getConfig().getDeviceId();
        ctx.getConfig().removeDeviceId(old);
        Storage.push(ctx, ctx.getConfig());

        SDKCore.instance.onDeviceId(ctx, null, old);

        acquireId(ctx, new Config.DID(Config.DID.REALM_DID, ctx.getConfig().getDeviceIdStrategy(), null), ctx.getConfig().isDeviceIdFallbackAllowed(), new Tasks.Callback<Config.DID>() {
            @Override
            public void call(Config.DID id) throws Exception {
                if (id != null) {
                    L.d("Got device id: " + id);
                    SDKCore.instance.onDeviceId(ctx, id, null);
                } else {
                    L.i("No device id of strategy " + ctx.getConfig().getDeviceIdStrategy() + " is available yet");
                }
            }
        });
    }

    /**
     * Resetting id without merging profiles on server:
     * <ul>
     *     <li>End current session if any</li>
     *     <li>Begin new session with new id if previously ended a session</li>
     * </ul>
     * @param ctx context to run in
     * @param id new user id
     */
    public void resetDeviceId(Ctx ctx, String id) {
        if (Utils.isEmpty(id)) {
            L.wtf("Empty id passed to resetId method");
        } else {
            final Config.DID old = ctx.getConfig().getDeviceId();
            ctx.getConfig().setDeviceId(new Config.DID(Config.DID.REALM_DID, Config.DID.STRATEGY_CUSTOM, id));
            Storage.push(ctx, ctx.getConfig());

            SDKCore.instance.onDeviceId(ctx, null, old);
            SDKCore.instance.onDeviceId(ctx, ctx.getConfig().getDeviceId(), null);
        }
    }

    protected Future<Config.DID> acquireId(final Ctx ctx, final Config.DID holder, final boolean fallbackAllowed, final Tasks.Callback<Config.DID> callback) {
        L.d("d4");
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
     * @param ctx Ctx to run in
     * @param holder DID object which holds strategy and possibly other info for id generation
     * @param fallbackAllowed whether to automatically fallback to any available alternative or not
     * @return {@link Config.DID} instance with an id
     */
    protected Config.DID acquireIdSync(final Ctx ctx, final Config.DID holder, final boolean fallbackAllowed) {
        if (testSleep > 0) {
            try {
                Thread.sleep(testSleep);
            } catch (InterruptedException ie) {
                L.wtf("Exception during tests", ie);
            }
        }

        L.d((ctx.getConfig().isLimited() ? "limited " : "")  + "acquireIdSync " + holder + " / " + fallbackAllowed);

        L.i("Generating " + holder.strategy + " / " + holder.realm);

        int index = holder.strategy;
//        DeviceIdGenerator generator = generators.get(index);
//        if ((generator == null || !generator.isAvailable()) && !fallbackAllowed) {
//            Log.wtf("Device id strategy " + index + " is not available, while fallback is not allowed. SDK won't function properly.");
//        } else {
//            String id = generator.generate(ctx, holder.realm);
//            if (Utils.isNotEmpty(id)) {
//                return new Config.DID(holder.realm, index, id);
//            } else if (!fallbackAllowed) {
//                Log.wtf("Device id " + index + " is not available, while fallback is not allowed. SDK won't function properly.");
//            }
//        }

        while (index > 0) {
            DeviceIdGenerator generator = generators.get(index);
            if (generator == null || !generator.isAvailable()) {
                if (fallbackAllowed) {
                    Log.w("Device id strategy " + index + " is not available. Falling back to next one.");
                    index--;
                    continue;
                } else {
                    Log.wtf("Device id strategy " + index + " is not available, while fallback is not allowed. SDK won't function properly.");
                    return null;
                }
            } else {
                String id = generator.generate(ctx, holder.realm);
                if (Utils.isNotEmpty(id)) {
                    return new Config.DID(holder.realm, index, id);
                } else if (fallbackAllowed) {
                    Log.w("Device id strategy " + index + " didn't return. Falling back to next one.");
                    index--;
                    continue;
                } else {
                    Log.wtf("Device id strategy " + index + " didn't return, while fallback is not allowed. SDK won't function properly.");
                }
            }
        }

        Log.wtf("No device id strategies to fallback from " + ctx.getConfig().getDeviceIdStrategy() + " is available. SDK won't function properly.");

        return null;
    }

    protected void callOnDeviceId(Ctx ctx, Config.DID id, Config.DID old) {
        SDKCore.instance.onDeviceId(ctx, id, old);
    }

    @Override
    public void stop(Ctx ctx, boolean clear) {
        if (tasks != null) {
            tasks.shutdown();
            tasks = null;
        }
    }

    private static long testSleep = 0L;

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
