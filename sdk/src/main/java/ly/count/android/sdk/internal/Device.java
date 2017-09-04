package ly.count.android.sdk.internal;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.*;
import android.view.Display;
import android.view.WindowManager;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import ly.count.android.sdk.Countly;

/**
 * Class encapsulating most of device-specific logic: metrics, info, etc.
 */

class Device {
    /**
     * One second in nanoseconds
     */
    static final Double NS_IN_SECOND = 1000000000.0d;
    static final Double NS_IN_MS = 1000000.0d;
    private static long lastTsMs;

    /**
     * Get operation system name
     *
     * @return the display name of the current operating system.
     */
    private static String getOS() {
        return "Android";
    }

    /**
     * Get Android version
     *
     * @return current operating system version as a displayable string.
     */
    private static String getOSVersion() {
        return android.os.Build.VERSION.RELEASE;
    }

    /**
     * Get device model
     *
     * @return device model name.
     */
    private static String getDevice() {
        return android.os.Build.MODEL;
    }

    /**
     * Get the non-scaled pixel resolution of the current default display being used by the
     * WindowManager in the specified context.
     *
     * @param context context to use to retrieve the current WindowManager
     * @return a string in the format "WxH", or the empty string "" if resolution cannot be determined
     */
    private static String getResolution(final android.content.Context context) {
        // user reported NPE in this method; that means either getSystemService or getDefaultDisplay
        // were returning null, even though the documentation doesn't say they should do so; so now
        // we catch Throwable and return empty string if that happens
        String resolution = "";
        try {
            final WindowManager wm = (WindowManager) context.getSystemService(android.content.Context.WINDOW_SERVICE);
            final Display display = wm.getDefaultDisplay();
            final DisplayMetrics metrics = new DisplayMetrics();
            display.getMetrics(metrics);
            resolution = metrics.widthPixels + "x" + metrics.heightPixels;
        }
        catch (Throwable t) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                android.util.Log.i(Countly.TAG, "Device resolution cannot be determined");
            }
        }
        return resolution;
    }

    /**
     * Maps the current display density to a string constant.
     *
     * @param context context to use to retrieve the current display metrics
     * @return a string constant representing the current display density, or the
     *         empty string if the density is unknown
     */
    private static String getDensity(final android.content.Context context) {
        String densityStr = "";
        final int density = context.getResources().getDisplayMetrics().densityDpi;
        switch (density) {
            case DisplayMetrics.DENSITY_LOW:
                densityStr = "LDPI";
                break;
            case DisplayMetrics.DENSITY_MEDIUM:
                densityStr = "MDPI";
                break;
            case DisplayMetrics.DENSITY_TV:
                densityStr = "TVDPI";
                break;
            case DisplayMetrics.DENSITY_HIGH:
                densityStr = "HDPI";
                break;
            //todo uncomment in android sdk 25
            //case DisplayMetrics.DENSITY_260:
            //    densityStr = "XHDPI";
            //    break;
            case DisplayMetrics.DENSITY_280:
                densityStr = "XHDPI";
                break;
            //todo uncomment in android sdk 25
            //case DisplayMetrics.DENSITY_300:
            //    densityStr = "XHDPI";
            //    break;
            case DisplayMetrics.DENSITY_XHIGH:
                densityStr = "XHDPI";
                break;
            //todo uncomment in android sdk 25
            //case DisplayMetrics.DENSITY_340:
            //    densityStr = "XXHDPI";
            //    break;
            case DisplayMetrics.DENSITY_360:
                densityStr = "XXHDPI";
                break;
            case DisplayMetrics.DENSITY_400:
                densityStr = "XXHDPI";
                break;
            case DisplayMetrics.DENSITY_420:
                densityStr = "XXHDPI";
                break;
            case DisplayMetrics.DENSITY_XXHIGH:
                densityStr = "XXHDPI";
                break;
            case DisplayMetrics.DENSITY_560:
                densityStr = "XXXHDPI";
                break;
            case DisplayMetrics.DENSITY_XXXHIGH:
                densityStr = "XXXHDPI";
                break;
            default:
                densityStr = "other";
                break;
        }
        return densityStr;
    }

    /**
     * Returns the display name of the current network operator from the
     * TelephonyManager from the specified context.
     *
     * @param context context to use to retrieve the TelephonyManager from
     * @return the display name of the current network operator, or the empty
     *         string if it cannot be accessed or determined
     */
    private static String getCarrier(final android.content.Context context) {
        String carrier = "";
        final TelephonyManager manager = (TelephonyManager) context.getSystemService(android.content.Context.TELEPHONY_SERVICE);
        if (manager != null) {
            carrier = manager.getNetworkOperatorName();
        }
        if (carrier == null || carrier.length() == 0) {
            carrier = "";
            if (Countly.sharedInstance().isLoggingEnabled()) {
                android.util.Log.i(Countly.TAG, "No carrier found");
            }
        }
        return carrier;
    }

    /**
     * Get device timezone offset in seconds
     *
     * @return timezone offset in seconds
     */
    public static int getTimezoneOffset() {
        return TimeZone.getDefault().getOffset(new Date().getTime()) / 60000;
    }

    /**
     * Get current locale
     *
     * @return current locale (ex. "en_US").
     */
    private static String getLocale() {
        final Locale locale = Locale.getDefault();
        return locale.getLanguage() + "_" + locale.getCountry();
    }

    /**
     * Get application version
     *
     * @return string stored in the specified context's package info versionName field,
     * or "1.0" if versionName is not present.
     */
    private static String getAppVersion(final android.content.Context context) {
        String result = Countly.DEFAULT_APP_VERSION;
        try {
            result = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        }
        catch (PackageManager.NameNotFoundException e) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                android.util.Log.i(Countly.TAG, "No app version found");
            }
        }
        return result;
    }

    /**
     * Get package name of an app which installed this app
     *
     * @return package name of the store
     */
    private static String getStore(final android.content.Context context) {
        String result = "";
        if(android.os.Build.VERSION.SDK_INT >= 3 ) {
            try {
                result = context.getPackageManager().getInstallerPackageName(context.getPackageName());
            } catch (Exception e) {
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    android.util.Log.i(Countly.TAG, "Can't get Installer package");
                }
            }
            if (result == null || result.length() == 0) {
                result = "";
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    android.util.Log.i(Countly.TAG, "No store found");
                }
            }
        }
        return result;
    }

    /**
     * Build metrics {@link Params} object as required by Countly server
     *
     * @param ctx Context in which to request metrics
     */
    static Params buildMetrics(final Context ctx) {
        android.content.Context context = ctx.getContext();
        Params params = new Params();
        params.obj("metrics")
                .put("_device", getDevice())
                .put("_os", getOS())
                .put("_os_version", getOSVersion())
                .put("_carrier", getCarrier(context))
                .put("_resolution", getResolution(context))
                .put("_density", getDensity(context))
                .put("_locale", getLocale())
                .put("_app_version", getAppVersion(context))
                .put("_store", getStore(context))
            .add();

        return params;
    }

    /**
     * Wraps {@link System#currentTimeMillis()} to always return different value, even within
     * same millisecond.
     *
     * @return unique time in ms
     */
    static synchronized long uniqueTimestamp() {
        long ms = System.currentTimeMillis();
        while (lastTsMs >= ms) {
            ms += 1;
        }
        lastTsMs = ms;
        return ms;
    }

    /**
     * Get current day of week
     *
     * @return day of week value, Sunday = 0, Saturday = 6
     */
    @SuppressLint("SwitchIntDef")
    static int currentDayOfWeek() {
        int day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        switch (day) {
            case Calendar.SUNDAY:
                return 0;
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

    /**
     * Get current hour of day
     *
     * @return current hour of day
     */
    static int currentHour() {
        return Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
    }

    /**
     * Convert time in nanoseconds to milliseconds
     *
     * @param ns time in nanoseconds
     * @return ns in milliseconds
     */
    static long nsToMs(long ns) {
        return Math.round(ns / NS_IN_MS);
    }

    /**
     * Convert time in nanoseconds to seconds
     *
     * @param ns time in nanoseconds
     * @return ns in seconds
     */
    static long nsToSec(long ns) {
        return Math.round(ns / NS_IN_SECOND);
    }

    /**
     * Check whether API version of current device is greater or equals to {@code min}
     * @param min minumum version that returns {@code true}
     * @return true if API version is greater or equeal to {@code min}, false otherwise
     */
    static boolean API(int min) {
        return Build.VERSION.SDK_INT >= min;
    }
}
