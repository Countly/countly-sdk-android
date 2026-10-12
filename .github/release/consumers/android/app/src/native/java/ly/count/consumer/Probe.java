package ly.count.consumer;

import android.content.Context;
import ly.count.android.sdknative.CountlyNative;

/**
 * Compile-time proof that the published native crash module can be used from an app.
 */
public final class Probe {
    private Probe() {
    }

    /**
     * Starts native crash reporting.
     */
    public static boolean start(Context context) {
        return CountlyNative.initNative(context);
    }
}
