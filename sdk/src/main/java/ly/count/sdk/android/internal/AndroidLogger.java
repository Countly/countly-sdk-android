package ly.count.sdk.android.internal;

import ly.count.sdk.internal.Log;

class AndroidLogger implements Log.Logger {
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
