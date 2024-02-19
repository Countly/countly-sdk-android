package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.HashMap;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.mockito.Mockito.mock;

@RunWith(AndroidJUnit4.class)
public class ModuleConfigurationTests {
    CountlyStore countlyStore;

    @Before
    public void setUp() {
        countlyStore = new CountlyStore(getContext(), mock(ModuleLog.class));
        countlyStore.clear();
    }

    @After
    public void tearDown() {
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

        Assert.assertFalse(countly.moduleConfiguration.serverConfigEnabled);
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

        Assert.assertTrue(countly.moduleConfiguration.serverConfigEnabled);
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
        countlyStore.setServerConfig(getStorageString(true, true));
        CountlyConfig config = TestUtils.createConfigurationConfig(true, null);
        Countly countly = (new Countly()).init(config);

        Assert.assertTrue(countly.moduleConfiguration.serverConfigEnabled);
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
        countlyStore.setServerConfig(getStorageString(false, false));
        CountlyConfig config = TestUtils.createConfigurationConfig(true, null);
        Countly countly = (new Countly()).init(config);

        Assert.assertTrue(countly.moduleConfiguration.serverConfigEnabled);
        Assert.assertNotNull(countlyStore.getServerConfig());
        Assert.assertFalse(countly.moduleConfiguration.getNetworkingEnabled());
        Assert.assertFalse(countly.moduleConfiguration.getTrackingEnabled());
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
        countlyStore.setServerConfig(getStorageString(true, true));
        CountlyConfig config = TestUtils.createConfigurationConfig(false, null);
        Countly countly = Countly.sharedInstance().init(config);

        Assert.assertFalse(countly.moduleConfiguration.serverConfigEnabled);
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
    @Test
    public void init_disabled_storageForbidding() throws JSONException {
        countlyStore.setServerConfig(getStorageString(false, false));
        CountlyConfig config = TestUtils.createConfigurationConfig(false, null);
        Countly countly = (new Countly()).init(config);

        Assert.assertFalse(countly.moduleConfiguration.serverConfigEnabled);
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
        countly = (new Countly()).init(config);
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

        countlyStore.setServerConfig(getStorageString(false, false));

        CountlyConfig config = TestUtils.createConfigurationConfig(true, null);
        Countly countly = (new Countly()).init(config);

        Assert.assertFalse(countly.moduleConfiguration.getNetworkingEnabled());
        Assert.assertFalse(countly.moduleConfiguration.getTrackingEnabled());

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
    String getStorageString(boolean tracking, boolean networking) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        JSONObject jsonObjectConfig = new JSONObject();

        jsonObjectConfig.put("tracking", tracking);
        jsonObjectConfig.put("networking", networking);

        jsonObject.put("v", 1);
        jsonObject.put("t", 1_681_808_287_464L);
        jsonObject.put("c", jsonObjectConfig);

        return jsonObject.toString();
    }
}
