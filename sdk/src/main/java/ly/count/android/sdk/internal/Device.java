package ly.count.android.sdk.internal;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Debug;
import android.os.Environment;
import android.os.StatFs;
import android.telephony.TelephonyManager;
import android.util.*;
import android.view.Display;
import android.view.WindowManager;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class encapsulating most of device-specific logic: metrics, info, etc.
 */

public class Device {
    private static final Log.Module L = Log.module("Device");

    /**
     * One second in nanoseconds
     */
    static final Double NS_IN_SECOND = 1000000000.0d;
    static final Double NS_IN_MS = 1000000.0d;
    static final Double MS_IN_SECOND = 1000d;
    static final Long BYTES_IN_MB = 1024L * 1024;

    static class TimeUniquenessEnsurer {
        List<Long> lastTsMs = new ArrayList<>(10);
        long addition = 0;

        long currentTimeMillis() {
            return System.currentTimeMillis() + addition;
        }

        synchronized long uniqueTimestamp() {
            long ms = currentTimeMillis();

            // change time back case
            if (lastTsMs.size() > 2) {
                long min = Collections.min(lastTsMs);
                if (ms < min) {
                    lastTsMs.clear();
                    lastTsMs.add(ms);
                    return ms;
                }
            }
            // usual case
            while (lastTsMs.contains(ms)) {
                ms += 1;
            }
            while (lastTsMs.size() >= 10) {
                lastTsMs.remove(0);
            }
            lastTsMs.add(ms);
            return ms;
        }
    }
    private static TimeUniquenessEnsurer timeGenerator = new TimeUniquenessEnsurer();

    /**
     * Get operation system name
     *
     * @return the display name of the current operating system.
     */
    static String getOS() {
        return "Android";
    }

    /**
     * Get Android version
     *
     * @return current operating system version as a displayable string.
     */
    static String getOSVersion() {
        return android.os.Build.VERSION.RELEASE;
    }

    /**
     * Get device model
     *
     * @return device model name.
     */
    public static String getDevice() {
        return android.os.Build.MODEL;
    }

    /**
     * Get the non-scaled pixel resolution of the current default display being used by the
     * WindowManager in the specified context.
     *
     * @param context context to use to retrieve the current WindowManager
     * @return a string in the format "WxH", or the empty string "" if resolution cannot be determined
     */
    static String getResolution(final android.content.Context context) {
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
            L.w("Device resolution cannot be determined", t);
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
    static String getDensity(final android.content.Context context) {
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
            case DisplayMetrics.DENSITY_260:
                densityStr = "XHDPI";
                break;
            case DisplayMetrics.DENSITY_280:
                densityStr = "XHDPI";
                break;
            case DisplayMetrics.DENSITY_300:
                densityStr = "XHDPI";
                break;
            case DisplayMetrics.DENSITY_XHIGH:
                densityStr = "XHDPI";
                break;
            case DisplayMetrics.DENSITY_340:
                densityStr = "XXHDPI";
                break;
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
    static String getCarrier(final android.content.Context context) {
        String carrier = "";
        final TelephonyManager manager = (TelephonyManager) context.getSystemService(android.content.Context.TELEPHONY_SERVICE);
        if (manager != null) {
            carrier = manager.getNetworkOperatorName();
        }
        if (carrier == null || carrier.length() == 0) {
            carrier = "";
            L.w("No carrier found");
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
    static String getLocale() {
        final Locale locale = Locale.getDefault();
        return locale.getLanguage() + "_" + locale.getCountry();
    }

    /**
     * Get application version
     *
     * @return string stored in the specified context's package info versionName field,
     * or "1.0" if versionName is not present.
     */
    static String getAppVersion(final android.content.Context context) {
        String result = "1.0";
        try {
            result = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        }
        catch (PackageManager.NameNotFoundException e) {
            L.w("No app version found", e);
        }
        return result;
    }

    /**
     * Get package name of an app which installed this app
     *
     * @return package name of the store
     */
    static String getStore(final android.content.Context context) {
        String result = "";
        try {
            result = context.getPackageManager().getInstallerPackageName(context.getPackageName());
        } catch (Exception e) {
            L.w("Can't get Installer package", e);
        }
        if (result == null || result.length() == 0) {
            result = "";
            L.w("No store found");
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
     * same millisecond and even when time changes. Works in a limited window of 10 timestamps for now.
     *
     * @return unique time in ms
     */
    static synchronized long uniqueTimestamp() {
        return timeGenerator.uniqueTimestamp();
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
     * Convert time in seconds to nanoseconds
     *
     * @param sec time in seconds
     * @return sec in nanoseconds
     */
    static long secToNs(long sec) {
        return Math.round(sec * NS_IN_SECOND);
    }

    /**
     * Convert time in seconds to milliseconds
     *
     * @param sec time in seconds
     * @return sec in nanoseconds
     */
    static long secToMs(long sec) {
        return Math.round(sec * MS_IN_SECOND);
    }

    /**
     * Check whether API version of current device is greater or equals to {@code min}
     * @param min minumum version that returns {@code true}
     * @return true if API version is greater or equeal to {@code min}, false otherwise
     */
    static boolean API(int min) {
        return Build.VERSION.SDK_INT >= min;
    }

    /**
     * Get total RAM in Mb
     *
     * @return total RAM in Mb or null if cannot determine
     */
    public static Long getRAMTotal() {
        RandomAccessFile reader = null;
        try {
            reader = new RandomAccessFile("/proc/meminfo", "r");
            String load = reader.readLine();

            // Get the Number value from the string
            Pattern p = Pattern.compile("(\\d+)");
            Matcher m = p.matcher(load);
            String value = "";
            while (m.find()) {
                value = m.group(1);
            }
            return Long.parseLong(value) / 1024;
        } catch (NumberFormatException e){
            L.e("Cannot parse meminfo", e);
            return null;
        } catch (IOException e) {
            L.e("Cannot read meminfo", e);
            return null;
        }
        finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Get current device manufacturer name.
     *
     * @return device manufacturer string
     */
    public static String getManufacturer() {
        return Build.MANUFACTURER;
    }

    /**
     * Get current device cpu.
     *
     * @return main CPU ABI
     */
    public static String getCpu() {
        if (Utils.API(Build.VERSION_CODES.LOLLIPOP)) {
            return Build.SUPPORTED_ABIS[0];
        } else {
            return Build.CPU_ABI;
        }
    }

    /**
     * Get current device OpenGL version.
     *
     * @return OpenGL version, falls back to 1 if cannot determine
     */
    public static Integer getOpenGL(android.content.Context context) {
        PackageManager packageManager = context.getPackageManager();
        FeatureInfo[] featureInfos = packageManager.getSystemAvailableFeatures();
        if (featureInfos == null || featureInfos.length == 0) {
            return 1;
        }
        for (FeatureInfo featureInfo : featureInfos) {
            // Null feature name means this feature is the open gl es version feature.
            if (featureInfo.name == null) {
                if (featureInfo.reqGlEsVersion != FeatureInfo.GL_ES_VERSION_UNDEFINED) {
                    return (featureInfo.reqGlEsVersion & 0xffff0000) >> 16;
                } else {
                    return 1; // Lack of property means OpenGL ES version 1
                }
            }
        }
        return 1;
    }

    /**
     * Get current device RAM amount.
     *
     * @return currently available RAM in Mb or {@code null} if couldn't determine
     */
    public static Long getRAMAvailable(android.content.Context context) {
        Long total = getRAMTotal();
        if (total == null) {
            return null;
        }
        total = total * BYTES_IN_MB;
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager) context.getSystemService(android.content.Context.ACTIVITY_SERVICE);
        activityManager.getMemoryInfo(mi);
        return (total - mi.availMem) / BYTES_IN_MB;
    }

    /**
     * Get current device disk space.
     *
     * @return currently available disk space in Mb
     */
    public static Long getDiskAvailable() {
        StatFs statFs = new StatFs(Environment.getRootDirectory().getAbsolutePath());
        long total = getDiskTotal() * BYTES_IN_MB, free;
        if (Utils.API(18)) {
            free = (statFs.getAvailableBlocksLong() * statFs.getBlockSizeLong());
        }  else {
            free = ((long)statFs.getAvailableBlocks() * (long)statFs.getBlockSize());
        }
        return (total - free) / BYTES_IN_MB;
    }

    /**
     * Get total device disk space.
     *
     * @return total device disk space in Mb
     */
    public static Long getDiskTotal() {
        StatFs statFs = new StatFs(Environment.getRootDirectory().getAbsolutePath());
        long total;
        if (Utils.API(18)) {
            total = (statFs.getBlockCountLong() * statFs.getBlockSizeLong());
        }  else {
            total = ((long)statFs.getBlockCount() * (long)statFs.getBlockSize());
        }
        return total / BYTES_IN_MB;
    }

    /**
     * Get current device battery level.
     *
     * @return device battery left in percent or {@code null} if couldn't determine
     */
    public static Float getBatteryLevel(android.content.Context context) {
        try {
            Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if(batteryIntent != null) {
                int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

                // Error checking that probably isn't needed but I added just in case.
                if (level > -1 && scale > 0) {
                    return 100.0f * (float) level / (float) scale;
                }
            }
        } catch (Exception e) {
            L.w("Can't get batter level", e);
        }

        return null;
    }

    /**
     * Get current device orientation.
     *
     * @return orientation name or {@code null} if couldn't determine
     */
    public static String getOrientation(android.content.Context context) {
        int orientation = context.getResources().getConfiguration().orientation;
        switch (orientation) {
            case  Configuration.ORIENTATION_LANDSCAPE:
                return "Landscape";
            case Configuration.ORIENTATION_PORTRAIT:
                return "Portrait";
            case Configuration.ORIENTATION_UNDEFINED:
                return "Unknown";
            default:
                return null;
        }
    }

    /**
     * Check if device is rooted.
     *
     * @return {@code true} if rooted, {@code false} otherwise
     */
    public static Boolean isRooted() {
        String[] paths = {
                "/sbin/su", "/system/bin/su", "/system/xbin/su",
                "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
                "/system/bin/failsafe/su", "/data/local/su" };
        for (String path : paths) {
            if (new File(path).exists()) return true;
        }
        return false;
    }

    /**
     * Check if device is online.
     *
     * @return {@code true} if has connectivity, {@code false} if doesn't, {@code null} if cannot determine
     */
    public static Boolean isOnline(android.content.Context context) {
        try {
            ConnectivityManager conMgr = (ConnectivityManager) context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            if (conMgr != null && conMgr.getActiveNetworkInfo() != null
                    && conMgr.getActiveNetworkInfo().isAvailable()
                    && conMgr.getActiveNetworkInfo().isConnected()) {

                return true;
            }
            return false;
        } catch(Exception e) {
            L.w("Exception while determining connectivity", e);
        }
        return null;
    }

    /**
     * Check if device is muted.
     *
     * @return {@code true} if muted, {@code false} if not, {@code null}  if cannot determine
     */
    public static Boolean isMuted(android.content.Context context) {
        AudioManager audio = (AudioManager) context.getSystemService(android.content.Context.AUDIO_SERVICE);
        if (audio == null) {
            return null;
        }
        switch( audio.getRingerMode() ){
            case AudioManager.RINGER_MODE_SILENT:
                return true;
            case AudioManager.RINGER_MODE_VIBRATE:
                return true;
            default:
                return false;
        }
    }

    /**
     * Check whether app is running in foreground.
     *
     * @param context context to check in
     * @return {@code true} if running in foreground, {@code false} otherwise
     */
    public static boolean isAppRunningInForeground (android.content.Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(android.content.Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
        if (appProcesses == null) {
            return false;
        }
        final String packageName = context.getPackageName();
        for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND && appProcess.processName.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isDebuggerConnected() {
        return Debug.isDebuggerConnected();
    }

    /**
     * Return current process name
     *
     * @param context Context to run in
     * @return process name String or {@code null} if cannot determine
     */
    public static String getProcessName(android.content.Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(android.content.Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> infos = manager.getRunningAppProcesses();
        int pid = android.os.Process.myPid();

        for (ActivityManager.RunningAppProcessInfo info : infos) {
            if (info.pid == pid) {
                return info.processName;
            }
        }

        return null;
    }

    public static boolean isInLimitedProcess(android.content.Context context) {
        return Utils.contains(getProcessName(context), ":countly");
    }

    public static boolean isSingleProcess(android.content.Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(android.content.Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> infos = manager.getRunningAppProcesses();
        int pid = android.os.Process.myPid();

        for (ActivityManager.RunningAppProcessInfo info : infos) {
            if (info.pid == pid) {
                for (ActivityManager.RunningAppProcessInfo i : infos) {
                    if (i != info && (Utils.contains(i.processName, info.processName) || Utils.contains(info.processName, i.processName))) {
                        return false;
                    }
                }
                return true;
            }
        }
        return true;
    }

}
