package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class CrashDetailsTests {

    @Test
    public void simpleCrashDetails_1(){
        String errorText = "SomeError";
        boolean nonfatal = false;
        boolean isNativeCrash = false;
        String cData = CrashDetails.getCrashData(getContext(), errorText, nonfatal, isNativeCrash);

        assertCrashData(cData, errorText, nonfatal, isNativeCrash);
    }

    @Test
    public void simpleCrashDetails_2(){
        String errorText = "SomeError!@##";
        boolean nonfatal = true;
        boolean isNativeCrash = false;
        String cData = CrashDetails.getCrashData(getContext(), errorText, nonfatal, isNativeCrash);

        assertCrashData(cData, errorText, nonfatal, isNativeCrash);
    }

    @Test
    public void simpleCrashDetails_3(){
        String errorText = "SomeError65756";
        boolean nonfatal = true;
        boolean isNativeCrash = true;
        String cData = CrashDetails.getCrashData(getContext(), errorText, nonfatal, isNativeCrash);

        assertCrashData(cData, errorText, nonfatal, isNativeCrash);
    }

    @Test
    public void simpleCrashDetails_4(){
        String errorText = "SomeErrorsh454353";
        boolean nonfatal = false;
        boolean isNativeCrash = true;
        String cData = CrashDetails.getCrashData(getContext(), errorText, nonfatal, isNativeCrash);

        assertCrashData(cData, errorText, nonfatal, isNativeCrash);
    }

    @Test
    public void testAddLogs(){
        String errorText = "fsdfdsfFFFDD";
        boolean nonfatal = false;
        boolean isNativeCrash = false;
        String cData = CrashDetails.getCrashData(getContext(), errorText, nonfatal, isNativeCrash);
        assertCrashData(cData, errorText, nonfatal, isNativeCrash);
        Assert.assertFalse(cData.contains("\"logs\":"));

        String [] sArr = TestUtils.createStringArray(8);
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

        String cData2 = CrashDetails.getCrashData(getContext(), errorText, nonfatal, isNativeCrash);
        assertCrashData(cData2, errorText, nonfatal, isNativeCrash);
        Assert.assertTrue(cData2.contains("\"_logs\":"));
    }

    @Test
    public void testCustomSegments(){
        String errorText = "SomeError!@##";
        boolean nonfatal = true;
        boolean isNativeCrash = false;
        String cData = CrashDetails.getCrashData(getContext(), errorText, nonfatal, isNativeCrash);

        assertCrashData(cData, errorText, nonfatal, isNativeCrash);

        Map<String, String> cSeg = TestUtils.createMapString(5);

        CrashDetails.setCustomSegments(cSeg);

        String cData2 = CrashDetails.getCrashData(getContext(), errorText, nonfatal, isNativeCrash);
        assertCrashData(cData, errorText, nonfatal, isNativeCrash);

        Assert.assertTrue(cData2.contains("_custom"));

        for(Map.Entry<String, String> entry : cSeg.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            Assert.assertTrue(cData2.contains(key));
            Assert.assertTrue(cData2.contains(value));
        }
    }

    void assertCrashData(String cData, String error, boolean nonfatal, boolean isNativeCrash){
        Assert.assertTrue(cData.contains("\"_error\":\"" + error + "\""));
        Assert.assertTrue(cData.contains("\"_nonfatal\":\"" + nonfatal+ "\""));
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

        if(isNativeCrash) {
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
