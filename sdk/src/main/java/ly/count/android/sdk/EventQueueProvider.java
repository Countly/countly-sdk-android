package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Map;

interface EventQueueProvider {
    void recordEventToEventQueue(final @NonNull String key, @Nullable final Map<String, Object> segmentation, final int count, final double sum, final double dur, final long timestamp, final int hour, final int dow, final @NonNull String eventID, final @Nullable String previousViewID, final @Nullable String currentViewId );
}
