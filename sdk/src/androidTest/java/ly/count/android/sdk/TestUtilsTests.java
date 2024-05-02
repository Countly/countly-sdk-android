package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TestUtilsTests {

    @Before
    public void setUp() {
        CountlyStore store = TestUtils.getCountyStore();
        store.clear(); // clear the store to make sure that there are no requests from previous tests
    }

    /**
     * "getCurrentRQ" without countly initialization
     * Validate that RQ is empty
     */
    @Test
    public void getCurrentRQ() {
        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);
    }

    /**
     * "getCurrentRQ" with countly initialization
     * Validate that RQ is not empty and request is present
     * And request is parsed correctly
     */
    @Test
    public void getCurrentRQ_notEmpty() {
        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);

        TestUtils.getCountyStore().addRequest("a=b&c=d&hi=7628y9u0%C4%B1oh&fiyua=5765", true);

        Assert.assertEquals(1, TestUtils.getCurrentRQ().length);
        Map<String, String> request = TestUtils.getCurrentRQ()[0];

        Assert.assertEquals(4, request.size());
        Assert.assertEquals("b", request.get("a"));
        Assert.assertEquals("d", request.get("c"));
        Assert.assertEquals("7628y9u0ıoh", request.get("hi"));
        Assert.assertEquals("5765", request.get("fiyua"));
    }

    /**
     * "getCurrentRQ" with trash request
     * Validate that RQ is not empty and trash request is present
     * Function should return it because it is a test function
     * Some tests might test trash requests
     */
    @Test
    public void getCurrentRQ_trashRequest() {
        CountlyStore store = TestUtils.getCountyStore();
        store.addRequest("This is not a request", true);
        Assert.assertEquals(1, TestUtils.getCurrentRQ().length);
        Assert.assertEquals("", TestUtils.getCurrentRQ()[0].get("This is not a request"));
    }

    /**
     * "getCurrentRQ" with wrong structure
     * Validate that RQ is not empty and wrongly structured request is present
     * Function should return it because it is a test function
     * Some tests might test wrong structure requests
     */
    @Test
    public void getCurrentRQ_wrongStructure() {
        CountlyStore store = TestUtils.getCountyStore();
        store.addRequest("&s==1", true);
        Assert.assertEquals(1, TestUtils.getCurrentRQ().length);
        Assert.assertNull(TestUtils.getCurrentRQ()[0].get("="));
        Assert.assertNull(TestUtils.getCurrentRQ()[0].get("s="));
    }

    @Test
    public void map_null() {
        Assert.assertEquals(0, TestUtils.map(null, null).size());
    }

    @Test
    public void map_empty() {
        Assert.assertEquals(0, TestUtils.map().size());
    }

    @Test
    public void map() {
        Assert.assertEquals(1, TestUtils.map("key", "value").size());
    }

    @Test
    public void map_notEven() {
        Assert.assertEquals(0, TestUtils.map("key", "value", "key2", "value2", "sad").size());
    }

    @Test
    public void map_notStringKey() {
        Map<String, Object> map = TestUtils.map(876, "value");
        Assert.assertEquals(1, map.size());
        Assert.assertEquals("value", map.get("876"));
    }
}
