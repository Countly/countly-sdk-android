package ly.count.sdk.internal;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ParamsTests {
    @Before
    public void setupEveryTest(){
    }

    @After
    public void cleanupEveryTests(){
    }

    @Test
    public void setup_default(){
        Params params = new Params();

        Assert.assertEquals(0, params.length());
        Assert.assertEquals("", params.toString());
    }

    @Test
    public void setup_string(){
        String value = "abcd";
        Params params = new Params(value);

        Assert.assertEquals(value.length(), params.length());
        Assert.assertEquals(value, params.toString());

        params.clear();

        Assert.assertEquals("", params.toString());
    }

    @Test (expected = NullPointerException.class)
    public void setup_null(){
        String value = null;
        Params params = new Params(value);
    }

    @Test
    public void setup_objects(){
        String[] vals = new String[] {"232", "fds", "tyty", "844"};
        Params params = new Params(vals[0], vals[1], vals[2], vals[3]);

        Assert.assertEquals(vals[0].length() + vals[1].length() + vals[2].length() + vals[3].length() + 3, params.length());
        Assert.assertEquals(vals[0] + "=" + vals[1] + "&" + vals[2] + "=" + vals[3], params.toString());
    }

    @Test
    public void add_keyValue(){
        String key = "key";
        String value = "value";

        Params params = new Params();
        params.add(key, value);
        String combined = key + "=" + value;

        Assert.assertEquals(combined.length(), params.length());
        Assert.assertEquals(combined, params.toString());
    }

    @Test
    public void add_object(){
        String key = "key";
        String value = "value";
        String combined = key + "=" + value;

        Params params = new Params();
        params.add(key, value);

        Assert.assertEquals(combined.length(), params.length());
        Assert.assertEquals(combined, params.toString());
    }

    @Test
    public void add_objects(){
        String[] keys = new String[] {"key", "other_key"};
        String[] values = new String[] {"value", "other_value"};
        String[] combined = new String[] {keys[0] + "=" + values[0], keys[1] + "=" + values[1]};

        Params params = new Params();
        params.add(keys[0], values[0], keys[1], values[1]);

        String combinedParams = combined[0] + "&" + combined[1];

        Assert.assertEquals(combinedParams.length(), params.length());
        Assert.assertEquals(combinedParams, params.toString());
    }

    @Test
    public void add_params(){
        String[] vals = new String[] {"abc", "123"};
        String combined = vals[0] + "&" + vals[1];
        Params params1 = new Params(vals[0]);
        Params params2 = new Params(vals[1]);

        Params params = new Params();
        params.add(params1);
        Assert.assertEquals(vals[0].length(), params.length());
        Assert.assertEquals(vals[0], params.toString());

        params.add(params2);
        Assert.assertEquals(combined.length(), params.length());
        Assert.assertEquals(combined, params.toString());
    }
}