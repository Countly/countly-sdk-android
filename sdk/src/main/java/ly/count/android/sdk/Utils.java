package ly.count.android.sdk;

import android.os.Build;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Iterator;

public class Utils {
    /**
     * Joins objects with a separator
     *
     * @param objects   objects to join
     * @param separator separator to use
     * @return resulting string
     */
    static <T> String join(Collection<T> objects, String separator) {
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

    public static boolean API(int version) {
        return Build.VERSION.SDK_INT >= version;
    }

    public static boolean isNotEqual(Object a, Object b) {
        return !isEqual(a, b);
    }

    public static boolean isEqual(Object a, Object b) {
        if (a == null || b == null || a == b) {
            return a == b;
        }
        return a.equals(b);
    }

    public static boolean contains(String string, String part) {
        if (string == null || part == null) {
            return false;
        } else {
            return string.contains(part);
        }
    }
}
