package ly.count.android.sdk;

import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import androidx.annotation.NonNull;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
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
}
