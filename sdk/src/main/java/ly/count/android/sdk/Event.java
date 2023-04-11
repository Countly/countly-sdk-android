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

import androidx.annotation.NonNull;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * This class holds the data for a single Count.ly custom event instance.
 * It also knows how to read & write itself to the Count.ly custom event JSON syntax.
 * See the following link for more info:
 * https://count.ly/resources/reference/custom-events
 */
class Event {
    protected static final String SEGMENTATION_KEY = "segmentation";
    protected static final String KEY_KEY = "key";
    protected static final String COUNT_KEY = "count";
    protected static final String SUM_KEY = "sum";
    protected static final String DUR_KEY = "dur";
    protected static final String TIMESTAMP_KEY = "timestamp";
    protected static final String DAY_OF_WEEK_KEY = "dow";
    protected static final String HOUR_KEY = "hour";
    protected static final String ID_KEY = "id";
    protected static final String PV_ID_KEY = "pvid";
    protected static final String CV_ID_KEY = "cvid";
    protected static final String PE_ID_KEY = "peid";

    public String key;
    public Map<String, String> segmentation;
    public Map<String, Integer> segmentationInt;
    public Map<String, Double> segmentationDouble;
    public Map<String, Boolean> segmentationBoolean;
    public int count;
    public double sum;
    public double dur;
    public long timestamp;
    public int hour;
    public int dow;
    public String id;
    public String pvid;
    public String cvid;
    public String peid;

    Event() {
    }

    Event(@NonNull String key, long timestamp, int hour, int dow) {
        this.key = key;
        this.timestamp = timestamp;
        this.hour = hour;
        this.dow = dow;
    }

    /**
     * Creates and returns a JSONObject containing the event data from this object.
     *
     * @return a JSONObject containing the event data from this object
     */
    JSONObject toJSON() {
        final JSONObject json = new JSONObject();

        try {
            json.put(KEY_KEY, key);
            json.put(COUNT_KEY, count);
            json.put(TIMESTAMP_KEY, timestamp);
            json.put(HOUR_KEY, hour);
            json.put(DAY_OF_WEEK_KEY, dow);

            //set the ID's only if they are not 'null'
            if (id != null) {
                json.put(ID_KEY, id);
            }

            if (pvid != null) {
                json.put(PV_ID_KEY, pvid);
            }

            if (cvid != null) {
                json.put(CV_ID_KEY, cvid);
            }

            if (peid != null) {
                json.put(PE_ID_KEY, peid);
            }

            JSONObject jobj = new JSONObject();
            if (segmentation != null) {
                for (Map.Entry<String, String> pair : segmentation.entrySet()) {
                    jobj.put(pair.getKey(), pair.getValue());
                }
            }

            if (segmentationInt != null) {
                for (Map.Entry<String, Integer> pair : segmentationInt.entrySet()) {
                    jobj.put(pair.getKey(), pair.getValue());
                }
            }

            if (segmentationDouble != null) {
                for (Map.Entry<String, Double> pair : segmentationDouble.entrySet()) {
                    jobj.put(pair.getKey(), pair.getValue());
                }
            }

            if (segmentationBoolean != null) {
                for (Map.Entry<String, Boolean> pair : segmentationBoolean.entrySet()) {
                    jobj.put(pair.getKey(), pair.getValue());
                }
            }

            if ((segmentation != null && !segmentation.isEmpty()) ||
                (segmentationInt != null && !segmentationInt.isEmpty()) ||
                (segmentationDouble != null && !segmentationDouble.isEmpty()) ||
                (segmentationBoolean != null && !segmentationBoolean.isEmpty())) {
                //we only write to the segmentation key if it contains at least one entry
                //we don't want to write an empty object
                json.put(SEGMENTATION_KEY, jobj);
            }

            // we put in the sum last, the only reason that a JSONException would be thrown
            // would be if sum is NaN or infinite, so in that case, at least we will return
            // a JSON object with the rest of the fields populated
            json.put(SUM_KEY, sum);

            //set duration only if it has any useful value
            if (dur > 0) {
                json.put(DUR_KEY, dur);
            }
        } catch (JSONException e) {
            Countly.sharedInstance().L.w("Got exception converting an Event to JSON", e);
        }

        return json;
    }

    /**
     * Factory method to create an Event from its JSON representation.
     *
     * @param json JSON object to extract event data from
     * @return Event object built from the data in the JSON or null if the "key" value is not
     * present or the empty string, or if a JSON exception occurs
     */
    static Event fromJSON(@NonNull final JSONObject json) {
        Event event = new Event();

        try {
            if (!json.isNull(KEY_KEY)) {
                event.key = json.getString(KEY_KEY);
            }
            event.count = json.optInt(COUNT_KEY);
            event.sum = json.optDouble(SUM_KEY, 0.0d);
            event.dur = json.optDouble(DUR_KEY, 0.0d);
            event.timestamp = json.optLong(TIMESTAMP_KEY);
            event.hour = json.optInt(HOUR_KEY);
            event.dow = json.optInt(DAY_OF_WEEK_KEY);

            // the parsed ID's might not be set or it might be set as null
            if (!json.isNull(ID_KEY)) {
                event.id = json.getString(ID_KEY);
            }
            if (!json.isNull(PV_ID_KEY)) {
                event.pvid = json.getString(PV_ID_KEY);
            }
            if (!json.isNull(CV_ID_KEY)) {
                event.cvid = json.getString(CV_ID_KEY);
            }
            if (!json.isNull(PE_ID_KEY)) {
                event.peid = json.getString(PE_ID_KEY);
            }

            if (!json.isNull(SEGMENTATION_KEY)) {
                //we would also enter here if segmentation was set to an empty object
                JSONObject segm = json.getJSONObject(SEGMENTATION_KEY);

                //we only create these objects if we would write to them
                HashMap<String, String> segmentation = null;
                HashMap<String, Integer> segmentationInt = null;
                HashMap<String, Double> segmentationDouble = null;
                HashMap<String, Boolean> segmentationBoolean = null;

                final Iterator nameItr = segm.keys();
                while (nameItr.hasNext()) {
                    final String key = (String) nameItr.next();
                    if (!segm.isNull(key)) {
                        Object obj = segm.opt(key);

                        if (obj instanceof Double) {
                            //in case it's a double
                            if (segmentationDouble == null) {
                                segmentationDouble = new HashMap<>();
                            }
                            segmentationDouble.put(key, segm.getDouble(key));
                        } else if (obj instanceof Integer) {
                            //in case it's a integer
                            if (segmentationInt == null) {
                                segmentationInt = new HashMap<>();
                            }
                            segmentationInt.put(key, segm.getInt(key));
                        } else if (obj instanceof Boolean) {
                            //in case it's a boolean
                            if (segmentationBoolean == null) {
                                segmentationBoolean = new HashMap<>();
                            }
                            segmentationBoolean.put(key, segm.getBoolean(key));
                        } else if (obj instanceof String) {
                            //in case it's a String
                            if (segmentation == null) {
                                segmentation = new HashMap<>();
                            }
                            segmentation.put(key, segm.getString(key));
                        }
                    }
                }
                event.segmentation = segmentation;
                event.segmentationDouble = segmentationDouble;
                event.segmentationInt = segmentationInt;
                event.segmentationBoolean = segmentationBoolean;
            }
        } catch (JSONException e) {
            Countly.sharedInstance().L.w("Got exception converting JSON to an Event", e);
            event = null;
        }

        if (event != null && event.key != null && event.key.length() > 0) {
            //in case the event has a key, it counts as a valid event
            return event;
        } else {
            //if the event doesn't even have a key, return 'null' since it's not a valid event
            return null;
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof Event)) {
            return false;
        }

        final Event e = (Event) o;

        return (Objects.equals(key, e.key)) &&
            timestamp == e.timestamp &&
            hour == e.hour &&
            dow == e.dow &&
            Objects.equals(id, e.id) &&
            Objects.equals(pvid, e.pvid) &&
            Objects.equals(cvid, e.cvid) &&
            Objects.equals(peid, e.peid) &&
            (Objects.equals(segmentation, e.segmentation));
    }

    @Override
    public int hashCode() {
        return (key != null ? key.hashCode() : 1) ^
            (segmentation != null ? segmentation.hashCode() : 1) ^
            (id != null ? id.hashCode() : 1) ^
            (pvid != null ? pvid.hashCode() : 1) ^
            (cvid != null ? cvid.hashCode() : 1) ^
            (peid != null ? peid.hashCode() : 1) ^
            (timestamp != 0 ? (int) timestamp : 1);
    }
}
