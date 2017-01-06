package ly.count.android.sdk.internal;


import ly.count.android.sdk.Config;

/**
 * Logging module. Exposes static functions for simplicity, thus can be used only from some point
 * in time when {@link Config} is created and {@link Module}s are up.
 */

public class Log extends ModuleBase {
    private static String TAG = "Countly";
    private static Log instance;

    private int level;
    private boolean testMode;

    @Override
    public void init(InternalConfig config) {
        instance = this;

        // let it be specific int and not index for visibility
        switch (config.getLoggingLevel()){
            case DEBUG:
                level = 0;
                break;
            case INFO:
                level = 1;
                break;
            case WARN:
                level = 2;
                break;
            case ERROR:
                level = 3;
                break;
            case OFF:
                level = -1;
                break;
        }

        testMode = config.isTestModeEnabled();
        TAG = config.getLoggingTag();
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
        if (instance.level >= 0) {
            android.util.Log.d(TAG, string, t);
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
        if (instance.level >= 1) {
            android.util.Log.i(TAG, string, t);
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
        if (instance.level >= 2) {
            android.util.Log.w(TAG, string, t);
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
        if (instance.level >= 3) {
            android.util.Log.e(TAG, string, t);
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
        android.util.Log.wtf(TAG, string);
        if (instance.level >= 3 && instance.testMode) {
            throw new IllegalStateException(string);
        }
    }

    /**
     * {@link ly.count.android.sdk.Config.LoggingLevel#ERROR} (Android wtf) level logging which throws an
     * exception when {@link Config#testMode} is enabled.
     *
     * @param string string to log
     * @param t exception to log along with {@code string}
     */
    public static void wtf(String string, Throwable t) {
        android.util.Log.wtf(TAG, string, t);
        if (instance.level >= 3 && instance.testMode) {
            throw new IllegalStateException(string, t);
        }
    }
}
