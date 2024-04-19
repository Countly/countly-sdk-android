package ly.count.android.sdk;

import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.util.Base64;
import androidx.annotation.NonNull;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
     * Joins all the strings in the specified collection into a single string with the specified delimiter.
     * Used in countlyStore
     */
    static String joinCountlyStore(@NonNull final List<String> collection, @NonNull final String delimiter) {
        int targetCapacity = collection.size() == 0 ? 0 : collection.size() * collection.get(0).length();
        final StringBuilder builder = new StringBuilder(targetCapacity);

        int i = 0;
        for (String s : collection) {
            builder.append(s);
            if (++i < collection.size()) {
                builder.append(delimiter);
            }
        }

        return builder.toString();
    }

    static String joinCountlyStore_reworked(@NonNull final List<String> collection, @NonNull final String delimiter) {
        return joinCountlyStore_reworked(collection, delimiter, 0);
    }

    /**
     * Joins all the strings in the specified collection into a single string with the specified delimiter.
     * Used in countlyStore
     * todo: Add tests for this
     */
    static String joinCountlyStore_reworked(@NonNull final List<String> collection, @NonNull final String delimiter, int startingEntry) {
        int cSize = collection.size();
        int targetCapacity;
        if (cSize == 0) {
            targetCapacity = 0;
        } else if (cSize == 1) {
            targetCapacity = cSize * collection.get(0).length();
        } else {
            targetCapacity = cSize * collection.get(0).length() + (cSize * delimiter.length());
        }

        final StringBuilder builder = new StringBuilder(targetCapacity);

        int i = startingEntry;
        for (int a = startingEntry; a < cSize; a++) {
            builder.append(collection.get(a));
            if (++i < cSize) {
                builder.append(delimiter);
            }
        }

        return builder.toString();
    }

    static String joinCountlyStoreArray_reworked(@NonNull final String[] collection, @NonNull final String delimiter) {
        return joinCountlyStoreArray_reworked(collection, delimiter, 0);
    }

    /**
     * todo: Add tests for this
     *
     * @param collection
     * @param delimiter
     * @param startingEntry
     * @return
     */
    static String joinCountlyStoreArray_reworked(@NonNull final String[] collection, @NonNull final String delimiter, int startingEntry) {
        int cSize = collection.length;
        int targetCapacity;
        if (cSize == 0) {
            targetCapacity = 0;
        } else if (cSize == 1) {
            targetCapacity = cSize * collection[0].length();
        } else {
            targetCapacity = cSize * collection[0].length() + (cSize * delimiter.length());
        }

        final StringBuilder builder = new StringBuilder(targetCapacity);

        int i = startingEntry;
        for (int a = startingEntry; a < cSize; a++) {
            builder.append(collection[a]);
            if (++i < cSize) {
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
    public static boolean isNullOrEmpty(String str) {
        return str == null || "".equals(str);
    }

    /**
     * StringUtils.isNotEmpty replacement.
     *
     * @param str string to check
     * @return false if null or empty string, true otherwise
     */
    public static boolean isNotNullOrEmpty(String str) {
        return !isNullOrEmpty(str);
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
     * This function checks if a request is older than an accepted duration.
     * As the expected duration you should use the developer provided 'dropAgeHours'
     *
     * @param request request string to check
     * @param dropAgeHours oldness threshold (in hours)
     * @return true if old, false if not
     */
    public static boolean isRequestTooOld(@NonNull final String request, final int dropAgeHours, @NonNull final String messagePrefix, final @NonNull ModuleLog L) {
        if (dropAgeHours <= 0) {
            L.v(messagePrefix + " isRequestTooOld, No request drop age set. Request will bypass age checks");
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
            long thresholdTimestampMs = UtilsTime.currentTimestampMs() - (dropAgeHours * 3_600_000L); // 1 hour = 3600000 milliseconds

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

    /**
     * Given a String value, it would return a part of it and the given string without that part
     * Ex. extractValueFromString("hey&a=b&c", "a=", "&") would return ["hey&c","b"]
     *
     * @param data - string value to be precessed
     * @param startStr - the string that comes just before the thing you want to extract
     * @param endStr - the string that where you would like to en your extraction
     * @return - returns a  [data, null] if no extraction. Else String[] with the (data - startStr - extractedStr) and extracted str as second item.
     */
    static String[] extractValueFromString(@NonNull String data, @NonNull String startStr, @NonNull String endStr) {
        int startingIndex = data.indexOf(startStr);
        if (startingIndex != -1) {
            // capture first part without starting str
            String initialPart = data.substring(0, startingIndex);

            // end of starting string
            startingIndex += startStr.length();

            // check if ending string exists
            int endingStrIndex = data.indexOf(endStr, startingIndex);

            // the string we are looking for is at the end
            if (endingStrIndex == -1) {
                return new String[] { initialPart, data.substring(startingIndex) };
            }

            // if ending str exists
            return new String[] { initialPart + data.substring(endingStrIndex), data.substring(startingIndex, endingStrIndex) };
        }

        // if startStr does not exist just return empty string[]
        return new String[] { data, null };
    }
}
