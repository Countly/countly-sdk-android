package ly.count.android.sdk.messaging;

import android.util.Log;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import ly.count.android.sdk.Countly;
import ly.count.android.sdk.ModuleLog;

/**
 * Utility class
 */

public class UtilsMessaging {
    private static final UtilsMessaging utils = new UtilsMessaging();

    static boolean reflectiveClassExists(String cls, ModuleLog L) {
        return utils._reflectiveClassExists(cls, L);
    }

    /**
     * Check whether class exists in default class loader.
     *
     * @param cls Class name to check
     * @return true if class exists, false otherwise
     */
    public boolean _reflectiveClassExists(String cls, ModuleLog L) {
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
    static Object reflectiveCall(String className, Object instance, String methodName, ModuleLog L, Object... args) {
        return utils._reflectiveCall(className, instance, methodName, L, args);
    }

    public Object _reflectiveCall(String className, Object instance, String methodName, ModuleLog L, Object... args) {
        try {
            L.d("cls " + className + ", inst " + instance);
            className = className == null && instance != null ? instance.getClass().getName() : className;
            Class<?> cls = instance == null ? Class.forName(className) : instance.getClass();
            Class<?>[] types = null;

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
     * Reflective method call encapsulation.
     *
     * @param className class to call method in
     * @param instance instance to call on, null for static methods
     * @param methodName method name
     * @param args optional arguments to pass to that method in the form arg1, arg1class, arg2, arg2class
     * @return false in case of failure, method result otherwise
     */
    static Object reflectiveCallStrict(String className, Object instance, String methodName, ModuleLog L, Object... args) {
        return utils._reflectiveCallStrict(className, instance, methodName, L, args);
    }

    public Object _reflectiveCallStrict(String className, Object instance, String methodName, ModuleLog L, Object... arguments) {
        try {
            Log.d(Countly.TAG, "cls " + className + ", inst " + instance);
            if (arguments != null && arguments.length % 2 != 0) {
                L.w("wrong arguments passed to reflectiveCallStrict");
                return null;
            }
            className = className == null && instance != null ? instance.getClass().getName() : className;
            Class<?> cls = instance == null ? Class.forName(className) : instance.getClass();
            Class<?>[] types = arguments != null && arguments.length > 0 ? new Class[arguments.length / 2] : null;
            Object[] args = new Object[arguments != null ? arguments.length / 2 : 0];

            if (arguments != null && arguments.length > 0) {
                for (int i = 0; i < types.length; i += 2) {
                    args[i] = arguments[i * 2];
                    types[i] = (Class<?>) arguments[i * 2 + 1];
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
}