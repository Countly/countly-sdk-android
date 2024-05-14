package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

@RunWith(AndroidJUnit4.class)
public class DeviceIdTests {
    CountlyStore store;

    OpenUDIDProvider openUDIDProvider;
    String currentOpenUDIDValue = "xxyyxx";

    @Before
    public void setUp() {
        Countly.sharedInstance().setLoggingEnabled(true);
        store = new CountlyStore(TestUtils.getContext(), mock(ModuleLog.class));
        store.clear();

        openUDIDProvider = new OpenUDIDProvider() {
            @Override public String getOpenUDID() {
                return currentOpenUDIDValue;
            }
        };
    }

    @After
    public void tearDown() {
        store.clear();
    }

    /**
     * Expecting exception to be thrown when initialising with a bad value
     * Hinting to be dev supplied but empty string
     */
    @Test(expected = IllegalStateException.class)
    public void constructorCustom_fail_1() {
        new DeviceId("", store, mock(ModuleLog.class), mock(OpenUDIDProvider.class));
    }

    /**
     * Checking temporary device ID mode is correctly recognised after init if set with "setId"
     * This would make sure that temp ID mode is verified mainly with the id string value
     */
    @Test
    public void temporaryIdModeEnabled_1() {
        DeviceId did = new DeviceId("dsd", store, mock(ModuleLog.class), mock(OpenUDIDProvider.class));
        assertFalse(did.isTemporaryIdModeEnabled());

        did.setId(DeviceIdType.OPEN_UDID, DeviceId.temporaryCountlyDeviceId);
        assertTrue(did.isTemporaryIdModeEnabled());

        did.setId(DeviceIdType.DEVELOPER_SUPPLIED, "ff");
        assertEquals("ff", did.getCurrentId());
        assertFalse(did.isTemporaryIdModeEnabled());

        did.setId(DeviceIdType.DEVELOPER_SUPPLIED, DeviceId.temporaryCountlyDeviceId);
        assertTrue(did.isTemporaryIdModeEnabled());

        did.setId(DeviceIdType.DEVELOPER_SUPPLIED, "12");
        assertEquals("12", did.getCurrentId());
        assertFalse(did.isTemporaryIdModeEnabled());

        did.setId(DeviceIdType.DEVELOPER_SUPPLIED, "34");
        assertEquals("34", did.getCurrentId());
        assertFalse(did.isTemporaryIdModeEnabled());

        did.setId(DeviceIdType.TEMPORARY_ID, DeviceId.temporaryCountlyDeviceId);
        assertTrue(did.isTemporaryIdModeEnabled());
    }

    /**
     * Checking setting temporary device ID mode during init
     */
    @Test
    public void temporaryIdModeEnabled_2() {
        DeviceId did2 = new DeviceId(DeviceId.temporaryCountlyDeviceId, store, mock(ModuleLog.class), mock(OpenUDIDProvider.class));
        assertTrue(did2.isTemporaryIdModeEnabled());

        //todo needs more work
    }

    /**
     * Validating that the correct type is returned after initialization
     */
    @Test
    public void getType() {
        assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, new DeviceId("dsd", store, mock(ModuleLog.class), mock(OpenUDIDProvider.class)).getType());
        store.clear();

        assertEquals(DeviceIdType.TEMPORARY_ID, new DeviceId(DeviceId.temporaryCountlyDeviceId, store, mock(ModuleLog.class), mock(OpenUDIDProvider.class)).getType());
        store.clear();

        assertEquals(DeviceIdType.OPEN_UDID, new DeviceId(null, store, mock(ModuleLog.class), new OpenUDIDProvider() {
            @Override public String getOpenUDID() {
                return "abc";
            }
        }).getType());
        store.clear();
    }

    /**
     * Validating that getId returns the expected value
     */
    @Test
    public void getId() {
        DeviceId did1 = new DeviceId("abc", store, mock(ModuleLog.class), mock(OpenUDIDProvider.class));
        assertEquals("abc", did1.getCurrentId());

        store.clear();

        DeviceId did2 = new DeviceId(DeviceId.temporaryCountlyDeviceId, store, mock(ModuleLog.class), mock(OpenUDIDProvider.class));
        assertEquals(DeviceId.temporaryCountlyDeviceId, did2.getCurrentId());

        store.clear();

        currentOpenUDIDValue = "ppp1";
        DeviceId did3 = new DeviceId(null, store, mock(ModuleLog.class), openUDIDProvider);
        assertEquals(currentOpenUDIDValue, did3.getCurrentId());
    }

    /**
     * Validating 'changeToId' with developer supplied values
     */
    @Test
    public void changeToCustomIdAndEnterTempIDMode() {
        DeviceId did = new DeviceId("abc", store, mock(ModuleLog.class), mock(OpenUDIDProvider.class));
        assertEquals("abc", did.getCurrentId());
        assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, did.getType());

        did.changeToCustomId("123");
        assertEquals("123", did.getCurrentId());
        assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, did.getType());

        did.enterTempIDMode();
        assertEquals(DeviceId.temporaryCountlyDeviceId, did.getCurrentId());
        assertEquals(DeviceIdType.TEMPORARY_ID, did.getType());

        did.changeToCustomId("aaa");
        assertEquals("aaa", did.getCurrentId());
        assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, did.getType());
    }

    /**
     * Validate if the values are stored and retrieved correctly
     * It should remember the previously set value and should not change during future init's
     *
     * First provided ID is developer supplied
     */
    @Test
    public void initWithDifferentValuesStartingDevID() {
        store.clear();

        DeviceId did = new DeviceId("abc", store, mock(ModuleLog.class), mock(OpenUDIDProvider.class));
        assertEquals("abc", did.getCurrentId());
        assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, did.getType());

        did = new DeviceId("123", store, mock(ModuleLog.class), mock(OpenUDIDProvider.class));
        assertEquals("abc", did.getCurrentId());
        assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, did.getType());

        did = new DeviceId(null, store, mock(ModuleLog.class), mock(OpenUDIDProvider.class));
        assertEquals("abc", did.getCurrentId());
        assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, did.getType());

        did = new DeviceId(DeviceId.temporaryCountlyDeviceId, store, mock(ModuleLog.class), mock(OpenUDIDProvider.class));
        assertEquals("abc", did.getCurrentId());
        assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, did.getType());
    }

    /**
     * Validate if the values are stored and retrieved correctly
     * It should remember the previously set value and should not change during future init's
     *
     * First provided ID is OpenUDID generated
     */
    @Test
    public void initWithDifferentValuesStartingOpenUDID() {
        currentOpenUDIDValue = "nmb";
        DeviceId did = new DeviceId(null, store, mock(ModuleLog.class), openUDIDProvider);
        assertEquals(currentOpenUDIDValue, did.getCurrentId());
        assertEquals(DeviceIdType.OPEN_UDID, did.getType());

        did = new DeviceId("123", store, mock(ModuleLog.class), openUDIDProvider);
        assertEquals(currentOpenUDIDValue, did.getCurrentId());
        assertEquals(DeviceIdType.OPEN_UDID, did.getType());

        did = new DeviceId(null, store, mock(ModuleLog.class), openUDIDProvider);
        assertEquals(currentOpenUDIDValue, did.getCurrentId());
        assertEquals(DeviceIdType.OPEN_UDID, did.getType());

        did = new DeviceId(DeviceId.temporaryCountlyDeviceId, store, mock(ModuleLog.class), openUDIDProvider);
        assertEquals(currentOpenUDIDValue, did.getCurrentId());
        assertEquals(DeviceIdType.OPEN_UDID, did.getType());
    }

    /**
     * Initially acquired openUDID value should remain stable
     * If during the second init it would generate a different value, it should still use the first one.
     */
    @Test
    public void reinitOpenUDID_differentValue() {
        currentOpenUDIDValue = "nmb";
        String initialValue = currentOpenUDIDValue;

        //init the first time and openUDID returns one value
        DeviceId did = new DeviceId(null, store, mock(ModuleLog.class), openUDIDProvider);
        assertEquals(currentOpenUDIDValue, did.getCurrentId());
        assertEquals(DeviceIdType.OPEN_UDID, did.getType());

        //init the second time and openUDID returns a different value
        currentOpenUDIDValue = "zxc";
        did = new DeviceId(null, store, mock(ModuleLog.class), openUDIDProvider);
        assertEquals(initialValue, did.getCurrentId());
        assertEquals(DeviceIdType.OPEN_UDID, did.getType());
    }

    /**
     * In case a device ID type sneaks past the migration script, there should be a fallback to the OPEN_UDID type
     */
    @Test
    public void legacyFallbackType_AdvertisingID() {
        store.clear();

        store.setDeviceID("xxx");
        store.setDeviceIDType(MigrationHelper.legacyDeviceIDTypeValue_AdvertisingID);

        DeviceId did = new DeviceId("123", store, mock(ModuleLog.class), openUDIDProvider);
        assertEquals("xxx", did.getCurrentId());
        assertEquals(DeviceIdType.OPEN_UDID, did.getType());
    }

    /**
     * In case a device ID type becomes 'null', there should be a fallback to the OPEN_UDID type
     */
    @Test
    public void legacyFallbackType_null() {
        store.clear();
        store.setDeviceID("xxx");

        DeviceId did = new DeviceId("123", store, mock(ModuleLog.class), openUDIDProvider);
        assertEquals("xxx", did.getCurrentId());
        assertEquals(DeviceIdType.OPEN_UDID, did.getType());
    }

    /**
     * Case where multiple device ID change calls affect the session duration
     * This is a scenario where the session duration is correctly calculated and sent
     * ---
     * The session duration is calculated based on the time between the last two device ID changes
     * And for every device ID change, a session duration is sent flow should be like this:
     * ---
     * set a couple of user properties
     * sleep 1 second
     * change device ID with merge (creates a session duration request with the duration of 1 second)
     * save user profiles
     * sleep 2 seconds
     * change device ID without merge (creates a session duration request with the duration of 2 seconds)
     * -- at this point 4 requests are generated (firs begin, 1 merge, 1 user profile, 1 end session for without merge)
     * sleep 1 second
     * set user property and save it
     * change device ID without merge with same device id (no session duration request)
     * set user properties
     * sleep 1 second
     * change device ID without merge with same device id (no session duration request)
     * save user properties
     * change device ID without merge with same device id (no session duration request)
     * sleep 1 second
     * set user properties and save them
     * sleep 1 second
     * change device ID with merge (creates a session duration request with the duration of 4 seconds)
     * -- at this point 8 requests are generated (firs begin, 1 merge, 1 user profile, 1 end session for without merge, 1 user profile, 1 user profile, 1 user profile, 1 merge)
     */
    @Test
    public void sessionDurationScenario_1() throws InterruptedException {
        CountlyConfig config = TestUtils.createBaseConfig();
        config.setRequiresConsent(false);
        config.lifecycleObserver = () -> true;

        Countly countly = new Countly().init(config);
        validateSessionRequest(0, null, null, false);

        countly.userProfile().setProperty("prop1", "string");
        countly.userProfile().setProperty("prop2", 123);
        countly.userProfile().setProperty("prop3", false);

        Thread.sleep(1000);

        countly.deviceId().changeWithMerge("ff_merge"); // this will generate a request with "session_duration" field and reset duration
        countly.userProfile().save();

        Thread.sleep(2000);

        countly.deviceId().changeWithoutMerge("ff"); // this will generate a request with "end_session", "session_duration" fields and reset duration
        Assert.assertEquals(4, TestUtils.getCurrentRQ().length);
        validateSessionRequest(1, 1, "ff_merge", false);
        validateSessionRequest(3, 2, null, true);

        Thread.sleep(1000);

        countly.userProfile().setProperty("prop4", new String[] { "sd" });
        countly.userProfile().save();
        countly.deviceId().changeWithoutMerge("ff"); // this will not affect the session duration
        countly.userProfile().setProperty("prop5", new HashMap<String, Object>() {{
            put("key", "value");
        }});
        countly.userProfile().setProperty("prop6", new HashMap<String, Object>() {{
            put("key", 123);
        }});
        countly.userProfile().setProperty("prop7", new HashMap<String, Object>() {{
            put("key", false);
        }});

        Thread.sleep(1000);

        countly.deviceId().changeWithoutMerge("ff"); // this will not affect the session duration
        countly.userProfile().save();
        countly.deviceId().changeWithoutMerge("ff"); // this will not affect the session duration

        Thread.sleep(1000);

        countly.userProfile().setProperty("prop1", "string_a");
        countly.userProfile().setProperty("prop2", 456);
        countly.userProfile().setProperty("prop3", true);
        countly.userProfile().save();

        Thread.sleep(1000);

        countly.deviceId().changeWithMerge("ff_merge"); // this will generate a request with "session_duration" field and reset duration

        Assert.assertEquals(8, TestUtils.getCurrentRQ().length);
        validateSessionRequest(7, 4, "ff_merge", false);
    }

    void validateSessionRequest(int idx, Integer duration, String deviceId, boolean endSession) {
        Map<String, String> request = TestUtils.getCurrentRQ()[idx];

        if (deviceId != null) {
            TestUtils.validateRequiredParams(request, deviceId);
        } else {
            TestUtils.validateRequiredParams(request);
        }

        if (endSession) {
            assertTrue(request.containsKey("end_session"));
        }

        if (duration != null) {
            assertEquals(duration, Integer.valueOf(request.get("session_duration")));
        } else {
            assertTrue(request.containsKey("begin_session"));
        }
    }
}
