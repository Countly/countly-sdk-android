package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import static androidx.test.InstrumentationRegistry.getContext;

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
