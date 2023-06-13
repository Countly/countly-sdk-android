package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Map;
import ly.count.android.sdk.internal.RemoteConfigHelper;
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
        RemoteConfigValueStore remoteConfigValueStore = RemoteConfigValueStore.dataFromString(null, false);

        remoteConfigValueStore.values.put("fd", 12);
        remoteConfigValueStore.values.put("2fd", 142);
        remoteConfigValueStore.values.put("f3d", 123);

        RemoteConfigValueStore.dataFromString(remoteConfigValueStore.dataToString(), false);
    }

    /**
     * Validating regressive cases for "dataFromString"
     */
    @Test
    public void rcvsDataFromStringNullEmpty() {
        RemoteConfigValueStore rcvs1 = RemoteConfigValueStore.dataFromString(null, false);
        Assert.assertNotNull(rcvs1);
        Assert.assertNotNull(rcvs1.values);
        Assert.assertEquals(0, rcvs1.values.length());

        RemoteConfigValueStore rcvs2 = RemoteConfigValueStore.dataFromString("", false);
        Assert.assertNotNull(rcvs2);
        Assert.assertNotNull(rcvs2.values);
        Assert.assertEquals(0, rcvs2.values.length());
    }

    /**
     * A simple "dataFromString" test case
     */
    @Test
    public void rcvsDataFromStringSamples_1() {
        String[] rcArr = new String[] { rcEStr("a", 123, false), rcEStr("b", "fg", false) };
        RemoteConfigValueStore rcvs = RemoteConfigValueStore.dataFromString(rcArrIntoJSON(rcArr), true);
        Assert.assertNotNull(rcvs);
        Assert.assertNotNull(rcvs.values);
        Assert.assertEquals(2, rcvs.values.length());

        Assert.assertEquals(123, rcvs.getValueLegacy("a"));
        Assert.assertEquals("fg", rcvs.getValueLegacy("b"));
        Assert.assertEquals(123, rcvs.getValue("a").value);
        Assert.assertEquals("fg", rcvs.getValue("b").value);
        Assert.assertFalse(rcvs.getValue("a").isCurrentUsersData);
        Assert.assertFalse(rcvs.getValue("b").isCurrentUsersData);
    }

    /**
     * A more complicated "dataFromString" test case
     * It also validates serialization and getAllValues call
     *
     * @throws JSONException
     */
    @Test
    public void rcvsDataFromStringSamples_2() throws JSONException {
        JSONArray jArrI = new JSONArray("[3,\"44\",5.1,7.7]");
        JSONObject jObjI = new JSONObject("{\"q\":6,\"w\":\"op\"}");

        String[] rcArr = new String[] { rcEStr("321", 123, false), rcEStr("😀", "😁"), rcEStr("c", jArrI), rcEStr("d", 6.5), rcEStr("e", jObjI) };
        RemoteConfigValueStore rcvs = RemoteConfigValueStore.dataFromString(rcArrIntoJSON(rcArr), true);
        Assert.assertNotNull(rcvs);
        Assert.assertNotNull(rcvs.values);

        //validate values while using "get"
        Assert.assertEquals(5, rcvs.values.length());

        Assert.assertEquals(123, rcvs.getValueLegacy("321"));
        Assert.assertEquals("\uD83D\uDE01", rcvs.getValueLegacy("\uD83D\uDE00"));
        Assert.assertEquals(6.5, rcvs.getValueLegacy("d"));

        Object v1 = rcvs.getValueLegacy("c");
        Object v2 = rcvs.getValueLegacy("e");

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
        Map<String, Object> allVals = rcvs.getAllValuesLegacy();

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

    @Test
    public void dataFromString_CurrentStructure() {
        String[] rcArr = new String[] { rcEStr("a", 123), rcEStr("b", "ccx", false) };
        RemoteConfigValueStore rcvs = RemoteConfigValueStore.dataFromString(rcArrIntoJSON(rcArr), false);

        Assert.assertEquals(123, rcvs.getValue("a").value);
        Assert.assertTrue(rcvs.getValue("a").isCurrentUsersData);

        Assert.assertEquals("ccx", rcvs.getValue("b").value);
        Assert.assertFalse(rcvs.getValue("b").isCurrentUsersData);
    }

    /**
     * Simple test for value merging
     */
    @Test
    public void rcvsMergeValues_1() throws JSONException {
        String[] rcArr = new String[] { rcEStr("a", 123), rcEStr("b", "fg") };
        RemoteConfigValueStore rcvs = RemoteConfigValueStore.dataFromString(rcArrIntoJSON(rcArr), false);
        JSONObject obj = new JSONObject("{\"b\": 123.3,\"c\": \"uio\"}");

        Map<String, RCData> newRC = RemoteConfigHelper.DownloadedValuesIntoMap(obj);
        rcvs.mergeValues(newRC, false);

        Assert.assertEquals(3, rcvs.values.length());

        Assert.assertEquals(123, rcvs.getValue("a").value);
        Assert.assertEquals(123.3, rcvs.getValue("b").value);
        Assert.assertEquals("uio", rcvs.getValue("c").value);
    }

    /**
     * Create a remote config entry string
     *
     * @param key
     * @param value
     * @return
     */
    public static String rcEStr(String key, Object value, boolean isCurrentUser) {
        StringBuilder ret = new StringBuilder();
        ret.append("\"").append(key).append("\":{\"");
        ret.append(RemoteConfigValueStore.keyValue);
        ret.append("\":");

        if (value instanceof String) {
            ret.append("\"");
            ret.append(value);
            ret.append("\"");
        } else if (value instanceof JSONArray) {
            ret.append(value);
        } else if (value instanceof JSONObject) {
            ret.append(value);
        } else {
            ret.append(value);
        }

        ret.append(",\"");
        ret.append(RemoteConfigValueStore.keyCacheFlag);
        ret.append("\":");

        ret.append(isCurrentUser ? RemoteConfigValueStore.cacheValFresh : RemoteConfigValueStore.cacheValCached);

        ret.append("}");

        return ret.toString();
    }

    public static String rcEStr(String key, Object value) {
        return rcEStr(key, value, true);
    }

    String rcArrIntoJSON(String[] arr) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        for (int a = 0; a < arr.length; a++) {
            if (a != 0) {
                sb.append(",");
            }

            sb.append(arr[a]);
        }
        String input = "{" + rcEStr("a", 123, true) + "," + rcEStr("b", "ccx", false) + "}";

        sb.append("}");
        return sb.toString();
    }
}
