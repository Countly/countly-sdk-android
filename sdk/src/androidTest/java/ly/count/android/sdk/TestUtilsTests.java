package ly.count.android.sdk;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.mockito.Mockito.mock;

@RunWith(AndroidJUnit4.class)
public class TestUtilsTests {

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
     * Validate that RQ is not empty and crash request is present
     * And crash object is valid
     */
    @Test
    public void getCurrentRQ_notEmpty() throws JSONException {
        CountlyStore store = new CountlyStore(ApplicationProvider.getApplicationContext(), mock(ModuleLog.class), false);
        store.clear(); // clear the store to make sure that there are no requests from previous tests

        CountlyConfig config = new CountlyConfig(ApplicationProvider.getApplicationContext(), "123", "https://test");
        Countly.sharedInstance().init(config);
        Countly.sharedInstance().crashes().recordUnhandledException(new Exception("test"));

        Assert.assertEquals(1, TestUtils.getCurrentRQ().length);
        Assert.assertTrue(TestUtils.getCurrentRQ()[0].containsKey("crash"));
        new JSONObject(TestUtils.getCurrentRQ()[0].get("crash"));
    }

    /**
     * "getCurrentRQ" with trash request
     * Validate that RQ is not empty and trash request is present
     * Function should return it because it is a test function
     * Some tests might test trash requests
     */
    @Test
    public void getCurrentRQ_trashRequest() {
        CountlyStore store = new CountlyStore(ApplicationProvider.getApplicationContext(), mock(ModuleLog.class), false);
        store.clear(); // clear the store to make sure that there are no requests from previous tests
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
        CountlyStore store = new CountlyStore(ApplicationProvider.getApplicationContext(), mock(ModuleLog.class), false);
        store.clear(); // clear the store to make sure that there are no requests from previous tests
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
