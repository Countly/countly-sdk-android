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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class CountlyStoreTests {
    CountlyStore store;
    final String countlyStoreName = "COUNTLY_STORE";
    final String countlyStoreNamePush = "ly.count.android.api.messaging";

    @Before
    public void setUp() {
        Countly.sharedInstance().setLoggingEnabled(true);
        store = new CountlyStore(getContext(), mock(ModuleLog.class));
        store.clear();
    }

    @After
    public void tearDown() {
        CountlyStore.MAX_REQUESTS = 1000;
        store.clear();
    }

    @Test
    public void testConstructor_nullContext() {
        try {
            new CountlyStore(null, mock(ModuleLog.class));
            fail("expected IllegalArgumentException when calling CountlyStore() ctor with null context");
        } catch (IllegalArgumentException ignored) {
            // success!
        }
    }

    @Test
    public void testConstructor() {
        Context mockContext = mock(Context.class);
        new CountlyStore(mockContext, mock(ModuleLog.class));
        verify(mockContext).getSharedPreferences(countlyStoreName, Context.MODE_PRIVATE);
    }

    @Test
    public void testConnections_prefIsNull() {
        // the clear() call in setUp ensures the pref is not present
        assertTrue(Arrays.equals(new String[0], store.getRequests()));
    }

    @Test
    public void testConnections_prefIsEmptyString() {
        // the following two calls will result in the pref being an empty string
        final String connStr = "blah";
        store.addRequest(connStr);
        store.removeRequest(connStr);
        assertTrue(Arrays.equals(new String[0], store.getRequests()));
    }

    @Test
    public void testConnections_prefHasSingleValue() {
        final String connStr = "blah";
        store.addRequest(connStr);
        assertTrue(Arrays.equals(new String[] { connStr }, store.getRequests()));
    }

    @Test
    public void testConnections_prefHasTwoValues() {
        final String connStr1 = "blah1";
        final String connStr2 = "blah2";
        store.addRequest(connStr1);
        store.addRequest(connStr2);
        assertTrue(Arrays.equals(new String[] { connStr1, connStr2 }, store.getRequests()));
    }

    @Test
    public void testEvents_prefIsNull() {
        // the clear() call in setUp ensures the pref is not present
        assertTrue(Arrays.equals(new String[0], store.getEvents()));
    }

    @Test
    public void testEvents_prefIsEmptyString() {
        // the following two calls will result in the pref being an empty string
        UtilsTime.Instant instant = UtilsTime.getCurrentInstant();
        store.addEvent("eventKey", null, null, null, null, instant.timestampMs, instant.hour, instant.dow, 1, 0.0d, 10.0d);
        store.removeEvents(store.getEventList());
        assertTrue(Arrays.equals(new String[0], store.getEvents()));
    }

    @Test
    public void testEvents_prefHasSingleValue() throws JSONException {
        final String eventKey = "eventKey";
        UtilsTime.Instant instant = UtilsTime.getCurrentInstant();
        store.addEvent(eventKey, null, null, null, null, instant.timestampMs, instant.hour, instant.dow, 1, 0.0d, 10.0d);
        final String[] eventJSONStrings = store.getEvents();
        final JSONObject eventJSONObj = new JSONObject(eventJSONStrings[0]);
        assertEquals(eventKey, eventJSONObj.getString("key"));
        // this is good enough, we verify the entire JSON content is written in later unit tests
    }

    @Test
    public void testEvents_prefHasTwoValues() throws JSONException {
        final String eventKey1 = "eventKey1";
        final String eventKey2 = "eventKey2";
        UtilsTime.Instant instant = UtilsTime.getCurrentInstant();
        store.addEvent(eventKey1, null, null, null, null, instant.timestampMs, instant.hour, instant.dow, 1, 0.0d, 10.0d);

        instant = UtilsTime.getCurrentInstant();
        store.addEvent(eventKey2, null, null, null, null, instant.timestampMs, instant.hour, instant.dow, 1, 0.0d, 10.0d);
        final String[] eventJSONStrs = store.getEvents();
        final JSONObject eventJSONObj1 = new JSONObject(eventJSONStrs[0]);
        assertEquals(eventKey1, eventJSONObj1.getString("key"));
        final JSONObject eventJSONObj2 = new JSONObject(eventJSONStrs[1]);
        assertEquals(eventKey2, eventJSONObj2.getString("key"));
        // this is good enough, we verify the entire JSON content is written in later unit tests
    }

    @Test
    public void testEventsList_noEvents() {
        assertEquals(new ArrayList<Event>(0), store.getEventList());
    }

    @Test
    public void testEventsList_singleEvent() {
        final Event event1 = new Event();
        event1.key = "eventKey1";
        event1.timestamp = UtilsTime.currentTimestampMs();
        event1.count = 1;
        event1.dur = 10.0d;
        store.addEvent(event1.key, event1.segmentation, null, null, null, event1.timestamp, event1.hour, event1.dow, event1.count, event1.sum, event1.dur);
        final List<Event> expected = new ArrayList<>(1);
        expected.add(event1);
        final List<Event> actual = store.getEventList();
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
        store.addEvent(event1.key, event1.segmentation, null, null, null, event1.timestamp, event1.hour, event1.dow, event1.count, event1.sum, event1.dur);
        store.addEvent(event2.key, event2.segmentation, null, null, null, event2.timestamp, event2.hour, event2.dow, event2.count, event2.sum, event2.dur);
        store.addEvent(event3.key, event3.segmentation, null, null, null, event3.timestamp, event3.hour, event3.dow, event3.count, event3.sum, event3.dur);
        final List<Event> expected = new ArrayList<>(3);
        expected.add(event2);
        expected.add(event3);
        expected.add(event1);
        final List<Event> actual = store.getEventList();
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
        final SharedPreferences prefs = getContext().getSharedPreferences(countlyStoreName, Context.MODE_PRIVATE);
        prefs.edit().putString("EVENTS", joinedEventsWithBadJSON).commit();

        final List<Event> expected = new ArrayList<>(2);
        expected.add(event1);
        expected.add(event2);
        final List<Event> actual = store.getEventList();
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
        final SharedPreferences prefs = getContext().getSharedPreferences(countlyStoreName, Context.MODE_PRIVATE);
        prefs.edit().putString("EVENTS", joinedEventsWithBadJSON).commit();

        final List<Event> expected = new ArrayList<>(2);
        expected.add(event1);
        expected.add(event2);
        final List<Event> actual = store.getEventList();
        assertEquals(expected, actual);
    }

    @Test
    public void testIsEmptyConnections_prefIsNull() {
        // the clear() call in setUp ensures the pref is not present
        assertTrue(store.noRequestsAvailable());
    }

    @Test
    public void testIsEmptyConnections_prefIsEmpty() {
        // the following two calls will result in the pref being an empty string
        final String connStr = "blah";
        store.addRequest(connStr);
        store.removeRequest(connStr);
        assertTrue(store.noRequestsAvailable());
    }

    @Test
    public void testIsEmptyConnections_prefIsPopulated() {
        final String connStr = "blah";
        store.addRequest(connStr);
        assertFalse(store.noRequestsAvailable());
    }

    @Test
    public void testAddConnection_nullStr() {
        store.addRequest(null);
        assertTrue(store.noRequestsAvailable());
    }

    @Test
    public void testAddConnection_emptyStr() {
        store.addRequest("");
        assertTrue(store.noRequestsAvailable());
    }

    @Test
    public void testRemoveConnection_nullStr() {
        store.addRequest("blah");
        store.removeRequest(null);
        assertFalse(store.noRequestsAvailable());
    }

    @Test
    public void testRemoveConnection_emptyStr() {
        store.addRequest("blah");
        store.removeRequest("");
        assertFalse(store.noRequestsAvailable());
    }

    @Test
    public void testRemoveConnection_firstConn() {
        store.addRequest("blah");
        assertFalse(store.noRequestsAvailable());
        store.removeRequest("blah");
        assertTrue(store.noRequestsAvailable());
    }

    @Test
    public void testRemoveConnection_notFirstConn() {
        store.addRequest("blah1");
        store.addRequest("blah2");
        assertEquals(2, store.getRequests().length);
        store.removeRequest("blah2");
        assertEquals(1, store.getRequests().length);
    }

    @Test
    public void testRemoveConnection_onlyRemovesFirstMatchingOne() {
        store.addRequest("blah1");
        store.addRequest("blah2");
        store.addRequest("blah1");
        assertEquals(3, store.getRequests().length);
        store.removeRequest("blah1");
        assertTrue(Arrays.equals(new String[] { "blah2", "blah1" }, store.getRequests()));
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

        store.addEvent(event1.key, event1.segmentation, null, null, null, event1.timestamp, event1.hour, event1.dow, event1.count, event1.sum, event1.dur);

        final List<Event> addedEvents = store.getEventList();
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

        store.addEvent(event1.key, event1.segmentation, null, null, null, event1.timestamp, event1.hour, event1.dow, event1.count, event1.sum, event1.dur);
        store.addEvent(event2.key, event2.segmentation, null, null, null, event2.timestamp, event2.hour, event2.dow, event2.count, event2.sum, event2.dur);

        final List<Event> eventsToRemove = store.getEventList();

        store.addEvent(event3.key, event3.segmentation, null, null, null, event3.timestamp, event3.hour, event3.dow, event3.count, event3.sum, event3.dur);

        store.removeEvents(eventsToRemove);

        final List<Event> events = store.getEventList();
        assertEquals(1, events.size());
        assertEquals(event3, events.get(0));
    }

    @Test
    public void testClear() {
        final SharedPreferences prefs = getContext().getSharedPreferences(countlyStoreName, Context.MODE_PRIVATE);
        assertFalse(prefs.contains("EVENTS"));
        assertFalse(prefs.contains("CONNECTIONS"));
        store.addRequest("blah");
        UtilsTime.Instant instant = UtilsTime.getCurrentInstant();
        store.addEvent("eventKey", null, null, null, null, instant.timestampMs, instant.hour, instant.dow, 1, 0.0d, 10.0d);
        assertTrue(prefs.contains("EVENTS"));
        assertTrue(prefs.contains("CONNECTIONS"));
        store.clear();
        assertFalse(prefs.contains("EVENTS"));
        assertFalse(prefs.contains("CONNECTIONS"));
    }

    @Test
    // just making sure we can get and set simple string preferences
    public void setGetPreference() {
        final SharedPreferences prefs = getContext().getSharedPreferences(countlyStoreName, Context.MODE_PRIVATE);
        String keyX = "xxx";

        assertNull(store.getPreference(keyX));
        store.setPreference(keyX, "asd");
        assertEquals("asd", store.getPreference(keyX));

        store.setPreference(keyX, "123");
        assertEquals("123", store.getPreference(keyX));

        store.setPreference(keyX, null);
        assertNull(store.getPreference(keyX));
        assertFalse(prefs.contains(keyX));
    }

    @Test
    public void setGetMessagingProvider() {
        assertEquals(0, CountlyStore.getMessagingProvider(getContext()));
        CountlyStore.storeMessagingProvider(1234, getContext());
        assertEquals(1234, CountlyStore.getMessagingProvider(getContext()));
    }

    @Test
    public void setGetMessagingMode() {
        assertEquals(-1, CountlyStore.getLastMessagingMode(getContext()));
        CountlyStore.cacheLastMessagingMode(1234, getContext());
        assertEquals(1234, CountlyStore.getLastMessagingMode(getContext()));
    }

    @Test
    public void setGetClearCachedPushData() {
        final SharedPreferences prefs = getContext().getSharedPreferences(countlyStoreNamePush, Context.MODE_PRIVATE);
        String keyX = "PUSH_ACTION_ID";
        String keyY = "PUSH_ACTION_INDEX";
        assertFalse(prefs.contains(keyX));
        assertFalse(prefs.contains(keyY));

        assertEquals(new String[] { null, null }, store.getCachedPushData());
        CountlyStore.cachePushData("asdf", "1234", getContext());
        assertEquals(new String[] { "asdf", "1234" }, store.getCachedPushData());

        store.clearCachedPushData();
        assertEquals(new String[] { null, null }, store.getCachedPushData());
        assertFalse(prefs.contains(keyX));
        assertFalse(prefs.contains(keyY));
    }

    @Test
    public void setGetConsentPush() {
        assertEquals(false, CountlyStore.getConsentPushNoInit(getContext()));
        assertEquals(false, store.getConsentPush());
        store.setConsentPush(true);
        assertEquals(true, store.getConsentPush());
        assertEquals(true, CountlyStore.getConsentPushNoInit(getContext()));
        store.setConsentPush(false);
        assertEquals(false, store.getConsentPush());
    }

    @Test
    public void setGetAdvertisingId() {
        store.setCachedAdvertisingId("qwe");
        assertEquals("qwe", store.getCachedAdvertisingId());
    }

    @Test
    public void setGetRemoteConfigValues() {
        store.setRemoteConfigValues("qwe");
        assertEquals("qwe", store.getRemoteConfigValues());
    }

    @Test
    public void removeConnection_nonExisting() {
        store.addRequest("blah1");
        store.addRequest("blah2");
        assertEquals(2, store.getRequests().length);
        store.removeRequest("blah3");
        assertEquals(2, store.getRequests().length);
        assertTrue(Arrays.equals(new String[] { "blah1", "blah2" }, store.getRequests()));
    }

    @Test
    public void replaceConnections() {
        store.addRequest("blah1");
        store.addRequest("blah2");
        assertTrue(Arrays.equals(new String[] { "blah1", "blah2" }, store.getRequests()));
        store.replaceRequests(new String[] { "aa", "bb", "cc" });
        assertTrue(Arrays.equals(new String[] { "aa", "bb", "cc" }, store.getRequests()));

        List<String> newList = new ArrayList<>();
        newList.add("33");
        newList.add("pp");
        store.replaceRequestList(newList);
        assertTrue(Arrays.equals(new String[] { "33", "pp" }, store.getRequests()));
    }

    @Test
    public void deleteOldestConnection() {
        store.addRequest("blah1");
        store.addRequest("blah2");
        store.addRequest("blah3");
        assertTrue(Arrays.equals(new String[] { "blah1", "blah2", "blah3" }, store.getRequests()));
        store.deleteOldestRequest();
        assertTrue(Arrays.equals(new String[] { "blah2", "blah3" }, store.getRequests()));
    }

    @Test
    public void addConnectionMaxRequests() {
        CountlyStore.MAX_REQUESTS = 2;
        store.addRequest("blah1");
        store.addRequest("blah2");
        assertTrue(Arrays.equals(new String[] { "blah1", "blah2" }, store.getRequests()));

        store.addRequest("blah3");
        assertTrue(Arrays.equals(new String[] { "blah2", "blah3" }, store.getRequests()));

        store.addRequest("123");
        assertTrue(Arrays.equals(new String[] { "blah3", "123" }, store.getRequests()));

        store.addRequest("1qwe");
        assertTrue(Arrays.equals(new String[] { "123", "1qwe" }, store.getRequests()));
    }

    /**
     * Validate that setting and retrieving device ID and device ID type works as intended
     */
    @Test
    public void testDeviceIDStorage() {
        String[] values = new String[] { "aa", "bb", "cc"};
        String[] values2 = new String[] { "11", "22", "33"};
        StorageProvider sp = store;
        store.clear();

        assertNull(sp.getDeviceID());
        assertNull(sp.getDeviceIDType());

        for (int a = 0 ; a <  values.length ; a++) {
            sp.setDeviceID(values[a]);
            assertEquals(values[a], sp.getDeviceID());
            assertNotEquals(values[a], sp.getDeviceIDType());

            sp.setDeviceIDType(values2[a]);
            assertEquals(values2[a], sp.getDeviceIDType());

            assertEquals(values[a], sp.getDeviceID());
        }
    }
}
