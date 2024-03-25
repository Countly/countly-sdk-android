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
     * By default all values should be either 'null' or
     * have their default value in case of value types
     */
    @Test
    public void basicConstructorDefaultValues() {
        final Event event = new Event();
        assertNull(event.key);
        assertNull(event.segmentation);
        assertNull(event.id);
        assertNull(event.cvid);
        assertNull(event.peid);
        assertNull(event.pvid);
        assertEquals(0, event.count);
        assertEquals(0.0d, event.sum, 0.000_000_1);
        assertEquals(0.0d, event.dur, 0.000_000_1);
        assertEquals(0, event.timestamp);
        assertEquals(0, event.hour);
        assertEquals(0, event.dow);
    }

    /**
     * All values except the key, timestamp, hour, dow should have their default and null values
     */
    @Test
    public void KeyConstructorDefaultValues() {
        final Event event = new Event("abc", 123L, 5, 7);
        assertEquals("abc", event.key);
        assertNull(event.segmentation);
        assertNull(event.id);
        assertNull(event.cvid);
        assertNull(event.peid);
        assertNull(event.pvid);
        assertEquals(0, event.count);
        assertEquals(0.0d, event.sum, 0.000_000_1);
        assertEquals(0.0d, event.dur, 0.000_000_1);
        assertEquals(123L, event.timestamp);
        assertEquals(5, event.hour);
        assertEquals(7, event.dow);
    }

    void TestEventComparer(Event event1, Event event2, boolean interpretedAsEqual) {
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
    public void equalsAndHashCodeValidation() {
        final Event event1 = new Event();
        final Event event2 = new Event();
        //noinspection ObjectEqualsNull
        assertNotEquals(null, event1);
        assertNotEquals(event1, new Object());
        TestEventComparer(event1, event2, true);

        event1.key = "eventKey";
        TestEventComparer(event1, event2, false);

        event2.key = "eventKey";
        TestEventComparer(event1, event2, true);

        event1.timestamp = 1234;
        TestEventComparer(event1, event2, false);

        event2.timestamp = 1234;
        TestEventComparer(event1, event2, true);

        event1.segmentation = new HashMap<>();
        TestEventComparer(event1, event2, false);

        event2.segmentation = new HashMap<>();
        TestEventComparer(event1, event2, true);

        event1.segmentation.put("segkey", "segvalue");
        TestEventComparer(event1, event2, false);

        event2.segmentation.put("segkey", "segvalue");
        TestEventComparer(event1, event2, true);

        event1.sum = 3.2;
        event2.count = 42;
        TestEventComparer(event1, event2, true);//todo it's unclear why both should be treated as equal

        event1.cvid = "cvid";
        TestEventComparer(event1, event2, false);

        event2.cvid = "cvid";
        TestEventComparer(event1, event2, true);

        event1.peid = "peid";
        TestEventComparer(event1, event2, false);

        event2.peid = "peid";
        TestEventComparer(event1, event2, true);

        event1.pvid = "pvid";
        TestEventComparer(event1, event2, false);

        event2.pvid = "pvid";
        TestEventComparer(event1, event2, true);

        event1.id = "id";
        TestEventComparer(event1, event2, false);

        event2.id = "id";
        TestEventComparer(event1, event2, true);
    }

    /**
     * We perform some validation
     * We return parsed object for additional checks afterwards
     *
     * @param jsonObj
     * @param expectedEvent
     * @return
     * @throws JSONException
     */
    void fromJSON_CompareExpectedToParsed(@NonNull JSONObject jsonObj, @Nullable final Event expectedEvent) throws JSONException {
        //validate events as they are parsed
        final Event parsedEvent = Event.fromJSON(jsonObj);

        if (!jsonObj.isNull(Event.KEY_KEY)) {
            assertEquals(expectedEvent.key, parsedEvent.key);
        }

        if (!jsonObj.isNull(Event.TIMESTAMP_KEY)) {
            assertEquals(expectedEvent.timestamp, parsedEvent.timestamp);
        }

        if (!jsonObj.isNull(Event.DAY_OF_WEEK_KEY)) {
            assertEquals(expectedEvent.dow, parsedEvent.dow);
        }

        if (!jsonObj.isNull(Event.HOUR_KEY)) {
            assertEquals(expectedEvent.hour, parsedEvent.hour);
        }

        if (!jsonObj.isNull(Event.DUR_KEY)) {
            assertEquals(expectedEvent.dur, parsedEvent.dur, 0.000_001);
        }

        if (!jsonObj.isNull(Event.COUNT_KEY)) {
            assertEquals(expectedEvent.count, parsedEvent.count);
        }

        if (!jsonObj.isNull(Event.SUM_KEY)) {
            assertEquals(expectedEvent.sum, parsedEvent.sum, 0.000_001);
        }

        if (!jsonObj.isNull(Event.ID_KEY)) {
            assertEquals(expectedEvent.id, parsedEvent.id);
        }

        if (!jsonObj.isNull(Event.PV_ID_KEY)) {
            assertEquals(expectedEvent.pvid, parsedEvent.pvid);
        }

        if (!jsonObj.isNull(Event.CV_ID_KEY)) {
            assertEquals(expectedEvent.cvid, parsedEvent.cvid);
        }

        if (!jsonObj.isNull(Event.PE_ID_KEY)) {
            assertEquals(expectedEvent.peid, parsedEvent.peid);
        }

        if (!jsonObj.isNull(Event.SEGMENTATION_KEY)) {
            assertEquals(expectedEvent.segmentation, parsedEvent.segmentation);
        }

        //finally compare to the given expected event
        assertEquals(expectedEvent, parsedEvent);
        assertEquals(expectedEvent.count, parsedEvent.count);
        assertEquals(expectedEvent.sum, parsedEvent.sum, 0.000_000_1);
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
     * @param key
     * @param segmentation - it is "object" so to support null
     * @return
     * @throws JSONException
     */
    JSONObject CreateEventJsonObj(@NonNull String key, @Nullable Object segmentation) throws JSONException {
        final JSONObject jsonObj = new JSONObject();
        jsonObj.put(Event.KEY_KEY, key);
        if (segmentation != null) {
            jsonObj.put(Event.SEGMENTATION_KEY, segmentation);
        }
        return jsonObj;
    }

    /**
     * Validating the 'fromJSON' call
     * Seems like we would want to explode when trying to parse 'null'
     * Probably that's because the value passed should never be 'null'
     */
    @Test
    public void fromJSON_nullJSONObj() {
        try {
            Event.fromJSON(null);
            fail("Expected NPE when calling Event.fromJSON with null");
        } catch (NullPointerException ignored) {
            // success
        }
    }

    /**
     * Validating the 'fromJSON' call
     * Testing how parsing works with a empty json object
     * Since the event does not have a key, it should be parsed to 'null'
     */
    @Test
    public void fromJSON_noKeyCausesJSONException() {
        final JSONObject jsonObj = new JSONObject();
        assertNull(Event.fromJSON(jsonObj));
    }

    /**
     * Validating the 'fromJSON' call
     * JSONObject does have a key set
     * But since the event does not have a valid (it's null) key, it should be parsed to 'null'
     */
    @Test
    public void fromJSON_KeyNull() throws JSONException {
        final JSONObject jsonObj = new JSONObject();
        jsonObj.put(Event.KEY_KEY, JSONObject.NULL);
        assertNull(Event.fromJSON(jsonObj));
    }

    /**
     * Validating the 'fromJSON' call
     * JSONObject does have a key set
     * Since the event does not have a valid (it's empty string) key, it should be parsed to 'null'
     */
    @Test
    public void fromJSON_KeyEmpty() throws JSONException {
        final JSONObject jsonObj = CreateEventJsonObj("", null);
        assertNull(Event.fromJSON(jsonObj));
    }

    /**
     * Validating the 'fromJSON' call
     * Basic event with only a key set
     * JSON obj with only key set
     * Both should be equal after parsing the JSON
     *
     * @throws JSONException
     */
    @Test
    public void fromJSON_KeyOnly() throws JSONException {
        final Event expected = CreateEvent("eventKey");
        final JSONObject jsonObj = CreateEventJsonObj(expected.key, null);
        fromJSON_CompareExpectedToParsed(jsonObj, expected);
    }

    /**
     * Validating the 'fromJSON' call
     * If JSON has other values set to 'null' and only the event key is set
     * then that should behave the same as only the event key is set
     *
     * @throws JSONException
     */
    @Test
    public void fromJSON_KeyOnlyAllOtherNull() throws JSONException {
        final Event expected = CreateEvent("eventKey");
        final JSONObject jsonObj = CreateEventJsonObj(expected.key, JSONObject.NULL);
        jsonObj.put(Event.TIMESTAMP_KEY, JSONObject.NULL);
        jsonObj.put(Event.DAY_OF_WEEK_KEY, JSONObject.NULL);
        jsonObj.put(Event.HOUR_KEY, JSONObject.NULL);
        jsonObj.put(Event.DUR_KEY, JSONObject.NULL);
        jsonObj.put(Event.COUNT_KEY, JSONObject.NULL);
        jsonObj.put(Event.SUM_KEY, JSONObject.NULL);
        jsonObj.put(Event.ID_KEY, JSONObject.NULL);
        jsonObj.put(Event.PV_ID_KEY, JSONObject.NULL);
        jsonObj.put(Event.CV_ID_KEY, JSONObject.NULL);
        fromJSON_CompareExpectedToParsed(jsonObj, expected);
    }

    /**
     * Validating the 'fromJSON' call
     * Checking if all fields except segmentation are parsing correctly
     *
     * @throws JSONException
     */
    @Test
    public void fromJSON_AllFieldsExceptnoSegmentation() throws JSONException {
        final Event expected = new Event();
        expected.key = "eventKey";
        expected.timestamp = 1234;
        expected.dow = 55;
        expected.hour = 89;
        expected.dur = 5;
        expected.count = 42;
        expected.sum = 3.2;
        expected.id = "id";
        expected.cvid = "cvid";
        expected.peid = "peid";
        expected.pvid = "pvid";
        final JSONObject jsonObj = CreateEventJsonObj(expected.key, null);
        jsonObj.put(Event.TIMESTAMP_KEY, expected.timestamp);
        jsonObj.put(Event.DAY_OF_WEEK_KEY, expected.dow);
        jsonObj.put(Event.HOUR_KEY, expected.hour);
        jsonObj.put(Event.DUR_KEY, expected.dur);
        jsonObj.put(Event.COUNT_KEY, expected.count);
        jsonObj.put(Event.SUM_KEY, expected.sum);
        jsonObj.put(Event.ID_KEY, expected.id);
        jsonObj.put(Event.PV_ID_KEY, expected.pvid);
        jsonObj.put(Event.CV_ID_KEY, expected.cvid);
        jsonObj.put(Event.PE_ID_KEY, expected.peid);
        fromJSON_CompareExpectedToParsed(jsonObj, expected);
    }

    /**
     * Validating the 'fromJSON' call
     * Checking specifically segmentation parsing
     * Validating when segmentation JSON would be 'null'
     *
     * @throws JSONException
     */
    @Test
    public void testFromJSON_nullSegmentation() throws JSONException {
        final Event expected = new Event();
        expected.key = "eventKey";
        final JSONObject jsonObj = CreateEventJsonObj(expected.key, JSONObject.NULL);
        fromJSON_CompareExpectedToParsed(jsonObj, expected);
    }

    /**
     * Validating the 'fromJSON' call
     * Checking specifically segmentation parsing
     * Validating when segmentation JSON would be another data type
     * Looks like we want to fail parsing and return 'null'
     *
     * @throws JSONException
     */
    @Test
    public void fromJSON_segmentationNotADictionary() throws JSONException {
        final Event expected = new Event();
        expected.key = "eventKey";
        final JSONObject jsonObj = CreateEventJsonObj(expected.key, 1234);
        assertNull(Event.fromJSON(jsonObj));
    }

    /**
     * Validating the 'fromJSON' call
     * Checking specifically segmentation parsing
     * Validating when segmentation JSON would be empty object
     *
     * @throws JSONException
     */
    @Test
    public void fromJSON_emptySegmentation() throws JSONException {
        final Event expected = new Event();
        expected.key = "eventKey";
        final JSONObject jsonObj = CreateEventJsonObj(expected.key, new JSONObject(new HashMap<>()));
        fromJSON_CompareExpectedToParsed(jsonObj, expected);
    }

    /**
     * Validating the 'fromJSON' call
     * Checking specifically segmentation parsing
     * Validating when segmentation JSON would contain all data types
     *
     * @throws JSONException
     */
    @Test
    public void fromJSON_withSegmentation_nonStringValues() throws JSONException {
        final Event expected = new Event();
        expected.key = "eventKey";
        expected.segmentation = new HashMap<>();
        expected.segmentation.put("sk1", "vall");
        expected.segmentation.put("sk2", 334.33d);
        expected.segmentation.put("segkey", 1234);
        expected.segmentation.put("sk3", true);

        final Map<Object, Object> valueMap = new HashMap<>();
        valueMap.put("segkey", 1234);
        valueMap.put("sk1", "vall");
        valueMap.put("sk2", 334.33d);
        valueMap.put("sk3", true);
        valueMap.put("sk4", new JSONObject());// this should be ignored
        final JSONObject jsonObj = CreateEventJsonObj(expected.key, new JSONObject(valueMap));
        fromJSON_CompareExpectedToParsed(jsonObj, expected);
    }

    /**
     * Validating the 'toJSON' call
     * Checking specifically segmentation handling
     * Validating when segmentation map's are 'null'
     * No segmentation values are written in JSON
     *
     * @throws JSONException
     */
    @Test
    public void toJSON_nullSegmentation() throws JSONException {
        final Event event = new Event();
        event.key = "eventKey";
        final JSONObject jsonObj = event.toJSON();

        assertNull(event.segmentation);

        assertEquals(6, jsonObj.length());
        assertEquals(event.key, jsonObj.getString(Event.KEY_KEY));
        assertEquals(event.count, jsonObj.getInt(Event.COUNT_KEY));
        assertEquals(event.sum, jsonObj.getInt(Event.SUM_KEY), 0.000001);
        assertEquals(event.timestamp, jsonObj.getLong(Event.TIMESTAMP_KEY));
        assertEquals(event.hour, jsonObj.getInt(Event.HOUR_KEY));
        assertEquals(event.dow, jsonObj.getInt(Event.DAY_OF_WEEK_KEY));
        assertTrue(jsonObj.isNull(Event.SEGMENTATION_KEY));
    }

    /**
     * Validating the 'toJSON' call
     * Checking specifically segmentation handling
     * Validating when segmentation map's are set to empty collections
     * No segmentation values are written in JSON
     *
     * @throws JSONException
     */
    @Test
    public void toJSON_emptySegmentation() throws JSONException {
        final Event event = new Event();
        event.key = "eventKey";
        event.segmentation = new HashMap<>();
        final JSONObject jsonObj = event.toJSON();

        assertEquals(6, jsonObj.length());
        assertEquals(event.key, jsonObj.getString(Event.KEY_KEY));
        assertEquals(event.count, jsonObj.getInt(Event.COUNT_KEY));
        assertEquals(event.sum, jsonObj.getInt(Event.SUM_KEY), 0.000001);
        assertEquals(event.timestamp, jsonObj.getLong(Event.TIMESTAMP_KEY));
        assertEquals(event.hour, jsonObj.getInt(Event.HOUR_KEY));
        assertEquals(event.dow, jsonObj.getInt(Event.DAY_OF_WEEK_KEY));
        assertTrue(jsonObj.isNull(Event.SEGMENTATION_KEY));
    }

    /**
     * Validating the 'toJSON' call
     * Serializing all values
     *
     * @throws JSONException
     */
    @Test
    public void toJSON_FullWithSegmentation() throws JSONException {
        final Event event = new Event();
        event.key = "eventKey";
        event.timestamp = 1234;
        event.dow = 67;
        event.hour = 89;
        event.count = 42;
        event.sum = 3.2;
        event.id = "id";
        event.pvid = "pvid";
        event.cvid = "cvid";
        event.peid = "peid";
        event.segmentation = new HashMap<>();
        event.segmentation.put("segkey", "segvalue");
        event.segmentation.put("segkey1", 123);
        event.segmentation.put("segkey2", 544.43d);
        event.segmentation.put("segkey3", true);
        final JSONObject jsonObj = event.toJSON();
        assertEquals(11, jsonObj.length());
        assertEquals(event.key, jsonObj.getString(Event.KEY_KEY));
        assertEquals(event.timestamp, jsonObj.getInt(Event.TIMESTAMP_KEY));
        assertEquals(event.dow, jsonObj.getInt(Event.DAY_OF_WEEK_KEY));
        assertEquals(event.hour, jsonObj.getInt(Event.HOUR_KEY));
        assertEquals(event.count, jsonObj.getInt(Event.COUNT_KEY));
        assertEquals(event.sum, jsonObj.getDouble(Event.SUM_KEY), 0.0000001);
        assertEquals(4, jsonObj.getJSONObject(Event.SEGMENTATION_KEY).length());
        assertEquals(event.segmentation.get("segkey"), jsonObj.getJSONObject(Event.SEGMENTATION_KEY).getString("segkey"));
        assertEquals(event.segmentation.get("segkey1"), jsonObj.getJSONObject(Event.SEGMENTATION_KEY).get("segkey1"));
        assertEquals((Double) event.segmentation.get("segkey2"), jsonObj.getJSONObject(Event.SEGMENTATION_KEY).getDouble("segkey2"), 0.0001d);
        assertEquals(event.segmentation.get("segkey3"), jsonObj.getJSONObject(Event.SEGMENTATION_KEY).getBoolean("segkey3"));
        assertEquals(event.id, jsonObj.getString(Event.ID_KEY));
        assertEquals(event.pvid, jsonObj.getString(Event.PV_ID_KEY));
        assertEquals(event.cvid, jsonObj.getString(Event.CV_ID_KEY));
        assertEquals(event.peid, jsonObj.getString(Event.PE_ID_KEY));
    }

    /**
     * Validating the 'toJSON' call
     * Showing that the sum field will be excluded from the serialized result if it was a NaN
     */
    @Test
    public void toJSON_sumNaNCausesJSONException() throws JSONException {
        final Event event = new Event();
        event.key = "eventKey";
        event.sum = Double.NaN;
        final JSONObject jsonObj = event.toJSON();

        assertEquals(5, jsonObj.length());
        assertEquals(event.key, jsonObj.getString(Event.KEY_KEY));
        assertEquals(event.count, jsonObj.getInt(Event.COUNT_KEY));
        assertEquals(event.timestamp, jsonObj.getLong(Event.TIMESTAMP_KEY));
        assertEquals(event.hour, jsonObj.getInt(Event.HOUR_KEY));
        assertEquals(event.dow, jsonObj.getInt(Event.DAY_OF_WEEK_KEY));
        assertTrue(jsonObj.isNull(Event.SUM_KEY));//this is 'null' because it was not added due to it being NaN
    }
}
