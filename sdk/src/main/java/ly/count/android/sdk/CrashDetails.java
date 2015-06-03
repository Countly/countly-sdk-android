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

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ConfigurationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.Locale;

/**
 * This class provides several static methods to retrieve information about
 * the current device and operating environment.
 *
 * It is important to call setDeviceID early, before logging any session or custom
 * event data.
 */
class CrashDetails {

    /**
     * Returns the current device manufacturer.
     */
    static String getManufacturer() {
        return android.os.Build.MANUFACTURER;
    }

    /**
     * Returns the current device cpu.
     */
    static String getCpu() {
        if(android.os.Build.VERSION.SDK_INT < 21 )
            return android.os.Build.CPU_ABI;
        else
            return Build.SUPPORTED_ABIS[0];
    }

    /**
     * Returns the current device openGL version.
     */
    static String getOpenGL(Context context) {
        if(android.os.Build.VERSION.SDK_INT >= 3 ) {
            final ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            final ConfigurationInfo configurationInfo = activityManager.getDeviceConfigurationInfo();
            return Integer.toString(configurationInfo.reqGlEsVersion);
        }
        return null;
    }

    /**
     * Returns the current device RAM amount.
     */
    static String getRamCurrent(Context context) {
        if(android.os.Build.VERSION.SDK_INT >= 16 ) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            activityManager.getMemoryInfo(mi);
            return Long.toString((mi.totalMem - mi.availMem) / 1048576L);
        }
        return null;
    }

    /**
     * Returns the total device RAM amount.
     */
    static String getRamTotal(Context context) {
        if(android.os.Build.VERSION.SDK_INT >= 16 ) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            activityManager.getMemoryInfo(mi);
            return Long.toString(mi.totalMem / 1048576L);
        }
        return null;
    }

    /**
     * Returns the current device disk space.
     */
    static String getDiskCurrent() {
        if(android.os.Build.VERSION.SDK_INT < 18 ) {
            StatFs statFs = new StatFs(Environment.getRootDirectory().getAbsolutePath());
            long   total  = (statFs.getBlockCount() * statFs.getBlockSize());
            long   free   = (statFs.getAvailableBlocks() * statFs.getBlockSize());
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
    static String getDiskTotal() {
        if(android.os.Build.VERSION.SDK_INT < 18 ) {
            StatFs statFs = new StatFs(Environment.getRootDirectory().getAbsolutePath());
            long   total  = (statFs.getBlockCount() * statFs.getBlockSize());
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
            int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

            // Error checking that probably isn't needed but I added just in case.
            if (level > -1 && scale > 0) {
                return Float.toString(((float) level / (float) scale) * 100.0f);
            }
        }
        catch(Exception e){
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.i(Countly.TAG, "Can't get batter level");
            }
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
        catch(Exception e){}
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
     * Returns a URL-encoded JSON string containing the device crash report
     * See the following link for more info:
     * http://resources.count.ly/v1.0/docs/i
     */
    static String getCrashData(final Context context, String error, Boolean nonfatal, String logs) {
        final JSONObject json = new JSONObject();

        fillJSONIfValuesNotEmpty(json,
                "_error", error,
                "_nonfatal", Boolean.toString(nonfatal),
                "_logs", logs,
                "_device", DeviceInfo.getDevice(),
                "_os", DeviceInfo.getOS(),
                "_os_version", DeviceInfo.getOSVersion(),
                "_resolution", DeviceInfo.getResolution(context),
                "_app_version", DeviceInfo.getAppVersion(context),
                "_manufacture", getManufacturer(),
                "_cpu", getCpu(),
                "_opengl", getOpenGL(context),
                "_ram_current", getRamCurrent(context),
                "_ram_total", getRamTotal(context),
                "_disk_current", getDiskCurrent(),
                "_disk_total", getDiskTotal(),
                "_bat", getBatteryLevel(context),
                "_orientation", getOrientation(context),
                "_root", isRooted(),
                "_online", isOnline(context),
                "_muted", isMuted(context)
                );

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
