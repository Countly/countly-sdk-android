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
        new DeviceId((DeviceIdType) null, (String) null, store, mock(ModuleLog.class), null);
    }

    /**
     * Checking temporary device ID mode is correctly recognised after init if set with "setId"
     * This would make sure that temp ID mode is verified mainly with the id string value
     */
    @Test
    public void temporaryIdModeEnabled_1() {
        DeviceId did = new DeviceId(DeviceIdType.DEVELOPER_SUPPLIED, "dsd", store, mock(ModuleLog.class), null);
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
     * Checking setting temporary device ID mode during init
     */
    @Test
    public void temporaryIdModeEnabled_2() {
        DeviceId did2 = new DeviceId(DeviceIdType.TEMPORARY_ID, DeviceId.temporaryCountlyDeviceId, store, mock(ModuleLog.class), null);
        assertTrue(did2.isTemporaryIdModeEnabled());

        //todo needs more work
    }

    /**
     * Validating that the correct type is returned after initialization
     */
    @Test
    public void getType() {
        assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, new DeviceId(DeviceIdType.DEVELOPER_SUPPLIED, "dsd", store, mock(ModuleLog.class), null).getType());
        store.clear();

        assertEquals(DeviceIdType.TEMPORARY_ID, new DeviceId(DeviceIdType.TEMPORARY_ID, DeviceId.temporaryCountlyDeviceId, store, mock(ModuleLog.class), null).getType());
        store.clear();

        assertEquals(DeviceIdType.OPEN_UDID, new DeviceId(DeviceIdType.OPEN_UDID, null, store, mock(ModuleLog.class), new OpenUDIDProvider() {
            @Override public String getOpenUDID() {
                return "abc";
            }
        }).getType());
        store.clear();

        assertEquals(DeviceIdType.OPEN_UDID, new DeviceId(DeviceIdType.ADVERTISING_ID, null, store, mock(ModuleLog.class), new OpenUDIDProvider() {
            @Override public String getOpenUDID() {
                return "123";
            }
        }).getType());
    }

    /**
     * Validating that getId returns the expected value
     */
    @Test
    public void getId() {
        DeviceId did1 = new DeviceId(DeviceIdType.DEVELOPER_SUPPLIED, "abc", store, mock(ModuleLog.class), null);
        assertEquals("abc", did1.getCurrentId());

        store.clear();

        DeviceId did2 = new DeviceId(DeviceIdType.TEMPORARY_ID, DeviceId.temporaryCountlyDeviceId, store, mock(ModuleLog.class), null);
        assertEquals(DeviceId.temporaryCountlyDeviceId, did2.getCurrentId());

        store.clear();

        currentOpenUDIDValue = "ppp1";
        DeviceId did3 = new DeviceId(DeviceIdType.OPEN_UDID, null, store, mock(ModuleLog.class), openUDIDProvider);
        assertEquals(currentOpenUDIDValue, did3.getCurrentId());

        store.clear();

        currentOpenUDIDValue = "openUDIDfallbackValue";
        DeviceId did4 = new DeviceId(DeviceIdType.ADVERTISING_ID, null, store, mock(ModuleLog.class), openUDIDProvider);
        assertNotNull(did4.getCurrentId());
        assertEquals(currentOpenUDIDValue, did4.getCurrentId());
    }

    /**
     * Validating 'changeToId' with developer supplied values
     */
    @Test
    public void changeToCustomIdAndEnterTempIDMode() {
        DeviceId did = new DeviceId(DeviceIdType.DEVELOPER_SUPPLIED, "abc", store, mock(ModuleLog.class), null);
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

        DeviceId did = new DeviceId(DeviceIdType.DEVELOPER_SUPPLIED, "abc", store, mock(ModuleLog.class), null);
        assertEquals("abc", did.getCurrentId());
        assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, did.getType());

        did = new DeviceId(DeviceIdType.DEVELOPER_SUPPLIED, "123", store, mock(ModuleLog.class), null);
        assertEquals("abc", did.getCurrentId());
        assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, did.getType());

        did = new DeviceId(DeviceIdType.OPEN_UDID, null, store, mock(ModuleLog.class), null);
        assertEquals("abc", did.getCurrentId());
        assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, did.getType());

        did = new DeviceId(DeviceIdType.ADVERTISING_ID, null, store, mock(ModuleLog.class), null);
        assertEquals("abc", did.getCurrentId());
        assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, did.getType());

        did = new DeviceId(DeviceIdType.TEMPORARY_ID, DeviceId.temporaryCountlyDeviceId, store, mock(ModuleLog.class), null);
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
        assertEquals(currentOpenUDIDValue, did.getCurrentId());
        assertEquals(DeviceIdType.OPEN_UDID, did.getType());

        did = new DeviceId(DeviceIdType.DEVELOPER_SUPPLIED, "123", store, mock(ModuleLog.class), openUDIDProvider);
        assertEquals(currentOpenUDIDValue, did.getCurrentId());
        assertEquals(DeviceIdType.OPEN_UDID, did.getType());

        did = new DeviceId(DeviceIdType.OPEN_UDID, null, store, mock(ModuleLog.class), openUDIDProvider);
        assertEquals(currentOpenUDIDValue, did.getCurrentId());
        assertEquals(DeviceIdType.OPEN_UDID, did.getType());

        did = new DeviceId(DeviceIdType.ADVERTISING_ID, null, store, mock(ModuleLog.class), openUDIDProvider);
        assertEquals(currentOpenUDIDValue, did.getCurrentId());
        assertEquals(DeviceIdType.OPEN_UDID, did.getType());

        did = new DeviceId(DeviceIdType.TEMPORARY_ID, DeviceId.temporaryCountlyDeviceId, store, mock(ModuleLog.class), openUDIDProvider);
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
        assertEquals(currentOpenUDIDValue, did.getCurrentId());
        assertEquals(DeviceIdType.OPEN_UDID, did.getType());

        //init the second time and openUDID returns a different value
        currentOpenUDIDValue = "zxc";
        did = new DeviceId(DeviceIdType.OPEN_UDID, null, store, mock(ModuleLog.class), openUDIDProvider);
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
