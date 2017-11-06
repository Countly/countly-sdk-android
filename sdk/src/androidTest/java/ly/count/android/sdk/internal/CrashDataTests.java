package ly.count.android.sdk.internal;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.*;
import android.content.Context;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.telephony.TelephonyManager;
import android.util.*;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import junit.framework.Assert;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ly.count.android.sdk.CountlyNeo;

public class CrashDataTests extends BaseTests {
    static class DeviceInfo {
        /**
         * Returns the display name of the current operating system.
         */
        static String getOS() {
            return "Android";
        }

        /**
         * Returns the current operating system version as a displayable string.
         */
        static String getOSVersion() {
            return android.os.Build.VERSION.RELEASE;
        }

        /**
         * Returns the current device model.
         */
        static String getDevice() {
            return android.os.Build.MODEL;
        }

        static String deepLink;

        /**
         * Returns the non-scaled pixel resolution of the current default display being used by the
         * WindowManager in the specified context.
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
                android.util.Log.i("Countly", "Device resolution cannot be determined");
            }
            return resolution;
        }

        /**
         * Maps the current display density to a string constant.
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
                android.util.Log.i("Countly", "No carrier found");
            }
            return carrier;
        }

        static int getTimezoneOffset() {
            return TimeZone.getDefault().getOffset(new Date().getTime()) / 60000;
        }

        /**
         * Returns the current locale (ex. "en_US").
         */
        static String getLocale() {
            final Locale locale = Locale.getDefault();
            return locale.getLanguage() + "_" + locale.getCountry();
        }

        /**
         * Returns the application version string stored in the specified
         * context's package info versionName field, or "1.0" if versionName
         * is not present.
         */
        static String getAppVersion(final android.content.Context context) {
            String result = "1.0";
            try {
                result = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            }
            catch (PackageManager.NameNotFoundException e) {
                android.util.Log.i("Countly", "No app version found");
            }
            return result;
        }

        /**
         * Returns the package name of the app that installed this app
         */
        static String getStore(final android.content.Context context) {
            String result = "";
            if(android.os.Build.VERSION.SDK_INT >= 3 ) {
                try {
                    result = context.getPackageManager().getInstallerPackageName(context.getPackageName());
                } catch (Exception e) {
                    android.util.Log.i("Countly", "Can't get Installer package");
                }
                if (result == null || result.length() == 0) {
                    result = "";
                    android.util.Log.i("Countly", "No store found");
                }
            }
            return result;
        }

        /**
         * Returns a URL-encoded JSON string containing the device metrics
         * to be associated with a begin session event.
         * See the following link for more info:
         * https://count.ly/resources/reference/server-api
         */
        static String getMetrics(final android.content.Context context) {
            final JSONObject json = new JSONObject();

            fillJSONIfValuesNotEmpty(json,
                    "_device", getDevice(),
                    "_os", getOS(),
                    "_os_version", getOSVersion(),
                    "_carrier", getCarrier(context),
                    "_resolution", getResolution(context),
                    "_density", getDensity(context),
                    "_locale", getLocale(),
                    "_app_version", getAppVersion(context),
                    "_store", getStore(context),
                    "_deep_link", deepLink);

            String result = json.toString();

            try {
                result = java.net.URLEncoder.encode(result, "UTF-8");
            } catch (UnsupportedEncodingException ignored) {
                // should never happen because Android guarantees UTF-8 support
            }

            return result;
        }

        /**
         * Utility method to fill JSONObject with supplied objects for supplied keys.
         * Fills json only with non-null and non-empty key/value pairs.
         * @param json JSONObject to fill
         * @param objects varargs of this kind: key1, value1, key2, value2, ...
         */
        static void fillJSONIfValuesNotEmpty(final JSONObject json, final String ... objects) {
            try {
                if (objects.length > 0 && objects.length % 2 == 0) {
                    for (int i = 0; i < objects.length; i += 2) {
                        final String key = objects[i];
                        final String value = objects[i + 1];
                        if (value != null && value.length() > 0) {
                            json.put(key, value);
                        }
                    }
                }
            } catch (JSONException ignored) {
                // shouldn't ever happen when putting String objects into a JSONObject,
                // it can only happen when putting NaN or INFINITE doubles or floats into it
            }
        }
    }

    static class CrashDetails {
        private static ArrayList<String> logs = new ArrayList<String>();
        private static Map<String,String> customSegments = null;
        private static boolean inBackground = true;
        private static long totalMemory = 0;

        private static long getTotalRAM() {
            if(totalMemory == 0) {
                RandomAccessFile reader = null;
                String load = null;
                try {
                    reader = new RandomAccessFile("/proc/meminfo", "r");
                    load = reader.readLine();

                    // Get the Number value from the string
                    Pattern p = Pattern.compile("(\\d+)");
                    Matcher m = p.matcher(load);
                    String value = "";
                    while (m.find()) {
                        value = m.group(1);
                    }
                    try {
                        totalMemory = Long.parseLong(value) / 1024;
                    }catch(NumberFormatException ex){
                        totalMemory = 0;
                    }
                } catch (IOException ex) {
                    try {
                        if(reader != null) {
                            reader.close();
                        }
                    } catch (IOException exc) {
                        exc.printStackTrace();
                    }
                    ex.printStackTrace();
                }
                finally {
                    try {
                        if(reader != null) {
                            reader.close();
                        }
                    } catch (IOException exc) {
                        exc.printStackTrace();
                    }
                }
            }
            return totalMemory;
        }

        /**
         * Notify when app is in foreground
         */
        static void inForeground() {
            inBackground = false;
        }

        /**
         * Notify when app is in background
         */
        static void inBackground() {
            inBackground = true;
        }

        /**
         * Returns app background state
         */
        static String isInBackground() {
            return Boolean.toString(inBackground);
        }

        /**
         * Adds a record in the log
         */
        static void addLog(String record) {
            logs.add(record);
        }

        /**
         * Returns the collected logs.
         */
        static String getLogs() {
            StringBuilder allLogs = new StringBuilder();

            for (String s : logs)
            {
                allLogs.append(s + "\n");
            }
            logs.clear();
            return allLogs.toString();
        }

        /**
         * Adds developer provided custom segments for crash,
         * like versions of dependency libraries.
         */
        static void setCustomSegments(Map<String,String> segments) {
            customSegments = new HashMap<String, String>();
            customSegments.putAll(segments);
        }

        /**
         * Get custom segments json string
         */
        static JSONObject getCustomSegments() {
            if(customSegments != null && !customSegments.isEmpty())
                return new JSONObject(customSegments);
            else
                return null;
        }


        /**
         * Returns the current device manufacturer.
         */
        static String getManufacturer() {
            return android.os.Build.MANUFACTURER;
        }

        /**
         * Returns the current device cpu.
         */
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        static String getCpu() {
            if(android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP )
                return android.os.Build.CPU_ABI;
            else
                return Build.SUPPORTED_ABIS[0];
        }

        /**
         * Returns the current device openGL version.
         */
        static String getOpenGL(Context context) {
            PackageManager packageManager = context.getPackageManager();
            FeatureInfo[] featureInfos = packageManager.getSystemAvailableFeatures();
            if (featureInfos != null && featureInfos.length > 0) {
                for (FeatureInfo featureInfo : featureInfos) {
                    // Null feature name means this feature is the open gl es version feature.
                    if (featureInfo.name == null) {
                        if (featureInfo.reqGlEsVersion != FeatureInfo.GL_ES_VERSION_UNDEFINED) {
                            return Integer.toString((featureInfo.reqGlEsVersion & 0xffff0000) >> 16);
                        } else {
                            return "1"; // Lack of property means OpenGL ES version 1
                        }
                    }
                }
            }
            return "1";
        }

        /**
         * Returns the current device RAM amount.
         */
        static String getRamCurrent(Context context) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            activityManager.getMemoryInfo(mi);
            return Long.toString(getTotalRAM() - (mi.availMem / 1048576L));
        }

        /**
         * Returns the total device RAM amount.
         */
        static String getRamTotal(Context context) {
            return Long.toString(getTotalRAM());
        }

        /**
         * Returns the current device disk space.
         */
        @TargetApi(18)
        static String getDiskCurrent() {
            if(android.os.Build.VERSION.SDK_INT < 18 ) {
                StatFs statFs = new StatFs(Environment.getRootDirectory().getAbsolutePath());
                long   total  = ((long)statFs.getBlockCount() * (long)statFs.getBlockSize());
                long   free   = ((long)statFs.getAvailableBlocks() * (long)statFs.getBlockSize());
                return Long.toString((total - free)/ 1048576L);
            }
            else{
                StatFs statFs = new StatFs(Environment.getRootDirectory().getAbsolutePath());
                long   total  = (statFs.getBlockCountLong() * statFs.getBlockSizeLong());
                long   free   = (statFs.getAvailableBlocksLong() * statFs.getBlockSizeLong());
                return Long.toString((total - free) / 1048576L);
            }
        }

        /**
         * Returns the current device disk space.
         */
        @TargetApi(18)
        static String getDiskTotal() {
            if(android.os.Build.VERSION.SDK_INT < 18 ) {
                StatFs statFs = new StatFs(Environment.getRootDirectory().getAbsolutePath());
                long   total  = ((long)statFs.getBlockCount() * (long)statFs.getBlockSize());
                return Long.toString(total/ 1048576L);
            }
            else{
                StatFs statFs = new StatFs(Environment.getRootDirectory().getAbsolutePath());
                long   total  = (statFs.getBlockCountLong() * statFs.getBlockSizeLong());
                return Long.toString(total/ 1048576L);
            }
        }

        /**
         * Returns the current device battery level.
         */
        static String getBatteryLevel(Context context) {
            try {
                Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if(batteryIntent != null) {
                    int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

                    // Error checking that probably isn't needed but I added just in case.
                    if (level > -1 && scale > 0) {
                        return Float.toString(((float) level / (float) scale) * 100.0f);
                    }
                }
            }
            catch(Exception e){
                android.util.Log.i("Countly", "Can't get batter level");
            }

            return null;
        }

        /**
         * Returns the current device orientation.
         */
        static String getOrientation(Context context) {
            int orientation = context.getResources().getConfiguration().orientation;
            switch(orientation)
            {
                case  Configuration.ORIENTATION_LANDSCAPE:
                    return "Landscape";
                case Configuration.ORIENTATION_PORTRAIT:
                    return "Portrait";
                case Configuration.ORIENTATION_SQUARE:
                    return "Square";
                case Configuration.ORIENTATION_UNDEFINED:
                    return "Unknown";
                default:
                    return null;
            }
        }

        /**
         * Checks if device is rooted.
         */
        static String isRooted() {
            String[] paths = { "/sbin/su", "/system/bin/su", "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
                    "/system/bin/failsafe/su", "/data/local/su" };
            for (String path : paths) {
                if (new File(path).exists()) return "true";
            }
            return "false";
        }

        /**
         * Checks if device is online.
         */
        static String isOnline(Context context) {
            try {
                ConnectivityManager conMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                if (conMgr != null && conMgr.getActiveNetworkInfo() != null
                        && conMgr.getActiveNetworkInfo().isAvailable()
                        && conMgr.getActiveNetworkInfo().isConnected()) {

                    return "true";
                }
                return "false";
            }
            catch(Exception e){
                Log.w("Countly", "Got exception determining connectivity", e);
            }
            return null;
        }

        /**
         * Checks if device is muted.
         */
        static String isMuted(Context context) {
            AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            switch( audio.getRingerMode() ){
                case AudioManager.RINGER_MODE_SILENT:
                    return "true";
                case AudioManager.RINGER_MODE_VIBRATE:
                    return "true";
                default:
                    return "false";
            }
        }

        /**
         * Utility method to fill JSONObject with supplied objects for supplied keys.
         * Fills json only with non-null and non-empty key/value pairs.
         * @param json JSONObject to fill
         * @param objects varargs of this kind: key1, value1, key2, value2, ...
         */
        static void fillJSONIfValuesNotEmpty(final JSONObject json, final String ... objects) {
            try {
                if (objects.length > 0 && objects.length % 2 == 0) {
                    for (int i = 0; i < objects.length; i += 2) {
                        final String key = objects[i];
                        final String value = objects[i + 1];
                        if (value != null && value.length() > 0) {
                            json.put(key, value);
                        }
                    }
                }
            } catch (JSONException ignored) {
                // shouldn't ever happen when putting String objects into a JSONObject,
                // it can only happen when putting NaN or INFINITE doubles or floats into it
            }
        }
    }


    @Test
    public void testAll() {
        Assert.assertEquals(DeviceInfo.getOS(), Device.getOS());
        Assert.assertEquals(DeviceInfo.getOSVersion(), Device.getOSVersion());
        Assert.assertEquals(DeviceInfo.getDevice(), Device.getDevice());
        Assert.assertEquals(DeviceInfo.getResolution(ctx.getContext()), Device.getResolution(ctx.getContext()));
        Assert.assertEquals(DeviceInfo.getDensity(ctx.getContext()), Device.getDensity(ctx.getContext()));
        Assert.assertEquals(DeviceInfo.getCarrier(ctx.getContext()), Device.getCarrier(ctx.getContext()));
        Assert.assertEquals(DeviceInfo.getTimezoneOffset(), Device.getTimezoneOffset());
        Assert.assertEquals(DeviceInfo.getLocale(), Device.getLocale());
        Assert.assertEquals(DeviceInfo.getAppVersion(ctx.getContext()), Device.getAppVersion(ctx.getContext()));
        Assert.assertEquals(DeviceInfo.getStore(ctx.getContext()), Device.getStore(ctx.getContext()));
        Assert.assertEquals((Long)CrashDetails.getTotalRAM(), Device.getRAMTotal());
        Assert.assertEquals(CrashDetails.getManufacturer(), Device.getManufacturer());
        Assert.assertEquals(CrashDetails.getCpu(), Device.getCpu());
        Assert.assertEquals(CrashDetails.getOpenGL(ctx.getContext()), String.valueOf(Device.getOpenGL(ctx.getContext())));
        Assert.assertTrue(ensureSimilar(Long.valueOf(CrashDetails.getRamCurrent(ctx.getContext())), Device.getRAMAvailable(ctx.getContext())));
//        Assert.assertEquals(CrashDetails.getRamCurrent(ctx.getContext()), String.valueOf(Device.getRAMAvailable(ctx.getContext())));
        Assert.assertTrue(ensureSimilar(Long.valueOf(CrashDetails.getDiskTotal()), Device.getDiskTotal()));
        Assert.assertTrue(ensureSimilar(Long.valueOf(CrashDetails.getDiskCurrent()), Device.getDiskAvailable()));
//        Assert.assertEquals(CrashDetails.getDiskTotal(), String.valueOf(Device.getDiskTotal()));
//        Assert.assertEquals(CrashDetails.getDiskCurrent(), String.valueOf(Device.getDiskAvailable()));
        Assert.assertEquals(CrashDetails.getBatteryLevel(ctx.getContext()), String.valueOf(Device.getBatteryLevel(ctx.getContext())));
        Assert.assertEquals(CrashDetails.getOrientation(ctx.getContext()), Device.getOrientation(ctx.getContext()));
        Assert.assertEquals(CrashDetails.isRooted(), String.valueOf(Device.isRooted()));
        Assert.assertEquals(String.valueOf(CrashDetails.isOnline(ctx.getContext())), String.valueOf(Device.isOnline(ctx.getContext())));
        Assert.assertEquals(String.valueOf(CrashDetails.isMuted(ctx.getContext())), String.valueOf(Device.isMuted(ctx.getContext())));
    }

    private boolean ensureSimilar(Long a, Long b) {
        return a == null ? b == null : Math.abs(a - b) / Math.abs(a) < Math.abs(a) * 0.05;
    }
}
