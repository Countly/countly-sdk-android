package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

@RunWith(AndroidJUnit4.class)
public class DeviceIdInitTests {
    CountlyStore countlyStore;

    @Before
    public void setUp() {
        countlyStore = new CountlyStore(getContext(), mock(ModuleLog.class));
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
        CountlyConfig cc = new CountlyConfig(getContext(), "aaa", "http://www.aa.bb");
        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertNotNull(countly.getDeviceID());
        Assert.assertEquals(DeviceId.Type.OPEN_UDID, countly.getDeviceIDType());
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
        CountlyConfig cc = new CountlyConfig(getContext(), "aaa", "http://www.aa.bb");
        cc.setDeviceId("qwe123");

        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertEquals("qwe123", countly.getDeviceID());
        Assert.assertEquals(DeviceId.Type.DEVELOPER_SUPPLIED, countly.getDeviceIDType());
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
        CountlyConfig cc = new CountlyConfig(getContext(), "aaa", "http://www.aa.bb");
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
        CountlyConfig configInitial = new CountlyConfig(getContext(), "aaa", "http://www.aa.bb");
        configInitial.setDeviceId("hjk");

        Countly cInitial = new Countly();
        cInitial.init(configInitial);

        String initialDId = cInitial.getDeviceID();

        Assert.assertEquals("hjk", cInitial.getDeviceID());
        Assert.assertEquals(DeviceId.Type.DEVELOPER_SUPPLIED, cInitial.getDeviceIDType());

        //setup followup state
        CountlyConfig cc = new CountlyConfig(getContext(), "aaa", "http://www.aa.bb");

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
        CountlyConfig configInitial = new CountlyConfig(getContext(), "aaa", "http://www.aa.bb");
        configInitial.setDeviceId("hjk");

        Countly cInitial = new Countly();
        cInitial.init(configInitial);

        String initialDId = cInitial.getDeviceID();

        Assert.assertEquals("hjk", cInitial.getDeviceID());
        Assert.assertEquals(DeviceId.Type.DEVELOPER_SUPPLIED, cInitial.getDeviceIDType());

        //setup followup state
        CountlyConfig cc = new CountlyConfig(getContext(), "aaa", "http://www.aa.bb");
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
        CountlyConfig configInitial = new CountlyConfig(getContext(), "aaa", "http://www.aa.bb");
        configInitial.setDeviceId("hjk");

        Countly cInitial = new Countly();
        cInitial.init(configInitial);

        String initialDId = cInitial.deviceId().getID();

        Assert.assertEquals("hjk", cInitial.deviceId().getID());
        Assert.assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, cInitial.deviceId().getType());

        //setup followup state
        CountlyConfig cc = new CountlyConfig(getContext(), "aaa", "http://www.aa.bb");
        cc.enableTemporaryDeviceIdMode();

        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertEquals(initialDId, countly.getDeviceID());
        Assert.assertEquals(DeviceId.Type.DEVELOPER_SUPPLIED, countly.getDeviceIDType());
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
        CountlyConfig configInitial = new CountlyConfig(getContext(), "aaa", "http://www.aa.bb");
        configInitial.setDeviceId("hjk");

        Countly cInitial = new Countly();
        cInitial.init(configInitial);

        String initialDId = cInitial.getDeviceID();

        Assert.assertEquals("hjk", cInitial.getDeviceID());
        Assert.assertEquals(DeviceId.Type.DEVELOPER_SUPPLIED, cInitial.getDeviceIDType());

        //setup followup state
        CountlyConfig cc = new CountlyConfig(getContext(), "aaa", "http://www.aa.bb");
        cc.enableTemporaryDeviceIdMode();
        cc.setDeviceId("890");

        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertEquals(initialDId, countly.getDeviceID());
        Assert.assertEquals(DeviceId.Type.DEVELOPER_SUPPLIED, countly.getDeviceIDType());
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
        CountlyConfig configInitial = new CountlyConfig(getContext(), "aaa", "http://www.aa.bb");

        Countly cInitial = new Countly();
        cInitial.init(configInitial);

        String initialDId = cInitial.deviceId().getID();

        Assert.assertNotNull(cInitial.deviceId().getID());
        Assert.assertEquals(DeviceId.Type.OPEN_UDID, cInitial.getDeviceIDType());

        //setup followup state
        CountlyConfig cc = new CountlyConfig(getContext(), "aaa", "http://www.aa.bb");

        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertEquals(initialDId, countly.getDeviceID());
        Assert.assertEquals(DeviceId.Type.OPEN_UDID, countly.getDeviceIDType());
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
        CountlyConfig configInitial = new CountlyConfig(getContext(), "aaa", "http://www.aa.bb");

        Countly cInitial = new Countly();
        cInitial.init(configInitial);

        String initialDId = cInitial.getDeviceID();

        Assert.assertNotNull(cInitial.getDeviceID());
        Assert.assertEquals(DeviceId.Type.OPEN_UDID, cInitial.getDeviceIDType());

        //setup followup state
        CountlyConfig cc = new CountlyConfig(getContext(), "aaa", "http://www.aa.bb");
        cc.setDeviceId("1qwe");

        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertEquals(initialDId, countly.getDeviceID());
        Assert.assertEquals(DeviceId.Type.OPEN_UDID, countly.getDeviceIDType());
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
        CountlyConfig configInitial = new CountlyConfig(getContext(), "aaa", "http://www.aa.bb");

        Countly cInitial = new Countly();
        cInitial.init(configInitial);

        String initialDId = cInitial.getDeviceID();

        Assert.assertNotNull(cInitial.getDeviceID());
        Assert.assertEquals(DeviceId.Type.OPEN_UDID, cInitial.getDeviceIDType());

        //setup followup state
        CountlyConfig cc = new CountlyConfig(getContext(), "aaa", "http://www.aa.bb");
        cc.enableTemporaryDeviceIdMode();

        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertEquals(initialDId, countly.getDeviceID());
        Assert.assertEquals(DeviceId.Type.OPEN_UDID, countly.getDeviceIDType());
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
        CountlyConfig configInitial = new CountlyConfig(getContext(), "aaa", "http://www.aa.bb");

        Countly cInitial = new Countly();
        cInitial.init(configInitial);

        String initialDId = cInitial.deviceId().getID();

        Assert.assertNotNull(cInitial.deviceId().getID());
        Assert.assertEquals(DeviceIdType.OPEN_UDID, cInitial.deviceId().getType());

        //setup followup state
        CountlyConfig cc = new CountlyConfig(getContext(), "aaa", "http://www.aa.bb");

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
        CountlyConfig configInitial = new CountlyConfig(getContext(), "aaa", "http://www.aa.bb");
        configInitial.enableTemporaryDeviceIdMode();
        configInitial.setLoggingEnabled(true);

        Countly cInitial = new Countly();
        cInitial.init(configInitial);

        String initialDId = cInitial.getDeviceID();

        Assert.assertNotNull(cInitial.getDeviceID());
        Assert.assertEquals(DeviceId.Type.TEMPORARY_ID, cInitial.getDeviceIDType());

        //setup followup state
        CountlyConfig cc = new CountlyConfig(getContext(), "aaa", "http://www.aa.bb");
        cc.setLoggingEnabled(true);

        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertEquals(initialDId, countly.getDeviceID());
        Assert.assertEquals(DeviceId.Type.TEMPORARY_ID, countly.getDeviceIDType());
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
        CountlyConfig configInitial = new CountlyConfig(getContext(), "aaa", "http://www.aa.bb");
        configInitial.enableTemporaryDeviceIdMode();

        Countly cInitial = new Countly();
        cInitial.init(configInitial);

        Assert.assertNotNull(cInitial.deviceId().getID());
        Assert.assertEquals(DeviceIdType.TEMPORARY_ID, cInitial.deviceId().getType());

        //setup followup state
        CountlyConfig cc = new CountlyConfig(getContext(), "aaa", "http://www.aa.bb");
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
        CountlyConfig configInitial = new CountlyConfig(getContext(), "aaa", "http://www.aa.bb");
        configInitial.enableTemporaryDeviceIdMode();

        Countly cInitial = new Countly();
        cInitial.init(configInitial);

        String initialDId = cInitial.getDeviceID();

        Assert.assertNotNull(cInitial.getDeviceID());
        Assert.assertEquals(DeviceId.Type.TEMPORARY_ID, cInitial.getDeviceIDType());

        //setup followup state
        CountlyConfig cc = new CountlyConfig(getContext(), "aaa", "http://www.aa.bb");
        cc.enableTemporaryDeviceIdMode();

        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertEquals(initialDId, countly.getDeviceID());
        Assert.assertEquals(DeviceId.Type.TEMPORARY_ID, countly.getDeviceIDType());
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
        CountlyConfig configInitial = new CountlyConfig(getContext(), "aaa", "http://www.aa.bb");
        configInitial.enableTemporaryDeviceIdMode();

        Countly cInitial = new Countly();
        cInitial.init(configInitial);

        String initialDId = cInitial.deviceId().getID();

        Assert.assertNotNull(cInitial.deviceId().getID());
        Assert.assertEquals(DeviceIdType.TEMPORARY_ID, cInitial.deviceId().getType());

        //setup followup state
        CountlyConfig cc = new CountlyConfig(getContext(), "aaa", "http://www.aa.bb");
        cc.enableTemporaryDeviceIdMode();
        cc.setDeviceId("frt");

        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertEquals("frt", countly.deviceId().getID());
        Assert.assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, countly.deviceId().getType());
    }

    /**
     * Usage of Advertising_ID is deprecated
     * if that type is used during the first init, it should be replaced with OPEN_UDID
     */
    @Test
    public void advertIdReplacedWithOpenUDID() {
        countlyStore.clear();

        //first init where advertising id mode is chosen. It should be replaced with open_udid
        CountlyConfig configInitial = new CountlyConfig(getContext(), "aaa", "http://www.aa.bb");
        configInitial.setIdMode(DeviceIdType.ADVERTISING_ID);

        Countly cInitial = new Countly();
        cInitial.init(configInitial);

        String initialDId = cInitial.deviceId().getID();

        Assert.assertNotNull(cInitial.deviceId().getID());
        Assert.assertEquals(DeviceIdType.OPEN_UDID, cInitial.deviceId().getType());


        //setup followup init. Adv id is still provided, id and type should be returned the same as befor
        CountlyConfig cc = new CountlyConfig(getContext(), "aaa", "http://www.aa.bb");
        configInitial.setIdMode(DeviceIdType.ADVERTISING_ID);

        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertEquals(initialDId, countly.deviceId().getID());
        Assert.assertEquals(DeviceIdType.OPEN_UDID, countly.deviceId().getType());
    }
}
