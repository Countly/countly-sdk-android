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
        new DeviceId("", store, mock(ModuleLog.class), null);
    }

    /**
     * Checking temporary device ID mode is correctly recognised after init if set with "setId"
     * This would make sure that temp ID mode is verified mainly with the id string value
     */
    @Test
    public void temporaryIdModeEnabled_1() {
        DeviceId did = new DeviceId("dsd", store, mock(ModuleLog.class), null);
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
        DeviceId did2 = new DeviceId(DeviceId.temporaryCountlyDeviceId, store, mock(ModuleLog.class), null);
        assertTrue(did2.isTemporaryIdModeEnabled());

        //todo needs more work
    }

    /**
     * Validating that the correct type is returned after initialization
     */
    @Test
    public void getType() {
        assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, new DeviceId("dsd", store, mock(ModuleLog.class), null).getType());
        store.clear();

        assertEquals(DeviceIdType.TEMPORARY_ID, new DeviceId(DeviceId.temporaryCountlyDeviceId, store, mock(ModuleLog.class), null).getType());
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
        DeviceId did1 = new DeviceId("abc", store, mock(ModuleLog.class), null);
        assertEquals("abc", did1.getCurrentId());

        store.clear();

        DeviceId did2 = new DeviceId(DeviceId.temporaryCountlyDeviceId, store, mock(ModuleLog.class), null);
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
        DeviceId did = new DeviceId("abc", store, mock(ModuleLog.class), null);
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

        DeviceId did = new DeviceId("abc", store, mock(ModuleLog.class), null);
        assertEquals("abc", did.getCurrentId());
        assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, did.getType());

        did = new DeviceId("123", store, mock(ModuleLog.class), null);
        assertEquals("abc", did.getCurrentId());
        assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, did.getType());

        did = new DeviceId(null, store, mock(ModuleLog.class), null);
        assertEquals("abc", did.getCurrentId());
        assertEquals(DeviceIdType.DEVELOPER_SUPPLIED, did.getType());

        did = new DeviceId(DeviceId.temporaryCountlyDeviceId, store, mock(ModuleLog.class), null);
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

    @Test
    public void xxx() {
        store.clear();

        store.setDeviceID("xxx");
        store.setDeviceIDType(MigrationHelper.legacyDeviceIDTypeValue_AdvertisingID);

        DeviceId did = new DeviceId("123", store, mock(ModuleLog.class), openUDIDProvider);
        assertEquals("xxx", did.getCurrentId());
        assertEquals(DeviceIdType.OPEN_UDID, did.getType());
    }
}
