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

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
@SuppressWarnings("ConstantConditions")
public class EventTests {

    @Before
    public void setUp() {
        Countly.sharedInstance().setLoggingEnabled(true);
    }

    @Test
    public void testConstructor() {
        final Event event = new Event();
        assertNull(event.key);
        assertNull(event.segmentation);
        assertEquals(0, event.count);
        assertEquals(0, event.timestamp);
        assertEquals(0.0d, event.sum, 0.0000001);
    }

    @Test
    public void testEqualsAndHashCode() {
        final Event event1 = new Event();
        final Event event2 = new Event();
        //noinspection ObjectEqualsNull
        assertFalse(event1.equals(null));
        assertNotEquals(event1, new Object());
        assertEquals(event1, event2);
        assertEquals(event1.hashCode(), event2.hashCode());

        event1.key = "eventKey";
        assertNotEquals(event1, event2);
        assertNotEquals(event2, event1);
        assertTrue(event1.hashCode() != event2.hashCode());

        event2.key = "eventKey";
        assertEquals(event1, event2);
        assertEquals(event2, event1);
        assertEquals(event1.hashCode(), event2.hashCode());

        event1.timestamp = 1234;
        assertNotEquals(event1, event2);
        assertNotEquals(event2, event1);
        assertTrue(event1.hashCode() != event2.hashCode());

        event2.timestamp = 1234;
        assertEquals(event1, event2);
        assertEquals(event2, event1);
        assertEquals(event1.hashCode(), event2.hashCode());

        event1.segmentation = new HashMap<>();
        assertNotEquals(event1, event2);
        assertNotEquals(event2, event1);
        assertTrue(event1.hashCode() != event2.hashCode());

        event2.segmentation = new HashMap<>();
        assertEquals(event1, event2);
        assertEquals(event2, event1);
        assertEquals(event1.hashCode(), event2.hashCode());

        event1.segmentation.put("segkey", "segvalue");
        assertNotEquals(event1, event2);
        assertNotEquals(event2, event1);
        assertTrue(event1.hashCode() != event2.hashCode());

        event2.segmentation.put("segkey", "segvalue");
        assertEquals(event1, event2);
        assertEquals(event2, event1);
        assertEquals(event1.hashCode(), event2.hashCode());

        event1.sum = 3.2;
        event2.count = 42;
        assertEquals(event1, event2);
        assertEquals(event2, event1);
        assertEquals(event1.hashCode(), event2.hashCode());
    }

    @Test
    public void testToJSON_nullSegmentation() throws JSONException {
        final Event event = new Event();
        event.key = "eventKey";
        event.timestamp = 1234;
        event.count = 42;
        event.sum = 3.2;
        final JSONObject jsonObj = event.toJSON();
        assertEquals(6, jsonObj.length());
        assertEquals(event.key, jsonObj.getString("key"));
        assertEquals(event.timestamp, jsonObj.getInt("timestamp"));
        assertEquals(event.count, jsonObj.getInt("count"));
        assertEquals(event.sum, jsonObj.getDouble("sum"), 0.0000001);
    }

    @Test
    public void testToJSON_emptySegmentation() throws JSONException {
        final Event event = new Event();
        event.key = "eventKey";
        event.timestamp = 1234;
        event.count = 42;
        event.sum = 3.2;
        event.segmentation = new HashMap<>();
        event.segmentationInt = new HashMap<>();
        event.segmentationDouble = new HashMap<>();
        event.segmentationBoolean = new HashMap<>();
        final JSONObject jsonObj = event.toJSON();
        assertEquals(7, jsonObj.length());
        assertEquals(event.key, jsonObj.getString("key"));
        assertEquals(event.timestamp, jsonObj.getInt("timestamp"));
        assertEquals(event.count, jsonObj.getInt("count"));
        assertEquals(event.sum, jsonObj.getDouble("sum"), 0.0000001);
        assertEquals(0, jsonObj.getJSONObject("segmentation").length());
    }

    @Test
    public void testToJSON_withSegmentation() throws JSONException {
        final Event event = new Event();
        event.key = "eventKey";
        event.timestamp = 1234;
        event.count = 42;
        event.sum = 3.2;
        event.segmentation = new HashMap<>();
        event.segmentationInt = new HashMap<>();
        event.segmentationDouble = new HashMap<>();
        event.segmentationBoolean = new HashMap<>();
        event.segmentation.put("segkey", "segvalue");
        event.segmentationInt.put("segkey1", 123);
        event.segmentationDouble.put("segkey2", 544.43d);
        event.segmentationBoolean.put("segkey3", true);
        final JSONObject jsonObj = event.toJSON();
        assertEquals(7, jsonObj.length());
        assertEquals(event.key, jsonObj.getString("key"));
        assertEquals(event.timestamp, jsonObj.getInt("timestamp"));
        assertEquals(event.count, jsonObj.getInt("count"));
        assertEquals(event.sum, jsonObj.getDouble("sum"), 0.0000001);
        assertEquals(4, jsonObj.getJSONObject("segmentation").length());
        assertEquals(event.segmentation.get("segkey"), jsonObj.getJSONObject("segmentation").getString("segkey"));
        assertEquals(event.segmentationInt.get("segkey1").intValue(), jsonObj.getJSONObject("segmentation").getInt("segkey1"));
        assertEquals(event.segmentationDouble.get("segkey2").doubleValue(), jsonObj.getJSONObject("segmentation").getDouble("segkey2"), 0.0001d);
        assertEquals(event.segmentationBoolean.get("segkey3").booleanValue(), jsonObj.getJSONObject("segmentation").getBoolean("segkey3"));
    }

    @Test
    public void testToJSON_sumNaNCausesJSONException() throws JSONException {
        final Event event = new Event();
        event.key = "eventKey";
        event.timestamp = 1234;
        event.count = 42;
        event.sum = Double.NaN;
        event.segmentation = new HashMap<>();
        event.segmentation.put("segkey", "segvalue");
        final JSONObject jsonObj = event.toJSON();
        assertEquals(6, jsonObj.length());
        assertEquals(event.key, jsonObj.getString("key"));
        assertEquals(event.timestamp, jsonObj.getInt("timestamp"));
        assertEquals(event.count, jsonObj.getInt("count"));
        assertEquals(1, jsonObj.getJSONObject("segmentation").length());
        assertEquals(event.segmentation.get("segkey"), jsonObj.getJSONObject("segmentation").getString("segkey"));
    }

    @Test
    public void testFromJSON_nullJSONObj() {
        try {
            Event.fromJSON(null);
            fail("Expected NPE when calling Event.fromJSON with null");
        } catch (NullPointerException ignored) {
            // success
        }
    }

    @Test
    public void testFromJSON_noKeyCausesJSONException() {
        final JSONObject jsonObj = new JSONObject();
        assertNull(Event.fromJSON(jsonObj));
    }

    @Test
    public void testFromJSON_nullKey() throws JSONException {
        final JSONObject jsonObj = new JSONObject();
        jsonObj.put("key", JSONObject.NULL);
        assertNull(Event.fromJSON(jsonObj));
    }

    @Test
    public void testFromJSON_emptyKey() throws JSONException {
        final JSONObject jsonObj = new JSONObject();
        jsonObj.put("key", "");
        assertNull(Event.fromJSON(jsonObj));
    }

    @Test
    public void testFromJSON_keyOnly() throws JSONException {
        final Event expected = new Event();
        expected.key = "eventKey";
        final JSONObject jsonObj = new JSONObject();
        jsonObj.put("key", expected.key);
        final Event actual = Event.fromJSON(jsonObj);
        assertEquals(expected, actual);
        assertEquals(expected.count, actual.count);
        assertEquals(expected.sum, actual.sum, 0.0000001);
    }

    @Test
    public void testFromJSON_keyOnly_nullOtherValues() throws JSONException {
        final Event expected = new Event();
        expected.key = "eventKey";
        final JSONObject jsonObj = new JSONObject();
        jsonObj.put("key", expected.key);
        jsonObj.put("timestamp", JSONObject.NULL);
        jsonObj.put("count", JSONObject.NULL);
        jsonObj.put("sum", JSONObject.NULL);
        final Event actual = Event.fromJSON(jsonObj);
        assertEquals(expected, actual);
        assertEquals(expected.count, actual.count);
        assertEquals(expected.sum, actual.sum, 0.0000001);
    }

    @Test
    public void testFromJSON_noSegmentation() throws JSONException {
        final Event expected = new Event();
        expected.key = "eventKey";
        expected.timestamp = 1234;
        expected.count = 42;
        expected.sum = 3.2;
        final JSONObject jsonObj = new JSONObject();
        jsonObj.put("key", expected.key);
        jsonObj.put("timestamp", expected.timestamp);
        jsonObj.put("count", expected.count);
        jsonObj.put("sum", expected.sum);
        final Event actual = Event.fromJSON(jsonObj);
        assertEquals(expected, actual);
        assertEquals(expected.count, actual.count);
        assertEquals(expected.sum, actual.sum, 0.0000001);
    }

    @Test
    public void testFromJSON_nullSegmentation() throws JSONException {
        final Event expected = new Event();
        expected.key = "eventKey";
        expected.timestamp = 1234;
        expected.count = 42;
        expected.sum = 3.2;
        final JSONObject jsonObj = new JSONObject();
        jsonObj.put("key", expected.key);
        jsonObj.put("timestamp", expected.timestamp);
        jsonObj.put("count", expected.count);
        jsonObj.put("sum", expected.sum);
        jsonObj.put("segmentation", JSONObject.NULL);
        final Event actual = Event.fromJSON(jsonObj);
        assertEquals(expected, actual);
        assertEquals(expected.count, actual.count);
        assertEquals(expected.sum, actual.sum, 0.0000001);
    }

    @Test
    public void testFromJSON_segmentationNotADictionary() throws JSONException {
        final Event expected = new Event();
        expected.key = "eventKey";
        expected.timestamp = 1234;
        expected.count = 42;
        expected.sum = 3.2;
        final JSONObject jsonObj = new JSONObject();
        jsonObj.put("key", expected.key);
        jsonObj.put("timestamp", expected.timestamp);
        jsonObj.put("count", expected.count);
        jsonObj.put("sum", expected.sum);
        jsonObj.put("segmentation", 1234);
        assertNull(Event.fromJSON(jsonObj));
    }

    @Test
    public void testFromJSON_emptySegmentation() throws JSONException {
        final Event expected = new Event();
        expected.key = "eventKey";
        expected.timestamp = 1234;
        expected.count = 42;
        expected.sum = 3.2;
        expected.segmentation = new HashMap<>();
        final JSONObject jsonObj = new JSONObject();
        jsonObj.put("key", expected.key);
        jsonObj.put("timestamp", expected.timestamp);
        jsonObj.put("count", expected.count);
        jsonObj.put("sum", expected.sum);
        jsonObj.put("segmentation", new JSONObject(expected.segmentation));
        final Event actual = Event.fromJSON(jsonObj);
        assertEquals(expected, actual);
        assertEquals(expected.count, actual.count);
        assertEquals(expected.sum, actual.sum, 0.0000001);
    }

    @Test
    public void testFromJSON_withSegmentation() throws JSONException {
        final Event expected = new Event();
        expected.key = "eventKey";
        expected.timestamp = 1234;
        expected.count = 42;
        expected.sum = 3.2;
        expected.segmentation = new HashMap<>();
        expected.segmentation.put("segkey", "segvalue");
        final JSONObject jsonObj = new JSONObject();
        jsonObj.put("key", expected.key);
        jsonObj.put("timestamp", expected.timestamp);
        jsonObj.put("count", expected.count);
        jsonObj.put("sum", expected.sum);
        jsonObj.put("segmentation", new JSONObject(expected.segmentation));
        final Event actual = Event.fromJSON(jsonObj);
        assertEquals(expected, actual);
        assertEquals(expected.count, actual.count);
        assertEquals(expected.sum, actual.sum, 0.0000001);
    }

    @Test
    public void testFromJSON_withSegmentation_nonStringValues() throws JSONException {
        final Event expected = new Event();
        expected.key = "eventKey";
        expected.timestamp = 1234;
        expected.count = 42;
        expected.sum = 3.2;
        expected.segmentation = new HashMap<>();
        expected.segmentation.put("sk1", "vall");
        expected.segmentationDouble = new HashMap<>();
        expected.segmentationDouble.put("sk2", 334.33d);
        expected.segmentationInt = new HashMap<>();
        expected.segmentationInt.put("segkey", 1234);
        expected.segmentationBoolean = new HashMap<>();
        expected.segmentationBoolean.put("sk3", true);

        final Map<Object, Object> valueMap = new HashMap<>();
        valueMap.put("segkey", 1234);
        valueMap.put("sk1", "vall");
        valueMap.put("sk2", 334.33d);
        valueMap.put("sk3", true);
        final JSONObject jsonObj = new JSONObject();
        jsonObj.put("key", expected.key);
        jsonObj.put("timestamp", expected.timestamp);
        jsonObj.put("count", expected.count);
        jsonObj.put("sum", expected.sum);
        jsonObj.put("segmentation", new JSONObject(valueMap));
        final Event actual = Event.fromJSON(jsonObj);
        assertEquals(expected, actual);
        assertEquals(expected.count, actual.count);
        assertEquals(expected.sum, actual.sum, 0.0000001);
    }

    @Test
    public void testSegmentationSorter() {
        String[] keys = new String[] { "a", "b", "c", "d", "e", "f", "l", "r" };

        Map<String, Object> automaticViewSegmentation = new HashMap<>();

        automaticViewSegmentation.put(keys[0], 2);
        automaticViewSegmentation.put(keys[1], 12);
        automaticViewSegmentation.put(keys[2], 123);
        automaticViewSegmentation.put(keys[3], 4.44d);
        automaticViewSegmentation.put(keys[4], "Six");
        automaticViewSegmentation.put(keys[5], "asdSix");
        automaticViewSegmentation.put(keys[6], false);
        automaticViewSegmentation.put(keys[7], true);

        HashMap<String, String> segmentsString = new HashMap<>();
        HashMap<String, Integer> segmentsInt = new HashMap<>();
        HashMap<String, Double> segmentsDouble = new HashMap<>();
        HashMap<String, Boolean> segmentsBoolean = new HashMap<>();
        HashMap<String, Object> segmentsReminder = new HashMap<>();

        Utils.fillInSegmentation(automaticViewSegmentation, segmentsString, segmentsInt, segmentsDouble, segmentsBoolean, segmentsReminder);

        assertEquals(automaticViewSegmentation.size(), keys.length);
        assertEquals(segmentsInt.size(), 3);
        assertEquals(segmentsDouble.size(), 1);
        assertEquals(segmentsString.size(), 2);
        assertEquals(segmentsBoolean.size(), 2);
        assertEquals(segmentsReminder.size(), 0);

        assertEquals(segmentsInt.get(keys[0]).intValue(), 2);
        assertEquals(segmentsInt.get(keys[1]).intValue(), 12);
        assertEquals(segmentsInt.get(keys[2]).intValue(), 123);
        assertEquals(segmentsDouble.get(keys[3]).doubleValue(), 4.44d, 0.00001);
        assertEquals(segmentsString.get(keys[4]), "Six");
        assertEquals(segmentsString.get(keys[5]), "asdSix");
        assertEquals(segmentsBoolean.get(keys[6]), false);
        assertEquals(segmentsBoolean.get(keys[7]), true);
    }

    @Test
    public void testSegmentationSorterReminder() {
        String[] keys = new String[] { "a", "b", "c", "d", "e", "f", "l", "r" };

        Map<String, Object> automaticViewSegmentation = new HashMap<>();

        Object obj = new Object();
        int[] arr = new int[] { 1, 2, 3 };

        automaticViewSegmentation.put(keys[0], 2);
        automaticViewSegmentation.put(keys[1], 12.2f);
        automaticViewSegmentation.put(keys[2], 4.44d);
        automaticViewSegmentation.put(keys[3], "Six");
        automaticViewSegmentation.put(keys[4], obj);
        automaticViewSegmentation.put(keys[5], arr);
        automaticViewSegmentation.put(keys[6], false);
        automaticViewSegmentation.put(keys[7], true);

        HashMap<String, String> segmentsString = new HashMap<>();
        HashMap<String, Integer> segmentsInt = new HashMap<>();
        HashMap<String, Double> segmentsDouble = new HashMap<>();
        HashMap<String, Boolean> segmentsBoolean = new HashMap<>();
        HashMap<String, Object> segmentsReminder = new HashMap<>();

        Utils.fillInSegmentation(automaticViewSegmentation, segmentsString, segmentsInt, segmentsDouble, segmentsBoolean, segmentsReminder);

        assertEquals(automaticViewSegmentation.size(), keys.length);
        assertEquals(segmentsInt.size(), 1);
        assertEquals(segmentsDouble.size(), 1);
        assertEquals(segmentsString.size(), 1);
        assertEquals(segmentsReminder.size(), 3);

        assertEquals(segmentsInt.get(keys[0]).intValue(), 2);
        assertEquals(segmentsDouble.get(keys[2]).doubleValue(), 4.44d, 0.00001);
        assertEquals(segmentsString.get(keys[3]), "Six");

        assertEquals(segmentsReminder.get(keys[1]), 12.2f);
        assertEquals(segmentsReminder.get(keys[4]), obj);
        assertEquals(segmentsReminder.get(keys[5]), arr);
        assertEquals(segmentsBoolean.get(keys[6]), false);
        assertEquals(segmentsBoolean.get(keys[7]), true);
    }
}
