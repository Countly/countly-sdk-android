package ly.count.android.sdk;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Reads the Meta Install Referrer once. The default implementation is {@link ContentProviderMetaReferrerReader};
 * tests provide their own through {@code CountlyConfig.metaReferrerReader}.
 */
interface MetaReferrerReader {

    /**
     * What a Meta app answered: the raw (encrypted) install referrer, whether the ad was clicked
     * (is_ct, 1 click, 0 view) and Meta's time of the interaction in seconds (0 when not given).
     */
    final class Result {
        @NonNull final String installReferrer;
        final int isCt;
        final long actualTimestamp;

        Result(@NonNull String installReferrer, int isCt, long actualTimestamp) {
            this.installReferrer = installReferrer;
            this.isCt = isCt;
            this.actualTimestamp = actualTimestamp;
        }
    }

    interface Callback {
        /**
         * @param result the referrer, or null when no Meta app answered
         */
        void onResult(@Nullable Result result);
    }

    /**
     * Reads the referrer of this Facebook app id and calls the callback exactly once, on another thread.
     * It never blocks the calling thread.
     */
    void read(@NonNull Context context, @NonNull String facebookAppId, @NonNull Callback callback);
}
