package ly.count.android.sdk.internal;

import android.annotation.SuppressLint;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Utility class
 */

class Utils {
    /**
     * One second in nanoseconds
     */
    static final Double NS_IN_SECOND = 1000000000.0d;
     static final Double NS_IN_MS = 1000000.0d;

    static String UTF8 = "UTF-8";

    /**
     * Joins objects with a separator
     * @param objects objects to join
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
     * URLEncoder wrapper to remove try-catch
     * @param str string to encode
     * @return url-encoded {@code str}
     */
    static String urlencode(String str) {
        try {
            return URLEncoder.encode(str, UTF8);
        } catch (UnsupportedEncodingException e) {
            Log.wtf("No UTF-8 encoding?", e);
            return "";
        }
    }

    private static long lastTsMs;

    /**
     * Wraps {@link System#currentTimeMillis()} to always return different value, even within
     * same millisecond.
     *
     * @return unique time in ms
     */
    static long uniqueTimestamp() {
        long ms = System.currentTimeMillis();
        while (lastTsMs >= ms) {
            ms += 1;
        }
        lastTsMs = ms;
        return ms;
    }

    /**
     * Get current day of week
     *
     * @return day of week value, Sunday = 0, Saturday = 6
     */
    @SuppressLint("SwitchIntDef")
    static int currentDayOfWeek() {
        int day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        switch (day) {
            case Calendar.SUNDAY:
                return 0;
            case Calendar.MONDAY:
                return 1;
            case Calendar.TUESDAY:
                return 2;
            case Calendar.WEDNESDAY:
                return 3;
            case Calendar.THURSDAY:
                return 4;
            case Calendar.FRIDAY:
                return 5;
            case Calendar.SATURDAY:
                return 6;
        }
        return 0;
    }

    /**
     * Get current hour of day
     *
     * @return current hour of day
     */
    static int currentHour() {
        return Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
    }

    /**
     * Convert time in nanoseconds to milliseconds
     *
     * @param ns time in nanoseconds
     * @return ns in milliseconds
     */
    static long nsToMs(long ns) {
        return Math.round(ns / NS_IN_MS);
    }

    /**
     * Convert time in nanoseconds to seconds
     *
     * @param ns time in nanoseconds
     * @return ns in seconds
     */
    static long nsToSec(long ns) {
        return Math.round(ns / NS_IN_SECOND);
    }

    /**
     * Get fields declared by class and its superclasses filtering test-related which
     * contain $ in their name
     *
     * @param cls class to check
     * @return list of declared fields
     */
    static List<Field> reflectiveGetDeclaredFields(Class<?> cls) {
        return reflectiveGetDeclaredFields(new ArrayList<Field>(), cls);
    }

    private static List<Field> reflectiveGetDeclaredFields(List<Field> list, Class<?> cls) {
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

    static boolean reflectiveClassExists(String cls) {
        try {
            Class.forName(cls);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    static Object reflectiveCall(String className, Object instance, String methodName, Object ...args) {
        try {
            Class<?> cls = Class.forName(className);
            Class<?> types[] = null;

            if (args != null && args.length > 0) {
                types = new Class[args.length];

                for (int i = 0; i < types.length; i++) {
                    types[i] = args[i].getClass();
                }
            }
            Method method = cls.getDeclaredMethod(methodName, types);
            return method.invoke(instance, args);
        } catch (ClassNotFoundException t) {
            Log.w("Cannot call " + methodName + " of " + className, t);
            return false;
        } catch (NoSuchMethodException t) {
            Log.w("Cannot call " + methodName + " of " + className, t);
            return false;
        } catch (IllegalAccessException t) {
            Log.w("Cannot call " + methodName + " of " + className, t);
            return false;
        } catch (InvocationTargetException t) {
            Log.w("Cannot call " + methodName + " of " + className, t);
            return false;
        }
    }

    public static boolean isEmpty(String str) {
        return str == null || "".equals(str);
    }

    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }
}
