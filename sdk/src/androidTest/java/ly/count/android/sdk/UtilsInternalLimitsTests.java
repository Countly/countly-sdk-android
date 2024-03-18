package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

@RunWith(AndroidJUnit4.class)
public class UtilsInternalLimitsTests {

    /**
     * "truncateKeyLength"
     * Test that the key (test) is truncated to the limit (2)
     * Expected result: "te"
     */
    @Test
    public void truncateKeyLength() {
        String key = "test";
        int limit = 2;

        String truncatedKey = UtilsInternalLimits.truncateKeyLength(key, limit, new ModuleLog(), "tag");
        Assert.assertEquals("te", truncatedKey);
    }

    /**
     * "truncateKeyLength"
     * Test that the key (null) is not truncated
     * Expected result: null
     */
    @Test
    public void truncateKeyLength_null() {
        String key = null;
        int limit = 4;
        ModuleLog spyLog = Mockito.spy(new ModuleLog());

        String truncatedKey = UtilsInternalLimits.truncateKeyLength(key, limit, spyLog, "tag");
        Assert.assertNull(truncatedKey);
        Mockito.verify(spyLog, Mockito.times(1)).d("[UtilsSdkInternalLimits] truncateKeyLength, key is null, returning");
    }

    /**
     * "truncateKeyLength"
     * Test that the key (empty) is not truncated
     * Expected result: empty string
     * Validate empty check log is called
     */
    @Test
    public void truncateKeyLength_empty() {
        String key = "";
        int limit = 4;
        ModuleLog spyLog = Mockito.spy(new ModuleLog());

        String truncatedKey = UtilsInternalLimits.truncateKeyLength(key, limit, spyLog, "tag");
        Assert.assertEquals("", truncatedKey);
        Mockito.verify(spyLog, Mockito.times(1)).d("[UtilsSdkInternalLimits] truncateKeyLength, key is empty, returning");
    }

    /**
     * "truncateKeyLength"
     * Limit is 4
     * Test that the first key (test_test) is truncated
     * Expected result: "test"
     * Test that the second key (test) is not truncated
     * Expected result: "test"
     */
    @Test
    public void truncateKeyLength_multiple() {
        String firstKey = "test_test";
        String secondKey = "test";
        int limit = 4;

        String firstTruncatedKey = UtilsInternalLimits.truncateKeyLength(firstKey, limit, new ModuleLog(), "tag");
        String secondTruncatedKey = UtilsInternalLimits.truncateKeyLength(secondKey, limit, new ModuleLog(), "tag");

        Assert.assertEquals("test", firstTruncatedKey);
        Assert.assertEquals(secondKey, secondTruncatedKey);
    }

    /**
     * "truncateSegmentationKeys"
     * Limit is 5
     * Test that the first key (test_test) is truncated
     * Expected result: "test_", Expected value: "value1"
     * Test that the second key (test) is not truncated
     * Expected result: "test", Expected value: "value2"
     */
    @Test
    public void truncateSegmentationKeys() {
        int limit = 5;
        Map<String, String> map = new ConcurrentHashMap<>();
        map.put("test_test", "value1");
        map.put("test", "value2");

        UtilsInternalLimits.truncateSegmentationKeys(map, limit, new ModuleLog(), "tag");

        Assert.assertEquals("value1", map.get("test_"));
        Assert.assertEquals("value2", map.get("test"));
    }

    /**
     * "truncateSegmentationKeys" with null map
     * Validate null check log is called
     */
    @Test
    public void truncateSegmentationKeys_null() {
        int limit = 5;
        Map<String, String> map = null;

        UtilsInternalLimits.truncateSegmentationKeys(map, limit, new ModuleLog(), "tag");
        Assert.assertNull(map);
    }

    /**
     * "truncateSegmentationKeys" with empty map
     * Validate map is empty
     */
    @Test
    public void truncateSegmentationKeys_empty() {
        int limit = 5;
        Map<String, String> map = new ConcurrentHashMap<>();

        UtilsInternalLimits.truncateSegmentationKeys(map, limit, new ModuleLog(), "tag");
        Assert.assertEquals(0, map.size());
    }

    /**
     * "truncateSegmentationKeys" with same base keys
     * Limit is 4
     * Map has keys "test1", "test2", "test3", "test4", "test5"
     * Resulting map will have only one key, and it is "test"
     * All values are removed and only one value is kept which is the last one what map.entrySet() returns
     */
    @Test
    public void truncateSegmentationKeys_inconsistentKeys() {
        int limit = 4;
        Map<String, String> map = new ConcurrentHashMap<>();
        map.put("test1", TestUtils.eKeys[0]);
        map.put("test2", TestUtils.eKeys[1]);
        map.put("test3", TestUtils.eKeys[2]);
        map.put("test4", TestUtils.eKeys[3]);
        map.put("test5", TestUtils.eKeys[4]);
        ModuleLog spyLog = Mockito.spy(new ModuleLog());

        UtilsInternalLimits.truncateSegmentationKeys(map, limit, spyLog, "tag");
        Assert.assertEquals(1, map.size());
        Assert.assertFalse(Objects.requireNonNull(map.get("test")).isEmpty());
    }
}
