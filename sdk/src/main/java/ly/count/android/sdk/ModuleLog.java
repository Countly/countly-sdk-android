package ly.count.android.sdk;

import android.util.Log;

public class ModuleLog {
    public interface LogCallback {
        void LogHappened(String logMessage, ModuleLog.LogLevel logLevel);
    }

    public enum LogLevel {Verbose, Debug, Info, Warning, Error}

    LogCallback logListener = null;

    void SetListener(LogCallback logListener) {
        this.logListener = logListener;
    }

    public void v(String msg) {
        if (!logEnabled()) {
            return;
        }
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.v(Countly.TAG, msg);
        }
        informListener(msg, null, LogLevel.Verbose);
    }

    public void d(String msg) {
        if (!logEnabled()) {
            return;
        }
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, msg);
        }
        informListener(msg, null, LogLevel.Debug);
    }

    public void i(String msg) {
        if (!logEnabled()) {
            return;
        }
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.i(Countly.TAG, msg);
        }
        informListener(msg, null, LogLevel.Info);
    }

    public void w(String msg) {
        w(msg, null);
    }

    public void w(String msg, Throwable t) {
        if (!logEnabled()) {
            return;
        }
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.w(Countly.TAG, msg);
        }
        informListener(msg, null, LogLevel.Warning);
    }

    public void e(String msg) {
        e(msg, null);
    }

    public void e(String msg, Throwable t) {
        if (!logEnabled()) {
            return;
        }
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.e(Countly.TAG, msg, t);
        }
        informListener(msg, t, LogLevel.Error);
    }

    public boolean logEnabled() {
        return (logListener != null) || Countly.sharedInstance().isLoggingEnabled();
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
            Log.e(Countly.TAG, "[ModuleLog] Failed to inform listener [" + ex.toString() + "]");
        }
    }
}
