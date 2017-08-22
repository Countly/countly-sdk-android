package ly.count.android.sdk.internal;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Utility class
 */

public class Utils {
    private static final Utils utils = new Utils();

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
            Log.d("Class " + cls + " not found");
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

    static Boolean reflectiveSetField(Object object, String name, Object value) {
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
            Log.w("Cannot access field " + name + " of " + cls, e);
        } catch (NoSuchFieldException e) {
            Log.w("No field " + name + " in " + cls, e);
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
            Log.w("Cannot access field " + name + " of " + object.getClass(), e);
        } catch (NoSuchFieldException e) {
            Log.w("No field " + name + " in " + object.getClass(), e);
        }
        return null;
    }

    private static Field findField(Class cls, String name) throws NoSuchFieldException {
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
//                Log.w("Cannot call " + methodName + " of " + cls.getName(), t);
//                return false;
//            } catch (IllegalAccessException t) {
//                Log.w("Cannot call " + methodName + " of " + cls.getName(), t);
//                return false;
//            } catch (InvocationTargetException t) {
//                Log.w("Cannot call " + methodName + " of " + cls.getName(), t);
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
//                Log.w("Cannot access field " + fieldName + " of " + cls.getName(), e);
//            } catch (NoSuchFieldException e) {
//                Log.w("No field " + fieldName + " in " + cls.getName(), e);
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
//                Log.w("Cannot access field " + fieldName + " of " + cls.getName(), e);
//            } catch (NoSuchFieldException e) {
//                Log.w("No field " + fieldName + " in " + cls.getName(), e);
//            }
//            return false;
//        }
//    }

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
}
