package ly.count.android.sdk.messaging;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.MessageDigest;

/**
 * Utility class
 */

public class UtilsMessaging {
    private static final UtilsMessaging utils = new UtilsMessaging();

    static final String UTF8 = "UTF-8";
    static final String CRLF = "\r\n";
    static final char[] BASE_16 = "0123456789ABCDEF".toCharArray();

    static boolean reflectiveClassExists(String cls) {
        return utils._reflectiveClassExists(cls);
    }

    /**
     * Check wether class exists in default class loader.
     *
     * @param cls Class name to check
     * @return true if class exists, false otherwise
     */
    public boolean _reflectiveClassExists(String cls) {
        try {
            Class.forName(cls);
            return true;
        } catch (ClassNotFoundException e) {
            Log.d("Countly", "Class " + cls + " not found");
            return false;
        }
    }

    /**
     * Reflective method call encapsulation.
     *
     * @param className  class to call method in
     * @param instance   instance to call on, null for static methods
     * @param methodName method name
     * @param args       optional arguments to pass to that method
     * @return false in case of failure, method result otherwise
     */
    static Object reflectiveCall(String className, Object instance, String methodName, Object... args) {
        return utils._reflectiveCall(className, instance, methodName, args);
    }

    public Object _reflectiveCall(String className, Object instance, String methodName, Object... args) {
        try {
            Log.d("Countly", "cls " + className + ", inst " + instance);
            className = className == null && instance != null ? instance.getClass().getName() : className;
            Class<?> cls = instance == null ? Class.forName(className) : instance.getClass();
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
            Log.w("Countly", "Cannot call " + methodName + " of " + className, t);
            return false;
        } catch (NoSuchMethodException t) {
            Log.w("Countly", "Cannot call " + methodName + " of " + className, t);
            return false;
        } catch (IllegalAccessException t) {
            Log.w("Countly", "Cannot call " + methodName + " of " + className, t);
            return false;
        } catch (InvocationTargetException t) {
            Log.w("Countly", "Cannot call " + methodName + " of " + className, t);
            return false;
        }
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
            Log.e("Countly", "Couldn't read stream: " + e);
            return null;
        } finally {
            try {
                bytes.close();
                stream.close();
            } catch (Throwable ignored) {
            }
        }
    }
}