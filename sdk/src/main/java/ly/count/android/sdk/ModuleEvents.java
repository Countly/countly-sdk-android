package ly.count.android.sdk;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;

import static ly.count.android.sdk.CountlyStarRating.STAR_RATING_EVENT_KEY;

public class ModuleEvents extends ModuleBase{
    //interface for SDK users
    final Events eventsInterface;

    public ModuleEvents(Countly cly, CountlyConfig config){
        super(cly);

        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleEvents] Initialising");
        }

        eventsInterface = new Events();
    }

    protected static boolean checkSegmentationTypes(Map<String, Object> segmentation){
        if (segmentation == null) {
            throw new IllegalStateException("[checkSegmentationTypes] provided segmentations can't be null!");
        }

        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "[checkSegmentationTypes] Calling checkSegmentationTypes, size:[" + segmentation.size() + "]");
        }

        for (Map.Entry<String, Object> pair : segmentation.entrySet()) {
            String key = pair.getKey();

            if(key == null || key.isEmpty()){
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.d(Countly.TAG, "[checkSegmentationTypes], provided segment with either 'null' or empty string key");
                }
                throw new IllegalStateException("provided segment with either 'null' or empty string key");
            }

            Object value = pair.getValue();

            if(value instanceof Integer){
                //expected
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.v(Countly.TAG, "[checkSegmentationTypes] found INTEGER with key:[" + key + "], value:[" + value + "]");
                }
            } else if(value instanceof Double) {
                //expected
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.v(Countly.TAG, "[checkSegmentationTypes] found DOUBLE with key:[" + key + "], value:[" + value + "]");
                }
            } else if(value instanceof String) {
                //expected
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.v(Countly.TAG, "[checkSegmentationTypes] found STRING with key:[" + key + "], value:[" + value + "]");
                }
            } else {
                //should not get here, it means that the user provided a unsupported type

                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.e(Countly.TAG, "[checkSegmentationTypes] provided unsupported segmentation type:[" + value.getClass().getCanonicalName() + "] with key:[" + key + "], returning [false]");
                }

                return false;
            }
        }

        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "[checkSegmentationTypes] returning [true]");
        }
        return true;
    }

    /**
     * Used for quickly sorting segments into their respective data type
     * @param allSegm
     * @param segmStr
     * @param segmInt
     * @param segmDouble
     */
    protected static synchronized void fillInSegmentation(Map<String, Object> allSegm, Map<String, String> segmStr, Map<String, Integer> segmInt, Map<String, Double> segmDouble, Map<String, Object> reminder) {
        for (Map.Entry<String, Object> pair : allSegm.entrySet()) {
            String key = pair.getKey();
            Object value = pair.getValue();

            if (value instanceof Integer) {
                segmInt.put(key, (Integer) value);
            } else if (value instanceof Double) {
                segmDouble.put(key, (Double) value);
            } else if (value instanceof String) {
                segmStr.put(key, (String) value);
            } else {
                if(reminder != null) {
                    reminder.put(key, value);
                }
            }
        }
    }

    synchronized void recordEventInternal(final String key, final int count, final double sum, final double dur, final Map<String, Object> segmentation, UtilsTime.Instant instant) {
        if (key == null || key.length() == 0) {
            throw new IllegalArgumentException("Valid Countly event key is required");
        }
        if (count < 1) {
            throw new IllegalArgumentException("Countly event count should be greater than zero");
        }

        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "Recording event with key: [" + key + "]");
        }

        if (!_cly.isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before recordEvent");
        }


        Map<String, String> segmentationString = null;
        Map<String, Integer> segmentationInt = null;
        Map<String, Double> segmentationDouble = null;

        if(segmentation != null) {
            segmentationString = new HashMap<>();
            segmentationInt = new HashMap<>();
            segmentationDouble = new HashMap<>();
            Map<String, Object> segmentationReminder = new HashMap<>();

            fillInSegmentation(segmentation, segmentationString, segmentationInt, segmentationDouble, segmentationReminder);

            if (segmentationReminder.size() > 0) {
                if (_cly.isLoggingEnabled()) {
                    Log.w(Countly.TAG, "Event contains events segments with unsupported types:");

                    for (String k : segmentationReminder.keySet()) {
                        if (k != null) {
                            Object obj = segmentationReminder.get(k);
                            if (obj != null){
                                Log.w(Countly.TAG, "Event segmentation key:[" + k + "], value type:[" + obj.getClass().getCanonicalName() + "]");
                            }
                        }
                    }
                }
            }

            for (String k : segmentationString.keySet()) {
                if (k == null || k.length() == 0) {
                    throw new IllegalArgumentException("Countly event segmentation key cannot be null or empty");
                }
                if (segmentationString.get(k) == null || segmentationString.get(k).length() == 0) {
                    throw new IllegalArgumentException("Countly event segmentation value cannot be null or empty");
                }
            }

            for (String k : segmentationInt.keySet()) {
                if (k == null || k.length() == 0) {
                    throw new IllegalArgumentException("Countly event segmentation key cannot be null or empty");
                }
                if (segmentationInt.get(k) == null) {
                    throw new IllegalArgumentException("Countly event segmentation value cannot be null");
                }
            }

            for (String k : segmentationDouble.keySet()) {
                if (k == null || k.length() == 0) {
                    throw new IllegalArgumentException("Countly event segmentation key cannot be null or empty");
                }
                if (segmentationDouble.get(k) == null) {
                    throw new IllegalArgumentException("Countly event segmentation value cannot be null");
                }
            }
        }

        switch (key) {
            case STAR_RATING_EVENT_KEY:
                if (Countly.sharedInstance().getConsent(Countly.CountlyFeatureNames.starRating)) {
                    _cly.eventQueue_.recordEvent(key, segmentationString, segmentationInt, segmentationDouble, count, sum, dur, instant);
                    _cly.sendEventsForced();
                }
                break;
            case ModuleViews.VIEW_EVENT_KEY:
                if (Countly.sharedInstance().getConsent(Countly.CountlyFeatureNames.views)) {
                    _cly.eventQueue_.recordEvent(key, segmentationString, segmentationInt, segmentationDouble, count, sum, dur, instant);
                    _cly.sendEventsForced();
                }
                break;
            case ModuleViews.ORIENTATION_EVENT_KEY:
                if (Countly.sharedInstance().getConsent(Countly.CountlyFeatureNames.events)) {
                    _cly.eventQueue_.recordEvent(key, segmentationString, segmentationInt, segmentationDouble, count, sum, dur, instant);
                    _cly.sendEventsIfNeeded();
                }
                break;
            default:
                if (Countly.sharedInstance().getConsent(Countly.CountlyFeatureNames.events)) {
                    _cly.eventQueue_.recordEvent(key, segmentationString, segmentationInt, segmentationDouble, count, sum, dur, instant);
                    _cly.sendEventsIfNeeded();
                }
                break;
        }
    }

    @Override
    void halt(){

    }

    public class Events{
        public synchronized void recordPastEvent(final String key, final Map<String, Object> segmentation, final int count, final double sum, final double dur, long timestamp) {
            if (!_cly.isInitialized()) {
                throw new IllegalStateException("Countly.sharedInstance().init must be called before recordPastEvent");
            }

            if(timestamp == 0){
                throw new IllegalStateException("Provided timestamp has to be greater that zero");
            }

            UtilsTime.Instant instant = UtilsTime.Instant.get(timestamp);
            recordEventInternal(key, count, sum, dur, segmentation, instant);
        }
    }
}
