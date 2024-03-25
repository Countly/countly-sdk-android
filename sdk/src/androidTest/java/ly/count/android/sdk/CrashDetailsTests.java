package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CrashDetailsTests {

    DeviceInfo regularDeviceInfo;

    @Before
    public void setUp() {
        Countly.sharedInstance().setLoggingEnabled(true);

        regularDeviceInfo = new DeviceInfo(null);
    }

    @Test
    public void simpleCrashDetails_1() {
        String errorText = "SomeError";
        boolean nonfatal = false;
        boolean isNativeCrash = false;
        String cData = createCrashDataStr(errorText, nonfatal, isNativeCrash, null, null);
        assertCrashData(cData, errorText, nonfatal, isNativeCrash);
    }

    @Test
    public void simpleCrashDetails_2() {
        String errorText = "SomeError!@##";
        boolean nonfatal = true;
        boolean isNativeCrash = false;
        String cData = createCrashDataStr(errorText, nonfatal, isNativeCrash, null, null);

        assertCrashData(cData, errorText, nonfatal, isNativeCrash);
    }

    @Test
    public void simpleCrashDetails_3() {
        String errorText = "SomeError65756";
        boolean nonfatal = true;
        boolean isNativeCrash = true;
        String cData = createCrashDataStr(errorText, nonfatal, isNativeCrash, null, null);
        assertCrashData(cData, errorText, nonfatal, isNativeCrash);
    }

    @Test
    public void simpleCrashDetails_4() {
        String errorText = "SomeErrorsh454353";
        boolean nonfatal = false;
        boolean isNativeCrash = true;
        String cData = createCrashDataStr(errorText, nonfatal, isNativeCrash, null, null);
        assertCrashData(cData, errorText, nonfatal, isNativeCrash);
    }

    @Test
    public void testAddLogs() {
        String errorText = "fsdfdsfFFFDD";
        boolean nonfatal = false;
        boolean isNativeCrash = false;
        String cData = createCrashDataStr(errorText, nonfatal, isNativeCrash, null, null);
        assertCrashData(cData, errorText, nonfatal, isNativeCrash);
        Assert.assertFalse(cData.contains("\"logs\":"));

        String[] sArr = TestUtils.createStringArray(8);
        for (String s : sArr) {
            DeviceInfo.addLog(s, 100, 100);
        }

        String allLogs = DeviceInfo.getLogs();
        for (String s : sArr) {
            Assert.assertTrue(allLogs.contains(s));
        }

        for (String s : sArr) {
            DeviceInfo.addLog(s, 100, 100);
        }

        String cData2 = createCrashDataStr(errorText, nonfatal, isNativeCrash, null, null);
        assertCrashData(cData2, errorText, nonfatal, isNativeCrash);
        Assert.assertTrue(cData2.contains("\"_logs\":"));
    }

    @Test
    public void testCustomSegments() {
        String errorText = "SomeError!@##";
        boolean nonfatal = true;
        boolean isNativeCrash = false;
        String cData = createCrashDataStr(errorText, nonfatal, isNativeCrash, null, null);

        assertCrashData(cData, errorText, nonfatal, isNativeCrash);

        Map<String, Object> cSeg = TestUtils.createMapString(5);

        String cData2 = createCrashDataStr(errorText, nonfatal, isNativeCrash, cSeg, null);
        assertCrashData(cData, errorText, nonfatal, isNativeCrash);

        Assert.assertTrue(cData2.contains("_custom"));

        for (Map.Entry<String, Object> entry : cSeg.entrySet()) {
            String key = entry.getKey();
            String value = (String) entry.getValue();

            Assert.assertTrue(cData2.contains(key));
            Assert.assertTrue(cData2.contains(value));
        }
    }

    @Test
    public void getCustomSegmentsJson() throws JSONException {
        Map<String, Object> cSeg = TestUtils.createMapString(5);

        JSONObject jobj = DeviceInfo.getCustomSegmentsJson(cSeg);

        Assert.assertEquals(cSeg.size(), jobj.length());

        for (Map.Entry<String, Object> entry : cSeg.entrySet()) {
            String key = entry.getKey();
            String value = (String) entry.getValue();

            Assert.assertEquals(value, jobj.get(key));
        }
    }

    /**
     * Making sure that retrieving crash metrics takes into account the provided metric overrides
     * It should only set the common values and not any other
     *
     * @throws JSONException
     */
    @Test
    public void crashMetrics_override() throws JSONException {
        String errorText = "SomeError!@##";
        boolean nonfatal = true;
        boolean isNativeCrash = false;

        JSONObject cData = createCrashData(errorText, nonfatal, isNativeCrash, null, null);
        Assert.assertEquals(regularDeviceInfo.mp.getDevice(), cData.getString("_device"));
        Assert.assertEquals(regularDeviceInfo.mp.getOS(), cData.getString("_os"));
        Assert.assertEquals(regularDeviceInfo.mp.getOSVersion(), cData.getString("_os_version"));
        Assert.assertEquals(regularDeviceInfo.mp.getResolution(TestUtils.getContext()), cData.getString("_resolution"));
        Assert.assertEquals(regularDeviceInfo.mp.getAppVersion(TestUtils.getContext()), cData.getString("_app_version"));
        Assert.assertEquals(regularDeviceInfo.mp.getManufacturer(), cData.getString("_manufacturer"));

        Map<String, String> metricOverride = new HashMap<>();
        metricOverride.put("a", "1");
        metricOverride.put("a1", "12");
        metricOverride.put("_device", "112");
        metricOverride.put("_os", "q12");
        metricOverride.put("_os_version", "w12");
        metricOverride.put("_resolution", "e12");
        metricOverride.put("_app_version", "r12");
        metricOverride.put("_manufacturer", "t12");

        JSONObject cData2 = createCrashData(errorText, nonfatal, isNativeCrash, null, metricOverride);
        Assert.assertFalse(cData2.has("a"));
        Assert.assertFalse(cData2.has("a1"));
        Assert.assertEquals(metricOverride.get("_device"), cData2.getString("_device"));
        Assert.assertEquals(metricOverride.get("_os"), cData2.getString("_os"));
        Assert.assertEquals(metricOverride.get("_os_version"), cData2.getString("_os_version"));
        Assert.assertEquals(metricOverride.get("_resolution"), cData2.getString("_resolution"));
        Assert.assertEquals(metricOverride.get("_app_version"), cData2.getString("_app_version"));
        Assert.assertEquals(metricOverride.get("_manufacturer"), cData2.getString("_manufacturer"));
    }

    private JSONObject createCrashData(String errorText, boolean nonfatal, boolean isNativeCrash, Map<String, Object> customSegmentation, Map<String, String> metricOverride) {
        return regularDeviceInfo.getCrashDataJSON(new CrashData(errorText, customSegmentation, DeviceInfo.getLogsAsList(), regularDeviceInfo.getCrashMetrics(TestUtils.getContext(), isNativeCrash, metricOverride), !nonfatal));
    }

    private String createCrashDataStr(String errorText, boolean nonfatal, boolean isNativeCrash, Map<String, Object> customSegmentation, Map<String, String> metricOverride) {
        return regularDeviceInfo.getCrashDataJSON(new CrashData(errorText, customSegmentation, DeviceInfo.getLogsAsList(), regularDeviceInfo.getCrashMetrics(TestUtils.getContext(), isNativeCrash, metricOverride), !nonfatal)).toString();
    }

    void assertCrashData(String cData, String error, boolean nonfatal, boolean isNativeCrash) {
        System.out.println(cData);
        Assert.assertTrue(cData.contains("\"_error\":\"" + error + "\""));
        Assert.assertTrue(cData.contains("\"_nonfatal\":\"" + nonfatal + "\""));
        Assert.assertTrue(cData.contains("\"_os\":\"Android\""));
        Assert.assertTrue(cData.contains("\"_device\":\""));
        Assert.assertTrue(cData.contains("\"_os_version\":\""));
        Assert.assertTrue(cData.contains("\"_resolution\":\""));
        Assert.assertTrue(cData.contains("\"_manufacturer\":\""));
        Assert.assertTrue(cData.contains("\"_cpu\":\""));
        Assert.assertTrue(cData.contains("\"_opengl\":\""));
        Assert.assertTrue(cData.contains("\"_root\":\""));
        Assert.assertTrue(cData.contains("\"_ram_total\":\""));
        Assert.assertTrue(cData.contains("\"_disk_total\":\""));

        if (isNativeCrash) {
            Assert.assertTrue(cData.contains("\"_native_cpp\":true"));
        } else {
            Assert.assertFalse(cData.contains("\"_native_cpp\":true"));

            Assert.assertTrue(cData.contains("\"_ram_current\":\""));
            Assert.assertTrue(cData.contains("\"_disk_current\":\""));
            Assert.assertTrue(cData.contains("\"_bat\":\""));
            Assert.assertTrue(cData.contains("\"_run\":\""));
            Assert.assertTrue(cData.contains("\"_orientation\":\""));
            Assert.assertTrue(cData.contains("\"_muted\":\""));
            Assert.assertTrue(cData.contains("\"_background\":\""));
        }
    }
}
