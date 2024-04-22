package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.List;
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
import static org.mockito.Mockito.verifyNoInteractions;

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
        Assert.assertTrue(Utils.isNullOrEmpty(""));
        Assert.assertTrue(Utils.isNullOrEmpty(null));

        Assert.assertFalse(Utils.isNullOrEmpty("aa"));
        Assert.assertFalse(Utils.isNullOrEmpty("1123āšē"));
    }

    @Test
    public void isNotEmpty() {
        Assert.assertFalse(Utils.isNotNullOrEmpty(""));
        Assert.assertFalse(Utils.isNotNullOrEmpty(null));

        Assert.assertTrue(Utils.isNotNullOrEmpty("aa"));
        Assert.assertTrue(Utils.isNotNullOrEmpty("1123āšē"));
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
        Assert.assertTrue(Utils.API(34));
        Assert.assertTrue(Utils.API(28));
        Assert.assertTrue(Utils.API(27));
        Assert.assertTrue(Utils.API(15));

        //The version the SDK is targeting should be below this value
        Assert.assertFalse(Utils.API(35));
    }

    @Test
    public void fillJSONIfValuesNotEmpty_noValues() {
        final JSONObject mockJSON = mock(JSONObject.class);
        Utils.fillJSONIfValuesNotEmpty(mockJSON);
        verifyNoInteractions(mockJSON);
    }

    @Test
    public void fillJSONIfValuesNotEmpty_oddNumberOfValues() {
        final JSONObject mockJSON = mock(JSONObject.class);
        Utils.fillJSONIfValuesNotEmpty(mockJSON, "key1", "value1", "key2");
        verifyNoInteractions(mockJSON);
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
