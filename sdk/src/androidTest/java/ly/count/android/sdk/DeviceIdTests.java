package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.After;
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
public class DeviceIdTests {
    CountlyStore store;

    OpenUDIDProvider openUDIDProvider;
    String currentOpenUDIDValue = "xxyyxx";

    @Before
    public void setUp() {
        Countly.sharedInstance().setLoggingEnabled(true);
        store = new CountlyStore(getContext(), mock(ModuleLog.class));
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
        new DeviceId(DeviceIdType.DEVELOPER_SUPPLIED, "", store, mock(ModuleLog.class), null);
    }

    /**
     * Expecting exception to be thrown when initialising with a bad value
     * Hinting to be dev supplied but null string
     */
    @Test(expected = IllegalStateException.class)
    public void constructorCustom_fail_2() {
        new DeviceId(DeviceIdType.DEVELOPER_SUPPLIED, (String) null, store, mock(ModuleLog.class), null);
    }

    /**
     * Expecting exception to be thrown when initialising with a bad value
     * Null type not allowed
     */
    @Test(expected = IllegalStateException.class)
    public void constructorSDKGenerated_fail_2() {
        new DeviceId((DeviceIdType) null, (String)null, store, mock(ModuleLog.class), null);
    }

    /**
     * Validating 'deviceIDEqualsNullSafe'
     * Just checking which value combinations return which values
     */
    @Test
    public void deviceIDEqualsNullSafe_1() {
        DeviceId did = new DeviceId(DeviceIdType.DEVELOPER_SUPPLIED, "aa", store, mock(ModuleLog.class), null);
        assertTrue(DeviceId.deviceIDEqualsNullSafe("a", DeviceIdType.OPEN_UDID, did));
        assertTrue(DeviceId.deviceIDEqualsNullSafe("a", DeviceIdType.TEMPORARY_ID, did));
        assertTrue(DeviceId.deviceIDEqualsNullSafe("a", DeviceIdType.ADVERTISING_ID, did));

        did.setId(DeviceIdType.OPEN_UDID, null);
        assertTrue(DeviceId.deviceIDEqualsNullSafe(null, DeviceIdType.ADVERTISING_ID, did));

        did.setId(DeviceIdType.OPEN_UDID, "b");
        assertFalse(DeviceId.deviceIDEqualsNullSafe("a", DeviceIdType.DEVELOPER_SUPPLIED, did));

        did.setId(DeviceIdType.OPEN_UDID, "a");
        assertTrue(DeviceId.deviceIDEqualsNullSafe("a", DeviceIdType.DEVELOPER_SUPPLIED, did));
    }

    /**
     * Checking temporary device ID mode is correctly recognised after init if set with "setId"
     * This would make sure that temp ID mode is verified mainly with the id string value
     */
    @Test
    public void temporaryIdModeEnabled_1() {
        DeviceId did = new DeviceId(DeviceIdType.DEVELOPER_SUPPLIED,"dsd", store, mock(ModuleLog.class), null);
        did.init();
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

        did.setId(DeviceIdType.ADVERTISING_ID, DeviceId.temporaryCountlyDeviceId);
        assertTrue(did.isTemporaryIdModeEnabled());

        did.setId(DeviceIdType.DEVELOPER_SUPPLIED, "34");
        assertEquals("34", did.getCurrentId());
        assertFalse(did.isTemporaryIdModeEnabled());

        did.setId(DeviceIdType.TEMPORARY_ID, DeviceId.temporaryCountlyDeviceId);
        assertTrue(did.isTemporaryIdModeEnabled());
    }

    /**
     * Checking dsetting temporary device ID mode during init
     */
    @Test
    public void temporaryIdModeEnabled_2() {
        DeviceId did2 = new DeviceId(DeviceIdType.TEMPORARY_ID, DeviceId.temporaryCountlyDeviceId, store, mock(ModuleLog.class), null);
        did2.init();
        assertTrue(did2.isTemporaryIdModeEnabled());

        //todo needs more work
    }

    /**
     * Validating that the correct type is returned after initialization
     */
    @Test
    public void getType() {
        assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, new DeviceId(DeviceIdType.DEVELOPER_SUPPLIED, "dsd", store, mock(ModuleLog.class), null).getType());
        assertEquals(DeviceIdType.TEMPORARY_ID, new DeviceId(DeviceIdType.TEMPORARY_ID, DeviceId.temporaryCountlyDeviceId, store, mock(ModuleLog.class), null).getType());
        assertEquals(DeviceIdType.OPEN_UDID, new DeviceId(DeviceIdType.OPEN_UDID, null, store, mock(ModuleLog.class), new OpenUDIDProvider() {
            @Override public String getOpenUDID() {
                return "abc";
            }
        }).getType());
        assertEquals(DeviceIdType.ADVERTISING_ID, new DeviceId(DeviceIdType.ADVERTISING_ID, null, store, mock(ModuleLog.class), null).getType());
    }

    /**
     * Validating that getId returns the expected value
     */
    @Test
    public void getId() {
        DeviceId did1 = new DeviceId(DeviceIdType.DEVELOPER_SUPPLIED, "abc", store, mock(ModuleLog.class), null);
        did1.init();
        assertEquals("abc", did1.getCurrentId());

        store.clear();

        DeviceId did2 = new DeviceId(DeviceIdType.TEMPORARY_ID, DeviceId.temporaryCountlyDeviceId, store, mock(ModuleLog.class), null);
        did2.init();
        assertEquals(DeviceId.temporaryCountlyDeviceId, did2.getCurrentId());

        store.clear();

        currentOpenUDIDValue = "ppp1";
        DeviceId did3 = new DeviceId(DeviceIdType.OPEN_UDID, null, store, mock(ModuleLog.class), openUDIDProvider);
        did3.init();
        assertEquals(currentOpenUDIDValue, did3.getCurrentId());

        store.clear();

        currentOpenUDIDValue = "openUDIDfallbackValue";
        DeviceId did4 = new DeviceId(DeviceIdType.ADVERTISING_ID, null, store, mock(ModuleLog.class), openUDIDProvider);
        did4.init();
        assertNotNull(did4.getCurrentId());
        assertEquals(currentOpenUDIDValue, did4.getCurrentId());
    }

    /**
     * Validating 'changeToId' with developer supplied values
     */
    @Test
    public void changeToIdDevSupplied() {
        DeviceId did = new DeviceId(DeviceIdType.DEVELOPER_SUPPLIED, "abc", store, mock(ModuleLog.class), null);
        did.init();
        assertEquals("abc", did.getCurrentId());

        did.changeToId(DeviceIdType.DEVELOPER_SUPPLIED, "123", false);
        assertEquals("123", did.getCurrentId());
        assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, did.getType());

        did.changeToId(DeviceIdType.DEVELOPER_SUPPLIED, "456", true);
        assertEquals("456", did.getCurrentId());
        assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, did.getType());
    }

    /**
     * Validating 'changeToId' around openUDID
     */
    @Test
    public void changeToIdOpenUDID() {
        DeviceId did = new DeviceId(DeviceIdType.DEVELOPER_SUPPLIED, "abc", store, mock(ModuleLog.class), openUDIDProvider);
        did.init();
        assertEquals("abc", did.getCurrentId());

        //set first value without running init, should use the provided value
        did.changeToId(DeviceIdType.OPEN_UDID, "123", false);
        assertEquals("123", did.getCurrentId());
        assertEquals(DeviceIdType.OPEN_UDID, did.getType());

        //do a reset
        did.changeToId(DeviceIdType.DEVELOPER_SUPPLIED, "aaa", true);
        assertEquals("aaa", did.getCurrentId());
        assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, did.getType());

        //change with init, since a value is specified, it should take precedence
        currentOpenUDIDValue = "uio|";
        did.changeToId(DeviceIdType.OPEN_UDID, "456", true);
        assertEquals("456", did.getCurrentId());
        assertEquals(DeviceIdType.OPEN_UDID, did.getType());

        //change with init, it should use it's own value because a null device ID is provided
        currentOpenUDIDValue = "sdfh";
        did.changeToId(DeviceIdType.OPEN_UDID, null, true);
        assertEquals(currentOpenUDIDValue, did.getCurrentId());
        assertEquals(DeviceIdType.OPEN_UDID, did.getType());
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

        DeviceId did = new DeviceId(DeviceIdType.DEVELOPER_SUPPLIED, "abc", store, mock(ModuleLog.class), null);
        did.init();
        assertEquals("abc", did.getCurrentId());
        assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, did.getType());

        did = new DeviceId(DeviceIdType.DEVELOPER_SUPPLIED, "123", store, mock(ModuleLog.class), null);
        did.init();
        assertEquals("abc", did.getCurrentId());
        assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, did.getType());

        did = new DeviceId(DeviceIdType.OPEN_UDID, null, store, mock(ModuleLog.class), null);
        did.init();
        assertEquals("abc", did.getCurrentId());
        assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, did.getType());

        did = new DeviceId(DeviceIdType.ADVERTISING_ID, null, store, mock(ModuleLog.class), null);
        did.init();
        assertEquals("abc", did.getCurrentId());
        assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, did.getType());

        did = new DeviceId(DeviceIdType.TEMPORARY_ID, DeviceId.temporaryCountlyDeviceId, store, mock(ModuleLog.class), null);
        did.init();
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
        DeviceId did = new DeviceId(DeviceIdType.OPEN_UDID, null, store, mock(ModuleLog.class), openUDIDProvider);
        did.init();
        assertEquals(currentOpenUDIDValue, did.getCurrentId());
        assertEquals(DeviceIdType.OPEN_UDID, did.getType());

        did = new DeviceId(DeviceIdType.DEVELOPER_SUPPLIED, "123", store, mock(ModuleLog.class), openUDIDProvider);
        did.init();
        assertEquals(currentOpenUDIDValue, did.getCurrentId());
        assertEquals(DeviceIdType.OPEN_UDID, did.getType());

        did = new DeviceId(DeviceIdType.OPEN_UDID, null, store, mock(ModuleLog.class), openUDIDProvider);
        did.init();
        assertEquals(currentOpenUDIDValue, did.getCurrentId());
        assertEquals(DeviceIdType.OPEN_UDID, did.getType());

        did = new DeviceId(DeviceIdType.ADVERTISING_ID, null, store, mock(ModuleLog.class), openUDIDProvider);
        did.init();
        assertEquals(currentOpenUDIDValue, did.getCurrentId());
        assertEquals(DeviceIdType.OPEN_UDID, did.getType());

        did = new DeviceId(DeviceIdType.TEMPORARY_ID, DeviceId.temporaryCountlyDeviceId, store, mock(ModuleLog.class), openUDIDProvider);
        did.init();
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
        DeviceId did = new DeviceId(DeviceIdType.OPEN_UDID, null, store, mock(ModuleLog.class), openUDIDProvider);
        did.init();
        assertEquals(currentOpenUDIDValue, did.getCurrentId());
        assertEquals(DeviceIdType.OPEN_UDID, did.getType());

        //init the second time and openUDID returns a different value
        currentOpenUDIDValue = "zxc";
        did = new DeviceId(DeviceIdType.OPEN_UDID, null, store, mock(ModuleLog.class), openUDIDProvider);
        did.init();
        assertEquals(initialValue, did.getCurrentId());
        assertEquals(DeviceIdType.OPEN_UDID, did.getType());
    }

    /**
     * Make sure that the device ID type changer works as expected
     */
    @Test
    public void validateDeviceIdTypeConversions() {
        assertEquals(DeviceIdType.OPEN_UDID, ModuleDeviceId.fromOldDeviceIdToNew(DeviceId.Type.OPEN_UDID));
        assertEquals(DeviceIdType.TEMPORARY_ID, ModuleDeviceId.fromOldDeviceIdToNew(DeviceId.Type.TEMPORARY_ID));
        assertEquals(DeviceIdType.ADVERTISING_ID, ModuleDeviceId.fromOldDeviceIdToNew(DeviceId.Type.ADVERTISING_ID));
        assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, ModuleDeviceId.fromOldDeviceIdToNew(DeviceId.Type.DEVELOPER_SUPPLIED));

        assertEquals(DeviceId.Type.OPEN_UDID, ModuleDeviceId.fromNewDeviceIdToOld(DeviceIdType.OPEN_UDID));
        assertEquals(DeviceId.Type.TEMPORARY_ID, ModuleDeviceId.fromNewDeviceIdToOld(DeviceIdType.TEMPORARY_ID));
        assertEquals(DeviceId.Type.ADVERTISING_ID, ModuleDeviceId.fromNewDeviceIdToOld(DeviceIdType.ADVERTISING_ID));
        assertEquals(DeviceId.Type.DEVELOPER_SUPPLIED, ModuleDeviceId.fromNewDeviceIdToOld(DeviceIdType.DEVELOPER_SUPPLIED));
    }
}
