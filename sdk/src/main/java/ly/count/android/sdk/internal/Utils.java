package ly.count.android.sdk.internal;

import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.security.MessageDigest;

import ly.count.sdk.internal.UtilsCore;

/**
 * Utility class
 */

public class Utils extends UtilsCore {
    protected static final Log.Module L = Log.module("Utils");

    protected static final Utils utils = new Utils();

    /**
     * URLEncoder wrapper to remove try-catch
     * @param str string to encode
     * @return url-encoded {@code str}
     */
    static String urlencode(String str) {
        try {
            return URLEncoder.encode(str, UTF8);
        } catch (UnsupportedEncodingException e) {
            L.wtf("No UTF-8 encoding?", e);
            return "";
        }
    }

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
            L.d("Class " + cls + " not found");
            return false;
        }
    }

    /**
     * Reflective method call encapsulation.
     *
     * @param className class to call method in
     * @param instance instance to call on, null for static methods
     * @param methodName method name
     * @param args optional arguments to pass to that method
     * @return false in case of failure, method result otherwise
     */
    static Object reflectiveCall(String className, Object instance, String methodName, Object ...args) {
        return utils._reflectiveCall(className, instance, methodName, args);
    }

    public Object _reflectiveCall(String className, Object instance, String methodName, Object ...args) {
        try {
            L.d("cls " + className + ", inst " + instance);
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
            L.w("Cannot call " + methodName + " of " + className, t);
            return false;
        } catch (NoSuchMethodException t) {
            L.w("Cannot call " + methodName + " of " + className, t);
            return false;
        } catch (IllegalAccessException t) {
            L.w("Cannot call " + methodName + " of " + className, t);
            return false;
        } catch (InvocationTargetException t) {
            L.w("Cannot call " + methodName + " of " + className, t);
            return false;
        }
    }

    /**
     * Reflective method call encapsulation with argument types specified explicitly before each parameter.
     *
     * @param className class to call method in
     * @param instance instance to call on, null for static methods
     * @param methodName method name
     * @param args optional arguments to pass to that method in format [arg1 class, arg1 value, arg2 class, arg2 value]
     * @return false in case of failure, method result otherwise
     */
    static Object reflectiveCallStrict(String className, Object instance, String methodName, Object ...args) {
        return utils._reflectiveCallStrict(className, instance, methodName, args);
    }

    public Object _reflectiveCallStrict(String className, Object instance, String methodName, Object ...args) {
        try {
            Class<?> cls = instance == null ? Class.forName(className) : instance.getClass();
            Class<?> types[] = args == null || args.length == 0 ? null : new Class[args.length / 2];
            Object arguments[] = args == null || args.length == 0 ? null : new Object[args.length / 2];

            if (args != null && args.length > 0) {
                for (int i = 0; i < args.length; i += 2) {
                    types[i / 2] = (Class<?>) args[i];
                    arguments[i / 2] = args[i + 1];
                }
            }
            Method method = cls.getDeclaredMethod(methodName, types);
            return method.invoke(instance, arguments);
        } catch (ClassNotFoundException t) {
            L.w("Cannot call " + methodName + " of " + className, t);
            return false;
        } catch (NoSuchMethodException t) {
            L.w("Cannot call " + methodName + " of " + className, t);
            return false;
        } catch (IllegalAccessException t) {
            L.w("Cannot call " + methodName + " of " + className, t);
            return false;
        } catch (InvocationTargetException t) {
            L.w("Cannot call " + methodName + " of " + className, t);
            return false;
        }
    }

    public static Boolean reflectiveSetField(Object object, String name, Object value) {
        return utils._reflectiveSetField(object, object.getClass(), name, value);
    }

    static Boolean reflectiveSetField(Class cls, String name, Object value) {
        return utils._reflectiveSetField(null, cls, name, value);
    }

    Boolean _reflectiveSetField(Object object, Class cls, String name, Object value) {
        try {
            Field field = findField(cls, name);
            boolean accessible = field.isAccessible();
            if (!accessible) {
                field.setAccessible(true);
            }
            field.set(object, value);
            if (!accessible) {
                field.setAccessible(false);
            }
            return true;
        } catch (IllegalAccessException e) {
            L.w("Cannot access field " + name + " of " + cls, e);
        } catch (NoSuchFieldException e) {
            L.w("No field " + name + " in " + cls, e);
        }
        return false;
    }


    static <T> T reflectiveGetField(Object object, String name) {
        return utils._reflectiveGetField(object, object.getClass(), name);
    }

    static <T> T reflectiveGetField(Class cls, String name) {
        return utils._reflectiveGetField(null, cls, name);
    }

    @SuppressWarnings("unchecked")
    <T> T _reflectiveGetField(Object object, Class cls, String name) {
        try {
            Field field = findField(cls, name);
            boolean accessible = field.isAccessible();
            if (!accessible) {
                field.setAccessible(true);
            }
            T value = (T) field.get(object);
            if (!accessible) {
                field.setAccessible(false);
            }
            return value;
        } catch (IllegalAccessException e) {
            L.w("Cannot access field " + name + " of " + object.getClass(), e);
        } catch (NoSuchFieldException e) {
            L.w("No field " + name + " in " + object.getClass(), e);
        }
        return null;
    }

//    public static final class Reflection<T> {
//        private T instance;
//        private Class<?> cls;
//
//        Reflection(T instance) {
//            this.instance = instance;
//            this.cls = instance.getClass();
//        }
//
//        Reflection(String className) throws ClassNotFoundException {
//            this.cls = Class.forName(className);
//        }
//
//        public Object call(String methodName, Object... args) {
//            try {
//                Class<?> types[] = null;
//
//                if (args != null && args.length > 0) {
//                    types = new Class[args.length];
//
//                    for (int i = 0; i < types.length; i++) {
//                        types[i] = args[i].getClass();
//                    }
//                }
//                Method method = cls.getDeclaredMethod(methodName, types);
//                return method.invoke(instance, args);
//            } catch (NoSuchMethodException t) {
//                L.w("Cannot call " + methodName + " of " + cls.getName(), t);
//                return false;
//            } catch (IllegalAccessException t) {
//                L.w("Cannot call " + methodName + " of " + cls.getName(), t);
//                return false;
//            } catch (InvocationTargetException t) {
//                L.w("Cannot call " + methodName + " of " + cls.getName(), t);
//                return false;
//            }
//        }
//
//        public Object get(String fieldName) {
//            try {
//                Field field = instance == null ? cls.getDeclaredField(fieldName): instance.getClass().getDeclaredField(fieldName);
//                boolean accessible = field.isAccessible();
//                if (!accessible) {
//                    field.setAccessible(true);
//                }
//                Object value = field.get(instance);
//                if (!accessible) {
//                    field.setAccessible(false);
//                }
//                return value;
//            } catch (IllegalAccessException e) {
//                L.w("Cannot access field " + fieldName + " of " + cls.getName(), e);
//            } catch (NoSuchFieldException e) {
//                L.w("No field " + fieldName + " in " + cls.getName(), e);
//            }
//            return null;
//        }
//
//        public Boolean set(String fieldName, Object value) {
//            try {
//                Field field = instance == null ? cls.getDeclaredField(fieldName): instance.getClass().getDeclaredField(fieldName);
//                boolean accessible = field.isAccessible();
//                if (!accessible) {
//                    field.setAccessible(true);
//                }
//                field.set(instance, value);
//                if (!accessible) {
//                    field.setAccessible(false);
//                }
//                return true;
//            } catch (IllegalAccessException e) {
//                L.w("Cannot access field " + fieldName + " of " + cls.getName(), e);
//            } catch (NoSuchFieldException e) {
//                L.w("No field " + fieldName + " in " + cls.getName(), e);
//            }
//            return false;
//        }
//    }

    public static boolean API(int version) {
        return Build.VERSION.SDK_INT >= version;
    }

    /**
     * Calculate digest (SHA-1, SHA-256, etc.) hash of the string provided
     *
     * @param digestName digest name like {@code "SHA-256"}, must be supported by Java, see {@link MessageDigest}
     * @param string string to hash
     * @return hash of the string or null in case of error
     */
    public static String digestHex(String digestName, String string) {
        try {
            MessageDigest digest = MessageDigest.getInstance(digestName);
            byte[] bytes = string.getBytes(UTF8);
            digest.update(bytes, 0, bytes.length);
            return hex(digest.digest());
        } catch (Throwable e) {
            Log.e("Cannot calculate sha1", e);
            return null;
        }
    }

    /**
     * Get hexadecimal string representation of a byte array
     *
     * @param bytes array of bytes to convert
     * @return hex string of the byte array in lower case
     */
    public static String hex(byte[] bytes) {
        char[] hexChars = new char[ bytes.length * 2 ];
        for( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[ j ] & 0xFF;
            hexChars[ j * 2 ] = BASE_16[ v >>> 4 ];
            hexChars[ j * 2 + 1 ] = BASE_16[ v & 0x0F ];
        }
        return new String(hexChars).toLowerCase();
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
            L.e("Couldn't read stream", e);
            return null;
        } finally {
            try {
                bytes.close();
                stream.close();
            } catch (Throwable ignored){}
        }
    }

}
