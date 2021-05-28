package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

@RunWith(AndroidJUnit4.class)
public class DeviceIdTests {
    CountlyStore store;

    @Before
    public void setUp() {
        Countly.sharedInstance().setLoggingEnabled(true);
        store = new CountlyStore(getContext(), mock(ModuleLog.class));
        store.clear();
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
        new DeviceId(store, "", mock(ModuleLog.class));
    }

    /**
     * Expecting exception to be thrown when initialising with a bad value
     * Hinting to be dev supplied but null string
     */
    @Test(expected = IllegalStateException.class)
    public void constructorCustom_fail_2() {
        new DeviceId(store, (String) null, mock(ModuleLog.class));
    }

    /**
     * Expecting exception to be thrown when initialising with a bad value
     * Dev supplied type not allowed with this constructor
     */
    @Test(expected = IllegalStateException.class)
    public void constructorSDKGenerated_fail_1() {
        new DeviceId(store, DeviceId.Type.DEVELOPER_SUPPLIED, mock(ModuleLog.class));
    }

    /**
     * Expecting exception to be thrown when initialising with a bad value
     * Null type not allowed
     */
    @Test(expected = IllegalStateException.class)
    public void constructorSDKGenerated_fail_2() {
        new DeviceId(store, (DeviceId.Type) null, mock(ModuleLog.class));
    }

    /**
     * Validating 'deviceIDEqualsNullSafe'
     * Just checking which value combinations return which values
     */
    @Test
    public void deviceIDEqualsNullSafe_1() {
        DeviceId did = new DeviceId(store, "aa", mock(ModuleLog.class));
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
        DeviceId did = new DeviceId(store, "dsd", mock(ModuleLog.class));
        assertFalse(did.temporaryIdModeEnabled());
        did.setId(DeviceId.Type.OPEN_UDID, DeviceId.temporaryCountlyDeviceId);
        assertTrue(did.temporaryIdModeEnabled());

        DeviceId did2 = new DeviceId(store, DeviceId.temporaryCountlyDeviceId, mock(ModuleLog.class));
        assertTrue(did2.temporaryIdModeEnabled());
    }

    /**
     * Validating that the correct type is returned after initialization
     */
    @Test
    public void getType() {
        assertEquals(DeviceId.Type.DEVELOPER_SUPPLIED, new DeviceId(store, "dsd", mock(ModuleLog.class)).getType());
        assertEquals(DeviceId.Type.TEMPORARY_ID, new DeviceId(store, DeviceId.temporaryCountlyDeviceId, mock(ModuleLog.class)).getType());
        assertEquals(DeviceId.Type.OPEN_UDID, new DeviceId(store, DeviceId.Type.OPEN_UDID, mock(ModuleLog.class)).getType());
        assertEquals(DeviceId.Type.ADVERTISING_ID, new DeviceId(store, DeviceId.Type.ADVERTISING_ID, mock(ModuleLog.class)).getType());
    }

    @Test
    public void getId() {
        DeviceId did1 = new DeviceId(store, "abc", mock(ModuleLog.class));
        did1.init(getContext());
        assertEquals("abc", did1.getId());

        store.clear();

        DeviceId did2 = new DeviceId(store, DeviceId.temporaryCountlyDeviceId, mock(ModuleLog.class));
        did2.init(getContext());
        assertEquals(DeviceId.temporaryCountlyDeviceId, did2.getId());

        store.clear();

        DeviceId did3 = new DeviceId(store, DeviceId.Type.OPEN_UDID, mock(ModuleLog.class));
        did3.init(getContext());
        assertNotNull(did3.getId());

        store.clear();

        DeviceId did4 = new DeviceId(store, DeviceId.Type.ADVERTISING_ID, mock(ModuleLog.class));
        did4.init(getContext());
        assertNotNull(did4.getId());
    }
}
