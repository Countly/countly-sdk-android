package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RemoteConfigVariantControlTests {

    @Test
    public void testConvertVariantsJsonToMap_ValidInput_Multi() throws JSONException {
        // Create a sample JSON object with variants
        JSONObject variantsObj = new JSONObject();
        JSONArray variantArray1 = new JSONArray();
        variantArray1.put(new JSONObject().put("name", "Variant 1"));
        variantArray1.put(new JSONObject().put("name", "Variant 2"));
        JSONArray variantArray2 = new JSONArray();
        variantArray2.put(new JSONObject().put("name", "Variant 3"));
        variantArray2.put(new JSONObject().put("name", "Variant 4"));
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
        variantsObj.put("key1", new JSONArray().put(new JSONObject().put("name", "Variant 1")));
        variantsObj.put("key2", new JSONArray().put(new JSONObject().put("name", "Variant 2")));

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

    @Test(expected = JSONException.class)
    public void testConvertVariantsJsonToMap_InvalidInput() throws JSONException {
        // Create a sample JSON object with invalid variants (missing "name" field)
        JSONObject variantsObj = new JSONObject();
        variantsObj.put("key1", new JSONArray().put(new JSONObject().put("invalid_key", "Invalid Value")));

        // Call the function to convert variants JSON to a map (expecting JSONException)
        ModuleRemoteConfig.convertVariantsJsonToMap(variantsObj);
    }


    @Test(expected = JSONException.class)
    public void testConvertVariantsJsonToMap_InvalidJson() throws JSONException {
        // Test with invalid JSON object
        JSONObject variantsObj = new JSONObject();
        variantsObj.put("key1", "Invalid JSON");

        // Call the function to convert variants JSON to a map (expecting JSONException)
        ModuleRemoteConfig.convertVariantsJsonToMap(variantsObj);
    }

    @Test
    public void testConvertVariantsJsonToMap_NoValues() throws JSONException {
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
        variantsObj.put("key2", new JSONArray().put(new JSONObject().put("name", "Variant 1")));

        // Structure 3: Multiple variants as JSON objects
        variantsObj.put("key3", new JSONArray().put(new JSONObject().put("name", "Variant 2")).put(new JSONObject().put("name", "Variant 3")));

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

    // TODO: is this okay?
    @Test
    public void testConvertVariantsJsonToMap_NullJsonKey() throws JSONException {
        // Test with a null JSON key
        String variantsObj = "{\"null\":[{\"name\":\"null\"}]}";

        // Call the function to convert variants JSON to a map (expecting JSONException)
        Map<String, String[]> resultMap = ModuleRemoteConfig.convertVariantsJsonToMap(new JSONObject(variantsObj));

        // Assert the values for key1
        String[] key1Variants = resultMap.get("null");
        Assert.assertEquals(1, key1Variants.length);
        Assert.assertEquals("null", key1Variants[0]);
    }
}
