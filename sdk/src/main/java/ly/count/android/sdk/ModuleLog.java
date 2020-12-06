package ly.count.android.sdk;

import android.util.Log;

public class ModuleLog {
    public interface LogCallback {
        void LogHappened(String logMessage, ModuleLog.LogLevel logLevel);
    }

    public enum LogLevel {Verbose, Debug, Info, Warning, Error}
    LogCallback logListener = null;
    ModuleLog() {
    }

    void SetListener(LogCallback logListener) {
        this.logListener = logListener;
    }

    void v(String msg) {
        if(!logEnabled()) {
            return;
        }
        if(Countly.sharedInstance().isLoggingEnabled()) {
            Log.v(Countly.TAG, msg);
        }
        informListener(msg, LogLevel.Verbose);
    }

    void d(String msg) {
        if(!logEnabled()) {
            return;
        }
        if(Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, msg);
        }
        informListener(msg, LogLevel.Debug);
    }

    void i(String msg) {
        if(!logEnabled()) {
            return;
        }
        if(Countly.sharedInstance().isLoggingEnabled()) {
            Log.i(Countly.TAG, msg);
        }
        informListener(msg, LogLevel.Info);
    }

    void w(String msg) {
        if(!logEnabled()) {
            return;
        }
        if(Countly.sharedInstance().isLoggingEnabled()) {
            Log.w(Countly.TAG, msg);
        }
        informListener(msg, LogLevel.Warning);
    }

    void e(String msg) {
        if(!logEnabled()) {
            return;
        }
        if(Countly.sharedInstance().isLoggingEnabled()) {
            Log.e(Countly.TAG, msg);
        }
        informListener(msg, LogLevel.Error);
    }

    boolean logEnabled() {
        return (logListener != null) || Countly.sharedInstance().isLoggingEnabled();
    }

    private void informListener(String msg, LogLevel level) {
        try {
            if (logListener != null) {
                logListener.LogHappened(msg, level);
            }
        } catch (Exception ex) {
            Log.e(Countly.TAG, "[ModuleLog] Failed to inform listener [" + ex.toString() + "]");
        }
    }
}
