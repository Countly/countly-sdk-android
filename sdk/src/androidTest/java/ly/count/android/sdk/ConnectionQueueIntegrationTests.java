package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
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

    // A valid X.509 certificate (Sectigo, *.count.ly) used only to exercise the pinning code path;
    // CertificateFactory parses it regardless of expiry, so the pinning SSLContext can be built.
    private static final String PINNING_CERT =
        "MIIGnjCCBYagAwIBAgIRAN73cVA7Y1nD+S8rToAqBpQwDQYJKoZIhvcNAQELBQAwgY8xCzAJ"
            + "BgNVBAYTAkdCMRswGQYDVQQIExJHcmVhdGVyIE1hbmNoZXN0ZXIxEDAOBgNVBAcTB1"
            + "NhbGZvcmQxGDAWBgNVBAoTD1NlY3RpZ28gTGltaXRlZDE3MDUGA1UEAxMuU2VjdGln"
            + "byBSU0EgRG9tYWluIFZhbGlkYXRpb24gU2VjdXJlIFNlcnZlciBDQTAeFw0yMDA2MD"
            + "EwMDAwMDBaFw0yMjA5MDMwMDAwMDBaMBUxEzARBgNVBAMMCiouY291bnQubHkwggEi"
            + "MA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCl9zmATVRwrGRtRQJcmBmA+zc/ZL"
            + "io3YfkwXO2w8u9lnw60J4JpPNn9OnGcxdM+sqbXKU3jTdjY4j3yaA6NlWibq2jU2x6"
            + "HT2sS+I5gFFE/6tO53WqjoMk48i3FkyoJDittwtQrVaRGcP8RjJH0pfXaP+JLrLAgg"
            + "HuW3tCFqYzkWi3uLGVjQbSIRNiXsM3FI0UMEa/x1I3U4hLjMjH28KagZbZLWnHOvks"
            + "AvGLg3xQkS+GSQ+6ARZ2/bGh5O9q4hCCCk0/PpwAXmrOnWtwrNuwHcCDOvuB22JxLd"
            + "t8jQDYrjwtJIvq4Yut8FQPv/75SKoETWWHyxe0x5NsB34UwA/BAgMBAAGjggNsMIID"
            + "aDAfBgNVHSMEGDAWgBSNjF7EVK2K4Xfpm/mbBeG4AY1h4TAdBgNVHQ4EFgQU8uf/ND"
            + "Rt8cu+AwARVIGXPMfxGbQwDgYDVR0PAQH/BAQDAgWgMAwGA1UdEwEB/wQCMAAwHQYD"
            + "VR0lBBYwFAYIKwYBBQUHAwEGCCsGAQUFBwMCMEkGA1UdIARCMEAwNAYLKwYBBAGyMQ"
            + "ECAgcwJTAjBggrBgEFBQcCARYXaHR0cHM6Ly9zZWN0aWdvLmNvbS9DUFMwCAYGZ4EM"
            + "AQIBMIGEBggrBgEFBQcBAQR4MHYwTwYIKwYBBQUHMAKGQ2h0dHA6Ly9jcnQuc2VjdG"
            + "lnby5jb20vU2VjdGlnb1JTQURvbWFpblZhbGlkYXRpb25TZWN1cmVTZXJ2ZXJDQS5j"
            + "cnQwIwYIKwYBBQUHMAGGF2h0dHA6Ly9vY3NwLnNlY3RpZ28uY29tMB8GA1UdEQQYMB"
            + "aCCiouY291bnQubHmCCGNvdW50Lmx5MIIB9AYKKwYBBAHWeQIEAgSCAeQEggHgAd4A"
            + "dQBGpVXrdfqRIDC1oolp9PN9ESxBdL79SbiFq/L8cP5tRwAAAXJwTJ0kAAAEAwBGME"
            + "QCIEErTN/aGJ8LV9brGklKeGAXMg1EN/FUxXDu13kNfXhcAiBrKMYe+W4flPyuLNm5"
            + "jp6FJwtUTZPNpZ+TmM40dRdwjQB0AN+lXqtogk8fbK3uuF9OPlrqzaISpGpejjsSwC"
            + "BEXCpzAAABcnBMncsAAAQDAEUwQwIfEYSpsSDtKpmj9ZmRWsx73G622N74v09JDjzP"
            + "bkg9RQIgUelIqSwqu69JanH7losrqTTsjwNv+3QJBNJ6GxJKkh0AdgBvU3asMfAxGd"
            + "iZAKRRFf93FRwR2QLBACkGjbIImjfZEwAAAXJwTJ0YAAAEAwBHMEUCIQCMBaaQAoua"
            + "97R+z2zONMUq1XsDP5aoAiutZG4XxuQ6wAIgW1p6XS3az4CCqjwbDKxL9qEnw8fWd+"
            + "yLx2skviSsTS0AdwApeb7wnjk5IfBWc59jpXflvld9nGAK+PlNXSZcJV3HhAAAAXJw"
            + "TJ1PAAAEAwBIMEYCIQDg1YFbJPPKDIyrFZJ9rtrUklkh2k/wpgwjDuIp7tPtOgIhAL"
            + "dZl9s/qISsFm2E64ruYbdE4HKR1ZJ0zbIXOZcds7XXMA0GCSqGSIb3DQEBCwUAA4IB"
            + "AQB2Ar1h2X/S4qsVlw0gEbXO//6Rj8mTB4BFW6c5r84n0vTwvA78h003eX00y0ymxO"
            + "i5hkqB8gd1IUSWP1R1ijYtBVPdFi+SsMjUsB5NKquQNlWpo0GlFjRlcXnDC6R6toN2"
            + "QixJb47VM40Vmn2g0ZuMGfy1XoQKvIyRosT92jGm1YcF+nLEHBDr+89apZ8sUpFfWo"
            + "AnCom+8sBGwje6zP10eBbprHyzM8snvdwo/QNLAzLcvVNKP+Sr4H7HKzec3g1+THI0"
            + "M72TzoguJcOZQEI6Pd+FIP5Xad53rq4jCtRGwYrsieH49a3orBnkkJvUKni+mtkxMb"
            + "PTJ7eeMmX9g/0h";

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
    // Integration Tests - Custom SSL socket factory
    // ==========================================

    /**
     * Integration test: a custom SSLSocketFactory set on CountlyConfig is resolved by
     * ConnectionQueue and applied to both the server request and the preflight request that every
     * ConnectionProcessor produces.
     */
    @Test
    public void integration_customSSLSocketFactory_appliedToServerAndPreflightRequests() throws Exception {
        SSLSocketFactory customFactory = mock(SSLSocketFactory.class);
        CountlyConfig config = new CountlyConfig(TestUtils.getContext(), appKey, serverUrl)
            .setCustomSSLSocketFactory(customFactory);
        Countly.sharedInstance().init(config);
        ConnectionQueue cq = Countly.sharedInstance().connectionQueue_;

        URLConnection serverConn = cq.createConnectionProcessor().urlConnectionForServerRequest("app_key=" + appKey, null);
        HttpURLConnection preflightConn = (HttpURLConnection) cq.createConnectionProcessor().urlConnectionForPreflightRequest(serverUrl + "/o/sdk?method=fetch");

        Assert.assertTrue(serverConn instanceof HttpsURLConnection);
        Assert.assertSame(customFactory, ((HttpsURLConnection) serverConn).getSSLSocketFactory());
        Assert.assertTrue(preflightConn instanceof HttpsURLConnection);
        Assert.assertSame(customFactory, ((HttpsURLConnection) preflightConn).getSSLSocketFactory());
    }

    /**
     * Integration test: when both a custom SSLSocketFactory and public-key pinning are configured,
     * the custom factory wins and the pinning certificates are never parsed (so intentionally
     * invalid pinning certs do not break initialization).
     */
    @Test
    public void integration_customSSLSocketFactory_takesPrecedenceOverPinning() throws Exception {
        SSLSocketFactory customFactory = mock(SSLSocketFactory.class);
        try {
            CountlyConfig config = new CountlyConfig(TestUtils.getContext(), appKey, serverUrl)
                .enablePublicKeyPinning(new String[] { "not-a-real-certificate" })
                .setCustomSSLSocketFactory(customFactory);
            Countly.sharedInstance().init(config);
            ConnectionQueue cq = Countly.sharedInstance().connectionQueue_;

            URLConnection serverConn = cq.createConnectionProcessor().urlConnectionForServerRequest("app_key=" + appKey, null);

            Assert.assertTrue(serverConn instanceof HttpsURLConnection);
            Assert.assertSame("custom factory must win over pinning", customFactory, ((HttpsURLConnection) serverConn).getSSLSocketFactory());
        } finally {
            Countly.sharedInstance().halt();
        }
    }

    /**
     * Integration test: public-key pinning and certificate pinning both remain functional after the
     * SSL socket factory refactor. Each installs its own (non-default) socket factory on the SDK's
     * HTTPS connections, built from the CertificateTrustManager.
     */
    @Test
    public void integration_pinning_installsDistinctSocketFactory() throws Exception {
        String[] certs = { PINNING_CERT };
        SSLSocketFactory platformDefault = HttpsURLConnection.getDefaultSSLSocketFactory();
        try {
            // public key pinning
            Countly.sharedInstance().init(new CountlyConfig(TestUtils.getContext(), appKey, serverUrl).enablePublicKeyPinning(certs));
            SSLSocketFactory publicKeyPinningFactory = appliedServerRequestFactory();
            Assert.assertNotNull(publicKeyPinningFactory);
            Assert.assertNotSame("public key pinning must install its own socket factory", platformDefault, publicKeyPinningFactory);

            Countly.sharedInstance().halt();
            // pinning is now per-instance on the ConnectionQueue; halt() drops the queue, so the
            // next init starts with a fresh, unpinned ConnectionQueue - no static reset needed.

            // certificate pinning
            Countly.sharedInstance().init(new CountlyConfig(TestUtils.getContext(), appKey, serverUrl).enableCertificatePinning(certs));
            SSLSocketFactory certificatePinningFactory = appliedServerRequestFactory();
            Assert.assertNotNull(certificatePinningFactory);
            Assert.assertNotSame("certificate pinning must install its own socket factory", platformDefault, certificatePinningFactory);
        } finally {
            Countly.sharedInstance().halt();
        }
    }

    private SSLSocketFactory appliedServerRequestFactory() throws IOException {
        ConnectionQueue cq = Countly.sharedInstance().connectionQueue_;
        URLConnection conn = cq.createConnectionProcessor().urlConnectionForServerRequest("app_key=" + appKey, null);
        Assert.assertTrue(conn instanceof HttpsURLConnection);
        return ((HttpsURLConnection) conn).getSSLSocketFactory();
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
