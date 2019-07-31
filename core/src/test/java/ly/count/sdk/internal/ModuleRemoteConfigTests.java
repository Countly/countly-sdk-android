package ly.count.sdk.internal;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Map;

@RunWith(JUnit4.class)
public class ModuleRemoteConfigTests extends BaseTestsCore {
    String [] keys = new String[]{"qwe", "123", "asd", "zxc", "wer", "sdf", "xcv"};
    JSONArray jsonArray = new JSONArray();

    @Before
    public void setupEveryTest(){
        jsonArray.put(12);
        jsonArray.put(23.4);
        jsonArray.put("asd");
        jsonArray.put("765");
    }

    @After
    public void cleanupEveryTests(){
    }

    @Test
    public void remoteConfigValueStoreAddRemoveValues(){
        ModuleRemoteConfig.RemoteConfigValueStore rcvs = createValueStore();

        Assert.assertEquals(123345567789645L, rcvs.getValue(keys[0]));
        Assert.assertEquals(23, rcvs.getValue(keys[1]));
        Assert.assertEquals("asas", rcvs.getValue(keys[2]));
        Assert.assertEquals(123.3, rcvs.getValue(keys[3]));
        Assert.assertEquals(true, rcvs.getValue(keys[4]));
        Assert.assertEquals(jsonArray.toString(), rcvs.getValue(keys[5]).toString());
    }

    @Test
    public void remoteConfigValueStoreStoreRestore(){
        ModuleRemoteConfig.RemoteConfigValueStore rcvs = createValueStore();
        ModuleRemoteConfig.RemoteConfigValueStore rcvs2 = new ModuleRemoteConfig.RemoteConfigValueStore();

        byte[] byteVal = rcvs.store();
        rcvs2.restore(byteVal);

        Assert.assertTrue(compareValueStores(rcvs, rcvs2));
    }

    @Test
    public void remoteConfigValueStoreMergeValues(){
        String[] keys = new String[]{"dsd", "123", "xcv", "ty", "aa", "nn", "zx", "io"};
        int[] valsI = new int[]{2,3,56,8,7,345,76,98};
        ModuleRemoteConfig.RemoteConfigValueStore rcvs = new ModuleRemoteConfig.RemoteConfigValueStore();

        //add values #1
        JSONObject jobj = new JSONObject();
        jobj.put(keys[0], valsI[0]);
        jobj.put(keys[1], valsI[1]);
        jobj.put(keys[2], keys[0]);
        rcvs.mergeValues(jobj);

        Assert.assertEquals(rcvs.getValue(keys[0]), valsI[0]);
        Assert.assertEquals(rcvs.getValue(keys[1]), valsI[1]);
        Assert.assertEquals(rcvs.getValue(keys[2]), keys[0]);

        //add values #2
        jobj = new JSONObject();
        JSONArray jarr = new JSONArray();
        jarr.put(valsI[0]);
        jarr.put(valsI[1]);
        jarr.put(valsI[2]);

        jobj.put(keys[3], jarr);
        jobj.put(keys[1], valsI[3]);
        jobj.put(keys[2], keys[5]);
        rcvs.mergeValues(jobj);
        jarr = new JSONArray();
        jarr.put(valsI[0]);
        jarr.put(valsI[1]);
        jarr.put(valsI[2]);

        Assert.assertEquals(rcvs.getValue(keys[0]), valsI[0]);
        Assert.assertEquals(rcvs.getValue(keys[1]), valsI[3]);
        Assert.assertEquals(rcvs.getValue(keys[2]), keys[5]);
        JSONArray jarr2 = (JSONArray)rcvs.getValue(keys[3]);

        for(int a = 0 ; a < jarr.length();a++){
            Assert.assertEquals(jarr.get(a), jarr2.get(a));
        }
    }


    boolean compareValueStores(ModuleRemoteConfig.RemoteConfigValueStore v1, ModuleRemoteConfig.RemoteConfigValueStore v2) {
        Map<String, Object> map1 = v1.values.toMap();
        Map<String, Object> map2 = v2.values.toMap();

        if (map1.size() != map2.size()) {
            return false;
        }

        for (Map.Entry<String, Object> entry : map1.entrySet())
        {
            for (Map.Entry<String, Object> entry2 : map2.entrySet())
            {
                if(entry.getKey().equals(entry2.getKey())){
                    Object vv1 = entry.getValue();
                    Object vv2 = entry2.getValue();
                    boolean res = vv1.equals(vv2);
                    if(!res){return false;}
                }
            }
        }

        return true;
    }

    ModuleRemoteConfig.RemoteConfigValueStore createValueStore(){
        ModuleRemoteConfig.RemoteConfigValueStore rcvs = new ModuleRemoteConfig.RemoteConfigValueStore();
        rcvs.values.put(keys[0], 123345567789645L);
        rcvs.values.put(keys[1], 23);
        rcvs.values.put(keys[2], "asas");
        rcvs.values.put(keys[3], 123.3);
        rcvs.values.put(keys[4], true);
        rcvs.values.put(keys[5], jsonArray);

        return rcvs;
    }

}
