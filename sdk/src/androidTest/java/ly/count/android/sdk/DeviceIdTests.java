package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Collections;
import org.json.JSONArray;
import org.json.JSONException;
import org.junit.After;
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
        store = TestUtils.getCountlyStore();
        store.clear();

        Countly.sharedInstance().halt();
        Countly.sharedInstance().setLoggingEnabled(true);

        openUDIDProvider = new OpenUDIDProvider() {
            @Override public String getUUID() {
                return currentOpenUDIDValue;
            }
        };
    }

    @After
    public void tearDown() {
        store.clear();

        Countly.sharedInstance().halt();
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
            @Override public String getUUID() {
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
     * change device ID with merge
     * save user profiles
     * sleep 2 seconds
     * change device ID without merge (creates a session duration request with the duration of 3 seconds) and will generate new begin session because consent not required
     * -- at this point 4 requests are generated (firs begin, 1 merge, 1 user profile, 1 end session for without merge, 1 begin session for the new user)
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
     * -- at this point 9 requests are generated (firs begin, 1 merge, 1 user profile, 1 end session for without merge, 1 begin session for the new user, 1 user profile, 1 user profile, 1 user profile, 1 merge)
     */
    @Test
    public void sessionDurationScenario_1() throws InterruptedException, JSONException {
        CountlyConfig config = TestUtils.createBaseConfig();
        config.setRequiresConsent(false);
        config.lifecycleObserver = () -> true;

        Countly countly = new Countly().init(config);
        ModuleSessionsTests.validateSessionBeginRequest(0, TestUtils.commonDeviceId);

        countly.userProfile().setProperty("prop1", "string");
        countly.userProfile().setProperty("prop2", 123);
        countly.userProfile().setProperty("prop3", false);

        Thread.sleep(1000);

        countly.deviceId().changeWithMerge("ff_merge");
        countly.userProfile().save();

        Thread.sleep(2000);

        countly.deviceId().changeWithoutMerge("ff"); // this will generate a request with "end_session", "session_duration" fields and reset duration + begin_session
        assertEquals(6, TestUtils.getCurrentRQ().length); // not 5 anymore, it will send orientation event as well

        TestUtils.validateRequest("ff_merge", TestUtils.map("old_device_id", "1234"), 1);
        ModuleEventsTests.validateEventInRQ("ff_merge", "[CLY]_orientation", null, 1, 0.0d, 0.0d, "_CLY_", "_CLY_", "_CLY_", "_CLY_", 2, -1, 0, 1);
        ModuleUserProfileTests.validateUserProfileRequest("ff_merge", 3, 6, TestUtils.map(), TestUtils.map("prop2", 123, "prop1", "string", "prop3", false));
        ModuleSessionsTests.validateSessionEndRequest(4, 3, "ff_merge");

        Thread.sleep(1000);

        countly.userProfile().setProperty("prop4", Collections.singletonList("sd"));
        countly.userProfile().save();
        countly.deviceId().changeWithoutMerge("ff"); // this will not affect the session duration
        countly.userProfile().setProperty("prop5", TestUtils.map("key", "value"));
        countly.userProfile().setProperty("prop6", TestUtils.map("key", 123));
        countly.userProfile().setProperty("prop7", TestUtils.map("key", false));

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

        assertEquals(11, TestUtils.getCurrentRQ().length);

        ModuleUserProfileTests.validateUserProfileRequest("ff", 7, 11, TestUtils.map(), TestUtils.map("prop4", new JSONArray(Collections.singletonList("sd"))));
        ModuleUserProfileTests.validateUserProfileRequest("ff", 8, 11, TestUtils.map(), TestUtils.map());
        ModuleUserProfileTests.validateUserProfileRequest("ff", 9, 11, TestUtils.map(), TestUtils.map("prop2", 456, "prop1", "string_a", "prop3", true));

        TestUtils.validateRequest("ff_merge", TestUtils.map("old_device_id", "ff"), 10);

        countly.halt();
        store.clear();
    }
}
