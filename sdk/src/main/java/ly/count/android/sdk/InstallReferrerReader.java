package ly.count.android.sdk;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Reads the install referrer once. The default implementation is {@link PlayInstallReferrerReader};
 * tests provide their own through {@code CountlyConfig.installReferrerReader}.
 */
interface InstallReferrerReader {
    //the referrer was read; the value may be null or empty when the install had none
    int STATUS_OK = 0;
    //the store service could not answer now; try again on a later launch
    int STATUS_RETRY = 1;
    //this device or store can never answer
    int STATUS_NOT_SUPPORTED = 2;

    interface Callback {
        void onResult(int status, @Nullable String referrer);
    }

    /**
     * @return false when the reading library is not part of the app
     */
    boolean isAvailable();

    /**
     * Reads the referrer and calls the callback exactly once, possibly on another thread.
     */
    void read(@NonNull Context context, @NonNull Callback callback);
}
