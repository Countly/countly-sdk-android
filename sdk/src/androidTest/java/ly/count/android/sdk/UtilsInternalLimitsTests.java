package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import static org.mockito.Mockito.mock;

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
        Mockito.verify(spyLog, Mockito.times(1)).w("tag: [UtilsSdkInternalLimits] truncateKeyLength, value is null, returning");
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
        Mockito.verify(spyLog, Mockito.times(1)).w("tag: [UtilsSdkInternalLimits] truncateKeyLength, value is empty, returning");
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

    /**
     * Make sure that nothing bad happens when providing null segmentation
     */
    @Test
    public void truncateSegmentationValues_null() {
        UtilsInternalLimits.truncateSegmentationValues(null, 10, "someTag", mock(ModuleLog.class));
        Assert.assertTrue(true);
    }

    /**
     * Make sure that nothing bad happens when providing empty segmentation
     */
    @Test
    public void truncateSegmentationValues_empty() {
        Map<String, Object> values = new HashMap<>();
        UtilsInternalLimits.truncateSegmentationValues(values, 10, "someTag", mock(ModuleLog.class));
        Assert.assertTrue(true);
    }

    /**
     * Make sure that nothing bad happens when providing segmentation with values under limit
     */
    @Test
    public void truncateSegmentationValues_underLimit() {
        Map<String, Object> values = new HashMap<>();
        values.put("a1", "1");
        values.put("a2", "2");
        values.put("a3", "3");
        values.put("a4", "4");
        UtilsInternalLimits.truncateSegmentationValues(values, 6, "someTag", mock(ModuleLog.class));

        Assert.assertEquals(4, values.size());
        Assert.assertEquals("1", values.get("a1"));
        Assert.assertEquals("2", values.get("a2"));
        Assert.assertEquals("3", values.get("a3"));
        Assert.assertEquals("4", values.get("a4"));
    }

    /**
     * Make sure that values are truncated when they are more then the limit
     */
    @Test
    public void truncateSegmentationValues_aboveLimit() {
        Map<String, Object> values = new HashMap<>();
        values.put("a1", "1");
        values.put("a2", "2");
        values.put("a3", "3");
        values.put("a4", "4");
        UtilsInternalLimits.truncateSegmentationValues(values, 2, "someTag", mock(ModuleLog.class));

        Assert.assertEquals(2, values.size());
        //after inspecting what is returned in the debugger, it should have the values of "a2" and "a4"
        //Assert.assertEquals("2", values.get("a2"));
        //Assert.assertEquals("4", values.get("a4"));
    }

    @Test
    public void removeReservedKeysFromSegmentation() {
        Map<String, Object> values = new HashMap<>();

        UtilsInternalLimits.removeReservedKeysFromSegmentation(values, new String[] {}, "", mock(ModuleLog.class));
        Assert.assertEquals(0, values.size());

        UtilsInternalLimits.removeReservedKeysFromSegmentation(values, new String[] { "a", "", null }, "", mock(ModuleLog.class));
        Assert.assertEquals(0, values.size());

        values.put("b", 1);
        Assert.assertEquals(1, values.size());
        UtilsInternalLimits.removeReservedKeysFromSegmentation(values, new String[] { "a", "a1", "", null }, "", mock(ModuleLog.class));
        Assert.assertEquals(1, values.size());
        Assert.assertTrue(values.containsKey("b"));

        values.put("a", 2);
        Assert.assertEquals(2, values.size());
        UtilsInternalLimits.removeReservedKeysFromSegmentation(values, new String[] { "a", "a1", "", null }, "", mock(ModuleLog.class));
        Assert.assertEquals(1, values.size());
        Assert.assertTrue(values.containsKey("b"));

        values.put("a", 2);
        values.put("c", 3);
        Assert.assertEquals(3, values.size());
        UtilsInternalLimits.removeReservedKeysFromSegmentation(values, new String[] { "a", "a1", "", null }, "", mock(ModuleLog.class));
        Assert.assertEquals(2, values.size());
        Assert.assertTrue(values.containsKey("b"));
        Assert.assertTrue(values.containsKey("c"));
    }

    @Test
    public void removeUnsupportedDataTypesNull() {
        Assert.assertFalse(UtilsInternalLimits.removeUnsupportedDataTypes(null));
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

        Assert.assertTrue(UtilsInternalLimits.removeUnsupportedDataTypes(segm));

        Assert.assertTrue(segm.containsKey("aa"));
        Assert.assertTrue(segm.containsKey("aa1"));
        Assert.assertTrue(segm.containsKey("1"));
        Assert.assertTrue(segm.containsKey("2"));
        Assert.assertTrue(segm.containsKey("3"));
        Assert.assertTrue(segm.containsKey("4"));
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

        Assert.assertTrue(UtilsInternalLimits.removeUnsupportedDataTypes(segm));

        Assert.assertEquals(0, segm.size());

        segm.put(null, null);
        segm.put("1", "dd");
        segm.put("2", 123);
        segm.put("", null);
        segm.put("3", 345.33d);
        segm.put("4", false);
        segm.put("aa1", new String[] { "ff", "33" });

        Assert.assertEquals(7, segm.size());

        Assert.assertTrue(UtilsInternalLimits.removeUnsupportedDataTypes(segm));

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

    @Test
    public void isSupportedDataType() {
        Assert.assertTrue(UtilsInternalLimits.isSupportedDataType("string"));
        Assert.assertTrue(UtilsInternalLimits.isSupportedDataType(123));
        Assert.assertTrue(UtilsInternalLimits.isSupportedDataType(123.33d));
        Assert.assertTrue(UtilsInternalLimits.isSupportedDataType(123.33f));
        Assert.assertTrue(UtilsInternalLimits.isSupportedDataType(true));
        Assert.assertTrue(UtilsInternalLimits.isSupportedDataType(false));
        Assert.assertFalse(UtilsInternalLimits.isSupportedDataType(new Object()));
        Assert.assertFalse(UtilsInternalLimits.isSupportedDataType(new int[] { 1, 2 }));
        Assert.assertFalse(UtilsInternalLimits.isSupportedDataType(null));
    }

    @Test
    public void truncateValueSize() {
        String value = "test";
        int limit = 2;

        String truncatedValue = UtilsInternalLimits.truncateValueSize(value, limit, new ModuleLog(), "tag");
        Assert.assertEquals("te", truncatedValue);
    }

    @Test
    public void truncateValueSize_null() {
        String value = null;
        int limit = 4;
        ModuleLog spyLog = Mockito.spy(new ModuleLog());

        String truncatedValue = UtilsInternalLimits.truncateValueSize(value, limit, spyLog, "tag");
        Assert.assertNull(truncatedValue);
        Mockito.verify(spyLog, Mockito.times(1)).w("tag: [UtilsSdkInternalLimits] truncateValueSize, value is null, returning");
    }

    @Test
    public void truncateValueSize_empty() {
        String value = "";
        int limit = 4;
        ModuleLog spyLog = Mockito.spy(new ModuleLog());

        String truncatedValue = UtilsInternalLimits.truncateValueSize(value, limit, spyLog, "tag");
        Assert.assertEquals("", truncatedValue);
        Mockito.verify(spyLog, Mockito.times(1)).w("tag: [UtilsSdkInternalLimits] truncateValueSize, value is empty, returning");
    }

    @Test
    public void truncateValueSize_multiple() {
        String firstValue = "test_test";
        String secondValue = "test";
        int limit = 4;

        String firstTruncatedValue = UtilsInternalLimits.truncateValueSize(firstValue, limit, new ModuleLog(), "tag");
        String secondTruncatedValue = UtilsInternalLimits.truncateValueSize(secondValue, limit, new ModuleLog(), "tag");

        Assert.assertEquals("test", firstTruncatedValue);
        Assert.assertEquals(secondValue, secondTruncatedValue);
    }

    @Test
    public void applySdkInternalLimitsToSegmentation() {
        Map<String, Object> segmentation = new ConcurrentHashMap<>();
        segmentation.put("test_test", "value1");
        segmentation.put("test", "value2");
        segmentation.put("hobbit", 456789);
        segmentation.put("map_to", 45.678f);
        segmentation.put("map_too", TestUtils.map("a", 1));
        segmentation.put("abcdefg", "12345");

        ConfigSdkInternalLimits limitsConfig = new ConfigSdkInternalLimits()
            .setMaxKeyLength(5)
            .setMaxValueSize(2)
            .setMaxSegmentationValues(3);

        UtilsInternalLimits.applySdkInternalLimitsToSegmentation(segmentation, limitsConfig, new ModuleLog(), "tag");

        Assert.assertEquals(3, segmentation.size());
        Assert.assertEquals("12", segmentation.get("abcde"));
        Assert.assertEquals("va", segmentation.get("test_"));
        Assert.assertEquals(45.678f, segmentation.get("map_t"));
    }

    @Test
    public void applySdkInternalLimitsToSegmentation_null() {
        Map<String, Object> segmentation = null;
        ConfigSdkInternalLimits limitsConfig = new ConfigSdkInternalLimits()
            .setMaxKeyLength(5)
            .setMaxValueSize(2)
            .setMaxSegmentationValues(3);

        UtilsInternalLimits.applySdkInternalLimitsToSegmentation(segmentation, limitsConfig, new ModuleLog(), "tag");
        Assert.assertNull(segmentation);
    }

    @Test
    public void applySdkInternalLimitsToSegmentation_empty() {
        Map<String, Object> segmentation = new ConcurrentHashMap<>();
        ConfigSdkInternalLimits limitsConfig = new ConfigSdkInternalLimits()
            .setMaxKeyLength(5)
            .setMaxValueSize(2)
            .setMaxSegmentationValues(3);

        UtilsInternalLimits.applySdkInternalLimitsToSegmentation(segmentation, limitsConfig, new ModuleLog(), "tag");
        Assert.assertEquals(0, segmentation.size());
    }
}
