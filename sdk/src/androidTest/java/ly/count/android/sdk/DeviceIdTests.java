package ly.count.android.sdk;

import android.bluetooth.BluetoothClass;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.InstrumentationRegistry.getContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class DeviceIdTests {
    CountlyStore store;

    @Before
    public void setUp() {
        Countly.sharedInstance().setLoggingEnabled(true);
        store = new CountlyStore(getContext());
        store.clear();
    }

    @After
    public void tearDown() {
        store.clear();
    }

    @Test(expected = IllegalStateException.class)
    public void constructorCustom_fail_1() {
        new DeviceId(store, "");
    }

    @Test(expected = IllegalStateException.class)
    public void constructorCustom_fail_2() {
        new DeviceId(store, (String) null);
    }

    @Test(expected = IllegalStateException.class)
    public void constructorSDKGenerated_fail_1() {
        new DeviceId(store, DeviceId.Type.DEVELOPER_SUPPLIED);
    }

    @Test(expected = IllegalStateException.class)
    public void constructorSDKGenerated_fail_2() {
        new DeviceId(store, (DeviceId.Type) null);
    }

    @Test
    public void deviceIDEqualsNullSafe_1() {
        DeviceId did = new DeviceId(store, "aa");
        assertTrue(DeviceId.deviceIDEqualsNullSafe("a", DeviceId.Type.OPEN_UDID,did));
        assertTrue(DeviceId.deviceIDEqualsNullSafe("a", DeviceId.Type.TEMPORARY_ID,did));
        assertTrue(DeviceId.deviceIDEqualsNullSafe("a", DeviceId.Type.ADVERTISING_ID,did));

        did.setId(DeviceId.Type.OPEN_UDID, null);
        assertTrue(DeviceId.deviceIDEqualsNullSafe(null, DeviceId.Type.ADVERTISING_ID, did));

        did.setId(DeviceId.Type.OPEN_UDID, "b");
        assertFalse(DeviceId.deviceIDEqualsNullSafe("a", DeviceId.Type.DEVELOPER_SUPPLIED, did));

        did.setId(DeviceId.Type.OPEN_UDID, "a");
        assertTrue(DeviceId.deviceIDEqualsNullSafe("a", DeviceId.Type.DEVELOPER_SUPPLIED, did));
    }

    @Test
    public void temporaryIdModeEnabled() {
        DeviceId did = new DeviceId(store, "dsd");
        assertFalse(did.temporaryIdModeEnabled());
        did.setId(DeviceId.Type.OPEN_UDID, DeviceId.temporaryCountlyDeviceId);
        assertTrue(did.temporaryIdModeEnabled());

        DeviceId did2 = new DeviceId(store, DeviceId.temporaryCountlyDeviceId);
        assertTrue(did2.temporaryIdModeEnabled());
    }

    @Test
    public void getType() {
        assertEquals(DeviceId.Type.DEVELOPER_SUPPLIED, new DeviceId(store, "dsd").getType());
        assertEquals(DeviceId.Type.TEMPORARY_ID, new DeviceId(store, DeviceId.temporaryCountlyDeviceId).getType());
        assertEquals(DeviceId.Type.OPEN_UDID, new DeviceId(store, DeviceId.Type.OPEN_UDID).getType());
        assertEquals(DeviceId.Type.ADVERTISING_ID, new DeviceId(store, DeviceId.Type.ADVERTISING_ID).getType());
    }
}
