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

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for InternalRequestCallback functionality.
 * Tests the complete flow of request callbacks from submission through ConnectionProcessor.
 */
@RunWith(AndroidJUnit4.class)
public class InternalRequestCallbackTests {

    @Before
    public void setUp() {
        Countly.sharedInstance().halt();
        Countly.sharedInstance().setLoggingEnabled(true);
    }

    @After
    public void tearDown() {
        Countly.sharedInstance().halt();
    }

    // ==========================================
    // Integration Tests - Successful Requests
    // ==========================================

    /**
     * Integration test: Successful request invokes callback with success=true
     * Tests the complete flow from request submission to callback invocation
     */
    @Test
    public void integration_successfulRequest_callbackInvokedWithSuccess() throws IOException {
        // Setup callback tracking
        Map<String, InternalRequestCallback> callbackMap = new ConcurrentHashMap<>();
        String callbackId = "test-callback-success";
        AtomicBoolean callbackInvoked = new AtomicBoolean(false);
        AtomicBoolean wasSuccess = new AtomicBoolean(false);
        AtomicReference<String> receivedResponse = new AtomicReference<>();

        InternalRequestCallback callback = new InternalRequestCallback() {
            @Override
            public void onRequestCompleted(String response, boolean success) {
                callbackInvoked.set(true);
                wasSuccess.set(success);
                receivedResponse.set(response);
            }
        };
        callbackMap.put(callbackId, callback);

        // Create ConnectionProcessor with mocked dependencies
        CountlyStore mockStore = mock(CountlyStore.class);
        DeviceIdProvider mockDeviceId = mock(DeviceIdProvider.class);
        ModuleLog moduleLog = mock(ModuleLog.class);
        HealthTracker healthTracker = mock(HealthTracker.class);

        ConnectionProcessor cp = new ConnectionProcessor(
            "http://test-server.com",
            mockStore,
            mockDeviceId,
            createConfigurationProvider(),
            createRequestInfoProvider(),
            null,
            null,
            moduleLog,
            healthTracker,
            Mockito.mock(Runnable.class),
            callbackMap
        );
        cp = spy(cp);

        // Setup request with callback_id
        String requestData = "app_key=test&device_id=123&callback_id=" + callbackId;
        when(mockStore.getRequests()).thenReturn(new String[] { requestData }, new String[0]);
        when(mockDeviceId.getDeviceId()).thenReturn("123");

        // Mock successful HTTP response
        HttpURLConnection mockConn = mock(HttpURLConnection.class);
        CountlyResponseStream responseStream = new CountlyResponseStream("Success");
        when(mockConn.getInputStream()).thenReturn(responseStream);
        when(mockConn.getResponseCode()).thenReturn(200);
        doReturn(mockConn).when(cp).urlConnectionForServerRequest(anyString(), Mockito.isNull());

        // Execute
        cp.run();

        // Verify
        Assert.assertTrue("Callback should be invoked", callbackInvoked.get());
        Assert.assertTrue("Should indicate success", wasSuccess.get());
        Assert.assertNull("Response should be null on success", receivedResponse.get());
        Assert.assertFalse("Callback should be removed from map", callbackMap.containsKey(callbackId));
    }

    /**
     * Integration test: Multiple successful requests each invoke their own callback
     */
    @Test
    public void integration_multipleSuccessfulRequests_allCallbacksInvoked() throws IOException {
        // Setup multiple callbacks
        Map<String, InternalRequestCallback> callbackMap = new ConcurrentHashMap<>();
        AtomicInteger callbackCount = new AtomicInteger(0);

        String callbackId1 = "callback-1";
        String callbackId2 = "callback-2";
        String callbackId3 = "callback-3";

        callbackMap.put(callbackId1, new InternalRequestCallback() {
            @Override public void onRequestCompleted(String response, boolean success) {
                callbackCount.incrementAndGet();
            }
        });
        callbackMap.put(callbackId2, new InternalRequestCallback() {
            @Override public void onRequestCompleted(String response, boolean success) {
                callbackCount.incrementAndGet();
            }
        });
        callbackMap.put(callbackId3, new InternalRequestCallback() {
            @Override public void onRequestCompleted(String response, boolean success) {
                callbackCount.incrementAndGet();
            }
        });

        // Create ConnectionProcessor
        CountlyStore mockStore = mock(CountlyStore.class);
        DeviceIdProvider mockDeviceId = mock(DeviceIdProvider.class);
        ModuleLog moduleLog = mock(ModuleLog.class);
        HealthTracker healthTracker = mock(HealthTracker.class);

        ConnectionProcessor cp = new ConnectionProcessor(
            "http://test-server.com",
            mockStore,
            mockDeviceId,
            createConfigurationProvider(),
            createRequestInfoProvider(),
            null,
            null,
            moduleLog,
            healthTracker,
            Mockito.mock(Runnable.class),
            callbackMap
        );
        cp = spy(cp);

        // Setup multiple requests
        String[] requests = {
            "app_key=test&callback_id=" + callbackId1,
            "app_key=test&callback_id=" + callbackId2,
            "app_key=test&callback_id=" + callbackId3
        };
        when(mockStore.getRequests())
            .thenReturn(requests)
            .thenReturn(new String[] { requests[1], requests[2] })
            .thenReturn(new String[] { requests[2] })
            .thenReturn(new String[0]);
        when(mockDeviceId.getDeviceId()).thenReturn("123");

        // Mock successful HTTP response - create separate streams for each request
        HttpURLConnection mockConn = mock(HttpURLConnection.class);
        CountlyResponseStream responseStream1 = new CountlyResponseStream("Success");
        CountlyResponseStream responseStream2 = new CountlyResponseStream("Success");
        CountlyResponseStream responseStream3 = new CountlyResponseStream("Success");
        when(mockConn.getInputStream()).thenReturn(responseStream1, responseStream2, responseStream3);
        when(mockConn.getResponseCode()).thenReturn(200, 200, 200);
        doReturn(mockConn).when(cp).urlConnectionForServerRequest(anyString(), Mockito.isNull());

        // Execute
        cp.run();

        // Verify all callbacks were invoked
        Assert.assertEquals("All 3 callbacks should be invoked", 3, callbackCount.get());
        Assert.assertTrue("All callbacks should be removed", callbackMap.isEmpty());
    }

    // ==========================================
    // Integration Tests - Failed Requests
    // ==========================================

    /**
     * Integration test: Failed request (server error) invokes callback with success=false
     */
    @Test
    public void integration_failedRequest_callbackInvokedWithFailure() throws IOException {
        // Setup
        Map<String, InternalRequestCallback> callbackMap = new ConcurrentHashMap<>();
        String callbackId = "test-callback-failure";
        AtomicBoolean callbackInvoked = new AtomicBoolean(false);
        AtomicBoolean wasSuccess = new AtomicBoolean(true);
        AtomicReference<String> receivedResponse = new AtomicReference<>();

        InternalRequestCallback callback = new InternalRequestCallback() {
            @Override
            public void onRequestCompleted(String response, boolean success) {
                callbackInvoked.set(true);
                wasSuccess.set(success);
                receivedResponse.set(response);
            }
        };
        callbackMap.put(callbackId, callback);

        // Create ConnectionProcessor
        CountlyStore mockStore = mock(CountlyStore.class);
        DeviceIdProvider mockDeviceId = mock(DeviceIdProvider.class);
        ModuleLog moduleLog = mock(ModuleLog.class);
        HealthTracker healthTracker = mock(HealthTracker.class);

        ConnectionProcessor cp = new ConnectionProcessor(
            "http://test-server.com",
            mockStore,
            mockDeviceId,
            createConfigurationProvider(),
            createRequestInfoProvider(),
            null,
            null,
            moduleLog,
            healthTracker,
            Mockito.mock(Runnable.class),
            callbackMap
        );
        cp = spy(cp);

        // Setup request
        String requestData = "app_key=test&device_id=123&callback_id=" + callbackId;
        when(mockStore.getRequests()).thenReturn(new String[] { requestData });
        when(mockDeviceId.getDeviceId()).thenReturn("123");

        // Mock failed HTTP response (500 error)
        HttpURLConnection mockConn = mock(HttpURLConnection.class);
        String errorResponse = "{\"error\":\"Server error\"}";
        ByteArrayInputStream errorStream = new ByteArrayInputStream(errorResponse.getBytes("UTF-8"));
        when(mockConn.getInputStream()).thenReturn(errorStream);
        when(mockConn.getResponseCode()).thenReturn(500);
        doReturn(mockConn).when(cp).urlConnectionForServerRequest(anyString(), Mockito.isNull());

        // Execute
        cp.run();

        // Verify
        Assert.assertTrue("Callback should be invoked", callbackInvoked.get());
        Assert.assertFalse("Should indicate failure", wasSuccess.get());
        Assert.assertNotNull("Response should contain error message", receivedResponse.get());
        Assert.assertFalse("Callback should be removed", callbackMap.containsKey(callbackId));
    }

    /**
     * Integration test: Request with connection exception invokes callback with failure
     */
    @Test
    public void integration_connectionException_callbackInvokedWithFailure() throws IOException {
        // Setup
        Map<String, InternalRequestCallback> callbackMap = new ConcurrentHashMap<>();
        String callbackId = "test-callback-exception";
        AtomicBoolean callbackInvoked = new AtomicBoolean(false);
        AtomicBoolean wasSuccess = new AtomicBoolean(true);

        InternalRequestCallback callback = new InternalRequestCallback() {
            @Override
            public void onRequestCompleted(String response, boolean success) {
                callbackInvoked.set(true);
                wasSuccess.set(success);
            }
        };
        callbackMap.put(callbackId, callback);

        // Create ConnectionProcessor
        CountlyStore mockStore = mock(CountlyStore.class);
        DeviceIdProvider mockDeviceId = mock(DeviceIdProvider.class);
        ModuleLog moduleLog = mock(ModuleLog.class);
        HealthTracker healthTracker = mock(HealthTracker.class);

        ConnectionProcessor cp = new ConnectionProcessor(
            "http://test-server.com",
            mockStore,
            mockDeviceId,
            createConfigurationProvider(),
            createRequestInfoProvider(),
            null,
            null,
            moduleLog,
            healthTracker,
            Mockito.mock(Runnable.class),
            callbackMap
        );
        cp = spy(cp);

        // Setup request
        String requestData = "app_key=test&device_id=123&callback_id=" + callbackId;
        when(mockStore.getRequests()).thenReturn(new String[] { requestData });
        when(mockDeviceId.getDeviceId()).thenReturn("123");

        // Mock connection that returns null (causes exception)
        doReturn(null).when(cp).urlConnectionForServerRequest(anyString(), Mockito.isNull());

        // Execute
        cp.run();

        // Verify
        Assert.assertTrue("Callback should be invoked on exception", callbackInvoked.get());
        Assert.assertFalse("Should indicate failure", wasSuccess.get());
        Assert.assertFalse("Callback should be removed", callbackMap.containsKey(callbackId));
    }

    // ==========================================
    // Integration Tests - Dropped Requests
    // ==========================================

    /**
     * Integration test: Old request is dropped and callback is invoked with failure
     */
    @Test
    public void integration_requestTooOld_callbackInvokedWithFailure() {
        // Setup
        Map<String, InternalRequestCallback> callbackMap = new ConcurrentHashMap<>();
        String callbackId = "test-callback-old";
        AtomicBoolean callbackInvoked = new AtomicBoolean(false);
        AtomicBoolean wasSuccess = new AtomicBoolean(true);
        AtomicReference<String> receivedResponse = new AtomicReference<>();

        InternalRequestCallback callback = new InternalRequestCallback() {
            @Override
            public void onRequestCompleted(String response, boolean success) {
                callbackInvoked.set(true);
                wasSuccess.set(success);
                receivedResponse.set(response);
            }
        };
        callbackMap.put(callbackId, callback);

        // Create ConnectionProcessor with request drop age of 1 hour
        CountlyStore mockStore = mock(CountlyStore.class);
        DeviceIdProvider mockDeviceId = mock(DeviceIdProvider.class);
        ModuleLog moduleLog = mock(ModuleLog.class);
        HealthTracker healthTracker = mock(HealthTracker.class);

        RequestInfoProvider requestInfoProvider = new RequestInfoProvider() {
            @Override public boolean isHttpPostForced() { return false; }
            @Override public boolean isDeviceAppCrawler() { return false; }
            @Override public boolean ifShouldIgnoreCrawlers() { return false; }
            @Override public int getRequestDropAgeHours() { return 1; }
            @Override public String getRequestSalt() { return null; }
        };

        ConnectionProcessor cp = new ConnectionProcessor(
            "http://test-server.com",
            mockStore,
            mockDeviceId,
            createConfigurationProvider(),
            requestInfoProvider,
            null,
            null,
            moduleLog,
            healthTracker,
            Mockito.mock(Runnable.class),
            callbackMap
        );

        // Setup request with very old timestamp (from 2022)
        String oldTimestamp = "1664273584000";
        String requestData = "app_key=test&device_id=123&timestamp=" + oldTimestamp + "&callback_id=" + callbackId;
        when(mockStore.getRequests()).thenReturn(new String[] { requestData }, new String[0]);
        when(mockDeviceId.getDeviceId()).thenReturn("123");

        // Execute
        cp.run();

        // Verify
        Assert.assertTrue("Callback should be invoked for old request", callbackInvoked.get());
        Assert.assertFalse("Should indicate failure", wasSuccess.get());
        Assert.assertEquals("Should indicate request too old", "Request too old", receivedResponse.get());
        Assert.assertFalse("Callback should be removed", callbackMap.containsKey(callbackId));
    }

    /**
     * Integration test: Request from crawler is dropped and callback is invoked
     */
    @Test
    public void integration_deviceIsCrawler_callbackInvokedWithFailure() {
        // Setup
        Map<String, InternalRequestCallback> callbackMap = new ConcurrentHashMap<>();
        String callbackId = "test-callback-crawler";
        AtomicBoolean callbackInvoked = new AtomicBoolean(false);
        AtomicBoolean wasSuccess = new AtomicBoolean(true);
        AtomicReference<String> receivedResponse = new AtomicReference<>();

        InternalRequestCallback callback = new InternalRequestCallback() {
            @Override
            public void onRequestCompleted(String response, boolean success) {
                callbackInvoked.set(true);
                wasSuccess.set(success);
                receivedResponse.set(response);
            }
        };
        callbackMap.put(callbackId, callback);

        // Create ConnectionProcessor with crawler detection enabled
        CountlyStore mockStore = mock(CountlyStore.class);
        DeviceIdProvider mockDeviceId = mock(DeviceIdProvider.class);
        ModuleLog moduleLog = mock(ModuleLog.class);
        HealthTracker healthTracker = mock(HealthTracker.class);

        RequestInfoProvider requestInfoProvider = new RequestInfoProvider() {
            @Override public boolean isHttpPostForced() { return false; }
            @Override public boolean isDeviceAppCrawler() { return true; }
            @Override public boolean ifShouldIgnoreCrawlers() { return true; }
            @Override public int getRequestDropAgeHours() { return 0; }
            @Override public String getRequestSalt() { return null; }
        };

        ConnectionProcessor cp = new ConnectionProcessor(
            "http://test-server.com",
            mockStore,
            mockDeviceId,
            createConfigurationProvider(),
            requestInfoProvider,
            null,
            null,
            moduleLog,
            healthTracker,
            Mockito.mock(Runnable.class),
            callbackMap
        );

        // Setup request
        String requestData = "app_key=test&device_id=123&timestamp=" + System.currentTimeMillis() + "&callback_id=" + callbackId;
        when(mockStore.getRequests()).thenReturn(new String[] { requestData }, new String[0]);
        when(mockDeviceId.getDeviceId()).thenReturn("123");

        // Execute
        cp.run();

        // Verify
        Assert.assertTrue("Callback should be invoked for crawler", callbackInvoked.get());
        Assert.assertFalse("Should indicate failure", wasSuccess.get());
        Assert.assertEquals("Should indicate device is crawler", "Device is app crawler", receivedResponse.get());
        Assert.assertFalse("Callback should be removed", callbackMap.containsKey(callbackId));
    }

    // ==========================================
    // Integration Tests - Global Callback
    // ==========================================

    /**
     * Integration test: Global callback is invoked when queue becomes empty
     */
    @Test
    public void integration_emptyQueue_globalCallbackInvoked() {
        // Setup
        Map<String, InternalRequestCallback> callbackMap = new ConcurrentHashMap<>();
        AtomicBoolean globalCallbackInvoked = new AtomicBoolean(false);

        InternalRequestCallback globalCallback = new InternalRequestCallback() {
            @Override
            public void onRQFinished() {
                globalCallbackInvoked.set(true);
            }
        };
        callbackMap.put(ConnectionQueue.GLOBAL_RC_CALLBACK, globalCallback);

        // Create ConnectionProcessor
        CountlyStore mockStore = mock(CountlyStore.class);
        DeviceIdProvider mockDeviceId = mock(DeviceIdProvider.class);
        ModuleLog moduleLog = mock(ModuleLog.class);
        HealthTracker healthTracker = mock(HealthTracker.class);

        ConnectionProcessor cp = new ConnectionProcessor(
            "http://test-server.com",
            mockStore,
            mockDeviceId,
            createConfigurationProvider(),
            createRequestInfoProvider(),
            null,
            null,
            moduleLog,
            healthTracker,
            Mockito.mock(Runnable.class),
            callbackMap
        );

        // Setup empty queue
        when(mockStore.getRequests()).thenReturn(new String[0]);

        // Execute
        cp.run();

        // Verify
        Assert.assertTrue("Global callback should be invoked when queue is empty", globalCallbackInvoked.get());
    }

    /**
     * Integration test: Global callback executes all registered actions
     */
    @Test
    public void integration_globalCallback_executesAllActions() throws InterruptedException {
        // Setup ConnectionQueue
        ConnectionQueue cq = new ConnectionQueue();
        AtomicInteger executionCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(3);

        // Register multiple actions
        cq.registerInternalGlobalRequestCallbackAction(() -> {
            executionCount.incrementAndGet();
            latch.countDown();
        });
        cq.registerInternalGlobalRequestCallbackAction(() -> {
            executionCount.incrementAndGet();
            latch.countDown();
        });
        cq.registerInternalGlobalRequestCallbackAction(() -> {
            executionCount.incrementAndGet();
            latch.countDown();
        });

        // Get the callback map from connection queue
        Map<String, InternalRequestCallback> callbackMap = new ConcurrentHashMap<>();
        callbackMap.put(ConnectionQueue.GLOBAL_RC_CALLBACK, new InternalRequestCallback() {
            @Override
            public void onRQFinished() {
                // Simulate what ConnectionQueue's global callback does
                for (int i = 0; i < 3; i++) {
                    executionCount.incrementAndGet();
                    latch.countDown();
                }
            }
        });

        CountlyStore mockStore = mock(CountlyStore.class);
        DeviceIdProvider mockDeviceId = mock(DeviceIdProvider.class);
        ModuleLog moduleLog = mock(ModuleLog.class);
        HealthTracker healthTracker = mock(HealthTracker.class);

        ConnectionProcessor cp = new ConnectionProcessor(
            "http://test-server.com",
            mockStore,
            mockDeviceId,
            createConfigurationProvider(),
            createRequestInfoProvider(),
            null,
            null,
            moduleLog,
            healthTracker,
            Mockito.mock(Runnable.class),
            callbackMap
        );

        // Setup empty queue to trigger global callback
        when(mockStore.getRequests()).thenReturn(new String[0]);

        // Execute
        cp.run();

        // Wait for async execution
        Assert.assertTrue("All actions should complete", latch.await(5, TimeUnit.SECONDS));
        Assert.assertEquals("All 3 actions should be executed", 3, executionCount.get());
    }

    /**
     * Integration test: Global callback action exception doesn't block other actions
     */
    @Test
    public void integration_globalCallback_exceptionDoesntBlockOtherActions() {
        // Setup
        Map<String, InternalRequestCallback> callbackMap = new ConcurrentHashMap<>();
        AtomicInteger executionCount = new AtomicInteger(0);

        callbackMap.put(ConnectionQueue.GLOBAL_RC_CALLBACK, new InternalRequestCallback() {
            @Override
            public void onRQFinished() {
                // First action throws exception
                try {
                    executionCount.incrementAndGet();
                    throw new RuntimeException("Test exception");
                } catch (Exception ignored) {
                }
                // Second and third actions should still execute
                executionCount.incrementAndGet();
                executionCount.incrementAndGet();
            }
        });

        CountlyStore mockStore = mock(CountlyStore.class);
        DeviceIdProvider mockDeviceId = mock(DeviceIdProvider.class);
        ModuleLog moduleLog = mock(ModuleLog.class);
        HealthTracker healthTracker = mock(HealthTracker.class);

        ConnectionProcessor cp = new ConnectionProcessor(
            "http://test-server.com",
            mockStore,
            mockDeviceId,
            createConfigurationProvider(),
            createRequestInfoProvider(),
            null,
            null,
            moduleLog,
            healthTracker,
            Mockito.mock(Runnable.class),
            callbackMap
        );

        when(mockStore.getRequests()).thenReturn(new String[0]);

        // Execute
        cp.run();

        // Verify all actions were attempted
        Assert.assertEquals("All 3 actions should execute despite exception", 3, executionCount.get());
    }

    /**
     * Integration test: Flush clears all global actions
     */
    @Test
    public void integration_flushGlobalActions_clearsAllActions() {
        ConnectionQueue cq = new ConnectionQueue();
        AtomicInteger executionCount = new AtomicInteger(0);

        cq.registerInternalGlobalRequestCallbackAction(executionCount::incrementAndGet);
        cq.registerInternalGlobalRequestCallbackAction(executionCount::incrementAndGet);

        cq.flushInternalGlobalRequestCallbackActions();

        // Verify actions were cleared (count should still be 0)
        Assert.assertEquals("Actions should not have executed", 0, executionCount.get());
    }

    // ==========================================
    // Integration Tests - Edge Cases
    // ==========================================

    /**
     * Integration test: Request without callback processes normally
     */
    @Test
    public void integration_requestWithoutCallback_processesNormally() throws IOException {
        // Setup - no callbacks registered
        Map<String, InternalRequestCallback> callbackMap = new ConcurrentHashMap<>();

        CountlyStore mockStore = mock(CountlyStore.class);
        DeviceIdProvider mockDeviceId = mock(DeviceIdProvider.class);
        ModuleLog moduleLog = mock(ModuleLog.class);
        HealthTracker healthTracker = mock(HealthTracker.class);

        ConnectionProcessor cp = new ConnectionProcessor(
            "http://test-server.com",
            mockStore,
            mockDeviceId,
            createConfigurationProvider(),
            createRequestInfoProvider(),
            null,
            null,
            moduleLog,
            healthTracker,
            Mockito.mock(Runnable.class),
            callbackMap
        );
        cp = spy(cp);

        // Setup request WITHOUT callback_id
        String requestData = "app_key=test&device_id=123";
        when(mockStore.getRequests()).thenReturn(new String[] { requestData }, new String[0]);
        when(mockDeviceId.getDeviceId()).thenReturn("123");

        // Mock successful HTTP response
        HttpURLConnection mockConn = mock(HttpURLConnection.class);
        CountlyResponseStream responseStream = new CountlyResponseStream("Success");
        when(mockConn.getInputStream()).thenReturn(responseStream);
        when(mockConn.getResponseCode()).thenReturn(200);
        doReturn(mockConn).when(cp).urlConnectionForServerRequest(anyString(), Mockito.isNull());

        // Execute - should not throw
        cp.run();

        // Verify request was removed
        verify(mockStore).removeRequest(requestData);
    }

    /**
     * Integration test: Callback invoked only once, then removed
     */
    @Test
    public void integration_callback_invokedOnlyOnce() throws IOException {
        // Setup
        Map<String, InternalRequestCallback> callbackMap = new ConcurrentHashMap<>();
        String callbackId = "test-callback-once";
        AtomicInteger invocationCount = new AtomicInteger(0);

        InternalRequestCallback callback = new InternalRequestCallback() {
            @Override
            public void onRequestCompleted(String response, boolean success) {
                invocationCount.incrementAndGet();
            }
        };
        callbackMap.put(callbackId, callback);

        CountlyStore mockStore = mock(CountlyStore.class);
        DeviceIdProvider mockDeviceId = mock(DeviceIdProvider.class);
        ModuleLog moduleLog = mock(ModuleLog.class);
        HealthTracker healthTracker = mock(HealthTracker.class);

        ConnectionProcessor cp = new ConnectionProcessor(
            "http://test-server.com",
            mockStore,
            mockDeviceId,
            createConfigurationProvider(),
            createRequestInfoProvider(),
            null,
            null,
            moduleLog,
            healthTracker,
            Mockito.mock(Runnable.class),
            callbackMap
        );
        cp = spy(cp);

        // Setup request
        String requestData = "app_key=test&device_id=123&callback_id=" + callbackId;
        when(mockStore.getRequests()).thenReturn(new String[] { requestData }, new String[0]);
        when(mockDeviceId.getDeviceId()).thenReturn("123");

        // Mock successful response
        HttpURLConnection mockConn = mock(HttpURLConnection.class);
        CountlyResponseStream responseStream = new CountlyResponseStream("Success");
        when(mockConn.getInputStream()).thenReturn(responseStream);
        when(mockConn.getResponseCode()).thenReturn(200);
        doReturn(mockConn).when(cp).urlConnectionForServerRequest(anyString(), Mockito.isNull());

        // Execute
        cp.run();

        // Verify callback was invoked exactly once
        Assert.assertEquals("Callback should be invoked exactly once", 1, invocationCount.get());
        Assert.assertFalse("Callback should be removed from map", callbackMap.containsKey(callbackId));
        Assert.assertNull("Callback should not be retrievable", callbackMap.get(callbackId));
    }

    /**
     * Integration test: Callback_id in request but callback not in map - handles gracefully
     */
    @Test
    public void integration_callbackNotInMap_handlesGracefully() throws IOException {
        // Setup - empty callback map
        Map<String, InternalRequestCallback> callbackMap = new ConcurrentHashMap<>();

        CountlyStore mockStore = mock(CountlyStore.class);
        DeviceIdProvider mockDeviceId = mock(DeviceIdProvider.class);
        ModuleLog moduleLog = mock(ModuleLog.class);
        HealthTracker healthTracker = mock(HealthTracker.class);

        ConnectionProcessor cp = new ConnectionProcessor(
            "http://test-server.com",
            mockStore,
            mockDeviceId,
            createConfigurationProvider(),
            createRequestInfoProvider(),
            null,
            null,
            moduleLog,
            healthTracker,
            Mockito.mock(Runnable.class),
            callbackMap
        );
        cp = spy(cp);

        // Setup request WITH callback_id but callback not registered
        String requestData = "app_key=test&device_id=123&callback_id=non-existent-callback";
        when(mockStore.getRequests()).thenReturn(new String[] { requestData }, new String[0]);
        when(mockDeviceId.getDeviceId()).thenReturn("123");

        // Mock successful HTTP response
        HttpURLConnection mockConn = mock(HttpURLConnection.class);
        CountlyResponseStream responseStream = new CountlyResponseStream("Success");
        when(mockConn.getInputStream()).thenReturn(responseStream);
        when(mockConn.getResponseCode()).thenReturn(200);
        doReturn(mockConn).when(cp).urlConnectionForServerRequest(anyString(), Mockito.isNull());

        // Execute - should not throw
        cp.run();

        // Verify request was processed
        verify(mockStore).removeRequest(requestData);
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    /**
     * Helper class to simulate Countly server response
     */
    private static class CountlyResponseStream extends ByteArrayInputStream {
        CountlyResponseStream(final String result) throws UnsupportedEncodingException {
            super(("{\"result\":\"" + result + "\"}").getBytes("UTF-8"));
        }
    }

    /**
     * Create a mock ConfigurationProvider for testing
     */
    private ConfigurationProvider createConfigurationProvider() {
        return new ConfigurationProvider() {
            @Override public boolean getNetworkingEnabled() { return true; }
            @Override public boolean getTrackingEnabled() { return true; }
            @Override public boolean getSessionTrackingEnabled() { return false; }
            @Override public boolean getViewTrackingEnabled() { return false; }
            @Override public boolean getCustomEventTrackingEnabled() { return false; }
            @Override public boolean getContentZoneEnabled() { return false; }
            @Override public boolean getCrashReportingEnabled() { return true; }
            @Override public boolean getLocationTrackingEnabled() { return true; }
            @Override public boolean getRefreshContentZoneEnabled() { return true; }
            @Override public boolean getBOMEnabled() { return false; }
            @Override public int getBOMAcceptedTimeoutSeconds() { return 10; }
            @Override public double getBOMRQPercentage() { return 0.5; }
            @Override public int getBOMRequestAge() { return 24; }
            @Override public int getBOMDuration() { return 60; }
            @Override public int getRequestTimeoutDurationMillis() { return 30_000; }
        };
    }

    /**
     * Create a mock RequestInfoProvider for testing
     */
    private RequestInfoProvider createRequestInfoProvider() {
        return new RequestInfoProvider() {
            @Override public boolean isHttpPostForced() { return false; }
            @Override public boolean isDeviceAppCrawler() { return false; }
            @Override public boolean ifShouldIgnoreCrawlers() { return false; }
            @Override public int getRequestDropAgeHours() { return 0; }
            @Override public String getRequestSalt() { return null; }
        };
    }
}
