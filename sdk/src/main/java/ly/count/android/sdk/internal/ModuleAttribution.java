package ly.count.android.sdk.internal;

import android.content.BroadcastReceiver;
import android.content.Intent;

import ly.count.android.sdk.Config;

/**
 * Attribution plugin implementation:
 * <ul>
 *     <li>Get Advertising ID from {@link ModuleDeviceId#acquireId(Context, Config.DID, boolean, Tasks.Callback)}, save it in {@link InternalConfig}</li>
 *     <li>Implement {@link android.content.BroadcastReceiver} listening & decoding for INSTALL_REFERRER broadcast</li>
 *     <li>Sends corresponding requests if needed</li>
 * </ul>
 */

public class ModuleAttribution extends ModuleBase {
    private static final Log.Module L = Log.module("ModuleAttribution");

    public static final String ACTION = "com.android.vending.INSTALL_REFERRER";
    public static final String EXTRA = "referrer";
    public static final String CLY_CID = "countly_cid";
    public static final String CLY_UID = "countly_cuid";
    public static final String CLY_AID = "adid";

    private InternalConfig config;
    private boolean advertisingIdNotAvailable = false;
    private String adid = null;

    public static class AttributionReferrerReceiver extends BroadcastReceiver {
        private InternalConfig config = null;

        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            final PendingResult[] pendingResult = new PendingResult[1];
            if (Device.API(11)) {
                pendingResult[0] = goAsync();
            }

            config = Core.initialized();
            if (config == null) {
                config = Core.initForBroadcastReceiver(context);
                if (config == null) {
                    // TODO: no config yet, TBD
                    L.w("[ModuleAttribution] Couldn't init Core");
                    return;
                }
                Core.instance.onLimitedContextAcquired(context);
            }

            L.d("[ModuleAttribution] It's " + ACTION + " broadcast");
            if (intent == null || !ACTION.equals(intent.getAction()) || !intent.hasExtra(EXTRA)) {
                return;
            }

            String referrer = Utils.urldecode(intent.getStringExtra(EXTRA));
            L.d("[ModuleAttribution] Referrer is " + referrer);

            if (Utils.isNotEmpty(referrer)) {
                extractReferrer(context, pendingResult, referrer);
            }
        }

        public void extractReferrer(android.content.Context context, final PendingResult[] pendingResult, String referrer) {
            if (referrer == null) {
                return;
            }
            String parts[] = referrer.split("&");
            String cid = null;
            String uid = null;
            for (String part : parts) {
                if (part.startsWith(CLY_CID)) {
                    cid = part.substring(CLY_CID.length() + 1).trim();
                }
                if (part.startsWith(CLY_UID)) {
                    uid = part.substring(CLY_UID.length() + 1).trim();
                }
            }
            L.i("[ModuleAttribution] Extracted Countly referrer: cid " + cid + " / uid " + uid);

            if (Utils.isNotEmpty(cid) || Utils.isNotEmpty(uid)) {
                // request won't be sent until advertising id is acquired
                Request request = recordRequest(cid, uid);

                ModuleRequests.pushAsync(new ContextImpl(context), request, new Tasks.Callback<Boolean>() {
                    @Override
                    public void call(Boolean success) throws Exception {
                        L.i("[ModuleAttribution] Done adding request: " + (success ? "success" : "failure"));
                        if (Device.API(11)) {
                            pendingResult[0].finish();
                        }
                    }
                });
            }
        }

        public Request recordRequest(String cid, String uid) {
            Request request = ModuleRequests.nonSessionRequest(config);
            request.params.add("campaign_id", cid, "campaign_user", uid);
            request.own(ModuleAttribution.class);
            return request;
        }
    }

    @Override
    public void init(InternalConfig config) {
        this.config = config;
    }

    /**
     * Getting {@link ly.count.android.sdk.Config.DeviceIdRealm#ADVERTISING_ID} procedure.
     * Works in line with {@link Module#onDeviceId(Context, Config.DID, Config.DID)} in some cases.
     */
    @Override
    public void onContextAcquired(final Context ctx) {
        if (config.getDeviceIdStrategy() == Config.DeviceIdStrategy.ADVERTISING_ID) {
            L.d("[ModuleAttribution] waiting for ModuleDeviceId to finish acquiring ADVERTISING_ID");
        } else {
            Config.DID did = config.getDeviceId();
            Config.DID adv = config.getDeviceId(Config.DeviceIdRealm.ADVERTISING_ID);
            if (adv == null) {
                if (did != null && did.strategy == Config.DeviceIdStrategy.ADVERTISING_ID) {
                    L.i("[ModuleAttribution] setting id from ADVERTISING_ID device id");
                    adv = new Config.DID(Config.DeviceIdRealm.ADVERTISING_ID, Config.DeviceIdStrategy.ADVERTISING_ID, did.id);
                    Core.onDeviceId(ctx, adv, null);
                } else {
                    L.d("[ModuleAttribution] getting ADVERTISING_ID");
                    Core.instance.acquireId(ctx, new Config.DID(Config.DeviceIdRealm.ADVERTISING_ID, Config.DeviceIdStrategy.ADVERTISING_ID, null), false, new Tasks.Callback<Config.DID>() {
                        @Override
                        public void call(Config.DID param) throws Exception {
                            if (param != null && param.id != null) {
                                L.i("[ModuleAttribution] getting ADVERTISING_ID succeeded: " + param);
                                Core.onDeviceId(ctx, param, null);
                            } else {
                                L.w("[ModuleAttribution] no ADVERTISING_ID available, Countly Attribution is unavailable");
                                advertisingIdNotAvailable = true;
                            }
                        }
                    });
                }
            } else {
                L.d("[ModuleAttribution] acquired ADVERTISING_ID previously");
                Core.onDeviceId(ctx, adv, adv);
            }
        }
    }

    /**
     * Getting {@link ly.count.android.sdk.Config.DeviceIdRealm#ADVERTISING_ID} procedure.
     * Works in line with {@link #onContextAcquired(Context)}} in some cases.
     */
    @Override
    public void onDeviceId(Context ctx, Config.DID deviceId, Config.DID oldDeviceId) {
        if (config.getDeviceIdStrategy() == Config.DeviceIdStrategy.ADVERTISING_ID && deviceId != null && deviceId.realm == Config.DeviceIdRealm.DEVICE_ID) {
            if (deviceId.strategy == Config.DeviceIdStrategy.ADVERTISING_ID) {
                L.d("[ModuleAttribution] waiting for ModuleDeviceId to finish acquiring ADVERTISING_ID done: " + deviceId);
                Core.onDeviceId(ctx, new Config.DID(Config.DeviceIdRealm.ADVERTISING_ID, Config.DeviceIdStrategy.ADVERTISING_ID, deviceId.id), null);
            } else {
                L.w("[ModuleAttribution] no ADVERTISING_ID available, Countly Attribution is unavailable after ModuleDeviceId flow");
                advertisingIdNotAvailable = true;
            }
        } else if (deviceId != null && deviceId.realm == Config.DeviceIdRealm.ADVERTISING_ID) {
            adid = deviceId.id;
        }
    }

    @Override
    public Boolean onRequest(Request request) {
        if (adid != null) {
            request.params.add(CLY_AID, adid);
            return true;
        } else if (advertisingIdNotAvailable) {
            return false;
        } else {
            return null;
        }
    }
}
