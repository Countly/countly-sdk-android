package ly.count.android.sdk.internal;


import java.lang.reflect.InvocationTargetException;

import ly.count.android.sdk.Config;

/**
 * Logging module. Exposes static functions for simplicity, thus can be used only from some point
 * in time when {@link Config} is created and {@link Module}s are up.
 */

public class Log extends ModuleBase {
    interface Logger {
        void d(String message);
        void d(String message, Throwable throwable);
        void i(String message);
        void i(String message, Throwable throwable);
        void w(String message);
        void w(String message, Throwable throwable);
        void e(String message);
        void e(String message, Throwable throwable);
        void wtf(String message);
        void wtf(String message, Throwable throwable);
    }

    static class AndroidLogger implements Logger {
        private String tag;

        public AndroidLogger(String tag) {
            this.tag = tag;
        }

        @Override public void d(String message) { android.util.Log.d(tag, message); }
        @Override public void d(String message, Throwable throwable) { android.util.Log.d(tag, message, throwable); }
        @Override public void i(String message) { android.util.Log.i(tag, message); }
        @Override public void i(String message, Throwable throwable) { android.util.Log.i(tag, message, throwable); }
        @Override public void w(String message) { android.util.Log.w(tag, message); }
        @Override public void w(String message, Throwable throwable) { android.util.Log.w(tag, message, throwable); }
        @Override public void e(String message) { android.util.Log.e(tag, message); }
        @Override public void e(String message, Throwable throwable) { android.util.Log.e(tag, message, throwable); }
        @Override public void wtf(String message) { android.util.Log.wtf(tag, message); }
        @Override public void wtf(String message, Throwable throwable) { android.util.Log.wtf(tag, message, throwable); }
    }

    static class SystemLogger implements Logger {
        private String tag;

        public SystemLogger(String tag) {
            this.tag = tag;
        }

        @Override public void d(String message) { System.out.println("[DEBUG]\t" + tag + "\t" + message); }
        @Override public void d(String message, Throwable throwable) { System.out.println("[DEBUG]\t" + tag + "\t" + message + " / " + throwable); }
        @Override public void i(String message) { System.out.println("[INFO]\t" + tag + "\t" + message); }
        @Override public void i(String message, Throwable throwable) { System.out.println("[INFO]\t" + tag + "\t" + message + " / " + throwable); }
        @Override public void w(String message) { System.out.println("[WARN]\t" + tag + "\t" + message); }
        @Override public void w(String message, Throwable throwable) { System.out.println("[WARN]\t" + tag + "\t" + message + " / " + throwable); }
        @Override public void e(String message) { System.out.println("[ERROR]\t" + tag + "\t" + message); }
        @Override public void e(String message, Throwable throwable) { System.out.println("[ERROR]\t" + tag + "\t" + message + " / " + throwable); }
        @Override public void wtf(String message) { System.out.println("[WTF]\t" + tag + "\t" + message); }
        @Override public void wtf(String message, Throwable throwable) { System.out.println("[WTF]\t" + tag + "\t" + message + " / " + throwable); }
    }

    private static Log instance;
    private static Logger logger;

    private Config.LoggingLevel level;
    private boolean testMode;

    @Override
    public void init(InternalConfig config) {
        instance = this;

        // let it be specific int and not index for visibility
        level = config.getLoggingLevel();

        testMode = config.isTestModeEnabled();

        try {
            logger = config.getLoggerClass().getConstructor(new Class<?>[]{String.class}).newInstance(config.getLoggingTag());
        } catch (Throwable t) {
            if (testMode) { throw new IllegalStateException(t); }
            else {
                System.out.println("Couldn't instantiate logger" + t.getLocalizedMessage());
            }
        }
    }

    /**
     * {@link ly.count.android.sdk.Config.LoggingLevel#DEBUG} level logging
     *
     * @param string string to log
     */
    public static void d(String string) {
        d(string, null);
    }

    /**
     * {@link ly.count.android.sdk.Config.LoggingLevel#DEBUG} level logging
     *
     * @param string string to log
     * @param t exception to log along with {@code string}
     */
    public static void d(String string, Throwable t) {
        if (instance != null && logger != null && instance.level != null && instance.level.prints(Config.LoggingLevel.DEBUG)) {
            if (t == null) {
                logger.d(string);
            } else {
                logger.d(string, t);
            }
        }
    }

    /**
     * {@link ly.count.android.sdk.Config.LoggingLevel#INFO} level logging
     *
     * @param string string to log
     */
    public static void i(String string) {
        i(string, null);
    }

    /**
     * {@link ly.count.android.sdk.Config.LoggingLevel#INFO} level logging
     *
     * @param string string to log
     * @param t exception to log along with {@code string}
     */
    public static void i(String string, Throwable t) {
        if (instance != null && logger != null && instance.level != null && instance.level.prints(Config.LoggingLevel.INFO)) {
            if (t == null) {
                logger.i(string);
            } else {
                logger.i(string, t);
            }
        }
    }

    /**
     * {@link ly.count.android.sdk.Config.LoggingLevel#WARN} level logging
     *
     * @param string string to log
     */
    public static void w(String string) {
        w(string, null);
    }

    /**
     * {@link ly.count.android.sdk.Config.LoggingLevel#WARN} level logging
     *
     * @param string string to log
     * @param t exception to log along with {@code string}
     */
    public static void w(String string, Throwable t) {
        if (instance != null && logger != null && instance.level != null && instance.level.prints(Config.LoggingLevel.WARN)) {
            if (t == null) {
                logger.w(string);
            } else {
                logger.w(string, t);
            }
        }
    }

    /**
     * {@link ly.count.android.sdk.Config.LoggingLevel#ERROR} level logging
     *
     * @param string string to log
     */
    public static void e(String string) {
        e(string, null);
    }

    /**
     * {@link ly.count.android.sdk.Config.LoggingLevel#ERROR} level logging
     *
     * @param string string to log
     * @param t exception to log along with {@code string}
     */
    public static void e(String string, Throwable t) {
        if (instance != null && logger != null && instance.level != null && instance.level.prints(Config.LoggingLevel.ERROR)) {
            if (t == null) {
                logger.e(string);
            } else {
                logger.e(string, t);
            }
        }
    }

    /**
     * {@link ly.count.android.sdk.Config.LoggingLevel#ERROR} (Android wtf) level logging which throws an
     * exception when {@link Config#testMode} is enabled.
     *
     * @param string string to log
     * @throws IllegalStateException when {@link Config#testMode} is on
     */
    public static void wtf(String string) {
        wtf(string, null);
    }

    /**
     * {@link ly.count.android.sdk.Config.LoggingLevel#ERROR} (Android wtf) level logging which throws an
     * exception when {@link Config#testMode} is enabled.
     *
     * @param string string to log
     * @param t exception to log along with {@code string}
     */
    public static void wtf(String string, Throwable t) {
        if (logger != null) {
            if (t == null) {
                logger.wtf(string);
            } else {
                logger.wtf(string, t);
            }
        } else {
            if (t == null) {
                android.util.Log.wtf("Countly", string);
            } else {
                android.util.Log.wtf("Countly", string, t);
            }
        }
        if (instance != null && instance.testMode) {
            throw new IllegalStateException(string, t);
        }
    }
}
