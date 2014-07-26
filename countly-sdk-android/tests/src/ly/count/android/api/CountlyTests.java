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
package ly.count.android.api;

import android.test.AndroidTestCase;

import java.util.HashMap;

import static org.mockito.Mockito.*;

public class CountlyTests extends AndroidTestCase {
    Countly mUninitedCountly;
    Countly mCountly;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        final CountlyStore countlyStore = new CountlyStore(getContext());
        countlyStore.clear();

        mUninitedCountly = new Countly();

        mCountly = new Countly();
        mCountly.init(getContext(), "http://countlytest.coupons.com", "appkey", "1234");
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testConstructor() {
        assertNotNull(mUninitedCountly.getConnectionQueue());
        assertNotNull(mUninitedCountly.getTimerService());
        assertNull(mUninitedCountly.getEventQueue());
        assertEquals(0, mUninitedCountly.getActivityCount());
        assertEquals(0, mUninitedCountly.getPrevSessionDurationStartTime());
    }

    public void testSharedInstance() {
        Countly sharedCountly = Countly.sharedInstance();
        assertNotNull(sharedCountly);
        assertSame(sharedCountly, Countly.sharedInstance());
    }

    public void testInit_nullContext() {
        try {
            mUninitedCountly.init(null, "http://countlytest.coupons.com", "appkey", "1234");
            fail("expected null context to throw IllegalArgumentException");
        } catch (IllegalArgumentException ignored) {
            // success!
        }
    }

    public void testInit_nullServerURL() {
        try {
            mUninitedCountly.init(getContext(), null, "appkey", "1234");
            fail("expected null server URL to throw IllegalArgumentException");
        } catch (IllegalArgumentException ignored) {
            // success!
        }
    }

    public void testInit_emptyServerURL() {
        try {
            mUninitedCountly.init(getContext(), "", "appkey", "1234");
            fail("expected empty server URL to throw IllegalArgumentException");
        } catch (IllegalArgumentException ignored) {
            // success!
        }
    }

    public void testInit_invalidServerURL() {
        try {
            mUninitedCountly.init(getContext(), "not-a-valid-server-url", "appkey", "1234");
            fail("expected invalid server URL to throw IllegalArgumentException");
        } catch (IllegalArgumentException ignored) {
            // success!
        }
    }

    public void testInit_nullAppKey() {
        try {
            mUninitedCountly.init(getContext(), "http://countlytest.coupons.com", null, "1234");
            fail("expected null app key to throw IllegalArgumentException");
        } catch (IllegalArgumentException ignored) {
            // success!
        }
    }

    public void testInit_emptyAppKey() {
        try {
            mUninitedCountly.init(getContext(), "http://countlytest.coupons.com", "", "1234");
            fail("expected empty app key to throw IllegalArgumentException");
        } catch (IllegalArgumentException ignored) {
            // success!
        }
    }

    public void testInit_nullDeviceID() {
        try {
            mUninitedCountly.init(getContext(), "http://countlytest.coupons.com", "appkey", null);
            fail("expected null device ID to throw IllegalArgumentException");
        } catch (IllegalArgumentException ignored) {
            // success!
        }
    }

    public void testInit_emptyDeviceID() {
        try {
            mUninitedCountly.init(getContext(), "http://countlytest.coupons.com", "appkey", "");
            fail("expected empty device ID to throw IllegalArgumentException");
        } catch (IllegalArgumentException ignored) {
            // success!
        }
    }

    public void testInit_twice() {
        mUninitedCountly.init(getContext(), "http://countlytest.coupons.com", "appkey", "1234");
        try {
            mUninitedCountly.init(getContext(), "http://countlytest.coupons.com", "appkey", "1234");
            fail("expected calling init twice to throw IllegalStateException");
        } catch (IllegalStateException ignored) {
            // success!
        }
    }

    public void testInit_normal() {
        final String deviceID = "1234";
        final String appKey = "appkey";
        final String serverURL = "http://countlytest.coupons.com";

        mUninitedCountly.init(getContext(), serverURL, appKey, deviceID);

        assertEquals(deviceID, DeviceInfo.getUDID());
        assertSame(getContext(), mUninitedCountly.getConnectionQueue().getContext());
        assertEquals(serverURL, mUninitedCountly.getConnectionQueue().getServerURL());
        assertEquals(appKey, mUninitedCountly.getConnectionQueue().getAppKey());
        assertNotNull(mUninitedCountly.getConnectionQueue().getCountlyStore());
        assertNotNull(mUninitedCountly.getEventQueue());
        assertSame(mUninitedCountly.getConnectionQueue().getCountlyStore(), mUninitedCountly.getEventQueue().getCountlyStore());
    }

    public void testOnStart_initNotCalled() {
        try {
            mUninitedCountly.onStart();
            fail("expected calling onStart before init to throw IllegalStateException");
        } catch (IllegalStateException ignored) {
            // success!
        }
    }

    public void testOnStart_firstCall() {
        final ConnectionQueue mockConnectionQueue = mock(ConnectionQueue.class);
        mCountly.setConnectionQueue(mockConnectionQueue);

        mCountly.onStart();

        assertEquals(1, mCountly.getActivityCount());
        final long prevSessionDurationStartTime = mCountly.getPrevSessionDurationStartTime();
        assertTrue(prevSessionDurationStartTime > 0);
        assertTrue(prevSessionDurationStartTime <= System.nanoTime());
        verify(mockConnectionQueue).beginSession();
    }

    public void testOnStart_subsequentCall() {
        final ConnectionQueue mockConnectionQueue = mock(ConnectionQueue.class);
        mCountly.setConnectionQueue(mockConnectionQueue);

        mCountly.onStart(); // first call to onStart
        final long prevSessionDurationStartTime = mCountly.getPrevSessionDurationStartTime();
        mCountly.onStart(); // second call to onStart

        assertEquals(2, mCountly.getActivityCount());
        assertEquals(prevSessionDurationStartTime, mCountly.getPrevSessionDurationStartTime());
        verify(mockConnectionQueue).beginSession();
    }

    public void testOnStop_initNotCalled() {
        try {
            mUninitedCountly.onStop();
            fail("expected calling onStop before init to throw IllegalStateException");
        } catch (IllegalStateException ignored) {
            // success!
        }
    }

    public void testOnStop_unbalanced() {
        try {
            mCountly.onStop();
            fail("expected calling onStop before init to throw IllegalStateException");
        } catch (IllegalStateException ignored) {
            // success!
        }
    }

    public void testOnStop_reallyStopping_emptyEventQueue() {
        final ConnectionQueue mockConnectionQueue = mock(ConnectionQueue.class);
        mCountly.setConnectionQueue(mockConnectionQueue);

        mCountly.onStart();
        mCountly.onStop();

        assertEquals(0, mCountly.getActivityCount());
        assertEquals(0, mCountly.getPrevSessionDurationStartTime());
        verify(mockConnectionQueue).endSession(0);
        verify(mockConnectionQueue, times(0)).recordEvents(anyString());
    }

    public void testOnStop_reallyStopping_nonEmptyEventQueue() {
        final ConnectionQueue mockConnectionQueue = mock(ConnectionQueue.class);
        mCountly.setConnectionQueue(mockConnectionQueue);

        final EventQueue mockEventQueue = mock(EventQueue.class);
        mCountly.setEventQueue(mockEventQueue);

        when(mockEventQueue.size()).thenReturn(1);
        final String eventStr = "blahblahblahblah";
        when(mockEventQueue.events()).thenReturn(eventStr);

        mCountly.onStart();
        mCountly.onStop();

        assertEquals(0, mCountly.getActivityCount());
        assertEquals(0, mCountly.getPrevSessionDurationStartTime());
        verify(mockConnectionQueue).endSession(0);
        verify(mockConnectionQueue).recordEvents(eventStr);
    }

    public void testOnStop_notStopping() {
        final ConnectionQueue mockConnectionQueue = mock(ConnectionQueue.class);
        mCountly.setConnectionQueue(mockConnectionQueue);

        mCountly.onStart();
        mCountly.onStart();
        final long prevSessionDurationStartTime = mCountly.getPrevSessionDurationStartTime();
        mCountly.onStop();

        assertEquals(1, mCountly.getActivityCount());
        assertEquals(prevSessionDurationStartTime, mCountly.getPrevSessionDurationStartTime());
        verify(mockConnectionQueue, times(0)).endSession(anyInt());
        verify(mockConnectionQueue, times(0)).recordEvents(anyString());
    }

    public void testRecordEvent_keyOnly() {
        final String eventKey = "eventKey";
        final Countly countly = spy(mCountly);
        doNothing().when(countly).recordEvent(eventKey, null, 1, 0.0d);
        countly.recordEvent(eventKey);
        verify(countly).recordEvent(eventKey, null, 1, 0.0d);
    }

    public void testRecordEvent_keyAndCount() {
        final String eventKey = "eventKey";
        final int count = 42;
        final Countly countly = spy(mCountly);
        doNothing().when(countly).recordEvent(eventKey, null, count, 0.0d);
        countly.recordEvent(eventKey, count);
        verify(countly).recordEvent(eventKey, null, count, 0.0d);
    }

    public void testRecordEvent_keyAndCountAndSum() {
        final String eventKey = "eventKey";
        final int count = 42;
        final double sum = 3.0d;
        final Countly countly = spy(mCountly);
        doNothing().when(countly).recordEvent(eventKey, null, count, sum);
        countly.recordEvent(eventKey, count, sum);
        verify(countly).recordEvent(eventKey, null, count, sum);
    }

    public void testRecordEvent_keyAndSegmentationAndCount() {
        final String eventKey = "eventKey";
        final int count = 42;
        final HashMap<String, String> segmentation = new HashMap<String, String>(1);
        segmentation.put("segkey1", "segvalue1");
        final Countly countly = spy(mCountly);
        doNothing().when(countly).recordEvent(eventKey, segmentation, count, 0.0d);
        countly.recordEvent(eventKey, segmentation, count);
        verify(countly).recordEvent(eventKey, segmentation, count, 0.0d);
    }

    public void testRecordEvent_initNotCalled() {
        final String eventKey = "eventKey";
        final int count = 42;
        final double sum = 3.0d;
        final HashMap<String, String> segmentation = new HashMap<String, String>(1);
        segmentation.put("segkey1", "segvalue1");

        try {
            mUninitedCountly.recordEvent(eventKey, segmentation, count, sum);
            fail("expected IllegalStateException when recordEvent called before init");
        } catch (IllegalStateException ignored) {
            // success
        }
    }

    public void testRecordEvent_nullKey() {
        final String eventKey = null;
        final int count = 42;
        final double sum = 3.0d;
        final HashMap<String, String> segmentation = new HashMap<String, String>(1);
        segmentation.put("segkey1", "segvalue1");

        try {
            mCountly.recordEvent(eventKey, segmentation, count, sum);
            fail("expected IllegalArgumentException when recordEvent called with null key");
        } catch (IllegalArgumentException ignored) {
            // success
        }
    }

    public void testRecordEvent_emptyKey() {
        final String eventKey = "";
        final int count = 42;
        final double sum = 3.0d;
        final HashMap<String, String> segmentation = new HashMap<String, String>(1);
        segmentation.put("segkey1", "segvalue1");

        try {
            mCountly.recordEvent(eventKey, segmentation, count, sum);
            fail("expected IllegalArgumentException when recordEvent called with empty key");
        } catch (IllegalArgumentException ignored) {
            // success
        }
    }

    public void testRecordEvent() {
        final String eventKey = "eventKey";
        final int count = 42;
        final double sum = 3.0d;
        final HashMap<String, String> segmentation = new HashMap<String, String>(1);
        segmentation.put("segkey1", "segvalue1");

        final EventQueue mockEventQueue = mock(EventQueue.class);
        mCountly.setEventQueue(mockEventQueue);

        final Countly countly = spy(mCountly);
        doNothing().when(countly).sendEventsIfNeeded();
        countly.recordEvent(eventKey, segmentation, count, sum);

        verify(mockEventQueue).recordEvent(eventKey, segmentation, count, sum);
        verify(countly).sendEventsIfNeeded();
    }

    public void testSendEventsIfNeeded_emptyQueue() {
        final ConnectionQueue mockConnectionQueue = mock(ConnectionQueue.class);
        mCountly.setConnectionQueue(mockConnectionQueue);

        final EventQueue mockEventQueue = mock(EventQueue.class);
        when(mockEventQueue.size()).thenReturn(0);
        mCountly.setEventQueue(mockEventQueue);

        mCountly.sendEventsIfNeeded();

        verify(mockEventQueue, times(0)).events();
        verifyZeroInteractions(mockConnectionQueue);
    }

    public void testSendEventsIfNeeded_lessThanThreshold() {
        final ConnectionQueue mockConnectionQueue = mock(ConnectionQueue.class);
        mCountly.setConnectionQueue(mockConnectionQueue);

        final EventQueue mockEventQueue = mock(EventQueue.class);
        when(mockEventQueue.size()).thenReturn(9);
        mCountly.setEventQueue(mockEventQueue);

        mCountly.sendEventsIfNeeded();

        verify(mockEventQueue, times(0)).events();
        verifyZeroInteractions(mockConnectionQueue);
    }

    public void testSendEventsIfNeeded_equalToThreshold() {
        final ConnectionQueue mockConnectionQueue = mock(ConnectionQueue.class);
        mCountly.setConnectionQueue(mockConnectionQueue);

        final EventQueue mockEventQueue = mock(EventQueue.class);
        when(mockEventQueue.size()).thenReturn(10);
        final String eventData = "blahblahblah";
        when(mockEventQueue.events()).thenReturn(eventData);
        mCountly.setEventQueue(mockEventQueue);

        mCountly.sendEventsIfNeeded();

        verify(mockEventQueue, times(1)).events();
        verify(mockConnectionQueue, times(1)).recordEvents(eventData);
    }

    public void testSendEventsIfNeeded_moreThanThreshold() {
        final ConnectionQueue mockConnectionQueue = mock(ConnectionQueue.class);
        mCountly.setConnectionQueue(mockConnectionQueue);

        final EventQueue mockEventQueue = mock(EventQueue.class);
        when(mockEventQueue.size()).thenReturn(20);
        final String eventData = "blahblahblah";
        when(mockEventQueue.events()).thenReturn(eventData);
        mCountly.setEventQueue(mockEventQueue);

        mCountly.sendEventsIfNeeded();

        verify(mockEventQueue, times(1)).events();
        verify(mockConnectionQueue, times(1)).recordEvents(eventData);
    }

    public void testOnTimer_noActiveSession() {
        final ConnectionQueue mockConnectionQueue = mock(ConnectionQueue.class);
        mCountly.setConnectionQueue(mockConnectionQueue);

        final EventQueue mockEventQueue = mock(EventQueue.class);
        mCountly.setEventQueue(mockEventQueue);

        mCountly.onTimer();

        verifyZeroInteractions(mockConnectionQueue, mockEventQueue);
    }

    public void testOnTimer_activeSession_emptyEventQueue() {
        final ConnectionQueue mockConnectionQueue = mock(ConnectionQueue.class);
        mCountly.setConnectionQueue(mockConnectionQueue);

        final EventQueue mockEventQueue = mock(EventQueue.class);
        when(mockEventQueue.size()).thenReturn(0);
        mCountly.setEventQueue(mockEventQueue);

        mCountly.onStart();
        mCountly.onTimer();

        verify(mockConnectionQueue).updateSession(0);
        verify(mockConnectionQueue, times(0)).recordEvents(anyString());
    }

    public void testOnTimer_activeSession_nonEmptyEventQueue() {
        final ConnectionQueue mockConnectionQueue = mock(ConnectionQueue.class);
        mCountly.setConnectionQueue(mockConnectionQueue);

        final EventQueue mockEventQueue = mock(EventQueue.class);
        when(mockEventQueue.size()).thenReturn(1);
        final String eventData = "blahblahblah";
        when(mockEventQueue.events()).thenReturn(eventData);
        mCountly.setEventQueue(mockEventQueue);

        mCountly.onStart();
        mCountly.onTimer();

        verify(mockConnectionQueue).updateSession(0);
        verify(mockConnectionQueue).recordEvents(eventData);
    }

    public void testRoundedSecondsSinceLastSessionDurationUpdate() {
        long prevSessionDurationStartTime = System.nanoTime() - 1000000000;
        mCountly.setPrevSessionDurationStartTime(prevSessionDurationStartTime);
        assertEquals(1, mCountly.roundedSecondsSinceLastSessionDurationUpdate());

        prevSessionDurationStartTime = System.nanoTime() - 2000000000;
        mCountly.setPrevSessionDurationStartTime(prevSessionDurationStartTime);
        assertEquals(2, mCountly.roundedSecondsSinceLastSessionDurationUpdate());

        prevSessionDurationStartTime = System.nanoTime() - 1600000000;
        mCountly.setPrevSessionDurationStartTime(prevSessionDurationStartTime);
        assertEquals(2, mCountly.roundedSecondsSinceLastSessionDurationUpdate());

        prevSessionDurationStartTime = System.nanoTime() - 1200000000;
        mCountly.setPrevSessionDurationStartTime(prevSessionDurationStartTime);
        assertEquals(1, mCountly.roundedSecondsSinceLastSessionDurationUpdate());
    }

    public void testIsValidURL_badURLs() {
        assertFalse(Countly.isValidURL(null));
        assertFalse(Countly.isValidURL(""));
        assertFalse(Countly.isValidURL(" "));
        assertFalse(Countly.isValidURL("blahblahblah.com"));
    }

    public void testIsValidURL_goodURL() {
        assertTrue(Countly.isValidURL("http://countly.corp.coupons.com"));
    }

    public void testCurrentTimestamp() {
        final int testTimestamp = (int) (System.currentTimeMillis() / 1000l);
        final int actualTimestamp = Countly.currentTimestamp();
        assertTrue(((testTimestamp - 1) <= actualTimestamp) && ((testTimestamp + 1) >= actualTimestamp));
    }
}
