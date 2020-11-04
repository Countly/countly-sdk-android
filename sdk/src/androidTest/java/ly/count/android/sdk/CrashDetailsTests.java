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

import static androidx.test.InstrumentationRegistry.getContext;

@RunWith(AndroidJUnit4.class)
public class CrashDetailsTests {

    @Before
    public void setUp() {
        Countly.sharedInstance().setLoggingEnabled(true);
    }

    @Test
    public void simpleCrashDetails_1() {
        String errorText = "SomeError";
        boolean nonfatal = false;
        boolean isNativeCrash = false;
        String cData = CrashDetails.getCrashData(getContext(), errorText, nonfatal, isNativeCrash, null);

        assertCrashData(cData, errorText, nonfatal, isNativeCrash);
    }

    @Test
    public void simpleCrashDetails_2() {
        String errorText = "SomeError!@##";
        boolean nonfatal = true;
        boolean isNativeCrash = false;
        String cData = CrashDetails.getCrashData(getContext(), errorText, nonfatal, isNativeCrash, null);

        assertCrashData(cData, errorText, nonfatal, isNativeCrash);
    }

    @Test
    public void simpleCrashDetails_3() {
        String errorText = "SomeError65756";
        boolean nonfatal = true;
        boolean isNativeCrash = true;
        String cData = CrashDetails.getCrashData(getContext(), errorText, nonfatal, isNativeCrash, null);

        assertCrashData(cData, errorText, nonfatal, isNativeCrash);
    }

    @Test
    public void simpleCrashDetails_4() {
        String errorText = "SomeErrorsh454353";
        boolean nonfatal = false;
        boolean isNativeCrash = true;
        String cData = CrashDetails.getCrashData(getContext(), errorText, nonfatal, isNativeCrash, null);

        assertCrashData(cData, errorText, nonfatal, isNativeCrash);
    }

    @Test
    public void testAddLogs() {
        String errorText = "fsdfdsfFFFDD";
        boolean nonfatal = false;
        boolean isNativeCrash = false;
        String cData = CrashDetails.getCrashData(getContext(), errorText, nonfatal, isNativeCrash, null);
        assertCrashData(cData, errorText, nonfatal, isNativeCrash);
        Assert.assertFalse(cData.contains("\"logs\":"));

        String[] sArr = TestUtils.createStringArray(8);
        for (String s : sArr) {
            CrashDetails.addLog(s);
        }

        String allLogs = CrashDetails.getLogs();
        for (String s : sArr) {
            allLogs.contains(s);
        }

        for (String s : sArr) {
            CrashDetails.addLog(s);
        }

        String cData2 = CrashDetails.getCrashData(getContext(), errorText, nonfatal, isNativeCrash, null);
        assertCrashData(cData2, errorText, nonfatal, isNativeCrash);
        Assert.assertTrue(cData2.contains("\"_logs\":"));
    }

    @Test
    public void testCustomSegments() {
        String errorText = "SomeError!@##";
        boolean nonfatal = true;
        boolean isNativeCrash = false;
        String cData = CrashDetails.getCrashData(getContext(), errorText, nonfatal, isNativeCrash, null);

        assertCrashData(cData, errorText, nonfatal, isNativeCrash);

        Map<String, Object> cSeg = TestUtils.createMapString(5);

        CrashDetails.setCustomSegments(cSeg);

        String cData2 = CrashDetails.getCrashData(getContext(), errorText, nonfatal, isNativeCrash, null);
        assertCrashData(cData, errorText, nonfatal, isNativeCrash);

        Assert.assertTrue(cData2.contains("_custom"));

        for (Map.Entry<String, Object> entry : cSeg.entrySet()) {
            String key = entry.getKey();
            String value = (String) entry.getValue();

            Assert.assertTrue(cData2.contains(key));
            Assert.assertTrue(cData2.contains(value));
        }

        Map<String, Object> additionalSeg = TestUtils.createMapString(6);
        String cData3 = CrashDetails.getCrashData(getContext(), errorText, nonfatal, isNativeCrash, additionalSeg);
        assertCrashData(cData, errorText, nonfatal, isNativeCrash);

        for (Map.Entry<String, Object> entry : cSeg.entrySet()) {
            String key = entry.getKey();
            String value = (String) entry.getValue();

            Assert.assertTrue(cData3.contains(key));
            Assert.assertTrue(cData3.contains(value));
        }

        for (Map.Entry<String, Object> entry : additionalSeg.entrySet()) {
            String key = entry.getKey();
            String value = (String) entry.getValue();

            Assert.assertTrue(cData3.contains(key));
            Assert.assertTrue(cData3.contains(value));
        }
    }

    @Test
    public void getCustomSegmentsJson() throws JSONException {
        Map<String, Object> cSeg = TestUtils.createMapString(5);
        Map<String, Object> additionalSeg = TestUtils.createMapString(6);

        CrashDetails.setCustomSegments(cSeg);
        JSONObject jobj = CrashDetails.getCustomSegmentsJson(additionalSeg);

        Assert.assertEquals(11, jobj.length());

        for (Map.Entry<String, Object> entry : cSeg.entrySet()) {
            String key = entry.getKey();
            String value = (String) entry.getValue();

            Assert.assertEquals(value, jobj.get(key));
        }

        for (Map.Entry<String, Object> entry : additionalSeg.entrySet()) {
            String key = entry.getKey();
            String value = (String) entry.getValue();

            Assert.assertEquals(value, additionalSeg.get(key));
        }
    }

    @Test
    public void getCustomSegmentsJsonOverlapping() throws JSONException {
        Map<String, Object> cSeg = new HashMap<>();
        cSeg.put("a", 1);
        cSeg.put("a1", 12);
        cSeg.put("a2", true);
        cSeg.put("a3", "fdf");

        Map<String, Object> additionalSeg = new HashMap<>();
        additionalSeg.put("38383", "fdfd");
        additionalSeg.put("a", false);
        additionalSeg.put("a2", "trtr");
        additionalSeg.put("a3", 84.3d);

        CrashDetails.setCustomSegments(cSeg);
        JSONObject jobj = CrashDetails.getCustomSegmentsJson(additionalSeg);

        Assert.assertEquals(5, jobj.length());

        Assert.assertEquals("fdfd", jobj.get("38383"));
        Assert.assertEquals(false, jobj.get("a"));
        Assert.assertEquals("trtr", jobj.get("a2"));
        Assert.assertEquals(12, jobj.get("a1"));
        Assert.assertEquals(84.3d, jobj.get("a3"));
    }

    void assertCrashData(String cData, String error, boolean nonfatal, boolean isNativeCrash) {
        Assert.assertTrue(cData.contains("\"_error\":\"" + error + "\""));
        Assert.assertTrue(cData.contains("\"_nonfatal\":\"" + nonfatal + "\""));
        Assert.assertTrue(cData.contains("\"_os\":\"Android\""));
        Assert.assertTrue(cData.contains("\"_device\":\""));
        Assert.assertTrue(cData.contains("\"_os_version\":\""));
        Assert.assertTrue(cData.contains("\"_resolution\":\""));
        Assert.assertTrue(cData.contains("\"_manufacture\":\""));
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
