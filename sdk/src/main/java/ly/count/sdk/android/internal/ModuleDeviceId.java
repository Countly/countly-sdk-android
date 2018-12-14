package ly.count.sdk.android.internal;

import android.annotation.SuppressLint;
import android.content.Context;
import android.provider.Settings;


import ly.count.sdk.android.Config;
import ly.count.sdk.internal.DeviceIdGenerator;
import ly.count.sdk.internal.Log;
import ly.count.sdk.internal.Utils;

public class ModuleDeviceId extends ly.count.sdk.internal.ModuleDeviceId {
    private static final Log.Module L = Log.module("ModuleDeviceId");

    public static final String ADVERTISING_ID_CLIENT_CLASS_NAME = "com.google.android.gms.ads.identifier.AdvertisingIdClient";
    static final String INSTANCE_ID_CLASS_NAME = "com.google.firebase.iid.FirebaseInstanceId";

    static {
        registerGenerator(Config.DeviceIdStrategy.ANDROID_ID.getIndex(), new DeviceIdGenerator() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public String generate(ly.count.sdk.internal.Ctx context, int realm) {
                if (realm != ly.count.sdk.Config.DID.REALM_DID) {
                    return null;
                }
                @SuppressLint("HardwareIds")
                String id = Settings.Secure.getString(((Context)context.getContext()).getContentResolver(), Settings.Secure.ANDROID_ID);

                // ANDROID_ID is null, or it's equals to the GalaxyTab generic ANDROID_ID or bad
                if (id == null || id.equals("9774d56d682e549c") || id.length() < 15) {
                    return null;
                }

                return id;
            }
        });

        registerGenerator(Config.DeviceIdStrategy.ADVERTISING_ID.getIndex(), new DeviceIdGenerator() {
            private final Log.Module L = Log.module("AdvertisingIdDeviceIdGenerator");

            @Override
            public boolean isAvailable() {
                return Utils.reflectiveClassExists(ADVERTISING_ID_CLIENT_CLASS_NAME);
            }

            @Override
            public String generate(ly.count.sdk.internal.Ctx context, int realm) {
                if (realm != ly.count.sdk.Config.DID.REALM_DID && realm != Config.DeviceIdRealm.ADVERTISING_ID.getIndex()) {
                    return null;
                }

                try {
                    Object info = Utils.reflectiveCallStrict(ADVERTISING_ID_CLIENT_CLASS_NAME, null, "getAdvertisingIdInfo", android.content.Context.class, context.getContext());
                    L.i("Got ADVERTISING_ID info");
                    if (info == null || info == Boolean.FALSE) {
                        L.d("AdvertisingIdClient.getAdvertisingIdInfo() returned " + info);
                        return null;
                    } else {
                        L.d("calling getId");
                        Object idObj = Utils.reflectiveCall(info.getClass().getCanonicalName(), info, "getId");
                        L.d("AdvertisingIdClient.getAdvertisingIdInfo().getId() returned " + idObj);
                        if (!(idObj instanceof String)) {
                            return null;
                        } else {
                            return (String) idObj;
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
                        return null;
                    } else {
                        // unexpected
                        L.w("Couldn't get advertising ID", t);
                        return null;
                    }
                }
            }
        });


        registerGenerator(Config.DeviceIdStrategy.INSTANCE_ID.getIndex(), new DeviceIdGenerator() {
            private final Log.Module L = Log.module("InstanceIdDeviceIdGenerator");

            @Override
            public boolean isAvailable() {
                return Utils.reflectiveClassExists(INSTANCE_ID_CLASS_NAME);
            }

            @Override
            public String generate(ly.count.sdk.internal.Ctx context, int realm) {
                if (realm != ly.count.sdk.Config.DID.REALM_DID && realm != Config.DeviceIdRealm.FCM_TOKEN.getIndex()) {
                    return null;
                }

                try {
                    Object instance = Utils.reflectiveCall(INSTANCE_ID_CLASS_NAME, null, "getInstance");
                    if (instance == null || instance == Boolean.FALSE) {
                        L.d("InstanceId.getInstance() returned " + instance);
                        return null;
                    } else {
                        Object idObj;
                        if (realm == ly.count.sdk.Config.DID.REALM_DID) {
                            idObj = Utils.reflectiveCall(instance.getClass().getName(), instance, "getId");
                        } else {
                            idObj = Utils.reflectiveCall(instance.getClass().getName(), instance, "getToken");
                        }
                        if (!(idObj instanceof String)) {
                            L.d("InstanceId.getInstance().getId() returned " + idObj);
                            return null;
                        } else {
                            return (String) idObj;
                        }
                    }
                } catch (Throwable t) {
                    if (t.getCause() != null && t.getCause().getClass().toString().contains("GooglePlayServicesAvailabilityException")) {
                        // recoverable, let device ID be null, which will result in storing all requests locally
                        // and rerunning them whenever Advertising ID becomes available
                        L.i("Instance ID cannot be determined yet");
                        return null;
                    } else if (t.getCause() != null && t.getCause().getClass().toString().contains("GooglePlayServicesNotAvailableException")) {
                        // non-recoverable, fallback to OpenUDID
                        L.i("Instance ID cannot be determined because Play Services are not available");
                        return null;
                    } else {
                        // unexpected
                        L.w("Couldn't get Instance ID", t);
                        return null;
                    }
                }
            }
        });
    }

    /**
     * Overriding core logic to reuse legacy id.
     *
     * @param ctx Ctx
     */
    @Override
    public void onContextAcquired(final ly.count.sdk.internal.Ctx ctx) {
        if (ctx.getConfig().getDeviceId() == null) {
            // either fresh install, or migration from legacy SDK

            String legacyDeviceId = Legacy.getOnce((Ctx) ctx, Legacy.KEY_ID_ID);

            if (Utils.isNotEmpty(legacyDeviceId)) {
                L.d("Migrating device id " + legacyDeviceId);

                ly.count.sdk.Config.DID did = new ly.count.sdk.Config.DID(ly.count.sdk.Config.DID.REALM_DID, ctx.getConfig().getDeviceIdStrategy(), legacyDeviceId);
                callOnDeviceId(ctx, did, did);
                return;
            }
        }

        super.onContextAcquired(ctx);
    }
}
