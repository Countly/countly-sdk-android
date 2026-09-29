package ly.count.consumer;

import ly.count.android.sdk.Countly;

/**
 * Compile-time proof that the published SDK can be used from an app.
 */
public final class Probe {
    private Probe() {
    }

    /**
     * Returns the SDK's default instance.
     */
    public static Countly sdk() {
        return Countly.sharedInstance();
    }
}
