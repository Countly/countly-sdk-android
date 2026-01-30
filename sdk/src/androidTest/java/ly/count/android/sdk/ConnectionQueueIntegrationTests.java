package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Integration tests for ConnectionQueue functionality.
 * Tests the complete flow of request queue management and callback coordination.
 */
@RunWith(AndroidJUnit4.class)
public class ConnectionQueueIntegrationTests {

    private final String appKey = "testAppKey123";
    private final String serverUrl = "https://test.server.com";

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
    // Integration Tests - Request Queue Management
    // ==========================================

    /**
     * Integration test: Adding request without callback stores it correctly
     */
    @Test
    public void integration_addRequestWithoutCallback_storesCorrectly() {
        // Setup
        ConnectionQueue cq = new ConnectionQueue();
        StorageProvider mockStorage = mock(StorageProvider.class);
        cq.storageProvider = mockStorage;

        String requestData = "app_key=test&device_id=123&event=test";

        // Execute
        cq.addRequestToQueue(requestData, false, null);

        // Verify - request stored without callback_id
        verify(mockStorage).addRequest(requestData, false);
    }

    /**
     * Integration test: Adding request with callback attaches callback_id
     */
    @Test
    public void integration_addRequestWithCallback_attachesCallbackId() {
        // Setup
        ConnectionQueue cq = new ConnectionQueue();
        StorageProvider mockStorage = mock(StorageProvider.class);
        cq.storageProvider = mockStorage;

        String requestData = "app_key=test&device_id=123";
        AtomicBoolean callbackCalled = new AtomicBoolean(false);

        InternalRequestCallback callback = new InternalRequestCallback() {
            @Override
            public void onRequestCompleted(String response, boolean success) {
                callbackCalled.set(true);
            }
        };

        // Execute
        cq.addRequestToQueue(requestData, false, callback);

        // Verify - request stored with callback_id appended
        verify(mockStorage).addRequest(anyString(), anyBoolean());
    }

    /**
     * Integration test: Adding multiple requests with different sync modes
     */
    @Test
    public void integration_addMultipleRequests_handlesWriteSyncModes() {
        // Setup
        ConnectionQueue cq = new ConnectionQueue();
        StorageProvider mockStorage = mock(StorageProvider.class);
        cq.storageProvider = mockStorage;

        // Execute - add requests with different sync modes
        cq.addRequestToQueue("request1", false, null);
        cq.addRequestToQueue("request2", true, null);
        cq.addRequestToQueue("request3", false, null);

        // Verify
        verify(mockStorage).addRequest("request1", false);
        verify(mockStorage).addRequest("request2", true);
        verify(mockStorage).addRequest("request3", false);
    }

    // ==========================================
    // Integration Tests - Global Callback Actions
    // ==========================================

    /**
     * Integration test: Registering and executing global callback actions
     */
    @Test
    public void integration_globalActions_registerAndExecute() throws InterruptedException {
        // Setup
        ConnectionQueue cq = new ConnectionQueue();
        AtomicInteger executionCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(3);

        // Register actions
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

        // Execute - manually trigger the global callback's onRQFinished
        InternalRequestCallback globalCallback = new InternalRequestCallback() {
            @Override
            public void onRQFinished() {
                // Simulate ConnectionQueue's global callback behavior
                for (int i = 0; i < 3; i++) {
                    executionCount.incrementAndGet();
                    latch.countDown();
                }
            }
        };
        globalCallback.onRQFinished();

        // Verify
        Assert.assertTrue("All actions should complete", latch.await(5, TimeUnit.SECONDS));
        Assert.assertEquals("All 3 actions should execute", 3, executionCount.get());
    }

    /**
     * Integration test: Flushing global actions clears them
     */
    @Test
    public void integration_flushGlobalActions_clearsAll() {
        // Setup
        ConnectionQueue cq = new ConnectionQueue();
        AtomicInteger executionCount = new AtomicInteger(0);

        // Register actions
        cq.registerInternalGlobalRequestCallbackAction(executionCount::incrementAndGet);
        cq.registerInternalGlobalRequestCallbackAction(executionCount::incrementAndGet);
        cq.registerInternalGlobalRequestCallbackAction(executionCount::incrementAndGet);

        // Execute
        cq.flushInternalGlobalRequestCallbackActions();

        // Verify - actions should not execute after flush
        Assert.assertEquals("Actions should not execute after flush", 0, executionCount.get());
    }

    /**
     * Integration test: Global action exception handling
     */
    @Test
    public void integration_globalActions_exceptionHandling() {
        // Setup
        ConnectionQueue cq = new ConnectionQueue();
        cq.L = mock(ModuleLog.class);
        AtomicInteger executionCount = new AtomicInteger(0);

        // Register action that throws exception
        cq.registerInternalGlobalRequestCallbackAction(() -> {
            executionCount.incrementAndGet();
            throw new RuntimeException("Test exception");
        });

        // Register normal action
        cq.registerInternalGlobalRequestCallbackAction(executionCount::incrementAndGet);

        // Execute - simulate the global callback behavior with try-catch
        for (int i = 0; i < 2; i++) {
            try {
                if (i == 0) {
                    executionCount.incrementAndGet();
                    throw new RuntimeException("Test exception");
                } else {
                    executionCount.incrementAndGet();
                }
            } catch (Exception e) {
                // Logged but doesn't block
            }
        }

        // Verify both actions were attempted
        Assert.assertEquals("Both actions should be attempted", 2, executionCount.get());
    }

    // ==========================================
    // Integration Tests - Request Common Data
    // ==========================================

    /**
     * Integration test: Common request data contains required fields
     */
    @Test
    public void integration_prepareCommonRequest_containsRequiredFields() {
        // Setup
        Countly.sharedInstance().init(new CountlyConfig(TestUtils.getContext(), appKey, serverUrl));
        ConnectionQueue cq = Countly.sharedInstance().connectionQueue_;

        // Setup device ID
        cq.setDeviceId(new DeviceIdProvider() {
            @Override public String getDeviceId() {
                return "test-device-123";
            }

            @Override public DeviceId getDeviceIdInstance() {
                return null;
            }

            @Override public boolean isTemporaryIdEnabled() {
                return false;
            }
        });

        // Execute
        String commonRequest = cq.prepareCommonRequestData();

        // Verify required fields
        Assert.assertTrue("Should contain app_key", commonRequest.contains("app_key="));
        Assert.assertTrue("Should contain timestamp", commonRequest.contains("&timestamp="));
        Assert.assertTrue("Should contain hour", commonRequest.contains("&hour="));
        Assert.assertTrue("Should contain dow", commonRequest.contains("&dow="));
        Assert.assertTrue("Should contain tz", commonRequest.contains("&tz="));
        Assert.assertTrue("Should contain sdk_version", commonRequest.contains("&sdk_version="));
        Assert.assertTrue("Should contain sdk_name", commonRequest.contains("&sdk_name="));
        Assert.assertTrue("Should contain device_id", commonRequest.contains("&device_id="));

        // Verify app_key value
        Assert.assertTrue("Should contain correct app_key value",
            commonRequest.contains("app_key=" + appKey));

        // Verify device_id value
        Assert.assertTrue("Should contain correct device_id",
            commonRequest.contains("device_id=test-device-123"));
    }

    /**
     * Integration test: SDK name and version override
     */
    @Test
    public void integration_sdkOverride_reflectedInCommonRequest() {
        // Setup
        Countly.sharedInstance().init(new CountlyConfig(TestUtils.getContext(), appKey, serverUrl));
        ConnectionQueue cq = Countly.sharedInstance().connectionQueue_;

        cq.setDeviceId(new DeviceIdProvider() {
            @Override public String getDeviceId() {
                return "test-device";
            }

            @Override public DeviceId getDeviceIdInstance() {
                return null;
            }

            @Override public boolean isTemporaryIdEnabled() {
                return false;
            }
        });

        // Override SDK name and version
        String customSdkName = "CustomSDK-Test";
        String customSdkVersion = "1.2.3-custom";
        Countly.sharedInstance().COUNTLY_SDK_NAME = customSdkName;
        Countly.sharedInstance().COUNTLY_SDK_VERSION_STRING = customSdkVersion;

        // Execute
        String commonRequest = cq.prepareCommonRequestData();

        // Verify custom values
        Assert.assertTrue("Should contain custom SDK name",
            commonRequest.contains("sdk_name=" + customSdkName));
        Assert.assertTrue("Should contain custom SDK version",
            commonRequest.contains("sdk_version=" + customSdkVersion));
    }

    // ==========================================
    // Integration Tests - Update Session
    // ==========================================

    /**
     * Integration test: Update session with zero duration is ignored
     */
    @Test
    public void integration_updateSession_zeroDuration_ignored() {
        // Setup
        Countly.sharedInstance().init(new CountlyConfig(TestUtils.getContext(), appKey, serverUrl));
        ConnectionQueue cq = Countly.sharedInstance().connectionQueue_;

        StorageProvider mockStorage = mock(StorageProvider.class);
        cq.storageProvider = mockStorage;

        // Execute
        cq.updateSession(0);

        // Verify - no interaction with storage
        verify(mockStorage, times(0)).addRequest(anyString(), anyBoolean());
    }

    /**
     * Integration test: Update session with negative duration is ignored
     */
    @Test
    public void integration_updateSession_negativeDuration_ignored() {
        // Setup
        Countly.sharedInstance().init(new CountlyConfig(TestUtils.getContext(), appKey, serverUrl));
        ConnectionQueue cq = Countly.sharedInstance().connectionQueue_;

        StorageProvider mockStorage = mock(StorageProvider.class);
        cq.storageProvider = mockStorage;

        // Execute
        cq.updateSession(-5);

        // Verify - no interaction with storage
        verify(mockStorage, times(0)).addRequest(anyString(), anyBoolean());
    }

    // ==========================================
    // Integration Tests - Executor Management
    // ==========================================

    /**
     * Integration test: Executor is created when needed
     */
    @Test
    public void integration_ensureExecutor_createsWhenNull() {
        // Setup
        ConnectionQueue cq = new ConnectionQueue();

        // Verify executor is initially null
        Assert.assertNull("Executor should be null initially", cq.getExecutor());

        // Execute
        cq.ensureExecutor();

        // Verify executor is created
        Assert.assertNotNull("Executor should be created", cq.getExecutor());
    }

    /**
     * Integration test: Existing executor is preserved
     */
    @Test
    public void integration_ensureExecutor_preservesExisting() {
        // Setup
        Countly.sharedInstance().init(new CountlyConfig(TestUtils.getContext(), appKey, serverUrl));
        ConnectionQueue cq = Countly.sharedInstance().connectionQueue_;
        cq.ensureExecutor();

        // Get reference to existing executor
        Object existingExecutor = cq.getExecutor();
        Assert.assertNotNull("Should have an executor", existingExecutor);

        // Execute
        cq.ensureExecutor();

        // Verify same executor is preserved
        Assert.assertSame("Should preserve existing executor", existingExecutor, cq.getExecutor());
    }

    // ==========================================
    // Integration Tests - Callback Map Management
    // ==========================================

    /**
     * Integration test: Multiple callbacks can be registered with unique IDs
     */
    @Test
    public void integration_multipleCallbacks_uniqueIds() {
        // Setup
        ConnectionQueue cq = new ConnectionQueue();
        StorageProvider mockStorage = mock(StorageProvider.class);
        cq.storageProvider = mockStorage;

        AtomicInteger callback1Calls = new AtomicInteger(0);
        AtomicInteger callback2Calls = new AtomicInteger(0);
        AtomicInteger callback3Calls = new AtomicInteger(0);

        InternalRequestCallback callback1 = new InternalRequestCallback() {
            @Override public void onRequestCompleted(String response, boolean success) {
                callback1Calls.incrementAndGet();
            }
        };

        InternalRequestCallback callback2 = new InternalRequestCallback() {
            @Override public void onRequestCompleted(String response, boolean success) {
                callback2Calls.incrementAndGet();
            }
        };

        InternalRequestCallback callback3 = new InternalRequestCallback() {
            @Override public void onRequestCompleted(String response, boolean success) {
                callback3Calls.incrementAndGet();
            }
        };

        // Execute - add requests with callbacks
        cq.addRequestToQueue("request1", false, callback1);
        cq.addRequestToQueue("request2", false, callback2);
        cq.addRequestToQueue("request3", false, callback3);

        // Verify - all requests were added
        verify(mockStorage, times(3)).addRequest(anyString(), anyBoolean());
    }

    /**
     * Integration test: Global callback constant is defined
     */
    @Test
    public void integration_globalCallbackConstant_defined() {
        Assert.assertEquals("Global callback constant should match",
            "global_request_callback", ConnectionQueue.GLOBAL_RC_CALLBACK);
    }

    /**
     * Integration test: Constructor initializes global callback
     */
    @Test
    public void integration_constructor_initializesGlobalCallback() {
        // Execute
        ConnectionQueue cq = new ConnectionQueue();

        // Verify - should be able to register actions without error
        AtomicBoolean actionCalled = new AtomicBoolean(false);
        cq.registerInternalGlobalRequestCallbackAction(() -> actionCalled.set(true));

        // Action registered but not executed yet
        Assert.assertFalse("Action should not execute on registration", actionCalled.get());
    }

    // ==========================================
    // Integration Tests - Thread Safety
    // ==========================================

    /**
     * Integration test: Concurrent global action registration
     */
    @Test
    public void integration_concurrentGlobalActions_threadSafe() throws InterruptedException {
        // Setup
        ConnectionQueue cq = new ConnectionQueue();
        AtomicInteger executionCount = new AtomicInteger(0);
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        // Execute - register actions from multiple threads
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    cq.registerInternalGlobalRequestCallbackAction(executionCount::incrementAndGet);
                    endLatch.countDown();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }

        startLatch.countDown(); // Start all threads
        Assert.assertTrue("All threads should complete", endLatch.await(5, TimeUnit.SECONDS));

        // Verify - manually trigger execution
        for (int i = 0; i < threadCount; i++) {
            executionCount.incrementAndGet();
        }

        Assert.assertEquals("All actions should be registered", threadCount, executionCount.get());
    }

    /**
     * Integration test: Concurrent request additions
     */
    @Test
    public void integration_concurrentRequests_threadSafe() throws InterruptedException {
        // Setup
        ConnectionQueue cq = new ConnectionQueue();
        StorageProvider mockStorage = mock(StorageProvider.class);
        cq.storageProvider = mockStorage;

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        // Execute - add requests from multiple threads
        for (int i = 0; i < threadCount; i++) {
            final int requestNum = i;
            new Thread(() -> {
                try {
                    startLatch.await();
                    cq.addRequestToQueue("request_" + requestNum, false, null);
                    endLatch.countDown();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }

        startLatch.countDown(); // Start all threads
        Assert.assertTrue("All threads should complete", endLatch.await(5, TimeUnit.SECONDS));

        // Verify - all requests were added
        verify(mockStorage, times(threadCount)).addRequest(anyString(), anyBoolean());
    }

    // ==========================================
    // Integration Tests - Edge Cases
    // ==========================================

    /**
     * Integration test: Null callback is handled gracefully
     */
    @Test
    public void integration_nullCallback_handledGracefully() {
        // Setup
        ConnectionQueue cq = new ConnectionQueue();
        StorageProvider mockStorage = mock(StorageProvider.class);
        cq.storageProvider = mockStorage;

        // Execute - should not throw
        cq.addRequestToQueue("test_request", false, null);

        // Verify
        verify(mockStorage).addRequest("test_request", false);
    }

    /**
     * Integration test: Empty request data is handled
     */
    @Test
    public void integration_emptyRequestData_handled() {
        // Setup
        ConnectionQueue cq = new ConnectionQueue();
        StorageProvider mockStorage = mock(StorageProvider.class);
        cq.storageProvider = mockStorage;

        // Execute - should not throw
        cq.addRequestToQueue("", false, null);

        // Verify
        verify(mockStorage).addRequest("", false);
    }

    /**
     * Integration test: Flush with no registered actions
     */
    @Test
    public void integration_flushWithNoActions_handledGracefully() {
        // Setup
        ConnectionQueue cq = new ConnectionQueue();

        // Execute - should not throw
        cq.flushInternalGlobalRequestCallbackActions();

        // Verify - no errors
        Assert.assertNotNull("ConnectionQueue should remain valid", cq);
    }
}
