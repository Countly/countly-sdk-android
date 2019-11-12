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
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;

import java.util.HashMap;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CountlyTests {
    Countly mUninitedCountly;
    Countly mCountly;

    @Before
    public void setUp() {
        final CountlyStore countlyStore = new CountlyStore(getContext());
        countlyStore.clear();

        mUninitedCountly = new Countly();

        mCountly = new Countly();
        mCountly.init(getContext(), "http://test.count.ly", "appkey", "1234");
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testConstructor() {
        assertNotNull(mUninitedCountly.getConnectionQueue());
        assertNull(mUninitedCountly.getConnectionQueue().getContext());
        assertNull(mUninitedCountly.getConnectionQueue().getServerURL());
        assertNull(mUninitedCountly.getConnectionQueue().getAppKey());
        assertNull(mUninitedCountly.getConnectionQueue().getCountlyStore());
        assertNotNull(mUninitedCountly.getTimerService());
        assertNull(mUninitedCountly.getEventQueue());
        assertEquals(0, mUninitedCountly.getActivityCount());
        assertEquals(0, mUninitedCountly.getPrevSessionDurationStartTime());
        assertFalse(mUninitedCountly.getDisableUpdateSessionRequests());
        assertFalse(mUninitedCountly.isLoggingEnabled());
    }

    @Test
    public void testSharedInstance() {
        Countly sharedCountly = Countly.sharedInstance();
        assertNotNull(sharedCountly);
        assertSame(sharedCountly, Countly.sharedInstance());
    }

    @Test
    public void testInitWithNoDeviceID() {
        mUninitedCountly = spy(mUninitedCountly);
        mUninitedCountly.init(getContext(), "http://test.count.ly", "appkey", null);
        verify(mUninitedCountly).init(getContext(), "http://test.count.ly", "appkey", null);
    }

    @Test
    public void testInit_nullContext() {
        try {
            mUninitedCountly.init(null, "http://test.count.ly", "appkey", "1234");
            fail("expected null context to throw IllegalArgumentException");
        } catch (IllegalArgumentException ignored) {
            // success!
        }
    }

    @Test
    public void testInit_nullServerURL() {
        try {
            mUninitedCountly.init(getContext(), null, "appkey", "1234");
            fail("expected null server URL to throw IllegalArgumentException");
        } catch (IllegalArgumentException ignored) {
            // success!
        }
    }

    @Test
    public void testInit_emptyServerURL() {
        try {
            mUninitedCountly.init(getContext(), "", "appkey", "1234");
            fail("expected empty server URL to throw IllegalArgumentException");
        } catch (IllegalArgumentException ignored) {
            // success!
        }
    }

    @Test
    public void testInit_invalidServerURL() {
        try {
            mUninitedCountly.init(getContext(), "not-a-valid-server-url", "appkey", "1234");
            fail("expected invalid server URL to throw IllegalArgumentException");
        } catch (IllegalArgumentException ignored) {
            // success!
        }
    }

    @Test
    public void testInit_nullAppKey() {
        try {
            mUninitedCountly.init(getContext(), "http://test.count.ly", null, "1234");
            fail("expected null app key to throw IllegalArgumentException");
        } catch (IllegalArgumentException ignored) {
            // success!
        }
    }

    @Test
    public void testInit_emptyAppKey() {
        try {
            mUninitedCountly.init(getContext(), "http://test.count.ly", "", "1234");
            fail("expected empty app key to throw IllegalArgumentException");
        } catch (IllegalArgumentException ignored) {
            // success!
        }
    }

    @Test
    public void testInit_nullDeviceID() {
        // null device ID is okay because it tells Countly to use OpenUDID
       mUninitedCountly.init(getContext(), "http://test.count.ly", "appkey", null);
    }

    @Test
    public void testInit_emptyDeviceID() {
        try {
            mUninitedCountly.init(getContext(), "http://test.count.ly", "appkey", "");
            fail("expected empty device ID to throw IllegalArgumentException");
        } catch (IllegalArgumentException ignored) {
            // success!
        }
    }

    @Test
    public void testInit_twiceWithSameParams() {
        final String deviceID = "1234";
        final String appKey = "appkey";
        final String serverURL = "http://test.count.ly";

        mUninitedCountly.init(getContext(), serverURL, appKey, deviceID);
        final EventQueue expectedEventQueue = mUninitedCountly.getEventQueue();
        final ConnectionQueue expectedConnectionQueue = mUninitedCountly.getConnectionQueue();
        final CountlyStore expectedCountlyStore = expectedConnectionQueue.getCountlyStore();
        assertNotNull(expectedEventQueue);
        assertNotNull(expectedConnectionQueue);
        assertNotNull(expectedCountlyStore);

        // second call with same params should succeed, no exception thrown
        mUninitedCountly.init(getContext(), serverURL, appKey, deviceID);

        assertSame(expectedEventQueue, mUninitedCountly.getEventQueue());
        assertSame(expectedConnectionQueue, mUninitedCountly.getConnectionQueue());
        assertSame(expectedCountlyStore, mUninitedCountly.getConnectionQueue().getCountlyStore());
        assertSame(getContext().getApplicationContext(), mUninitedCountly.getConnectionQueue().getContext());
        assertEquals(serverURL, mUninitedCountly.getConnectionQueue().getServerURL());
        assertEquals(appKey, mUninitedCountly.getConnectionQueue().getAppKey());
        assertSame(mUninitedCountly.getConnectionQueue().getCountlyStore(), mUninitedCountly.getEventQueue().getCountlyStore());
    }

    //todo fix test, problem while mocking
    /*
    public void testInit_twiceWithDifferentContext() {
        mUninitedCountly.init(getContext(), "http://test.count.ly", "appkey", "1234");
        // changing context is okay since SharedPrefs are global singletons
        mUninitedCountly.init(mock(Context.class), "http://test.count.ly", "appkey", "1234");
    }
    */

    @Test
    public void testInit_twiceWithDifferentServerURL() {
        mUninitedCountly.init(getContext(), "http://test1.count.ly", "appkey", "1234");
        try {
            mUninitedCountly.init(getContext(), "http://test2.count.ly", "appkey", "1234");
            fail("expected IllegalStateException to be thrown when calling init a second time with different serverURL");
        }
        catch (IllegalStateException ignored) {
            // success!
        }
    }

    @Test
    public void testInit_twiceWithDifferentAppKey() {
        mUninitedCountly.init(getContext(), "http://test.count.ly", "appkey1", "1234");
        try {
            mUninitedCountly.init(getContext(), "http://test.count.ly", "appkey2", "1234");
            fail("expected IllegalStateException to be thrown when calling init a second time with different serverURL");
        }
        catch (IllegalStateException ignored) {
            // success!
        }
    }

    @Test
    public void testInit_twiceWithDifferentDeviceID() {
        mUninitedCountly.init(getContext(), "http://test.count.ly", "appkey", "1234");
        try {
            mUninitedCountly.init(getContext(), "http://test.count.ly", "appkey", "4321");
            fail("expected IllegalStateException to be thrown when calling init a second time with different serverURL");
        }
        catch (IllegalStateException ignored) {
            // success!
        }
    }

    @Test
    public void testInit_normal() {
        final String deviceID = "1234";
        final String appKey = "appkey";
        final String serverURL = "http://test.count.ly";

        mUninitedCountly.init(getContext(), serverURL, appKey, deviceID);

        assertSame(getContext().getApplicationContext(), mUninitedCountly.getConnectionQueue().getContext());
        assertEquals(serverURL, mUninitedCountly.getConnectionQueue().getServerURL());
        assertEquals(appKey, mUninitedCountly.getConnectionQueue().getAppKey());
        assertNotNull(mUninitedCountly.getConnectionQueue().getCountlyStore());
        assertNotNull(mUninitedCountly.getEventQueue());
        assertSame(mUninitedCountly.getConnectionQueue().getCountlyStore(), mUninitedCountly.getEventQueue().getCountlyStore());
    }

    @Test
    public void testHalt_notInitialized() {
        mUninitedCountly.halt();
        assertNotNull(mUninitedCountly.getConnectionQueue());
        assertNull(mUninitedCountly.getConnectionQueue().getContext());
        assertNull(mUninitedCountly.getConnectionQueue().getServerURL());
        assertNull(mUninitedCountly.getConnectionQueue().getAppKey());
        assertNull(mUninitedCountly.getConnectionQueue().getCountlyStore());
        assertNotNull(mUninitedCountly.getTimerService());
        assertNull(mUninitedCountly.getEventQueue());
        assertEquals(0, mUninitedCountly.getActivityCount());
        assertEquals(0, mUninitedCountly.getPrevSessionDurationStartTime());
    }


    //todo fix test, problem while mocking
    /*
    public void testHalt() {
        final CountlyStore mockCountlyStore = mock(CountlyStore.class);
        mCountly.getConnectionQueue().setCountlyStore(mockCountlyStore);
        mCountly.onStart(null);
        assertTrue(0 != mCountly.getPrevSessionDurationStartTime());
        assertTrue(0 != mCountly.getActivityCount());
        assertNotNull(mCountly.getEventQueue());
        assertNotNull(mCountly.getConnectionQueue().getContext());
        assertNotNull(mCountly.getConnectionQueue().getServerURL());
        assertNotNull(mCountly.getConnectionQueue().getAppKey());
        assertNotNull(mCountly.getConnectionQueue().getContext());

        mCountly.halt();

        verify(mockCountlyStore).clear();
        assertNotNull(mCountly.getConnectionQueue());
        assertNull(mCountly.getConnectionQueue().getContext());
        assertNull(mCountly.getConnectionQueue().getServerURL());
        assertNull(mCountly.getConnectionQueue().getAppKey());
        assertNull(mCountly.getConnectionQueue().getCountlyStore());
        assertNotNull(mCountly.getTimerService());
        assertNull(mCountly.getEventQueue());
        assertEquals(0, mCountly.getActivityCount());
        assertEquals(0, mCountly.getPrevSessionDurationStartTime());
    }*/


    @Test
    public void testOnStart_initNotCalled() {
        try {
            mUninitedCountly.onStart(null);
            fail("expected calling onStart before init to throw IllegalStateException");
        } catch (IllegalStateException ignored) {
            // success!
        }
    }

    //todo fix test, problem while mocking
    /*
    public void testOnStart_firstCall() {
        final ConnectionQueue mockConnectionQueue = mock(ConnectionQueue.class);
        mCountly.setConnectionQueue(mockConnectionQueue);

        mCountly.onStart(null);

        assertEquals(1, mCountly.getActivityCount());
        final long prevSessionDurationStartTime = mCountly.getPrevSessionDurationStartTime();
        assertTrue(prevSessionDurationStartTime > 0);
        assertTrue(prevSessionDurationStartTime <= System.nanoTime());
        verify(mockConnectionQueue).beginSession();
    }
    */

    //todo fix test, problem while mocking
    /*
    public void testOnStart_subsequentCall() {
        final ConnectionQueue mockConnectionQueue = mock(ConnectionQueue.class);
        mCountly.setConnectionQueue(mockConnectionQueue);

        mCountly.onStart(null); // first call to onStart
        final long prevSessionDurationStartTime = mCountly.getPrevSessionDurationStartTime();
        mCountly.onStart(null); // second call to onStart

        assertEquals(2, mCountly.getActivityCount());
        assertEquals(prevSessionDurationStartTime, mCountly.getPrevSessionDurationStartTime());
        verify(mockConnectionQueue).beginSession();
    }
    */

    @Test
    public void testOnStop_initNotCalled() {
        try {
            mUninitedCountly.onStop();
            fail("expected calling onStop before init to throw IllegalStateException");
        } catch (IllegalStateException ignored) {
            // success!
        }
    }

    @Test
    public void testOnStop_unbalanced() {
        try {
            mCountly.onStop();
            fail("expected calling onStop before init to throw IllegalStateException");
        } catch (IllegalStateException ignored) {
            // success!
        }
    }

    //todo fix test, problem while mocking
    /*
    public void testOnStop_reallyStopping_emptyEventQueue() {
        final ConnectionQueue mockConnectionQueue = mock(ConnectionQueue.class);
        mCountly.setConnectionQueue(mockConnectionQueue);

        mCountly.onStart(null);
        mCountly.onStop();

        assertEquals(0, mCountly.getActivityCount());
        assertEquals(0, mCountly.getPrevSessionDurationStartTime());
        verify(mockConnectionQueue).endSession(0);
        verify(mockConnectionQueue, times(0)).recordEvents(anyString());
    }
    */

    //todo fix test, problem while mocking
    /*
    public void testOnStop_reallyStopping_nonEmptyEventQueue() {
        final ConnectionQueue mockConnectionQueue = mock(ConnectionQueue.class);
        mCountly.setConnectionQueue(mockConnectionQueue);

        final EventQueue mockEventQueue = mock(EventQueue.class);
        mCountly.setEventQueue(mockEventQueue);

        when(mockEventQueue.size()).thenReturn(1);
        final String eventStr = "blahblahblahblah";
        when(mockEventQueue.events()).thenReturn(eventStr);

        mCountly.onStart(null);
        mCountly.onStop();

        assertEquals(0, mCountly.getActivityCount());
        assertEquals(0, mCountly.getPrevSessionDurationStartTime());
        verify(mockConnectionQueue).endSession(0);
        verify(mockConnectionQueue).recordEvents(eventStr);
    }
    */

    //todo fix test, problem while mocking
    /*
    public void testOnStop_notStopping() {
        final ConnectionQueue mockConnectionQueue = mock(ConnectionQueue.class);
        mCountly.setConnectionQueue(mockConnectionQueue);

        mCountly.onStart(null);
        mCountly.onStart(null);
        final long prevSessionDurationStartTime = mCountly.getPrevSessionDurationStartTime();
        mCountly.onStop();

        assertEquals(1, mCountly.getActivityCount());
        assertEquals(prevSessionDurationStartTime, mCountly.getPrevSessionDurationStartTime());
        verify(mockConnectionQueue, times(0)).endSession(anyInt());
        verify(mockConnectionQueue, times(0)).recordEvents(anyString());
    }
    */

    @Test
    public void testRecordEvent_keyOnly() {
        final String eventKey = "eventKey";
        final Countly countly = spy(mCountly);
        doNothing().when(countly).recordEvent(eventKey, null, 1, 0.0d);
        countly.recordEvent(eventKey);
        verify(countly).recordEvent(eventKey, null, 1, 0.0d);
    }

    @Test
    public void testRecordEvent_keyAndCount() {
        final String eventKey = "eventKey";
        final int count = 42;
        final Countly countly = spy(mCountly);


        doNothing().when(countly).recordEvent(eventKey, null, count, 0.0d);
        //doReturn(true).when(countly).recordEvent(eventKey, null, count, 0.0d);
        countly.recordEvent(eventKey, count);
        verify(countly).recordEvent(eventKey, null, count, 0.0d);

        //doNothing().when(countly).recordEvent(eventKey, null, count, 0.0d);
        //doNothing().when(countly).recordEvent(eventKey, count);
        //countly.recordEvent(eventKey, count);
        //verify(countly).recordEvent(eventKey, null, count, 0.0d);

        //doNothing().when(countly).recordEvent(eventKey, count);
        //countly.recordEvent(eventKey, count);
        //verify(countly).recordEvent(eventKey, count);

        //doNothing().when(countly).recordEvent(eventKey, null, count, 0.0d);
        //countly.recordEvent(eventKey, null, count, 0.0d);
        //verify(countly).recordEvent(eventKey, null, count, 0.0d);
    }

    @Test
    public void testRecordEvent_keyAndCountAndSum() {
        final String eventKey = "eventKey";
        final int count = 42;
        final double sum = 3.0d;
        final Countly countly = spy(mCountly);
        doNothing().when(countly).recordEvent(eventKey, null, count, sum);
        countly.recordEvent(eventKey, count, sum);
        verify(countly).recordEvent(eventKey, null, count, sum);
    }

    @Test
    public void testRecordEvent_keyAndSegmentationAndCount() {
        final String eventKey = "eventKey";
        final int count = 42;
        final HashMap<String, String> segmentation = new HashMap<>(1);
        segmentation.put("segkey1", "segvalue1");
        final Countly countly = spy(mCountly);
        doNothing().when(countly).recordEvent(eventKey, segmentation, count, 0.0d);
        countly.recordEvent(eventKey, segmentation, count);
        verify(countly).recordEvent(eventKey, segmentation, count, 0.0d);
    }

    @Test
    public void testRecordEvent_initNotCalled() {
        final String eventKey = "eventKey";
        final int count = 42;
        final double sum = 3.0d;
        final HashMap<String, String> segmentation = new HashMap<>(1);
        segmentation.put("segkey1", "segvalue1");

        try {
            mUninitedCountly.recordEvent(eventKey, segmentation, count, sum);
            fail("expected IllegalStateException when recordEvent called before init");
        } catch (IllegalStateException ignored) {
            // success
        }
    }

    @Test
    public void testRecordEvent_nullKey() {
        final String eventKey = null;
        final int count = 42;
        final double sum = 3.0d;
        final HashMap<String, String> segmentation = new HashMap<>(1);
        segmentation.put("segkey1", "segvalue1");

        try {
            //noinspection ConstantConditions
            mCountly.recordEvent(eventKey, segmentation, count, sum);
            fail("expected IllegalArgumentException when recordEvent called with null key");
        } catch (IllegalArgumentException ignored) {
            // success
        }
    }

    @Test
    public void testRecordEvent_emptyKey() {
        final String eventKey = "";
        final int count = 42;
        final double sum = 3.0d;
        final HashMap<String, String> segmentation = new HashMap<>(1);
        segmentation.put("segkey1", "segvalue1");

        try {
            mCountly.recordEvent(eventKey, segmentation, count, sum);
            fail("expected IllegalArgumentException when recordEvent called with empty key");
        } catch (IllegalArgumentException ignored) {
            // success
        }
    }

    @Test
    public void testRecordEvent_countIsZero() {
        final String eventKey = "";
        final int count = 0;
        final double sum = 3.0d;
        final HashMap<String, String> segmentation = new HashMap<>(1);
        segmentation.put("segkey1", "segvalue1");

        try {
            mCountly.recordEvent(eventKey, segmentation, count, sum);
            fail("expected IllegalArgumentException when recordEvent called with count=0");
        } catch (IllegalArgumentException ignored) {
            // success
        }
    }

    @Test
    public void testRecordEvent_countIsNegative() {
        final String eventKey = "";
        final int count = -1;
        final double sum = 3.0d;
        final HashMap<String, String> segmentation = new HashMap<>(1);
        segmentation.put("segkey1", "segvalue1");

        try {
            mCountly.recordEvent(eventKey, segmentation, count, sum);
            fail("expected IllegalArgumentException when recordEvent called with a negative count");
        } catch (IllegalArgumentException ignored) {
            // success
        }
    }

    @Test
    public void testRecordEvent_segmentationHasNullKey() {
        final String eventKey = "";
        final int count = 1;
        final double sum = 3.0d;
        final HashMap<String, String> segmentation = new HashMap<>(1);
        segmentation.put(null, "segvalue1");

        try {
            mCountly.recordEvent(eventKey, segmentation, count, sum);
            fail("expected IllegalArgumentException when recordEvent called with segmentation with null key");
        } catch (IllegalArgumentException ignored) {
            // success
        }
    }

    @Test
    public void testRecordEvent_segmentationHasEmptyKey() {
        final String eventKey = "";
        final int count = 1;
        final double sum = 3.0d;
        final HashMap<String, String> segmentation = new HashMap<>(1);
        segmentation.put("", "segvalue1");

        try {
            mCountly.recordEvent(eventKey, segmentation, count, sum);
            fail("expected IllegalArgumentException when recordEvent called with segmentation with empty key");
        } catch (IllegalArgumentException ignored) {
            // success
        }
    }

    @Test
    public void testRecordEvent_segmentationHasNullValue() {
        final String eventKey = "";
        final int count = 1;
        final double sum = 3.0d;
        final HashMap<String, String> segmentation = new HashMap<>(1);
        segmentation.put("segkey1", null);

        try {
            mCountly.recordEvent(eventKey, segmentation, count, sum);
            fail("expected IllegalArgumentException when recordEvent called with segmentation with null value");
        } catch (IllegalArgumentException ignored) {
            // success
        }
    }

    @Test
    public void testRecordEvent_segmentationHasEmptyValue() {
        final String eventKey = "";
        final int count = 1;
        final double sum = 3.0d;
        final HashMap<String, String> segmentation = new HashMap<>(1);
        segmentation.put("segkey1", "");

        try {
            mCountly.recordEvent(eventKey, segmentation, count, sum);
            fail("expected IllegalArgumentException when recordEvent called with segmentation with empty value");
        } catch (IllegalArgumentException ignored) {
            // success
        }
    }

    //todo fix test, problem while mocking
    /*
    @Test
    public void testRecordEvent() {
        final String eventKey = "eventKey";
        final int count = 42;
        final double sum = 3.0d;
        final double dur = 10.0d;
        final HashMap<String, String> segmentation = new HashMap<String, String>(1);
        segmentation.put("segkey1", "segvalue1");

        final EventQueue mockEventQueue = mock(EventQueue.class);
        mCountly.setEventQueue(mockEventQueue);

        final Countly countly = spy(mCountly);
        doNothing().when(countly).sendEventsIfNeeded();
        countly.recordEvent(eventKey, segmentation, count, sum);

        verify(mockEventQueue).recordEvent(eventKey, segmentation, null, null, count, sum, dur);
        verify(countly).sendEventsIfNeeded();
    }

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
    public void testOnTimer_noActiveSession() {
        final ConnectionQueue mockConnectionQueue = mock(ConnectionQueue.class);
        mCountly.setConnectionQueue(mockConnectionQueue);

        final EventQueue mockEventQueue = mock(EventQueue.class);
        mCountly.setEventQueue(mockEventQueue);

        mCountly.onTimer();

        verifyZeroInteractions(mockConnectionQueue, mockEventQueue);
    }

    @Test
    public void testOnTimer_activeSession_emptyEventQueue() {
        final ConnectionQueue mockConnectionQueue = mock(ConnectionQueue.class);
        mCountly.setConnectionQueue(mockConnectionQueue);

        final EventQueue mockEventQueue = mock(EventQueue.class);
        when(mockEventQueue.size()).thenReturn(0);
        mCountly.setEventQueue(mockEventQueue);

        mCountly.onStart(null);
        mCountly.onTimer();

        verify(mockConnectionQueue).updateSession(0);
        verify(mockConnectionQueue, times(0)).recordEvents(anyString());
    }

    @Test
    public void testOnTimer_activeSession_nonEmptyEventQueue() {
        final ConnectionQueue mockConnectionQueue = mock(ConnectionQueue.class);
        mCountly.setConnectionQueue(mockConnectionQueue);

        final EventQueue mockEventQueue = mock(EventQueue.class);
        when(mockEventQueue.size()).thenReturn(1);
        final String eventData = "blahblahblah";
        when(mockEventQueue.events()).thenReturn(eventData);
        mCountly.setEventQueue(mockEventQueue);

        mCountly.onStart(null);
        mCountly.onTimer();

        verify(mockConnectionQueue).updateSession(0);
        verify(mockConnectionQueue).recordEvents(eventData);
    }

    @Test
    public void testOnTimer_activeSession_emptyEventQueue_sessionTimeUpdatesDisabled() {
        final ConnectionQueue mockConnectionQueue = mock(ConnectionQueue.class);
        mCountly.setConnectionQueue(mockConnectionQueue);
        mCountly.setDisableUpdateSessionRequests(true);

        final EventQueue mockEventQueue = mock(EventQueue.class);
        when(mockEventQueue.size()).thenReturn(0);
        mCountly.setEventQueue(mockEventQueue);

        mCountly.onStart(null);
        mCountly.onTimer();

        verify(mockConnectionQueue, times(0)).updateSession(anyInt());
        verify(mockConnectionQueue, times(0)).recordEvents(anyString());
    }

    @Test
    public void testOnTimer_activeSession_nonEmptyEventQueue_sessionTimeUpdatesDisabled() {
        final ConnectionQueue mockConnectionQueue = mock(ConnectionQueue.class);
        mCountly.setConnectionQueue(mockConnectionQueue);
        mCountly.setDisableUpdateSessionRequests(true);

        final EventQueue mockEventQueue = mock(EventQueue.class);
        when(mockEventQueue.size()).thenReturn(1);
        final String eventData = "blahblahblah";
        when(mockEventQueue.events()).thenReturn(eventData);
        mCountly.setEventQueue(mockEventQueue);

        mCountly.onStart(null);
        mCountly.onTimer();

        verify(mockConnectionQueue, times(0)).updateSession(anyInt());
        verify(mockConnectionQueue).recordEvents(eventData);
    }
    */

    @Test
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

    @Test
    public void testIsValidURL_badURLs() {
        assertFalse(Countly.isValidURL(null));
        assertFalse(Countly.isValidURL(""));
        assertFalse(Countly.isValidURL(" "));
        assertFalse(Countly.isValidURL("blahblahblah.com"));
    }

    @Test
    public void testIsValidURL_goodURL() {
        assertTrue(Countly.isValidURL("http://test.count.ly"));
    }

    @Test
    public void testCurrentTimestamp() {
        final int testTimestamp = (int) (System.currentTimeMillis() / 1000L);
        final int actualTimestamp = Countly.currentTimestamp();
        assertTrue(((testTimestamp - 1) <= actualTimestamp) && ((testTimestamp + 1) >= actualTimestamp));
    }

    @Test
    public void testSetDisableUpdateSessionRequests() {
        assertFalse(mCountly.getDisableUpdateSessionRequests());
        mCountly.setDisableUpdateSessionRequests(true);
        assertTrue(mCountly.getDisableUpdateSessionRequests());
        mCountly.setDisableUpdateSessionRequests(false);
        assertFalse(mCountly.getDisableUpdateSessionRequests());
    }

    @Test
    public void testLoggingFlag() {
        assertFalse(mUninitedCountly.isLoggingEnabled());
        mUninitedCountly.setLoggingEnabled(true);
        assertTrue(mUninitedCountly.isLoggingEnabled());
        mUninitedCountly.setLoggingEnabled(false);
        assertFalse(mUninitedCountly.isLoggingEnabled());
    }
}
