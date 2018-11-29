package ly.count.sdk.android.internal;

import android.os.Build;

import ly.count.sdk.internal.Log;

/**
 * Utility class
 */

public class Utils extends ly.count.sdk.internal.Utils {
    protected static final Log.Module L = Log.module("Utils");

    protected static final Utils utils = new Utils();

    public static boolean API(int version) {
        return Build.VERSION.SDK_INT >= version;
    }
}
