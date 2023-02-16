package ly.count.android.sdk;

import androidx.annotation.NonNull;

interface ViewIdProvider {
    @NonNull String getCurrentViewId();

    @NonNull String getPreviousViewId();
}
