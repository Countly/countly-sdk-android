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

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CountlyStoreTests {
    CountlyStore store;

    @Before
    public void setUp() {
        Countly.sharedInstance().setLoggingEnabled(true);
        store = new CountlyStore(getContext());
        store.clear();
    }

    @After
    public void tearDown() {
        store.clear();
    }

    @Test
    public void testConstructor_nullContext() {
        try {
            new CountlyStore(null);
            fail("expected IllegalArgumentException when calling CountlyStore() ctor with null context");
        } catch (IllegalArgumentException ignored) {
            // success!
        }
    }

    @Test
    public void testConstructor() {
        Context mockContext = mock(Context.class);
        new CountlyStore(mockContext);
        verify(mockContext).getSharedPreferences("COUNTLY_STORE", Context.MODE_PRIVATE);
    }

    @Test
    public void testConnections_prefIsNull() {
        // the clear() call in setUp ensures the pref is not present
        assertTrue(Arrays.equals(new String[0], store.connections()));
    }

    @Test
    public void testConnections_prefIsEmptyString() {
        // the following two calls will result in the pref being an empty string
        final String connStr = "blah";
        store.addConnection(connStr);
        store.removeConnection(connStr);
        assertTrue(Arrays.equals(new String[0], store.connections()));
    }

    @Test
    public void testConnections_prefHasSingleValue() {
        final String connStr = "blah";
        store.addConnection(connStr);
        assertTrue(Arrays.equals(new String[]{connStr}, store.connections()));
    }

    @Test
    public void testConnections_prefHasTwoValues() {
        final String connStr1 = "blah1";
        final String connStr2 = "blah2";
        store.addConnection(connStr1);
        store.addConnection(connStr2);
        assertTrue(Arrays.equals(new String[]{connStr1,connStr2}, store.connections()));
    }

    @Test
    public void testEvents_prefIsNull() {
        // the clear() call in setUp ensures the pref is not present
        assertTrue(Arrays.equals(new String[0], store.events()));
    }

    @Test
    public void testEvents_prefIsEmptyString() {
        // the following two calls will result in the pref being an empty string
        UtilsTime.Instant instant = UtilsTime.getCurrentInstant();
        store.addEvent("eventKey", null, null, null, instant.timestampMs, instant.hour, instant.dow, 1, 0.0d, 10.0d);
        store.removeEvents(store.eventsList());
        assertTrue(Arrays.equals(new String[0], store.events()));
    }

    @Test
    public void testEvents_prefHasSingleValue() throws JSONException {
        final String eventKey = "eventKey";
        UtilsTime.Instant instant = UtilsTime.getCurrentInstant();
        store.addEvent(eventKey, null, null, null, instant.timestampMs, instant.hour, instant.dow, 1, 0.0d, 10.0d);
        final String[] eventJSONStrings = store.events();
        final JSONObject eventJSONObj = new JSONObject(eventJSONStrings[0]);
        assertEquals(eventKey, eventJSONObj.getString("key"));
        // this is good enough, we verify the entire JSON content is written in later unit tests
    }

    @Test
    public void testEvents_prefHasTwoValues() throws JSONException {
        final String eventKey1 = "eventKey1";
        final String eventKey2 = "eventKey2";
        UtilsTime.Instant instant = UtilsTime.getCurrentInstant();
        store.addEvent(eventKey1, null, null, null, instant.timestampMs, instant.hour, instant.dow, 1, 0.0d, 10.0d);

        instant = UtilsTime.getCurrentInstant();
        store.addEvent(eventKey2, null, null, null, instant.timestampMs, instant.hour, instant.dow, 1, 0.0d, 10.0d);
        final String[] eventJSONStrs = store.events();
        final JSONObject eventJSONObj1 = new JSONObject(eventJSONStrs[0]);
        assertEquals(eventKey1, eventJSONObj1.getString("key"));
        final JSONObject eventJSONObj2 = new JSONObject(eventJSONStrs[1]);
        assertEquals(eventKey2, eventJSONObj2.getString("key"));
        // this is good enough, we verify the entire JSON content is written in later unit tests
    }

    @Test
    public void testEventsList_noEvents() {
        assertEquals(new ArrayList<Event>(0), store.eventsList());
    }

    @Test
    public void testEventsList_singleEvent() {
        final Event event1 = new Event();
        event1.key = "eventKey1";
        event1.timestamp = UtilsTime.currentTimestampMs();
        event1.count = 1;
        event1.dur = 10.0d;
        store.addEvent(event1.key, event1.segmentation, null, null, event1.timestamp, event1.hour, event1.dow, event1.count, event1.sum, event1.dur);
        final List<Event> expected = new ArrayList<>(1);
        expected.add(event1);
        final List<Event> actual = store.eventsList();
        assertEquals(expected, actual);
    }

    @Test
    public void testEventsList_sortingOfMultipleEvents() {
        final Event event1 = new Event();
        event1.key = "eventKey1";
        event1.timestamp = UtilsTime.currentTimestampMs();
        event1.count = 1;
        event1.dur = 10.0d;
        final Event event2 = new Event();
        event2.key = "eventKey2";
        event2.timestamp = UtilsTime.currentTimestampMs() - 60000;
        event2.count = 1;
        event2.dur = 10.0d;
        final Event event3 = new Event();
        event3.key = "eventKey3";
        event3.timestamp = UtilsTime.currentTimestampMs() - 30000;
        event3.count = 1;
        event3.dur = 10.0d;
        store.addEvent(event1.key, event1.segmentation, null, null, event1.timestamp, event1.hour, event1.dow, event1.count, event1.sum, event1.dur);
        store.addEvent(event2.key, event2.segmentation, null, null, event2.timestamp, event2.hour, event2.dow, event2.count, event2.sum, event2.dur);
        store.addEvent(event3.key, event3.segmentation, null, null, event3.timestamp, event3.hour, event3.dow, event3.count, event3.sum, event3.dur);
        final List<Event> expected = new ArrayList<>(3);
        expected.add(event2);
        expected.add(event3);
        expected.add(event1);
        final List<Event> actual = store.eventsList();
        assertEquals(expected, actual);
    }

    @Test
    public void testEventsList_badJSON() {
        final Event event1 = new Event();
        UtilsTime.Instant instant = UtilsTime.getCurrentInstant();
        event1.key = "eventKey1";
        event1.timestamp = instant.timestampMs - 60000;
        event1.hour = instant.hour;
        event1.dow = instant.dow;
        event1.count = 1;
        final Event event2 = new Event();
        instant = UtilsTime.getCurrentInstant();
        event2.key = "eventKey2";
        event2.timestamp = instant.timestampMs;
        event2.hour = instant.hour;
        event2.dow = instant.dow;
        event2.count = 1;

        final String joinedEventsWithBadJSON = event1.toJSON().toString() + ":::blah:::" + event2.toJSON().toString();
        final SharedPreferences prefs = getContext().getSharedPreferences("COUNTLY_STORE", Context.MODE_PRIVATE);
        prefs.edit().putString("EVENTS", joinedEventsWithBadJSON).commit();

        final List<Event> expected = new ArrayList<>(2);
        expected.add(event1);
        expected.add(event2);
        final List<Event> actual = store.eventsList();
        assertEquals(expected, actual);
    }

    @Test
    public void testEventsList_EventFromJSONReturnsNull() {
        final Event event1 = new Event();
        UtilsTime.Instant instant = UtilsTime.getCurrentInstant();
        event1.key = "eventKey1";
        event1.timestamp = instant.timestampMs - 60000;
        event1.hour = instant.hour;
        event1.dow = instant.dow;
        event1.count = 1;
        final Event event2 = new Event();
        instant = UtilsTime.getCurrentInstant();
        event2.key = "eventKey2";
        event2.timestamp = instant.timestampMs;
        event2.hour = instant.hour;
        event2.dow = instant.dow;
        event2.count = 1;

        final String joinedEventsWithBadJSON = event1.toJSON().toString() + ":::{\"key\":null}:::" + event2.toJSON().toString();
        final SharedPreferences prefs = getContext().getSharedPreferences("COUNTLY_STORE", Context.MODE_PRIVATE);
        prefs.edit().putString("EVENTS", joinedEventsWithBadJSON).commit();

        final List<Event> expected = new ArrayList<>(2);
        expected.add(event1);
        expected.add(event2);
        final List<Event> actual = store.eventsList();
        assertEquals(expected, actual);
    }

    @Test
    public void testIsEmptyConnections_prefIsNull() {
        // the clear() call in setUp ensures the pref is not present
        assertTrue(store.isEmptyConnections());
    }

    @Test
    public void testIsEmptyConnections_prefIsEmpty() {
        // the following two calls will result in the pref being an empty string
        final String connStr = "blah";
        store.addConnection(connStr);
        store.removeConnection(connStr);
        assertTrue(store.isEmptyConnections());
    }

    @Test
    public void testIsEmptyConnections_prefIsPopulated() {
        final String connStr = "blah";
        store.addConnection(connStr);
        assertFalse(store.isEmptyConnections());
    }

    @Test
    public void testAddConnection_nullStr() {
        store.addConnection(null);
        assertTrue(store.isEmptyConnections());
    }

    @Test
    public void testAddConnection_emptyStr() {
        store.addConnection("");
        assertTrue(store.isEmptyConnections());
    }

    @Test
    public void testRemoveConnection_nullStr() {
        store.addConnection("blah");
        store.removeConnection(null);
        assertFalse(store.isEmptyConnections());
    }

    @Test
    public void testRemoveConnection_emptyStr() {
        store.addConnection("blah");
        store.removeConnection("");
        assertFalse(store.isEmptyConnections());
    }

    @Test
    public void testRemoveConnection_firstConn() {
        store.addConnection("blah");
        assertFalse(store.isEmptyConnections());
        store.removeConnection("blah");
        assertTrue(store.isEmptyConnections());
    }

    @Test
    public void testRemoveConnection_notFirstConn() {
        store.addConnection("blah1");
        store.addConnection("blah2");
        assertEquals(2, store.connections().length);
        store.removeConnection("blah2");
        assertEquals(1, store.connections().length);
    }

    @Test
    public void testRemoveConnection_onlyRemovesFirstMatchingOne() {
        store.addConnection("blah1");
        store.addConnection("blah2");
        store.addConnection("blah1");
        assertEquals(3, store.connections().length);
        store.removeConnection("blah1");
        assertTrue(Arrays.equals(new String[]{"blah2", "blah1"}, store.connections()));
    }

    @Test
    public void testAddEvent() {
        final Event event1 = new Event();
        event1.key = "eventKey1";
        event1.timestamp = UtilsTime.currentTimestampMs() - 60000;
        event1.count = 42;
        event1.sum = 3.2;
        event1.dur = 10.0d;
        event1.segmentation = new HashMap<>(2);
        event1.segmentation.put("segKey1", "segValue1");
        event1.segmentation.put("segKey2", "segValue2");

        store.addEvent(event1.key, event1.segmentation, null, null, event1.timestamp, event1.hour, event1.dow, event1.count, event1.sum, event1.dur);

        final List<Event> addedEvents = store.eventsList();
        assertEquals(1, addedEvents.size());
        final Event addedEvent = addedEvents.get(0);
        assertEquals(event1, addedEvent);
        assertEquals(event1.count, addedEvent.count);
        assertEquals(event1.sum, addedEvent.sum, 0.0000001);
    }

    @Test
    public void testRemoveEvents() {
        final Event event1 = new Event();
        event1.key = "eventKey1";
        event1.timestamp = UtilsTime.currentTimestampMs() - 60000;
        event1.count = 1;
        event1.dur = 10.0d;
        final Event event2 = new Event();
        event2.key = "eventKey2";
        event2.timestamp = UtilsTime.currentTimestampMs() - 30000;
        event2.count = 1;
        event2.dur = 10.0d;
        final Event event3 = new Event();
        event3.key = "eventKey2";
        event3.timestamp = UtilsTime.currentTimestampMs();
        event3.count = 1;
        event3.dur = 10.0d;

        store.addEvent(event1.key, event1.segmentation, null, null, event1.timestamp, event1.hour, event1.dow, event1.count, event1.sum, event1.dur);
        store.addEvent(event2.key, event2.segmentation, null, null, event2.timestamp, event2.hour, event2.dow, event2.count, event2.sum, event2.dur);

        final List<Event> eventsToRemove = store.eventsList();

        store.addEvent(event3.key, event3.segmentation, null, null, event3.timestamp, event3.hour, event3.dow, event3.count, event3.sum, event3.dur);

        store.removeEvents(eventsToRemove);

        final List<Event> events = store.eventsList();
        assertEquals(1, events.size());
        assertEquals(event3, events.get(0));
    }

    @Test
    public void testClear() {
        final SharedPreferences prefs = getContext().getSharedPreferences("COUNTLY_STORE", Context.MODE_PRIVATE);
        assertFalse(prefs.contains("EVENTS"));
        assertFalse(prefs.contains("CONNECTIONS"));
        store.addConnection("blah");
        UtilsTime.Instant instant = UtilsTime.getCurrentInstant();
        store.addEvent("eventKey", null, null, null, instant.timestampMs, instant.hour, instant.dow, 1, 0.0d, 10.0d);
        assertTrue(prefs.contains("EVENTS"));
        assertTrue(prefs.contains("CONNECTIONS"));
        store.clear();
        assertFalse(prefs.contains("EVENTS"));
        assertFalse(prefs.contains("CONNECTIONS"));
    }
}
