package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Map;
import ly.count.android.sdk.internal.RemoteConfigValueStore;
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
public class RemoteConfigValueStoreTests {
    CountlyStore countlyStore;

    @Before
    public void setUp() {
        Countly.sharedInstance().setLoggingEnabled(true);
        countlyStore = new CountlyStore(getContext(), mock(ModuleLog.class));
    }

    /**
     * Basic serialization / deserialization test
     *
     * @throws JSONException
     */
    @Test
    public void rcvsSerializeDeserialize() throws JSONException {
        RemoteConfigValueStore remoteConfigValueStore = RemoteConfigValueStore.dataFromString(null);

        remoteConfigValueStore.values.put("fd", 12);
        remoteConfigValueStore.values.put("2fd", 142);
        remoteConfigValueStore.values.put("f3d", 123);

        RemoteConfigValueStore.dataFromString(remoteConfigValueStore.dataToString());
    }

    /**
     * Validating regressive cases for "dataFromString"
     */
    @Test
    public void rcvsDataFromStringNullEmpty() {
        RemoteConfigValueStore rcvs1 = RemoteConfigValueStore.dataFromString(null);
        Assert.assertNotNull(rcvs1);
        Assert.assertNotNull(rcvs1.values);
        Assert.assertEquals(0, rcvs1.values.length());

        RemoteConfigValueStore rcvs2 = RemoteConfigValueStore.dataFromString("");
        Assert.assertNotNull(rcvs2);
        Assert.assertNotNull(rcvs2.values);
        Assert.assertEquals(0, rcvs2.values.length());
    }

    /**
     * A simple "dataFromString" test case
     */
    @Test
    public void rcvsDataFromStringSamples_1() {
        RemoteConfigValueStore rcvs = RemoteConfigValueStore.dataFromString("{\"a\": 123,\"b\": \"fg\"}");
        Assert.assertNotNull(rcvs);
        Assert.assertNotNull(rcvs.values);
        Assert.assertEquals(2, rcvs.values.length());

        Assert.assertEquals(123, rcvs.getValue("a"));
        Assert.assertEquals("fg", rcvs.getValue("b"));
    }

    /**
     * A more complicated "dataFromString" test case
     * It also validates serialization and getAllValues call
     *
     * @throws JSONException
     */
    @Test
    public void rcvsDataFromStringSamples_2() throws JSONException {
        String initialString = "{\"321\":123,\"\uD83D\uDE00\":\"\uD83D\uDE01\",\"c\":[3,\"44\",5.1,7.7],\"d\":6.5,\"e\":{\"q\":6,\"w\":\"op\"}}";
        RemoteConfigValueStore rcvs = RemoteConfigValueStore.dataFromString(initialString);
        Assert.assertNotNull(rcvs);
        Assert.assertNotNull(rcvs.values);

        Assert.assertEquals(initialString, rcvs.dataToString());//quickly validate deserialization

        //validate values while using "get"
        Assert.assertEquals(5, rcvs.values.length());

        Assert.assertEquals(123, rcvs.getValue("321"));
        Assert.assertEquals("\uD83D\uDE01", rcvs.getValue("\uD83D\uDE00"));
        Assert.assertEquals(6.5, rcvs.getValue("d"));

        Object v1 = rcvs.getValue("c");
        Object v2 = rcvs.getValue("e");

        JSONArray jArr = (JSONArray) v1;
        Assert.assertEquals(4, jArr.length());
        Assert.assertEquals(3, jArr.getInt(0));
        Assert.assertEquals("44", jArr.getString(1));
        Assert.assertEquals(5.1, jArr.getDouble(2), 0.000001);
        Assert.assertEquals(7.7, jArr.get(3));

        JSONObject jObj = (JSONObject) v2;
        Assert.assertEquals(2, jObj.length());
        Assert.assertEquals(6, jObj.get("q"));
        Assert.assertEquals("op", jObj.get("w"));

        //validate that all of the values are the same when returned with getAllValues
        Map<String, Object> allVals = rcvs.getAllValues();

        Assert.assertEquals(5, allVals.size());

        Assert.assertEquals(123, allVals.get("321"));
        Assert.assertEquals("\uD83D\uDE01", allVals.get("\uD83D\uDE00"));
        Assert.assertEquals(6.5, allVals.get("d"));

        Object v3 = allVals.get("c");
        Object v4 = allVals.get("e");

        JSONArray jArr2 = (JSONArray) v3;
        Assert.assertEquals(4, jArr2.length());
        Assert.assertEquals(3, jArr2.getInt(0));
        Assert.assertEquals("44", jArr2.getString(1));
        Assert.assertEquals(5.1, jArr2.getDouble(2), 0.000001);
        Assert.assertEquals(7.7, jArr2.get(3));

        JSONObject jObj2 = (JSONObject) v4;
        Assert.assertEquals(2, jObj2.length());
        Assert.assertEquals(6, jObj2.get("q"));
        Assert.assertEquals("op", jObj2.get("w"));
    }

    /**
     * Simple test for value merging
     *
     * @throws JSONException
     */
    @Test
    public void rcvsMergeValues_1() throws JSONException {
        RemoteConfigValueStore rcvs1 = RemoteConfigValueStore.dataFromString("{\"a\": 123,\"b\": \"fg\"}");
        RemoteConfigValueStore rcvs2 = RemoteConfigValueStore.dataFromString("{\"b\": 123.3,\"c\": \"uio\"}");

        rcvs1.mergeValues(rcvs2.values);

        Assert.assertEquals(3, rcvs1.values.length());

        Assert.assertEquals(123, rcvs1.getValue("a"));
        Assert.assertEquals(123.3, rcvs1.getValue("b"));
        Assert.assertEquals("uio", rcvs1.getValue("c"));
    }
}
