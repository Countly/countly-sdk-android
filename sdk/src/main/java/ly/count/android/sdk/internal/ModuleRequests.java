package ly.count.android.sdk.internal;

import java.util.concurrent.Future;

import ly.count.android.sdk.Session;

/**
 * Centralized place for all requests construction & handling.
 */

public class ModuleRequests extends ModuleBase {
    private static InternalConfig config;
    private static Params metrics;

    @Override
    public void init(InternalConfig config) {
        super.init(config);
        ModuleRequests.config = config;
    }

    @Override
    public void onContextAcquired(Context context) {
        super.onContextAcquired(context);
        ModuleRequests.metrics = Device.buildMetrics(context);
    }

    public static Future<Boolean> sessionBegin(SessionImpl session) {
        Request request = addCommon(config, session, Request.build("begin_session", 1));
        request.params.add(metrics);
        // TODO: country, city, location
        return pushAsync(request);
    }

    public static Future<Boolean> sessionUpdate(SessionImpl session, Long seconds) {
        Request request = addCommon(config, session, Request.build());

        if (seconds != null && seconds > 0) {
            request.params.add("session_duration", seconds);
        }

        // TODO: location
        return pushAsync(request);
    }

    public static Future<Boolean> sessionEnd(SessionImpl session, Long seconds) {
        Request request = addCommon(config, session, Request.build("end_session", 1));

        if (seconds != null && seconds > 0) {
            request.params.add("session_duration", seconds);
        }
        // TODO: location / override_id
        return pushAsync(request);
    }

    public static Future<Boolean> location(double latitude, double longitude) {
        return pushAsync(addCommon(config, null, Request.build("location", latitude + "," + longitude)));
    }

    public static Future<Boolean> changeId(InternalConfig config, Context context, String oldId) {
        Request request = addCommon(config, null, Request.build("begin_session", 1));
        return pushAsync(request);
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
        return addCommon(config, null, new Request());
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
     * @param request Request to store
     * @return {@link Future} which resolves to {@code} true if stored successfully, false otherwise
     */
    public static Future<Boolean> pushAsync(Request request) {
        return pushAsync(request, null);
    }

    /**
     * Common store-request logic: store & send a ping to the service.
     *
     * @param request Request to store
     * @param callback Callback (nullable) to call when storing is done, called in {@link Storage} {@link Thread}
     * @return {@link Future} which resolves to {@code} true if stored successfully, false otherwise
     */
    public static Future<Boolean> pushAsync(Request request, final Tasks.Callback<Boolean> callback) {
        Log.d("New request " + request.storageId() + ": " + request);
        return Storage.pushAsync(request, new Tasks.Callback<Boolean>() {
            @Override
            public void call(Boolean param) throws Exception {
                Core.sendToService(CountlyService.CMD_PING, null);
                if (callback != null) {
                    callback.call(param);
                }
            }
        });
    }
}
