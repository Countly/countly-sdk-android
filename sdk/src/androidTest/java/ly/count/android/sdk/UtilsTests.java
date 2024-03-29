package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;

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
    public void isEmpty() {
        Assert.assertTrue(Utils.isEmpty(""));
        Assert.assertTrue(Utils.isEmpty(null));

        Assert.assertFalse(Utils.isEmpty("aa"));
        Assert.assertFalse(Utils.isEmpty("1123āšē"));
    }

    @Test
    public void isNotEmpty() {
        Assert.assertFalse(Utils.isNotEmpty(""));
        Assert.assertFalse(Utils.isNotEmpty(null));

        Assert.assertTrue(Utils.isNotEmpty("aa"));
        Assert.assertTrue(Utils.isNotEmpty("1123āšē"));
    }

    /**
     * A simple verification that 'join' is performing as expected
     */
    @Test
    public void join() {
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
    public void APITargeting() {
        //The version the SDK is targeting should be above these values
        Assert.assertTrue(Utils.API(28));
        Assert.assertTrue(Utils.API(27));
        Assert.assertTrue(Utils.API(15));

        //The version the SDK is targeting should be below this value
        Assert.assertFalse(Utils.API(34));
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

    /**
     * Make sure that nothing bad happens when providing null segmentation
     */
    @Test
    public void truncateSegmentationValues_null() {
        Utils.truncateSegmentationValues(null, 10, "someTag", mock(ModuleLog.class));
        Assert.assertTrue(true);
    }

    /**
     * Make sure that nothing bad happens when providing empty segmentation
     */
    @Test
    public void truncateSegmentationValues_empty() {
        Map<String, Object> values = new HashMap<>();
        Utils.truncateSegmentationValues(values, 10, "someTag", mock(ModuleLog.class));
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
        Utils.truncateSegmentationValues(values, 6, "someTag", mock(ModuleLog.class));

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
        Utils.truncateSegmentationValues(values, 2, "someTag", mock(ModuleLog.class));

        Assert.assertEquals(2, values.size());
        //after inspecting what is returned in the debugger, it should have the values of "a2" and "a4"
        //Assert.assertEquals("2", values.get("a2"));
        //Assert.assertEquals("4", values.get("a4"));
    }

    @Test
    public void fillJSONIfValuesNotEmpty_noValues() {
        final JSONObject mockJSON = mock(JSONObject.class);
        Utils.fillJSONIfValuesNotEmpty(mockJSON);
        verifyZeroInteractions(mockJSON);
    }

    @Test
    public void fillJSONIfValuesNotEmpty_oddNumberOfValues() {
        final JSONObject mockJSON = mock(JSONObject.class);
        Utils.fillJSONIfValuesNotEmpty(mockJSON, "key1", "value1", "key2");
        verifyZeroInteractions(mockJSON);
    }

    @Test
    public void fillJSONIfValuesNotEmpty() throws JSONException {
        final JSONObject json = new JSONObject();
        Utils.fillJSONIfValuesNotEmpty(json, "key1", "value1", "key2", null, "key3", "value3", "key4", "", "key5", "value5");
        Assert.assertEquals("value1", json.get("key1"));
        assertFalse(json.has("key2"));
        Assert.assertEquals("value3", json.get("key3"));
        assertFalse(json.has("key4"));
        Assert.assertEquals("value5", json.get("key5"));
    }

    @Test
    public void removeReservedKeysFromSegmentation() {
        Map<String, Object> values = new HashMap<>();

        Utils.removeReservedKeysFromSegmentation(values, new String[] {}, "", mock(ModuleLog.class));
        Assert.assertEquals(0, values.size());

        Utils.removeReservedKeysFromSegmentation(values, new String[] { "a", "", null }, "", mock(ModuleLog.class));
        Assert.assertEquals(0, values.size());

        values.put("b", 1);
        Assert.assertEquals(1, values.size());
        Utils.removeReservedKeysFromSegmentation(values, new String[] { "a", "a1", "", null }, "", mock(ModuleLog.class));
        Assert.assertEquals(1, values.size());
        Assert.assertTrue(values.containsKey("b"));

        values.put("a", 2);
        Assert.assertEquals(2, values.size());
        Utils.removeReservedKeysFromSegmentation(values, new String[] { "a", "a1", "", null }, "", mock(ModuleLog.class));
        Assert.assertEquals(1, values.size());
        Assert.assertTrue(values.containsKey("b"));

        values.put("a", 2);
        values.put("c", 3);
        Assert.assertEquals(3, values.size());
        Utils.removeReservedKeysFromSegmentation(values, new String[] { "a", "a1", "", null }, "", mock(ModuleLog.class));
        Assert.assertEquals(2, values.size());
        Assert.assertTrue(values.containsKey("b"));
        Assert.assertTrue(values.containsKey("c"));
    }

    /**
     * For verifying if formatTimeDifference returns correct time information from milliseconds
     */
    @Test
    public void timeFormatterTests() {
        Assert.assertEquals("-1 millisecond(s)", Utils.formatTimeDifference(-1));
        Assert.assertEquals("0 millisecond(s)", Utils.formatTimeDifference(0));
        Assert.assertEquals("5 millisecond(s)", Utils.formatTimeDifference(5));
        Assert.assertEquals("1 second(s)", Utils.formatTimeDifference(1000));
        Assert.assertEquals("2 second(s)", Utils.formatTimeDifference(2000));
        Assert.assertEquals("1 minute(s)", Utils.formatTimeDifference(60_000));
        Assert.assertEquals("20 minute(s)", Utils.formatTimeDifference(1_200_000));
        Assert.assertEquals("1 hour(s)", Utils.formatTimeDifference(3_600_000));
        Assert.assertEquals("2 hour(s)", Utils.formatTimeDifference(9_600_000)); // instead of ~2.5
        Assert.assertEquals("1 day(s) and 0 hour(s)", Utils.formatTimeDifference(86_400_000));
        Assert.assertEquals("9 day(s) and 7 hour(s)", Utils.formatTimeDifference(804_000_000));
        Assert.assertEquals("1 month(s) and 0 day(s)", Utils.formatTimeDifference(2_592_000_000L));
        Assert.assertEquals("2 month(s) and 27 day(s)", Utils.formatTimeDifference(7_522_090_000L));
    }

    /**
     * Verifying if the isRequestTooOld works correctly if a request has a timestamp
     */
    @Test
    public void isRequestTooOld_validRequest() {
        String request = "request&timestamp=1692963331000";
        boolean result = Utils.isRequestTooOld(request, 1, "Test", mock(ModuleLog.class));
        assertTrue(result);
    }

    /**
     * Verifying if the isRequestTooOld works correctly if a request does not have a timestamp
     */
    @Test
    public void isRequestTooOld_noTimestampInRequest() {
        String request = "request";
        boolean result = Utils.isRequestTooOld(request, 1, "Test", mock(ModuleLog.class));
        assertFalse(result);
    }

    /**
     * Verifying if the isRequestTooOld works correctly if a negative dropAge provided
     */
    @Test
    public void isRequestTooOld_negativeZeroDropAge() {
        String request = "request&timestamp=1692963331000";
        boolean result = Utils.isRequestTooOld(request, -1, "Test", mock(ModuleLog.class));
        assertFalse(result);

        result = Utils.isRequestTooOld(request, 0, "Test", mock(ModuleLog.class));
        assertFalse(result);
    }

    /**
     * Verifying if the isRequestTooOld works correctly if the request has an invalid timestamp
     */
    @Test
    public void isRequestTooOld_invalidTimestamp() {
        String request = "request&timestamp=invalid_timestamp";
        boolean result = Utils.isRequestTooOld(request, 1, "Test", mock(ModuleLog.class));
        assertFalse(result);
    }

    /**
     * Verify if the extractValueFromString works correctly with different requests
     */
    @Test
    public void extractValuesFromString() {
        // extraction part is at the center
        String[] extractResult = Utils.extractValueFromString("sth&new_end_point=o/sdk&sthelse", "&new_end_point=", "&");
        Assert.assertEquals("sth&sthelse", extractResult[0]);
        Assert.assertEquals("o/sdk", extractResult[1]);

        // extraction part is at the center with double ending string
        extractResult = Utils.extractValueFromString("sth&new_end_point=o/sdk&sthelse&", "&new_end_point=", "&");
        Assert.assertEquals("sth&sthelse&", extractResult[0]);
        Assert.assertEquals("o/sdk", extractResult[1]);

        // extraction part does not exist
        extractResult = Utils.extractValueFromString("sth&", "&new_end_point=", "&");
        Assert.assertEquals("sth&", extractResult[0]);
        Assert.assertNull(extractResult[1]);

        // only starting string
        extractResult = Utils.extractValueFromString("&new_end_point=", "&new_end_point=", "&");
        Assert.assertEquals("", extractResult[0]);
        Assert.assertEquals("", extractResult[1]);
    }
}
