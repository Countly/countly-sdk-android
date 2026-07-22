package ly.count.android.sdk;

import android.util.Log;

public class ModuleLog {
    public interface LogCallback {
        void LogHappened(String logMessage, ModuleLog.LogLevel logLevel);
    }

    public enum LogLevel {Verbose, Debug, Info, Warning, Error}

    LogCallback logListener = null;

    HealthTracker healthTracker;

    int countWarnings = 0;
    int countErrors = 0;

    // Per-instance logging state. In a multi-instance setup every Countly object owns its own
    // ModuleLog, so console output honors that instance's own config instead of the singleton's.
    // loggingEnabled is mirrored from the owning Countly (see Countly#setLoggingEnabled); tag is set
    // once per named instance in Countly.instance(name) (named instances get "Countly-<name>", the
    // default keeps the plain "Countly" tag) so a named instance's logcat output is attributable.
    boolean loggingEnabled = false;
    String tag = Countly.TAG;

    void SetListener(LogCallback logListener) {
        this.logListener = logListener;
    }

    void setLoggingEnabled(boolean loggingEnabled) {
        this.loggingEnabled = loggingEnabled;
    }

    void setTag(String tag) {
        this.tag = tag;
    }

    void trackWarning() {
        if (healthTracker == null) {
            countWarnings++;
        } else {
            healthTracker.logWarning();
        }
    }

    void trackError() {
        if (healthTracker == null) {
            countErrors++;
        } else {
            healthTracker.logError();
        }
    }

    void setHealthChecker(HealthTracker healthTracker) {
        v("[ModuleLog] Setting healthTracker W:" + countWarnings + " E:" + countErrors);
        this.healthTracker = healthTracker;

        if (healthTracker == null) {
            return;
        }

        for (int a = 0; a < countErrors; a++) {
            healthTracker.logError();
        }

        for (int a = 0; a < countWarnings; a++) {
            healthTracker.logWarning();
        }

        countWarnings = 0;
        countErrors = 0;
    }

    public void v(String msg) {
        if (!logEnabled()) {
            return;
        }
        if (loggingEnabled) {
            Log.v(tag, msg);
        }
        informListener(msg, null, LogLevel.Verbose);
    }

    public void d(String msg) {
        if (!logEnabled()) {
            return;
        }
        if (loggingEnabled) {
            Log.d(tag, msg);
        }
        informListener(msg, null, LogLevel.Debug);
    }

    public void i(String msg) {
        if (!logEnabled()) {
            return;
        }
        if (loggingEnabled) {
            Log.i(tag, msg);
        }
        informListener(msg, null, LogLevel.Info);
    }

    public void w(String msg) {
        w(msg, null);
    }

    public void w(String msg, Throwable t) {
        trackWarning();
        if (!logEnabled()) {
            return;
        }
        if (loggingEnabled) {
            Log.w(tag, msg);
        }
        informListener(msg, null, LogLevel.Warning);
    }

    public void e(String msg) {
        e(msg, null);
    }

    public void e(String msg, Throwable t) {
        trackError();
        if (!logEnabled()) {
            return;
        }
        if (loggingEnabled) {
            Log.e(tag, msg, t);
        }
        informListener(msg, t, LogLevel.Error);
    }

    public boolean logEnabled() {
        return logListener != null || loggingEnabled;
    }

    private void informListener(String msg, final Throwable t, final LogLevel level) {
        try {
            if (msg == null) {
                msg = "";
            }
            if (t != null) {
                msg += Log.getStackTraceString(t);
            }

            if (logListener != null) {
                logListener.LogHappened(msg, level);
            }
        } catch (Exception ex) {
            Log.e(tag, "[ModuleLog] Failed to inform listener [" + ex.toString() + "]");
        }
    }
}
