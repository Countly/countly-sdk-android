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

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * This class provides several static methods to retrieve information about
 * the current device and operating environment.
 */
class DeviceInfo {
    /**
     * Returns the display name of the current operating system.
     */
    @SuppressWarnings("SameReturnValue")
    static String getOS() {
        return "Android";
    }

    /**
     * Returns the current operating system version as a displayable string.
     */
    @SuppressWarnings("SameReturnValue")
    static String getOSVersion() {
        return android.os.Build.VERSION.RELEASE;
    }

    /**
     * Returns the current device model.
     */
    @SuppressWarnings("SameReturnValue")
    static String getDevice() {
        return android.os.Build.MODEL;
    }

    @SuppressWarnings("SameReturnValue")
    static String getManufacturer() {
        return Build.MANUFACTURER;
    }

    /**
     * Returns the non-scaled pixel resolution of the current default display being used by the
     * WindowManager in the specified context.
     *
     * @param context context to use to retrieve the current WindowManager
     * @return a string in the format "WxH", or the empty string "" if resolution cannot be determined
     */
    static String getResolution(final Context context) {
        // user reported NPE in this method; that means either getSystemService or getDefaultDisplay
        // were returning null, even though the documentation doesn't say they should do so; so now
        // we catch Throwable and return empty string if that happens
        String resolution = "";
        try {
            final WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            final Display display = wm.getDefaultDisplay();
            final DisplayMetrics metrics = new DisplayMetrics();
            display.getMetrics(metrics);
            resolution = metrics.widthPixels + "x" + metrics.heightPixels;
        } catch (Throwable t) {
            Countly.sharedInstance().L.i("[DeviceInfo] Device resolution cannot be determined");
        }
        return resolution;
    }

    /**
     * Maps the current display density to a string constant.
     *
     * @param context context to use to retrieve the current display metrics
     * @return a string constant representing the current display density, or the
     * empty string if the density is unknown
     */
    static String getDensity(final Context context) {
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

    /**
     * Returns the display name of the current network operator from the
     * TelephonyManager from the specified context.
     *
     * @param context context to use to retrieve the TelephonyManager from
     * @return the display name of the current network operator, or the empty
     * string if it cannot be accessed or determined
     */
    static String getCarrier(final Context context) {
        String carrier = "";
        final TelephonyManager manager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (manager != null) {
            carrier = manager.getNetworkOperatorName();
        }
        if (carrier == null || carrier.length() == 0) {
            carrier = "";
            Countly.sharedInstance().L.i("[DeviceInfo] No carrier found");
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
    static String getAppVersion(final Context context) {
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

    /**
     * Returns the package name of the app that installed this app
     */
    static String getStore(final Context context) {
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

    /**
     * Returns what kind of device this is. The potential values are:
     * ["console", "mobile", "tablet", "smarttv", "wearable", "embedded", "desktop"]
     * Currently the Android SDK differentiates between ["mobile", "tablet", "smarttv"]
     */
    static String getDeviceType(final Context context) {
        if (Utils.isDeviceTv(context)) {
            return "smarttv";
        }

        if (Utils.isDeviceTablet(context)) {
            return "tablet";
        }

        return "mobile";
    }

    /**
     * Returns a URL-encoded JSON string containing the device metrics
     * to be associated with a begin session event.
     * See the following link for more info:
     * https://count.ly/resources/reference/server-api
     */
    static String getMetrics(final Context context, final Map<String, String> metricOverride) {
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
            "_manufacturer", getManufacturer(),
            "_device_type", getDeviceType(context));

        //override metric values
        if (metricOverride != null) {
            for (String k : metricOverride.keySet()) {
                if (k == null || k.length() == 0) {
                    Countly.sharedInstance().L.w("Provided metric override key can't be null or empty");
                    continue;
                }

                String overrideValue = metricOverride.get(k);

                if (overrideValue == null) {
                    Countly.sharedInstance().L.w("Provided metric override value can't be null, key:[" + k + "]");
                    continue;
                }

                try {
                    json.put(k, overrideValue);
                } catch (Exception ex) {
                    Countly.sharedInstance().L.e("Could not set metric override, [" + ex + "]");
                }
            }
        }

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
