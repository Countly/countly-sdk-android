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
import androidx.annotation.Nullable;
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

    /**
     * By default all values should be either null or
     * have their default value in case of value types
     */
    @Test
    public void testDefaultValueBasicConstructor() {
        final Event event = new Event();
        assertNull(event.key);
        assertNull(event.segmentation);
        assertNull(event.segmentationInt);
        assertNull(event.segmentationDouble);
        assertNull(event.segmentationBoolean);
        assertNull(event.id);
        assertNull(event.cvid);
        assertNull(event.pvid);
        assertEquals(0, event.count);
        assertEquals(0.0d, event.sum, 0.0000001);
        assertEquals(0.0d, event.dur, 0.0000001);
        assertEquals(0, event.timestamp);
        assertEquals(0, event.hour);
        assertEquals(0, event.dow);
    }

    /**
     * All values except the key, timestamp, hour, dow should have their default and null values
     */
    @Test
    public void testDefaultValueKeyConstructor() {
        final Event event = new Event("abc", 123L, 5, 7);
        assertEquals("abc", event.key);
        assertNull(event.segmentation);
        assertNull(event.segmentationInt);
        assertNull(event.segmentationDouble);
        assertNull(event.segmentationBoolean);
        assertNull(event.id);
        assertNull(event.cvid);
        assertNull(event.pvid);
        assertEquals(0, event.count);
        assertEquals(0.0d, event.sum, 0.0000001);
        assertEquals(0.0d, event.dur, 0.0000001);
        assertEquals(123L, event.timestamp);
        assertEquals(5, event.hour);
        assertEquals(7, event.dow);
    }

    void TestCompareEvents(Event event1, Event event2, boolean interpretedAsEqual) {
        if (interpretedAsEqual) {
            assertEquals(event1, event2);
            assertEquals(event2, event1);
            assertEquals(event1.hashCode(), event2.hashCode());
        } else {
            assertNotEquals(event1, event2);
            assertNotEquals(event2, event1);
            assertTrue(event1.hashCode() != event2.hashCode());
        }
    }

    /**
     * Validate how setting different fields would affect equality and hash code
     */
    @Test
    public void testEqualsAndHashCode() {
        final Event event1 = new Event();
        final Event event2 = new Event();
        //noinspection ObjectEqualsNull
        assertNotEquals(null, event1);
        assertNotEquals(event1, new Object());
        TestCompareEvents(event1, event2, true);

        event1.key = "eventKey";
        TestCompareEvents(event1, event2, false);

        event2.key = "eventKey";
        TestCompareEvents(event1, event2, true);

        event1.timestamp = 1234;
        TestCompareEvents(event1, event2, false);

        event2.timestamp = 1234;
        TestCompareEvents(event1, event2, true);

        event1.segmentation = new HashMap<>();
        TestCompareEvents(event1, event2, false);

        event2.segmentation = new HashMap<>();
        TestCompareEvents(event1, event2, true);

        event1.segmentation.put("segkey", "segvalue");
        TestCompareEvents(event1, event2, false);

        event2.segmentation.put("segkey", "segvalue");
        TestCompareEvents(event1, event2, true);

        event1.sum = 3.2;
        event2.count = 42;
        TestCompareEvents(event1, event2, true);//todo it's unclear why both should be treated as equal

        event1.cvid = "cvid";
        TestCompareEvents(event1, event2, false);

        event2.cvid = "cvid";
        TestCompareEvents(event1, event2, true);

        event1.pvid = "pvid";
        TestCompareEvents(event1, event2, false);

        event2.pvid = "pvid";
        TestCompareEvents(event1, event2, true);

        event1.id = "id";
        TestCompareEvents(event1, event2, false);

        event2.id = "id";
        TestCompareEvents(event1, event2, true);
    }

    /**
     * Seems like we would want to explode when trying to parse 'null'
     * Probably that's because the value passed should never be 'null'
     */
    @Test
    public void testFromJSON_nullJSONObj() {
        try {
            Event.fromJSON(null);
            fail("Expected NPE when calling Event.fromJSON with null");
        } catch (NullPointerException ignored) {
            // success
        }
    }

    /**
     * Testing how parsing works with a empty json object
     * Since the event does not have a key, it should be parsed to 'null'
     */
    @Test
    public void testFromJSON_noKeyCausesJSONException() {
        final JSONObject jsonObj = new JSONObject();
        assertNull(Event.fromJSON(jsonObj));
    }

    /**
     * JSONObject does have a key set
     * But since the event does not have a valid (it's null) key, it should be parsed to 'null'
     */
    @Test
    public void testFromJSON_KeyNull() throws JSONException {
        final JSONObject jsonObj = new JSONObject();
        jsonObj.put("key", JSONObject.NULL);
        assertNull(Event.fromJSON(jsonObj));
    }

    /**
     * JSONObject does have a key set
     * Since the event does not have a valid (it's empty string) key, it should be parsed to 'null'
     */
    @Test
    public void testFromJSON_KeyEmpty() throws JSONException {
        final JSONObject jsonObj = new JSONObject();
        jsonObj.put("key", "");
        assertNull(Event.fromJSON(jsonObj));
    }

    /**
     * Basic event with only a key set
     * JSON obj with only key set
     * Both should be equal after parsing the JSON
     *
     * @throws JSONException
     */
    @Test
    public void testFromJSON_KeyOnly() throws JSONException {
        final Event expected = CreateEvent("eventKey");
        final JSONObject jsonObj = CreateEventJsonObj(expected.key);
        CompareEventJSON(jsonObj, "eventKey", null, null, expected);
    }

    /**
     * If JSON has other values set to 'null' and only the event key is set
     * then that should behave the same as only the event key is set
     *
     * @throws JSONException
     */
    @Test
    public void testFromJSON_KeyOnlyAllOtherNull() throws JSONException {
        final Event expected = CreateEvent("eventKey");
        final JSONObject jsonObj = new JSONObject();
        jsonObj.put(Event.KEY_KEY, expected.key);
        jsonObj.put(Event.TIMESTAMP_KEY, JSONObject.NULL);
        jsonObj.put(Event.DAY_OF_WEEK_KEY, JSONObject.NULL);
        jsonObj.put(Event.DUR_KEY, JSONObject.NULL);
        jsonObj.put(Event.HOUR_KEY, JSONObject.NULL);
        jsonObj.put(Event.COUNT_KEY, JSONObject.NULL);
        jsonObj.put(Event.SUM_KEY, JSONObject.NULL);
        jsonObj.put(Event.ID_KEY, JSONObject.NULL);
        jsonObj.put(Event.PV_ID_KEY, JSONObject.NULL);
        jsonObj.put(Event.CV_ID_KEY, JSONObject.NULL);
        jsonObj.put(Event.SEGMENTATION_KEY, JSONObject.NULL);
        CompareEventJSON(jsonObj, expected.key, null, null, expected);
    }

    @Test
    public void testToJSON_nullSegmentation() throws JSONException {
        //CreateAndValidateEvent("eventKey", 1234L, 42, 3.2d, 9);

        final Event event = new Event();
        event.key = "eventKey";
        event.timestamp = 1234;
        event.count = 42;
        event.sum = 3.2;
        event.id = "id";
        event.pvid = "pvid";
        event.cvid = "cvid";
        final JSONObject jsonObj = event.toJSON();
        assertEquals(9, jsonObj.length());
        assertEquals(event.key, jsonObj.getString("key"));
        assertEquals(event.timestamp, jsonObj.getInt("timestamp"));
        assertEquals(event.count, jsonObj.getInt("count"));
        assertEquals(event.sum, jsonObj.getDouble("sum"), 0.0000001);
        assertEquals(event.id, jsonObj.getString("id"));
        assertEquals(event.pvid, jsonObj.getString("pvid"));
        assertEquals(event.cvid, jsonObj.getString("cvid"));
    }

    void CreateAndValidateEvent(@NonNull String eventKey, Long timestamp, Integer count, Double sum, int expectedCount) throws JSONException {
        //int fieldCount = 4;
        final Event event = new Event();

        if (timestamp != null) {
            event.timestamp = timestamp;
            //fieldCount++;
        }

        final JSONObject jsonObj = event.toJSON();
        //assertEquals(fieldCount, jsonObj.length());
        assertEquals(expectedCount, jsonObj.length());

        assertEquals(event.key, jsonObj.getString("key"));
        assertEquals(event.timestamp, jsonObj.getInt("timestamp"));
        assertEquals(event.count, jsonObj.getInt("count"));
        assertEquals(event.sum, jsonObj.getDouble("sum"), 0.0000001);
        assertEquals(event.id, jsonObj.getString("id"));
        assertEquals(event.pvid, jsonObj.getString("pvid"));
        assertEquals(event.cvid, jsonObj.getString("cvid"));
    }

    @Test
    public void testToJSON_emptySegmentation() throws JSONException {
        final Event event = new Event();
        event.key = "eventKey";
        event.timestamp = 1234;
        event.count = 42;
        event.sum = 3.2;
        event.id = "id";
        event.pvid = "pvid";
        event.cvid = "cvid";
        event.segmentation = new HashMap<>();
        event.segmentationInt = new HashMap<>();
        event.segmentationDouble = new HashMap<>();
        event.segmentationBoolean = new HashMap<>();
        final JSONObject jsonObj = event.toJSON();
        assertEquals(10, jsonObj.length());
        assertEquals(event.key, jsonObj.getString("key"));
        assertEquals(event.timestamp, jsonObj.getInt("timestamp"));
        assertEquals(event.count, jsonObj.getInt("count"));
        assertEquals(event.sum, jsonObj.getDouble("sum"), 0.0000001);
        assertEquals(0, jsonObj.getJSONObject("segmentation").length());
        assertEquals(event.id, jsonObj.getString("id"));
        assertEquals(event.pvid, jsonObj.getString("pvid"));
        assertEquals(event.cvid, jsonObj.getString("cvid"));
    }

    @Test
    public void testToJSON_withSegmentation() throws JSONException {
        final Event event = new Event();
        event.key = "eventKey";
        event.timestamp = 1234;
        event.count = 42;
        event.sum = 3.2;
        event.id = "id";
        event.pvid = "pvid";
        event.cvid = "cvid";
        event.segmentation = new HashMap<>();
        event.segmentationInt = new HashMap<>();
        event.segmentationDouble = new HashMap<>();
        event.segmentationBoolean = new HashMap<>();
        event.segmentation.put("segkey", "segvalue");
        event.segmentationInt.put("segkey1", 123);
        event.segmentationDouble.put("segkey2", 544.43d);
        event.segmentationBoolean.put("segkey3", true);
        final JSONObject jsonObj = event.toJSON();
        assertEquals(10, jsonObj.length());
        assertEquals(event.key, jsonObj.getString("key"));
        assertEquals(event.timestamp, jsonObj.getInt("timestamp"));
        assertEquals(event.count, jsonObj.getInt("count"));
        assertEquals(event.sum, jsonObj.getDouble("sum"), 0.0000001);
        assertEquals(4, jsonObj.getJSONObject("segmentation").length());
        assertEquals(event.segmentation.get("segkey"), jsonObj.getJSONObject("segmentation").getString("segkey"));
        assertEquals(event.segmentationInt.get("segkey1").intValue(), jsonObj.getJSONObject("segmentation").getInt("segkey1"));
        assertEquals(event.segmentationDouble.get("segkey2").doubleValue(), jsonObj.getJSONObject("segmentation").getDouble("segkey2"), 0.0001d);
        assertEquals(event.segmentationBoolean.get("segkey3").booleanValue(), jsonObj.getJSONObject("segmentation").getBoolean("segkey3"));
        assertEquals(event.id, jsonObj.getString("id"));
        assertEquals(event.pvid, jsonObj.getString("pvid"));
        assertEquals(event.cvid, jsonObj.getString("cvid"));
    }

    @Test
    public void testToJSON_sumNaNCausesJSONException() throws JSONException {
        final Event event = new Event();
        event.key = "eventKey";
        event.timestamp = 1234;
        event.count = 42;
        event.sum = Double.NaN;
        event.id = "id";
        event.pvid = "pvid";
        event.cvid = "cvid";
        event.segmentation = new HashMap<>();
        event.segmentation.put("segkey", "segvalue");
        final JSONObject jsonObj = event.toJSON();
        assertEquals(9, jsonObj.length());
        assertEquals(event.key, jsonObj.getString("key"));
        assertEquals(event.timestamp, jsonObj.getInt("timestamp"));
        assertEquals(event.count, jsonObj.getInt("count"));
        assertEquals(1, jsonObj.getJSONObject("segmentation").length());
        assertEquals(event.segmentation.get("segkey"), jsonObj.getJSONObject("segmentation").getString("segkey"));
        assertEquals(event.id, jsonObj.getString("id"));
        assertEquals(event.pvid, jsonObj.getString("pvid"));
        assertEquals(event.cvid, jsonObj.getString("cvid"));
    }

    void CompareEventJSON(@NonNull JSONObject jsonObj, @NonNull String eventKey, @Nullable Integer count, @Nullable Double sum, @Nullable final Event expectedEvent) throws JSONException {
        //validate JSON fields with the one that are given
        if (eventKey != null) {
            assertTrue(jsonObj.has(Event.KEY_KEY));
            assertEquals(eventKey, jsonObj.getString(Event.KEY_KEY));
        }

        if (count != null) {
            assertTrue(jsonObj.has(Event.COUNT_KEY));
            assertEquals(count.intValue(), jsonObj.getInt(Event.COUNT_KEY));
        }

        if (sum != null) {
            assertTrue(jsonObj.has(Event.SUM_KEY));
            assertEquals(sum, jsonObj.getDouble(Event.SUM_KEY), 0.0000001);
        }

        //validate events as they are parsed
        final Event parsedEvent = Event.fromJSON(jsonObj);

        if (eventKey != null) {
            assertEquals(eventKey, parsedEvent.key);
        }

        if (count != null) {
            assertEquals(count.intValue(), parsedEvent.count);
        }

        if (sum != null) {
            assertEquals(sum, parsedEvent.sum, 0.0000001);
        }

        //finally compare to the given expected event
        if (expectedEvent != null) {
            assertEquals(expectedEvent, parsedEvent);
            assertEquals(expectedEvent.count, parsedEvent.count);
            assertEquals(expectedEvent.sum, parsedEvent.sum, 0.0000001);
        }
    }

    /**
     * Values not provided are not set
     *
     * @param eventKey
     * @return
     */
    Event CreateEvent(@NonNull String eventKey) {
        final Event event = new Event();
        event.key = eventKey;
        return event;
    }

    /**
     * Values not provided are not set.
     *
     * @param key
     * @return
     * @throws JSONException
     */
    JSONObject CreateEventJsonObj(String key) throws JSONException {
        final JSONObject jsonObj = new JSONObject();
        if (key != null) {
            jsonObj.put("key", key);
        }
        return jsonObj;
    }

    @Test
    public void testFromJSON_noSegmentation() throws JSONException {
        final Event expected = new Event();
        expected.key = "eventKey";
        expected.timestamp = 1234;
        expected.count = 42;
        expected.sum = 3.2;
        expected.id = "id";
        expected.cvid = "cvid";
        expected.pvid = "pvid";
        final JSONObject jsonObj = new JSONObject();
        jsonObj.put("key", expected.key);
        jsonObj.put("timestamp", expected.timestamp);
        jsonObj.put("count", expected.count);
        jsonObj.put("sum", expected.sum);
        jsonObj.put("id", expected.id);
        jsonObj.put("pvid", expected.pvid);
        jsonObj.put("cvid", expected.cvid);
        CompareEventJSON(jsonObj, "eventKey", null, null, expected);
    }

    @Test
    public void testFromJSON_nullSegmentation() throws JSONException {
        final Event expected = new Event();
        expected.key = "eventKey";
        expected.timestamp = 1234;
        expected.count = 42;
        expected.sum = 3.2;
        expected.id = "id";
        expected.cvid = "cvid";
        expected.pvid = "pvid";
        final JSONObject jsonObj = new JSONObject();
        jsonObj.put("key", expected.key);
        jsonObj.put("timestamp", expected.timestamp);
        jsonObj.put("count", expected.count);
        jsonObj.put("sum", expected.sum);
        jsonObj.put("id", expected.id);
        jsonObj.put("pvid", expected.pvid);
        jsonObj.put("cvid", expected.cvid);
        jsonObj.put("segmentation", JSONObject.NULL);
        CompareEventJSON(jsonObj, "eventKey", null, null, expected);
    }

    @Test
    public void testFromJSON_segmentationNotADictionary() throws JSONException {
        final Event expected = new Event();
        expected.key = "eventKey";
        expected.timestamp = 1234;
        expected.count = 42;
        expected.sum = 3.2;
        expected.id = "id";
        expected.cvid = "cvid";
        expected.pvid = "pvid";
        final JSONObject jsonObj = new JSONObject();
        jsonObj.put("key", expected.key);
        jsonObj.put("timestamp", expected.timestamp);
        jsonObj.put("count", expected.count);
        jsonObj.put("sum", expected.sum);
        jsonObj.put("id", expected.id);
        jsonObj.put("pvid", expected.pvid);
        jsonObj.put("cvid", expected.cvid);
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
        expected.id = "id";
        expected.cvid = "cvid";
        expected.pvid = "pvid";
        expected.segmentation = new HashMap<>();
        final JSONObject jsonObj = new JSONObject();
        jsonObj.put("key", expected.key);
        jsonObj.put("timestamp", expected.timestamp);
        jsonObj.put("count", expected.count);
        jsonObj.put("sum", expected.sum);
        jsonObj.put("id", expected.id);
        jsonObj.put("pvid", expected.pvid);
        jsonObj.put("cvid", expected.cvid);
        jsonObj.put("segmentation", new JSONObject(expected.segmentation));
        CompareEventJSON(jsonObj, "eventKey", null, null, expected);
    }

    @Test
    public void testFromJSON_withSegmentation() throws JSONException {
        final Event expected = new Event();
        expected.key = "eventKey";
        expected.timestamp = 1234;
        expected.count = 42;
        expected.sum = 3.2;
        expected.id = "id";
        expected.cvid = "cvid";
        expected.pvid = "pvid";
        expected.segmentation = new HashMap<>();
        expected.segmentation.put("segkey", "segvalue");
        final JSONObject jsonObj = new JSONObject();
        jsonObj.put("key", expected.key);
        jsonObj.put("timestamp", expected.timestamp);
        jsonObj.put("count", expected.count);
        jsonObj.put("sum", expected.sum);
        jsonObj.put("id", expected.id);
        jsonObj.put("pvid", expected.pvid);
        jsonObj.put("cvid", expected.cvid);
        jsonObj.put("segmentation", new JSONObject(expected.segmentation));
        CompareEventJSON(jsonObj, "eventKey", null, null, expected);
    }

    @Test
    public void testFromJSON_withSegmentation_nonStringValues() throws JSONException {
        final Event expected = new Event();
        expected.key = "eventKey";
        expected.timestamp = 1234;
        expected.count = 42;
        expected.sum = 3.2;
        expected.id = "id";
        expected.cvid = "cvid";
        expected.pvid = "pvid";
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
        jsonObj.put("id", expected.id);
        jsonObj.put("pvid", expected.pvid);
        jsonObj.put("cvid", expected.cvid);
        jsonObj.put("segmentation", new JSONObject(valueMap));
        CompareEventJSON(jsonObj, "eventKey", null, null, expected);
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
