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

    /**
     * First init where:
     * Device ID is not provided,
     * Temporary ID mode is not provided
     *
     * SDK should generate OPEN_UDID device ID
     */
    @Test
    public void firstInitNothingProvided() {
        countlyStore.clear();
        CountlyConfig cc = new CountlyConfig(getContext(), "aaa", "http://www.aa.bb");
        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertNotNull(countly.getDeviceID());
        Assert.assertEquals(DeviceId.Type.OPEN_UDID, countly.getDeviceIDType());
    }

    /**
     * First init where:
     * Device ID is provided,
     * Temporary ID mode is not provided
     *
     * SDK should use provided device ID
     */
    @Test
    public void firstInitProvidedDeviceId() {
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
     * Device ID is not provided,
     * Temporary ID mode is  provided
     *
     * SDK should enable temporary device ID mode
     */
    @Test
    public void firstInitProvidedTempIdMode() {
        countlyStore.clear();
        CountlyConfig cc = new CountlyConfig(getContext(), "aaa", "http://www.aa.bb");
        cc.enableTemporaryDeviceIdMode();

        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertEquals(DeviceId.temporaryCountlyDeviceId, countly.getDeviceID());
        Assert.assertEquals(DeviceId.Type.TEMPORARY_ID, countly.getDeviceIDType());
    }

    /**
     * First init where:
     * Device ID is provided,
     * Temporary ID mode is also provided
     *
     * SDK should use provided device ID as that takes precedence
     */
    @Test
    public void firstInitProvidedDeviceIdAndTempIdMode() {
        countlyStore.clear();
        CountlyConfig cc = new CountlyConfig(getContext(), "aaa", "http://www.aa.bb");
        cc.setDeviceId("qwe1234");
        cc.enableTemporaryDeviceIdMode();

        Countly countly = new Countly();
        countly.init(cc);

        Assert.assertEquals("qwe1234", countly.getDeviceID());
        Assert.assertEquals(DeviceId.Type.DEVELOPER_SUPPLIED, countly.getDeviceIDType());
    }
}
