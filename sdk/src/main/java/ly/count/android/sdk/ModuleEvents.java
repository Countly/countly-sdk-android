package ly.count.android.sdk;

import java.util.HashMap;
import java.util.Map;
import ly.count.android.sdk.messaging.ModulePush;

public class ModuleEvents extends ModuleBase implements EventProvider {
    static final Map<String, Event> timedEvents = new HashMap<>();

    //interface for SDK users
    final Events eventsInterface;

    EventQueueProvider eventQueueProvider;

    ModuleEvents(Countly cly, CountlyConfig config) {
        super(cly, config);
        L.v("[ModuleEvents] Initialising");

        eventProvider = this;
        config.eventProvider = this;
        eventQueueProvider = config.eventQueueProvider;

        eventsInterface = new Events();
    }

    void checkCachedPushData(CountlyStore cs) {
        L.d("[ModuleEvents] Starting cache call");

        String[] cachedData = cs.getCachedPushData();

        if (cachedData != null && cachedData[0] != null && cachedData[1] != null) {
            //found valid data cached, record it
            L.d("[ModuleEvents] Found cached push event, recording it");

            Map<String, Object> map = new HashMap<>();
            map.put(ModulePush.PUSH_EVENT_ACTION_ID_KEY, cachedData[0]);
            map.put(ModulePush.PUSH_EVENT_ACTION_INDEX_KEY, cachedData[1]);
            recordEventInternal(ModulePush.PUSH_EVENT_ACTION, map, 1, 0, 0, null);
        }

        if (cachedData != null && (cachedData[0] != null || cachedData[1] != null)) {
            //if something was recorded, clear it
            cs.clearCachedPushData();
        }
    }

    /**
     * @param key
     * @param segmentation
     * @param count
     * @param sum
     * @param dur
     * @param instant
     */
    public void recordEventInternal(final String key, final Map<String, Object> segmentation, final int count, final double sum, final double dur, UtilsTime.Instant instant) {
        L.v("[ModuleEvents] calling 'recordEventInternal'");
        if (key == null || key.length() == 0) {
            throw new IllegalArgumentException("Valid Countly event key is required");
        }
        if (count < 1) {
            throw new IllegalArgumentException("Countly event count should be greater than zero");
        }

        L.d("[ModuleEvents] Recording event with key: [" + key + "]");

        if (!_cly.isInitialized()) {
            throw new IllegalStateException("Countly.sharedInstance().init must be called before recordEvent");
        }

        if (segmentation != null) {
            Utils.removeUnsupportedDataTypes(segmentation);
        }

        //record the current event timestamps
        //if a past event is recorded, instant value will not be null
        if (instant == null) {
            instant = UtilsTime.getCurrentInstant();
        }

        final long timestamp = instant.timestampMs;
        final int hour = instant.hour;
        final int dow = instant.dow;

        switch (key) {
            case ModuleFeedback.NPS_EVENT_KEY:
            case ModuleFeedback.SURVEY_EVENT_KEY:
                if (consentProvider.getConsent(Countly.CountlyFeatureNames.feedback)) {
                    eventQueueProvider.recordEventToEventQueue(key, segmentation, count, sum, dur, timestamp, hour, dow);
                    _cly.moduleRequestQueue.sendEventsIfNeeded(true);
                }
                break;
            case ModuleRatings.STAR_RATING_EVENT_KEY:
                if (consentProvider.getConsent(Countly.CountlyFeatureNames.starRating)) {
                    eventQueueProvider.recordEventToEventQueue(key, segmentation, count, sum, dur, timestamp, hour, dow);
                    _cly.moduleRequestQueue.sendEventsIfNeeded(false);
                }
                break;
            case ModuleViews.VIEW_EVENT_KEY:
                if (consentProvider.getConsent(Countly.CountlyFeatureNames.views)) {
                    eventQueueProvider.recordEventToEventQueue(key, segmentation, count, sum, dur, timestamp, hour, dow);
                    _cly.moduleRequestQueue.sendEventsIfNeeded(false);
                }
                break;
            case ModuleViews.ORIENTATION_EVENT_KEY:
                if (consentProvider.getConsent(Countly.CountlyFeatureNames.users)) {
                    eventQueueProvider.recordEventToEventQueue(key, segmentation, count, sum, dur, timestamp, hour, dow);
                    _cly.moduleRequestQueue.sendEventsIfNeeded(false);
                }
                break;
            default:
                if (consentProvider.getConsent(Countly.CountlyFeatureNames.events)) {
                    eventQueueProvider.recordEventToEventQueue(key, segmentation, count, sum, dur, timestamp, hour, dow);
                    _cly.moduleRequestQueue.sendEventsIfNeeded(false);
                }
                break;
        }
    }

    boolean startEventInternal(final String key) {
        if (key == null || key.length() == 0) {
            L.e("[ModuleEvents] Can't start event with a null or empty key");
            return false;
        }
        if (timedEvents.containsKey(key)) {
            return false;
        }
        L.d("[ModuleEvents] Starting event: [" + key + "]");
        timedEvents.put(key, new Event(key));
        return true;
    }

    boolean endEventInternal(final String key, final Map<String, Object> segmentation, final int count, final double sum) {
        L.d("[ModuleEvents] Ending event: [" + key + "]");

        if (key == null || key.length() == 0) {
            L.e("[ModuleEvents] Can't end event with a null or empty key");
            return false;
        }

        Event event = timedEvents.remove(key);

        if (event != null) {
            if (!consentProvider.getConsent(Countly.CountlyFeatureNames.events)) {
                return true;
            }

            if (key == null || key.length() == 0) {
                throw new IllegalArgumentException("Valid Countly event key is required");
            }
            if (count < 1) {
                throw new IllegalArgumentException("Countly event count should be greater than zero");
            }
            L.d("[ModuleEvents] Ending event: [" + key + "]");

            long currentTimestamp = UtilsTime.currentTimestampMs();
            double duration = (currentTimestamp - event.timestamp) / 1000.0;
            UtilsTime.Instant instant = new UtilsTime.Instant(event.timestamp, event.hour, event.dow);

            eventProvider.recordEventInternal(key, segmentation, count, sum, duration, instant);
            return true;
        } else {
            return false;
        }
    }

    boolean cancelEventInternal(final String key) {
        if (key == null || key.length() == 0) {
            L.e("[ModuleEvents] Can't cancel event with a null or empty key");
            return false;
        }

        Event event = timedEvents.remove(key);

        return event != null;
    }

    @Override
    void initFinished(CountlyConfig config) {
        checkCachedPushData(_cly.countlyStore);
    }

    @Override
    void halt() {
        timedEvents.clear();
    }

    public class Events {
        /**
         * Record a event with a custom timestamp.
         * Use this in case you want to record events that you have tracked
         * and stored internally
         *
         * @param key event key
         * @param segmentation custom segmentation you want to set, leave null if you don't want to add anything
         * @param timestamp unix timestamp in milliseconds of when the event occurred
         */
        public void recordPastEvent(final String key, final Map<String, Object> segmentation, long timestamp) {
            synchronized (_cly) {
                if (timestamp == 0) {
                    throw new IllegalStateException("Provided timestamp has to be greater that zero");
                }

                recordPastEvent(key, segmentation, 1, 0, 0, timestamp);
            }
        }

        /**
         * Record a event with a custom timestamp.
         * Use this in case you want to record events that you have tracked
         * and stored internally
         *
         * @param key event key
         * @param segmentation custom segmentation you want to set, leave null if you don't want to add anything
         * @param count how many of these events have occurred, default value is "1"
         * @param sum set sum if needed, default value is "0"
         * @param dur duration of the event, default value is "0"
         * @param timestamp unix timestamp in milliseconds of when the event occurred
         */
        public void recordPastEvent(final String key, final Map<String, Object> segmentation, final int count, final double sum, final double dur, long timestamp) {
            synchronized (_cly) {
                L.i("[Events] Calling recordPastEvent: [" + key + "]");

                if (timestamp == 0) {
                    throw new IllegalStateException("Provided timestamp has to be greater that zero");
                }

                UtilsTime.Instant instant = UtilsTime.Instant.get(timestamp);
                recordEventInternal(key, segmentation, count, sum, dur, instant);
            }
        }

        /**
         * Start timed event with a specified key
         *
         * @param key name of the custom event, required, must not be the empty string or null
         * @return true if no event with this key existed before and event is started, false otherwise
         */
        public boolean startEvent(final String key) {
            synchronized (_cly) {
                if (!_cly.isInitialized()) {
                    throw new IllegalStateException("Countly.sharedInstance().init must be called before startEvent");
                }

                return startEventInternal(key);
            }
        }

        /**
         * End timed event with a specified key
         *
         * @param key name of the custom event, required, must not be the empty string or null
         * @return true if event with this key has been previously started, false otherwise
         */
        public boolean endEvent(final String key) {
            synchronized (_cly) {
                return endEvent(key, null, 1, 0);
            }
        }

        /**
         * End timed event with a specified key
         *
         * @param key name of the custom event, required, must not be the empty string
         * @param segmentation segmentation dictionary to associate with the event, can be null
         * @param count count to associate with the event, should be more than zero, default value is 1
         * @param sum sum to associate with the event, default value is 0
         * @return true if event with this key has been previously started, false otherwise
         * @throws IllegalStateException if Countly SDK has not been initialized
         * @throws IllegalArgumentException if key is null or empty, count is less than 1, or if segmentation contains null or empty keys or values
         */
        public boolean endEvent(final String key, final Map<String, Object> segmentation, final int count, final double sum) {
            synchronized (_cly) {
                if (!_cly.isInitialized()) {
                    throw new IllegalStateException("Countly.sharedInstance().init must be called before endEvent");
                }

                return endEventInternal(key, segmentation, count, sum);
            }
        }

        /**
         * Cancel timed event with a specified key
         *
         * @return true if event with this key has been previously started, false otherwise
         **/
        public boolean cancelEvent(final String key) {
            synchronized (_cly) {
                L.i("[Events] Calling cancelEvent: [" + key + "]");

                return cancelEventInternal(key);
            }
        }

        /**
         * Records a custom event with no segmentation values, a count of one and a sum of zero.
         *
         * @param key name of the custom event, required, must not be the empty string
         * @throws IllegalStateException if Countly SDK has not been initialized
         * @throws IllegalArgumentException if key is null or empty
         */
        public void recordEvent(final String key) {
            synchronized (_cly) {
                recordEvent(key, null, 1, 0);
            }
        }

        /**
         * Records a custom event with no segmentation values, the specified count, and a sum of zero.
         *
         * @param key name of the custom event, required, must not be the empty string
         * @param count count to associate with the event, should be more than zero
         * @throws IllegalStateException if Countly SDK has not been initialized
         * @throws IllegalArgumentException if key is null or empty
         */
        public void recordEvent(final String key, final int count) {
            synchronized (_cly) {
                recordEvent(key, null, count, 0);
            }
        }

        /**
         * Records a custom event with no segmentation values, and the specified count and sum.
         *
         * @param key name of the custom event, required, must not be the empty string
         * @param count count to associate with the event, should be more than zero
         * @param sum sum to associate with the event
         * @throws IllegalStateException if Countly SDK has not been initialized
         * @throws IllegalArgumentException if key is null or empty
         */
        public void recordEvent(final String key, final int count, final double sum) {
            synchronized (_cly) {
                recordEvent(key, null, count, sum);
            }
        }

        /**
         * Records a custom event with the specified segmentation values and count, and a sum of zero.
         *
         * @param key name of the custom event, required, must not be the empty string
         * @param segmentation segmentation dictionary to associate with the event, can be null. Allowed values are String, int, double, boolean
         * @throws IllegalStateException if Countly SDK has not been initialized
         * @throws IllegalArgumentException if key is null or empty
         */
        public void recordEvent(final String key, final Map<String, Object> segmentation) {
            synchronized (_cly) {
                recordEvent(key, segmentation, 1, 0);
            }
        }

        /**
         * Records a custom event with the specified segmentation values and count, and a sum of zero.
         *
         * @param key name of the custom event, required, must not be the empty string
         * @param segmentation segmentation dictionary to associate with the event, can be null. Allowed values are String, int, double, boolean
         * @param count count to associate with the event, should be more than zero
         * @throws IllegalStateException if Countly SDK has not been initialized
         * @throws IllegalArgumentException if key is null or empty
         */
        public void recordEvent(final String key, final Map<String, Object> segmentation, final int count) {
            synchronized (_cly) {
                recordEvent(key, segmentation, count, 0);
            }
        }

        /**
         * Records a custom event with the specified values.
         *
         * @param key name of the custom event, required, must not be the empty string
         * @param segmentation segmentation dictionary to associate with the event, can be null. Allowed values are String, int, double, boolean
         * @param count count to associate with the event, should be more than zero
         * @param sum sum to associate with the event
         * @throws IllegalStateException if Countly SDK has not been initialized
         * @throws IllegalArgumentException if key is null or empty, count is less than 1, or if segmentation contains null or empty keys or values
         */
        public void recordEvent(final String key, final Map<String, Object> segmentation, final int count, final double sum) {
            synchronized (_cly) {
                recordEvent(key, segmentation, count, sum, 0);
            }
        }

        /**
         * Records a custom event with the specified values.
         *
         * @param key name of the custom event, required, must not be the empty string
         * @param segmentation segmentation dictionary to associate with the event, can be null
         * @param count count to associate with the event, should be more than zero
         * @param sum sum to associate with the event
         * @param dur duration of an event
         * @throws IllegalStateException if Countly SDK has not been initialized
         * @throws IllegalArgumentException if key is null or empty, count is less than 1, or if segmentation contains null or empty keys or values
         */
        public void recordEvent(final String key, final Map<String, Object> segmentation, final int count, final double sum, final double dur) {
            synchronized (_cly) {
                if (!_cly.isInitialized()) {
                    throw new IllegalStateException("Countly.sharedInstance().init must be called before recordEvent");
                }

                L.i("[Events] Calling recordEvent: [" + key + "]");

                eventProvider.recordEventInternal(key, segmentation, count, sum, dur, null);
            }
        }
    }
}
