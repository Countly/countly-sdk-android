/*
Copyright (c) 2012, 2013, 2014 Countly

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/
package ly.count.android.sdk;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.util.AbstractMap;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

/**
 * This class provides several static methods to retrieve information about
 * the current device and operating environment.
 */
class DeviceInfo {
    private final static int startTime = UtilsTime.currentTimestampSeconds();
    private boolean inBackground = true;
    private static long totalMemory = 0;

    MetricProvider mp;
    private final MetricProvider mpOverride;

    public DeviceInfo(MetricProvider mpOverride) {
        this.mpOverride = mpOverride != null ? mpOverride : new MetricProvider() {};

        mp = new MetricProvider() {
            @NonNull
            @Override public String getOS() {
                String ov = DeviceInfo.this.mpOverride.getOS();
                if (ov != null) return ov;
                return "Android";
            }

            @NonNull
            @Override
            public String getOSVersion() {
                String ov = DeviceInfo.this.mpOverride.getOSVersion();
                if (ov != null) return ov;
                return android.os.Build.VERSION.RELEASE;
            }

            @NonNull
            @Override
            public String getDevice() {
                String ov = DeviceInfo.this.mpOverride.getDevice();
                if (ov != null) return ov;
                return android.os.Build.MODEL;
            }

            @NonNull
            @Override
            public String getManufacturer() {
                String ov = DeviceInfo.this.mpOverride.getManufacturer();
                if (ov != null) return ov;
                return Build.MANUFACTURER;
            }

            @NonNull
            @Override
            public String getResolution(@NonNull final Context context) {
                String ov = DeviceInfo.this.mpOverride.getResolution(context);
                if (ov != null) return ov;
                String resolution = "";
                try {
                    final DisplayMetrics metrics = getDisplayMetrics(context);
                    resolution = metrics.widthPixels + "x" + metrics.heightPixels;
                } catch (Throwable t) {
                    Countly.sharedInstance().L.i("[DeviceInfo] Device resolution cannot be determined");
                }
                return resolution;
            }

            @NonNull
            @Override
            public DisplayMetrics getDisplayMetrics(@NonNull final Context context) {
                DisplayMetrics ov = DeviceInfo.this.mpOverride.getDisplayMetrics(context);
                if (ov != null) return ov;
                return UtilsDevice.getDisplayMetrics(context);
            }

            @NonNull
            @Override
            public String getDensity(@NonNull final Context context) {
                String ov = DeviceInfo.this.mpOverride.getDensity(context);
                if (ov != null) return ov;
                String densityStr;
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
                    case DisplayMetrics.DENSITY_280:
                    case DisplayMetrics.DENSITY_300:
                    case DisplayMetrics.DENSITY_XHIGH:
                        densityStr = "XHDPI";
                        break;
                    case DisplayMetrics.DENSITY_340:
                    case DisplayMetrics.DENSITY_360:
                    case DisplayMetrics.DENSITY_400:
                    case DisplayMetrics.DENSITY_420:
                    case DisplayMetrics.DENSITY_XXHIGH:
                        densityStr = "XXHDPI";
                        break;
                    case DisplayMetrics.DENSITY_560:
                    case DisplayMetrics.DENSITY_XXXHIGH:
                        densityStr = "XXXHDPI";
                        break;
                    default:
                        densityStr = "other";
                        break;
                }
                return densityStr;
            }

            @NonNull
            @Override
            public String getCarrier(@NonNull final Context context) {
                String ov = DeviceInfo.this.mpOverride.getCarrier(context);
                if (ov != null) return ov;
                String carrier = "";
                final TelephonyManager manager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                if (manager != null) {
                    carrier = manager.getNetworkOperatorName();
                }
                if (carrier == null || carrier.length() == 0) {
                    carrier = "";
                    Countly.sharedInstance().L.i("[DeviceInfo] No carrier found");
                }
                if (carrier.equals("--")) {
                    carrier = "";
                }
                return carrier;
            }

            @Override
            public String getTimezoneOffset() {
                String ov = DeviceInfo.this.mpOverride.getTimezoneOffset();
                if (ov != null) return ov;
                return Integer.toString(TimeZone.getDefault().getOffset(new Date().getTime()) / 60_000);
            }

            @NonNull
            @Override
            public String getLocale() {
                String ov = DeviceInfo.this.mpOverride.getLocale();
                if (ov != null) return ov;
                final Locale locale = Locale.getDefault();
                return locale.getLanguage() + "_" + locale.getCountry();
            }

            @NonNull
            @Override
            public String getAppVersion(@NonNull final Context context) {
                String ov = DeviceInfo.this.mpOverride.getAppVersion(context);
                if (ov != null) return ov;
                String result = Countly.DEFAULT_APP_VERSION;
                try {
                    String tmpVersion = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
                    if (tmpVersion != null) {
                        result = tmpVersion;
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    Countly.sharedInstance().L.i("[DeviceInfo] No app version found");
                }
                return result;
            }

            @NonNull
            @Override
            public String getStore(@NonNull final Context context) {
                String ov = DeviceInfo.this.mpOverride.getStore(context);
                if (ov != null) return ov;
                String result = "";
                try {
                    result = context.getPackageManager().getInstallerPackageName(context.getPackageName());
                } catch (Exception e) {
                    Countly.sharedInstance().L.d("[DeviceInfo, getStore] Can't get Installer package ");
                }
                if (result == null || result.length() == 0) {
                    result = "";
                    Countly.sharedInstance().L.d("[DeviceInfo, getStore] No store found");
                }
                return result;
            }

            @NonNull
            @Override
            public String getDeviceType(@NonNull final Context context) {
                String ov = DeviceInfo.this.mpOverride.getDeviceType(context);
                if (ov != null) return ov;
                if (Utils.isDeviceTv(context)) {
                    return "smarttv";
                }
                if (Utils.isDeviceTablet(context)) {
                    return "tablet";
                }
                return "mobile";
            }

            @Override
            public String getTotalRAM() {
                String ov = DeviceInfo.this.mpOverride.getTotalRAM();
                if (ov != null) return ov;
                return Long.toString(getTotalRAMInternal());
            }

            @NonNull
            @Override
            public String getRamCurrent(Context context) {
                String ov = DeviceInfo.this.mpOverride.getRamCurrent(context);
                if (ov != null) return ov;
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                activityManager.getMemoryInfo(mi);
                return Long.toString(getTotalRAMInternal() - (mi.availMem / 1_048_576L));
            }

            @NonNull
            @Override
            public String getRamTotal() {
                String ov = DeviceInfo.this.mpOverride.getRamTotal();
                if (ov != null) return ov;
                return Long.toString(getTotalRAMInternal());
            }

            @NonNull
            @Override
            public String getCpu() {
                String ov = DeviceInfo.this.mpOverride.getCpu();
                if (ov != null) return ov;
                return Build.SUPPORTED_ABIS[0];
            }

            @NonNull
            @Override
            public String getOpenGL(Context context) {
                String ov = DeviceInfo.this.mpOverride.getOpenGL(context);
                if (ov != null) return ov;
                PackageManager packageManager = context.getPackageManager();
                FeatureInfo[] featureInfos = packageManager.getSystemAvailableFeatures();
                if (featureInfos != null && featureInfos.length > 0) {
                    for (FeatureInfo featureInfo : featureInfos) {
                        if (featureInfo.name == null) {
                            if (featureInfo.reqGlEsVersion != FeatureInfo.GL_ES_VERSION_UNDEFINED) {
                                return Integer.toString((featureInfo.reqGlEsVersion & 0xffff0000) >> 16);
                            } else {
                                return "1";
                            }
                        }
                    }
                }
                return "1";
            }

            @NonNull
            public Map.Entry<String, String> getDiskSpaces(Context context) {
                Map.Entry<String, String> ov = DeviceInfo.this.mpOverride.getDiskSpaces(context);
                if (ov != null) return ov;
                long totalBytes = 0;
                long usedBytes = 0;

                StorageManager sm = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
                if (sm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        for (android.os.storage.StorageVolume sv : sm.getStorageVolumes()) {
                            if (sv.isPrimary()) {
                                StorageStatsManager stm = (StorageStatsManager) context.getSystemService(Context.STORAGE_STATS_SERVICE);
                                totalBytes += stm.getTotalBytes(StorageManager.UUID_DEFAULT);
                                usedBytes += stm.getTotalBytes(StorageManager.UUID_DEFAULT) - stm.getFreeBytes(StorageManager.UUID_DEFAULT);
                            }
                        }
                    } catch (Exception e) {
                        Countly.sharedInstance().L.w("[DeviceInfo] getDiskSpaces, Got exception while trying to get all volumes storage", e);
                    }
                } else {
                    try {
                        File path = Environment.getDataDirectory(); // /data
                        StatFs statFs = new StatFs(path.getAbsolutePath());

                        long blockSize, totalBlocks, availableBlocks;

                        blockSize = statFs.getBlockSizeLong();
                        totalBlocks = statFs.getBlockCountLong();
                        availableBlocks = statFs.getAvailableBlocksLong();

                        totalBytes = totalBlocks * blockSize;
                        long freeBytes = availableBlocks * blockSize;
                        usedBytes = totalBytes - freeBytes;
                    } catch (Exception e) {
                        Countly.sharedInstance().L.w("[DeviceInfo] getDiskSpaces, Got exception while trying to get all volumes storage", e);
                    }
                }

                long totalMb = totalBytes / 1024 / 1024;
                long usedMb = usedBytes / 1024 / 1024;

                Countly.sharedInstance().L.d("[DeviceInfo] getDiskSpaces, totalSpaceInMB:[" + totalMb + "], usedSpaceInMB:[" + usedMb + "]");
                return new AbstractMap.SimpleEntry<>(Long.toString(totalMb), Long.toString(usedMb));
            }

            @Nullable
            @Override
            public String getBatteryLevel(Context context) {
                String ov = DeviceInfo.this.mpOverride.getBatteryLevel(context);
                if (ov != null) return ov;
                try {
                    Intent batteryIntent;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED), null, null, Context.RECEIVER_NOT_EXPORTED);
                    } else {
                        batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                    }
                    if (batteryIntent != null) {
                        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                        if (level > -1 && scale > 0) {
                            return Float.toString(((float) level / (float) scale) * 100.0f);
                        }
                    }
                } catch (Exception e) {
                    Countly.sharedInstance().L.i("Can't get battery level");
                }
                return null;
            }

            @Nullable
            @Override
            public String getOrientation(Context context) {
                String ov = DeviceInfo.this.mpOverride.getOrientation(context);
                if (ov != null) return ov;
                int orientation = context.getResources().getConfiguration().orientation;
                switch (orientation) {
                    case Configuration.ORIENTATION_LANDSCAPE:
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

            @NonNull
            @Override
            public String isRooted() {
                String ov = DeviceInfo.this.mpOverride.isRooted();
                if (ov != null) return ov;
                String[] paths = {
                    "/sbin/su", "/system/bin/su", "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
                    "/system/bin/failsafe/su", "/data/local/su"
                };
                for (String path : paths) {
                    if (new File(path).exists()) return "true";
                }
                return "false";
            }

            @SuppressLint("MissingPermission")
            @Nullable
            @Override
            public String isOnline(Context context) {
                String ov = DeviceInfo.this.mpOverride.isOnline(context);
                if (ov != null) return ov;
                try {
                    ConnectivityManager conMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                    if (conMgr != null && conMgr.getActiveNetworkInfo() != null
                        && conMgr.getActiveNetworkInfo().isAvailable()
                        && conMgr.getActiveNetworkInfo().isConnected()) {

                        return "true";
                    }
                    return "false";
                } catch (Exception e) {
                    Countly.sharedInstance().L.w("isOnline, Got exception determining netwprl connectivity", e);
                }
                return null;
            }

            @NonNull
            @Override
            public String isMuted(Context context) {
                String ov = DeviceInfo.this.mpOverride.isMuted(context);
                if (ov != null) return ov;
                try {
                    AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                    switch (audio.getRingerMode()) {
                        case AudioManager.RINGER_MODE_SILENT:
                        case AudioManager.RINGER_MODE_VIBRATE:
                            return "true";
                        default:
                            return "false";
                    }
                } catch (Throwable thr) {
                    return "false";
                }
            }

            @Override
            public String hasHinge(Context context) {
                String ov = DeviceInfo.this.mpOverride.hasHinge(context);
                if (ov != null) return ov;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE) + "";
                }
                return "false";
            }

            @Override public String getRunningTime() {
                String ov = DeviceInfo.this.mpOverride.getRunningTime();
                if (ov != null) return ov;
                return Integer.toString(UtilsTime.currentTimestampSeconds() - startTime);
            }
        };
    }

    private long getTotalRAMInternal() {
        if (totalMemory == 0) {
            RandomAccessFile reader = null;
            String load;
            try {
                reader = new RandomAccessFile("/proc/meminfo", "r");
                load = reader.readLine();

                Pattern p = Pattern.compile("(\\d+)");
                Matcher m = p.matcher(load);
                String value = "";
                while (m.find()) {
                    value = m.group(1);
                }
                try {
                    if (value != null) {
                        totalMemory = Long.parseLong(value) / 1024;
                    } else {
                        totalMemory = 0;
                    }
                } catch (NumberFormatException ex) {
                    totalMemory = 0;
                }
            } catch (IOException ex) {
                try {
                    if (reader != null) {
                        reader.close();
                    }
                } catch (IOException exc) {
                    exc.printStackTrace();
                }
                ex.printStackTrace();
            } finally {
                try {
                    if (reader != null) {
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
     * Returns the common metrics that would be shared with session, remote config and crash metrics
     * If metric override is provided, it will check for specific keys and override them
     *
     * @param context
     * @param metricOverride
     * @return
     */
    @NonNull
    Map<String, Object> getCommonMetrics(@NonNull final Context context, @Nullable final Map<String, String> metricOverride, @NonNull ModuleLog L) {
        final Map<String, Object> map = new ConcurrentHashMap<>();

        putIfNotNullAndNotEmpty(map, "_device", mp.getDevice());
        putIfNotNullAndNotEmpty(map, "_os", mp.getOS());
        putIfNotNullAndNotEmpty(map, "_os_version", mp.getOSVersion());
        putIfNotNullAndNotEmpty(map, "_resolution", mp.getResolution(context));
        putIfNotNullAndNotEmpty(map, "_app_version", mp.getAppVersion(context));
        putIfNotNullAndNotEmpty(map, "_manufacturer", mp.getManufacturer());
        putIfNotNullAndNotEmpty(map, "_has_hinge", mp.hasHinge(context));

        if (metricOverride != null) {
            try {

                if (metricOverride.containsKey("_device")) {
                    map.put("_device", metricOverride.get("_device"));
                }
                if (metricOverride.containsKey("_os")) {
                    map.put("_os", metricOverride.get("_os"));
                }
                if (metricOverride.containsKey("_os_version")) {
                    map.put("_os_version", metricOverride.get("_os_version"));
                }
                if (metricOverride.containsKey("_resolution")) {
                    map.put("_resolution", metricOverride.get("_resolution"));
                }
                if (metricOverride.containsKey("_app_version")) {
                    map.put("_app_version", metricOverride.get("_app_version"));
                }
                if (metricOverride.containsKey("_manufacturer")) {
                    map.put("_manufacturer", metricOverride.get("_manufacturer"));
                }
                if (metricOverride.containsKey("_has_hinge")) {
                    map.put("_has_hinge", metricOverride.get("_has_hinge"));
                }
            } catch (Exception e) {
                L.e("[DeviceInfo] getCommonMetrics, SDK encountered failure while trying to apply metric override, " + e);
            }
        }

        return map;
    }

    private void putIfNotNullAndNotEmpty(@NonNull Map<String, Object> metrics, String key, String value) {
        if (value != null && !value.isEmpty()) {
            metrics.put(key, value);
        }
    }

    /**
     * Returns url encoded metrics that would be used for "begin_session" requests and remote config
     *
     * @param context
     * @param metricOverride
     * @return
     */
    @NonNull
    String getMetrics(@NonNull final Context context, @Nullable final Map<String, String> metricOverride, @NonNull ModuleLog L) {
        //we set the override to null because all of the entries will be overwritten anyway
        Map<String, Object> metrics = getCommonMetrics(context, null, L);

        putIfNotNullAndNotEmpty(metrics, "_carrier", mp.getCarrier(context));
        putIfNotNullAndNotEmpty(metrics, "_density", mp.getDensity(context));
        putIfNotNullAndNotEmpty(metrics, "_locale", mp.getLocale());
        putIfNotNullAndNotEmpty(metrics, "_store", mp.getStore(context));
        putIfNotNullAndNotEmpty(metrics, "_device_type", mp.getDeviceType(context));

        if (metricOverride != null) {
            for (String k : metricOverride.keySet()) {
                if (k == null || k.isEmpty()) {
                    L.w("[DeviceInfo] getMetrics, Provided metric override key can't be null or empty");
                    continue;
                }

                String overrideValue = metricOverride.get(k);

                if (overrideValue == null) {
                    L.w("[DeviceInfo] getMetrics, Provided metric override value can't be null, key:[" + k + "]");
                    continue;
                }

                metrics.put(k, overrideValue);
            }
        }

        String result = new JSONObject(metrics).toString();

        try {
            result = java.net.URLEncoder.encode(result, "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            // should never happen because Android guarantees UTF-8 support
            Countly.sharedInstance().L.e("[getMetrics] encode failed, [" + ex + "]");
        }

        return result;
    }

    @NonNull
    String getMetricsHealthCheck(@NonNull final Context context, @Nullable final Map<String, String> metricOverride) {
        Map<String, Object> metrics = new ConcurrentHashMap<>();

        String appVersion = mp.getAppVersion(context);

        if (metricOverride != null) {
            if (metricOverride.containsKey("_app_version")) {
                appVersion = metricOverride.get("_app_version");
            }
        }

        metrics.put("_app_version", appVersion);

        String result = new JSONObject(metrics).toString();

        try {
            result = java.net.URLEncoder.encode(result, "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            // should never happen because Android guarantees UTF-8 support
            Countly.sharedInstance().L.e("[getMetrics] encode failed, [" + ex + "]");
        }

        return result;
    }

    /**
     * Returns a JSON object containing the device crash report
     */
    @NonNull
    JSONObject getCrashDataJSON(@NonNull CrashData crashData, final boolean isNativeCrash) {
        Map<String, Object> crashDataMap = crashData.getCrashMetrics();

        //setting this first so the followup are not picked up as "dev changes" in the change field
        crashDataMap.put("_ob", crashData.getChangedFieldsAsInt());

        putIfNotNullAndNotEmpty(crashDataMap, "_error", crashData.getStackTrace());
        putIfNotNullAndNotEmpty(crashDataMap, "_nonfatal", Boolean.toString(!crashData.getFatal()));

        if (!isNativeCrash) {
            String breadcrumbs = crashData.getBreadcrumbsAsString();
            if (!breadcrumbs.isEmpty()) {
                crashDataMap.put("_logs", breadcrumbs);
            }
        }

        if (!crashData.getCrashSegmentation().isEmpty()) {
            crashDataMap.put("_custom", crashData.getCrashSegmentation());
        }

        return new JSONObject(crashDataMap);
    }

    @NonNull
    Map<String, Object> getCrashMetrics(@NonNull final Context context, boolean isNativeCrash, @Nullable final Map<String, String> metricOverride, @NonNull ModuleLog L) {
        Map<String, Object> metrics = getCommonMetrics(context, metricOverride, L);

        Map.Entry<String, String> storageMb = mp.getDiskSpaces(context);

        putIfNotNullAndNotEmpty(metrics, "_cpu", mp.getCpu());
        putIfNotNullAndNotEmpty(metrics, "_opengl", mp.getOpenGL(context));
        putIfNotNullAndNotEmpty(metrics, "_root", mp.isRooted());
        putIfNotNullAndNotEmpty(metrics, "_ram_total", mp.getRamTotal());
        putIfNotNullAndNotEmpty(metrics, "_disk_total", storageMb.getKey());

        if (!isNativeCrash) {
            //if is not a native crash
            putIfNotNullAndNotEmpty(metrics, "_ram_current", mp.getRamCurrent(context));
            putIfNotNullAndNotEmpty(metrics, "_disk_current", storageMb.getValue());
            putIfNotNullAndNotEmpty(metrics, "_bat", mp.getBatteryLevel(context));
            putIfNotNullAndNotEmpty(metrics, "_run", mp.getRunningTime());
            putIfNotNullAndNotEmpty(metrics, "_orientation", mp.getOrientation(context));
            putIfNotNullAndNotEmpty(metrics, "_online", mp.isOnline(context));
            putIfNotNullAndNotEmpty(metrics, "_muted", mp.isMuted(context));
            putIfNotNullAndNotEmpty(metrics, "_background", isInBackground());
        } else {
            //if is a native crash
            metrics.put("_native_cpp", true);
        }

        return metrics;
    }

    @NonNull
    public String getAppVersionWithOverride(@NonNull final Context context, @Nullable final Map<String, String> metricOverride) {
        String appVersion = mp.getAppVersion(context);

        if (metricOverride != null && metricOverride.containsKey("_app_version")) {
            String overrideVersion = metricOverride.get("_app_version");

            if (overrideVersion != null) {
                appVersion = overrideVersion;
            }
        }

        return appVersion;
    }

    /**
     * Notify when app is in foreground
     */
    void inForeground() {
        inBackground = false;
    }

    /**
     * Notify when app is in background
     */
    void inBackground() {
        inBackground = true;
    }

    /**
     * Returns app background state
     */
    String isInBackground() {
        return Boolean.toString(inBackground);
    }
}
