package ly.count.android.sdk;

import java.util.Map;

interface EventQueueProvider {
    void recordEventToEventQueue(final String key, final Map<String, Object> segmentation, final int count, final double sum, final double dur, final long timestamp, final int hour, final int dow);
}
