/*
Copyright (c) 2012, 2013, 2014 Countly

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/
package ly.count.android.sdk;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * This class holds the data for a single Count.ly custom event instance.
 * It also knows how to read & write itself to the Count.ly custom event JSON syntax.
 * See the following link for more info:
 * https://count.ly/resources/reference/custom-events
 */
public class Event {

    public static class Instant {
        public final long timestamp;
        public final int hour;
        public final int dow;

        private Instant(long timestampInMillis, int hour, int dow) {
            this.timestamp = timestampInMillis;
            this.hour = hour;
            this.dow = dow;
        }

        public static Instant get(long timestampInMillis) {
            if (timestampInMillis < 0L) {
                throw new IllegalArgumentException(
                        "timestampInMillis must be greater than or equal to zero");
            }
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(timestampInMillis);
            final int hour = calendar.get(Calendar.HOUR_OF_DAY);
            // Calendar days are 1-based, Countly days are 0-based
            final int dow = calendar.get(Calendar.DAY_OF_WEEK) - 1;
            return new Event.Instant(timestampInMillis, hour, dow);
        }
    }

    public static class Builder {

        private Event event_;

        public Builder(String key) {
            reset(key);
        }

        public void reset(String key) {
            if (key == null) {
                throw new IllegalArgumentException("event key cannot be null");
            }
            event_ = new Event();
            event_.count = 1;
            event_.key = key;
        }

        public Builder putSegmentationEntry(String key, String value) {
            if (key == null || value == null) {
                throw new IllegalArgumentException("key and value must be non-null");
            }
            if (event_.segmentation == null) {
                event_.segmentation = new HashMap<>();
            }
            event_.segmentation.put(key, value);
            return this;
        }

        public Builder setSegmentation(Map<String, String> segmentation) {
            event_.segmentation = segmentation;
            return this;
        }

        public Builder putSegmentationIntEntry(String key, Integer value) {
            if (key == null || value == null) {
                throw new IllegalArgumentException("key and value must be non-null");
            }
            if (event_.segmentationInt == null) {
                event_.segmentationInt = new HashMap<>();
            }
            event_.segmentationInt.put(key, value);
            return this;
        }

        public Builder setSegmentationInt(Map<String, Integer> segmentationInt) {
            event_.segmentationInt = segmentationInt;
            return this;
        }

        public Builder putSegmentationDoubleEntry(String key, Double value) {
            if (key == null || value == null) {
                throw new IllegalArgumentException("key and value must be non-null");
            }
            if (event_.segmentationDouble == null) {
                event_.segmentationDouble = new HashMap<>();
            }
            event_.segmentationDouble.put(key, value);
            return this;
        }

        public Builder setSegmentationDouble(Map<String, Double> segmentationDouble) {
            event_.segmentationDouble = segmentationDouble;
            return this;
        }

        public Builder setCount(int count) {
            if (count < 1) {
                throw new IllegalArgumentException("count must be greater than or equal to 1");
            }
            event_.count = count;
            return this;
        }

        public Builder setSum(double sum) {
            event_.sum = sum;
            return this;
        }

        public Builder setDur(double dur) {
            if (dur < 0d) {
                throw new IllegalArgumentException(
                        "duration must be greater than or equal to zero");
            }
            event_.dur = dur;
            return this;
        }

        public Builder setInstant(Instant instant) {
            if (instant.timestamp < 0L) {
                throw new IllegalArgumentException(
                        "timestamp must be greater than or equal to zero");
            }
            if (instant.hour < 0 || instant.hour > 23) {
                throw new IllegalArgumentException("hour must be in range [0, 23]");
            }
            if (instant.dow < 0 || instant.dow > 6) {
                throw new IllegalArgumentException("dow must be in range [0, 6]");
            }
            event_.timestamp = instant.timestamp;
            event_.hour = instant.hour;
            event_.dow = instant.dow;
            return this;
        }

        public Event build() {
            return this.event_;
        }

    }

    private static final String SEGMENTATION_KEY = "segmentation";
    private static final String KEY_KEY = "key";
    private static final String COUNT_KEY = "count";
    private static final String SUM_KEY = "sum";
    private static final String DUR_KEY = "dur";
    private static final String TIMESTAMP_KEY = "timestamp";
    private static final String DAY_OF_WEEK = "dow";
    private static final String HOUR = "hour";

    public String key;
    public Map<String, String> segmentation;
    public Map<String, Integer> segmentationInt;
    public Map<String, Double> segmentationDouble;
    public int count;
    public double sum;
    public double dur;
    public long timestamp;
    public int hour;
    public int dow;

    Event () {}

    /**
     * Creates and returns a JSONObject containing the event data from this object.
     * @return a JSONObject containing the event data from this object
     */
    public JSONObject toJSON() {
        final JSONObject json = new JSONObject();

        try {
            json.put(KEY_KEY, key);
            json.put(COUNT_KEY, count);
            json.put(TIMESTAMP_KEY, timestamp);
            json.put(HOUR, hour);
            json.put(DAY_OF_WEEK, dow);

            JSONObject jobj = new JSONObject();
            if (segmentation != null) {
                for (Map.Entry<String, String> pair : segmentation.entrySet()) {
                    jobj.put(pair.getKey(), pair.getValue());
                }
            }

            if(segmentationInt != null){
                for (Map.Entry<String, Integer> pair : segmentationInt.entrySet()) {
                    jobj.put(pair.getKey(), pair.getValue());
                }
            }

            if(segmentationDouble != null){
                for (Map.Entry<String, Double> pair : segmentationDouble.entrySet()) {
                    jobj.put(pair.getKey(), pair.getValue());
                }
            }

            if(segmentation != null || segmentationInt != null || segmentationDouble != null) {
                json.put(SEGMENTATION_KEY, jobj);
            }

            // we put in the sum last, the only reason that a JSONException would be thrown
            // would be if sum is NaN or infinite, so in that case, at least we will return
            // a JSON object with the rest of the fields populated
            json.put(SUM_KEY, sum);

            if (dur > 0) {
                json.put(DUR_KEY, dur);
            }
        }
        catch (JSONException e) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.w(Countly.TAG, "Got exception converting an Event to JSON", e);
            }
        }

        return json;
    }

    /**
     * Factory method to create an Event from its JSON representation.
     * @param json JSON object to extract event data from
     * @return Event object built from the data in the JSON or null if the "key" value is not
     *         present or the empty string, or if a JSON exception occurs
     * @throws NullPointerException if JSONObject is null
     */
    public static Event fromJSON(final JSONObject json) {
        Event event = new Event();

        try {
            if (!json.isNull(KEY_KEY)) {
                event.key = json.getString(KEY_KEY);
            }
            event.count = json.optInt(COUNT_KEY);
            event.sum = json.optDouble(SUM_KEY, 0.0d);
            event.dur = json.optDouble(DUR_KEY, 0.0d);
            event.timestamp = json.optLong(TIMESTAMP_KEY);
            event.hour = json.optInt(HOUR);
            event.dow = json.optInt(DAY_OF_WEEK);

            if (!json.isNull(SEGMENTATION_KEY)) {
                JSONObject segm = json.getJSONObject(SEGMENTATION_KEY);

                final HashMap<String, String> segmentation = new HashMap<>();
                final HashMap<String, Integer> segmentationInt = new HashMap<>();
                final HashMap<String, Double> segmentationDouble = new HashMap<>();

                final Iterator nameItr = segm.keys();
                while (nameItr.hasNext()) {
                    final String key = (String) nameItr.next();
                    if (!segm.isNull(key)) {
                        Object obj = segm.opt(key);

                        if(obj instanceof Double){
                            //in case it's a double
                            segmentationDouble.put(key, segm.getDouble(key));
                        } else if(obj instanceof Integer){
                            //in case it's a integer
                            segmentationInt.put(key, segm.getInt(key));
                        } else {
                            //assume it's String
                            segmentation.put(key, segm.getString(key));
                        }
                    }
                }
                event.segmentation = segmentation;
                event.segmentationDouble = segmentationDouble;
                event.segmentationInt = segmentationInt;
            }
        }
        catch (JSONException e) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.w(Countly.TAG, "Got exception converting JSON to an Event", e);
            }
            event = null;
        }

        return (event != null && event.key != null && event.key.length() > 0) ? event : null;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || !(o instanceof Event)) {
            return false;
        }

        final Event e = (Event) o;

        return (key == null ? e.key == null : key.equals(e.key)) &&
               timestamp == e.timestamp &&
               hour == e.hour &&
               dow == e.dow &&
               (segmentation == null ? e.segmentation == null : segmentation.equals(e.segmentation));
    }

    @Override
    public int hashCode() {
        return (key != null ? key.hashCode() : 1) ^
               (segmentation != null ? segmentation.hashCode() : 1) ^
               (timestamp != 0 ? (int)timestamp : 1);
    }
}
