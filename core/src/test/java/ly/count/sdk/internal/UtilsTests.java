package ly.count.sdk.internal;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class UtilsTests {
    @Before
    public void setupEveryTest(){
    }

    @After
    public void cleanupEveryTests(){
    }

    @Test
    public void base_64_decodeToString(){
        String decodeSource = "MTIzNDU=";
        String decodeTarget = "12345";

        Assert.assertEquals(decodeTarget, Utils.Base64.decodeToString(decodeSource));
    }

    @Test
    public void base_64_decodeToByte(){
        String decodeSource = "MTIzNDU=";
        String decodeTarget = "12345";
        byte[] decodeTargetBytes = decodeTarget.getBytes();
        byte[] resBytes = Utils.Base64.decode(decodeSource);

        Assert.assertArrayEquals(decodeTargetBytes, resBytes);
    }

    @Test
    public void base_64_encodeByte() {
        String source = "12345";
        byte[] sourceBytes = source.getBytes();
        String resTarget = "MTIzNDU=";

        Assert.assertEquals(resTarget, Utils.Base64.encode(sourceBytes));
    }

    @Test
    public void base_64_encodeString(){
        String source = "12345";
        String resTarget = "MTIzNDU=";

        Assert.assertEquals(resTarget, Utils.Base64.encode(source));
    }
}
