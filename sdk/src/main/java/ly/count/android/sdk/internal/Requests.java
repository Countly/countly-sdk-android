package ly.count.android.sdk.internal;

import android.annotation.SuppressLint;

import java.util.Calendar;

import ly.count.android.sdk.Session;

/**
 * Centralized place for all requests construction & handling.
 */

public class Requests {

    public static Request sessionBegin(InternalConfig config, long timestamp, Session session) {
//        return addCommon(config, timestamp, Request.build(
//                "begin_session", 1,
//                "metrics",
//        ))
//        return Request.build(
//                "app_key", config.getServerAppKey(),
//                "timestamp", timestamp,
//                "hour",
//        )
        return null;
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

    private static Request addCommon(InternalConfig config, long ms, Request request) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(ms);

        request.params.add("app_key", config.getServerAppKey())
                .add("timestamp", (int)(ms / 1000L))
                .add("hour", calendar.get(Calendar.HOUR_OF_DAY))
                .add("dow", dow(calendar))
                .add("sdk_name", config.getSdkName())
                .add("sdk_version", config.getSdkVersion());
        return request;
    }

    /**
     * Get current timestamp.
     *
     * @return current timestamp in seconds
     */
    private static int currentTimestamp(long ms) {
        return 0;
    }

    /**
     * Get current hour or day.
     *
     * @return current hour of the day in 24h-format
     */
    private static int currentHour(){
        return Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
    }

    /**
     * Get current day of week.
     *
     * @return current day of week, 0 is Sunday
     */
    @SuppressLint("SwitchIntDef")
    private static int dow(Calendar calendar){
        int day = calendar.get(Calendar.DAY_OF_WEEK);
        switch (day) {
            case Calendar.MONDAY:
                return 1;
            case Calendar.TUESDAY:
                return 2;
            case Calendar.WEDNESDAY:
                return 3;
            case Calendar.THURSDAY:
                return 4;
            case Calendar.FRIDAY:
                return 5;
            case Calendar.SATURDAY:
                return 6;
        }
        return 0;
    }

}
