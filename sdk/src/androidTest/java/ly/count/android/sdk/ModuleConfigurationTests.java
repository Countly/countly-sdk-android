package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
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

    @Before
    public void setUp() {
        countlyStore = TestUtils.getCountyStore();
        countlyStore.clear();
        Countly.sharedInstance().halt();
    }

    @After
    public void tearDown() {
        TestUtils.getCountyStore().clear();
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
     * Test default server configuration values
     */
    @Test
    public void serverConfig_DefaultValues() throws InterruptedException {
        countly = new Countly().init(TestUtils.createBaseConfig().setLoggingEnabled(false));
        Thread.sleep(2000); // simulate sdk initialization delay
        new ServerConfigBuilder().defaults().validateAgainst(countly);
    }

    /**
     * Test provided server configuration values
     */
    @Test
    public void serverConfig_ProvidedValues() throws InterruptedException, JSONException {
        initServerConfigWithValues(CountlyConfig::setServerConfiguration);
    }

    /**
     * Test server configuration values with immediate request generator
     */
    @Test
    public void serverConfig_WithImmediateRequestGenerator() throws InterruptedException, JSONException {
        initServerConfigWithValues((countlyConfig, serverConfig) -> {
            countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(serverConfig);
        });
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

        TestUtils.getCountyStore().setServerConfig(createStorageConfig(true, true, false));
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
        int configParameterCount = 26; // plus config, timestamp and version parameters
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
     *
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
     *
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
     *
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
     *
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
     *
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
     *
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

    private ImmediateRequestGenerator createIRGForSpecificResponse(final String targetResponse) {
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
            .traceLinesLimit(89);

        String serverConfig = builder.build();
        CountlyConfig countlyConfig = TestUtils.createBaseConfig().setLoggingEnabled(false);
        configSetter.accept(countlyConfig, serverConfig);

        countly = new Countly().init(countlyConfig);
        Thread.sleep(2000);

        builder.validateAgainst(countly);
    }

    private Map<String, Object> prepareServerConfig(Object... args) {
        ServerConfigBuilder builder = new ServerConfigBuilder()
            .defaults();

        Map<String, Object> serverConfig = builder.buildConfigMap();
        serverConfig.putAll(TestUtils.map(args));
        return serverConfig;
    }
}
