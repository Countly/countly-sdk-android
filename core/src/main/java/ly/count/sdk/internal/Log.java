package ly.count.sdk.internal;

import ly.count.sdk.Config;

/**
 * Logging module. Exposes static functions for simplicity, thus can be used only from some point
 * in time when {@link Config} is created and {@link Module}s are up.
 */

public class Log extends ModuleBase {
    public interface Logger {
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

    public static class SystemLogger implements Logger {
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

    public static final class Module {
        String name;

        Module(String name) {
            this.name = name;
        }

        public void d(String message) { Log.d("[" + name + "] " + message); }
        public void d(String message, Throwable throwable) { Log.d("[" + name + "] " + message, throwable); }
        public void i(String message) { Log.i("[" + name + "] " + message); }
        public void i(String message, Throwable throwable) { Log.i("[" + name + "] " + message, throwable); }
        public void w(String message) { Log.w("[" + name + "] " + message); }
        public void w(String message, Throwable throwable) { Log.w("[" + name + "] " + message, throwable); }
        public void e(String message) { Log.e("[" + name + "] " + message); }
        public void e(String message, Throwable throwable) { Log.e("[" + name + "] " + message, throwable); }
        public void wtf(String message) { Log.wtf("[" + name + "] " + message); }
        public void wtf(String message, Throwable throwable) { Log.wtf("[" + name + "] " + message, throwable); }
    }

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

    @Override
    public Integer getFeature() {
        return CoreFeature.Logs.getIndex();
    }

    public static void deinit() {
        instance = null;
    }

    public static Module module(String name) {
        return new Module(name);
    }

    /**
     * {@link Config.LoggingLevel#DEBUG} level logging
     *
     * @param string string to log
     */
    public static void d(String string) {
        d(string, null);
    }

    /**
     * {@link Config.LoggingLevel#DEBUG} level logging
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
     * {@link Config.LoggingLevel#INFO} level logging
     *
     * @param string string to log
     */
    public static void i(String string) {
        i(string, null);
    }

    /**
     * {@link Config.LoggingLevel#INFO} level logging
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
     * {@link Config.LoggingLevel#WARN} level logging
     *
     * @param string string to log
     */
    public static void w(String string) {
        w(string, null);
    }

    /**
     * {@link Config.LoggingLevel#WARN} level logging
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
     * {@link Config.LoggingLevel#ERROR} level logging
     *
     * @param string string to log
     */
    public static void e(String string) {
        e(string, null);
    }

    /**
     * {@link Config.LoggingLevel#ERROR} level logging
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
     * {@link Config.LoggingLevel#ERROR} (Android wtf) level logging which throws an
     * exception when {@link Config#testMode} is enabled.
     *
     * @param string string to log
     * @throws IllegalStateException when {@link Config#testMode} is on
     */
    public static void wtf(String string) {
        wtf(string, null);
    }

    /**
     * {@link Config.LoggingLevel#ERROR} (Android wtf) level logging which throws an
     * exception when {@link Config#testMode} is enabled.
     *
     * @param string string to log
     * @param t exception to log along with {@code string}
     */
    public static void wtf(String string, Throwable t) {
        if (instance == null || instance.level == null || instance.level != Config.LoggingLevel.OFF) {
            if (logger != null) {
                if (t == null) {
                    logger.wtf(string);
                } else {
                    logger.wtf(string, t);
                }
            } else {
                new SystemLogger("Countly").w(string, t);
            }
        }
        if (instance != null && instance.testMode) {
            throw new IllegalStateException(string, t);
        }
    }
}
