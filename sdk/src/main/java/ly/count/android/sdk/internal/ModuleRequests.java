package ly.count.android.sdk.internal;

import java.util.concurrent.Future;

import ly.count.android.sdk.Config;
import ly.count.sdk.User;

/**
 * Centralized place for all requests construction & handling.
 */

public class ModuleRequests extends ModuleBase {
    private static final Log.Module L = Log.module("ModuleRequests");

    private static InternalConfig config;
    private static Params metrics;

    public interface ParamsInjector {
        void call(Params params);
    }

    @Override
    public void init(InternalConfig config) {
        super.init(config);
        ModuleRequests.config = config;
    }

    @Override
    public void onContextAcquired(Context ctx) {
        super.onContextAcquired(ctx);
        ModuleRequests.metrics = Device.buildMetrics(ctx);
    }

    @Override
    public Config.Feature getFeature() {
        return null;
    }

    public static Future<Boolean> sessionBegin(Context ctx, SessionImpl session) {
        Request request = addCommon(config, session, Request.build("begin_session", 1));
        request.params.add(metrics);
        session.params.clear();
        return pushAsync(ctx, request);
    }

    public static Future<Boolean> sessionUpdate(Context ctx, SessionImpl session, Long seconds) {
        Request request = addCommon(config, session, Request.build());

        if (seconds != null && seconds > 0) {
            request.params.add("session_duration", seconds);
        }

        session.params.clear();
        return pushAsync(ctx, request);
    }

    public static Future<Boolean> sessionEnd(Context ctx, SessionImpl session, Long seconds, String did, Tasks.Callback<Boolean> callback) {
        Request request = addCommon(config, session, Request.build("end_session", 1));

        if (did != null && Utils.isNotEqual(did, request.params.get(Params.PARAM_DEVICE_ID))) {
            request.params.remove(Params.PARAM_DEVICE_ID);
            request.params.add(Params.PARAM_DEVICE_ID, did);
        }

        if (seconds != null && seconds > 0) {
            request.params.add("session_duration", seconds);
        }

        session.params.clear();
        return pushAsync(ctx, request, callback);
    }

    public static Future<Boolean> location(Context ctx, double latitude, double longitude) {
        return pushAsync(ctx, addCommon(config, null, Request.build("location", latitude + "," + longitude)));
    }

    public static Future<Boolean> changeId(Context ctx, InternalConfig config, Context context, String oldId) {
        Request request = addCommon(config, null, Request.build("begin_session", 1));
        return pushAsync(ctx, request);
//        String data = "app_key=" + appKey_
//                + "&timestamp=" + Countly.currentTimestampMs()
//                + "&hour=" + Countly.currentHour()
//                + "&dow=" + Countly.currentDayOfWeek()
//                + "&tz=" + DeviceInfo.getTimezoneOffset()
//                + "&sdk_version=" + Countly.COUNTLY_SDK_VERSION_STRING
//                + "&sdk_name=" + Countly.COUNTLY_SDK_NAME
//                + "&begin_session=1"
//                + "&metrics=" + DeviceInfo.getMetrics(context_);

    }

    static Request nonSessionRequest(InternalConfig config) {
        return addCommon(config == null ? ModuleRequests.config : config, null, new Request());
    }

    static void injectParams(Context ctx, ParamsInjector injector) {
        SessionImpl session = Core.instance.getSession();
        if (session == null) {
            Request request = nonSessionRequest(config);
            injector.call(request.params);
            pushAsync(ctx, request);
        } else {
            injector.call(session.params);
        }
    }

    private static Request addCommon(InternalConfig config, SessionImpl session, Request request) {
        request.params.add("timestamp", Device.uniqueTimestamp())
                .add("tz", Device.getTimezoneOffset())
                .add("hour", Device.currentHour())
                .add("dow", Device.currentDayOfWeek());

        if (session != null) {
            request.params.add("session_id", session.getId());

            synchronized (session.storageId()) {
                if (!session.events.isEmpty()) {
                    request.params.arr("events").put(session.events).add();
                    session.events.clear();
                }
                request.params.add(session.params);
                session.params.clear();
            }
        }

        if (config.getDeviceId() != null) {
            request.params.add(Params.PARAM_DEVICE_ID, config.getDeviceId().id);
        }

        return request;
    }

    static Request addRequired(InternalConfig config, Request request) {
        if (!request.params.has("sdk_name")) {
            request.params.add("sdk_name", config.getSdkName())
                    .add("sdk_version", config.getSdkVersion())
                    .add("app_key", config.getServerAppKey());
        }

        if (!request.params.has(Params.PARAM_DEVICE_ID)) {
            if (config.getDeviceId() == null) {
                return null;
            } else {
                request.params.add(Params.PARAM_DEVICE_ID, config.getDeviceId().id);
                return request;
            }
        }

        if (request.params.has("begin_session")) {
            User user = Core.instance.user();
            if (user.country() != null) {
                request.params.add("country_code", user.country());
            }
            if (user.city() != null) {
                request.params.add("city", user.city());
            }
            if (user.location() != null) {
                request.params.add("location", user.location());
            }
        }

        return request;
    }

    /**
     * Common store-request logic: store & send a ping to the service.
     *
     * @param ctx Context to run in
     * @param request Request to store
     * @return {@link Future} which resolves to {@code} true if stored successfully, false otherwise
     */
    public static Future<Boolean> pushAsync(Context ctx, Request request) {
        return pushAsync(ctx, request, null);
    }

    /**
     * Common store-request logic: store & send a ping to the service.
     *
     * @param ctx Context to run in
     * @param request Request to store
     * @param callback Callback (nullable) to call when storing is done, called in {@link Storage} {@link Thread}
     * @return {@link Future} which resolves to {@code} true if stored successfully, false otherwise
     */
    public static Future<Boolean> pushAsync(final Context ctx, Request request, final Tasks.Callback<Boolean> callback) {
        L.d("New request " + request.storageId() + ": " + request);
        return Storage.pushAsync(ctx, request, new Tasks.Callback<Boolean>() {
            @Override
            public void call(Boolean param) throws Exception {
                CoreLifecycle.sendToService(ctx, CountlyService.CMD_PING, null);
                if (callback != null) {
                    callback.call(param);
                }
            }
        });
    }
}
