package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

@RunWith(AndroidJUnit4.class)
public class TemporaryIDTests {
    CountlyStore store;
    StorageProvider sp;

    @Before
    public void setUp() {
        Countly.sharedInstance().setLoggingEnabled(true);
        store = new CountlyStore(TestUtils.getContext(), mock(ModuleLog.class), false);
        sp = store;
        store.clear();
    }

    @After
    public void tearDown() {
        store.clear();
    }

    //String temporaryIdOverrideTag = "&override_id=" + DeviceId.temporaryCountlyDeviceId;
    //String temporaryIdTag = "&device_id=" + DeviceId.temporaryCountlyDeviceId;

    @NonNull String[] CreateInitialTmpIDState(@NonNull String did, @NonNull DeviceIdType dType, @NonNull String replaceDid, @NonNull CountlyStore cStore) {
        String req1 = "aa=45&device_id=" + DeviceId.temporaryCountlyDeviceId;
        String req2 = "12=qw&device_id=55";
        String req3 = "68=45&device_id=" + DeviceId.temporaryCountlyDeviceId + "&ff=bb";

        cStore.addRequest(req1, false);
        cStore.addRequest(req2, false);
        cStore.addRequest(req3, false);

        cStore.setDeviceID(did);
        cStore.setDeviceIDType(dType.toString());

        String[] reqs1 = cStore.getRequests();

        assertEquals(req1, reqs1[0]);
        assertEquals(req2, reqs1[1]);
        assertEquals(req3, reqs1[2]);

        String[] ret = { req1, req2, req3 };

        for (int a = 0; a < ret.length; a++) {
            ret[a] = ret[a].replace("&device_id=" + DeviceId.temporaryCountlyDeviceId, "&device_id=" + replaceDid);
        }

        return ret;
    }

    @Test
    public void rqWithTempInitWithNoID() {
        String[] ret = CreateInitialTmpIDState("abc", DeviceIdType.OPEN_UDID, "abc", store);

        Countly mCountly = new Countly();
        mCountly.init(new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setLoggingEnabled(true));

        String[] reqs = store.getRequests();
        assertArrayEquals(ret, reqs);
    }

    /*
     * RQ has requests with temp ID, and some with a device ID
     * SDK has acquired a device ID before init.
     * During init no device ID is provided.
     *
     * After init the RQ requests with temp ID should be changed to the stored ID
     */
    @Test
    public void rqWithTempInitWithTemp() {
        String[] ret = CreateInitialTmpIDState("abc", DeviceIdType.OPEN_UDID, "abc", store);

        Countly mCountly = new Countly();
        mCountly.init(new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setLoggingEnabled(true).enableTemporaryDeviceIdMode());

        String[] reqs = store.getRequests();
        assertArrayEquals(ret, reqs);
    }

    @Test
    public void rqWithTempInitWithCustomID() {
        String[] ret = CreateInitialTmpIDState("abc", DeviceIdType.OPEN_UDID, "abc", store);

        Countly mCountly = new Countly();
        mCountly.init(new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setLoggingEnabled(true).setDeviceId("a123d"));

        String[] reqs = store.getRequests();
        assertArrayEquals(ret, reqs);
    }
}
