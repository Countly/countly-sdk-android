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
import android.annotation.TargetApi;
import android.app.ActivityManager;
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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.LinkedList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * This class provides several static methods to retrieve information about
 * the current device and operating environment for crash reporting purposes.
 */
class CrashDetails {
    private static final LinkedList<String> logs = new LinkedList<>();
    private static final int startTime = UtilsTime.currentTimestampSeconds();
    private static boolean inBackground = true;
    private static long totalMemory = 0;

    private static long getTotalRAM() {
        if (totalMemory == 0) {
            RandomAccessFile reader = null;
            String load;
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
    static void addLog(@NonNull String record, int maxBreadcrumbCount, int maxBreadcrumbLength) {
        int recordLength = record.length();
        if (recordLength > maxBreadcrumbLength) {
            Countly.sharedInstance().L.d("Breadcrumb exceeds character limit: [" + recordLength + "], reducing it to: [" + maxBreadcrumbLength + "]");
            record = record.substring(0, maxBreadcrumbLength);
        }

        logs.add(record);

        if (logs.size() > maxBreadcrumbCount) {
            Countly.sharedInstance().L.d("Breadcrumb amount limit exceeded, deleting the oldest one");
            logs.removeFirst();
        }
    }

    /**
     * Returns the collected logs.
     */
    static String getLogs() {
        StringBuilder allLogs = new StringBuilder();

        for (String s : logs) {
            allLogs.append(s).append("\n");
        }
        logs.clear();
        return allLogs.toString();
    }

    /**
     * Get custom segments json string from the provided map
     */
    static JSONObject getCustomSegmentsJson(@Nullable final Map<String, Object> customSegments) {
        if (customSegments == null || customSegments.isEmpty()) {
            return null;
        }

        JSONObject returnedSegmentation = new JSONObject();
        for (String k : customSegments.keySet()) {
            if (k != null) {
                try {
                    returnedSegmentation.put(k, customSegments.get(k));
                } catch (JSONException e) {
                    Countly.sharedInstance().L.w("[getCustomSegmentsJson] Failed to add custom segmentation to crash");
                }
            }
        }

        return returnedSegmentation;
    }

    /**
     * Returns the current device manufacturer.
     */
    @SuppressWarnings("SameReturnValue")
    static String getManufacturer() {
        return android.os.Build.MANUFACTURER;
    }

    /**
     * Returns the current device cpu.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    static String getCpu() {
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return android.os.Build.CPU_ABI;
        } else {
            return Build.SUPPORTED_ABIS[0];
        }
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
    static String getRamTotal() {
        return Long.toString(getTotalRAM());
    }

    /**
     * Returns the current device disk space.
     */
    @TargetApi(18)
    static String getDiskCurrent() {
        if (android.os.Build.VERSION.SDK_INT < 18) {
            StatFs statFs = new StatFs(Environment.getRootDirectory().getAbsolutePath());
            long total = ((long) statFs.getBlockCount() * (long) statFs.getBlockSize());
            long free = ((long) statFs.getAvailableBlocks() * (long) statFs.getBlockSize());
            return Long.toString((total - free) / 1048576L);
        } else {
            StatFs statFs = new StatFs(Environment.getRootDirectory().getAbsolutePath());
            long total = (statFs.getBlockCountLong() * statFs.getBlockSizeLong());
            long free = (statFs.getAvailableBlocksLong() * statFs.getBlockSizeLong());
            return Long.toString((total - free) / 1048576L);
        }
    }

    /**
     * Returns the current device disk space.
     */
    @TargetApi(18)
    static String getDiskTotal() {
        if (android.os.Build.VERSION.SDK_INT < 18) {
            StatFs statFs = new StatFs(Environment.getRootDirectory().getAbsolutePath());
            long total = ((long) statFs.getBlockCount() * (long) statFs.getBlockSize());
            return Long.toString(total / 1048576L);
        } else {
            StatFs statFs = new StatFs(Environment.getRootDirectory().getAbsolutePath());
            long total = (statFs.getBlockCountLong() * statFs.getBlockSizeLong());
            return Long.toString(total / 1048576L);
        }
    }

    /**
     * Returns the current device battery level.
     */
    static String getBatteryLevel(Context context) {
        try {
            Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (batteryIntent != null) {
                int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

                // Error checking that probably isn't needed but I added just in case.
                if (level > -1 && scale > 0) {
                    return Float.toString(((float) level / (float) scale) * 100.0f);
                }
            }
        } catch (Exception e) {
            Countly.sharedInstance().L.i("Can't get battery level");
        }

        return null;
    }

    /**
     * Get app's running time before crashing.
     */
    static String getRunningTime() {
        return Integer.toString(UtilsTime.currentTimestampSeconds() - startTime);
    }

    /**
     * Returns the current device orientation.
     */
    static String getOrientation(Context context) {
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

    /**
     * Checks if device is rooted.
     */
    static String isRooted() {
        String[] paths = {
            "/sbin/su", "/system/bin/su", "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
            "/system/bin/failsafe/su", "/data/local/su"
        };
        for (String path : paths) {
            if (new File(path).exists()) return "true";
        }
        return "false";
    }

    /**
     * Checks if device is online.
     */
    @SuppressLint("MissingPermission")
    static String isOnline(Context context) {
        try {
            ConnectivityManager conMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (conMgr != null && conMgr.getActiveNetworkInfo() != null
                && conMgr.getActiveNetworkInfo().isAvailable()
                && conMgr.getActiveNetworkInfo().isConnected()) {

                return "true";
            }
            return "false";
        } catch (Exception e) {
            Countly.sharedInstance().L.w("Got exception determining connectivity", e);
        }
        return null;
    }

    /**
     * Checks if device is muted.
     */
    static String isMuted(Context context) {
        try {
            AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            switch (audio.getRingerMode()) {
                case AudioManager.RINGER_MODE_SILENT:
                    // Fall-through
                case AudioManager.RINGER_MODE_VIBRATE:
                    return "true";
                default:
                    return "false";
            }
        } catch (Throwable thr) {
            return "false";
        }
    }

    /**
     * Returns a URL-encoded JSON string containing the device crash report
     * See the following link for more info:
     * http://resources.count.ly/v1.0/docs/i
     */
    static String getCrashData(final Context context, String error, Boolean nonfatal, boolean isNativeCrash, final String crashBreadcrumbs, final Map<String, Object> customCrashSegmentation) {
        final JSONObject json = new JSONObject();

        fillJSONIfValuesNotEmpty(json,
            "_error", error,
            "_nonfatal", Boolean.toString(nonfatal),
            "_device", DeviceInfo.getDevice(),
            "_os", DeviceInfo.getOS(),
            "_os_version", DeviceInfo.getOSVersion(),
            "_resolution", DeviceInfo.getResolution(context),
            "_app_version", DeviceInfo.getAppVersion(context),
            "_manufacture", getManufacturer(),
            "_cpu", getCpu(),
            "_opengl", getOpenGL(context),
            "_root", isRooted(),
            "_ram_total", getRamTotal(),
            "_disk_total", getDiskTotal()
        );

        if (!isNativeCrash) {
            //if is not a native crash
            fillJSONIfValuesNotEmpty(json,
                "_logs", crashBreadcrumbs,
                "_ram_current", getRamCurrent(context),
                "_disk_current", getDiskCurrent(),
                "_bat", getBatteryLevel(context),
                "_run", getRunningTime(),
                "_orientation", getOrientation(context),
                "_online", isOnline(context),
                "_muted", isMuted(context),
                "_background", isInBackground()
            );
        } else {
            //if is a native crash
            try {
                json.put("_native_cpp", true);
            } catch (JSONException ignored) {
            }
        }

        try {
            json.put("_custom", getCustomSegmentsJson(customCrashSegmentation));
        } catch (JSONException e) {
            //no custom segments
        }
        return json.toString();
    }

    /**
     * Utility method to fill JSONObject with supplied objects for supplied keys.
     * Fills json only with non-null and non-empty key/value pairs.
     *
     * @param json JSONObject to fill
     * @param objects varargs of this kind: key1, value1, key2, value2, ...
     */
    static void fillJSONIfValuesNotEmpty(final JSONObject json, final String... objects) {
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
