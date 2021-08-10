package ly.count.android.sdk;

import java.util.Map;

interface EventQueueProvider {
    void recordEventToEventQueue(final String key, final Map<String, String> segmentation, final Map<String, Integer> segmentationInt, final Map<String, Double> segmentationDouble, final Map<String, Boolean> segmentationBoolean, final int count, final double sum, final double dur, UtilsTime.Instant instant);
}
