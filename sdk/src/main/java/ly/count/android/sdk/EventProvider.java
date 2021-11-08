package ly.count.android.sdk;

import java.util.Map;

interface EventProvider {
    void recordEventInternal(final String key, final Map<String, Object> segmentation, final int count, final double sum, final double dur, UtilsTime.Instant instant);
}
