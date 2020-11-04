package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class UtilsNetworkingTest {

    @Before
    public void setUp() {
        Countly.sharedInstance().setLoggingEnabled(true);
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testIsValidUrl() {
        Assert.assertFalse(UtilsNetworking.isValidURL(""));
        Assert.assertFalse(UtilsNetworking.isValidURL(null));
        Assert.assertFalse(UtilsNetworking.isValidURL("113"));
        Assert.assertFalse(UtilsNetworking.isValidURL("sdfsdfgdg dfsd sdf dsf sdf"));
        Assert.assertFalse(UtilsNetworking.isValidURL("www.d.dd"));

        Assert.assertTrue(UtilsNetworking.isValidURL("http:/www.d.dd"));
        Assert.assertTrue(UtilsNetworking.isValidURL("http://www.d.dd"));
        Assert.assertTrue(UtilsNetworking.isValidURL("https://www.d.dd"));
    }

    @Test
    public void testEncodeDecode() {
        String[] list = new String[] { "132", "āšēŗŗ", "&#(%^$(&#^@$%$&!_)@(*#_$", " ds fdsf 8ds7f0d&)(^ F*(D&F%S( SD%(F" };

        for (String item : list) {
            Assert.assertEquals(UtilsNetworking.urlDecodeString(UtilsNetworking.urlEncodeString(item)), item);
        }
    }

    @Test
    public void testEncode() {
        String a = "&#(%^$(&#^@$%$&!_)@(_$";
        String b = "%26%23%28%25%5E%24%28%26%23%5E%40%24%25%24%26%21_%29%40%28_%24";

        Assert.assertEquals(UtilsNetworking.urlEncodeString(a), b);
    }

    @Test
    public void testDecode() {
        String a = "GFģšģīūŗģ($^#^$";
        String b = "GF%C4%A3%C5%A1%C4%A3%C4%AB%C5%AB%C5%97%C4%A3%28%24%5E%23%5E%24";

        Assert.assertEquals(UtilsNetworking.urlDecodeString(b), a);
    }

    @Test
    public void testSHA256() {
        String[] list_a = new String[] { "435687gyhfdgfdhg784engģšģģ€»€–’€’€’€–’€’–", "908hdg234-hbFDSFSREģšņēŗ", "908hdg sfdg 234-hbFdsfg D \"\"::$#F GF !@#$%^&*()+_ fdsh fghg" };
        String[] list_b = new String[] { "c641f7596a2959479d66f5b1ff9d11b5aaa24c185e27a636de242fab1e19d924", "3d8ad9c4c9194e2e1be44b408849bc4bad1c2624196440d016e14217ce2d5d24", "78cb302c277088418b8c91332eac2e336f6de107f3a336ddf05333b74778b92c" };

        for (int a = 0; a < list_a.length; a++) {
            Assert.assertEquals(UtilsNetworking.sha256Hash(list_a[a]), list_b[a]);
        }
    }
}
