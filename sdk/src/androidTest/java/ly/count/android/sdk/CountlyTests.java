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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.HashMap;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class CountlyTests {
    Countly mUninitedCountly;
    Countly mCountly;

    @Before
    public void setUp() {
        final CountlyStore countlyStore = new CountlyStore(getContext(), mock(ModuleLog.class));
        countlyStore.clear();

        mUninitedCountly = new Countly();

        mCountly = new Countly();
        mCountly.init((new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting());
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
        assertNull(mUninitedCountly.moduleSessions);
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
        CountlyConfig cc = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly"));
        mUninitedCountly.init(cc);
        verify(mUninitedCountly).init(cc);
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
            mUninitedCountly.init((new CountlyConfig(getContext(), "appkey", null)).setDeviceId("1234"));
            fail("expected null server URL to throw IllegalArgumentException");
        } catch (IllegalArgumentException ignored) {
            // success!
        }
    }

    @Test
    public void testInit_emptyServerURL() {
        try {
            mUninitedCountly.init((new CountlyConfig(getContext(), "appkey", "")).setDeviceId("1234"));
            fail("expected empty server URL to throw IllegalArgumentException");
        } catch (IllegalArgumentException ignored) {
            // success!
        }
    }

    @Test
    public void testInit_invalidServerURL() {
        try {
            mUninitedCountly.init((new CountlyConfig(getContext(), "appkey", "not-a-valid-server-url")).setDeviceId("1234"));
            fail("expected invalid server URL to throw IllegalArgumentException");
        } catch (IllegalArgumentException ignored) {
            // success!
        }
    }

    @Test
    public void testInit_nullAppKey() {
        try {
            mUninitedCountly.init((new CountlyConfig(getContext(), null, "http://test.count.ly")).setDeviceId("1234"));
            fail("expected null app key to throw IllegalArgumentException");
        } catch (IllegalArgumentException ignored) {
            // success!
        }
    }

    @Test
    public void testInit_emptyAppKey() {
        try {
            mUninitedCountly.init((new CountlyConfig(getContext(), "", "http://test.count.ly")).setDeviceId("1234"));
            fail("expected empty app key to throw IllegalArgumentException");
        } catch (IllegalArgumentException ignored) {
            // success!
        }
    }

    @Test
    public void testInit_nullDeviceID() {
        // null device ID is okay because it tells Countly to use OpenUDID
        mUninitedCountly.init((new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId(null));
    }

    @Test
    public void testInit_emptyDeviceID() {
        try {
            mUninitedCountly.init((new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId(""));
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

        mUninitedCountly.init((new CountlyConfig(getContext(), appKey, serverURL)).setDeviceId(deviceID));
        final EventQueue expectedEventQueue = mUninitedCountly.getEventQueue();
        final ConnectionQueue expectedConnectionQueue = mUninitedCountly.getConnectionQueue();
        final CountlyStore expectedCountlyStore = expectedConnectionQueue.getCountlyStore();
        assertNotNull(expectedEventQueue);
        assertNotNull(expectedConnectionQueue);
        assertNotNull(expectedCountlyStore);

        // second call with same params should succeed, no exception thrown
        mUninitedCountly.init((new CountlyConfig(getContext(), appKey, serverURL)).setDeviceId(deviceID));

        assertSame(expectedEventQueue, mUninitedCountly.getEventQueue());
        assertSame(expectedConnectionQueue, mUninitedCountly.getConnectionQueue());
        assertSame(expectedCountlyStore, mUninitedCountly.getConnectionQueue().getCountlyStore());
        assertSame(getContext().getApplicationContext(), mUninitedCountly.getConnectionQueue().getContext());
        assertEquals(serverURL, mUninitedCountly.getConnectionQueue().getServerURL());
        assertEquals(appKey, mUninitedCountly.getConnectionQueue().getAppKey());
        assertSame(mUninitedCountly.getConnectionQueue().getCountlyStore(), mUninitedCountly.countlyStore);
    }

    @Test
    public void testInit_twiceWithDifferentContext() {
        mUninitedCountly.init(getContext(), "http://test.count.ly", "appkey", "1234");
        // changing context is okay since SharedPrefs are global singletons

        Context mContext = mock(Context.class);
        when(mContext.getCacheDir()).thenReturn(getContext().getCacheDir());

        mUninitedCountly.init(mContext, "http://test.count.ly", "appkey", "1234");
    }

    @Test
    public void testInit_twiceWithDifferentServerURL() {
        mUninitedCountly.init((new CountlyConfig(getContext(), "appkey", "http://test1.count.ly")).setDeviceId("1234"));
        try {
            mUninitedCountly.init((new CountlyConfig(getContext(), "appkey", "http://test2.count.ly")).setDeviceId("1234"));
            // success!
            // should not throw a exception anymore
        } catch (IllegalStateException ignored) {
            fail("expected IllegalStateException to be thrown when calling init a second time with different serverURL");
        }
    }

    @Test
    public void testInit_twiceWithDifferentAppKey() {
        mUninitedCountly.init((new CountlyConfig(getContext(), "appkey1", "http://test.count.ly")).setDeviceId("1234"));
        try {
            mUninitedCountly.init((new CountlyConfig(getContext(), "appkey2", "http://test.count.ly")).setDeviceId("1234"));
            // success!
            // should not throw a exception anymore
        } catch (IllegalStateException ignored) {
            fail("expected IllegalStateException to be thrown when calling init a second time with different app key");
        }
    }

    @Test
    public void testInit_twiceWithDifferentDeviceID() {
        mUninitedCountly.init((new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234"));
        try {
            mUninitedCountly.init((new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("4321"));
            // success!
            // should not throw a exception anymore
        } catch (IllegalStateException ignored) {
            fail("expected IllegalStateException to be thrown when calling init a second time with different device ID");
        }
    }

    @Test
    public void testInit_normal() {
        final String deviceID = "1234";
        final String appKey = "appkey";
        final String serverURL = "http://test.count.ly";

        mUninitedCountly.init((new CountlyConfig(getContext(), appKey, serverURL)).setDeviceId(deviceID));

        assertSame(getContext().getApplicationContext(), mUninitedCountly.getConnectionQueue().getContext());
        assertEquals(serverURL, mUninitedCountly.getConnectionQueue().getServerURL());
        assertEquals(appKey, mUninitedCountly.getConnectionQueue().getAppKey());
        assertNotNull(mUninitedCountly.getConnectionQueue().getCountlyStore());
        assertNotNull(mUninitedCountly.getEventQueue());
        assertSame(mUninitedCountly.getConnectionQueue().getCountlyStore(), mUninitedCountly.countlyStore);
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
        assertNull(mUninitedCountly.moduleSessions);
    }

    @Test
    public void testHalt() {
        CountlyStore mockCountlyStore = mock(CountlyStore.class);

        when(mockCountlyStore.getCachedAdvertisingId()).thenReturn("");

        mCountly.getConnectionQueue().setCountlyStore(mockCountlyStore);
        mCountly.onStart(null);
        assertTrue(0 != mCountly.getPrevSessionDurationStartTime());
        assertTrue(0 != mCountly.getActivityCount());
        assertNotNull(mCountly.getEventQueue());
        assertNotNull(mCountly.getConnectionQueue().getContext());
        assertNotNull(mCountly.getConnectionQueue().getServerURL());
        assertNotNull(mCountly.getConnectionQueue().getAppKey());
        assertNotNull(mCountly.getConnectionQueue().getContext());

        assertNotEquals(0, mCountly.modules.size());

        assertNotNull(mCountly.moduleSessions);
        assertNotNull(mCountly.moduleCrash);
        assertNotNull(mCountly.moduleEvents);
        assertNotNull(mCountly.moduleRatings);
        assertNotNull(mCountly.moduleViews);

        for (ModuleBase module : mCountly.modules) {
            assertNotNull(module);
        }

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

        assertNull(mCountly.moduleSessions);
        assertNull(mCountly.moduleCrash);
        assertNull(mCountly.moduleEvents);
        assertNull(mCountly.moduleRatings);
        assertNull(mCountly.moduleViews);
        assertEquals(0, mCountly.modules.size());
    }

    @Test
    public void testOnStart_initNotCalled() {
        try {
            mUninitedCountly.onStart(null);
            // success!
            // should not throw a exception anymore
        } catch (IllegalStateException ignored) {
            fail("expected calling onStart before init to throw IllegalStateException");
        }
    }

    @Test
    public void testOnStart_firstCall() {
        final ConnectionQueue mockConnectionQueue = mock(ConnectionQueue.class);
        mCountly.setConnectionQueue(mockConnectionQueue);

        mCountly.onStart(null);

        assertEquals(1, mCountly.getActivityCount());
        final long prevSessionDurationStartTime = mCountly.getPrevSessionDurationStartTime();
        assertTrue(prevSessionDurationStartTime > 0);
        assertTrue(prevSessionDurationStartTime <= System.nanoTime());
        verify(mockConnectionQueue).beginSession(false, null, null, null, null);
    }

    @Test
    public void testOnStart_subsequentCall() {
        final ConnectionQueue mockConnectionQueue = mock(ConnectionQueue.class);
        mCountly.setConnectionQueue(mockConnectionQueue);

        mCountly.onStart(null); // first call to onStart
        final long prevSessionDurationStartTime = mCountly.getPrevSessionDurationStartTime();
        mCountly.onStart(null); // second call to onStart

        assertEquals(2, mCountly.getActivityCount());
        assertEquals(prevSessionDurationStartTime, mCountly.getPrevSessionDurationStartTime());
        verify(mockConnectionQueue).beginSession(false, null, null, null, null);
    }

    @Test
    public void testOnStop_initNotCalled() {
        try {
            mUninitedCountly.onStop();
            // success!
            // call should not throw exception anymore
        } catch (IllegalStateException ignored) {

            fail("expected calling onStop before init to throw IllegalStateException");
        }
    }

    @Test
    public void testOnStop_unbalanced() {
        try {
            mCountly.onStop();
            // success!
            // call should not throw exception anymore
        } catch (IllegalStateException ignored) {
            fail("expected calling onStop before init to throw IllegalStateException");
        }
    }

    @Test
    public void testOnStop_reallyStopping_emptyEventQueue() {
        final ConnectionQueue mockConnectionQueue = mock(ConnectionQueue.class);
        mCountly.setConnectionQueue(mockConnectionQueue);

        mCountly.onStart(null);
        mCountly.onStop();

        assertEquals(0, mCountly.getActivityCount());
        assertEquals(0, mCountly.getPrevSessionDurationStartTime());
        verify(mockConnectionQueue).endSession(0, null);
        verify(mockConnectionQueue, times(0)).recordEvents(anyString());
    }

    @Test
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
        verify(mockConnectionQueue).endSession(0, null);
        verify(mockConnectionQueue).recordEvents(eventStr);
    }

    @Test
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
        countly.recordEvent(eventKey, null, count, 0.0d);
        verify(countly).recordEvent(eventKey, null, count, 0.0d);
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
            // success
            // should not throw a exception anymore
        } catch (IllegalStateException ignored) {
            fail("expected IllegalStateException when recordEvent called before init");
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

    @Test
    public void testRecordEvent() {
        final String eventKey = "eventKey";
        final int count = 42;
        final double sum = 3.0d;
        final double dur = 10.0d;
        final HashMap<String, String> segmentation = new HashMap<>(1);
        segmentation.put("segkey1", "segvalue1");
        final HashMap<String, Double> segmD = new HashMap<>();
        final HashMap<String, Integer> segmI = new HashMap<>();
        final HashMap<String, Boolean> segmB = new HashMap<>();

        final EventQueue mockEventQueue = mock(EventQueue.class);
        mCountly.setEventQueue(mockEventQueue);

        //create a spied countly class
        final Countly countly = spy(mCountly);
        countly.moduleEvents = new ModuleEvents(countly, countly.config_);

        doNothing().when(countly).sendEventsIfNeeded();
        doReturn(true).when(countly).isInitialized();

        countly.recordEvent(eventKey, segmentation, count, sum, dur);

        verify(mockEventQueue).recordEvent(eventKey, segmentation, segmI, segmD, segmB, count, sum, dur, null);
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
        when(mockEventQueue.size()).thenReturn(100);
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
        when(mockEventQueue.size()).thenReturn(120);
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

        verifyZeroInteractions(mockEventQueue);
        verify(mockConnectionQueue).tick();
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

    @Test
    public void testRoundedSecondsSinceLastSessionDurationUpdate() {
        long prevSessionDurationStartTime = System.nanoTime() - 1000000000;
        mCountly.setPrevSessionDurationStartTime(prevSessionDurationStartTime);
        assertEquals(1, mCountly.moduleSessions.roundedSecondsSinceLastSessionDurationUpdate());

        prevSessionDurationStartTime = System.nanoTime() - 2000000000;
        mCountly.setPrevSessionDurationStartTime(prevSessionDurationStartTime);
        assertEquals(2, mCountly.moduleSessions.roundedSecondsSinceLastSessionDurationUpdate());

        prevSessionDurationStartTime = System.nanoTime() - 1600000000;
        mCountly.setPrevSessionDurationStartTime(prevSessionDurationStartTime);
        assertEquals(2, mCountly.moduleSessions.roundedSecondsSinceLastSessionDurationUpdate());

        prevSessionDurationStartTime = System.nanoTime() - 1200000000;
        mCountly.setPrevSessionDurationStartTime(prevSessionDurationStartTime);
        assertEquals(1, mCountly.moduleSessions.roundedSecondsSinceLastSessionDurationUpdate());
    }

    @Test
    public void testIsValidURL_badURLs() {
        assertFalse(UtilsNetworking.isValidURL(null));
        assertFalse(UtilsNetworking.isValidURL(""));
        assertFalse(UtilsNetworking.isValidURL(" "));
        assertFalse(UtilsNetworking.isValidURL("blahblahblah.com"));
    }

    @Test
    public void testIsValidURL_goodURL() {
        assertTrue(UtilsNetworking.isValidURL("http://test.count.ly"));
    }

    @Test
    public void testCurrentTimestamp() {
        final int testTimestamp = (int) (System.currentTimeMillis() / 1000L);
        final int actualTimestamp = UtilsTime.currentTimestampSeconds();
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
