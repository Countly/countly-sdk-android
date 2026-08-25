package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.mockito.Mockito.mock;

@RunWith(AndroidJUnit4.class)
public class ScenarioDeviceIdInitTests {
    CountlyStore countlyStore;

    @Before
    public void setUp() {
        countlyStore = new CountlyStore(TestUtils.getContext(), mock(ModuleLog.class));
    }

    @After
    public void tearDown() {
    }

    //first init

    /**
     * First init where:
     * Device ID is not provided,
     * Temporary ID mode is not provided
     *
     * SDK should generate OPEN_UDID device ID
     */
    @Test
    public void firstInitProvidedNothing() {
        countlyStore.clear();
        CountlyConfig cc = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");
        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertNotNull(countly.deviceId().getID());
        Assert.assertEquals(DeviceIdType.OPEN_UDID, countly.deviceId().getType());
    }

    /**
     * First init where:
     * Custom Device ID is provided,
     * Temporary ID mode is not provided
     *
     * SDK should use provided device ID
     */
    @Test
    public void firstInitProvidedCustomId() {
        countlyStore.clear();
        CountlyConfig cc = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");
        cc.setDeviceId("qwe123");

        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertEquals("qwe123", countly.deviceId().getID());
        Assert.assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, countly.deviceId().getType());
    }

    /**
     * First init where:
     * Custom Device ID is not provided,
     * Temporary ID mode is  provided
     *
     * SDK should enable temporary device ID mode
     */
    @Test
    public void firstInitProvidedTempId() {
        countlyStore.clear();
        CountlyConfig cc = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");
        cc.enableTemporaryDeviceIdMode();

        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertEquals(DeviceId.temporaryCountlyDeviceId, countly.deviceId().getID());
        Assert.assertEquals(DeviceIdType.TEMPORARY_ID, countly.deviceId().getType());
    }

    //Followup inits

    /**
     * Followup init where previously:
     * Custom devices ID was set
     *
     * now:
     * Device ID is not provided,
     * Temporary ID mode is not provided
     */
    @Test
    public void followupInitPrevCustomProvidedNothing() {
        countlyStore.clear();

        //setup initial state
        CountlyConfig configInitial = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");
        configInitial.setDeviceId("hjk");

        Countly cInitial = new Countly();
        cInitial.init(configInitial);

        String initialDId = cInitial.deviceId().getID();

        Assert.assertEquals("hjk", cInitial.deviceId().getID());
        Assert.assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, cInitial.deviceId().getType());

        //setup followup state
        CountlyConfig cc = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");

        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertEquals(initialDId, countly.deviceId().getID());
        Assert.assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, countly.deviceId().getType());
    }

    /**
     * Followup init where previously:
     * Custom devices ID was set
     *
     * now:
     * Device ID is provided,
     * Temporary ID mode is not provided
     */
    @Test
    public void followupInitPrevCustomProvidedCustomId() {
        countlyStore.clear();

        //setup initial state
        CountlyConfig configInitial = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");
        configInitial.setDeviceId("hjk");

        Countly cInitial = new Countly();
        cInitial.init(configInitial);

        String initialDId = cInitial.deviceId().getID();

        Assert.assertEquals("hjk", cInitial.deviceId().getID());
        Assert.assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, cInitial.deviceId().getType());

        //setup followup state
        CountlyConfig cc = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");
        cc.setDeviceId("zxc");

        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertEquals(initialDId, countly.deviceId().getID());
        Assert.assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, countly.deviceId().getType());
    }

    /**
     * Followup init where previously:
     * Custom devices ID was set
     *
     * now:
     * Device ID is not provided,
     * Temporary ID mode is provided
     */
    @Test
    public void followupInitPrevCustomProvidedTempId() {
        countlyStore.clear();

        //setup initial state
        CountlyConfig configInitial = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");
        configInitial.setDeviceId("hjk");

        Countly cInitial = new Countly();
        cInitial.init(configInitial);

        String initialDId = cInitial.deviceId().getID();

        Assert.assertEquals("hjk", cInitial.deviceId().getID());
        Assert.assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, cInitial.deviceId().getType());

        //setup followup state
        CountlyConfig cc = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");
        cc.enableTemporaryDeviceIdMode();

        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertEquals(initialDId, countly.deviceId().getID());
        Assert.assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, countly.deviceId().getType());
    }

    /**
     * Followup init where previously:
     * Custom devices ID was set
     *
     * now:
     * Device ID is provided,
     * Temporary ID mode is provided
     */
    @Test
    public void followupInitPrevCustomProvidedCustomIdTempId() {
        countlyStore.clear();

        //setup initial state
        final String specificDeviceId = "hjk";
        CountlyConfig configInitial = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");
        configInitial.setDeviceId(specificDeviceId);

        Countly cInitial = new Countly();
        cInitial.init(configInitial);

        String initialDId = cInitial.deviceId().getID();

        Assert.assertEquals(specificDeviceId, cInitial.deviceId().getID());
        Assert.assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, cInitial.deviceId().getType());

        //setup followup state
        CountlyConfig cc = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");
        cc.enableTemporaryDeviceIdMode();
        cc.setDeviceId("890");

        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertEquals(initialDId, countly.deviceId().getID());
        Assert.assertEquals(specificDeviceId, countly.deviceId().getID());
        Assert.assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, countly.deviceId().getType());
    }

    /**
     * Followup init where previously:
     * Nothing was provided - OPEN_UDID Devices ID was generated
     *
     * now:
     * Device ID is not provided,
     * Temporary ID mode is not provided
     */
    @Test
    public void followupInitPrevNothingProvidedNothing() {
        countlyStore.clear();

        //setup initial state
        CountlyConfig configInitial = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");

        Countly cInitial = new Countly();
        cInitial.init(configInitial);

        String initialDId = cInitial.deviceId().getID();

        Assert.assertNotNull(cInitial.deviceId().getID());
        Assert.assertEquals(DeviceIdType.OPEN_UDID, cInitial.deviceId().getType());

        //setup followup state
        CountlyConfig cc = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");

        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertEquals(initialDId, countly.deviceId().getID());
        Assert.assertEquals(DeviceIdType.OPEN_UDID, countly.deviceId().getType());
    }

    /**
     * Followup init where previously:
     * Nothing was provided - OPEN_UDID Devices ID was generated
     *
     * now:
     * Device ID is provided,
     * Temporary ID mode is not provided
     */
    @Test
    public void followupInitPrevNothingProvidedCustomId() {
        countlyStore.clear();

        //setup initial state
        CountlyConfig configInitial = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");

        Countly cInitial = new Countly();
        cInitial.init(configInitial);

        String initialDId = cInitial.deviceId().getID();

        Assert.assertNotNull(cInitial.deviceId().getID());
        Assert.assertEquals(DeviceIdType.OPEN_UDID, cInitial.deviceId().getType());

        //setup followup state
        CountlyConfig cc = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");
        cc.setDeviceId("1qwe");

        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertEquals(initialDId, countly.deviceId().getID());
        Assert.assertEquals(DeviceIdType.OPEN_UDID, countly.deviceId().getType());
    }

    /**
     * Followup init where previously:
     * Nothing was provided - OPEN_UDID Devices ID was generated
     *
     * now:
     * Device ID is not provided,
     * Temporary ID mode is provided
     */
    @Test
    public void followupInitPrevNothingProvidedTempId() {
        countlyStore.clear();

        //setup initial state
        CountlyConfig configInitial = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");

        Countly cInitial = new Countly();
        cInitial.init(configInitial);

        String initialDId = cInitial.deviceId().getID();

        Assert.assertNotNull(cInitial.deviceId().getID());
        Assert.assertEquals(DeviceIdType.OPEN_UDID, cInitial.deviceId().getType());

        //setup followup state
        CountlyConfig cc = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");
        cc.enableTemporaryDeviceIdMode();

        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertEquals(initialDId, countly.deviceId().getID());
        Assert.assertEquals(DeviceIdType.OPEN_UDID, countly.deviceId().getType());
    }

    /**
     * Followup init where previously:
     * Nothing was provided - OPEN_UDID Devices ID was generated
     *
     * now:
     * Device ID is provided,
     * Temporary ID mode is provided
     */
    @Test
    public void followupInitPrevNothingProvidedCustomIdTempId() {
        countlyStore.clear();

        //setup initial state
        CountlyConfig configInitial = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");

        Countly cInitial = new Countly();
        cInitial.init(configInitial);

        String initialDId = cInitial.deviceId().getID();

        Assert.assertNotNull(cInitial.deviceId().getID());
        Assert.assertEquals(DeviceIdType.OPEN_UDID, cInitial.deviceId().getType());

        //setup followup state
        CountlyConfig cc = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");

        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertEquals(initialDId, countly.deviceId().getID());
        Assert.assertEquals(DeviceIdType.OPEN_UDID, countly.deviceId().getType());
    }

    /**
     * Followup init where previously:
     * Temporary ID was provided
     *
     * now:
     * Device ID is not provided,
     * Temporary ID mode is not provided
     * Device should remain in temp ID mode
     */
    @Test
    public void followupInitPrevTempIdProvidedNothing() {
        countlyStore.clear();

        //setup initial state
        CountlyConfig configInitial = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");
        configInitial.enableTemporaryDeviceIdMode();
        configInitial.setLoggingEnabled(true);

        Countly cInitial = new Countly();
        cInitial.init(configInitial);

        String initialDId = cInitial.deviceId().getID();

        Assert.assertNotNull(cInitial.deviceId().getID());
        Assert.assertEquals(DeviceIdType.TEMPORARY_ID, cInitial.deviceId().getType());

        //setup followup state
        CountlyConfig cc = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");
        cc.setLoggingEnabled(true);

        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertEquals(initialDId, countly.deviceId().getID());
        Assert.assertEquals(DeviceIdType.TEMPORARY_ID, countly.deviceId().getType());
    }

    /**
     * Followup init where previously:
     * Temporary ID was provided
     *
     * now:
     * Device ID is provided,
     * Temporary ID mode is not provided
     */
    @Test
    public void followupInitPrevTempIdProvidedCustomId() {
        countlyStore.clear();

        //setup initial state
        CountlyConfig configInitial = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");
        configInitial.enableTemporaryDeviceIdMode();

        Countly cInitial = new Countly();
        cInitial.init(configInitial);

        Assert.assertNotNull(cInitial.deviceId().getID());
        Assert.assertEquals(DeviceIdType.TEMPORARY_ID, cInitial.deviceId().getType());

        //setup followup state
        CountlyConfig cc = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");
        cc.setDeviceId("uio");

        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertEquals("uio", countly.deviceId().getID());
        Assert.assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, countly.deviceId().getType());
    }

    /**
     * Followup init where previously:
     * Temporary ID was provided
     *
     * now:
     * Device ID is not provided,
     * Temporary ID mode is provided
     */
    @Test
    public void followupInitPrevTempIdProvidedTempId() {
        countlyStore.clear();

        //setup initial state
        CountlyConfig configInitial = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");
        configInitial.enableTemporaryDeviceIdMode();

        Countly cInitial = new Countly();
        cInitial.init(configInitial);

        String initialDId = cInitial.deviceId().getID();

        Assert.assertNotNull(cInitial.deviceId().getID());
        Assert.assertEquals(DeviceIdType.TEMPORARY_ID, cInitial.deviceId().getType());

        //setup followup state
        CountlyConfig cc = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");
        cc.enableTemporaryDeviceIdMode();

        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertEquals(initialDId, countly.deviceId().getID());
        Assert.assertEquals(DeviceIdType.TEMPORARY_ID, countly.deviceId().getType());
    }

    /**
     * Followup init where previously:
     * Temporary ID was provided
     *
     * now:
     * Device ID is provided,
     * Temporary ID mode is provided
     */
    @Test
    public void followupInitPrevTempIdProvidedCustomIdTempId() {
        countlyStore.clear();

        //setup initial state
        CountlyConfig configInitial = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");
        configInitial.enableTemporaryDeviceIdMode();

        Countly cInitial = new Countly();
        cInitial.init(configInitial);

        String initialDId = cInitial.deviceId().getID();

        Assert.assertNotNull(cInitial.deviceId().getID());
        Assert.assertEquals(DeviceIdType.TEMPORARY_ID, cInitial.deviceId().getType());

        //setup followup state
        CountlyConfig cc = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");
        cc.enableTemporaryDeviceIdMode();
        cc.setDeviceId("frt");

        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertEquals("frt", countly.deviceId().getID());
        Assert.assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, countly.deviceId().getType());
    }

    //Followup inits with the stored device ID cleared

    /**
     * Followup init where previously:
     * Custom device ID was set
     *
     * now:
     * Stored device ID clearing is enabled,
     * Device ID is provided,
     * Temporary ID mode is not provided
     *
     * SDK should drop the stored ID and use the newly provided one
     */
    @Test
    public void followupInitClearStoredIdProvidedCustomId() {
        countlyStore.clear();

        //setup initial state
        CountlyConfig configInitial = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");
        configInitial.setDeviceId("hjk");

        Countly cInitial = new Countly();
        cInitial.init(configInitial);

        Assert.assertEquals("hjk", cInitial.deviceId().getID());
        Assert.assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, cInitial.deviceId().getType());

        //setup followup state
        CountlyConfig cc = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");
        cc.enableClearStoredDeviceId();
        cc.setDeviceId("zxc");

        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertEquals("zxc", countly.deviceId().getID());
        Assert.assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, countly.deviceId().getType());
    }

    /**
     * Followup init where previously:
     * Nothing was provided - OPEN_UDID device ID was generated
     *
     * now:
     * Stored device ID clearing is enabled,
     * Device ID is not provided,
     * Temporary ID mode is not provided
     *
     * SDK should generate a new OPEN_UDID device ID instead of reusing the previous one
     */
    @Test
    public void followupInitClearStoredIdProvidedNothing() {
        countlyStore.clear();

        //setup initial state
        CountlyConfig configInitial = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");

        Countly cInitial = new Countly();
        cInitial.init(configInitial);

        String initialDId = cInitial.deviceId().getID();

        Assert.assertNotNull(initialDId);
        Assert.assertEquals(DeviceIdType.OPEN_UDID, cInitial.deviceId().getType());

        //setup followup state
        CountlyConfig cc = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");
        cc.enableClearStoredDeviceId();

        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertNotNull(countly.deviceId().getID());
        Assert.assertNotEquals(initialDId, countly.deviceId().getID());
        Assert.assertEquals(DeviceIdType.OPEN_UDID, countly.deviceId().getType());
    }

    /**
     * Followup init where previously:
     * Temporary ID was provided
     *
     * now:
     * Stored device ID clearing is enabled,
     * Device ID is not provided,
     * Temporary ID mode is not provided
     *
     * SDK should leave temporary ID mode and generate an OPEN_UDID device ID
     */
    @Test
    public void followupInitClearStoredIdPrevTempIdProvidedNothing() {
        countlyStore.clear();

        //setup initial state
        CountlyConfig configInitial = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");
        configInitial.enableTemporaryDeviceIdMode();

        Countly cInitial = new Countly();
        cInitial.init(configInitial);

        Assert.assertEquals(DeviceId.temporaryCountlyDeviceId, cInitial.deviceId().getID());
        Assert.assertEquals(DeviceIdType.TEMPORARY_ID, cInitial.deviceId().getType());

        //setup followup state
        CountlyConfig cc = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");
        cc.enableClearStoredDeviceId();

        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertNotNull(countly.deviceId().getID());
        Assert.assertNotEquals(DeviceId.temporaryCountlyDeviceId, countly.deviceId().getID());
        Assert.assertEquals(DeviceIdType.OPEN_UDID, countly.deviceId().getType());
    }

    /**
     * Followup init where previously:
     * Custom device ID was set
     *
     * now:
     * Stored device ID clearing is enabled,
     * Device ID is not provided,
     * Temporary ID mode is provided
     *
     * SDK should enter temporary ID mode because the stored ID no longer blocks it
     */
    @Test
    public void followupInitClearStoredIdProvidedTempId() {
        countlyStore.clear();

        //setup initial state
        CountlyConfig configInitial = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");
        configInitial.setDeviceId("hjk");

        Countly cInitial = new Countly();
        cInitial.init(configInitial);

        Assert.assertEquals("hjk", cInitial.deviceId().getID());
        Assert.assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, cInitial.deviceId().getType());

        //setup followup state
        CountlyConfig cc = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");
        cc.enableClearStoredDeviceId();
        cc.enableTemporaryDeviceIdMode();

        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertEquals(DeviceId.temporaryCountlyDeviceId, countly.deviceId().getID());
        Assert.assertEquals(DeviceIdType.TEMPORARY_ID, countly.deviceId().getType());
    }

    /**
     * First init where:
     * Stored device ID clearing is enabled but nothing was stored yet,
     * Custom Device ID is provided
     *
     * SDK should behave the same as a plain first init and use the provided device ID
     */
    @Test
    public void firstInitClearStoredIdProvidedCustomId() {
        countlyStore.clear();

        CountlyConfig cc = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");
        cc.enableClearStoredDeviceId();
        cc.setDeviceId("qwe123");

        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertEquals("qwe123", countly.deviceId().getID());
        Assert.assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, countly.deviceId().getType());
    }

    /**
     * Followup init where previously:
     * Temporary ID was provided
     *
     * now:
     * Stored device ID clearing is enabled,
     * Device ID is provided,
     * Temporary ID mode is not provided
     *
     * SDK should leave temporary ID mode and use the provided device ID
     */
    @Test
    public void followupInitClearStoredIdPrevTempIdProvidedCustomId() {
        countlyStore.clear();

        //setup initial state
        CountlyConfig configInitial = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");
        configInitial.enableTemporaryDeviceIdMode();

        Countly cInitial = new Countly();
        cInitial.init(configInitial);

        Assert.assertEquals(DeviceId.temporaryCountlyDeviceId, cInitial.deviceId().getID());
        Assert.assertEquals(DeviceIdType.TEMPORARY_ID, cInitial.deviceId().getType());

        //setup followup state
        CountlyConfig cc = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");
        cc.enableClearStoredDeviceId();
        cc.setDeviceId("uio");

        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertEquals("uio", countly.deviceId().getID());
        Assert.assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, countly.deviceId().getType());
    }

    /**
     * Followup init where previously:
     * Custom device ID was set
     *
     * now:
     * Stored device ID clearing is enabled,
     * Device ID is not provided,
     * Temporary ID mode is not provided
     *
     * SDK should generate an OPEN_UDID device ID, changing the stored type
     */
    @Test
    public void followupInitClearStoredIdPrevCustomProvidedNothing() {
        countlyStore.clear();

        //setup initial state
        CountlyConfig configInitial = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");
        configInitial.setDeviceId("hjk");

        Countly cInitial = new Countly();
        cInitial.init(configInitial);

        Assert.assertEquals("hjk", cInitial.deviceId().getID());
        Assert.assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, cInitial.deviceId().getType());

        //setup followup state
        CountlyConfig cc = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");
        cc.enableClearStoredDeviceId();

        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertNotNull(countly.deviceId().getID());
        Assert.assertNotEquals("hjk", countly.deviceId().getID());
        Assert.assertEquals(DeviceIdType.OPEN_UDID, countly.deviceId().getType());
    }

    /**
     * Followup init where previously:
     * Temporary ID was provided and a request was left in the queue with the temporary ID
     *
     * now:
     * Stored device ID clearing is enabled
     *
     * The request left in the queue should be sent under the newly resolved device ID instead of being dropped
     */
    @Test
    public void followupInitClearStoredIdRetagsQueuedTempIdRequests() {
        countlyStore.clear();

        //setup initial state, a request held back while in temporary ID mode
        countlyStore.addRequest("aa=45&device_id=" + DeviceId.temporaryCountlyDeviceId, false);
        countlyStore.setDeviceID(DeviceId.temporaryCountlyDeviceId);
        countlyStore.setDeviceIDType(DeviceIdType.TEMPORARY_ID.toString());

        //setup followup state
        CountlyConfig cc = new CountlyConfig(TestUtils.getContext(), "aaa", "http://www.aa.bb");
        cc.enableClearStoredDeviceId();
        cc.setDeviceId("uio");

        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertEquals("uio", countly.deviceId().getID());
        Assert.assertArrayEquals(new String[] { "aa=45&device_id=uio" }, countlyStore.getRequests());
    }
}
