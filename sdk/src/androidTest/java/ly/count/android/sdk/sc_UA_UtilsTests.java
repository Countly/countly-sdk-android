package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class sc_UA_UtilsTests {

    /**
     * Make sure the random value generator matches the required pattern
     */
    @Test
    public void t001_validatingIDGenerator() {
        String base64Regex = "^[A-Za-z0-9+/]*={0,2}$";

        @NonNull String result1 = Utils.safeRandomVal();
        @NonNull String result2 = Utils.safeRandomVal();

        Assert.assertNotNull(result1);
        Assert.assertNotNull(result2);
        Assert.assertTrue(result1.matches(base64Regex));
        Assert.assertTrue(result2.matches(base64Regex));
        Assert.assertEquals(21, result1.length(), result2.length());
        Assert.assertNotEquals(result1, result2);
    }
}
