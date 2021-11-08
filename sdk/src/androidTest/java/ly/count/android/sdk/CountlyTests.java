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
        assertNull(mUninitedCountly.getConnectionQueue().baseInfoProvider);
        assertNull(mUninitedCountly.getConnectionQueue().getStorageProvider());
        assertNotNull(mUninitedCountly.getTimerService());
        assertEquals(0, mUninitedCountly.getActivityCount());
        assertNull(mUninitedCountly.moduleSessions);
        assertFalse(mUninitedCountly.getDisableUpdateSessionRequests());
        assertFalse(mUninitedCountly.isLoggingEnabled());
        Assert.assertTrue(mCountly.isInitialized());
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
            mUninitedCountly.init(new CountlyConfig(null, "appkey", "http://test.count.ly"));
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
        final ConnectionQueue expectedConnectionQueue = mUninitedCountly.getConnectionQueue();
        final StorageProvider expectedCountlyStore = expectedConnectionQueue.getStorageProvider();
        Assert.assertTrue(mCountly.isInitialized());
        assertNotNull(expectedConnectionQueue);
        assertNotNull(expectedCountlyStore);

        // second call with same params should succeed, no exception thrown
        mUninitedCountly.init((new CountlyConfig(getContext(), appKey, serverURL)).setDeviceId(deviceID));

        Assert.assertTrue(mCountly.isInitialized());
        assertSame(expectedConnectionQueue, mUninitedCountly.getConnectionQueue());
        assertSame(expectedCountlyStore, mUninitedCountly.getConnectionQueue().getStorageProvider());
        assertSame(getContext().getApplicationContext(), mUninitedCountly.getConnectionQueue().getContext());
        assertEquals(serverURL, mUninitedCountly.getConnectionQueue().baseInfoProvider.getServerURL());
        assertEquals(appKey, mUninitedCountly.getConnectionQueue().baseInfoProvider.getAppKey());
        assertSame(mUninitedCountly.getConnectionQueue().getStorageProvider(), mUninitedCountly.countlyStore);
    }

    @Test
    public void testInit_twiceWithDifferentContext() {
        mUninitedCountly.init(new CountlyConfig(getContext(), "appkey", "http://test.count.ly"));
        // changing context is okay since SharedPrefs are global singletons

        Context mContext = mock(Context.class);
        when(mContext.getCacheDir()).thenReturn(getContext().getCacheDir());

        mUninitedCountly.init(new CountlyConfig(mContext, "appkey", "http://test.count.ly"));
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
        assertEquals(serverURL, mUninitedCountly.getConnectionQueue().baseInfoProvider.getServerURL());
        assertEquals(appKey, mUninitedCountly.getConnectionQueue().baseInfoProvider.getAppKey());
        assertNotNull(mUninitedCountly.getConnectionQueue().getStorageProvider());
        Assert.assertTrue(mCountly.isInitialized());
        assertSame(mUninitedCountly.getConnectionQueue().getStorageProvider(), mUninitedCountly.countlyStore);
    }

    @Test
    public void testHalt_notInitialized() {
        mUninitedCountly.halt();
        assertNotNull(mUninitedCountly.getConnectionQueue());
        assertNull(mUninitedCountly.getConnectionQueue().getContext());
        assertNull(mUninitedCountly.getConnectionQueue().baseInfoProvider);
        assertNull(mUninitedCountly.getConnectionQueue().getStorageProvider());
        assertNotNull(mUninitedCountly.getTimerService());
        Assert.assertTrue(mCountly.isInitialized());
        assertEquals(0, mUninitedCountly.getActivityCount());
        assertNull(mUninitedCountly.moduleSessions);
    }

    //@Test
    //public void testHalt() {
    //    CountlyStore mockCountlyStore = mock(CountlyStore.class);
    //
    //    when(mockCountlyStore.getCachedAdvertisingId()).thenReturn("");
    //
    //    mCountly.getConnectionQueue().setStorageProvider(mockCountlyStore);
    //    mCountly.onStart(null);
    //    assertTrue(0 != mCountly.getPrevSessionDurationStartTime());
    //    assertTrue(0 != mCountly.getActivityCount());
    //    Assert.assertTrue(mCountly.isInitialized());
    //    assertNotNull(mCountly.getConnectionQueue().getContext());
    //    assertNotNull(mCountly.getConnectionQueue().baseInfoProvider.getServerURL());
    //    assertNotNull(mCountly.getConnectionQueue().baseInfoProvider.getAppKey());
    //    assertNotNull(mCountly.getConnectionQueue().getContext());
    //
    //    assertNotEquals(0, mCountly.modules.size());
    //
    //    assertNotNull(mCountly.moduleSessions);
    //    assertNotNull(mCountly.moduleCrash);
    //    assertNotNull(mCountly.moduleEvents);
    //    assertNotNull(mCountly.moduleRatings);
    //    assertNotNull(mCountly.moduleViews);
    //
    //    for (ModuleBase module : mCountly.modules) {
    //        assertNotNull(module);
    //    }
    //
    //    mCountly.halt();
    //
    //    verify(mockCountlyStore).clear();
    //    assertNotNull(mCountly.getConnectionQueue());
    //    assertNull(mCountly.getConnectionQueue().getContext());
    //    assertNull(mCountly.getConnectionQueue().baseInfoProvider);
    //    assertNull(mCountly.getConnectionQueue().getStorageProvider());
    //    assertNotNull(mCountly.getTimerService());
    //    Assert.assertFalse(mCountly.isInitialized());
    //    assertEquals(0, mCountly.getActivityCount());
    //
    //    assertNull(mCountly.moduleSessions);
    //    assertNull(mCountly.moduleCrash);
    //    assertNull(mCountly.moduleEvents);
    //    assertNull(mCountly.moduleRatings);
    //    assertNull(mCountly.moduleViews);
    //    assertEquals(0, mCountly.modules.size());
    //}

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
        RequestQueueProvider requestQueueProvider = TestUtils.setRequestQueueProviderToMock(mCountly, mock(RequestQueueProvider.class));

        mCountly.onStart(null);

        assertEquals(1, mCountly.getActivityCount());
        final long prevSessionDurationStartTime = mCountly.getPrevSessionDurationStartTime();
        assertTrue(prevSessionDurationStartTime > 0);
        assertTrue(prevSessionDurationStartTime <= System.nanoTime());
        verify(requestQueueProvider).beginSession(false, null, null, null, null);
    }

    @Test
    public void testOnStart_subsequentCall() {
        RequestQueueProvider requestQueueProvider = TestUtils.setRequestQueueProviderToMock(mCountly, mock(RequestQueueProvider.class));

        mCountly.onStart(null); // first call to onStart
        final long prevSessionDurationStartTime = mCountly.getPrevSessionDurationStartTime();
        mCountly.onStart(null); // second call to onStart

        assertEquals(2, mCountly.getActivityCount());
        assertEquals(prevSessionDurationStartTime, mCountly.getPrevSessionDurationStartTime());
        verify(requestQueueProvider).beginSession(false, null, null, null, null);
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
        RequestQueueProvider requestQueueProvider = TestUtils.setRequestQueueProviderToMock(mCountly, mock(RequestQueueProvider.class));

        mCountly.onStart(null);
        mCountly.onStop();

        assertEquals(0, mCountly.getActivityCount());
        assertEquals(0, mCountly.getPrevSessionDurationStartTime());
        verify(requestQueueProvider).endSession(0, null);
        verify(requestQueueProvider, times(0)).recordEvents(anyString());
    }

    /**
     * Start and stop an activity.
     * Make sure that the session is stopped correctly.
     * Verify that the stored events are added to the request queue
     */
    @Test
    public void testOnStop_reallyStopping_nonEmptyEventQueue() {
        RequestQueueProvider requestQueueProvider = TestUtils.setRequestQueueProviderToMock(mCountly, mock(RequestQueueProvider.class));

        mCountly.moduleEvents.eventQueueProvider = mock(EventQueueProvider.class);

        StorageProvider sp = mock(StorageProvider.class);
        TestUtils.setStorageProviderToMock(mCountly, sp);

        when(sp.getEventQueueSize()).thenReturn(1);
        final String eventStr = "blahblahblahblah";
        when(sp.getEventsForRequestAndEmptyEventQueue()).thenReturn(eventStr);

        mCountly.onStart(null);
        mCountly.onStop();

        assertEquals(0, mCountly.getActivityCount());
        assertEquals(0, mCountly.getPrevSessionDurationStartTime());
        verify(requestQueueProvider).endSession(0, null);
        verify(requestQueueProvider).recordEvents(eventStr);
    }

    @Test
    public void testOnStop_notStopping() {
        RequestQueueProvider requestQueueProvider = TestUtils.setRequestQueueProviderToMock(mCountly, mock(RequestQueueProvider.class));

        mCountly.onStart(null);
        mCountly.onStart(null);
        final long prevSessionDurationStartTime = mCountly.getPrevSessionDurationStartTime();
        mCountly.onStop();

        assertEquals(1, mCountly.getActivityCount());
        assertEquals(prevSessionDurationStartTime, mCountly.getPrevSessionDurationStartTime());
        verify(requestQueueProvider, times(0)).endSession(anyInt());
        verify(requestQueueProvider, times(0)).recordEvents(anyString());
    }

    /**
     * There are no events in the event queue.
     * Make sure that no event requests is created when 'sendEventsIfNeeded' is called.
     */
    @Test
    public void testSendEventsIfNeeded_emptyQueue() {
        RequestQueueProvider requestQueueProvider = TestUtils.setRequestQueueProviderToMock(mCountly, mock(RequestQueueProvider.class));
        mCountly.moduleEvents.storageProvider = mock(StorageProvider.class);

        when(mCountly.moduleEvents.storageProvider.getEventQueueSize()).thenReturn(0);

        mCountly.moduleRequestQueue.sendEventsIfNeeded(false);

        verify(mCountly.moduleEvents.storageProvider, times(0)).getEventsForRequestAndEmptyEventQueue();
        verifyZeroInteractions(requestQueueProvider);
    }

    /**
     * There are some events in the event queue, but they are less than the threshold.
     * Make sure that no event requests is created when 'sendEventsIfNeeded' is called.
     */
    @Test
    public void testSendEventsIfNeeded_lessThanThreshold() {
        RequestQueueProvider requestQueueProvider = TestUtils.setRequestQueueProviderToMock(mCountly, mock(RequestQueueProvider.class));
        mCountly.moduleEvents.storageProvider = mock(StorageProvider.class);

        when(mCountly.moduleEvents.storageProvider.getEventQueueSize()).thenReturn(9);

        mCountly.moduleRequestQueue.sendEventsIfNeeded(false);

        verify(mCountly.moduleEvents.storageProvider, times(0)).getEventsForRequestAndEmptyEventQueue();
        verifyZeroInteractions(requestQueueProvider);
    }

    @Test
    public void testSendEventsIfNeeded_equalToThreshold() {
        RequestQueueProvider requestQueueProvider = TestUtils.setRequestQueueProviderToMock(mCountly, mock(RequestQueueProvider.class));
        mCountly.config_.storageProvider = mock(StorageProvider.class);
        mCountly.moduleRequestQueue.storageProvider = mCountly.config_.storageProvider;

        when(mCountly.config_.storageProvider.getEventQueueSize()).thenReturn(100);
        final String eventData = "blahblahblah";
        when(mCountly.config_.storageProvider.getEventsForRequestAndEmptyEventQueue()).thenReturn(eventData);

        mCountly.moduleRequestQueue.sendEventsIfNeeded(false);

        verify(mCountly.config_.storageProvider, times(1)).getEventsForRequestAndEmptyEventQueue();
        verify(requestQueueProvider, times(1)).recordEvents(eventData);
    }

    @Test
    public void testSendEventsIfNeeded_moreThanThreshold() {
        RequestQueueProvider requestQueueProvider = TestUtils.setRequestQueueProviderToMock(mCountly, mock(RequestQueueProvider.class));
        mCountly.config_.storageProvider = mock(StorageProvider.class);
        mCountly.moduleRequestQueue.storageProvider = mCountly.config_.storageProvider;

        when(mCountly.config_.storageProvider.getEventQueueSize()).thenReturn(120);
        final String eventData = "blahblahblah";
        when(mCountly.config_.storageProvider.getEventsForRequestAndEmptyEventQueue()).thenReturn(eventData);

        mCountly.moduleRequestQueue.sendEventsIfNeeded(false);

        verify(mCountly.config_.storageProvider, times(1)).getEventsForRequestAndEmptyEventQueue();
        verify(requestQueueProvider, times(1)).recordEvents(eventData);
    }

    @Test
    public void testOnTimer_noActiveSession() {
        RequestQueueProvider requestQueueProvider = TestUtils.setRequestQueueProviderToMock(mCountly, mock(RequestQueueProvider.class));
        mCountly.config_.storageProvider = mock(StorageProvider.class);
        mCountly.moduleEvents.eventQueueProvider = mock(EventQueueProvider.class);

        mCountly.onTimer();

        verify(requestQueueProvider).tick();
    }

    @Test
    public void testOnTimer_activeSession_emptyEventQueue() {
        RequestQueueProvider requestQueueProvider = TestUtils.setRequestQueueProviderToMock(mCountly, mock(RequestQueueProvider.class));
        mCountly.config_.storageProvider = mock(StorageProvider.class);

        when(mCountly.config_.storageProvider.getEventQueueSize()).thenReturn(0);

        mCountly.onStart(null);
        mCountly.onTimer();

        verify(requestQueueProvider).updateSession(0);
        verify(requestQueueProvider, times(0)).recordEvents(anyString());
    }

    @Test
    public void testOnTimer_activeSession_nonEmptyEventQueue() {
        RequestQueueProvider requestQueueProvider = TestUtils.setRequestQueueProviderToMock(mCountly, mock(RequestQueueProvider.class));
        mCountly.config_.storageProvider = mock(StorageProvider.class);
        mCountly.moduleRequestQueue.storageProvider = mCountly.config_.storageProvider;

        when(mCountly.config_.storageProvider.getEventQueueSize()).thenReturn(1);
        final String eventData = "blahblahblah";
        when(mCountly.config_.storageProvider.getEventsForRequestAndEmptyEventQueue()).thenReturn(eventData);

        mCountly.onStart(null);
        mCountly.onTimer();

        verify(requestQueueProvider).updateSession(0);
        verify(requestQueueProvider).recordEvents(eventData);
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
    public void testLoggingFlag() {
        assertFalse(mUninitedCountly.isLoggingEnabled());
        mUninitedCountly.setLoggingEnabled(true);
        assertTrue(mUninitedCountly.isLoggingEnabled());
        mUninitedCountly.setLoggingEnabled(false);
        assertFalse(mUninitedCountly.isLoggingEnabled());
    }

    /*
    //todo fix these

    @Test
    public void testOnTimer_activeSession_emptyEventQueue_sessionTimeUpdatesDisabled() {
        final ConnectionQueue mockConnectionQueue = mock(ConnectionQueue.class);
        mCountly.setConnectionQueue(mockConnectionQueue);
        mCountly.setDisableUpdateSessionRequests(true);
        mCountly.config_.storageProvider = mock(StorageProvider.class);

        when(mCountly.config_.storageProvider.getEventQueueSize()).thenReturn(0);

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
        mCountly.config_.storageProvider = mock(StorageProvider.class);

        when(mCountly.config_.storageProvider.getEventQueueSize()).thenReturn(1);
        final String eventData = "blahblahblah";
        when(mCountly.config_.storageProvider.getEventsForRequestAndEmptyEventQueue()).thenReturn(eventData);

        mCountly.onStart(null);
        mCountly.onTimer();

        verify(mockConnectionQueue, times(0)).updateSession(anyInt());
        verify(mockConnectionQueue).recordEvents(eventData);
    }

    @Test
    public void testSetDisableUpdateSessionRequests() {
        assertFalse(mCountly.getDisableUpdateSessionRequests());
        mCountly.setDisableUpdateSessionRequests(true);
        assertTrue(mCountly.getDisableUpdateSessionRequests());
        mCountly.setDisableUpdateSessionRequests(false);
        assertFalse(mCountly.getDisableUpdateSessionRequests());
    }
    */
}
