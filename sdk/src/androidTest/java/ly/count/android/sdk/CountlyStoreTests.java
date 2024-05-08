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
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
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

import static org.junit.Assert.assertArrayEquals;
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
    StorageProvider sp;
    final String countlyStoreName = "COUNTLY_STORE";
    final String countlyStoreNamePush = "ly.count.android.api.messaging";

    final String[] eKeys = TestUtils.eKeys;
    final String[] requestEntries = TestUtils.requestEntries;
    final String[] oldRequestEntries = TestUtils.tooOldRequestEntries;

    @Before
    public void setUp() {
        Countly.sharedInstance().setLoggingEnabled(true);
        store = new CountlyStore(TestUtils.getContext(), mock(ModuleLog.class));
        sp = store;
        store.clear();
    }

    @After
    public void tearDown() {
        store.clear();
    }

    /**
     * Convenience function for recording event to countly store and generating a current timestamp
     * This wraps the messy recording function
     *
     * @param eventKey
     * @param cs
     */
    void RecordEvent(String eventKey, CountlyStore cs) {
        UtilsTime.Instant instant = UtilsTime.getCurrentInstant();
        cs.recordEventToEventQueue(eventKey, null, 1, 0.0d, 10.0d, instant.timestampMs, instant.hour, instant.dow, null, null, null, null);
    }

    /**
     * Convenience function for tests
     * Uses the event object to record an event to EQ
     * This wraps the messy recording function
     *
     * @param e
     * @param cs
     */
    void RecordEvent(Event e, CountlyStore cs) {
        cs.recordEventToEventQueue(e.key, e.segmentation, e.count, e.sum, e.dur, e.timestamp, e.hour, e.dow, e.id, e.pvid, e.cvid, e.peid);
    }

    /**
     * Convenience function for creating an event with just a event key
     * It populates other fields
     *
     * @param eventKey
     * @return
     */
    Event CreateEvent(String eventKey) {
        Event e = new Event();
        e.key = eventKey;
        e.hour = 3;
        e.dow = 5;
        e.cvid = "a";
        e.pvid = "t";
        e.peid = "o";

        return e;
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

    /**
     * When store is cleared, it should still return empty array
     */
    @Test
    public void getRequests_InitialState() {
        assertArrayEquals(new String[0], store.getRequests());
    }

    /**
     * Adding and removing the same requests should produce an empty queue that is returned as empty array
     */
    @Test
    public void getRequests_AddRemoveRequest() {
        store.addRequest(requestEntries[0], false);
        store.removeRequest(requestEntries[0]);
        assertArrayEquals(new String[0], store.getRequests());
    }

    /**
     * Adding a single entry to the request queue should work as intended
     */
    @Test
    public void getRequests_AddSingleEntry() {
        store.addRequest(requestEntries[0], false);
        assertArrayEquals(new String[] { requestEntries[0] }, store.getRequests());
    }

    /**
     * Adding two entries to the request queue should work as intended
     */
    @Test
    public void getRequests_AddTwoEntries() {
        store.addRequest(requestEntries[0], false);
        store.addRequest(requestEntries[1], false);
        assertArrayEquals(new String[] { requestEntries[0], requestEntries[1] }, store.getRequests());
    }

    /**
     * When store is cleared, it should still return empty array
     */
    @Test
    public void getEvents_InitialState() {
        assertArrayEquals(new String[0], store.getEvents());
    }

    /**
     * Adding an event and then removing the whole list of events should result in an empty String array when requesting the list again.
     * This should not return 'null'
     */
    @Test
    public void getEvents_addEventRemoveAll() {
        RecordEvent(eKeys[0], store);
        store.removeEvents(store.getEventList());
        assertArrayEquals(new String[0], store.getEvents());
    }

    @Test
    public void getEvents_addSingleEntry() throws JSONException {
        RecordEvent(eKeys[0], store);
        final String[] eventJSONStrings = store.getEvents();
        assertEquals(eKeys[0], new JSONObject(eventJSONStrings[0]).getString("key"));
        // this is good enough, we verify the entire JSON content is written in later unit tests
    }

    @Test
    public void getEvents_addTwoEntries() throws JSONException {
        RecordEvent(eKeys[0], store);
        RecordEvent(eKeys[1], store);

        final String[] eventJSONStrs = store.getEvents();
        assertEquals(eKeys[0], new JSONObject(eventJSONStrs[0]).getString("key"));
        assertEquals(eKeys[1], new JSONObject(eventJSONStrs[1]).getString("key"));
        // this is good enough, we verify the entire JSON content is written in later unit tests
    }

    @Test
    public void getEventList_InitialState() {
        assertEquals(new ArrayList<Event>(0), store.getEventList());
    }

    @Test
    public void getEventList_singleEvent() {
        final Event event1 = CreateEvent(eKeys[1]);
        RecordEvent(event1, store);
        final List<Event> expected = new ArrayList<>(1);
        expected.add(event1);
        final List<Event> actual = store.getEventList();
        assertEquals(expected, actual);
    }

    /**
     * Validating 'getEventList' to make sure events are correctly ordered by their timestamp
     */
    @Test
    public void getEventList_sortingOfMultipleEvents() {
        final Event event1 = CreateEvent(eKeys[0]);
        event1.timestamp = UtilsTime.currentTimestampMs();
        final Event event2 = CreateEvent(eKeys[1]);
        event2.timestamp = UtilsTime.currentTimestampMs() - 60_000;
        final Event event3 = CreateEvent(eKeys[2]);
        event3.timestamp = UtilsTime.currentTimestampMs() - 30_000;

        RecordEvent(event1, store);
        RecordEvent(event2, store);
        RecordEvent(event3, store);
        final List<Event> expected = new ArrayList<>(3);
        expected.add(event2);
        expected.add(event3);
        expected.add(event1);
        final List<Event> actual = store.getEventList();
        assertEquals(expected, actual);
    }

    /**
     * Store events with a bad string snippet in the middle.
     * The list should correctly return the correct entries and bad entry should be skipped
     */
    @Test
    public void getEventList_badJSONStored() {
        final Event event1 = CreateEvent(eKeys[0]);
        event1.timestamp = UtilsTime.getCurrentInstant().timestampMs;

        final Event event2 = CreateEvent(eKeys[1]);
        event2.timestamp = UtilsTime.getCurrentInstant().timestampMs - 60_000;

        //insert bad entry
        final String joinedEventsWithBadJSON = event1.toJSON().toString() + ":::blah:::" + event2.toJSON().toString();
        final SharedPreferences prefs = TestUtils.getContext().getSharedPreferences(countlyStoreName, Context.MODE_PRIVATE);
        prefs.edit().putString("EVENTS", joinedEventsWithBadJSON).commit();

        final List<Event> expected = new ArrayList<>(2);
        expected.add(event2);
        expected.add(event1);
        final List<Event> actual = store.getEventList();
        assertEquals(expected, actual);
    }

    /**
     * Store events with a null entry in the middle.
     * The list should correctly return the correct entries and bad entry should be skipped
     */
    @Test
    public void getEventList_nullEntryStored() {
        final Event event1 = CreateEvent(eKeys[2]);
        event1.timestamp = UtilsTime.getCurrentInstant().timestampMs;

        final Event event2 = CreateEvent(eKeys[3]);
        event2.timestamp = UtilsTime.getCurrentInstant().timestampMs - 60_000;

        //insert null entry
        final String joinedEventsWithBadJSON = event1.toJSON().toString() + ":::{\"key\":null}:::" + event2.toJSON().toString();
        final SharedPreferences prefs = TestUtils.getContext().getSharedPreferences(countlyStoreName, Context.MODE_PRIVATE);
        prefs.edit().putString("EVENTS", joinedEventsWithBadJSON).commit();

        final List<Event> expected = new ArrayList<>(2);
        expected.add(event2);
        expected.add(event1);
        final List<Event> actual = store.getEventList();
        assertEquals(expected, actual);
    }

    @Test
    public void removeRequest_notFirstConn() {
        store.addRequest(requestEntries[0], false);
        store.addRequest(requestEntries[1], false);
        assertEquals(2, store.getRequests().length);
        store.removeRequest(requestEntries[1]);
        assertEquals(1, store.getRequests().length);
    }

    @Test
    public void removeRequest_onlyRemovesFirstMatchingOne() {
        store.addRequest(requestEntries[0], false);
        store.addRequest(requestEntries[1], false);
        store.addRequest(requestEntries[0], false);
        assertEquals(3, store.getRequests().length);
        store.removeRequest(requestEntries[0]);
        assertTrue(Arrays.equals(new String[] { requestEntries[1], requestEntries[0] }, store.getRequests()));
    }

    @Test
    public void recordEventToEventQueue() {
        final Event event = CreateEvent(eKeys[3]);
        event.segmentation = new HashMap<>(2);
        event.segmentation.put("segKey1", "segValue1");
        event.segmentation.put("segKey2", "segValue2");

        RecordEvent(event, store);

        final List<Event> addedEvents = store.getEventList();
        assertEquals(1, addedEvents.size());
        final Event addedEvent = addedEvents.get(0);
        assertEquals(event, addedEvent);
        assertEquals(event.count, addedEvent.count);
        assertEquals(event.sum, addedEvent.sum, 0.000_000_1);
    }

    @Test
    public void testRemoveEvents() {
        final Event event1 = CreateEvent(eKeys[1]);
        event1.timestamp = UtilsTime.currentTimestampMs() - 60_000;

        final Event event2 = CreateEvent(eKeys[2]);
        event2.timestamp = UtilsTime.currentTimestampMs() - 30_000;

        final Event event3 = CreateEvent(eKeys[3]);
        event3.timestamp = UtilsTime.currentTimestampMs();

        RecordEvent(event1, store);
        RecordEvent(event2, store);

        final List<Event> eventsToRemove = store.getEventList();

        RecordEvent(event3, store);

        store.removeEvents(eventsToRemove);

        final List<Event> events = store.getEventList();
        assertEquals(1, events.size());
        assertEquals(event3, events.get(0));
    }

    @Test
    public void testClear() {
        final SharedPreferences prefs = TestUtils.getContext().getSharedPreferences(countlyStoreName, Context.MODE_PRIVATE);
        assertFalse(prefs.contains("EVENTS"));
        assertFalse(prefs.contains("CONNECTIONS"));
        store.addRequest("blah", false);

        RecordEvent(eKeys[0], store);
        assertTrue(prefs.contains("EVENTS"));
        assertTrue(prefs.contains("CONNECTIONS"));

        store.clear();
        assertFalse(prefs.contains("EVENTS"));
        assertFalse(prefs.contains("CONNECTIONS"));
    }

    @Test
    public void setGetMessagingProvider() {
        assertEquals(0, CountlyStore.getMessagingProvider(TestUtils.getContext()));
        CountlyStore.storeMessagingProvider(1234, TestUtils.getContext());
        assertEquals(1234, CountlyStore.getMessagingProvider(TestUtils.getContext()));
    }

    @Test
    public void setGetClearCachedPushData() {
        final SharedPreferences prefs = TestUtils.getContext().getSharedPreferences(countlyStoreNamePush, Context.MODE_PRIVATE);
        String keyX = "PUSH_ACTION_ID";
        String keyY = "PUSH_ACTION_INDEX";
        assertFalse(prefs.contains(keyX));
        assertFalse(prefs.contains(keyY));

        assertEquals(new String[] { null, null }, store.getCachedPushData());
        CountlyStore.cachePushData("asdf", "1234", TestUtils.getContext());
        assertEquals(new String[] { "asdf", "1234" }, store.getCachedPushData());

        store.clearCachedPushData();
        assertEquals(new String[] { null, null }, store.getCachedPushData());
        assertFalse(prefs.contains(keyX));
        assertFalse(prefs.contains(keyY));
    }

    @Test
    public void setGetConsentPush() {
        assertEquals(false, CountlyStore.getConsentPushNoInit(TestUtils.getContext()));
        assertEquals(false, store.getConsentPush());
        store.setConsentPush(true);
        assertEquals(true, store.getConsentPush());
        assertEquals(true, CountlyStore.getConsentPushNoInit(TestUtils.getContext()));
        store.setConsentPush(false);
        assertEquals(false, store.getConsentPush());
    }

    /**
     * Validate that the setter and getter for cached advertising ID is working as expected
     */
    @Test
    public void setGetAdvertisingId() {
        assertEquals("", sp.getCachedAdvertisingId());

        sp.setCachedAdvertisingId("qwe");
        assertEquals("qwe", store.getCachedAdvertisingId());

        sp.setCachedAdvertisingId("");
        assertEquals("", sp.getCachedAdvertisingId());

        sp.setCachedAdvertisingId("123");
        assertEquals("123", sp.getCachedAdvertisingId());
    }

    /**
     * Validate that the setter and getter for remote config preferences is working as expected
     */
    @Test
    public void setGetRemoteConfigValues() {
        assertEquals("", sp.getRemoteConfigValues());

        sp.setRemoteConfigValues("qwe");
        assertEquals("qwe", sp.getRemoteConfigValues());

        sp.setRemoteConfigValues("");
        assertEquals("", sp.getRemoteConfigValues());

        sp.setRemoteConfigValues("123");
        assertEquals("123", sp.getRemoteConfigValues());
    }

    /**
     * Validate that the setter and getter for star rating preferences is working as expected
     */
    @Test
    public void setGetStarRatingPreferences() {
        assertEquals("", sp.getStarRatingPreferences());

        sp.setStarRatingPreferences("abc");
        assertEquals("abc", sp.getStarRatingPreferences());

        sp.setStarRatingPreferences("");
        assertEquals("", sp.getStarRatingPreferences());

        sp.setStarRatingPreferences("123");
        assertEquals("123", sp.getStarRatingPreferences());
    }

    /**
     * Removing a non existing request should cause no problems or changes
     */
    @Test
    public void removeRequest_nonExisting() {
        store.addRequest(requestEntries[0], false);
        store.addRequest(requestEntries[1], false);
        assertEquals(2, store.getRequests().length);
        store.removeRequest(requestEntries[2]);
        assertEquals(2, store.getRequests().length);
        assertTrue(Arrays.equals(new String[] { requestEntries[0], requestEntries[1] }, store.getRequests()));
    }

    /**
     * Validating that 'replaceRequests' and 'replaceRequestList' work as intended
     */
    @Test
    public void replaceRequestQueue() {
        //add initial requests
        store.addRequest(requestEntries[0], false);
        store.addRequest(requestEntries[1], false);
        assertArrayEquals(new String[] { requestEntries[0], requestEntries[1] }, store.getRequests());

        //replace with a new queue in array form
        store.replaceRequests(new String[] { requestEntries[2], requestEntries[3], requestEntries[4] });
        assertArrayEquals(new String[] { requestEntries[2], requestEntries[3], requestEntries[4] }, store.getRequests());

        //replace with a new queue in List form
        List<String> newList = new ArrayList<>();
        newList.add(requestEntries[5]);
        newList.add(requestEntries[6]);
        store.replaceRequestList(newList);
        assertArrayEquals(new String[] { requestEntries[5], requestEntries[6] }, store.getRequests());
    }

    /**
     * Validating that 'deleteOldestRequest' deletes the oldest request
     */
    @Test
    public void deleteOldestRequest() {
        store.maxRequestQueueSize = 5;
        store.addRequest(requestEntries[0], false);
        store.addRequest(requestEntries[1], false);
        store.addRequest(requestEntries[2], false);
        store.addRequest(requestEntries[3], false);
        store.addRequest(requestEntries[4], false);
        assertArrayEquals(new String[] { requestEntries[0], requestEntries[1], requestEntries[2], requestEntries[3], requestEntries[4] }, store.getRequests());
        store.maxRequestQueueSize = 3;
        store.addRequest(requestEntries[5], false);
        assertArrayEquals(new String[] { requestEntries[3], requestEntries[4], requestEntries[5] }, store.getRequests());
    }

    /**
     * Validating that 'checkAndRemoveTooOldRequests' would delete old requests
     */
    @Test
    public void deleteOldRequests() {
        // add requests with old timestamps and a new/weird one
        store.addRequest(oldRequestEntries[0], false);
        store.addRequest(requestEntries[0], false);
        store.addRequest(oldRequestEntries[1], false);
        store.addRequest(oldRequestEntries[2], false);

        // nothing erased as no age limit set
        assertArrayEquals(new String[] { oldRequestEntries[0], requestEntries[0], oldRequestEntries[1], oldRequestEntries[2] }, store.getRequests());
        List<String> requests = new ArrayList<>(Arrays.asList(store.getRequests()));
        int beforeQueueSize = requests.size();
        store.checkAndRemoveTooOldRequests(requests);
        assertEquals(beforeQueueSize, requests.size());
        assertArrayEquals(new String[] { oldRequestEntries[0], requestEntries[0], oldRequestEntries[1], oldRequestEntries[2] }, requests.toArray());

        // with age limit, all but weird/new one removed
        store.setRequestAgeLimit(1);
        store.checkAndRemoveTooOldRequests(requests);
        assertEquals(3 + requests.size(), beforeQueueSize);
        assertArrayEquals(new String[] { requestEntries[0] }, requests.toArray());
    }

    @Test
    public void deleteOldRequests_emptyQueue() {
        List<String> queue = Arrays.asList(store.getRequests());
        int beforeQueueSize = queue.size();
        store.checkAndRemoveTooOldRequests(queue);
        assertEquals(beforeQueueSize, queue.size());
    }

    @Test
    public void addRequest_MaxQueueLimit_requestAge() {
        store.setLimits(3);
        store.setRequestAgeLimit(1);
        store.addRequest(requestEntries[0], false);
        store.addRequest(requestEntries[1], false);
        store.addRequest(requestEntries[2], false);
        assertArrayEquals(new String[] { requestEntries[0], requestEntries[1], requestEntries[2] }, store.getRequests());

        store.addRequest(oldRequestEntries[0], false);

        assertArrayEquals(new String[] { requestEntries[1], requestEntries[2], oldRequestEntries[0] }, store.getRequests());

        store.addRequest(oldRequestEntries[1], false);
        assertArrayEquals(new String[] { requestEntries[1], requestEntries[2], oldRequestEntries[1] }, store.getRequests());

        store.addRequest(requestEntries[3], false);
        assertArrayEquals(new String[] { requestEntries[1], requestEntries[2], requestEntries[3] }, store.getRequests());
    }

    /**
     * Validating that the max request queue size is respected,
     * And that it removes the oldest entry when it is about to be exceeded
     */
    @Test
    public void addRequest_MaxQueueLimit() {
        store.setLimits(2);
        store.addRequest(requestEntries[0], false);
        store.addRequest(requestEntries[1], false);
        assertArrayEquals(new String[] { requestEntries[0], requestEntries[1] }, store.getRequests());

        store.addRequest(requestEntries[2], false);
        assertArrayEquals(new String[] { requestEntries[1], requestEntries[2] }, store.getRequests());

        store.addRequest(requestEntries[3], false);
        assertArrayEquals(new String[] { requestEntries[2], requestEntries[3] }, store.getRequests());

        store.addRequest(requestEntries[4], false);
        assertArrayEquals(new String[] { requestEntries[3], requestEntries[4] }, store.getRequests());
    }

    /**
     * Validate that setting and retrieving device ID and device ID type works as intended
     */
    @Test
    public void testDeviceIDStorage() {
        String[] values = { "aa", null, "bb", "", "cc" };
        String[] values2 = { "11", "22", null, "33", "" };

        assertNull(sp.getDeviceID());
        assertNull(sp.getDeviceIDType());

        for (int a = 0; a < values.length; a++) {
            sp.setDeviceID(values[a]);
            assertEquals(values[a], sp.getDeviceID());
            assertNotEquals(values[a], sp.getDeviceIDType());

            sp.setDeviceIDType(values2[a]);
            assertEquals(values2[a], sp.getDeviceIDType());

            assertEquals(values[a], sp.getDeviceID());
        }
    }

    /**
     * Validating basic functionality of setting and retrieving schema version
     */
    @Test
    public void settingRetrievingSchemaVersion() {
        //test default
        assertEquals(-1, sp.getDataSchemaVersion());

        sp.setDataSchemaVersion(0);
        assertEquals(0, sp.getDataSchemaVersion());

        sp.setDataSchemaVersion(5);
        assertEquals(5, sp.getDataSchemaVersion());

        sp.setDataSchemaVersion(100);
        assertEquals(100, sp.getDataSchemaVersion());
    }

    /**
     * Validating 'anythingSetInStorage' separately
     */
    @Test
    public void validatingAnythingSetInStorageSeparate() {
        assertFalse(sp.anythingSetInStorage());

        sp.addRequest("234ff", false);
        assertTrue(sp.anythingSetInStorage());
        store.clear();

        sp.replaceRequestList(new ArrayList<String>());
        assertTrue(sp.anythingSetInStorage());
        store.clear();

        store.recordEventToEventQueue("dfdf", null, 5, 5, 3, 34_545L, 4, 2, null, null, null, null);
        assertTrue(sp.anythingSetInStorage());
        store.clear();

        sp.setStarRatingPreferences("dfg");
        assertTrue(sp.anythingSetInStorage());
        store.clear();

        sp.setCachedAdvertisingId("iop");
        assertTrue(sp.anythingSetInStorage());
        store.clear();

        sp.setDataSchemaVersion(44);
        assertTrue(sp.anythingSetInStorage());
        store.clear();

        sp.setDeviceID("fdf");
        assertTrue(sp.anythingSetInStorage());
        store.clear();

        sp.setRemoteConfigValues("yui");
        assertTrue(sp.anythingSetInStorage());
        store.clear();

        sp.setDeviceIDType("bb");
        assertTrue(sp.anythingSetInStorage());
        store.clear();

        CountlyStore.storeMessagingProvider(9623, TestUtils.getContext());
        assertTrue(sp.anythingSetInStorage());
        store.clear();

        CountlyStore.cachePushData("mnc", "uio", TestUtils.getContext());
        assertTrue(sp.anythingSetInStorage());
        store.clear();

        CountlyStore.cachePushData(null, "uio", TestUtils.getContext());
        assertTrue(sp.anythingSetInStorage());
        store.clear();

        CountlyStore.cachePushData("mnc", null, TestUtils.getContext());
        assertTrue(sp.anythingSetInStorage());
        store.clear();

        sp.setServerConfig("qwe");
        assertTrue(sp.anythingSetInStorage());
        store.clear();
    }

    /**
     * Validating 'anythingSetInStorage' by adding all possible storage entries
     */
    @Test
    public void validatingAnythingSetInStorageAggregate() {
        assertFalse(sp.anythingSetInStorage());

        sp.addRequest("234ff", false);
        assertTrue(sp.anythingSetInStorage());

        sp.replaceRequestList(new ArrayList<String>());
        assertTrue(sp.anythingSetInStorage());

        store.recordEventToEventQueue("dfdf", null, 5, 5, 3, 34_545L, 4, 2, null, null, null, null);
        assertTrue(sp.anythingSetInStorage());

        sp.setStarRatingPreferences("dfg");
        assertTrue(sp.anythingSetInStorage());

        sp.setCachedAdvertisingId("iop");
        assertTrue(sp.anythingSetInStorage());

        sp.setRemoteConfigValues("yui");
        assertTrue(sp.anythingSetInStorage());

        sp.setDeviceID("fdf");
        assertTrue(sp.anythingSetInStorage());

        sp.setDeviceIDType("bb");
        assertTrue(sp.anythingSetInStorage());

        sp.setDataSchemaVersion(44);
        assertTrue(sp.anythingSetInStorage());

        CountlyStore.storeMessagingProvider(9623, TestUtils.getContext());
        assertTrue(sp.anythingSetInStorage());

        CountlyStore.cachePushData("mnc", "uio", TestUtils.getContext());
        assertTrue(sp.anythingSetInStorage());

        sp.setServerConfig("qwe");
        assertTrue(sp.anythingSetInStorage());
    }

    /**
     * Testing 'getEventQueueSize' in a scenario where the event queue is an empty string
     */
    @Test
    public void getEventQueueSizeEmpty() {
        store.writeEventDataToStorage("");
        assertEquals(0, sp.getEventQueueSize());
    }

    /**
     * Testing 'getEventQueueSize' in a scenario where the event queue contains 2 "events"
     */
    @Test
    public void getEventQueueSizeSimple() {
        store.writeEventDataToStorage("a" + CountlyStore.DELIMITER + "b");
        assertEquals(2, sp.getEventQueueSize());
    }

    /**
     * Validate 'getEventsForRequestAndEmptyEventQueue' in a situation where there are no events
     * This should return an empty array
     *
     * @throws UnsupportedEncodingException
     */
    @Test
    public void getEventsForRequestAndEmptyEventQueueWithNoEvents() throws UnsupportedEncodingException {
        store.writeEventDataToStorage("");
        final String expected = URLEncoder.encode("[]", "UTF-8");
        assertEquals(expected, sp.getEventsForRequestAndEmptyEventQueue());
        assertEquals(0, sp.getEventQueueSize());
    }

    /**
     * Validate 'getEventsForRequestAndEmptyEventQueue' in a situation where there are 2 events
     * Both event should be added to the return value
     *
     * @throws UnsupportedEncodingException
     */
    @Test
    public void getEventsForRequestAndEmptyEventQueueWithSimpleEvents() throws UnsupportedEncodingException {
        final Event event1 = CreateEvent(eKeys[0]);
        store.addEvent(event1);
        final Event event2 = CreateEvent(eKeys[1]);
        store.addEvent(event2);

        final String jsonToEncode = "[" + event1.toJSON().toString() + "," + event2.toJSON().toString() + "]";
        final String expected = URLEncoder.encode(jsonToEncode, "UTF-8");
        assertEquals(expected, sp.getEventsForRequestAndEmptyEventQueue());
        assertEquals(0, sp.getEventQueueSize());
    }

    @Test
    public void getSetServerConfig() {
        store.clear();
        assertNull(sp.getServerConfig());
        sp.setServerConfig("qwe");
        assertEquals("qwe", sp.getServerConfig());
    }

    /**
     * <pre>
     * 1- Init countly with the limit of 250 requests
     *  - Check RQ is empty
     * 2- Add 300 requests
     *  - Check if the first 50 requests are removed
     *  - Check size is 250
     * 3- Stop the countly
     * 4 - Init countly with the limit of 10 requests
     *  - Check RQ is 250
     * 5- Add 20 requests
     *  - On every request addition queue should be dropped to the limit of 10
     *  - On first one queue should be dropped to the 150
     *  - On second one queue should be dropped to the 50
     *  - On third one queue should be dropped to the 10
     *  - On the last one queue should be size of 10
     *  </pre>
     */
    @Test
    public void addRequest_maxQueueSizeLimit_Scenario() {
        Countly countly = new Countly().init(TestUtils.createBaseConfig().setMaxRequestQueueSize(250));
        assertEquals(0, TestUtils.getCurrentRQ().length);

        addRequests(300, countly.countlyStore);
        assertEquals(250, TestUtils.getCurrentRQ().length);

        countly = new Countly().init(TestUtils.createBaseConfig().setMaxRequestQueueSize(10));
        assertEquals(250, TestUtils.getCurrentRQ().length);

        addRequests(1, countly.countlyStore);
        assertEquals(150, TestUtils.getCurrentRQ().length);

        addRequests(1, countly.countlyStore);
        assertEquals(50, TestUtils.getCurrentRQ().length);

        addRequests(1, countly.countlyStore);
        assertEquals(10, TestUtils.getCurrentRQ().length);

        addRequests(17, countly.countlyStore);
        assertEquals(10, TestUtils.getCurrentRQ().length);
    }

    private void addRequests(int count, CountlyStore countlyStore) {
        for (int i = 0; i < count; i++) {
            countlyStore.addRequest("request" + i, false);
        }
    }
}
