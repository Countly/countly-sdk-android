package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class UtilsTests {

    @Before
    public void setUp(){
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

    @Test
    public void testJoin() {
        List<String> a = new ArrayList<>();
        a.add("a");
        a.add("b");
        a.add("cd");
        String res = Utils.join(a, "-");

        Assert.assertEquals(res, "a-b-cd");
    }

    @Test
    public void testAPI() {
        Assert.assertTrue(Utils.API(28));
        Assert.assertTrue(Utils.API(27));
        Assert.assertTrue(Utils.API(15));

        Assert.assertFalse(Utils.API(32));
    }

    @Test
    public void removeKeysFromMapNullBoth(){
        Map<String, Object> res = Utils.removeKeysFromMap(null, null);
        Assert.assertNull(res);
    }

    @Test
    public void removeKeysFromMapNullKeys(){
        Map<String, Object> res = Utils.removeKeysFromMap(null, new String[]{"1", "2"});
        Assert.assertNull(res);
    }

    @Test
    public void removeKeysFromMapNullData(){
        Map<String, Object> map = new HashMap<>();
        map.put("1", "ff");
        map.put("2", "ee");
        map.put("3", "kk");
        map.put("4", "fer");

        Map<String, Object> res = Utils.removeKeysFromMap(map, null);
        Assert.assertEquals(map, res);
    }

    @Test
    public void removeKeysFromMap(){
        Map<String, Object> map = new HashMap<>();
        map.put("1", "ff");
        map.put("2", "ee");
        map.put("3", "kk");
        map.put("4", "fer");

        String[] keys = new String[] {"1", "3"};

        Map<String, Object> res = Utils.removeKeysFromMap(map, keys);
        Assert.assertEquals(2, res.size());
        Assert.assertTrue(res.containsKey("2"));
        Assert.assertTrue(res.containsKey("4"));
    }
}
