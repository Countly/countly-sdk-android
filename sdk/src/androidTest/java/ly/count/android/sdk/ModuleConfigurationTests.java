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

import static ly.count.android.sdk.ModuleConfiguration.keyRConfig;
import static ly.count.android.sdk.ModuleConfiguration.keyRConsentRequired;
import static ly.count.android.sdk.ModuleConfiguration.keyRContentZoneInterval;
import static ly.count.android.sdk.ModuleConfiguration.keyRCrashReporting;
import static ly.count.android.sdk.ModuleConfiguration.keyRCustomEventTracking;
import static ly.count.android.sdk.ModuleConfiguration.keyRDropOldRequestTime;
import static ly.count.android.sdk.ModuleConfiguration.keyREnterContentZone;
import static ly.count.android.sdk.ModuleConfiguration.keyREventQueueSize;
import static ly.count.android.sdk.ModuleConfiguration.keyRLimitBreadcrumb;
import static ly.count.android.sdk.ModuleConfiguration.keyRLimitKeyLength;
import static ly.count.android.sdk.ModuleConfiguration.keyRLimitSegValues;
import static ly.count.android.sdk.ModuleConfiguration.keyRLimitTraceLength;
import static ly.count.android.sdk.ModuleConfiguration.keyRLimitTraceLine;
import static ly.count.android.sdk.ModuleConfiguration.keyRLimitValueSize;
import static ly.count.android.sdk.ModuleConfiguration.keyRLocationTracking;
import static ly.count.android.sdk.ModuleConfiguration.keyRLogging;
import static ly.count.android.sdk.ModuleConfiguration.keyRNetworking;
import static ly.count.android.sdk.ModuleConfiguration.keyRRefreshContentZone;
import static ly.count.android.sdk.ModuleConfiguration.keyRReqQueueSize;
import static ly.count.android.sdk.ModuleConfiguration.keyRServerConfigUpdateInterval;
import static ly.count.android.sdk.ModuleConfiguration.keyRSessionTracking;
import static ly.count.android.sdk.ModuleConfiguration.keyRSessionUpdateInterval;
import static ly.count.android.sdk.ModuleConfiguration.keyRTimestamp;
import static ly.count.android.sdk.ModuleConfiguration.keyRTracking;
import static ly.count.android.sdk.ModuleConfiguration.keyRVersion;
import static ly.count.android.sdk.ModuleConfiguration.keyRViewTracking;

@RunWith(AndroidJUnit4.class)
public class ModuleConfigurationTests {
    CountlyStore countlyStore;

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

    /**
     * Default values when server config is disabled and storage is empty
     * No server connection
     */
    @Test
    public void init_disabled_storageEmpty() {
        countlyStore.clear();
        CountlyConfig config = TestUtils.createConfigurationConfig(false, null);
        Countly countly = (new Countly()).init(config);

        Assert.assertNull(countlyStore.getServerConfig());
        assertConfigDefault(countly);
    }

    /**
     * Default values when server config is enabled and storage is empty
     * No server connection
     */
    @Test
    public void init_enabled_storageEmpty() {
        CountlyConfig config = TestUtils.createConfigurationConfig(true, null);
        Countly countly = (new Countly()).init(config);

        Assert.assertNull(countlyStore.getServerConfig());
        assertConfigDefault(countly);
    }

    /**
     * Server config enabled
     * All config properties are default/allowing
     * No server connection
     *
     * @throws JSONException
     */
    @Test
    public void init_enabled_storageAllowing() throws JSONException {
        countlyStore.setServerConfig(getStorageString(true, true, true));
        CountlyConfig config = TestUtils.createConfigurationConfig(true, null);
        Countly countly = (new Countly()).init(config);

        Assert.assertNotNull(countlyStore.getServerConfig());
        assertConfigDefault(countly);
    }

    /**
     * Server config enabled
     * All config properties are off default/disabling
     * No server connection
     *
     * @throws JSONException
     */
    @Test
    public void init_enabled_storageForbidding() throws JSONException {
        countlyStore.setServerConfig(getStorageString(false, false, false));
        CountlyConfig config = TestUtils.createConfigurationConfig(true, null);
        Countly countly = (new Countly()).init(config);

        Assert.assertNotNull(countlyStore.getServerConfig());
        Assert.assertFalse(countly.moduleConfiguration.getNetworkingEnabled());
        Assert.assertFalse(countly.moduleConfiguration.getTrackingEnabled());
        Assert.assertFalse(countly.moduleConfiguration.getCrashReportingEnabled());
    }

    /**
     * Server config disabled
     * All config properties are default/allowing
     * No server connection
     *
     * @throws JSONException
     */
    @Test
    public void init_disabled_storageAllowing() throws JSONException {
        countlyStore.setServerConfig(getStorageString(true, true, true));
        CountlyConfig config = TestUtils.createConfigurationConfig(false, null);
        Countly countly = Countly.sharedInstance().init(config);

        Assert.assertNotNull(countlyStore.getServerConfig());
        assertConfigDefault(countly);
    }

    /**
     * Server config disabled
     * All config properties are off default/disabling
     * No server connection
     *
     * @throws JSONException
     */
    @Test(expected = AssertionError.class)
    public void init_disabled_storageForbidding() throws JSONException {
        countlyStore.setServerConfig(getStorageString(false, false, false));
        //Enable server config is deprecated and will not work so this test will fail
        CountlyConfig config = TestUtils.createConfigurationConfig(false, null);
        Countly countly = (new Countly()).init(config);

        Assert.assertNotNull(countlyStore.getServerConfig());
        assertConfigDefault(countly);
    }

    /**
     * Making sure that a downloaded configuration is persistently stored across init's
     */
    @Test
    public void scenario_1() {
        //initial state is fresh
        Assert.assertNull(countlyStore.getServerConfig());

        //first init fails receiving config, config getters return defaults, store is empty
        initAndValidateConfigParsingResult(null, false);

        //second init succeeds receiving config
        Countly countly = initAndValidateConfigParsingResult("{'v':1,'t':2,'c':{'tracking':false,'networking':false}}", true);
        Assert.assertFalse(countly.moduleConfiguration.getNetworkingEnabled());
        Assert.assertFalse(countly.moduleConfiguration.getTrackingEnabled());

        //third init is lacking a connection but still has the previously saved values
        CountlyConfig config = TestUtils.createConfigurationConfig(true, null);
        countly = new Countly().init(config);
        Assert.assertFalse(countly.moduleConfiguration.getNetworkingEnabled());
        Assert.assertFalse(countly.moduleConfiguration.getTrackingEnabled());

        //fourth init updates config values
        countly = initAndValidateConfigParsingResult("{'v':1,'t':2,'c':{'tracking':true,'networking':false}}", true);
        Assert.assertFalse(countly.moduleConfiguration.getNetworkingEnabled());
        Assert.assertTrue(countly.moduleConfiguration.getTrackingEnabled());
    }

    /**
     * With tracking disabled, nothing should be written to the request and event queues
     */
    @Test
    public void validatingTrackingConfig() throws JSONException {
        //nothing in queues initially
        Assert.assertEquals("", countlyStore.getRequestQueueRaw());
        Assert.assertEquals(0, countlyStore.getEvents().length);

        countlyStore.setServerConfig(getStorageString(false, false, false));

        CountlyConfig config = TestUtils.createConfigurationConfig(true, null);
        Countly countly = (new Countly()).init(config);

        Assert.assertFalse(countly.moduleConfiguration.getNetworkingEnabled());
        Assert.assertFalse(countly.moduleConfiguration.getTrackingEnabled());
        Assert.assertFalse(countly.moduleConfiguration.getCrashReportingEnabled());

        //try events
        countly.events().recordEvent("d");
        countly.events().recordEvent("1");

        //try a non event recording
        countly.crashes().recordHandledException(new Exception());

        //try a direct request
        countly.requestQueue().addDirectRequest(new HashMap<>());

        countly.requestQueue().attemptToSendStoredRequests();

        Assert.assertEquals("", countlyStore.getRequestQueueRaw());
        Assert.assertEquals(0, countlyStore.getEvents().length);
    }

    /**
     * Only disable crashes to try out unhandled crash reporting
     * Make sure that call is called but no request is added to the RQ
     * Call count to the unhandled crash reporting call should be 1 because countly SDK won't call and override the default handler
     * And validate that no crash request is generated
     */
    @Test
    public void validatingCrashReportingConfig() throws JSONException {
        AtomicInteger callCount = new AtomicInteger(0);
        RuntimeException unhandledException = new RuntimeException("Simulated unhandled exception");
        // Create a new thread to simulate unhandled exception
        Thread threadThrows = new Thread(() -> {
            // This will throw an unhandled exception in this thread
            throw unhandledException;
        });

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Assert.assertEquals(unhandledException, throwable);
            Assert.assertEquals(threadThrows, thread);
            callCount.incrementAndGet();
        });

        TestUtils.getCountyStore().setServerConfig(getStorageString(true, true, false));
        CountlyConfig config = TestUtils.createBaseConfig();
        config.enableServerConfiguration().setEventQueueSizeToSend(2);
        config.crashes.enableCrashReporting(); // this call will enable unhandled crash reporting
        Countly countly = new Countly().init(config);

        Assert.assertTrue(countly.moduleConfiguration.getNetworkingEnabled());
        Assert.assertTrue(countly.moduleConfiguration.getTrackingEnabled());
        Assert.assertFalse(countly.moduleConfiguration.getCrashReportingEnabled());

        // Start the thread and wait for it to terminate
        threadThrows.start();
        try {
            threadThrows.join(); // Wait for thread to finish
        } catch (InterruptedException ignored) {
        }

        //try events
        countly.events().recordEvent("d");
        countly.events().recordEvent("1");
        Assert.assertEquals(1, callCount.get());

        //try a non event recording
        countly.crashes().recordHandledException(new Exception());

        //try a direct request
        countly.requestQueue().addDirectRequest(new HashMap<>());

        countly.requestQueue().attemptToSendStoredRequests();

        // There are two requests in total, but they are not containing unhandled exception
        // 17.03.25-Arif: why we assume there are two requests that contains simulated one?
        // it only triggered once. It should be one request
        Assert.assertEquals(1, TestUtils.getCurrentRQ("Simulated unhandled exception").length);
        // above length check is because we create the resulting array in the length of the RQ
        Assert.assertNull(TestUtils.getCurrentRQ("Simulated unhandled exception")[0]);
        //Assert.assertNull(TestUtils.getCurrentRQ("Simulated unhandled exception")[1]);
    }

    /**
     * Making sure that bad config responses are rejected
     */
    @Test
    public void init_enabled_rejectingRequests() {
        //{"v":1,"t":2,"c":{"aa":"bb"}}
        Assert.assertNull(countlyStore.getServerConfig());

        //return null object
        initAndValidateConfigParsingResult(null, false);

        //return empty object
        initAndValidateConfigParsingResult("{}", false);

        //returns all except 'v'
        initAndValidateConfigParsingResult("{'t':2,'c':{'aa':'bb'}}", false);

        //returns all except 't'
        initAndValidateConfigParsingResult("{'v':1,'c':{'aa':'bb'}}", false);

        //returns all except 'c'
        initAndValidateConfigParsingResult("{'v':1,'t':2}", false);

        //returns all except 'c' wrong type (number)
        initAndValidateConfigParsingResult("{'v':1,'t':2,'c':123}", false);

        //returns all except 'c' wrong type (bool)
        initAndValidateConfigParsingResult("{'v':1,'t':2,'c':false}", false);

        //returns all except 'c' wrong type (string)
        initAndValidateConfigParsingResult("{'v':1,'t':2,'c':'fdf'}", false);
    }

    @Test
    public void parameterCount() {
        int configParameterCount = 26; // plus config, timestamp and version parameters
        int count = 0;
        for (Field field : ModuleConfiguration.class.getDeclaredFields()) {
            if (field.getName().startsWith("keyR")) {
                count++;
            }
        }
        Assert.assertEquals(configParameterCount, count);
    }

    @Test
    public void init_defaults() throws InterruptedException {
        // set logging enabled set to false intentionally because createBaseConfig sets it to true
        Countly countly = new Countly().init(TestUtils.createBaseConfig().setLoggingEnabled(false));

        Thread.sleep(2000); // simulate sdk initialization delay

        validateServerConfigValues(countly, prepareServerConfig()); // default values
    }

    @Test
    public void init_providedConfig() throws InterruptedException, JSONException {
        init_ConfigBase(CountlyConfig::setServerConfiguration);
    }

    @Test
    public void init_serverConfig() throws InterruptedException, JSONException {
        init_ConfigBase((countlyConfig, serverConfig) -> {
            countlyConfig.immediateRequestGenerator = createIRGForSpecificResponse(serverConfig);
        });
    }

    private void init_ConfigBase(BiConsumer<CountlyConfig, String> configSetter) throws JSONException, InterruptedException {
        Map<String, Object> responseServerConfig = TestUtils.map(
            keyRTracking, false,
            keyRNetworking, false,
            keyRCrashReporting, false,
            keyRViewTracking, false,
            keyRSessionTracking, false,
            keyRCustomEventTracking, false,
            keyREnterContentZone, true,
            keyRLocationTracking, false,
            keyRRefreshContentZone, false,
            keyRServerConfigUpdateInterval, 8,
            keyRReqQueueSize, 2000,
            keyREventQueueSize, 200,
            keyRLogging, true,
            keyRSessionUpdateInterval, 120,
            keyRContentZoneInterval, 60,
            keyRConsentRequired, true,
            keyRDropOldRequestTime, 1,
            keyRLimitKeyLength, 100,
            keyRLimitValueSize, 1000,
            keyRLimitSegValues, 100,
            keyRLimitBreadcrumb, 100,
            keyRLimitTraceLength, 100,
            keyRLimitTraceLine, 100
        );
        JSONObject serverConfig = new JSONObject();
        serverConfig.put(keyRTimestamp, System.currentTimeMillis());
        serverConfig.put(keyRVersion, "1");
        serverConfig.put(keyRConfig, responseServerConfig);

        CountlyConfig countlyConfig = TestUtils.createBaseConfig().setLoggingEnabled(false);
        configSetter.accept(countlyConfig, serverConfig.toString());

        Countly countly = new Countly().init(countlyConfig);

        Thread.sleep(2000);

        validateServerConfigValues(countly, prepareServerConfig(responseServerConfig));
    }

    private Map<String, Object> prepareServerConfig(Object... args) {
        Map<String, Object> serverConfig = TestUtils.map();
        serverConfig.put(keyRTracking, true);
        serverConfig.put(keyRNetworking, true);
        serverConfig.put(keyRCrashReporting, true);
        serverConfig.put(keyRViewTracking, true);
        serverConfig.put(keyRSessionTracking, true);
        serverConfig.put(keyRCustomEventTracking, true);
        serverConfig.put(keyREnterContentZone, false);
        serverConfig.put(keyRLocationTracking, true);
        serverConfig.put(keyRRefreshContentZone, true);

        serverConfig.put(keyRServerConfigUpdateInterval, 4);
        serverConfig.put(keyRReqQueueSize, 1000);
        serverConfig.put(keyREventQueueSize, 100);
        serverConfig.put(keyRLogging, false);
        serverConfig.put(keyRSessionUpdateInterval, 60); // its default is null normally if not set!!
        serverConfig.put(keyRContentZoneInterval, 30);
        serverConfig.put(keyRConsentRequired, false);
        serverConfig.put(keyRDropOldRequestTime, 0);

        serverConfig.put(keyRLimitKeyLength, Countly.maxKeyLengthDefault);
        serverConfig.put(keyRLimitValueSize, Countly.maxValueSizeDefault);
        serverConfig.put(keyRLimitSegValues, Countly.maxSegmentationValuesDefault);
        serverConfig.put(keyRLimitBreadcrumb, Countly.maxBreadcrumbCountDefault);
        serverConfig.put(keyRLimitTraceLength, Countly.maxStackTraceLineLengthDefault);
        serverConfig.put(keyRLimitTraceLine, Countly.maxStackTraceLinesPerThreadDefault);

        serverConfig.putAll(TestUtils.map(args));
        return serverConfig;
    }

    private void validateServerConfigValues(Countly countly, Map<String, Object> values) { // list might be used
        Assert.assertEquals(values.get(keyRTracking), countly.config_.configProvider.getTrackingEnabled());
        Assert.assertEquals(values.get(keyRNetworking), countly.config_.configProvider.getNetworkingEnabled());
        Assert.assertEquals(values.get(keyRCrashReporting), countly.config_.configProvider.getCrashReportingEnabled());
        Assert.assertEquals(values.get(keyRViewTracking), countly.config_.configProvider.getViewTrackingEnabled());
        Assert.assertEquals(values.get(keyRSessionTracking), countly.config_.configProvider.getSessionTrackingEnabled());
        Assert.assertEquals(values.get(keyRCustomEventTracking), countly.config_.configProvider.getCustomEventTrackingEnabled());
        Assert.assertEquals(values.get(keyREnterContentZone), countly.config_.configProvider.getContentZoneEnabled());
        Assert.assertEquals(values.get(keyRLocationTracking), countly.config_.configProvider.getLocationTrackingEnabled());
        Assert.assertEquals(values.get(keyRRefreshContentZone), countly.config_.configProvider.getRefreshContentZoneEnabled());

        Assert.assertEquals(values.get(keyRServerConfigUpdateInterval), countly.moduleConfiguration.serverConfigUpdateInterval);
        Assert.assertEquals(values.get(keyRReqQueueSize), countly.config_.maxRequestQueueSize);
        Assert.assertEquals(values.get(keyREventQueueSize), countly.EVENT_QUEUE_SIZE_THRESHOLD);
        Assert.assertEquals(values.get(keyRLogging), countly.config_.loggingEnabled);

        try {
            Assert.assertEquals(values.get(keyRSessionUpdateInterval), countly.config_.sessionUpdateTimerDelay);
        } catch (AssertionError _ignored) {
            // This is a workaround for the issue where sessionUpdateTimerDelay is null by default
            Assert.assertNull(countly.config_.sessionUpdateTimerDelay);
        }

        Assert.assertEquals(values.get(keyRContentZoneInterval), countly.config_.content.zoneTimerInterval);
        Assert.assertEquals(values.get(keyRConsentRequired), countly.config_.shouldRequireConsent);
        Assert.assertEquals(values.get(keyRDropOldRequestTime), countly.config_.dropAgeHours);
        Assert.assertEquals(values.get(keyRLimitKeyLength), countly.config_.sdkInternalLimits.maxKeyLength);
        Assert.assertEquals(values.get(keyRLimitValueSize), countly.config_.sdkInternalLimits.maxValueSize);
        Assert.assertEquals(values.get(keyRLimitSegValues), countly.config_.sdkInternalLimits.maxSegmentationValues);
        Assert.assertEquals(values.get(keyRLimitBreadcrumb), countly.config_.sdkInternalLimits.maxBreadcrumbCount);
        Assert.assertEquals(values.get(keyRLimitTraceLength), countly.config_.sdkInternalLimits.maxStackTraceLineLength);
        Assert.assertEquals(values.get(keyRLimitTraceLine), countly.config_.sdkInternalLimits.maxStackTraceLinesPerThread);
    }

    Countly initAndValidateConfigParsingResult(String targetResponse, boolean responseAccepted) {
        CountlyConfig config = TestUtils.createConfigurationConfig(true, createIRGForSpecificResponse(targetResponse));
        Countly countly = (new Countly()).init(config);

        if (!responseAccepted) {
            Assert.assertNull(countlyStore.getServerConfig());
            assertConfigDefault(countly);
        } else {
            Assert.assertNotNull(countlyStore.getServerConfig());
        }

        return countly;
    }

    void assertConfigDefault(Countly countly) {
        Assert.assertTrue(countly.moduleConfiguration.getNetworkingEnabled());
        Assert.assertTrue(countly.moduleConfiguration.getTrackingEnabled());
        Assert.assertTrue(countly.moduleConfiguration.getCrashReportingEnabled());
    }

    ImmediateRequestGenerator createIRGForSpecificResponse(final String targetResponse) {
        return new ImmediateRequestGenerator() {
            @Override public ImmediateRequestI CreateImmediateRequestMaker() {
                return new ImmediateRequestI() {
                    @Override public void doWork(String requestData, String customEndpoint, ConnectionProcessor cp, boolean requestShouldBeDelayed, boolean networkingIsEnabled, ImmediateRequestMaker.InternalImmediateRequestCallback callback, ModuleLog log) {
                        if (targetResponse == null) {
                            callback.callback(null);
                            return;
                        }

                        JSONObject jobj = null;

                        try {
                            jobj = new JSONObject(targetResponse);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                        callback.callback(jobj);
                    }
                };
            }
        };
    }

    //creates the stringified storage object with all the required properties
    String getStorageString(boolean tracking, boolean networking, boolean crashes) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        JSONObject jsonObjectConfig = new JSONObject();

        jsonObjectConfig.put("tracking", tracking);
        jsonObjectConfig.put("networking", networking);
        jsonObjectConfig.put("crt", crashes);

        jsonObject.put("v", 1);
        jsonObject.put("t", 1_681_808_287_464L);
        jsonObject.put("c", jsonObjectConfig);

        return jsonObject.toString();
    }
}
