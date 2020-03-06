package ly.count.android.sdk;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;

class ModuleEvents extends ModuleBase{
    static final Map<String, Event> timedEvents = new HashMap<>();
    static final String[] reservedSegmentationKeys = new String[] {"name", "segment", "visit", "start", "bounce", "exit", "view", "domain", "dur"};

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
            } else if(value instanceof Boolean) {
                //expected
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.v(Countly.TAG, "[checkSegmentationTypes] found Boolean with key:[" + key + "], value:[" + value + "]");
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
     * @param segmBoolean
     */
    protected static synchronized void fillInSegmentation(Map<String, Object> allSegm, Map<String, String> segmStr, Map<String, Integer> segmInt, Map<String, Double> segmDouble, Map<String, Boolean> segmBoolean,Map<String, Object> reminder) {
        for (Map.Entry<String, Object> pair : allSegm.entrySet()) {
            String key = pair.getKey();
            Object value = pair.getValue();

            if (value instanceof Integer) {
                segmInt.put(key, (Integer) value);
            } else if (value instanceof Double) {
                segmDouble.put(key, (Double) value);
            } else if (value instanceof String) {
                segmStr.put(key, (String) value);
            } else if (value instanceof Boolean) {
                segmBoolean.put(key, (Boolean) value);
            } else {
                if(reminder != null) {
                    reminder.put(key, value);
                }
            }
        }
    }

    synchronized void recordEventInternal(final String key, final Map<String, Object> segmentation, final int count, final double sum, final double dur, UtilsTime.Instant instant) {
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
        Map<String, Boolean> segmentationBoolean = null;

        if(segmentation != null) {
            segmentationString = new HashMap<>();
            segmentationInt = new HashMap<>();
            segmentationDouble = new HashMap<>();
            segmentationBoolean = new HashMap<>();
            Map<String, Object> segmentationReminder = new HashMap<>();

            fillInSegmentation(segmentation, segmentationString, segmentationInt, segmentationDouble, segmentationBoolean, segmentationReminder);

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

            Map[] maps = new Map[] {segmentationInt, segmentationDouble, segmentationBoolean};

            for(int a = 0 ; a < maps.length ; a++){
                for (Object k : maps[a].keySet()) {
                    if (k == null || ((String)k).length() == 0) {
                        throw new IllegalArgumentException("Countly event segmentation key cannot be null or empty");
                    }
                    if (maps[a].get(k) == null) {
                        throw new IllegalArgumentException("Countly event segmentation value cannot be null");
                    }
                }
            }
        }

        switch (key) {
            case ModuleRatings.STAR_RATING_EVENT_KEY:
                if (Countly.sharedInstance().getConsent(Countly.CountlyFeatureNames.starRating)) {
                    _cly.eventQueue_.recordEvent(key, segmentationString, segmentationInt, segmentationDouble, segmentationBoolean, count, sum, dur, instant);
                    _cly.sendEventsForced();
                }
                break;
            case ModuleViews.VIEW_EVENT_KEY:
                if (Countly.sharedInstance().getConsent(Countly.CountlyFeatureNames.views)) {
                    _cly.eventQueue_.recordEvent(key, segmentationString, segmentationInt, segmentationDouble, segmentationBoolean, count, sum, dur, instant);
                    _cly.sendEventsForced();
                }
                break;
            case ModuleViews.ORIENTATION_EVENT_KEY:
                if (Countly.sharedInstance().getConsent(Countly.CountlyFeatureNames.events)) {
                    _cly.eventQueue_.recordEvent(key, segmentationString, segmentationInt, segmentationDouble, segmentationBoolean, count, sum, dur, instant);
                    _cly.sendEventsIfNeeded();
                }
                break;
            default:
                if (Countly.sharedInstance().getConsent(Countly.CountlyFeatureNames.events)) {
                    _cly.eventQueue_.recordEvent(key, segmentationString, segmentationInt, segmentationDouble, segmentationBoolean, count, sum, dur, instant);
                    _cly.sendEventsIfNeeded();
                }
                break;
        }
    }

    synchronized boolean startEventInternal(final String key){
        if (key == null || key.length() == 0) {
            throw new IllegalArgumentException("Valid Countly event key is required");
        }
        if (timedEvents.containsKey(key)) {
            return false;
        }
        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleEvents] Starting event: [" + key + "]");
        }
        timedEvents.put(key, new Event(key));
        return true;
    }

    synchronized boolean endEventInternal(final String key, final Map<String, Object> segmentation, final int count, final double sum){
        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleEvents] Ending event: [" + key + "]");
        }

        Event event = timedEvents.remove(key);

        if (event != null) {
            if(!_cly.getConsent(Countly.CountlyFeatureNames.events)) {
                return true;
            }

            if (key == null || key.length() == 0) {
                throw new IllegalArgumentException("Valid Countly event key is required");
            }
            if (count < 1) {
                throw new IllegalArgumentException("Countly event count should be greater than zero");
            }
            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "Ending event: [" + key + "]");
            }

            long currentTimestamp = UtilsTime.currentTimestampMs();
            double duration = (currentTimestamp - event.timestamp) / 1000.0;
            UtilsTime.Instant instant = new UtilsTime.Instant(event.timestamp, event.hour, event.dow);

            recordEventInternal(key, segmentation, count, sum, duration, instant);
            return true;
        } else {
            return false;
        }
    }

    synchronized boolean cancelEventInternal(final String key) {
        Event event = timedEvents.remove(key);

        return event != null;
    }

    @Override
    void halt(){
        timedEvents.clear();
    }

    public class Events{
        /**
         * Record a event with a custom timestamp.
         * Use this in case you want to record events that you have tracked
         * and stored internally
         * @param key event key
         * @param segmentation custom segmentation you want to set, leave null if you don't want to add anything
         * @param timestamp unix timestamp in miliseconds of when the event occurred
         */
        public synchronized void recordPastEvent(final String key, final Map<String, Object> segmentation, long timestamp) {
            if(timestamp == 0){
                throw new IllegalStateException("Provided timestamp has to be greater that zero");
            }

            recordPastEvent(key, segmentation, 1, 0, 0, timestamp);
        }

        /**
         * Record a event with a custom timestamp.
         * Use this in case you want to record events that you have tracked
         * and stored internally
         * @param key event key
         * @param segmentation custom segmentation you want to set, leave null if you don't want to add anything
         * @param count how many of these events have occured, default value is "1"
         * @param sum set sum if needed, default value is "0"
         * @param dur duration of the event, default value is "0"
         * @param timestamp unix timestamp in miliseconds of when the event occurred
         */
        public synchronized void recordPastEvent(final String key, final Map<String, Object> segmentation, final int count, final double sum, final double dur, long timestamp) {
            if(timestamp == 0){
                throw new IllegalStateException("Provided timestamp has to be greater that zero");
            }

            UtilsTime.Instant instant = UtilsTime.Instant.get(timestamp);
            recordEventInternal(key, segmentation, count, sum, dur, instant);
        }

        /**
         * Start timed event with a specified key
         * @param key name of the custom event, required, must not be the empty string or null
         * @return true if no event with this key existed before and event is started, false otherwise
         */
        public synchronized boolean startEvent(final String key) {
            if (!_cly.isInitialized()) {
                throw new IllegalStateException("Countly.sharedInstance().init must be called before recordEvent");
            }

            return startEventInternal(key);
        }

        /**
         * End timed event with a specified key
         * @param key name of the custom event, required, must not be the empty string or null
         * @return true if event with this key has been previously started, false otherwise
         */
        public synchronized boolean endEvent(final String key) {
            return endEvent(key, null, 1, 0);
        }

        /**
         * End timed event with a specified key
         * @param key name of the custom event, required, must not be the empty string
         * @param segmentation segmentation dictionary to associate with the event, can be null
         * @param count count to associate with the event, should be more than zero
         * @param sum sum to associate with the event
         * @throws IllegalStateException if Countly SDK has not been initialized
         * @throws IllegalArgumentException if key is null or empty, count is less than 1, or if segmentation contains null or empty keys or values
         * @return true if event with this key has been previously started, false otherwise
         */
        public synchronized boolean endEvent(final String key, final Map<String, Object> segmentation, final int count, final double sum) {
            if (!_cly.isInitialized()) {
                throw new IllegalStateException("Countly.sharedInstance().init must be called before recordEvent");
            }

            if(segmentation != null){
                Utils.removeKeysFromMap(segmentation, ModuleEvents.reservedSegmentationKeys);
            }

            return endEventInternal(key, segmentation, count, sum);
        }

        /**
         * Cancel timed event with a specified key
         * @return true if event with this key has been previously started, false otherwise
         **/
        public synchronized boolean cancelEvent(final String key) {
            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[Events] Calling cancelEvent: [" + key + "]");
            }

            return cancelEventInternal(key);
        }

        /**
         * Records a custom event with no segmentation values, a count of one and a sum of zero.
         * @param key name of the custom event, required, must not be the empty string
         * @throws IllegalStateException if Countly SDK has not been initialized
         * @throws IllegalArgumentException if key is null or empty
         */
        public void recordEvent(final String key) {
            recordEvent(key, null, 1, 0);
        }

        /**
         * Records a custom event with no segmentation values, the specified count, and a sum of zero.
         * @param key name of the custom event, required, must not be the empty string
         * @param count count to associate with the event, should be more than zero
         * @throws IllegalStateException if Countly SDK has not been initialized
         * @throws IllegalArgumentException if key is null or empty
         */
        public void recordEvent(final String key, final int count) {
            recordEvent(key, null, count, 0);
        }

        /**
         * Records a custom event with no segmentation values, and the specified count and sum.
         * @param key name of the custom event, required, must not be the empty string
         * @param count count to associate with the event, should be more than zero
         * @param sum sum to associate with the event
         * @throws IllegalStateException if Countly SDK has not been initialized
         * @throws IllegalArgumentException if key is null or empty
         */
        public void recordEvent(final String key, final int count, final double sum) {
            recordEvent(key, null, count, sum);
        }

        /**
         * Records a custom event with the specified segmentation values and count, and a sum of zero.
         * @param key name of the custom event, required, must not be the empty string
         * @param segmentation segmentation dictionary to associate with the event, can be null. Allowed values are String, int, double, boolean
         * @throws IllegalStateException if Countly SDK has not been initialized
         * @throws IllegalArgumentException if key is null or empty
         */
        public void recordEvent(final String key, final Map<String, Object> segmentation) {
            recordEvent(key, segmentation, 1, 0);
        }

        /**
         * Records a custom event with the specified segmentation values and count, and a sum of zero.
         * @param key name of the custom event, required, must not be the empty string
         * @param segmentation segmentation dictionary to associate with the event, can be null. Allowed values are String, int, double, boolean
         * @param count count to associate with the event, should be more than zero
         * @throws IllegalStateException if Countly SDK has not been initialized
         * @throws IllegalArgumentException if key is null or empty
         */
        public void recordEvent(final String key, final Map<String, Object> segmentation, final int count) {
            recordEvent(key, segmentation, count, 0);
        }

        /**
         * Records a custom event with the specified values.
         * @param key name of the custom event, required, must not be the empty string
         * @param segmentation segmentation dictionary to associate with the event, can be null. Allowed values are String, int, double, boolean
         * @param count count to associate with the event, should be more than zero
         * @param sum sum to associate with the event
         * @throws IllegalStateException if Countly SDK has not been initialized
         * @throws IllegalArgumentException if key is null or empty, count is less than 1, or if segmentation contains null or empty keys or values
         */
        public synchronized void recordEvent(final String key, final Map<String, Object> segmentation, final int count, final double sum) {
            recordEvent(key, segmentation, count, sum, 0);
        }

        /**
         * Records a custom event with the specified values.
         * @param key name of the custom event, required, must not be the empty string
         * @param segmentation segmentation dictionary to associate with the event, can be null
         * @param count count to associate with the event, should be more than zero
         * @param sum sum to associate with the event
         * @param dur duration of an event
         * @throws IllegalStateException if Countly SDK has not been initialized
         * @throws IllegalArgumentException if key is null or empty, count is less than 1, or if segmentation contains null or empty keys or values
         */
        public synchronized void recordEvent(final String key, final Map<String, Object> segmentation, final int count, final double sum, final double dur) {
            if (!_cly.isInitialized()) {
                throw new IllegalStateException("Countly.sharedInstance().init must be called before recordEvent");
            }

            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[Events] Calling recordEvent: [" + key + "]");
            }

            if(segmentation != null){
                Utils.removeKeysFromMap(segmentation, ModuleEvents.reservedSegmentationKeys);
            }

            recordEventInternal(key, segmentation, count, sum, dur, null);
        }
    }
}
