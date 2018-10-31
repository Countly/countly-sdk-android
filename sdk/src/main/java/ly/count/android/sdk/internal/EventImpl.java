package ly.count.android.sdk.internal;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import ly.count.sdk.Event;
import ly.count.sdk.Session;
import ly.count.sdk.internal.JSONable;

/**
 * Event base class implementation
 */

class EventImpl implements Event, JSONable {
    private static final Log.Module L = Log.module("EventImpl");

    private final SessionImpl session;
    private final String key;

    private Map<String, String> segmentation;

    private int count;
    private Double sum;
    private Double duration;

    private long timestamp;
    private int hour;
    private int dow;

    /**
     * True when some validation failed in any Event method. Results in event being discarded in
     * production mode and {@link IllegalStateException} in test mode thanks to {@link Log#wtf(String, Throwable)}.
     */
    private boolean invalid = false;

    EventImpl(SessionImpl session, String key) {
        if (session == null) {
            invalid = true;
            L.wtf("Session cannot be null for an event");
        }
        if (key == null || "".equals(key)) {
            invalid = true;
            L.wtf("Event key cannot be null or empty");
        }
        this.session = session;
        this.key = key;
        this.count = 1;
        this.timestamp = Device.uniqueTimestamp();
        this.hour = Device.currentHour();
        this.dow = Device.currentDayOfWeek();
    }

    @Override
    public Session record() {
        if (session != null && !invalid) {
            invalid = true;
            return session.recordEvent(this);
        } else {
            return session;
        }
    }

    @Override
    public Session endAndRecord() {
        setDuration((Device.uniqueTimestamp() - timestamp) / 1000);
        return record();
    }

    @Override
    public Event addSegment(String key, String value) {
        if (key == null || "".equals(key)) {
            invalid = true;
            L.wtf("Segmentation key " + key + " for event " + this.key + " is empty");
            return this;
        }

        if (value == null || "".equals(value)) {
            invalid = true;
            L.wtf("Segmentation value " + value + " (" + key + ") for event " + this.key + " is empty");
            return this;
        }

        if (segmentation == null) {
            segmentation = new HashMap<>();
        }

        segmentation.put(key, value);

        return this;
    }

    @Override
    public Event addSegments(String... segmentation) {
        if (segmentation == null || segmentation.length == 0) {
            invalid = true;
            L.wtf("Segmentation varargs array is empty");
            return this;
        }

        if (segmentation.length % 2 != 0) {
            invalid = true;
            L.wtf("Segmentation varargs array length is not even");
            return this;
        }

        for (int i = 0; i < segmentation.length; i += 2) {
            addSegment(segmentation[i], segmentation[i + 1]);
        }
        return this;
    }

    @Override
    public Event setSegmentation(Map<String, String> segmentation) {
        if (segmentation == null) {
            invalid = true;
            L.wtf("Segmentation map is null");
            return this;
        }

        this.segmentation = new HashMap<>();
        for (String k : segmentation.keySet()) {
            addSegment(k, segmentation.get(k));
        }

        return this;
    }

    @Override
    public Event setCount(int count) {
        if (count <= 0) {
            invalid = true;
            L.wtf("Event " + key + " count cannot be 0 or less");
            return this;
        }
        this.count = count;
        return this;
    }

    @Override
    public Event setSum(double sum) {
        if (Double.isInfinite(sum) || Double.isNaN(sum)) {
            invalid = true;
            L.wtf("NaN infinite value cannot be event '" + key + "' sum");
        } else {
            this.sum = sum;
        }
        return this;
    }

    @Override
    public Event setDuration(double duration) {
        if (Double.isInfinite(duration) || Double.isNaN(duration) || duration < 0) {
            invalid = true;
            L.wtf("NaN, infinite or negative value cannot be event '" + key + "' duration");
        } else {
            this.duration = duration;
        }
        return this;
    }

    @Override
    public int hashCode() {
        return (key + timestamp).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof EventImpl)) {
            return false;
        }
        EventImpl event = (EventImpl)obj;
        if (timestamp != event.timestamp) {
            return false;
        }
        if (key == null || event.key == null || !key.equals(event.key)) {
            return false;
        }
        if (count != event.count) {
            return false;
        }
        if ((sum != null && !sum.equals(event.sum) || (event.sum != null && !event.sum.equals(sum)))) {
            return false;
        }
        if ((duration != null && !duration.equals(event.duration) || (event.duration != null && !event.duration.equals(duration)))) {
            return false;
        }
        if ((segmentation != null && !segmentation.equals(event.segmentation) || (event.segmentation != null && !event.segmentation.equals(segmentation)))) {
            return false;
        }
        return true;
    }

    private static final String SEGMENTATION_KEY = "segmentation";
    private static final String KEY_KEY = "key";
    private static final String COUNT_KEY = "count";
    private static final String SUM_KEY = "sum";
    private static final String DUR_KEY = "dur";
    private static final String TIMESTAMP_KEY = "timestamp";
    private static final String DAY_OF_WEEK = "dow";
    private static final String HOUR = "hour";

    /**
     * Serialize to JSON format according to Countly server requirements
     * @return JSON string
     */
    public String toJSON() {
        final JSONObject json = new JSONObject();

        try {
            json.put(KEY_KEY, key);
            json.put(COUNT_KEY, count);
            json.put(TIMESTAMP_KEY, timestamp);
            json.put(HOUR, hour);
            json.put(DAY_OF_WEEK, dow);

            if (segmentation != null) {
                json.put(SEGMENTATION_KEY, new JSONObject(segmentation));
            }

            if (sum != null) {
                json.put(SUM_KEY, sum);
            }

            if (duration != null) {
                json.put(DUR_KEY, duration);
            }
        } catch (JSONException e) {
            L.wtf("Cannot serialize event to JSON", e);
        }

        return json.toString();
    }

    /**
     * Serialize to JSON format according to Countly server requirements
     * @return JSON string
     */
    static EventImpl fromJSON(SessionImpl session, String jsonString) {
        try {
            JSONObject json = new JSONObject(jsonString);

            if (!json.has(KEY_KEY) || json.isNull(KEY_KEY)) {
                L.wtf("Bad JSON for deserialization of event: " + jsonString);
                return null;
            }
            EventImpl event = new EventImpl(session, json.getString(KEY_KEY));

            event.count = json.optInt(COUNT_KEY, 1);
            if (json.has(SUM_KEY) && !json.isNull(SUM_KEY)) {
                event.sum = json.optDouble(SUM_KEY, 0);
            }
            if (json.has(DUR_KEY) && !json.isNull(DUR_KEY)) {
                event.duration = json.optDouble(DUR_KEY, 0);
            }
            event.timestamp = json.optLong(TIMESTAMP_KEY);
            event.hour = json.optInt(HOUR);
            event.dow = json.optInt(DAY_OF_WEEK);

            if (!json.isNull(SEGMENTATION_KEY)) {
                final JSONObject segm = json.getJSONObject(SEGMENTATION_KEY);
                final HashMap<String, String> segmentation = new HashMap<String, String>(segm.length());
                final Iterator<String> nameItr = segm.keys();
                while (nameItr.hasNext()) {
                    final String key = nameItr.next();
                    if (!segm.isNull(key)) {
                        segmentation.put(key, segm.getString(key));
                    }
                }
                event.segmentation = segmentation;
            }

            return event;
        } catch (JSONException e) {
            L.wtf("Cannot deserialize event from JSON", e);
        }

        return null;
    }

    long getTimestamp() {
        return timestamp;
    }

    public String getKey() {
        return key;
    }

    public int getCount() {
        return count;
    }

    public Double getSum() {
        return sum;
    }

    public Double getDuration() {
        return duration;
    }

    public String getSegment(String key) {
        return segmentation.get(key);
    }

    public boolean isInvalid() {
        return invalid;
    }

    @Override
    public String toString() {
        return toJSON();
    }
}
