package ly.count.android.sdk;

import android.app.Activity;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

@RunWith(AndroidJUnit4.class)
public class ModuleConfigurationTests {
    private CountlyStore countlyStore;
    private Countly countly;

    /**
     * Finishes all running TransparentActivity instances and waits for them to be destroyed.
     * This prevents crashes when halt() is called while activities are still running.
     */
    private void finishAllTransparentActivities() {
        // First, finish all activities
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                if (activity instanceof TransparentActivity) {
                    activity.finish();
                }
            }
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.STARTED)) {
                if (activity instanceof TransparentActivity) {
                    activity.finish();
                }
            }
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.CREATED)) {
                if (activity instanceof TransparentActivity) {
                    activity.finish();
                }
            }
        });

        // Wait until all TransparentActivity instances are destroyed
        long startTime = System.currentTimeMillis();
        long timeout = 5000; // 5 second timeout

        while (System.currentTimeMillis() - startTime < timeout) {
            final boolean[] hasRunningActivity = { false };
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                for (Stage stage : new Stage[] { Stage.RESUMED, Stage.STARTED, Stage.CREATED, Stage.STOPPED, Stage.PAUSED }) {
                    for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage)) {
                        if (activity instanceof TransparentActivity) {
                            hasRunningActivity[0] = true;
                            return;
                        }
                    }
                }
            });

            if (!hasRunningActivity[0]) {
                return; // All activities destroyed
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
        }
    }

    @Before
    public void setUp() {
        // Finish any stale TransparentActivity instances from previous tests
        // before calling halt() to prevent NPE crashes
        finishAllTransparentActivities();
        countlyStore = TestUtils.getCountlyStore();
        countlyStore.clear();
        Countly.sharedInstance().halt();
    }

    @After
    public void tearDown() {
        finishAllTransparentActivities();
        TestUtils.getCountlyStore().clear();
        Countly.sharedInstance().halt();
    }

    // ================ Basic Configuration Tests ================

    /**
     * Test default configuration when server config is disabled and storage is empty
     */
    @Test
    public void defaultConfig_WhenServerConfigDisabledAndStorageEmpty() {
        countlyStore.clear();
        CountlyConfig config = TestUtils.createIRGeneratorConfig(null);
        countly = new Countly().init(config);

        Assert.assertNull(countlyStore.getServerConfig());
        assertDefaultConfigValues(countly);
    }

    /**
     * Test default configuration when server config is enabled and storage is empty
     */
    @Test
    public void defaultConfig_WhenServerConfigEnabledAndStorageEmpty() {
        CountlyConfig config = TestUtils.createIRGeneratorConfig(null);
        config.enableServerConfiguration();
        countly = new Countly().init(config);

        Assert.assertNull(countlyStore.getServerConfig());
        assertDefaultConfigValues(countly);
    }

    // ================ Server Configuration Tests ================

    /**
     * Test configuration when server config is enabled and all properties are allowing
     */
    @Test
    public void serverConfig_WhenEnabledAndAllPropertiesAllowing() throws JSONException {
        countlyStore.setServerConfig(createStorageConfig(true, true, true));
        CountlyConfig config = TestUtils.createIRGeneratorConfig(null);
        config.enableServerConfiguration();
        countly = new Countly().init(config);

        Assert.assertNotNull(countlyStore.getServerConfig());
        assertDefaultConfigValues(countly);
    }

    /**
     * Test configuration when server config is enabled and all properties are forbidding
     */
    @Test
    public void serverConfig_WhenEnabledAndAllPropertiesForbidding() throws JSONException {
        countlyStore.setServerConfig(createStorageConfig(false, false, false));
        CountlyConfig config = TestUtils.createIRGeneratorConfig(null);
        config.enableServerConfiguration();
        countly = new Countly().init(config);

        Assert.assertNotNull(countlyStore.getServerConfig());
        Assert.assertFalse(countly.moduleConfiguration.getNetworkingEnabled());
        Assert.assertFalse(countly.moduleConfiguration.getTrackingEnabled());
        Assert.assertFalse(countly.moduleConfiguration.getCrashReportingEnabled());
    }

    /**
     * Test configuration when server config is disabled and all properties are allowing
     */
    @Test
    public void serverConfig_WhenDisabledAndAllPropertiesAllowing() throws JSONException {
        countlyStore.setServerConfig(createStorageConfig(true, true, true));
        CountlyConfig config = TestUtils.createIRGeneratorConfig(null);
        countly = Countly.sharedInstance().init(config);

        Assert.assertNotNull(countlyStore.getServerConfig());
        assertDefaultConfigValues(countly);
    }

    /**
     * Test configuration when server config is disabled and all properties are forbidding
     * This test is expected to fail as server config is deprecated
     */
    @Test(expected = AssertionError.class)
    public void serverConfig_WhenDisabledAndAllPropertiesForbidding() throws JSONException {
        countlyStore.setServerConfig(createStorageConfig(false, false, false));
        CountlyConfig config = TestUtils.createIRGeneratorConfig(null);
        countly = new Countly().init(config);

        Assert.assertNotNull(countlyStore.getServerConfig());
        assertDefaultConfigValues(countly);
    }

    // ================ Server Configuration Validation Tests ================

    /**
     * Tests that default server configuration values are correctly applied when no custom configuration is provided.
     * Verifies that all default values match the expected configuration.
     */
    @Test
    public void serverConfig_DefaultValues() throws InterruptedException {
        countly = new Countly().init(TestUtils.createBaseConfig().setLoggingEnabled(false));
        Thread.sleep(2000); // simulate sdk initialization delay
        new ServerConfigBuilder().defaults().validateAgainst(countly);
    }

    /**
     * Tests that custom server configuration values are correctly applied when provided directly.
     * Verifies that the configuration is properly parsed and applied to the SDK.
     */
    @Test
    public void serverConfig_ProvidedValues() throws InterruptedException, JSONException {
        initServerConfigWithValues(CountlyConfig::setSDKBehaviorSettings);
    }

    /**
     * Tests that server configuration values are correctly applied when using an immediate request generator.
     * Verifies that the configuration is properly handled when received through the request generator.
     */
    @Test
    public void serverConfig_WithImmediateRequestGenerator() throws InterruptedException, JSONException {
        initServerConfigWithValues((countlyConfig, serverConfig) -> {
            countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(serverConfig);
        });
    }

    /**
     * Tests that all features work correctly with default server configuration.
     * Verifies that all SDK features (sessions, events, views, crashes, etc.) function as expected
     * when using default configuration values.
     */
    @Test
    public void serverConfig_Defaults_AllFeatures() throws JSONException, InterruptedException {
        base_allFeatures((sc) -> {
        }, 1, 1, 1, 2, 1);
    }

    /**
     * Tests that all features are properly disabled when explicitly configured to be disabled.
     * Verifies that no requests are generated and no data is collected when all features are disabled.
     */
    @Test
    public void disable_allFeatures() throws JSONException, InterruptedException {
        ServerConfigBuilder sc = new ServerConfigBuilder();
        sc.networking(false).sessionTracking(false).customEventTracking(false).viewTracking(false)
            .crashReporting(false).locationTracking(false).contentZone(false).refreshContentZone(false).tracking(false).consentRequired(false);

        int[] counts = setupTest_allFeatures(sc.buildJson());

        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);
        Assert.assertEquals(0, TestUtils.getCountlyStore().getEventQueueSize());

        flow_allFeatures();
        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);

        immediateFlow_allFeatures();
        Assert.assertEquals(0, countlyStore.getEventQueueSize());

        feedbackFlow_allFeatures();
        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);
        Assert.assertEquals(0, countlyStore.getEventQueueSize());

        validateCounts(counts, 0, 0, 0, 0, 1);
    }

    /**
     * Tests that consent requirement is properly handled when enabled.
     * Verifies that:
     * 1. Initial consent request is sent
     * 2. No data is collected until consent is given
     * 3. Location is properly handled with empty value
     */
    @Test
    public void consentEnabled_allFeatures() throws JSONException, InterruptedException {
        ServerConfigBuilder sc = new ServerConfigBuilder();
        sc.consentRequired(true);

        int[] counts = setupTest_allFeatures(sc.buildJson());

        Assert.assertEquals(2, TestUtils.getCurrentRQ().length);
        Assert.assertEquals(0, countlyStore.getEventQueueSize());
        ModuleConsentTests.validateConsentRequest(TestUtils.commonDeviceId, 0, new boolean[] { false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false });
        TestUtils.validateRequest(TestUtils.commonDeviceId, TestUtils.map("location", ""), 1);

        flow_allFeatures();
        immediateFlow_allFeatures();
        Assert.assertEquals(0, countlyStore.getEventQueueSize());
        feedbackFlow_allFeatures();

        Assert.assertEquals(2, TestUtils.getCurrentRQ().length);
        Assert.assertEquals(0, countlyStore.getEventQueueSize());

        validateCounts(counts, 1, 0, 0, 0, 1);
    }

    /**
     * Tests that session tracking is properly disabled when configured.
     * Verifies that:
     * 1. No session requests are generated
     * 2. Other features (events, views, crashes) continue to work
     * 3. Request counts and order are maintained correctly
     */
    @Test
    public void sessionsDisabled_allFeatures() throws JSONException, InterruptedException {
        ServerConfigBuilder sc = new ServerConfigBuilder();
        sc.sessionTracking(false);
        int[] counts = setupTest_allFeatures(sc.buildJson());

        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);
        Assert.assertEquals(0, TestUtils.getCountlyStore().getEventQueueSize());

        String stackTrace = flow_allFeatures();

        ModuleCrashTests.validateCrash(stackTrace, "", false, false, 7, 0, TestUtils.map(), 0, TestUtils.map(), new ArrayList<>());
        validateEventInRQ("test_event", TestUtils.map(), 1, 7, 0, 2);
        validateEventInRQ("[CLY]_view", TestUtils.map("name", "test_view", "segment", "Android", "visit", "1"), 1, 7, 1, 2);
        ModuleUserProfileTests.validateUserProfileRequest(2, 7, TestUtils.map(), TestUtils.map("test_property", "test_value"));
        TestUtils.validateRequest(TestUtils.commonDeviceId, TestUtils.map("location", "gps"), 3);
        ModuleAPMTests.validateNetworkRequest(4, 7, "test_trace", 1111, 400, 2000, 1111);
        TestUtils.validateRequest(TestUtils.commonDeviceId, TestUtils.map("attribution_data", "test_data"), 5);
        TestUtils.validateRequest(TestUtils.commonDeviceId, TestUtils.map("key", "value"), 6);

        Assert.assertEquals(7, TestUtils.getCurrentRQ().length);

        immediateFlow_allFeatures();

        Assert.assertEquals(0, countlyStore.getEventQueueSize());

        feedbackFlow_allFeatures();
        Assert.assertEquals(0, countlyStore.getEventQueueSize());

        validateEventInRQ("[CLY]_star_rating", TestUtils.map("platform", "android", "app_version", Countly.DEFAULT_APP_VERSION, "rating", "5", "widget_id", "test", "contactMe", true, "email", "test", "comment", "test"), 7, 8, 0, 2);
        validateEventInRQ("[CLY]_nps", TestUtils.map("app_version", Countly.DEFAULT_APP_VERSION, "widget_id", "test", "closed", "1", "platform", "android"), 7, 8, 1, 2);

        Assert.assertEquals(8, TestUtils.getCurrentRQ().length);

        validateCounts(counts, 1, 1, 1, 2, 1);
    }

    /**
     * Tests that crash reporting is properly disabled when configured.
     * Verifies that:
     * 1. Crash reports are not sent
     * 2. Other features continue to work normally
     * 3. Request counts and order are maintained correctly
     */
    @Test
    public void crashReportingDisabled_allFeatures() throws JSONException, InterruptedException {
        ServerConfigBuilder sc = new ServerConfigBuilder();
        sc.crashReporting(false);
        int[] counts = setupTest_allFeatures(sc.buildJson());

        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);
        Assert.assertEquals(0, TestUtils.getCountlyStore().getEventQueueSize());

        flow_allFeatures();

        ModuleSessionsTests.validateSessionBeginRequest(0, TestUtils.commonDeviceId);
        validateEventInRQ("[CLY]_orientation", TestUtils.map("mode", "portrait"), 1, 7, 0, 3);
        validateEventInRQ("test_event", TestUtils.map(), 1, 7, 1, 3);
        validateEventInRQ("[CLY]_view", TestUtils.map("name", "test_view", "segment", "Android", "visit", "1", "start", "1"), 1, 7, 2, 3);
        ModuleUserProfileTests.validateUserProfileRequest(2, 7, TestUtils.map(), TestUtils.map("test_property", "test_value"));
        TestUtils.validateRequest(TestUtils.commonDeviceId, TestUtils.map("location", "gps"), 3);
        ModuleAPMTests.validateNetworkRequest(4, 7, "test_trace", 1111, 400, 2000, 1111);
        TestUtils.validateRequest(TestUtils.commonDeviceId, TestUtils.map("attribution_data", "test_data"), 5);
        TestUtils.validateRequest(TestUtils.commonDeviceId, TestUtils.map("key", "value"), 6);

        Assert.assertEquals(7, TestUtils.getCurrentRQ().length);

        immediateFlow_allFeatures();

        Assert.assertEquals(0, countlyStore.getEventQueueSize());

        feedbackFlow_allFeatures();
        Assert.assertEquals(0, countlyStore.getEventQueueSize());

        validateEventInRQ("[CLY]_star_rating", TestUtils.map("platform", "android", "app_version", Countly.DEFAULT_APP_VERSION, "rating", "5", "widget_id", "test", "contactMe", true, "email", "test", "comment", "test"), 7, 8, 0, 2);
        validateEventInRQ("[CLY]_nps", TestUtils.map("app_version", Countly.DEFAULT_APP_VERSION, "widget_id", "test", "closed", "1", "platform", "android"), 7, 8, 1, 2);

        Assert.assertEquals(8, TestUtils.getCurrentRQ().length);

        validateCounts(counts, 1, 1, 1, 2, 1);
    }

    /**
     * Tests that view tracking is properly disabled when configured.
     * Verifies that:
     * 1. View events are not sent
     * 2. Other features continue to work normally
     * 3. Request counts and order are maintained correctly
     */
    @Test
    public void viewTrackingDisabled_allFeatures() throws JSONException, InterruptedException {
        ServerConfigBuilder sc = new ServerConfigBuilder();
        sc.viewTracking(false);
        int[] counts = setupTest_allFeatures(sc.buildJson());

        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);
        Assert.assertEquals(0, TestUtils.getCountlyStore().getEventQueueSize());

        String stackTrace = flow_allFeatures();

        ModuleSessionsTests.validateSessionBeginRequest(0, TestUtils.commonDeviceId);
        ModuleCrashTests.validateCrash(stackTrace, "", false, false, 8, 1, TestUtils.map(), 0, TestUtils.map(), new ArrayList<>());
        validateEventInRQ("[CLY]_orientation", TestUtils.map("mode", "portrait"), 2, 8, 0, 2);
        validateEventInRQ("test_event", TestUtils.map(), 2, 8, 1, 2);
        ModuleUserProfileTests.validateUserProfileRequest(3, 8, TestUtils.map(), TestUtils.map("test_property", "test_value"));
        TestUtils.validateRequest(TestUtils.commonDeviceId, TestUtils.map("location", "gps"), 4);
        ModuleAPMTests.validateNetworkRequest(5, 8, "test_trace", 1111, 400, 2000, 1111);
        TestUtils.validateRequest(TestUtils.commonDeviceId, TestUtils.map("attribution_data", "test_data"), 6);
        TestUtils.validateRequest(TestUtils.commonDeviceId, TestUtils.map("key", "value"), 7);

        Assert.assertEquals(8, TestUtils.getCurrentRQ().length);

        immediateFlow_allFeatures();

        Assert.assertEquals(0, countlyStore.getEventQueueSize());

        feedbackFlow_allFeatures();
        Assert.assertEquals(0, countlyStore.getEventQueueSize());

        validateEventInRQ("[CLY]_star_rating", TestUtils.map("platform", "android", "app_version", Countly.DEFAULT_APP_VERSION, "rating", "5", "widget_id", "test", "contactMe", true, "email", "test", "comment", "test"), 8, 9, 0, 2);
        validateEventInRQ("[CLY]_nps", TestUtils.map("app_version", Countly.DEFAULT_APP_VERSION, "widget_id", "test", "closed", "1", "platform", "android"), 8, 9, 1, 2);

        Assert.assertEquals(9, TestUtils.getCurrentRQ().length);

        validateCounts(counts, 1, 1, 1, 2, 1);
    }

    /**
     * Tests that custom event tracking is properly disabled when configured.
     * Verifies that:
     * 1. Custom events are not sent
     * 2. Other features continue to work normally
     * 3. Request counts and order are maintained correctly
     */
    @Test
    public void customEventTrackingDisabled_allFeatures() throws JSONException, InterruptedException {
        ServerConfigBuilder sc = new ServerConfigBuilder();
        sc.customEventTracking(false);
        int[] counts = setupTest_allFeatures(sc.buildJson());

        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);
        Assert.assertEquals(0, TestUtils.getCountlyStore().getEventQueueSize());

        String stackTrace = flow_allFeatures();

        ModuleSessionsTests.validateSessionBeginRequest(0, TestUtils.commonDeviceId);
        ModuleCrashTests.validateCrash(stackTrace, "", false, false, 8, 1, TestUtils.map(), 0, TestUtils.map(), new ArrayList<>());
        validateEventInRQ("[CLY]_orientation", TestUtils.map("mode", "portrait"), 2, 8, 0, 2);
        validateEventInRQ("[CLY]_view", TestUtils.map("name", "test_view", "segment", "Android", "visit", "1", "start", "1"), 2, 8, 1, 2);
        ModuleUserProfileTests.validateUserProfileRequest(3, 8, TestUtils.map(), TestUtils.map("test_property", "test_value"));
        TestUtils.validateRequest(TestUtils.commonDeviceId, TestUtils.map("location", "gps"), 4);
        ModuleAPMTests.validateNetworkRequest(5, 8, "test_trace", 1111, 400, 2000, 1111);
        TestUtils.validateRequest(TestUtils.commonDeviceId, TestUtils.map("attribution_data", "test_data"), 6);
        TestUtils.validateRequest(TestUtils.commonDeviceId, TestUtils.map("key", "value"), 7);

        Assert.assertEquals(8, TestUtils.getCurrentRQ().length);

        immediateFlow_allFeatures();

        Assert.assertEquals(0, countlyStore.getEventQueueSize());

        feedbackFlow_allFeatures();
        Assert.assertEquals(0, countlyStore.getEventQueueSize());

        validateEventInRQ("[CLY]_star_rating", TestUtils.map("platform", "android", "app_version", Countly.DEFAULT_APP_VERSION, "rating", "5", "widget_id", "test", "contactMe", true, "email", "test", "comment", "test"), 8, 9, 0, 2);
        validateEventInRQ("[CLY]_nps", TestUtils.map("app_version", Countly.DEFAULT_APP_VERSION, "widget_id", "test", "closed", "1", "platform", "android"), 8, 9, 1, 2);

        Assert.assertEquals(9, TestUtils.getCurrentRQ().length);

        validateCounts(counts, 1, 1, 1, 2, 1);
    }

    /**
     * Tests that networking is properly disabled when configured.
     * Verifies that no network requests are generated when networking is disabled.
     */
    @Test
    public void networkingDisabled_allFeatures() throws JSONException, InterruptedException {
        base_allFeatures((sc) -> sc.networking(false), 0, 0, 0, 0, 1);
    }

    /**
     * Tests that location tracking is properly disabled when configured.
     * Verifies that:
     * 1. Location updates are not sent
     * 2. Location is properly cleared
     * 3. Other features continue to work normally
     */
    @Test
    public void locationTrackingDisabled_allFeatures() throws JSONException, InterruptedException {
        ServerConfigBuilder sc = new ServerConfigBuilder();
        sc.locationTracking(false);
        int[] counts = setupTest_allFeatures(sc.buildJson());

        Assert.assertEquals(1, TestUtils.getCurrentRQ().length);
        Assert.assertEquals(0, countlyStore.getEventQueueSize());
        TestUtils.validateRequest(TestUtils.commonDeviceId, TestUtils.map("location", ""), 0);

        String stackTrace = flow_allFeatures();

        ModuleSessionsTests.validateSessionBeginRequest(1, TestUtils.commonDeviceId);
        ModuleCrashTests.validateCrash(stackTrace, "", false, false, 8, 2, TestUtils.map(), 0, TestUtils.map(), new ArrayList<>());
        validateEventInRQ("[CLY]_orientation", TestUtils.map("mode", "portrait"), 3, 8, 0, 3);
        validateEventInRQ("test_event", TestUtils.map(), 3, 8, 1, 3);
        validateEventInRQ("[CLY]_view", TestUtils.map("name", "test_view", "segment", "Android", "visit", "1", "start", "1"), 3, 8, 2, 3);
        ModuleUserProfileTests.validateUserProfileRequest(4, 8, TestUtils.map(), TestUtils.map("test_property", "test_value"));
        ModuleAPMTests.validateNetworkRequest(5, 8, "test_trace", 1111, 400, 2000, 1111);
        TestUtils.validateRequest(TestUtils.commonDeviceId, TestUtils.map("attribution_data", "test_data"), 6);
        TestUtils.validateRequest(TestUtils.commonDeviceId, TestUtils.map("key", "value"), 7);

        Assert.assertEquals(8, TestUtils.getCurrentRQ().length);

        immediateFlow_allFeatures();
        Assert.assertEquals(0, countlyStore.getEventQueueSize());

        feedbackFlow_allFeatures();
        Assert.assertEquals(0, countlyStore.getEventQueueSize());

        validateEventInRQ("[CLY]_star_rating", TestUtils.map("platform", "android", "app_version", Countly.DEFAULT_APP_VERSION, "rating", "5", "widget_id", "test", "contactMe", true, "email", "test", "comment", "test"), 8, 9, 0, 2);
        validateEventInRQ("[CLY]_nps", TestUtils.map("app_version", Countly.DEFAULT_APP_VERSION, "widget_id", "test", "closed", "1", "platform", "android"), 8, 9, 1, 2);

        Assert.assertEquals(9, TestUtils.getCurrentRQ().length);

        validateCounts(counts, 1, 1, 1, 2, 1);
    }

    /**
     * Tests that tracking is properly disabled when configured.
     * Verifies that no tracking-related requests are generated when tracking is disabled.
     */
    @Test
    public void trackingDisabled_allFeatures() throws JSONException, InterruptedException {
        ServerConfigBuilder sc = new ServerConfigBuilder();
        sc.tracking(false);
        int[] counts = setupTest_allFeatures(sc.buildJson());

        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);
        Assert.assertEquals(0, countlyStore.getEventQueueSize());

        flow_allFeatures();
        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);

        immediateFlow_allFeatures();
        Assert.assertEquals(0, countlyStore.getEventQueueSize());
        feedbackFlow_allFeatures();
        Assert.assertEquals(0, countlyStore.getEventQueueSize());

        Thread.sleep(1000); // wait for immediate requests to be processed
        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);

        Assert.assertEquals(1, counts[0]); // health check request
        Assert.assertEquals(1, counts[1]);
        Assert.assertEquals(1, counts[2]);
        Assert.assertEquals(2, counts[3]);
        Assert.assertEquals(1, counts[4]); // server config request
    }

    /**
     * Tests that content zone refresh is properly disabled when configured.
     * Verifies that content zone refresh requests are not generated.
     */
    @Test
    public void refreshContentZoneDisabled_allFeatures() throws JSONException, InterruptedException {
        base_allFeatures((sc) -> sc.refreshContentZone(false), 1, 1, 1, 1, 1);
    }

    /**
     * Tests that content zone is properly enabled when configured.
     * Verifies that content zone requests are generated with the correct frequency.
     */
    @Test
    public void contentZoneEnabled_allFeatures() throws JSONException, InterruptedException {
        base_allFeatures((sc) -> sc.contentZone(true), 1, 1, 1, 4, 1);
    }

    // ================ Configuration Persistence Tests ================

    /**
     * Test that downloaded configuration persists across multiple initializations
     */
    @Test
    public void configurationPersistence_AcrossMultipleInits() {
        // Initial state should be fresh
        Assert.assertNull(countlyStore.getServerConfig());

        // First init fails receiving config, should return defaults
        initAndValidateConfigParsingResult(null, false);

        // Second init succeeds receiving config
        countly = initAndValidateConfigParsingResult("{'v':1,'t':2,'c':{'tracking':false,'networking':false}}", true);
        Assert.assertFalse(countly.moduleConfiguration.getNetworkingEnabled());
        Assert.assertFalse(countly.moduleConfiguration.getTrackingEnabled());

        // Third init lacks connection but should have previously saved values
        CountlyConfig config = TestUtils.createIRGeneratorConfig(null);
        config.enableServerConfiguration();
        countly = new Countly().init(config);
        Assert.assertFalse(countly.moduleConfiguration.getNetworkingEnabled());
        Assert.assertFalse(countly.moduleConfiguration.getTrackingEnabled());

        // Fourth init updates config values
        countly = initAndValidateConfigParsingResult("{'v':1,'t':2,'c':{'tracking':true,'networking':false}}", true);
        Assert.assertFalse(countly.moduleConfiguration.getNetworkingEnabled());
        Assert.assertTrue(countly.moduleConfiguration.getTrackingEnabled());
    }

    // ================ Tracking Configuration Tests ================

    /**
     * Test that nothing is written to queues when tracking is disabled
     */
    @Test
    public void trackingDisabled_NoQueueWrites() throws JSONException {
        Assert.assertEquals("", countlyStore.getRequestQueueRaw());
        Assert.assertEquals(0, countlyStore.getEvents().length);

        countlyStore.setServerConfig(createStorageConfig(false, false, false));
        CountlyConfig config = TestUtils.createIRGeneratorConfig(null);
        config.enableServerConfiguration();
        countly = new Countly().init(config);

        Assert.assertFalse(countly.moduleConfiguration.getNetworkingEnabled());
        Assert.assertFalse(countly.moduleConfiguration.getTrackingEnabled());
        Assert.assertFalse(countly.moduleConfiguration.getCrashReportingEnabled());

        // Try various operations that should be blocked
        countly.events().recordEvent("d");
        countly.events().recordEvent("1");
        countly.crashes().recordHandledException(new Exception());
        countly.requestQueue().addDirectRequest(new HashMap<>());
        countly.requestQueue().attemptToSendStoredRequests();

        Assert.assertEquals("", countlyStore.getRequestQueueRaw());
        Assert.assertEquals(0, countlyStore.getEvents().length);
    }

    // ================ Crash Reporting Tests ================

    /**
     * Test unhandled crash reporting when crashes are disabled
     */
    @Test
    public void crashReporting_UnhandledCrashesWhenDisabled() throws JSONException {
        AtomicInteger callCount = new AtomicInteger(0);
        RuntimeException unhandledException = new RuntimeException("Simulated unhandled exception");

        Thread threadThrows = new Thread(() -> {
            throw unhandledException;
        });

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Assert.assertEquals(unhandledException, throwable);
            Assert.assertEquals(threadThrows, thread);
            callCount.incrementAndGet();
        });

        TestUtils.getCountlyStore().setServerConfig(createStorageConfig(true, true, false));
        CountlyConfig config = TestUtils.createBaseConfig();
        config.enableServerConfiguration().setEventQueueSizeToSend(2);
        config.crashes.enableCrashReporting();
        countly = new Countly().init(config);

        Assert.assertTrue(countly.moduleConfiguration.getNetworkingEnabled());
        Assert.assertTrue(countly.moduleConfiguration.getTrackingEnabled());
        Assert.assertFalse(countly.moduleConfiguration.getCrashReportingEnabled());

        threadThrows.start();
        try {
            threadThrows.join();
        } catch (InterruptedException ignored) {
        }

        countly.events().recordEvent("d");
        countly.events().recordEvent("1");
        Assert.assertEquals(1, callCount.get());

        countly.crashes().recordHandledException(new Exception());
        countly.requestQueue().addDirectRequest(new HashMap<>());
        countly.requestQueue().attemptToSendStoredRequests();

        Assert.assertEquals(1, TestUtils.getCurrentRQ("Simulated unhandled exception").length);
        Assert.assertNull(TestUtils.getCurrentRQ("Simulated unhandled exception")[0]);
    }

    // ================ Invalid Configuration Tests ================

    /**
     * Test rejection of various invalid configuration responses
     */
    @Test
    public void invalidConfigResponses_AreRejected() {
        Assert.assertNull(countlyStore.getServerConfig());

        // Test various invalid configurations
        initAndValidateConfigParsingResult(null, false);
        initAndValidateConfigParsingResult("{}", false);
        initAndValidateConfigParsingResult("{'t':2,'c':{'aa':'bb'}}", false);
        initAndValidateConfigParsingResult("{'v':1,'c':{'aa':'bb'}}", false);
        initAndValidateConfigParsingResult("{'v':1,'t':2}", false);
        initAndValidateConfigParsingResult("{'v':1,'t':2,'c':123}", false);
        initAndValidateConfigParsingResult("{'v':1,'t':2,'c':false}", false);
        initAndValidateConfigParsingResult("{'v':1,'t':2,'c':'fdf'}", false);
    }

    // ================ Configuration Parameter Tests ================

    /**
     * Test that all configuration parameters are properly defined
     */
    @Test
    public void configurationParameterCount() {
        int configParameterCount = 41; // plus config, timestamp and version parameters, UPDATE: list filters, user property cache limit, and journey trigger events
        int count = 0;
        for (Field field : ModuleConfiguration.class.getDeclaredFields()) {
            if (field.getName().startsWith("keyR")) {
                count++;
            }
        }
        Assert.assertEquals(configParameterCount, count);
    }

    // ================ Scenario Tests ================

    /**
     * Test a complete scenario where custom events are enabled first but disabled later
     */
    @Test
    public void scenario_customEventTrackingDisabled() throws JSONException {
        // Initial setup with all features enabled
        ServerConfigBuilder serverConfigBuilder = new ServerConfigBuilder()
            .defaults();

        Countly countly = new Countly().init(TestUtils.createIRGeneratorConfig(createIRGForSpecificResponse(serverConfigBuilder.build())));

        // Verify initial state
        Assert.assertTrue(countly.moduleConfiguration.getCustomEventTrackingEnabled());

        // Record some events to verify tracking
        countly.events().recordEvent("test_event");
        Assert.assertEquals(1, countlyStore.getEvents().length);

        // Record some views to verify view tracking is not blocked by custom event tracking
        countly.views().startAutoStoppedView("test_view");

        Assert.assertEquals(2, countlyStore.getEvents().length); // 1 event + 1 auto stopped view start

        // Update configuration to disable custom event tracking
        serverConfigBuilder.customEventTracking(false);
        countly = new Countly().init(TestUtils.createIRGeneratorConfig(createIRGForSpecificResponse(serverConfigBuilder.build())));

        // Verify custom event tracking is disabled
        Assert.assertFalse(countly.moduleConfiguration.getCustomEventTrackingEnabled());

        // Try to record events - should be blocked
        countly.events().recordEvent("blocked_event");
        Assert.assertEquals(2, countlyStore.getEvents().length); // 1 event + 1 auto stopped view start no new events
    }

    /**
     * Test that view tracking is properly disabled when configured
     * View tracking is independent of custom event tracking
     */
    @Test
    public void scenario_viewTrackingDisabled() throws JSONException {
        // Initial setup with all features enabled
        ServerConfigBuilder serverConfigBuilder = new ServerConfigBuilder()
            .defaults();

        Countly countly = new Countly().init(TestUtils.createIRGeneratorConfig(createIRGForSpecificResponse(serverConfigBuilder.build())));

        // Verify initial state
        Assert.assertTrue(countly.moduleConfiguration.getCustomEventTrackingEnabled());

        // Record some events to verify tracking
        countly.events().recordEvent("test_event");
        Assert.assertEquals(1, countlyStore.getEvents().length);

        // Record some views to verify view tracking is not blocked by custom event tracking
        countly.views().startAutoStoppedView("test_view");

        Assert.assertEquals(2, countlyStore.getEvents().length); // 1 event + 1 auto stopped view start

        // Update configuration to disable custom event tracking
        serverConfigBuilder.viewTracking(false);
        countly = new Countly().init(TestUtils.createIRGeneratorConfig(createIRGForSpecificResponse(serverConfigBuilder.build())));

        // Verify custom event tracking is disabled
        Assert.assertFalse(countly.moduleConfiguration.getViewTrackingEnabled());

        // Try to record events - should be blocked
        countly.views().startAutoStoppedView("test_view_1");
        Assert.assertEquals(2, countlyStore.getEvents().length); // 1 event + 1 auto stopped view start but no views
    }

    /**
     * Test that tracking is properly disabled when configured
     * When tracking is disabled, no new requests should be generated
     */
    @Test
    public void scenario_trackingDisabled() throws JSONException, InterruptedException {
        // Initial setup with all features enabled
        ServerConfigBuilder serverConfigBuilder = new ServerConfigBuilder()
            .defaults();

        Countly countly = new Countly().init(TestUtils.createIRGeneratorConfig(createIRGForSpecificResponse(serverConfigBuilder.build())));
        countly.onStartInternal(null);
        // Verify initial state
        Assert.assertTrue(countly.moduleConfiguration.getTrackingEnabled());
        Thread.sleep(1000);

        Assert.assertEquals(1, TestUtils.getCurrentRQ().length); // begin session request

        serverConfigBuilder.tracking(false);
        countly = new Countly().init(TestUtils.createIRGeneratorConfig(createIRGForSpecificResponse(serverConfigBuilder.build())));
        countly.onStartInternal(null);
        Thread.sleep(1000);
        Assert.assertEquals(1, TestUtils.getCurrentRQ().length); // assert that no new request is added
    }

    /**
     * Test that networking is properly disabled when configured
     * When networking is disabled, request queue operations are skipped
     */
    @Test
    public void scenario_networkingDisabled() throws JSONException, InterruptedException {
        // Initial setup with all features enabled
        ServerConfigBuilder serverConfigBuilder = new ServerConfigBuilder()
            .defaults();

        Countly.sharedInstance().init(TestUtils.createIRGeneratorConfig(createIRGForSpecificResponse(serverConfigBuilder.build())));
        Countly.sharedInstance().onStartInternal(null);
        ModuleLog mockLog = Mockito.mock(ModuleLog.class);

        Countly.sharedInstance().L = mockLog;
        // Verify initial state
        Assert.assertTrue(Countly.sharedInstance().moduleConfiguration.getNetworkingEnabled());
        Thread.sleep(1000);

        Assert.assertEquals(1, TestUtils.getCurrentRQ().length); // begin session request

        serverConfigBuilder.networking(false);
        Countly.sharedInstance().onStopInternal();
        Countly.sharedInstance().sdkIsInitialised = false;
        Mockito.verify(mockLog, Mockito.never()).w("[ConnectionProcessor] run, Networking config is disabled, request queue skipped");

        Assert.assertEquals(3, TestUtils.getCurrentRQ().length); //first begin + orientation + first end session

        Countly.sharedInstance().init(TestUtils.createIRGeneratorConfig(createIRGForSpecificResponse(serverConfigBuilder.build())));
        Countly.sharedInstance().onStartInternal(null);
        Thread.sleep(1000);
        Assert.assertFalse(Countly.sharedInstance().moduleConfiguration.getNetworkingEnabled());

        Assert.assertEquals(4, TestUtils.getCurrentRQ().length); //first begin + orientation + first end session + second begin
        Countly.sharedInstance().requestQueue().attemptToSendStoredRequests();
        Thread.sleep(1000);

        Mockito.verify(mockLog, Mockito.atLeastOnce()).w("[ConnectionProcessor] run, Networking config is disabled, request queue skipped");
    }

    /**
     * Test that session tracking is properly disabled when configured
     * When session tracking is disabled, no new session requests should be generated
     */
    @Test
    public void scenario_sessionTrackingDisabled() throws JSONException, InterruptedException {
        // Initial setup with all features enabled
        ServerConfigBuilder serverConfigBuilder = new ServerConfigBuilder()
            .defaults();

        Countly.sharedInstance().init(TestUtils.createIRGeneratorConfig(createIRGForSpecificResponse(serverConfigBuilder.build())));
        Countly.sharedInstance().onStartInternal(null);

        // Verify initial state
        Assert.assertTrue(Countly.sharedInstance().moduleConfiguration.getSessionTrackingEnabled());
        Thread.sleep(1000);

        Assert.assertEquals(1, TestUtils.getCurrentRQ().length); // begin session request
        ModuleSessionsTests.validateSessionBeginRequest(0, TestUtils.commonDeviceId);

        serverConfigBuilder.sessionTracking(false);
        Countly.sharedInstance().onStopInternal();
        Countly.sharedInstance().sdkIsInitialised = false;

        Assert.assertEquals(3, TestUtils.getCurrentRQ().length); //first begin + orientation + first end session

        Countly.sharedInstance().init(TestUtils.createIRGeneratorConfig(createIRGForSpecificResponse(serverConfigBuilder.build())));
        Countly.sharedInstance().onStartInternal(null);
        Thread.sleep(1000);
        Assert.assertFalse(Countly.sharedInstance().moduleConfiguration.getSessionTrackingEnabled());

        Assert.assertEquals(3, TestUtils.getCurrentRQ().length); // same error count because no session request generated
    }

    /**
     * Test that session tracking is properly disabled when configured with manual session control
     * Manual session control allows explicit session management
     */
    @Test
    public void scenario_sessionTrackingDisabled_manualSessions() throws JSONException, InterruptedException {
        // Initial setup with all features enabled
        ServerConfigBuilder serverConfigBuilder = new ServerConfigBuilder()
            .defaults();

        Countly.sharedInstance().init(TestUtils.createIRGeneratorConfig(createIRGForSpecificResponse(serverConfigBuilder.build()))
            .enableManualSessionControl());
        Countly.sharedInstance().onStartInternal(null);

        // Verify initial state
        Assert.assertTrue(Countly.sharedInstance().moduleConfiguration.getSessionTrackingEnabled());
        serverConfigBuilder.validateAgainst(Countly.sharedInstance());
        Assert.assertEquals(0, TestUtils.getCurrentRQ().length); // no session request

        Countly.sharedInstance().sessions().beginSession();
        Assert.assertEquals(1, TestUtils.getCurrentRQ().length); // begin session request
        ModuleSessionsTests.validateSessionBeginRequest(0, TestUtils.commonDeviceId);

        Thread.sleep(1000);
        Countly.sharedInstance().sessions().endSession();
        Assert.assertEquals(3, TestUtils.getCurrentRQ().length); // begin session request + orientation + end session request

        ModuleSessionsTests.validateSessionEndRequest(2, 1, TestUtils.commonDeviceId);

        serverConfigBuilder.sessionTracking(false);
        Countly.sharedInstance().onStopInternal();
        Countly.sharedInstance().sdkIsInitialised = false;

        Assert.assertEquals(3, TestUtils.getCurrentRQ().length); // same request count

        Countly.sharedInstance().init(TestUtils.createIRGeneratorConfig(createIRGForSpecificResponse(serverConfigBuilder.build()))
            .enableManualSessionControl());
        Countly.sharedInstance().onStartInternal(null);
        Thread.sleep(1000);
        Assert.assertFalse(Countly.sharedInstance().moduleConfiguration.getSessionTrackingEnabled());
        serverConfigBuilder.validateAgainst(Countly.sharedInstance());

        Assert.assertFalse(Countly.sharedInstance().moduleSessions.sessionIsRunning());
        Countly.sharedInstance().sessions().beginSession();
        Assert.assertFalse(Countly.sharedInstance().moduleSessions.sessionIsRunning());

        Thread.sleep(1000);
        Countly.sharedInstance().sessions().updateSession();

        Thread.sleep(1000);
        Assert.assertFalse(Countly.sharedInstance().moduleSessions.sessionIsRunning());
        Countly.sharedInstance().sessions().endSession();
        Assert.assertFalse(Countly.sharedInstance().moduleSessions.sessionIsRunning());

        Assert.assertEquals(3, TestUtils.getCurrentRQ().length); // same request count
    }

    /**
     * Test that location tracking is properly disabled when configured
     * Location updates are blocked when tracking is disabled
     * Location updates include city, country, GPS coordinates and IP
     */
    @Test
    public void scenario_locationTrackingDisabled() throws JSONException {
        // Initial setup with all features enabled
        ServerConfigBuilder serverConfigBuilder = new ServerConfigBuilder()
            .defaults();

        Countly.sharedInstance().init(TestUtils.createIRGeneratorConfig(createIRGForSpecificResponse(serverConfigBuilder.build())));
        Countly.sharedInstance().onStartInternal(null);
        // Verify initial state
        Assert.assertTrue(Countly.sharedInstance().moduleConfiguration.getLocationTrackingEnabled());
        serverConfigBuilder.validateAgainst(Countly.sharedInstance());
        Assert.assertEquals(1, TestUtils.getCurrentRQ().length); // session request

        Countly.sharedInstance().location().setLocation("country", "city", "gps", "ip");
        Assert.assertEquals(2, TestUtils.getCurrentRQ().length); // location request
        Assert.assertTrue(TestUtils.getCurrentRQ()[1].containsKey("location"));

        serverConfigBuilder.locationTracking(false);
        Countly.sharedInstance().onStopInternal();
        Countly.sharedInstance().sdkIsInitialised = false; // reset sdk

        Countly.sharedInstance().init(TestUtils.createIRGeneratorConfig(createIRGForSpecificResponse(serverConfigBuilder.build())));
        Countly.sharedInstance().onStartInternal(null);
        Assert.assertFalse(Countly.sharedInstance().moduleConfiguration.getLocationTrackingEnabled());
        serverConfigBuilder.validateAgainst(Countly.sharedInstance());

        Assert.assertEquals(6, TestUtils.getCurrentRQ().length);

        Countly.sharedInstance().location().setLocation("country1", "city1", "gps1", "ip1");
        Countly.sharedInstance().location().disableLocation();
        Countly.sharedInstance().location().setLocation("country2", "city2", "gps2", "ip2");

        TestUtils.validateRequest(TestUtils.commonDeviceId, TestUtils.map("location", ""), 4); // this will be from server config
        TestUtils.validateRequest(TestUtils.commonDeviceId, TestUtils.map("location", ""), 6); // this is from disable location

        // first begin session  + location request + orientation + first end session + location reset + second begin session
        Assert.assertEquals(7, TestUtils.getCurrentRQ().length); // same request count
    }

    /**
     * Test that consent requirement is properly handled when enabled/disabled
     * When consent is required, operations are blocked until consent is given
     * Attribution is used as a test case since it's not directly affected by server config
     * Need to verify both the request queue and consent state
     */
    @Test
    public void scenario_consentRequiredDisabled() throws JSONException {
        // Initial setup with all features enabled
        ServerConfigBuilder serverConfigBuilder = new ServerConfigBuilder()
            .defaults();

        Countly.sharedInstance().init(TestUtils.createIRGeneratorConfig(createIRGForSpecificResponse(serverConfigBuilder.build())));
        // Verify initial state
        Assert.assertFalse(Countly.sharedInstance().config_.shouldRequireConsent);
        serverConfigBuilder.validateAgainst(Countly.sharedInstance());

        // use a feature that is not affected directly from the server configuration
        Countly.sharedInstance().attribution().recordDirectAttribution("_special_test", "_special_test");
        Assert.assertEquals(1, TestUtils.getCurrentRQ().length); // attribution request
        TestUtils.validateRequest(TestUtils.commonDeviceId, TestUtils.map("attribution_data", "_special_test"), 0);

        serverConfigBuilder.consentRequired(true);
        Countly.sharedInstance().sdkIsInitialised = false;
        Countly.sharedInstance().init(TestUtils.createIRGeneratorConfig(createIRGForSpecificResponse(serverConfigBuilder.build())));
        Assert.assertTrue(Countly.sharedInstance().config_.shouldRequireConsent);
        serverConfigBuilder.validateAgainst(Countly.sharedInstance());

        Assert.assertEquals(3, TestUtils.getCurrentRQ().length); // first attribution request, empty consent, empty location

        Countly.sharedInstance().attribution().recordDirectAttribution("_special_test", "_special_test");
        Assert.assertEquals(3, TestUtils.getCurrentRQ().length); // changes nothing because no consent for attribution
        ModuleConsentTests.validateConsentRequest(TestUtils.commonDeviceId, 1, new boolean[] { false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false });
        TestUtils.validateRequest(TestUtils.commonDeviceId, TestUtils.map("location", ""), 2);
    }

    /**
     * Tests that the event queue size limit is properly enforced.
     * Verifies that:
     * 1. Events are queued until the size limit is reached
     * 2. When limit is reached, events sent in a batch
     * 3. New events queued after the batch sent
     * 4. Event order maintained in the queue
     */
    @Test
    public void eventQueueSize() throws JSONException {
        CountlyConfig countlyConfig = TestUtils.createBaseConfig().setLoggingEnabled(false).enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(new ServerConfigBuilder().eventQueueSize(3).build());
        Countly.sharedInstance().init(countlyConfig);

        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);
        Assert.assertEquals(0, TestUtils.getCountlyStore().getEventQueueSize());

        Countly.sharedInstance().events().recordEvent("test_event");
        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);
        Assert.assertEquals(1, TestUtils.getCountlyStore().getEventQueueSize());

        Countly.sharedInstance().events().recordEvent("test_event_1");
        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);
        Assert.assertEquals(2, TestUtils.getCountlyStore().getEventQueueSize());

        Countly.sharedInstance().events().recordEvent("test_event_2");
        Assert.assertEquals(1, TestUtils.getCurrentRQ().length);
        Assert.assertEquals(0, TestUtils.getCountlyStore().getEventQueueSize());

        Countly.sharedInstance().events().recordEvent("test_event_3");
        Assert.assertEquals(1, TestUtils.getCurrentRQ().length);
        Assert.assertEquals(1, TestUtils.getCountlyStore().getEventQueueSize());

        validateEventInRQ("test_event", TestUtils.map(), 0, 1, 0, 3);
        validateEventInRQ("test_event_1", TestUtils.map(), 0, 1, 1, 3);
        validateEventInRQ("test_event_2", TestUtils.map(), 0, 1, 2, 3);
    }

    /**
     * Tests that the request queue size limit is properly enforced.
     * Verifies that:
     * 1. Requests are queued until the size limit is reached
     * 2. When limit is reached, first item removed
     * 3. Different types of requests (sessions, attribution, location) are counted towards the limit
     */
    @Test
    public void requestQueueSize() throws JSONException {
        CountlyConfig countlyConfig = TestUtils.createBaseConfig().setLoggingEnabled(false).enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(new ServerConfigBuilder().requestQueueSize(3).build());
        Countly.sharedInstance().init(countlyConfig);

        Countly.sharedInstance().sessions().beginSession();
        ModuleSessionsTests.validateSessionBeginRequest(0, TestUtils.commonDeviceId);

        Countly.sharedInstance().attribution().recordDirectAttribution("_special_test", "_special_test");
        Assert.assertEquals(2, TestUtils.getCurrentRQ().length);

        Countly.sharedInstance().location().setLocation("country", "city", "gps", "ip");
        Assert.assertEquals(3, TestUtils.getCurrentRQ().length);

        Map<String, String> params = new ConcurrentHashMap<>();
        params.put("key", "value");
        Countly.sharedInstance().requestQueue().addDirectRequest(params);

        boolean failed = false;
        try {
            ModuleSessionsTests.validateSessionBeginRequest(0, TestUtils.commonDeviceId); // this will be not true anymore
            failed = true;
        } catch (Throwable e) {
            // do nothing
        }

        Assert.assertFalse(failed);
    }

    // ================ Helper Methods ================

    private void assertDefaultConfigValues(Countly countly) {
        Assert.assertTrue(countly.moduleConfiguration.getNetworkingEnabled());
        Assert.assertTrue(countly.moduleConfiguration.getTrackingEnabled());
        Assert.assertTrue(countly.moduleConfiguration.getCrashReportingEnabled());
    }

    private Countly initAndValidateConfigParsingResult(String targetResponse, boolean responseAccepted) {
        CountlyConfig config = TestUtils.createIRGeneratorConfig(createIRGForSpecificResponse(targetResponse));
        config.enableServerConfiguration();
        countly = new Countly().init(config);

        if (!responseAccepted) {
            Assert.assertNull(countlyStore.getServerConfig());
            assertDefaultConfigValues(countly);
        } else {
            Assert.assertNotNull(countlyStore.getServerConfig());
        }

        return countly;
    }

    private String createStorageConfig(boolean tracking, boolean networking, boolean crashes) throws JSONException {
        return new ServerConfigBuilder()
            .tracking(tracking)
            .networking(networking)
            .crashReporting(crashes)
            .build();
    }

    static ImmediateRequestGenerator createIRGForSpecificResponse(final String targetResponse) {
        return new ImmediateRequestGenerator() {
            @Override
            public ImmediateRequestI CreateImmediateRequestMaker() {
                return new ImmediateRequestI() {
                    @Override
                    public void doWork(String requestData, String customEndpoint, ConnectionProcessor cp, boolean requestShouldBeDelayed, boolean networkingIsEnabled, ImmediateRequestMaker.InternalImmediateRequestCallback callback, ModuleLog log) {
                        if (targetResponse == null) {
                            callback.callback(null);
                            return;
                        }

                        try {
                            callback.callback(new JSONObject(targetResponse));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                };
            }

            @Override public ImmediateRequestI CreatePreflightRequestMaker() {
                return null;
            }
        };
    }

    private void initServerConfigWithValues(BiConsumer<CountlyConfig, String> configSetter) throws JSONException, InterruptedException {
        ServerConfigBuilder builder = new ServerConfigBuilder()
            // Feature flags
            .tracking(false)
            .networking(false)
            .crashReporting(false)
            .viewTracking(false)
            .sessionTracking(false)
            .customEventTracking(false)
            .contentZone(true)
            .locationTracking(false)
            .refreshContentZone(false)

            // Intervals and sizes
            .serverConfigUpdateInterval(8)
            .requestQueueSize(2000)
            .eventQueueSize(200)
            .logging(true)
            .sessionUpdateInterval(120)
            .contentZoneInterval(60)
            .consentRequired(true)
            .dropOldRequestTime(1)
            .keyLengthLimit(89)
            .valueSizeLimit(43)
            .segmentationValuesLimit(25)
            .breadcrumbLimit(90)
            .traceLengthLimit(78)
            .traceLinesLimit(89)
            .userPropertyCacheLimit(67)

            // Filters
            .eventFilterList(new HashSet<>(), false)
            .userPropertyFilterList(new HashSet<>(), false)
            .segmentationFilterList(new HashSet<>(), false)
            .eventSegmentationFilterMap(new ConcurrentHashMap<>(), false)
            .journeyTriggerEvents(new HashSet<>());

        String serverConfig = builder.build();
        CountlyConfig countlyConfig = TestUtils.createBaseConfig().setLoggingEnabled(false);
        configSetter.accept(countlyConfig, serverConfig);

        countly = new Countly().init(countlyConfig);
        Thread.sleep(2000);

        builder.validateAgainst(countly);
    }

    private int[] setupTest_allFeatures(JSONObject serverConfig) {
        final int[] counts = { 0, 0, 0, 0, 0 };
        CountlyConfig countlyConfig = TestUtils.createBaseConfig().setLoggingEnabled(false).enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = new ImmediateRequestGenerator() {
            @Override public ImmediateRequestI CreateImmediateRequestMaker() {
                return new ImmediateRequestI() {
                    @Override public void doWork(String requestData, String customEndpoint, ConnectionProcessor cp, boolean requestShouldBeDelayed, boolean networkingIsEnabled, ImmediateRequestMaker.InternalImmediateRequestCallback callback, ModuleLog log) {
                        if (networkingIsEnabled) {
                            if (requestData.contains("&hc=")) {
                                counts[0] += 1;
                            } else if (requestData.contains("&method=feedback")) {
                                counts[1] += 1;
                            } else if (requestData.contains("&method=rc")) {
                                counts[2] += 1;
                            } else if (requestData.contains("&method=queue")) {
                                counts[3] += 1;
                            } else if (requestData.contains("&method=sc")) {
                                // do nothing
                            } else {
                                Assert.fail("Unexpected request data: " + requestData);
                            }
                        }

                        if (requestData.contains("&method=sc")) {
                            counts[4] += 1;
                            Assert.assertTrue(networkingIsEnabled);
                            callback.callback(serverConfig);
                        }
                    }
                };
            }

            @Override public ImmediateRequestI CreatePreflightRequestMaker() {
                return null;
            }
        };
        countlyConfig.metricProviderOverride = new MockedMetricProvider();
        Countly.sharedInstance().init(countlyConfig);
        Countly.sharedInstance().moduleContent.CONTENT_START_DELAY_MS = 0; // make it zero to catch content immediate request
        Countly.sharedInstance().moduleContent.REFRESH_CONTENT_ZONE_DELAY_MS = 0; // make it zero to catch content immediate request
        return counts;
    }

    private void validateCounts(int[] counts, int hc, int fc, int rc, int cc, int sc) {
        Assert.assertEquals(hc, counts[0]); // health check request
        Assert.assertEquals(fc, counts[1]); // feedback request
        Assert.assertEquals(rc, counts[2]); // remote config request
        Assert.assertEquals(cc, counts[3]); // content request
        Assert.assertEquals(sc, counts[4]); // server config request
    }

    private String flow_allFeatures() throws InterruptedException {
        Countly.sharedInstance().sessions().beginSession();

        Countly.sharedInstance().events().recordEvent("test_event");

        Countly.sharedInstance().views().startView("test_view");

        Exception e = new Exception("test_exception");
        Countly.sharedInstance().crashes().recordHandledException(e);

        Countly.sharedInstance().userProfile().setProperty("test_property", "test_value");
        Countly.sharedInstance().userProfile().save(); // events will be packed on this

        Countly.sharedInstance().location().setLocation("country", "city", "gps", "ip");

        Countly.sharedInstance().apm().recordNetworkTrace("test_trace", 400, 2000, 1111, 1111, 2222);

        Countly.sharedInstance().attribution().recordDirectAttribution("_special_test", "test_data");

        Map<String, String> params = new ConcurrentHashMap<>();
        params.put("key", "value");
        Countly.sharedInstance().requestQueue().addDirectRequest(params);

        return ModuleCrashTests.extractStackTrace(e);
    }

    private void immediateFlow_allFeatures() throws InterruptedException {
        Countly.sharedInstance().remoteConfig().downloadAllKeys(null); // will add one rc immediate request
        Countly.sharedInstance().feedback().getAvailableFeedbackWidgets(new ModuleFeedback.RetrieveFeedbackWidgets() {
            @Override public void onFinished(List<ModuleFeedback.CountlyFeedbackWidget> retrievedWidgets, String error) {

            }
        }); // will add one feedback immediate request
        Countly.sharedInstance().contents().enterContentZone(); // will add one content immediate request

        Thread.sleep(1000);

        Countly.sharedInstance().contents().refreshContentZone(); // will add one more content immediate request
    }

    private void feedbackFlow_allFeatures() {
        // could not mock ratings immediate, it requires a context
        Countly.sharedInstance().ratings().recordRatingWidgetWithID("test", 5, "test", "test", true);
        ModuleFeedback.CountlyFeedbackWidget widget = new ModuleFeedback.CountlyFeedbackWidget();
        widget.name = "test";
        widget.widgetId = "test";
        widget.type = ModuleFeedback.FeedbackWidgetType.nps;
        Countly.sharedInstance().feedback().reportFeedbackWidgetManually(widget, null, null);
    }

    private static void validateEventInRQ(String eventName, Map<String, Object> segmentation, int idx, int rqCount, int eventIdx, int eventCount) throws JSONException {
        ModuleEventsTests.validateEventInRQ(TestUtils.commonDeviceId, eventName, segmentation, 1, 0.0, 0.0, "_CLY_", "_CLY_", "_CLY_", "_CLY_", idx, rqCount, eventIdx, eventCount);
    }

    private void base_allFeatures(Consumer<ServerConfigBuilder> consumer, int hc, int fc, int rc, int cc, int scc) throws JSONException, InterruptedException {
        ServerConfigBuilder sc = new ServerConfigBuilder();
        consumer.accept(sc);
        int[] counts = setupTest_allFeatures(sc.buildJson());

        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);
        Assert.assertEquals(0, TestUtils.getCountlyStore().getEventQueueSize());

        String stackTrace = flow_allFeatures();

        ModuleSessionsTests.validateSessionBeginRequest(0, TestUtils.commonDeviceId);
        ModuleCrashTests.validateCrash(stackTrace, "", false, false, 8, 1, TestUtils.map(), 0, TestUtils.map(), new ArrayList<>());
        validateEventInRQ("[CLY]_orientation", TestUtils.map("mode", "portrait"), 2, 8, 0, 3);
        validateEventInRQ("test_event", TestUtils.map(), 2, 8, 1, 3);
        validateEventInRQ("[CLY]_view", TestUtils.map("name", "test_view", "segment", "Android", "visit", "1", "start", "1"), 2, 8, 2, 3);
        ModuleUserProfileTests.validateUserProfileRequest(3, 8, TestUtils.map(), TestUtils.map("test_property", "test_value"));
        TestUtils.validateRequest(TestUtils.commonDeviceId, TestUtils.map("location", "gps"), 4);
        ModuleAPMTests.validateNetworkRequest(5, 8, "test_trace", 1111, 400, 2000, 1111);
        TestUtils.validateRequest(TestUtils.commonDeviceId, TestUtils.map("attribution_data", "test_data"), 6);
        TestUtils.validateRequest(TestUtils.commonDeviceId, TestUtils.map("key", "value"), 7);

        Assert.assertEquals(8, TestUtils.getCurrentRQ().length);

        immediateFlow_allFeatures();

        Assert.assertEquals(0, countlyStore.getEventQueueSize());

        feedbackFlow_allFeatures();
        Assert.assertEquals(0, countlyStore.getEventQueueSize());

        validateEventInRQ("[CLY]_star_rating", TestUtils.map("platform", "android", "app_version", Countly.DEFAULT_APP_VERSION, "rating", "5", "widget_id", "test", "contactMe", true, "email", "test", "comment", "test"), 8, 9, 0, 2);
        validateEventInRQ("[CLY]_nps", TestUtils.map("app_version", Countly.DEFAULT_APP_VERSION, "widget_id", "test", "closed", "1", "platform", "android"), 8, 9, 1, 2);

        Assert.assertEquals(9, TestUtils.getCurrentRQ().length);

        validateCounts(counts, hc, fc, rc, cc, scc);
    }

    // ================ Event Filter Tests ================

    /**
     * Tests that event blacklist properly blocks filtered events.
     * Events in the blacklist should not be recorded.
     */
    @Test
    public void eventFilter_blacklist_blocksFilteredEvents() throws JSONException {
        Set<String> blacklist = new HashSet<>();
        blacklist.add("blocked_event");
        blacklist.add("another_blocked");

        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().eventFilterList(blacklist, false).build()
        );
        Countly.sharedInstance().init(countlyConfig);

        // Record a blocked event - should not be recorded
        Countly.sharedInstance().events().recordEvent("blocked_event");
        Assert.assertEquals(0, countlyStore.getEventQueueSize());

        // Record another blocked event - should not be recorded
        Countly.sharedInstance().events().recordEvent("another_blocked");
        Assert.assertEquals(0, countlyStore.getEventQueueSize());

        // Record an allowed event - should be recorded
        Countly.sharedInstance().events().recordEvent("allowed_event");
        Assert.assertEquals(1, countlyStore.getEventQueueSize());
        Assert.assertTrue(countlyStore.getEvents()[0].contains("allowed_event"));

        // Verify the filter state
        Assert.assertFalse(Countly.sharedInstance().moduleConfiguration.getEventFilterList().isWhitelist);
        Assert.assertTrue(Countly.sharedInstance().moduleConfiguration.getEventFilterList().filterList.contains("blocked_event"));
    }

    /**
     * Tests that event whitelist only allows specified events.
     * Only events in the whitelist should be recorded.
     */
    @Test
    public void eventFilter_whitelist_onlyAllowsSpecifiedEvents() throws JSONException {
        Set<String> whitelist = new HashSet<>();
        whitelist.add("allowed_event");
        whitelist.add("another_allowed");

        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().eventFilterList(whitelist, true).build()
        );
        Countly.sharedInstance().init(countlyConfig);

        // Record an allowed event - should be recorded
        Countly.sharedInstance().events().recordEvent("allowed_event");
        Assert.assertEquals(1, countlyStore.getEventQueueSize());

        // Record another allowed event - should be recorded
        Countly.sharedInstance().events().recordEvent("another_allowed");
        Assert.assertEquals(2, countlyStore.getEventQueueSize());

        // Record an event not in whitelist - should not be recorded
        Countly.sharedInstance().events().recordEvent("not_in_whitelist");
        Assert.assertEquals(2, countlyStore.getEventQueueSize());

        Assert.assertFalse(countlyStore.getEvents()[0].contains("not_in_whitelist"));
        Assert.assertFalse(countlyStore.getEvents()[1].contains("not_in_whitelist"));

        // Verify the filter state
        Assert.assertTrue(Countly.sharedInstance().moduleConfiguration.getEventFilterList().isWhitelist);
        Assert.assertTrue(Countly.sharedInstance().moduleConfiguration.getEventFilterList().filterList.contains("allowed_event"));
    }

    /**
     * Tests that an empty event filter allows all events.
     * When no filter rules are defined, all events should pass through.
     */
    @Test
    public void eventFilter_emptyFilter_allowsAllEvents() throws JSONException {
        Set<String> emptySet = new HashSet<>();

        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().eventFilterList(emptySet, false).build()
        );
        Countly.sharedInstance().init(countlyConfig);

        // All events should be recorded with empty filter
        Countly.sharedInstance().events().recordEvent("event_1");
        Assert.assertEquals(1, countlyStore.getEventQueueSize());

        Countly.sharedInstance().events().recordEvent("event_2");
        Assert.assertEquals(2, countlyStore.getEventQueueSize());

        Countly.sharedInstance().events().recordEvent("any_event");
        Assert.assertEquals(3, countlyStore.getEventQueueSize());
    }

    /**
     * Tests that internal events bypass event filters.
     * Internal SDK events like views should not be affected by event filters.
     */
    @Test
    public void eventFilter_internalEventsNotAffected() throws JSONException {
        Set<String> blacklist = new HashSet<>();
        blacklist.add("[CLY]_view"); // Try to block view events
        blacklist.add("test_blocked"); // A custom event to block

        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().eventFilterList(blacklist, false).build()
        );
        Countly.sharedInstance().init(countlyConfig);

        int initialQueueSize = countlyStore.getEventQueueSize();

        // View events should still be recorded (internal events bypass filters)
        Countly.sharedInstance().views().startView("test_view");
        Assert.assertEquals(initialQueueSize + 1, countlyStore.getEventQueueSize());

        // Custom blocked event should be blocked
        Countly.sharedInstance().events().recordEvent("test_blocked");
        Assert.assertEquals(initialQueueSize + 1, countlyStore.getEventQueueSize()); // Still same, blocked

        Assert.assertTrue(countlyStore.getEvents()[initialQueueSize].contains("[CLY]_view"));
    }

    // ================ User Property Filter Tests ================

    /**
     * Tests that an empty user property filter allows all properties.
     * When no filter rules are defined, all properties should pass through.
     */
    @Test
    public void userPropertyFilter_emptyFilter_allowsAllProperties() throws JSONException {
        Set<String> emptySet = new HashSet<>();

        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().userPropertyFilterList(emptySet, false).build()
        );
        Countly.sharedInstance().init(countlyConfig);

        // All properties should be allowed with empty filter
        Map<String, Object> properties = new ConcurrentHashMap<>();
        properties.put("prop1", "value1");
        properties.put("prop2", "value2");
        properties.put("any_prop", "value3");

        Countly.sharedInstance().userProfile().setProperties(properties);

        Assert.assertEquals(3, Countly.sharedInstance().moduleUserProfile.custom.size());
        Assert.assertTrue(Countly.sharedInstance().moduleUserProfile.custom.containsKey("prop1"));
        Assert.assertTrue(Countly.sharedInstance().moduleUserProfile.custom.containsKey("prop2"));
        Assert.assertTrue(Countly.sharedInstance().moduleUserProfile.custom.containsKey("any_prop"));

        Countly.sharedInstance().userProfile().save();
        ModuleUserProfileTests.validateUserProfileRequest(TestUtils.map(),
            TestUtils.map("prop1", "value1", "prop2", "value2", "any_prop", "value3"));
    }

    /**
     * Tests that user property blacklist properly blocks filtered properties.
     * Properties in the blacklist should not be recorded.
     */
    @Test
    public void userPropertyFilter_blacklist_blocksFilteredProperties() throws JSONException {
        Set<String> blacklist = new HashSet<>();
        blacklist.add("blocked_prop");
        blacklist.add("another_blocked");

        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().userPropertyFilterList(blacklist, false).build()
        );
        Countly.sharedInstance().init(countlyConfig);

        // Set properties - blocked ones should be filtered
        Map<String, Object> properties = new ConcurrentHashMap<>();
        properties.put("blocked_prop", "value1");
        properties.put("another_blocked", "value2");
        properties.put("allowed_prop", "value3");

        Countly.sharedInstance().userProfile().setProperties(properties);

        // Only allowed_prop should be set in custom properties
        Assert.assertNotNull(Countly.sharedInstance().moduleUserProfile.custom);
        Assert.assertTrue(Countly.sharedInstance().moduleUserProfile.custom.containsKey("allowed_prop"));
        Assert.assertFalse(Countly.sharedInstance().moduleUserProfile.custom.containsKey("blocked_prop"));
        Assert.assertFalse(Countly.sharedInstance().moduleUserProfile.custom.containsKey("another_blocked"));

        // Save and verify request only contains allowed property
        Countly.sharedInstance().userProfile().save();
        ModuleUserProfileTests.validateUserProfileRequest(TestUtils.map(), TestUtils.map("allowed_prop", "value3"));
    }

    /**
     * Tests that user property whitelist only allows specified properties.
     */
    @Test
    public void userPropertyFilter_whitelist_onlyAllowsSpecifiedProperties() throws JSONException {
        Set<String> whitelist = new HashSet<>();
        whitelist.add("allowed_prop");

        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().userPropertyFilterList(whitelist, true).build()
        );
        Countly.sharedInstance().init(countlyConfig);

        Map<String, Object> properties = new ConcurrentHashMap<>();
        properties.put("allowed_prop", "value1");
        properties.put("not_allowed", "value2");

        Countly.sharedInstance().userProfile().setProperties(properties);

        Assert.assertNotNull(Countly.sharedInstance().moduleUserProfile.custom);
        Assert.assertTrue(Countly.sharedInstance().moduleUserProfile.custom.containsKey("allowed_prop"));
        Assert.assertFalse(Countly.sharedInstance().moduleUserProfile.custom.containsKey("not_allowed"));

        // Save and verify request only contains allowed property
        Countly.sharedInstance().userProfile().save();
        ModuleUserProfileTests.validateUserProfileRequest(TestUtils.map(), TestUtils.map("allowed_prop", "value1"));
    }

    /**
     * Tests that named user properties bypass filters.
     * Named properties like name, email, username should not be filtered.
     */
    @Test
    public void userPropertyFilter_namedPropertiesBypassFilter() throws JSONException {
        Set<String> blacklist = new HashSet<>();
        blacklist.add("name");
        blacklist.add("email");
        blacklist.add("custom_blocked");

        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().userPropertyFilterList(blacklist, false).build()
        );
        Countly.sharedInstance().init(countlyConfig);

        Map<String, Object> properties = new ConcurrentHashMap<>();
        properties.put("name", "John Doe");
        properties.put("email", "john@example.com");
        properties.put("custom_blocked", "blocked_value");

        Countly.sharedInstance().userProfile().setProperties(properties);

        // Named properties should be set despite being in blacklist
        Assert.assertEquals("John Doe", Countly.sharedInstance().moduleUserProfile.name);
        Assert.assertEquals("john@example.com", Countly.sharedInstance().moduleUserProfile.email);

        // Save and verify named properties are in request but custom_blocked is not
        Countly.sharedInstance().userProfile().save();
        ModuleUserProfileTests.validateUserProfileRequest(
            TestUtils.map("name", "John Doe", "email", "john@example.com"),
            TestUtils.map()  // custom_blocked should be filtered out
        );
    }

    /**
     * Tests that modifyCustomData respects user property filters.
     */
    @Test
    public void userPropertyFilter_modifyCustomData_respectsFilter() throws JSONException {
        Set<String> blacklist = new HashSet<>();
        blacklist.add("blocked_prop");

        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().userPropertyFilterList(blacklist, false).build()
        );
        Countly.sharedInstance().init(countlyConfig);

        // Try to increment a blocked property - should be ignored
        Countly.sharedInstance().userProfile().incrementBy("blocked_prop", 5);
        Assert.assertNull(Countly.sharedInstance().moduleUserProfile.customMods);

        // Increment an allowed property - should work
        Countly.sharedInstance().userProfile().incrementBy("allowed_prop", 5);
        Assert.assertNotNull(Countly.sharedInstance().moduleUserProfile.customMods);
        Assert.assertTrue(Countly.sharedInstance().moduleUserProfile.customMods.containsKey("allowed_prop"));

        // Save and verify request only contains allowed property with increment
        Countly.sharedInstance().userProfile().save();
        JSONObject expectedMod = new JSONObject();
        expectedMod.put("$inc", 5);
        ModuleUserProfileTests.validateUserProfileRequest(
            TestUtils.map(),
            TestUtils.map("allowed_prop", expectedMod)
        );
    }

    // ================ Segmentation Filter Tests ================

    /**
     * Tests that segmentation blacklist removes filtered keys from event segmentation.
     */
    @Test
    public void segmentationFilter_blacklist_removesFilteredKeys() throws JSONException {
        Set<String> blacklist = new HashSet<>();
        blacklist.add("blocked_key");
        blacklist.add("another_blocked");

        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().segmentationFilterList(blacklist, false).eventQueueSize(1).build()
        );
        Countly.sharedInstance().init(countlyConfig);

        Map<String, Object> segmentation = new ConcurrentHashMap<>();
        segmentation.put("blocked_key", "value1");
        segmentation.put("another_blocked", "value2");
        segmentation.put("allowed_key", "value3");

        Countly.sharedInstance().events().recordEvent("test_event", segmentation);

        // Verify only allowed_key is in the recorded event
        Assert.assertEquals(1, TestUtils.getCurrentRQ().length);
        validateEventInRQ("test_event", TestUtils.map("allowed_key", "value3"), 0, 1, 0, 1);
    }

    /**
     * Tests that segmentation whitelist only keeps specified keys.
     */
    @Test
    public void segmentationFilter_whitelist_onlyKeepsSpecifiedKeys() throws JSONException {
        Set<String> whitelist = new HashSet<>();
        whitelist.add("allowed_key");

        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().segmentationFilterList(whitelist, true).eventQueueSize(1).build()
        );
        Countly.sharedInstance().init(countlyConfig);

        Map<String, Object> segmentation = new ConcurrentHashMap<>();
        segmentation.put("allowed_key", "value1");
        segmentation.put("not_allowed_1", "value2");
        segmentation.put("not_allowed_2", "value3");

        Countly.sharedInstance().events().recordEvent("test_event", segmentation);

        Assert.assertEquals(1, TestUtils.getCurrentRQ().length);
        validateEventInRQ("test_event", TestUtils.map("allowed_key", "value1"), 0, 1, 0, 1);
    }

    /**
     * Tests that empty segmentation filter allows all keys.
     */
    @Test
    public void segmentationFilter_emptyFilter_allowsAllKeys() throws JSONException {
        Set<String> emptySet = new HashSet<>();

        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().segmentationFilterList(emptySet, false).eventQueueSize(1).build()
        );
        Countly.sharedInstance().init(countlyConfig);

        Map<String, Object> segmentation = new ConcurrentHashMap<>();
        segmentation.put("key1", "value1");
        segmentation.put("key2", "value2");

        Countly.sharedInstance().events().recordEvent("test_event", segmentation);

        Assert.assertEquals(1, TestUtils.getCurrentRQ().length);
        validateEventInRQ("test_event", TestUtils.map("key1", "value1", "key2", "value2"), 0, 1, 0, 1);
    }

    // ================ Event Segmentation Filter Tests ================

    /**
     * Tests that event-specific segmentation blacklist only affects specified events.
     */
    @Test
    public void eventSegmentationFilter_blacklist_affectsSpecificEvents() throws JSONException {
        Map<String, Set<String>> filterMap = new ConcurrentHashMap<>();
        Set<String> event1Filter = new HashSet<>();
        event1Filter.add("blocked_for_event1");
        filterMap.put("event1", event1Filter);

        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().eventSegmentationFilterMap(filterMap, false).eventQueueSize(1).build()
        );
        Countly.sharedInstance().init(countlyConfig);

        // For event1, blocked_for_event1 should be removed
        Map<String, Object> segmentation1 = new ConcurrentHashMap<>();
        segmentation1.put("blocked_for_event1", "value1");
        segmentation1.put("allowed_key", "value2");
        Countly.sharedInstance().events().recordEvent("event1", segmentation1);

        Assert.assertEquals(1, TestUtils.getCurrentRQ().length);
        validateEventInRQ("event1", TestUtils.map("allowed_key", "value2"), 0, 1, 0, 1);

        // For event2, blocked_for_event1 should NOT be removed (filter only applies to event1)
        Map<String, Object> segmentation2 = new ConcurrentHashMap<>();
        segmentation2.put("blocked_for_event1", "value1");
        segmentation2.put("other_key", "value2");
        Countly.sharedInstance().events().recordEvent("event2", segmentation2);

        Assert.assertEquals(2, TestUtils.getCurrentRQ().length);
        validateEventInRQ("event2", TestUtils.map("blocked_for_event1", "value1", "other_key", "value2"), 1, 2, 0, 1);
    }

    /**
     * Tests that event-specific segmentation whitelist only keeps specified keys for that event.
     */
    @Test
    public void eventSegmentationFilter_whitelist_onlyKeepsSpecifiedKeysForEvent() throws JSONException {
        Map<String, Set<String>> filterMap = new ConcurrentHashMap<>();
        Set<String> event1Filter = new HashSet<>();
        event1Filter.add("allowed_for_event1");
        filterMap.put("event1", event1Filter);

        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().eventSegmentationFilterMap(filterMap, true).eventQueueSize(1).build()
        );
        Countly.sharedInstance().init(countlyConfig);

        // For event1, only allowed_for_event1 should remain
        Map<String, Object> segmentation = new ConcurrentHashMap<>();
        segmentation.put("allowed_for_event1", "value1");
        segmentation.put("not_allowed", "value2");
        Countly.sharedInstance().events().recordEvent("event1", segmentation);

        Assert.assertEquals(1, TestUtils.getCurrentRQ().length);
        validateEventInRQ("event1", TestUtils.map("allowed_for_event1", "value1"), 0, 1, 0, 1);
    }

    /**
     * Tests that events without specific rules pass all segmentation.
     */
    @Test
    public void eventSegmentationFilter_noRulesForEvent_allowsAllSegmentation() throws JSONException {
        Map<String, Set<String>> filterMap = new ConcurrentHashMap<>();
        Set<String> event1Filter = new HashSet<>();
        event1Filter.add("some_key");
        filterMap.put("event1", event1Filter);

        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().eventSegmentationFilterMap(filterMap, false).eventQueueSize(1).build()
        );
        Countly.sharedInstance().init(countlyConfig);

        // event2 has no rules, so all segmentation should pass
        Map<String, Object> segmentation = new ConcurrentHashMap<>();
        segmentation.put("any_key", "value1");
        segmentation.put("any_other_key", "value2");
        Countly.sharedInstance().events().recordEvent("event2", segmentation);

        Assert.assertEquals(1, TestUtils.getCurrentRQ().length);
        validateEventInRQ("event2", TestUtils.map("any_key", "value1", "any_other_key", "value2"), 0, 1, 0, 1);
    }

    /**
     * Tests that both general segmentation filter and event-specific filter are applied.
     */
    @Test
    public void segmentationFilters_combined_bothFiltersApplied() throws JSONException {
        // General segmentation blacklist
        Set<String> generalBlacklist = new HashSet<>();
        generalBlacklist.add("general_blocked");

        // Event-specific blacklist
        Map<String, Set<String>> eventFilterMap = new ConcurrentHashMap<>();
        Set<String> eventFilter = new HashSet<>();
        eventFilter.add("event_specific_blocked");
        eventFilterMap.put("test_event", eventFilter);

        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults()
                .segmentationFilterList(generalBlacklist, false)
                .eventSegmentationFilterMap(eventFilterMap, false)
                .eventQueueSize(1)
                .build()
        );
        Countly.sharedInstance().init(countlyConfig);

        Map<String, Object> segmentation = new ConcurrentHashMap<>();
        segmentation.put("general_blocked", "value1");
        segmentation.put("event_specific_blocked", "value2");
        segmentation.put("allowed_key", "value3");

        Countly.sharedInstance().events().recordEvent("test_event", segmentation);

        // Both general and event-specific blocked keys should be removed
        Assert.assertEquals(1, TestUtils.getCurrentRQ().length);
        validateEventInRQ("test_event", TestUtils.map("allowed_key", "value3"), 0, 1, 0, 1);
    }

    // ================ Journey Trigger Events Tests ================

    /**
     * Tests that journey trigger events are correctly configured.
     */
    @Test
    public void journeyTriggerEvents_configuredCorrectly() throws JSONException {
        Set<String> triggerEvents = new HashSet<>();
        triggerEvents.add("trigger_event");
        triggerEvents.add("another_trigger");

        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults()
                .journeyTriggerEvents(triggerEvents)
                .build()
        );
        Countly.sharedInstance().init(countlyConfig);

        // Verify trigger events are configured
        Assert.assertTrue(Countly.sharedInstance().moduleConfiguration.getJourneyTriggerEvents().contains("trigger_event"));
        Assert.assertTrue(Countly.sharedInstance().moduleConfiguration.getJourneyTriggerEvents().contains("another_trigger"));
        Assert.assertEquals(2, Countly.sharedInstance().moduleConfiguration.getJourneyTriggerEvents().size());
    }

    /**
     * Tests that empty journey trigger events set is handled correctly.
     */
    @Test
    public void journeyTriggerEvents_emptySet_noRefresh() throws JSONException {
        Set<String> emptyTriggerEvents = new HashSet<>();

        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().journeyTriggerEvents(emptyTriggerEvents).build()
        );
        Countly.sharedInstance().init(countlyConfig);

        Assert.assertTrue(Countly.sharedInstance().moduleConfiguration.getJourneyTriggerEvents().isEmpty());
    }

    /**
     * Tests that recording a journey trigger event forces event flush and registers
     * a callback for content zone refresh.
     * Verifies:
     * 1. JTE event is immediately flushed to RQ (force flush behavior)
     * 2. The request contains callback_id (refresh will be triggered on completion)
     * 3. Non-JTE events don't trigger this behavior
     */
    @Test
    public void journeyTriggerEvents_triggersRefreshContentZone() throws JSONException {
        Set<String> triggerEvents = new HashSet<>();
        triggerEvents.add("jte_event");

        // Use high event queue threshold to verify force flush behavior
        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults()
                .journeyTriggerEvents(triggerEvents)
                .eventQueueSize(100)  // High threshold
                .build()
        );
        Countly.sharedInstance().init(countlyConfig);

        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);

        // Record a non-JTE event - should NOT force flush (queue threshold is 100)
        Countly.sharedInstance().events().recordEvent("regular_event");
        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);  // Still in event queue
        Assert.assertEquals(1, countlyStore.getEventQueueSize());

        // Record a JTE event - should force flush and have callback_id
        Countly.sharedInstance().events().recordEvent("jte_event");
        Assert.assertEquals(1, TestUtils.getCurrentRQ().length);  // Force flushed to RQ
        Assert.assertEquals(0, countlyStore.getEventQueueSize());  // Event queue emptied

        // Verify the request contains callback_id (indicates refresh callback registered)
        Map<String, String>[] rq = TestUtils.getCurrentRQ();
        Assert.assertTrue(rq[0].containsKey("callback_id"));
    }

    /**
     * Tests that when a journey trigger event is recorded and successfully delivered,
     * refreshContentZone is called.
     * Uses a mocked ConnectionProcessor to simulate HTTP responses through the SDK's normal flow.
     */
    @Test
    public void journeyTriggerEvents_contentZoneRefreshFlow() throws Exception {
        Set<String> triggerEvents = new HashSet<>();
        triggerEvents.add("purchase_complete");

        final AtomicInteger contentRequestCount = new AtomicInteger(0);

        testJTEWithMockedWebServer((request, response) -> {
            if (request.getPath().contains("&method=queue")) {
                contentRequestCount.incrementAndGet();
            }

            // Server config request - return JTE config with refreshContentZone disabled
            if (request.getPath().contains("&method=sc")) {
                try {
                    response.setBody(new ServerConfigBuilder().defaults()
                        .journeyTriggerEvents(triggerEvents)
                        .contentZone(true)
                        .build());
                } catch (JSONException ignored) {
                }
            }
        }, () -> {
            try {
                // Wait for server config to be fetched and applied
                Thread.sleep(2000);
                // verify that enter is called
                Assert.assertEquals(1, contentRequestCount.get());

                // Record JTE event - this adds request with callback to RQ
                Countly.sharedInstance().events().recordEvent("purchase_complete");
                Assert.assertEquals(1, TestUtils.getCurrentRQ().length);

                // Get the callback_id from the request
                Map<String, String>[] rq = TestUtils.getCurrentRQ();
                String callbackId = rq[0].get("callback_id");
                Assert.assertNotNull(callbackId);
                Thread.sleep(1000);
                Assert.assertEquals(2, contentRequestCount.get());
                Assert.assertEquals(0, TestUtils.getCurrentRQ().length);
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * Tests that journey trigger does NOT send content request when event request fails.
     * The content refresh callback is only called on success, so failed event requests
     * should not trigger content zone refresh.
     */
    @Test
    public void journeyTriggerEvents_noContentRequestOnEventFailure() throws Exception {
        Set<String> triggerEvents = new HashSet<>();
        triggerEvents.add("journey_event");

        final AtomicInteger contentRequestCount = new AtomicInteger(0);
        final AtomicInteger eventRequestCount = new AtomicInteger(0);

        testJTEWithMockedWebServer((request, response) -> {
            if (request.getPath().contains("&method=queue")) {
                contentRequestCount.incrementAndGet();
            }

            // Track event requests
            if (request.getPath().contains("events=")) {
                eventRequestCount.incrementAndGet();
                response.setResponseCode(500)
                    .setBody("Internal Server Error");
            }

            // Server config request - return JTE config with refreshContentZone disabled
            if (request.getPath().contains("&method=sc")) {
                try {
                    response.setBody(new ServerConfigBuilder().defaults()
                        .journeyTriggerEvents(triggerEvents)
                        .build());
                } catch (JSONException ignored) {
                }
            }
        }, () -> {
            try {
                Thread.sleep(1000);
                int initialContentCount = contentRequestCount.get();

                // Record JTE event - this should try to send but fail
                Countly.sharedInstance().events().recordEvent("journey_event");
                Thread.sleep(2000);

                // Event request should have been attempted
                Assert.assertTrue(eventRequestCount.get() >= 1);

                // Content request should NOT have been made since event failed
                // The callback only fires on success
                Assert.assertEquals(initialContentCount, contentRequestCount.get());
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * Tests that journey trigger skips content refresh when already in content zone.
     * When isCurrentlyInContentZone is true, refreshContentZone should skip.
     */
    @Test
    public void journeyTriggerEvents_skipsRefreshWhenInContentZone() throws Exception {
        Set<String> triggerEvents = new HashSet<>();
        triggerEvents.add("journey_event");
        triggerEvents.add("journey_event_2");

        AtomicInteger contentRequestCount = new AtomicInteger(0);
        AtomicBoolean returnContent = new AtomicBoolean(false);

        testJTEWithMockedWebServer((request, response) -> {
            if (request.getPath().contains("&method=queue")) {
                contentRequestCount.incrementAndGet();
                // Return valid content JSON when returnContent is true
                // This will set isCurrentlyInContentZone=true in ModuleContent
                if (returnContent.get()) {
                    String contentJson = "{\"html\":\"https://countly.com\",\"geo\":{\"p\":{\"x\":0,\"y\":0,\"w\":100,\"h\":100},\"l\":{\"x\":0,\"y\":0,\"w\":100,\"h\":100}}}";
                    response.setBody(contentJson);
                }
            }

            // Server config request - return JTE config with refreshContentZone disabled
            if (request.getPath().contains("&method=sc")) {
                try {
                    response.setBody(new ServerConfigBuilder().defaults()
                        .journeyTriggerEvents(triggerEvents)
                        .contentZone(true)
                        .sessionTracking(false)  // Disable to prevent auto sessions
                        .build());
                } catch (JSONException ignored) {
                }
            }
        }, () -> {
            try {
                // Wait for server config to be fetched and applied
                Thread.sleep(2000);
                // verify that enter is called
                Assert.assertEquals(1, contentRequestCount.get());

                // Record JTE event - this adds request with callback to RQ
                returnContent.set(true);
                Countly.sharedInstance().events().recordEvent("journey_event");
                Assert.assertEquals(1, TestUtils.getCurrentRQ().length);

                Thread.sleep(2000);  // Allow time for content to be fetched and TransparentActivity to launch
                Assert.assertEquals(2, contentRequestCount.get());

                // Note: RQ may contain session requests from activity lifecycle when TransparentActivity launches
                // This is expected behavior - the core test is verifying content refresh is skipped

                // Record another JTE - should NOT trigger content refresh since isCurrentlyInContentZone=true
                Countly.sharedInstance().events().recordEvent("journey_event_2");
                Thread.sleep(1000);
                // Content request count should NOT increase since we're already in content zone, so refresh should skip
                Assert.assertEquals(2, contentRequestCount.get());

                // Finish all TransparentActivity instances before calling exitContentZone
                // This ensures the activity lifecycle completes while SDK is still initialized
                finishAllTransparentActivities();
                Thread.sleep(2000); // Wait for activity lifecycle to complete

                Countly.sharedInstance().contents().exitContentZone();
            } catch (InterruptedException ignored) {
            }
        });
    }

    /**
     * Tests that multiple journey trigger events each trigger their own flush.
     * Each JTE event should be immediately flushed with its own callback_id.
     */
    @Test
    public void journeyTriggerEvents_multipleEventsEachFlush() throws JSONException {
        Set<String> triggerEvents = new HashSet<>();
        triggerEvents.add("jte_1");
        triggerEvents.add("jte_2");

        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults()
                .journeyTriggerEvents(triggerEvents)
                .eventQueueSize(100)  // High threshold to verify force flush
                .build()
        );
        Countly.sharedInstance().init(countlyConfig);

        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);

        // Record first JTE event
        Countly.sharedInstance().events().recordEvent("jte_1");
        Assert.assertEquals(1, TestUtils.getCurrentRQ().length);
        String firstCallbackId = TestUtils.getCurrentRQ()[0].get("callback_id");
        Assert.assertNotNull(firstCallbackId);

        // Record second JTE event
        Countly.sharedInstance().events().recordEvent("jte_2");
        Assert.assertEquals(2, TestUtils.getCurrentRQ().length);
        String secondCallbackId = TestUtils.getCurrentRQ()[1].get("callback_id");
        Assert.assertNotNull(secondCallbackId);

        // Each event should have its own callback_id
        Assert.assertNotEquals(firstCallbackId, secondCallbackId);
    }

    /**
     * Tests that non-journey events stay queued while JTE events are flushed immediately.
     * This verifies the selective flush behavior.
     */
    @Test
    public void journeyTriggerEvents_nonJteStaysQueued() throws JSONException {
        Set<String> triggerEvents = new HashSet<>();
        triggerEvents.add("journey_event");

        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults()
                .journeyTriggerEvents(triggerEvents)
                .eventQueueSize(100)  // High threshold
                .build()
        );
        Countly.sharedInstance().init(countlyConfig);

        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);
        Assert.assertEquals(0, countlyStore.getEventQueueSize());

        // Record a non-JTE event - should stay in queue
        Countly.sharedInstance().events().recordEvent("regular_event");
        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);
        Assert.assertEquals(1, countlyStore.getEventQueueSize());

        // Record another non-JTE event
        Countly.sharedInstance().events().recordEvent("another_regular");
        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);
        Assert.assertEquals(2, countlyStore.getEventQueueSize());

        // Record a JTE event - should flush ALL events
        Countly.sharedInstance().events().recordEvent("journey_event");
        Assert.assertEquals(1, TestUtils.getCurrentRQ().length);
        Assert.assertEquals(0, countlyStore.getEventQueueSize());

        // Verify the flushed request contains all 3 events
        Map<String, String>[] rq = TestUtils.getCurrentRQ();
        String events = rq[0].get("events");
        Assert.assertNotNull(events);
        Assert.assertTrue(events.contains("regular_event"));
        Assert.assertTrue(events.contains("another_regular"));
        Assert.assertTrue(events.contains("journey_event"));
    }

    /**
     * Tests that JTE triggers content zone refresh with retry mechanism on empty responses.
     * When the server returns an empty content response, the SDK should retry up to 3 times.
     * Verifies:
     * 1. JTE event flushes immediately with callback_id
     * 2. Content fetch is triggered after successful event delivery
     * 3. Empty responses trigger retry mechanism (total 4 requests: 1 initial + 3 retries)
     */
    @Test
    public void journeyTriggerEvents_refreshRetriesCorrectlyAfterProvidingEmptyResponse() throws Exception {
        Set<String> triggerEvents = new HashSet<>();
        triggerEvents.add("journey_event");

        final AtomicInteger contentRequestCount = new AtomicInteger(0);
        final AtomicInteger eventRequestCount = new AtomicInteger(0);

        testJTEWithMockedWebServer((request, response) -> {
            if (request.getPath().contains("&method=queue")) {
                contentRequestCount.incrementAndGet();
                response.setBody("[]");  // Simulate empty content response
            }
            // Track event requests
            if (request.getPath().contains("events=")) {
                eventRequestCount.incrementAndGet();
            }
            // Server config request - return JTE config with refreshContentZone disabled
            if (request.getPath().contains("&method=sc")) {
                try {
                    response.setBody(new ServerConfigBuilder().defaults()
                        .journeyTriggerEvents(triggerEvents)
                        .contentZone(true)  // Content zone enabled but refresh disabled
                        .build());
                } catch (JSONException ignored) {
                }
            }
        }, () -> {
            try {
                Thread.sleep(1000);

                // Content zone is enabled, so enter should be called
                Assert.assertEquals(1, contentRequestCount.get());

                // Record JTE event - should still flush immediately with callback_id
                Countly.sharedInstance().events().recordEvent("journey_event");
                Assert.assertEquals(1, TestUtils.getCurrentRQ().length);

                // Verify callback_id is present (callback is still registered)
                Map<String, String>[] rq = TestUtils.getCurrentRQ();
                Assert.assertTrue(rq[0].containsKey("callback_id"));

                Thread.sleep(4000);

                // Event should have been sent
                Assert.assertTrue(eventRequestCount.get() >= 1);

                // Verify that retry happened 3 times
                Assert.assertEquals(4, contentRequestCount.get());
            } catch (InterruptedException ignored) {
            }
        });
    }

    /**
     * Tests that content zone refresh retries stop when valid content is received.
     * When empty responses are followed by a valid content response, retries should cease.
     * Verifies:
     * 1. JTE event flushes immediately with callback_id
     * 2. Empty responses trigger retry mechanism
     * 3. Valid content response stops further retries (content shown, no more requests)
     */
    @Test
    public void journeyTriggerEvents_refreshRetryStopAfterValidContentResponse() throws Exception {
        Set<String> triggerEvents = new HashSet<>();
        triggerEvents.add("journey_event");

        final AtomicInteger contentRequestCount = new AtomicInteger(0);
        final AtomicInteger eventRequestCount = new AtomicInteger(0);
        final AtomicBoolean returnContent = new AtomicBoolean(false);

        testJTEWithMockedWebServer((request, response) -> {
            if (request.getPath().contains("&method=queue")) {
                contentRequestCount.incrementAndGet();
                response.setBody("[]");  // Simulate empty content response
                if (returnContent.get()) {
                    String contentJson = "{\"html\":\"https://countly.com\",\"geo\":{\"p\":{\"x\":0,\"y\":0,\"w\":100,\"h\":100},\"l\":{\"x\":0,\"y\":0,\"w\":100,\"h\":100}}}";
                    response.setBody(contentJson);
                }
            }
            // Track event requests
            if (request.getPath().contains("events=")) {
                eventRequestCount.incrementAndGet();
            }
            // Server config request - return JTE config with refreshContentZone disabled
            if (request.getPath().contains("&method=sc")) {
                try {
                    response.setBody(new ServerConfigBuilder().defaults()
                        .journeyTriggerEvents(triggerEvents)
                        .contentZone(true)  // Content zone enabled but refresh disabled
                        .build());
                } catch (JSONException ignored) {
                }
            }
        }, () -> {
            try {
                Thread.sleep(1000);

                // Content zone is enabled, so enter should be called
                Assert.assertEquals(1, contentRequestCount.get());

                // Record JTE event - should still flush immediately with callback_id
                Countly.sharedInstance().events().recordEvent("journey_event");
                Assert.assertEquals(1, TestUtils.getCurrentRQ().length);

                // Verify callback_id is present (callback is still registered)
                Map<String, String>[] rq = TestUtils.getCurrentRQ();
                Assert.assertTrue(rq[0].containsKey("callback_id"));

                Thread.sleep(2000);
                returnContent.set(true);

                // Event should have been sent
                Assert.assertTrue(eventRequestCount.get() >= 1);

                // Verify that retry happened 2 times and it got the valid content on 3rd try
                Assert.assertEquals(3, contentRequestCount.get());
            } catch (InterruptedException ignored) {
            }
        });
    }

    private void testJTEWithMockedWebServer(BiConsumer<RecordedRequest, MockResponse> customRequestFlow, Runnable runnable) throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(new Dispatcher() {
                @NotNull @Override public MockResponse dispatch(@NotNull RecordedRequest recordedRequest) throws InterruptedException {
                    MockResponse response = new MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "application/json").setBody("{\"result\": \"Success\"}");
                    // Track content requests
                    customRequestFlow.accept(recordedRequest, response);
                    return response;
                }
            });

            server.start();
            String serverUrl = server.url("/").toString();
            serverUrl = serverUrl.substring(0, serverUrl.length() - 1);

            CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
            countlyConfig.metricProviderOverride = new MockedMetricProvider();
            countlyConfig.setServerURL(serverUrl);
            Countly.sharedInstance().init(countlyConfig);
            Countly.sharedInstance().moduleContent.CONTENT_START_DELAY_MS = 0;
            Countly.sharedInstance().moduleContent.REFRESH_CONTENT_ZONE_DELAY_MS = 0;

            Thread.sleep(1000);

            runnable.run();

            server.shutdown();
        }
    }

    // ================ User Property Cache Limit Tests ================

    /**
     * Tests that user property cache limit default value is correct.
     */
    @Test
    public void userPropertyCacheLimit_defaultValue() throws JSONException {
        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().build()
        );
        Countly.sharedInstance().init(countlyConfig);

        Assert.assertEquals(100, Countly.sharedInstance().moduleConfiguration.getUserPropertyCacheLimit());
    }

    /**
     * Tests that user property cache limit can be updated via server configuration.
     */
    @Test
    public void userPropertyCacheLimit_serverConfigUpdate() throws JSONException {
        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().userPropertyCacheLimit(50).build()
        );
        Countly.sharedInstance().init(countlyConfig);

        Assert.assertEquals(50, Countly.sharedInstance().moduleConfiguration.getUserPropertyCacheLimit());
    }

    /**
     * Tests that user property cache limit can be configured via SDK behavior settings.
     * This tests loading from local configuration rather than server response.
     */
    @Test
    public void userPropertyCacheLimit_configuredViaSdkBehaviorSettings() throws JSONException {
        // Create a server config with custom value and use it as SDK behavior settings
        String serverConfig = new ServerConfigBuilder().defaults().userPropertyCacheLimit(75).build();

        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.setSDKBehaviorSettings(serverConfig);
        Countly.sharedInstance().init(countlyConfig);

        Assert.assertEquals(75, Countly.sharedInstance().moduleConfiguration.getUserPropertyCacheLimit());
    }

    /**
     * Tests that user property cache limit enforcement removes oldest properties when exceeded.
     * When more custom properties are set than the limit, oldest ones should be removed.
     */
    @Test
    public void userPropertyCacheLimit_enforcesLimitOnSetProperties() throws JSONException {
        // Set a small cache limit
        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().userPropertyCacheLimit(3).build()
        );
        Countly.sharedInstance().init(countlyConfig);

        Assert.assertEquals(3, Countly.sharedInstance().moduleConfiguration.getUserPropertyCacheLimit());

        // Set more properties than the limit
        Map<String, Object> properties = new ConcurrentHashMap<>();
        properties.put("prop1", "value1");
        properties.put("prop2", "value2");
        properties.put("prop3", "value3");
        properties.put("prop4", "value4");
        properties.put("prop5", "value5");

        Countly.sharedInstance().userProfile().setProperties(properties);

        // Only 3 properties should remain (the limit)
        Assert.assertNotNull(Countly.sharedInstance().moduleUserProfile.custom);
        Assert.assertEquals(3, Countly.sharedInstance().moduleUserProfile.custom.size());
    }

    /**
     * Tests that user property cache limit does not affect properties when under limit.
     */
    @Test
    public void userPropertyCacheLimit_allowsPropertiesUnderLimit() throws JSONException {
        // Set a cache limit higher than properties we'll add
        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().userPropertyCacheLimit(10).build()
        );
        Countly.sharedInstance().init(countlyConfig);

        // Set fewer properties than the limit
        Map<String, Object> properties = new ConcurrentHashMap<>();
        properties.put("prop1", "value1");
        properties.put("prop2", "value2");
        properties.put("prop3", "value3");

        Countly.sharedInstance().userProfile().setProperties(properties);

        // All properties should remain
        Assert.assertNotNull(Countly.sharedInstance().moduleUserProfile.custom);
        Assert.assertEquals(3, Countly.sharedInstance().moduleUserProfile.custom.size());
        Assert.assertTrue(Countly.sharedInstance().moduleUserProfile.custom.containsKey("prop1"));
        Assert.assertTrue(Countly.sharedInstance().moduleUserProfile.custom.containsKey("prop2"));
        Assert.assertTrue(Countly.sharedInstance().moduleUserProfile.custom.containsKey("prop3"));
    }

    /**
     * Tests that named properties are not affected by user property cache limit.
     * Named properties (name, email, etc.) should be separate from custom properties limit.
     */
    @Test
    public void userPropertyCacheLimit_namedPropertiesNotAffected() throws JSONException {
        // Set a small cache limit for custom properties
        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().userPropertyCacheLimit(2).build()
        );
        Countly.sharedInstance().init(countlyConfig);

        // Set named properties plus custom properties exceeding limit
        Map<String, Object> properties = new ConcurrentHashMap<>();
        properties.put("name", "John Doe");
        properties.put("email", "john@example.com");
        properties.put("username", "johndoe");
        properties.put("custom1", "value1");
        properties.put("custom2", "value2");
        properties.put("custom3", "value3");

        Countly.sharedInstance().userProfile().setProperties(properties);

        // Named properties should be set regardless of limit
        Assert.assertEquals("John Doe", Countly.sharedInstance().moduleUserProfile.name);
        Assert.assertEquals("john@example.com", Countly.sharedInstance().moduleUserProfile.email);
        Assert.assertEquals("johndoe", Countly.sharedInstance().moduleUserProfile.username);

        // Custom properties should be limited to 2
        Assert.assertNotNull(Countly.sharedInstance().moduleUserProfile.custom);
        Assert.assertEquals(2, Countly.sharedInstance().moduleUserProfile.custom.size());
    }

    /**
     * Tests that cache limit enforcement works across multiple setProperties calls.
     */
    @Test
    public void userPropertyCacheLimit_enforcesAcrossMultipleCalls() throws JSONException {
        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().userPropertyCacheLimit(3).build()
        );
        Countly.sharedInstance().init(countlyConfig);

        // First call - add 2 properties
        Map<String, Object> props1 = new ConcurrentHashMap<>();
        props1.put("prop1", "value1");
        props1.put("prop2", "value2");
        Countly.sharedInstance().userProfile().setProperties(props1);

        Assert.assertEquals(2, Countly.sharedInstance().moduleUserProfile.custom.size());

        // Second call - add 2 more (total 4, but limit is 3)
        Map<String, Object> props2 = new ConcurrentHashMap<>();
        props2.put("prop3", "value3");
        props2.put("prop4", "value4");
        Countly.sharedInstance().userProfile().setProperties(props2);

        // Should be limited to 3
        Assert.assertEquals(3, Countly.sharedInstance().moduleUserProfile.custom.size());
    }

    /**
     * Tests that cache limit of 1 only keeps one property.
     */
    @Test
    public void userPropertyCacheLimit_limitOfOne() throws JSONException {
        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().userPropertyCacheLimit(1).build()
        );
        Countly.sharedInstance().init(countlyConfig);

        Map<String, Object> properties = new ConcurrentHashMap<>();
        properties.put("prop1", "value1");
        properties.put("prop2", "value2");
        properties.put("prop3", "value3");

        Countly.sharedInstance().userProfile().setProperties(properties);

        // Only 1 property should remain
        Assert.assertEquals(1, Countly.sharedInstance().moduleUserProfile.custom.size());
    }

    /**
     * Tests that cache limit of 0 is treated as invalid and the default limit (100) is used.
     * The SDK validation requires values > 0, so 0 is rejected.
     */
    @Test
    public void userPropertyCacheLimit_limitOfZero_usesDefault() throws JSONException {
        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().userPropertyCacheLimit(0).build()
        );
        Countly.sharedInstance().init(countlyConfig);

        Map<String, Object> properties = new ConcurrentHashMap<>();
        properties.put("prop1", "value1");
        properties.put("prop2", "value2");

        Countly.sharedInstance().userProfile().setProperties(properties);

        // Limit of 0 is invalid (SDK requires > 0), so default (100) is used
        // Both properties should be stored
        Assert.assertEquals(2, Countly.sharedInstance().moduleUserProfile.custom.size());

        // Verify default limit is applied
        Assert.assertEquals(100, Countly.sharedInstance().moduleConfiguration.getUserPropertyCacheLimit());
    }

    /**
     * Tests that cache limit is enforced on modification operations (incrementBy).
     * When using incrementBy on multiple properties exceeding the limit, oldest should be removed.
     */
    @Test
    public void userPropertyCacheLimit_enforcesOnIncrementBy() throws JSONException {
        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().userPropertyCacheLimit(2).build()
        );
        Countly.sharedInstance().init(countlyConfig);

        // Add multiple increment operations exceeding the limit
        Countly.sharedInstance().userProfile().incrementBy("counter1", 1);
        Assert.assertEquals(1, Countly.sharedInstance().moduleUserProfile.customMods.size());

        Countly.sharedInstance().userProfile().incrementBy("counter2", 2);
        Assert.assertEquals(2, Countly.sharedInstance().moduleUserProfile.customMods.size());

        Countly.sharedInstance().userProfile().incrementBy("counter3", 3);
        // Should be limited to 2
        Assert.assertEquals(2, Countly.sharedInstance().moduleUserProfile.customMods.size());

        Countly.sharedInstance().userProfile().incrementBy("counter4", 4);
        // Still limited to 2
        Assert.assertEquals(2, Countly.sharedInstance().moduleUserProfile.customMods.size());
    }

    /**
     * Tests that cache limit is enforced on multiply operations.
     */
    @Test
    public void userPropertyCacheLimit_enforcesOnMultiply() throws JSONException {
        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().userPropertyCacheLimit(2).build()
        );
        Countly.sharedInstance().init(countlyConfig);

        Countly.sharedInstance().userProfile().multiply("value1", 2);
        Countly.sharedInstance().userProfile().multiply("value2", 3);
        Countly.sharedInstance().userProfile().multiply("value3", 4);

        // Should be limited to 2
        Assert.assertEquals(2, Countly.sharedInstance().moduleUserProfile.customMods.size());
    }

    /**
     * Tests that cache limit is enforced on push operations.
     */
    @Test
    public void userPropertyCacheLimit_enforcesOnPush() throws JSONException {
        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().userPropertyCacheLimit(2).build()
        );
        Countly.sharedInstance().init(countlyConfig);

        Countly.sharedInstance().userProfile().push("array1", "item1");
        Countly.sharedInstance().userProfile().push("array2", "item2");
        Countly.sharedInstance().userProfile().push("array3", "item3");

        // Should be limited to 2
        Assert.assertEquals(2, Countly.sharedInstance().moduleUserProfile.customMods.size());
    }

    /**
     * Tests that cache limit is enforced on mixed modification operations.
     */
    @Test
    public void userPropertyCacheLimit_enforcesOnMixedMods() throws JSONException {
        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().userPropertyCacheLimit(3).build()
        );
        Countly.sharedInstance().init(countlyConfig);

        Countly.sharedInstance().userProfile().incrementBy("counter", 1);
        Countly.sharedInstance().userProfile().multiply("multiplier", 2);
        Countly.sharedInstance().userProfile().push("array", "item");
        Countly.sharedInstance().userProfile().saveMax("maxValue", 100);
        Countly.sharedInstance().userProfile().saveMin("minValue", 1);

        // Should be limited to 3
        Assert.assertEquals(3, Countly.sharedInstance().moduleUserProfile.customMods.size());
    }

    /**
     * Tests that updating the same property doesn't increase count.
     */
    @Test
    public void userPropertyCacheLimit_samePropertyUpdateDoesNotIncreaseCount() throws JSONException {
        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().userPropertyCacheLimit(2).build()
        );
        Countly.sharedInstance().init(countlyConfig);

        // Increment same property multiple times
        Countly.sharedInstance().userProfile().incrementBy("counter", 1);
        Assert.assertEquals(1, Countly.sharedInstance().moduleUserProfile.customMods.size());

        Countly.sharedInstance().userProfile().incrementBy("counter", 2);
        Assert.assertEquals(1, Countly.sharedInstance().moduleUserProfile.customMods.size());

        Countly.sharedInstance().userProfile().incrementBy("counter", 3);
        Assert.assertEquals(1, Countly.sharedInstance().moduleUserProfile.customMods.size());

        // Add a different property
        Countly.sharedInstance().userProfile().incrementBy("otherCounter", 1);
        Assert.assertEquals(2, Countly.sharedInstance().moduleUserProfile.customMods.size());
    }

    /**
     * Tests that custom properties and customMods have separate limits.
     * Both should respect the same cache limit independently.
     */
    @Test
    public void userPropertyCacheLimit_separateLimitsForCustomAndMods() throws JSONException {
        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().userPropertyCacheLimit(2).build()
        );
        Countly.sharedInstance().init(countlyConfig);

        // Add properties via setProperties
        Map<String, Object> properties = new ConcurrentHashMap<>();
        properties.put("prop1", "value1");
        properties.put("prop2", "value2");
        properties.put("prop3", "value3");
        Countly.sharedInstance().userProfile().setProperties(properties);

        // Custom should be limited to 2
        Assert.assertEquals(2, Countly.sharedInstance().moduleUserProfile.custom.size());

        // Add modifications
        Countly.sharedInstance().userProfile().incrementBy("counter1", 1);
        Countly.sharedInstance().userProfile().incrementBy("counter2", 2);
        Countly.sharedInstance().userProfile().incrementBy("counter3", 3);

        // CustomMods should also be limited to 2
        Assert.assertEquals(2, Countly.sharedInstance().moduleUserProfile.customMods.size());

        // Both limits are independent
        Assert.assertEquals(2, Countly.sharedInstance().moduleUserProfile.custom.size());
        Assert.assertEquals(2, Countly.sharedInstance().moduleUserProfile.customMods.size());
    }

    // ================ Filter Configuration Parsing Tests ================

    /**
     * Tests that filter configuration is correctly parsed from server response.
     */
    @Test
    public void filterConfigParsing_allFiltersCorrectlyParsed() throws JSONException {
        Set<String> eventBlacklist = new HashSet<>();
        eventBlacklist.add("blocked_event");

        Set<String> userPropertyBlacklist = new HashSet<>();
        userPropertyBlacklist.add("blocked_prop");

        Set<String> segmentationBlacklist = new HashSet<>();
        segmentationBlacklist.add("blocked_seg");

        Map<String, Set<String>> eventSegBlacklist = new ConcurrentHashMap<>();
        Set<String> eventSpecificSeg = new HashSet<>();
        eventSpecificSeg.add("specific_key");
        eventSegBlacklist.put("specific_event", eventSpecificSeg);

        Set<String> journeyTriggers = new HashSet<>();
        journeyTriggers.add("journey_event");

        ServerConfigBuilder builder = new ServerConfigBuilder().defaults()
            .eventFilterList(eventBlacklist, false)
            .userPropertyFilterList(userPropertyBlacklist, false)
            .segmentationFilterList(segmentationBlacklist, false)
            .eventSegmentationFilterMap(eventSegBlacklist, false)
            .journeyTriggerEvents(journeyTriggers)
            .userPropertyCacheLimit(200);

        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(builder.build());
        Countly.sharedInstance().init(countlyConfig);

        // Verify all filters are correctly configured
        Assert.assertFalse(Countly.sharedInstance().moduleConfiguration.getEventFilterList().isWhitelist);
        Assert.assertTrue(Countly.sharedInstance().moduleConfiguration.getEventFilterList().filterList.contains("blocked_event"));

        Assert.assertFalse(Countly.sharedInstance().moduleConfiguration.getUserPropertyFilterList().isWhitelist);
        Assert.assertTrue(Countly.sharedInstance().moduleConfiguration.getUserPropertyFilterList().filterList.contains("blocked_prop"));

        Assert.assertFalse(Countly.sharedInstance().moduleConfiguration.getSegmentationFilterList().isWhitelist);
        Assert.assertTrue(Countly.sharedInstance().moduleConfiguration.getSegmentationFilterList().filterList.contains("blocked_seg"));

        Assert.assertFalse(Countly.sharedInstance().moduleConfiguration.getEventSegmentationFilterList().isWhitelist);
        Assert.assertTrue(Countly.sharedInstance().moduleConfiguration.getEventSegmentationFilterList().filterList.containsKey("specific_event"));

        Assert.assertTrue(Countly.sharedInstance().moduleConfiguration.getJourneyTriggerEvents().contains("journey_event"));

        Assert.assertEquals(200, Countly.sharedInstance().moduleConfiguration.getUserPropertyCacheLimit());
    }

    /**
     * Tests that blacklist mode is correctly set when using blacklist.
     */
    @Test
    public void filterConfigParsing_blacklistModeSet() throws JSONException {
        Set<String> blacklist = new HashSet<>();
        blacklist.add("blocked_event");

        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().eventFilterList(blacklist, false).build()
        );
        Countly.sharedInstance().init(countlyConfig);

        // Should be blacklist mode
        Assert.assertFalse(Countly.sharedInstance().moduleConfiguration.getEventFilterList().isWhitelist);
        Assert.assertTrue(Countly.sharedInstance().moduleConfiguration.getEventFilterList().filterList.contains("blocked_event"));
    }

    /**
     * Tests that whitelist mode is correctly set when using whitelist.
     */
    @Test
    public void filterConfigParsing_whitelistModeSet() throws JSONException {
        Set<String> whitelist = new HashSet<>();
        whitelist.add("allowed_event");

        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().eventFilterList(whitelist, true).build()
        );
        Countly.sharedInstance().init(countlyConfig);

        // Should be whitelist mode
        Assert.assertTrue(Countly.sharedInstance().moduleConfiguration.getEventFilterList().isWhitelist);
        Assert.assertTrue(Countly.sharedInstance().moduleConfiguration.getEventFilterList().filterList.contains("allowed_event"));
    }

    // ================ Edge Case Tests ================

    /**
     * Tests filter behavior with special characters in event names.
     */
    @Test
    public void edgeCase_specialCharactersInEventName() throws JSONException {
        Set<String> blacklist = new HashSet<>();
        blacklist.add("event with spaces");
        blacklist.add("event-with-dashes");
        blacklist.add("event_with_underscores");

        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().eventFilterList(blacklist, false).build()
        );
        Countly.sharedInstance().init(countlyConfig);

        // All should be blocked
        Countly.sharedInstance().events().recordEvent("event with spaces");
        Assert.assertEquals(0, countlyStore.getEventQueueSize());

        Countly.sharedInstance().events().recordEvent("event-with-dashes");
        Assert.assertEquals(0, countlyStore.getEventQueueSize());

        Countly.sharedInstance().events().recordEvent("event_with_underscores");
        Assert.assertEquals(0, countlyStore.getEventQueueSize());

        // This should pass
        Countly.sharedInstance().events().recordEvent("normal_event");
        Assert.assertEquals(1, countlyStore.getEventQueueSize());
    }

    /**
     * Tests filter behavior with empty segmentation map.
     */
    @Test
    public void edgeCase_emptySegmentationMap() throws JSONException {
        Set<String> segBlacklist = new HashSet<>();
        segBlacklist.add("some_key");

        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().segmentationFilterList(segBlacklist, false).eventQueueSize(1).build()
        );
        Countly.sharedInstance().init(countlyConfig);

        // Record event with empty segmentation - should work fine
        Countly.sharedInstance().events().recordEvent("test_event", new HashMap<>());
        Assert.assertEquals(1, TestUtils.getCurrentRQ().length);
    }

    /**
     * Tests filter behavior with null segmentation.
     */
    @Test
    public void edgeCase_nullSegmentation() throws JSONException {
        Set<String> segBlacklist = new HashSet<>();
        segBlacklist.add("some_key");

        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().segmentationFilterList(segBlacklist, false).eventQueueSize(1).build()
        );
        Countly.sharedInstance().init(countlyConfig);

        // Record event with null segmentation - should work fine
        Countly.sharedInstance().events().recordEvent("test_event");
        Assert.assertEquals(1, TestUtils.getCurrentRQ().length);
    }

    /**
     * Tests that filter configuration update works during runtime.
     */
    @Test
    public void edgeCase_filterConfigurationRuntimeUpdate() throws JSONException {
        // Start with no filters
        Set<String> emptySet = new HashSet<>();
        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().eventFilterList(emptySet, false).build()
        );
        Countly.sharedInstance().init(countlyConfig);

        // Events should be allowed
        Countly.sharedInstance().events().recordEvent("test_event");
        Assert.assertEquals(1, countlyStore.getEventQueueSize());

        // Reinitialize with filter
        Countly.sharedInstance().halt();
        countlyStore.clear(); // Clear storage for fresh start

        Set<String> blacklist = new HashSet<>();
        blacklist.add("test_event");
        CountlyConfig config2 = TestUtils.createBaseConfig().enableManualSessionControl();
        config2.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().eventFilterList(blacklist, false).build()
        );
        Countly.sharedInstance().init(config2);

        // Now event should be blocked
        Countly.sharedInstance().events().recordEvent("test_event");
        Assert.assertEquals(0, countlyStore.getEventQueueSize()); // 0 because blocked

        // Different event should pass
        Countly.sharedInstance().events().recordEvent("other_event");
        Assert.assertEquals(1, countlyStore.getEventQueueSize());
    }

    /**
     * Tests multiple events with different filter rules applied correctly.
     */
    @Test
    public void edgeCase_multipleEventsWithDifferentFilters() throws JSONException {
        Map<String, Set<String>> eventSegFilterMap = new ConcurrentHashMap<>();

        Set<String> event1Filter = new HashSet<>();
        event1Filter.add("key_a");
        eventSegFilterMap.put("event1", event1Filter);

        Set<String> event2Filter = new HashSet<>();
        event2Filter.add("key_b");
        eventSegFilterMap.put("event2", event2Filter);

        CountlyConfig countlyConfig = TestUtils.createBaseConfig().enableManualSessionControl();
        countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(
            new ServerConfigBuilder().defaults().eventSegmentationFilterMap(eventSegFilterMap, false).eventQueueSize(1).build()
        );
        Countly.sharedInstance().init(countlyConfig);

        // event1: key_a should be blocked, key_b allowed
        Map<String, Object> seg1 = new ConcurrentHashMap<>();
        seg1.put("key_a", "value");
        seg1.put("key_b", "value");
        Countly.sharedInstance().events().recordEvent("event1", seg1);

        Assert.assertEquals(1, TestUtils.getCurrentRQ().length);
        validateEventInRQ("event1", TestUtils.map("key_b", "value"), 0, 1, 0, 1);

        // event2: key_b should be blocked, key_a allowed
        Map<String, Object> seg2 = new ConcurrentHashMap<>();
        seg2.put("key_a", "value");
        seg2.put("key_b", "value");
        Countly.sharedInstance().events().recordEvent("event2", seg2);

        Assert.assertEquals(2, TestUtils.getCurrentRQ().length);
        validateEventInRQ("event2", TestUtils.map("key_a", "value"), 1, 2, 0, 1);
    }
}
