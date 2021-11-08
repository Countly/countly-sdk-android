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

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class ConnectionQueueTests {
    ConnectionQueue connQ;
    ConnectionQueue freshConnQ;
    final static long timestampAllowance = 150;
    final String appKey = "abcDeFgHiJkLmNoPQRstuVWxyz";

    @Before
    public void setUp() {
        Countly.sharedInstance().halt();
        Countly.sharedInstance().setLoggingEnabled(true);
        freshConnQ = new ConnectionQueue();
        Countly.sharedInstance().init(new CountlyConfig(getContext(), appKey, "http://countly.coupons.com"));
        connQ = Countly.sharedInstance().connectionQueue_;

        //connQ = new ConnectionQueue();
        //connQ.setAppKey("abcDeFgHiJkLmNoPQRstuVWxyz");
        //connQ.setServerURL("http://countly.coupons.com");
        //connQ.setContext(getContext());
        CountlyStore cs = mock(CountlyStore.class);
        when(cs.getCachedAdvertisingId()).thenReturn("");
        connQ.storageProvider = cs;
        connQ.setDeviceId(mock(DeviceId.class));
        connQ.setExecutor(mock(ExecutorService.class));
    }

    @Test
    public void testConstructor() {
        assertNull(freshConnQ.storageProvider);
        assertNull(freshConnQ.getDeviceId());
        assertNull(freshConnQ.baseInfoProvider);
        assertNull(freshConnQ.getContext());
        assertNull(freshConnQ.getExecutor());
    }

    @Test
    public void testAppKey() {
        assertEquals(appKey, connQ.baseInfoProvider.getAppKey());
    }

    @Test
    public void testContext() {
        freshConnQ.setContext(getContext());
        assertSame(getContext(), freshConnQ.getContext());
    }

    @Test
    public void testServerURL() {
        final String serverURL = "http://countly.coupons.com";
        assertEquals(serverURL, connQ.baseInfoProvider.getServerURL());
    }

    @Test
    public void testCountlyStore() {
        final CountlyStore store = new CountlyStore(getContext(), mock(ModuleLog.class));
        freshConnQ.storageProvider = store;
        assertSame(store, freshConnQ.storageProvider);
    }

    @Test
    public void testDeviceId() {
        final CountlyStore store = new CountlyStore(getContext(), mock(ModuleLog.class));
        final DeviceId deviceId = new DeviceId(DeviceIdType.DEVELOPER_SUPPLIED, "blah", store, mock(ModuleLog.class), null);
        freshConnQ.setDeviceId(deviceId);
        assertSame(deviceId, freshConnQ.getDeviceId());
    }

    @Test
    public void testExecutor() {
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        freshConnQ.setExecutor(executor);
        assertSame(executor, freshConnQ.getExecutor());
    }

    @Test
    public void testCheckInternalState_nullAppKey() {
        connQ.checkInternalState(); // shouldn't throw
        Countly.sharedInstance().moduleRequestQueue.appKey = null;
        try {
            freshConnQ.checkInternalState();
            fail("expected IllegalStateException when internal state is not set up");
        } catch (IllegalStateException ignored) {
            // success!
        }
    }

    @Test
    public void testCheckInternalState_emptyAppKey() {
        connQ.checkInternalState(); // shouldn't throw
        Countly.sharedInstance().moduleRequestQueue.appKey = "";
        try {
            freshConnQ.checkInternalState();
            fail("expected IllegalStateException when internal state is not set up");
        } catch (IllegalStateException ignored) {
            // success!
        }
    }

    @Test
    public void testCheckInternalState_nullStore() {
        connQ.checkInternalState(); // shouldn't throw
        connQ.storageProvider = null;
        try {
            freshConnQ.checkInternalState();
            fail("expected IllegalStateException when internal state is not set up");
        } catch (IllegalStateException ignored) {
            // success!
        }
    }

    @Test
    public void testCheckInternalState_nullContext() {
        connQ.checkInternalState(); // shouldn't throw
        connQ.setContext(null);
        try {
            freshConnQ.checkInternalState();
            fail("expected IllegalStateException when internal state is not set up");
        } catch (IllegalStateException ignored) {
            // success!
        }
    }

    @Test
    public void testCheckInternalState_nullServerURL() {
        connQ.checkInternalState(); // shouldn't throw
        Countly.sharedInstance().moduleRequestQueue.serverURL = null;
        try {
            freshConnQ.checkInternalState();
            fail("expected IllegalStateException when internal state is not set up");
        } catch (IllegalStateException ignored) {
            // success!
        }
    }

    @Test
    public void testCheckInternalState_invalidServerURL() {
        connQ.checkInternalState(); // shouldn't throw
        Countly.sharedInstance().moduleRequestQueue.serverURL = "blahblahblah.com";
        try {
            freshConnQ.checkInternalState();
            fail("expected IllegalStateException when internal state is not set up");
        } catch (IllegalStateException ignored) {
            // success!
        }
    }

    @Test
    public void testBeginSession_checkInternalState() {
        try {
            freshConnQ.beginSession(false, null, null, null, null);
            fail("expected IllegalStateException when internal state is not set up");
        } catch (IllegalStateException ignored) {
            // success!
        }
    }

    /*
    @Test
    public void testBeginSession() throws JSONException, UnsupportedEncodingException {
        connQ.beginSession(false, null, null, null, null);
        final ArgumentCaptor<String> arg = ArgumentCaptor.forClass(String.class);
        verify(connQ.storageProvider).addRequest(arg.capture());
        verify(connQ.getExecutor()).submit(any(ConnectionProcessor.class));

        // verify query parameters
        final String queryStr = arg.getValue();
        final Map<String, String> queryParams = parseQueryParams(queryStr);
        assertEquals(connQ.baseInfoProvider.getAppKey(), queryParams.get("app_key"));
        assertNull(queryParams.get("device_id"));
        final long curTimestamp = UtilsTime.currentTimestampMs();
        final long actualTimestamp = Long.parseLong(queryParams.get("timestamp"));
        // this check attempts to account for minor time changes during this test
        assertTrue(((curTimestamp - 400) <= actualTimestamp) && ((curTimestamp + 400) >= actualTimestamp));
        assertEquals(Countly.sharedInstance().COUNTLY_SDK_VERSION_STRING, queryParams.get("sdk_version"));
        assertEquals("1", queryParams.get("begin_session"));
        // validate metrics
        final JSONObject actualMetrics = new JSONObject(queryParams.get("metrics"));
        final String metricsJsonStr = URLDecoder.decode(DeviceInfo.getMetrics(getContext(), null), "UTF-8");
        final JSONObject expectedMetrics = new JSONObject(metricsJsonStr);
        assertEquals(expectedMetrics.length(), actualMetrics.length());
        final Iterator actualMetricsKeyIterator = actualMetrics.keys();
        while (actualMetricsKeyIterator.hasNext()) {
            final String key = (String) actualMetricsKeyIterator.next();
            assertEquals(expectedMetrics.get(key), actualMetrics.get(key));
        }
    }*/

    @Test
    public void testUpdateSession_checkInternalState() {
        try {
            freshConnQ.updateSession(15);
            fail("expected IllegalStateException when internal state is not set up");
        } catch (IllegalStateException ignored) {
            // success!
        }
    }

    @Test
    public void testUpdateSession_zeroDuration() {
        connQ.updateSession(0);
        verifyZeroInteractions(connQ.getExecutor(), connQ.storageProvider);
    }

    @Test
    public void testUpdateSession_negativeDuration() {
        connQ.updateSession(-1);
        verifyZeroInteractions(connQ.getExecutor(), connQ.storageProvider);
    }

    //@Test
    //public void testUpdateSession_moreThanZeroDuration() {
    //    connQ.updateSession(60);
    //    final ArgumentCaptor<String> arg = ArgumentCaptor.forClass(String.class);
    //    verify(connQ.getStorageProvider()).addRequest(arg.capture());
    //    verify(connQ.getExecutor()).submit(any(ConnectionProcessor.class));
    //
    //    // verify query parameters
    //    final String queryStr = arg.getValue();
    //    final Map<String, String> queryParams = parseQueryParams(queryStr);
    //    assertEquals(connQ.baseInfoProvider.getAppKey(), queryParams.get("app_key"));
    //    assertNull(queryParams.get("device_id"));
    //    final long curTimestamp = UtilsTime.currentTimestampMs();
    //    final long actualTimestamp = Long.parseLong(queryParams.get("timestamp"));
    //    // this check attempts to account for minor time changes during this test
    //    assertTrue(((curTimestamp - 400) <= actualTimestamp) && ((curTimestamp + 400) >= actualTimestamp));
    //    assertEquals("60", queryParams.get("session_duration"));
    //}

    @Test
    public void testEndSession_checkInternalState() {
        try {
            freshConnQ.endSession(15);
            fail("expected IllegalStateException when internal state is not set up");
        } catch (IllegalStateException ignored) {
            // success!
        }
    }
    /*
       @Test
       public void testEndSession_zeroDuration() {
           connQ.endSession(0);
           final ArgumentCaptor<String> arg = ArgumentCaptor.forClass(String.class);
           verify(connQ.getStorageProvider()).addRequest(arg.capture());
           verify(connQ.getExecutor()).submit(any(ConnectionProcessor.class));

           // verify query parameters
           final String queryStr = arg.getValue();
           final Map<String, String> queryParams = parseQueryParams(queryStr);
           assertEquals(connQ.baseInfoProvider.getAppKey(), queryParams.get("app_key"));
           assertNull(queryParams.get("device_id"));
           final long curTimestamp = UtilsTime.currentTimestampMs();
           final long curTimestampBelow = curTimestamp - timestampAllowance;
           final long curTimestampAbove = curTimestamp + timestampAllowance;
           final long actualTimestamp = Long.parseLong(queryParams.get("timestamp"));
           // this check attempts to account for minor time changes during this test
           assertTrue((curTimestampBelow <= actualTimestamp) && (curTimestampAbove >= actualTimestamp));
           assertFalse(queryParams.containsKey("session_duration"));
           assertEquals("1", queryParams.get("end_session"));
       }

       @Test
       public void testEndSession_negativeDuration() {
           connQ.endSession(-1);
           final ArgumentCaptor<String> arg = ArgumentCaptor.forClass(String.class);
           verify(connQ.getStorageProvider()).addRequest(arg.capture());
           verify(connQ.getExecutor()).submit(any(ConnectionProcessor.class));

           // verify query parameters
           final String queryStr = arg.getValue();
           final Map<String, String> queryParams = parseQueryParams(queryStr);
           assertEquals(connQ.baseInfoProvider.getAppKey(), queryParams.get("app_key"));
           assertNull(queryParams.get("device_id"));
           final long curTimestamp = UtilsTime.currentTimestampMs();
           final long curTimestampBelow = curTimestamp - timestampAllowance;
           final long curTimestampAbove = curTimestamp + timestampAllowance;
           final long actualTimestamp = Long.parseLong(queryParams.get("timestamp"));
           // this check attempts to account for minor time changes during this test
           assertTrue((curTimestampBelow <= actualTimestamp) && (curTimestampAbove >= actualTimestamp));
           assertFalse(queryParams.containsKey("session_duration"));
           assertEquals("1", queryParams.get("end_session"));
       }

       @Test
       public void testEndSession_moreThanZeroDuration() {
           connQ.endSession(15);
           final ArgumentCaptor<String> arg = ArgumentCaptor.forClass(String.class);
           verify(connQ.getStorageProvider()).addRequest(arg.capture());
           verify(connQ.getExecutor()).submit(any(ConnectionProcessor.class));

           // verify query parameters
           final String queryStr = arg.getValue();
           final Map<String, String> queryParams = parseQueryParams(queryStr);
           assertEquals(connQ.baseInfoProvider.getAppKey(), queryParams.get("app_key"));
           assertNull(queryParams.get("device_id"));
           final long curTimestamp = UtilsTime.currentTimestampMs();
           final long curTimestampBelow = curTimestamp - timestampAllowance;
           final long curTimestampAbove = curTimestamp + timestampAllowance;
           final long actualTimestamp = Long.parseLong(queryParams.get("timestamp"));
           // this check attempts to account for minor time changes during this test
           assertTrue((curTimestampBelow <= actualTimestamp) && (curTimestampAbove >= actualTimestamp));
           assertEquals("1", queryParams.get("end_session"));
           assertEquals("15", queryParams.get("session_duration"));
       }
       */
    @Test
    public void testRecordEvents_checkInternalState() {
        try {
            freshConnQ.recordEvents("blahblahblah");
            fail("expected IllegalStateException when internal state is not set up");
        } catch (IllegalStateException ignored) {
            // success!
        }
    }

    /*
    @Test
    public void testRecordEvents() {
        final String eventData = "blahblahblah";
        connQ.recordEvents(eventData);
        final ArgumentCaptor<String> arg = ArgumentCaptor.forClass(String.class);
        verify(connQ.getStorageProvider()).addRequest(arg.capture());
        verify(connQ.getExecutor()).submit(any(ConnectionProcessor.class));

        // verify query parameters
        final String queryStr = arg.getValue();
        final Map<String, String> queryParams = parseQueryParams(queryStr);
        assertEquals(connQ.baseInfoProvider.getAppKey(), queryParams.get("app_key"));
        assertNull(queryParams.get("device_id"));
        final long curTimestamp = UtilsTime.currentTimestampMs();
        final long curTimestampBelow = curTimestamp - timestampAllowance;
        final long curTimestampAbove = curTimestamp + timestampAllowance;
        final long actualTimestamp = Long.parseLong(queryParams.get("timestamp"));
        // this check attempts to account for minor time changes during this test
        assertTrue((curTimestampBelow <= actualTimestamp) && (curTimestampAbove >= actualTimestamp));
        assertEquals(eventData, queryParams.get("events"));
    }
     */

    private Map<String, String> parseQueryParams(final String queryStr) {
        final String urlStr = "http://server?" + queryStr;
        final Uri uri = Uri.parse(urlStr);
        final Set<String> queryParameterNames = uri.getQueryParameterNames();
        final Map<String, String> queryParams = new HashMap<>(queryParameterNames.size());
        for (String paramName : queryParameterNames) {
            queryParams.put(paramName, uri.getQueryParameter(paramName));
        }
        return queryParams;
    }

    @Test
    public void testEnsureExecutor_nullExecutor() {
        assertNull(freshConnQ.getExecutor());
        freshConnQ.ensureExecutor();
        assertNotNull(freshConnQ.getExecutor());
    }

    @Test
    public void testEnsureExecutor_alreadyHasExecutor() {
        ExecutorService executor = connQ.getExecutor();
        assertNotNull(executor);
        connQ.ensureExecutor();
        assertSame(executor, connQ.getExecutor());
    }

    /*

    @Test
    public void testTick_storeHasNoConnections() {
        when(connQ.getStorageProvider().ifNoRequestsAvailable()).thenReturn(true);
        connQ.tick();
        verifyZeroInteractions(connQ.getExecutor());
    }

     */

    //@Test
    //public void testTick_storeHasConnectionsAndFutureIsNull() {
    //    final Future mockFuture = mock(Future.class);
    //    when(connQ.getExecutor().submit(any(ConnectionProcessor.class))).thenReturn(mockFuture);
    //    connQ.tick();
    //    verify(connQ.getExecutor()).submit(any(ConnectionProcessor.class));
    //    assertSame(mockFuture, connQ.getConnectionProcessorFuture());
    //}

    /*
    @Test
    public void testTick_checkConnectionProcessor() {
        final ArgumentCaptor<Runnable> arg = ArgumentCaptor.forClass(Runnable.class);
        when(connQ.getExecutor().submit(arg.capture())).thenReturn(null);
        connQ.tick();
        assertEquals(((ConnectionProcessor) arg.getValue()).getServerURL(), connQ.baseInfoProvider.getServerURL());
        assertSame(((ConnectionProcessor) arg.getValue()).getCountlyStore(), connQ.getStorageProvider());
    }
*/
    //@Test
    //public void testTick_storeHasConnectionsAndFutureIsDone() {
    //    final Future<?> mockFuture = mock(Future.class);
    //    when(mockFuture.isDone()).thenReturn(true);
    //    connQ.setConnectionProcessorFuture(mockFuture);
    //    final Future mockFuture2 = mock(Future.class);
    //    when(connQ.getExecutor().submit(any(ConnectionProcessor.class))).thenReturn(mockFuture2);
    //    connQ.tick();
    //    verify(connQ.getExecutor()).submit(any(ConnectionProcessor.class));
    //    assertSame(mockFuture2, connQ.getConnectionProcessorFuture());
    //}

    //@Test
    //public void testTick_storeHasConnectionsButFutureIsNotDone() {
    //    final Future<?> mockFuture = mock(Future.class);
    //    connQ.setConnectionProcessorFuture(mockFuture);
    //    connQ.tick();
    //    verifyZeroInteractions(connQ.getExecutor());
    //}

    @Test
    public void testPrepareCommonRequest() {
        // 0 - test default common request
        // 1 - test common request with SDK_name and SDK_version override
        for (int a = 0; a < 2; a++) {
            if (a == 1) {
                Countly.sharedInstance().COUNTLY_SDK_NAME = "someBigNew123-+name";
                Countly.sharedInstance().COUNTLY_SDK_VERSION_STRING = "123sdf.v-213";
            }

            UtilsTime.Instant instant = UtilsTime.getCurrentInstant();
            String commonR = connQ.prepareCommonRequestData();
            Assert.assertTrue(commonR.contains("app_key="));
            Assert.assertTrue(commonR.contains("&timestamp="));
            Assert.assertTrue(commonR.contains("&hour="));
            Assert.assertTrue(commonR.contains("&dow="));
            Assert.assertTrue(commonR.contains("&tz="));
            Assert.assertTrue(commonR.contains("&sdk_version="));
            Assert.assertTrue(commonR.contains("&sdk_name="));

            String[] parts = commonR.split("&");

            for (String part : parts) {
                String[] pair = part.split("=");
                switch (pair[0]) {
                    case "app_key":
                        Assert.assertTrue(pair[1].equals(appKey));
                        break;
                    case "tz":
                        Assert.assertTrue(pair[1].equals("" + DeviceInfo.getTimezoneOffset()));
                        break;
                    case "sdk_version":
                        if (a == 0) {
                            Assert.assertTrue(pair[1].equals("21.11.0"));
                        } else if (a == 1) {
                            Assert.assertTrue(pair[1].equals("123sdf.v-213"));
                        }
                        break;
                    case "sdk_name":
                        if (a == 0) {
                            Assert.assertTrue(pair[1].equals("java-native-android"));
                        } else if (a == 1) {
                            Assert.assertTrue(pair[1].equals("someBigNew123-+name"));
                        }
                        break;
                    case "hour":
                        Assert.assertTrue(pair[1].equals("" + instant.hour));
                        break;
                    case "dow":
                        Assert.assertTrue(pair[1].equals("" + instant.dow));
                        break;
                }
            }
        }
    }
}
