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
        new DeviceId(store, "", mock(ModuleLog.class), null);
    }

    /**
     * Expecting exception to be thrown when initialising with a bad value
     * Hinting to be dev supplied but null string
     */
    @Test(expected = IllegalStateException.class)
    public void constructorCustom_fail_2() {
        new DeviceId(store, (String) null, mock(ModuleLog.class), null);
    }

    /**
     * Expecting exception to be thrown when initialising with a bad value
     * Dev supplied type not allowed with this constructor
     */
    @Test(expected = IllegalStateException.class)
    public void constructorSDKGenerated_fail_1() {
        new DeviceId(store, DeviceId.Type.DEVELOPER_SUPPLIED, mock(ModuleLog.class), null);
    }

    /**
     * Expecting exception to be thrown when initialising with a bad value
     * Null type not allowed
     */
    @Test(expected = IllegalStateException.class)
    public void constructorSDKGenerated_fail_2() {
        new DeviceId(store, (DeviceId.Type) null, mock(ModuleLog.class), null);
    }

    /**
     * Validating 'deviceIDEqualsNullSafe'
     * Just checking which value combinations return which values
     */
    @Test
    public void deviceIDEqualsNullSafe_1() {
        DeviceId did = new DeviceId(store, "aa", mock(ModuleLog.class), null);
        assertTrue(DeviceId.deviceIDEqualsNullSafe("a", DeviceId.Type.OPEN_UDID, did));
        assertTrue(DeviceId.deviceIDEqualsNullSafe("a", DeviceId.Type.TEMPORARY_ID, did));
        assertTrue(DeviceId.deviceIDEqualsNullSafe("a", DeviceId.Type.ADVERTISING_ID, did));

        did.setId(DeviceId.Type.OPEN_UDID, null);
        assertTrue(DeviceId.deviceIDEqualsNullSafe(null, DeviceId.Type.ADVERTISING_ID, did));

        did.setId(DeviceId.Type.OPEN_UDID, "b");
        assertFalse(DeviceId.deviceIDEqualsNullSafe("a", DeviceId.Type.DEVELOPER_SUPPLIED, did));

        did.setId(DeviceId.Type.OPEN_UDID, "a");
        assertTrue(DeviceId.deviceIDEqualsNullSafe("a", DeviceId.Type.DEVELOPER_SUPPLIED, did));
    }

    /**
     * Checking different ways of setting temporary device ID mode
     */
    @Test
    public void temporaryIdModeEnabled() {
        DeviceId did = new DeviceId(store, "dsd", mock(ModuleLog.class), null);
        assertFalse(did.temporaryIdModeEnabled());
        did.setId(DeviceId.Type.OPEN_UDID, DeviceId.temporaryCountlyDeviceId);
        assertTrue(did.temporaryIdModeEnabled());

        DeviceId did2 = new DeviceId(store, DeviceId.temporaryCountlyDeviceId, mock(ModuleLog.class), null);
        assertTrue(did2.temporaryIdModeEnabled());
    }

    /**
     * Validating that the correct type is returned after initialization
     */
    @Test
    public void getType() {
        assertEquals(DeviceId.Type.DEVELOPER_SUPPLIED, new DeviceId(store, "dsd", mock(ModuleLog.class), null).getType());
        assertEquals(DeviceId.Type.TEMPORARY_ID, new DeviceId(store, DeviceId.temporaryCountlyDeviceId, mock(ModuleLog.class), null).getType());
        assertEquals(DeviceId.Type.OPEN_UDID, new DeviceId(store, DeviceId.Type.OPEN_UDID, mock(ModuleLog.class), null).getType());
        assertEquals(DeviceId.Type.ADVERTISING_ID, new DeviceId(store, DeviceId.Type.ADVERTISING_ID, mock(ModuleLog.class), null).getType());
    }

    /**
     * Validating that getId returns the expected value
     */
    @Test
    public void getId() {
        DeviceId did1 = new DeviceId(store, "abc", mock(ModuleLog.class), null);
        did1.init(getContext());
        assertEquals("abc", did1.getId());

        store.clear();

        DeviceId did2 = new DeviceId(store, DeviceId.temporaryCountlyDeviceId, mock(ModuleLog.class), null);
        did2.init(getContext());
        assertEquals(DeviceId.temporaryCountlyDeviceId, did2.getId());

        store.clear();

        currentOpenUDIDValue = "ppp1";
        DeviceId did3 = new DeviceId(store, DeviceId.Type.OPEN_UDID, mock(ModuleLog.class), openUDIDProvider);
        did3.init(getContext());
        assertEquals(currentOpenUDIDValue, did3.getId());

        store.clear();

        currentOpenUDIDValue = "openUDIDfallbackValue";
        DeviceId did4 = new DeviceId(store, DeviceId.Type.ADVERTISING_ID, mock(ModuleLog.class), openUDIDProvider);
        did4.init(getContext());
        assertNotNull(did4.getId());
        assertEquals(currentOpenUDIDValue, did4.getId());
    }

    /**
     * Validating 'changeToId' with developer supplied values
     */
    @Test
    public void changeToIdDevSupplied() {
        DeviceId did = new DeviceId(store, "abc", mock(ModuleLog.class), null);
        did.init(getContext());
        assertEquals("abc", did.getId());

        did.changeToId(getContext(), DeviceId.Type.DEVELOPER_SUPPLIED, "123", false);
        assertEquals("123", did.getId());
        assertEquals(DeviceId.Type.DEVELOPER_SUPPLIED, did.getType());


        did.changeToId(getContext(), DeviceId.Type.DEVELOPER_SUPPLIED, "456", true);
        assertEquals("456", did.getId());
        assertEquals(DeviceId.Type.DEVELOPER_SUPPLIED, did.getType());
    }

    /**
     * Validating 'changeToId' around openUDID
     */
    @Test
    public void changeToIdOpenUDID() {
        DeviceId did = new DeviceId(store, "abc", mock(ModuleLog.class), openUDIDProvider);
        did.init(getContext());
        assertEquals("abc", did.getId());

        //set first value without running init, should use the provided value
        did.changeToId(getContext(), DeviceId.Type.OPEN_UDID, "123", false);
        assertEquals("123", did.getId());
        assertEquals(DeviceId.Type.OPEN_UDID, did.getType());

        //do a reset
        did.changeToId(getContext(), DeviceId.Type.DEVELOPER_SUPPLIED, "aaa", true);
        assertEquals("aaa", did.getId());
        assertEquals(DeviceId.Type.DEVELOPER_SUPPLIED, did.getType());

        //change with init, since a value is specified, it should take precedence
        currentOpenUDIDValue = "uio|";
        did.changeToId(getContext(), DeviceId.Type.OPEN_UDID, "456", true);
        assertEquals("456", did.getId());
        assertEquals(DeviceId.Type.OPEN_UDID, did.getType());

        //change with init, it should use it's own value because a null device ID is provided
        currentOpenUDIDValue = "sdfh";
        did.changeToId(getContext(), DeviceId.Type.OPEN_UDID, null, true);
        assertEquals(currentOpenUDIDValue, did.getId());
        assertEquals(DeviceId.Type.OPEN_UDID, did.getType());
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

        DeviceId did = new DeviceId(store, "abc", mock(ModuleLog.class), null);
        did.init(getContext());
        assertEquals("abc", did.getId());
        assertEquals(DeviceId.Type.DEVELOPER_SUPPLIED, did.getType());

        did = new DeviceId(store, "123", mock(ModuleLog.class), null);
        did.init(getContext());
        assertEquals("abc", did.getId());
        assertEquals(DeviceId.Type.DEVELOPER_SUPPLIED, did.getType());

        did = new DeviceId(store, DeviceId.Type.OPEN_UDID, mock(ModuleLog.class), null);
        did.init(getContext());
        assertEquals("abc", did.getId());
        assertEquals(DeviceId.Type.DEVELOPER_SUPPLIED, did.getType());

        did = new DeviceId(store, DeviceId.Type.ADVERTISING_ID, mock(ModuleLog.class), null);
        did.init(getContext());
        assertEquals("abc", did.getId());
        assertEquals(DeviceId.Type.DEVELOPER_SUPPLIED, did.getType());

        did = new DeviceId(store, DeviceId.Type.TEMPORARY_ID, mock(ModuleLog.class), null);
        did.init(getContext());
        assertEquals("abc", did.getId());
        assertEquals(DeviceId.Type.DEVELOPER_SUPPLIED, did.getType());
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
        DeviceId did = new DeviceId(store, DeviceId.Type.OPEN_UDID, mock(ModuleLog.class), openUDIDProvider);
        did.init(getContext());
        assertEquals(currentOpenUDIDValue, did.getId());
        assertEquals(DeviceId.Type.OPEN_UDID, did.getType());

        did = new DeviceId(store, "123", mock(ModuleLog.class), openUDIDProvider);
        did.init(getContext());
        assertEquals(currentOpenUDIDValue, did.getId());
        assertEquals(DeviceId.Type.OPEN_UDID, did.getType());

        did = new DeviceId(store, DeviceId.Type.OPEN_UDID, mock(ModuleLog.class), openUDIDProvider);
        did.init(getContext());
        assertEquals(currentOpenUDIDValue, did.getId());
        assertEquals(DeviceId.Type.OPEN_UDID, did.getType());

        did = new DeviceId(store, DeviceId.Type.ADVERTISING_ID, mock(ModuleLog.class), openUDIDProvider);
        did.init(getContext());
        assertEquals(currentOpenUDIDValue, did.getId());
        assertEquals(DeviceId.Type.OPEN_UDID, did.getType());

        did = new DeviceId(store, DeviceId.Type.TEMPORARY_ID, mock(ModuleLog.class), openUDIDProvider);
        did.init(getContext());
        assertEquals(currentOpenUDIDValue, did.getId());
        assertEquals(DeviceId.Type.OPEN_UDID, did.getType());
    }

    @Test
    public void reinitOpenUDID_differentValue() {
        currentOpenUDIDValue = "nmb";
        String initialValue = currentOpenUDIDValue;

        //init the first time and openUDID returns one value
        DeviceId did = new DeviceId(store, DeviceId.Type.OPEN_UDID, mock(ModuleLog.class), openUDIDProvider);
        did.init(getContext());
        assertEquals(currentOpenUDIDValue, did.getId());
        assertEquals(DeviceId.Type.OPEN_UDID, did.getType());

        //init the second time and openUDID returns a different value
        currentOpenUDIDValue = "zxc";
        did = new DeviceId(store, DeviceId.Type.OPEN_UDID, mock(ModuleLog.class), openUDIDProvider);
        did.init(getContext());
        assertEquals(initialValue, did.getId());
        assertEquals(DeviceId.Type.OPEN_UDID, did.getType());

    }
}
