package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Map;

interface EventProvider {
    void recordEventInternal(final @NonNull String key, final @Nullable Map<String, Object> segmentation, final int count, final double sum, final double dur, UtilsTime.Instant instant, final @Nullable String eventIdOverride);
}
