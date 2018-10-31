package ly.count.sdk.internal;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Utility core class
 */

public class UtilsCore {
    public static final String UTF8 = "UTF-8";
    public static final String CRLF = "\r\n";
    public static final char[] BASE_16 = "0123456789ABCDEF".toCharArray();

    /**
     * Joins objects with a separator
     * @param objects objects to join
     * @param separator separator to use
     * @return resulting string
     */
    public static <T> String join(Collection<T> objects, String separator) {
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
     * URLDecoder wrapper to remove try-catch
     * @param str string to decode
     * @return url-decoded {@code str}
     */
    public static String urldecode(String str) {
        try {
            return URLDecoder.decode(str, UTF8);
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    /**
     * Get fields declared by class and its superclasses filtering test-related which
     * contain $ in their name
     *
     * @param cls class to check
     * @return list of declared fields
     */
    public static List<Field> reflectiveGetDeclaredFields(Class<?> cls) {
        return reflectiveGetDeclaredFields(new ArrayList<Field>(), cls);
    }

    public static List<Field> reflectiveGetDeclaredFields(List<Field> list, Class<?> cls) {
        List<Field> curr = new ArrayList<>(Arrays.asList(cls.getDeclaredFields()));
        for (int i = 0; i < curr.size(); i++) {
            if (curr.get(i).getName().contains("$")) {
                curr.remove(i);
                i--;
            }
        }
        list.addAll(curr);
        if (cls.getSuperclass() != null) {
            reflectiveGetDeclaredFields(list, cls.getSuperclass());
        }
        return list;
    }

    public static Field findField(Class cls, String name) throws NoSuchFieldException {
        try {
            return cls.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            if (cls.getSuperclass() == null) {
                throw e;
            } else {
                return findField(cls.getSuperclass(), name);
            }
        }
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
        if (string == null) {
            return false;
        } else if (part == null) {
            return false;
        } else {
            return string.contains(part);
        }
    }
}
