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

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;

/**
 * This class queues event data locally and can convert that event data to JSON
 * for submission to a Count.ly server.
 *
 * None of the methods in this class are synchronized because access to this class is
 * controlled by the Countly singleton, which is synchronized.
 *
 * NOTE: This class is only public to facilitate unit testing, because
 * of this bug in dexmaker: https://code.google.com/p/dexmaker/issues/detail?id=34
 */
public class EventQueue {
    private final CountlyStore countlyStore_;

    /**
     * Constructs an EventQueue.
     *
     * @param countlyStore backing store to be used for local event queue persistence
     */
    EventQueue(final CountlyStore countlyStore) {
        countlyStore_ = countlyStore;
    }

    /**
     * Returns the number of events in the local event queue.
     *
     * @return the number of events in the local event queue
     */
    int size() {
        return countlyStore_.getEvents().length;
    }

    /**
     * Removes all current events from the local queue and returns them as a
     * URL-encoded JSON string that can be submitted to a ConnectionQueue.
     *
     * @return URL-encoded JSON string of event data from the local event queue
     */
    String events() {
        String result;

        final List<Event> events = countlyStore_.getEventList();

        final JSONArray eventArray = new JSONArray();
        for (Event e : events) {
            eventArray.put(e.toJSON());
        }

        result = eventArray.toString();

        countlyStore_.removeEvents(events);

        try {
            result = java.net.URLEncoder.encode(result, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // should never happen because Android guarantees UTF-8 support
        }

        return result;
    }

    /**
     * Records a custom Count.ly event to the local event queue.
     *
     * @param key name of the custom event, required, must not be the empty string
     * @param segmentation segmentation values for the custom event, may be null
     * @param count count associated with the custom event, should be more than zero
     * @param sum sum associated with the custom event, if not used, pass zero.
     * NaN and infinity values will be quietly ignored.
     */
    void recordEvent(final String key, final Map<String, String> segmentation, final Map<String, Integer> segmentationInt, final Map<String, Double> segmentationDouble, final Map<String, Boolean> segmentationBoolean, final int count,
        final double sum, final double dur, UtilsTime.Instant instant) {
        if (instant == null) {
            instant = UtilsTime.getCurrentInstant();
        }

        final long timestamp = instant.timestampMs;
        final int hour = instant.hour;
        final int dow = instant.dow;
        countlyStore_.addEvent(key, segmentation, segmentationInt, segmentationDouble, segmentationBoolean, timestamp, hour, dow, count, sum, dur);
    }

    void recordEvent(final Event event) {
        UtilsTime.Instant instant = UtilsTime.getCurrentInstant();

        event.hour = instant.hour;
        event.dow = instant.dow;
        countlyStore_.addEvent(event);
    }

    // for unit tests
    CountlyStore getCountlyStore() {
        return countlyStore_;
    }
}
