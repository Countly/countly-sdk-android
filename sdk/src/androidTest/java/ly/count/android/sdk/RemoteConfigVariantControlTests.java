package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.mockito.Mockito.mock;

@RunWith(AndroidJUnit4.class)
public class RemoteConfigVariantControlTests {
    CountlyStore countlyStore;

    @Before
    public void setUp() {
        Countly.sharedInstance().setLoggingEnabled(true);
        countlyStore = new CountlyStore(getContext(), mock(ModuleLog.class));
        countlyStore.clear();
    }

    @Test
    public void testConvertVariantsJsonToMap_ValidInput_Multi() throws JSONException {
        // Create a sample JSON object with variants
        JSONObject variantsObj = new JSONObject();
        JSONArray variantArray1 = new JSONArray();
        variantArray1.put(new JSONObject().put(ModuleRemoteConfig.variantObjectNameKey, "Variant 1"));
        variantArray1.put(new JSONObject().put(ModuleRemoteConfig.variantObjectNameKey, "Variant 2"));
        JSONArray variantArray2 = new JSONArray();
        variantArray2.put(new JSONObject().put(ModuleRemoteConfig.variantObjectNameKey, "Variant 3"));
        variantArray2.put(new JSONObject().put(ModuleRemoteConfig.variantObjectNameKey, "Variant 4"));
        variantsObj.put("key1", variantArray1);
        variantsObj.put("key2", variantArray2);

        // Call the function to convert variants JSON to a map
        Map<String, String[]> resultMap = ModuleRemoteConfig.convertVariantsJsonToMap(variantsObj);

        // Assert the expected map values
        Assert.assertEquals(2, resultMap.size());

        // Assert the values for key1
        String[] key1Variants = resultMap.get("key1");
        Assert.assertEquals(2, key1Variants.length);
        Assert.assertEquals("Variant 1", key1Variants[0]);
        Assert.assertEquals("Variant 2", key1Variants[1]);

        // Assert the values for key2
        String[] key2Variants = resultMap.get("key2");
        Assert.assertEquals(2, key2Variants.length);
        Assert.assertEquals("Variant 3", key2Variants[0]);
        Assert.assertEquals("Variant 4", key2Variants[1]);
    }

    @Test
    public void testConvertVariantsJsonToMap_ValidInput_Single() throws JSONException {
        // Create a sample JSON object with valid variants
        JSONObject variantsObj = new JSONObject();
        variantsObj.put("key1", new JSONArray().put(new JSONObject().put(ModuleRemoteConfig.variantObjectNameKey, "Variant 1")));
        variantsObj.put("key2", new JSONArray().put(new JSONObject().put(ModuleRemoteConfig.variantObjectNameKey, "Variant 2")));

        // Call the function to convert variants JSON to a map
        Map<String, String[]> resultMap = ModuleRemoteConfig.convertVariantsJsonToMap(variantsObj);

        // Assert the expected map values
        Assert.assertEquals(2, resultMap.size());

        // Assert the values for key1
        String[] key1Variants = resultMap.get("key1");
        Assert.assertEquals(1, key1Variants.length);
        Assert.assertEquals("Variant 1", key1Variants[0]);

        // Assert the values for key2
        String[] key2Variants = resultMap.get("key2");
        Assert.assertEquals(1, key2Variants.length);
        Assert.assertEquals("Variant 2", key2Variants[0]);
    }

    @Test
    public void testConvertVariantsJsonToMap_InvalidInput() throws JSONException {
        // Create a sample JSON object with invalid variants (missing "name" field)
        JSONObject variantsObj = new JSONObject();
        variantsObj.put("key1", new JSONArray().put(new JSONObject().put("invalid_key", "Invalid Value")));

        // Call the function to convert variants JSON to a map
        Map<String, String[]> resultMap = ModuleRemoteConfig.convertVariantsJsonToMap(variantsObj);
        Assert.assertEquals(1, resultMap.size());

        // Assert the values for key1
        String[] key1Variants = resultMap.get("key1");
        Assert.assertEquals(0, key1Variants.length);
    }

    @Test
    public void testConvertVariantsJsonToMap_InvalidJson() throws JSONException {
        // Test with invalid JSON object
        JSONObject variantsObj = new JSONObject();
        variantsObj.put("key1", "Invalid JSON");

        // Call the function to convert variants JSON to a map (expecting JSONException)
        ModuleRemoteConfig.convertVariantsJsonToMap(variantsObj);

        // Call the function to convert variants JSON to a map
        Map<String, String[]> resultMap = ModuleRemoteConfig.convertVariantsJsonToMap(variantsObj);
        Assert.assertEquals(0, resultMap.size());
    }

    /**
     * Empty JSON should produce an empty map
     */
    @Test
    public void testConvertVariantsJsonToMap_NoValues() {
        // Create an empty JSON object
        JSONObject variantsObj = new JSONObject();

        // Call the function to convert variants JSON to a map
        Map<String, String[]> resultMap = ModuleRemoteConfig.convertVariantsJsonToMap(variantsObj);

        // Assert that the map is empty
        Assert.assertTrue(resultMap.isEmpty());
    }

    @Test
    public void testConvertVariantsJsonToMap_DifferentStructures() throws JSONException {
        // Test with JSON object having different structures
        JSONObject variantsObj = new JSONObject();

        // Structure 1: Empty JSON array
        variantsObj.put("key1", new JSONArray());

        // Structure 2: Single variant as JSON object
        variantsObj.put("key2", new JSONArray().put(new JSONObject().put(ModuleRemoteConfig.variantObjectNameKey, "Variant 1")));

        // Structure 3: Multiple variants as JSON objects
        variantsObj.put("key3", new JSONArray().put(new JSONObject().put(ModuleRemoteConfig.variantObjectNameKey, "Variant 2")).put(new JSONObject().put(ModuleRemoteConfig.variantObjectNameKey, "Variant 3")));

        // Call the function to convert variants JSON to a map
        Map<String, String[]> resultMap = ModuleRemoteConfig.convertVariantsJsonToMap(variantsObj);

        // Assert the expected map values
        Assert.assertEquals(3, resultMap.size());

        // Assert the values for key1 (empty array)
        String[] key1Variants = resultMap.get("key1");
        Assert.assertEquals(0, key1Variants.length);

        // Assert the values for key2 (single variant)
        String[] key2Variants = resultMap.get("key2");
        Assert.assertEquals(1, key2Variants.length);
        Assert.assertEquals("Variant 1", key2Variants[0]);

        // Assert the values for key3 (multiple variants)
        String[] key3Variants = resultMap.get("key3");
        Assert.assertEquals(2, key3Variants.length);
        Assert.assertEquals("Variant 2", key3Variants[0]);
        Assert.assertEquals("Variant 3", key3Variants[1]);
    }

    /**
     * variant with a string test name "null" and a string variant name "null" should be let through
     *
     * @throws JSONException
     */
    @Test
    public void testConvertVariantsJsonToMap_NullJsonKey() throws JSONException {
        // Test with a null JSON key string
        String variantsObj = "{\"null\":[{\"name\":\"null\"}]}";

        // Call the function to convert variants JSON to a map (expecting JSONException)
        Map<String, String[]> resultMap = ModuleRemoteConfig.convertVariantsJsonToMap(new JSONObject(variantsObj));

        // Assert the values for key1
        String[] key1Variants = resultMap.get("null");
        Assert.assertEquals(1, key1Variants.length);
        Assert.assertEquals("null", key1Variants[0]);
    }

    @Test
    public void testNormalFlow() {
        CountlyConfig config = TestUtils.createVariantConfig(createIRGForSpecificResponse("{\"key\":[{\"name\":\"variant\"}]}"));
        Countly countly = (new Countly()).init(config);

        // Developer did not provide a callback
        countly.moduleRemoteConfig.remoteConfigInterface.TestingDownloadVariantInformation(null);
        Map<String, String[]> values = countly.moduleRemoteConfig.remoteConfigInterface.testingGetAllVariants();
        String[] variantArray = countly.moduleRemoteConfig.remoteConfigInterface.testingGetVariantsForKey("key");
        String[] variantArrayFalse = countly.moduleRemoteConfig.remoteConfigInterface.testingGetVariantsForKey("key2");

        //Assert the values
        String[] key1Variants = values.get("key");
        Assert.assertEquals(1, key1Variants.length);
        Assert.assertEquals("variant", key1Variants[0]);
        Assert.assertEquals(1, variantArray.length);
        Assert.assertEquals("variant", variantArray[0]);
        Assert.assertEquals(0, variantArrayFalse.length);
    }

    /**
     * Reject a variant if it's name is a null json value
     */
    @Test
    public void testNullVariant() {
        CountlyConfig config = TestUtils.createVariantConfig(createIRGForSpecificResponse("{\"key\":[{\"name\":null}]}"));
        Countly countly = (new Countly()).init(config);

        // Developer did not provide a callback
        countly.moduleRemoteConfig.remoteConfigInterface.TestingDownloadVariantInformation(null);
        Map<String, String[]> values = countly.moduleRemoteConfig.remoteConfigInterface.testingGetAllVariants();

        // Assert the values
        String[] key1Variants = values.get("key");
        Assert.assertEquals(0, key1Variants.length);
    }

    /**
     * Reject variant entries where the object has no entry with the "name" key
     */
    @Test
    public void testFilteringWrongKeys() {
        CountlyConfig config = TestUtils.createVariantConfig(createIRGForSpecificResponse("{\"key\":[{\"noname\":\"variant1\"},{\"name\":\"variant2\"}]}"));
        Countly countly = (new Countly()).init(config);

        // Developer did not provide a callback
        countly.moduleRemoteConfig.remoteConfigInterface.TestingDownloadVariantInformation(null);
        Map<String, String[]> values = countly.moduleRemoteConfig.remoteConfigInterface.testingGetAllVariants();

        //Assert the values
        String[] key1Variants = values.get("key");
        Assert.assertEquals(1, key1Variants.length);
        Assert.assertEquals("variant2", key1Variants[0]);
    }

    ImmediateRequestGenerator createIRGForSpecificResponse(final String targetResponse) {
        return () -> (requestData, customEndpoint, cp, requestShouldBeDelayed, networkingIsEnabled, callback, log) -> {
            if (targetResponse == null) {
                callback.callback(null);
                return;
            }

            JSONObject jobj = null;

            try {
                jobj = new JSONObject(targetResponse);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            callback.callback(jobj);
        };
    }
}
