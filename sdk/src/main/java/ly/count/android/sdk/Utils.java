package ly.count.android.sdk;

import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.util.Base64;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.json.JSONException;
import org.json.JSONObject;

import static android.content.Context.UI_MODE_SERVICE;

public class Utils {
    private static final ExecutorService bg = Executors.newSingleThreadExecutor();

    public static Future<?> runInBackground(Runnable runnable) {
        return bg.submit(runnable);
    }

    public static <T> Future<T> runInBackground(Callable<T> runnable) {
        return bg.submit(runnable);
    }

    /**
     * Joins objects with a separator
     *
     * @param objects objects to join
     * @param separator separator to use
     * @return resulting string
     */
    static <T> String join(final Collection<T> objects, @NonNull final String separator) {
        StringBuilder sb = new StringBuilder();
        Iterator<T> iter = objects.iterator();
        while (iter.hasNext()) {
            sb.append(iter.next());
            if (iter.hasNext()) {
                sb.append(separator);
            }
        }
        return sb.toString();
    }

    /**
     * Joins all the strings in the specified collection into a single string with the specified delimiter.
     * Used in countlyStore
     */
    static String joinCountlyStore(final Collection<String> collection, final String delimiter) {
        final StringBuilder builder = new StringBuilder();

        int i = 0;
        for (String s : collection) {
            builder.append(s);
            if (++i < collection.size()) {
                builder.append(delimiter);
            }
        }

        return builder.toString();
    }

    /**
     * StringUtils.isEmpty replacement.
     *
     * @param str string to check
     * @return true if null or empty string, false otherwise
     */
    public static boolean isEmpty(String str) {
        return str == null || "".equals(str);
    }

    /**
     * StringUtils.isNotEmpty replacement.
     *
     * @param str string to check
     * @return false if null or empty string, true otherwise
     */
    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    /**
     * Returns true if the version you are checking is at or below the build version
     *
     * @param version
     * @return
     */
    @androidx.annotation.ChecksSdkIntAtLeast(parameter = 0)
    public static boolean API(int version) {
        return Build.VERSION.SDK_INT >= version;
    }

    /**
     * Read stream into a byte array
     *
     * @param stream input to read
     * @return stream contents or {@code null} in case of error
     */
    public static byte[] readStream(InputStream stream) {
        if (stream == null) {
            return null;
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[1024];
            int len = 0;
            while ((len = stream.read(buffer)) != -1) {
                bytes.write(buffer, 0, len);
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            Countly.sharedInstance().L.e("Couldn't read stream: " + e);
            return null;
        } finally {
            try {
                bytes.close();
                stream.close();
            } catch (Throwable ignored) {
            }
        }
    }

    static String inputStreamToString(InputStream stream) {
        BufferedReader br = new BufferedReader(new InputStreamReader(stream));

        StringBuilder sbRes = new StringBuilder();

        while (true) {
            String streamLine;
            try {
                streamLine = br.readLine();
            } catch (IOException e) {
                Countly.sharedInstance().L.e("", e);
                break;
            }

            if (streamLine == null) {
                break;
            }

            if (sbRes.length() > 0) {
                //if it's not empty then there has been a previous line
                sbRes.append("\n");
            }

            sbRes.append(streamLine);
        }

        return sbRes.toString();
    }

    /**
     * Creates a crypto-safe SHA-256 hashed random value
     *
     * @return returns a random string value
     */
    public static String safeRandomVal() {
        long timestamp = System.currentTimeMillis();
        SecureRandom random = new SecureRandom();
        byte[] value = new byte[6];
        random.nextBytes(value);
        String b64Value = Base64.encodeToString(value, Base64.NO_WRAP);
        return b64Value + timestamp;
    }

    /**
     * Removes unsupported data types
     *
     * @param data
     * @return returns true if any entry had been removed
     */
    static boolean removeUnsupportedDataTypes(Map<String, Object> data) {
        if (data == null) {
            return false;
        }

        boolean removed = false;

        for (Iterator<Map.Entry<String, Object>> it = data.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Object> entry = it.next();
            String key = entry.getKey();
            Object value = entry.getValue();

            if (key == null || key.isEmpty() || !(value instanceof String || value instanceof Integer || value instanceof Double || value instanceof Boolean)) {
                //found unsupported data type or null key or value, removing
                it.remove();
                removed = true;
            }
        }

        if (removed) {
            Countly.sharedInstance().L.w("[Utils] Unsupported data types were removed from provided segmentation");
        }

        return removed;
    }

    /**
     * Used to remove reserved keys from segmentation map
     *
     * @param segmentation
     * @param reservedKeys
     * @param messagePrefix
     * @param L
     */
    static void removeReservedKeysFromSegmentation(@Nullable Map<String, Object> segmentation, @NonNull String[] reservedKeys, @NonNull String messagePrefix, @NonNull ModuleLog L) {
        if (segmentation == null) {
            return;
        }

        for (String rKey : reservedKeys) {
            if (segmentation.containsKey(rKey)) {
                L.w(messagePrefix + " provided segmentation contains protected key [" + rKey + "]");
                segmentation.remove(rKey);
            }
        }
    }

    /**
     * Used for quickly sorting segments into their respective data type
     *
     * @param allSegm
     * @param segmStr
     * @param segmInt
     * @param segmDouble
     * @param segmBoolean
     */
    protected static synchronized void fillInSegmentation(Map<String, Object> allSegm, Map<String, String> segmStr, Map<String, Integer> segmInt, Map<String, Double> segmDouble, Map<String, Boolean> segmBoolean,
        Map<String, Object> reminder) {
        for (Map.Entry<String, Object> pair : allSegm.entrySet()) {
            String key = pair.getKey();
            Object value = pair.getValue();

            if (value instanceof Integer) {
                segmInt.put(key, (Integer) value);
            } else if (value instanceof Double) {
                segmDouble.put(key, (Double) value);
            } else if (value instanceof String) {
                segmStr.put(key, (String) value);
            } else if (value instanceof Boolean) {
                segmBoolean.put(key, (Boolean) value);
            } else {
                if (reminder != null) {
                    reminder.put(key, value);
                }
            }
        }
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

    //https://stackoverflow.com/a/40310535

    /**
     * Used for detecting if current device is a tablet of phone
     */
    static boolean isDeviceTablet(Context context) {
        if (context == null) {
            return false;
        }

        return (context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    /**
     * Used for detecting if device is a tv
     *
     * @return
     */
    @SuppressWarnings("RedundantIfStatement")
    static boolean isDeviceTv(Context context) {
        if (context == null) {
            return false;
        }

        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(UI_MODE_SERVICE);

        if (uiModeManager == null) {
            return false;
        }

        if (uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Checks and transforms the provided Object if it does not
     * comply with the key count limit.
     *
     * @param maxCount Int @NonNull - max number of keys allowed
     * @param L ModuleLog @NonNull - Logger function
     * @param messagePrefix String @NonNull - name of the module this function was called
     * @param segmentation Map<String, Object> @Nullable- segmentation that will be checked
     */
    static void truncateSegmentationValues(@Nullable final Map<String, Object> segmentation, final int maxCount, @NonNull final String messagePrefix, final @NonNull ModuleLog L) {
        if (segmentation == null) {
            return;
        }

        Iterator<Map.Entry<String, Object>> iterator = segmentation.entrySet().iterator();
        while (iterator.hasNext()) {
            if (segmentation.size() > maxCount) {
                Map.Entry<String, Object> value = iterator.next();
                String key = value.getKey();
                L.w(messagePrefix + ", Value exceeded the maximum segmentation count key:[" + key + "]");
                iterator.remove();
            } else {
                break;
            }
        }
    }

    /**
     * This function checks if a request is older than an accepted duration.
     * As the expected duration you should use the developer provided 'dropAgeHours'
     *
     * @param request request string to check
     * @param dropAgeHours oldness threshold (in hours)
     * @return true if old, false if not
     */
    public static boolean isRequestTooOld(@NonNull final String request, @NonNull final int dropAgeHours, @NonNull final String messagePrefix, final @NonNull ModuleLog L) {
        if (dropAgeHours <= 0) {
            L.d(messagePrefix + " isRequestTooOld, No request drop age set. Request will bypass age checks");
            return false;
        }

        // starting index
        int timestampStartIndex = request.indexOf("&timestamp=");

        if (timestampStartIndex == -1) {
            L.w(messagePrefix + " isRequestTooOld, No timestamp in request");
            return false;
        }

        try {
            // starting index +11 gets to the end of the tag and then +13 to get timestamp
            long requestTimestampMs = Long.parseLong(request.substring(timestampStartIndex + 11, timestampStartIndex + 24));

            // calculate the threshold timestamp by subtracting dropAgeHours from the current time
            long thresholdTimestampMs = UtilsTime.currentTimestampMs() - (dropAgeHours * 3600000L); // 1 hour = 3600000 milliseconds

            // check if the request's timestamp is older than the threshold
            boolean result = requestTimestampMs < thresholdTimestampMs;

            if (result) {
                long timeGapMs = thresholdTimestampMs - requestTimestampMs;
                String message = formatTimeDifference(timeGapMs);

                L.v(messagePrefix + " isRequestTooOld, This request is " + message + " older than acceptable time frame");
            }

            return result;
        } catch (NumberFormatException e) {
            L.w(messagePrefix + " isRequestTooOld, Timestamp is not long");
            return false;
        }
    }

    /**
     * For a given milliseconds this returns a String message that gives the closest coherent timeframe back
     *
     * @param differenceMs - long milliseconds
     * @return String message
     */
    public static String formatTimeDifference(final long differenceMs) {
        long seconds = differenceMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        long months = days / 30;

        if (months > 0) {
            long remainingDays = days % 30;
            return months + " month(s) and " + remainingDays + " day(s)";
        } else if (days > 0) {
            long remainingHours = hours % 24;
            return days + " day(s) and " + remainingHours + " hour(s)";
        } else if (hours > 0) {
            return hours + " hour(s)";
        } else if (minutes > 0) {
            return minutes + " minute(s)";
        } else if (seconds > 0) {
            return seconds + " second(s)";
        } else {
            return differenceMs + " millisecond(s)";
        }
    }
}
