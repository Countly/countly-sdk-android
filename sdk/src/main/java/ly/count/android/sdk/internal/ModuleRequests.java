package ly.count.android.sdk.internal;

import java.util.concurrent.Future;

/**
 * Centralized place for all requests construction & handling.
 */

public class ModuleRequests extends ModuleBase {
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

    public static Future<Boolean> sessionEnd(Context ctx, SessionImpl session, Long seconds, Tasks.Callback<Boolean> callback) {
        Request request = addCommon(config, session, Request.build("end_session", 1));

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

    // TODO: synchronization
    static void injectParams(Context ctx, ParamsInjector injector) {
        if (Core.instance.sessionLeading() == null) {
            Request request = nonSessionRequest(config);
            injector.call(request.params);
            pushAsync(ctx, request);
        } else {
            injector.call(Core.instance.sessionLeading().params);
        }
    }

    private static Request addCommon(InternalConfig config, SessionImpl session, Request request) {
        request.params.add("timestamp", Device.uniqueTimestamp())
                .add("tz", Device.getTimezoneOffset())
                .add("hour", Device.currentHour())
                .add("dow", Device.currentDayOfWeek());

        if (session != null) {
            request.params.add("session_id", session.getId());

            if (!session.events.isEmpty()) {
                request.params.arr("events").put(session.events).add();
            }

            request.params.add(session.params);
        }

        return request;
    }

    static Request addRequired(InternalConfig config, Request request) {
        request.params.add("sdk_name", config.getSdkName())
                .add("sdk_version", config.getSdkVersion())
                .add("app_key", config.getServerAppKey())
                .add("device_id", config.getDeviceId().id);
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
        Log.d("New request " + request.storageId() + ": " + request);
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
