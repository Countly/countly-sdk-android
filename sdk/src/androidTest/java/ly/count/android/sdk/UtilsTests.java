package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class UtilsTests {

    @Before
    public void setUp() {
        Countly.sharedInstance().setLoggingEnabled(true);
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testIsEmpty() {
        Assert.assertTrue(Utils.isEmpty(""));
        Assert.assertTrue(Utils.isEmpty(null));

        Assert.assertFalse(Utils.isEmpty("aa"));
        Assert.assertFalse(Utils.isEmpty("1123āšē"));
    }

    @Test
    public void testIsNotEmpty() {
        Assert.assertFalse(Utils.isNotEmpty(""));
        Assert.assertFalse(Utils.isNotEmpty(null));

        Assert.assertTrue(Utils.isNotEmpty("aa"));
        Assert.assertTrue(Utils.isNotEmpty("1123āšē"));
    }

    /**
     * A simple verification that 'join' is performing as expected
     */
    @Test
    public void testJoin() {
        List<String> a = new ArrayList<>();
        a.add("a");
        a.add("b");
        a.add("cd");
        String res = Utils.join(a, "-");

        Assert.assertEquals(res, "a-b-cd");
    }

    /**
     * A simple verification that 'joinCountlyStore' is performing as expected
     */
    @Test
    public void joinCountlyStore() {
        List<String> a = new ArrayList<>();
        a.add("a");
        a.add("11b");
        a.add("cd22");
        String res = Utils.joinCountlyStore(a, ";");
        Assert.assertEquals(res, "a;11b;cd22");
    }

    @Test
    public void testAPI() {
        Assert.assertTrue(Utils.API(28));
        Assert.assertTrue(Utils.API(27));
        Assert.assertTrue(Utils.API(15));

        Assert.assertFalse(Utils.API(32));
    }

    @Test
    public void removeUnsupportedDataTypesNull() {
        Assert.assertFalse(Utils.removeUnsupportedDataTypes(null));
    }

    @Test
    public void removeUnsupportedDataTypes() {
        Map<String, Object> segm = new HashMap<>();

        segm.put("aa", "dd");
        segm.put("aa1", "dda");
        segm.put("1", 1234);
        segm.put("2", 1234.55d);
        segm.put("3", true);
        segm.put("4", 45.4f);
        segm.put("41", new Object());
        segm.put("42", new int[] { 1, 2 });

        Assert.assertTrue(Utils.removeUnsupportedDataTypes(segm));

        Assert.assertTrue(segm.containsKey("aa"));
        Assert.assertTrue(segm.containsKey("aa1"));
        Assert.assertTrue(segm.containsKey("1"));
        Assert.assertTrue(segm.containsKey("2"));
        Assert.assertTrue(segm.containsKey("3"));
        Assert.assertFalse(segm.containsKey("4"));
        Assert.assertFalse(segm.containsKey("41"));
        Assert.assertFalse(segm.containsKey("42"));
    }

    @Test
    public void removeUnsupportedDataTypes2() {
        Map<String, Object> segm = new HashMap<>();

        segm.put("", "dd");
        segm.put(null, "dda");
        segm.put("aa", null);

        Assert.assertEquals(3, segm.size());

        Assert.assertTrue(Utils.removeUnsupportedDataTypes(segm));

        Assert.assertEquals(0, segm.size());

        segm.put(null, null);
        segm.put("1", "dd");
        segm.put("2", 123);
        segm.put("", null);
        segm.put("3", 345.33d);
        segm.put("4", false);
        segm.put("aa1", new String[] { "ff", "33" });

        Assert.assertEquals(7, segm.size());

        Assert.assertTrue(Utils.removeUnsupportedDataTypes(segm));

        Assert.assertEquals(4, segm.size());
        Assert.assertTrue(segm.containsKey("1"));
        Assert.assertTrue(segm.containsKey("2"));
        Assert.assertTrue(segm.containsKey("3"));
        Assert.assertTrue(segm.containsKey("4"));
        Assert.assertEquals("dd", segm.get("1"));
        Assert.assertEquals(123, segm.get("2"));
        Assert.assertEquals(345.33d, segm.get("3"));
        Assert.assertEquals(false, segm.get("4"));
    }
}
