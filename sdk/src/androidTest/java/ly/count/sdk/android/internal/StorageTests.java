package ly.count.sdk.android.internal;

import android.support.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.Future;

import ly.count.sdk.internal.ModuleRequests;
import ly.count.sdk.internal.Storable;
import ly.count.sdk.internal.Storage;

@RunWith(AndroidJUnit4.class)
public class StorageTests extends BaseTestsAndroid {

    @Override
    public void setUp() throws Exception {
        super.setUp();
        setUpApplication(defaultConfig());
    }

    @Test
    public void core_pushOne() {
        Storable storable = storable(), restored = storable();
        Utils.reflectiveSetField(restored, "id", storable.storageId());


        Assert.assertTrue(sdk.storableWrite(ctx, storable));

        List<Long> files = sdk.storableList(ctx, storable.storagePrefix(), 0);
        Assert.assertNotNull(files);
        Assert.assertEquals(1, files.size());

        byte[] data = sdk.storableReadBytes(ctx, storable.storagePrefix(), storable.storageId());
        Assert.assertNotNull(data);
        Assert.assertTrue(restored.restore(data));
        Assert.assertEquals(storable, restored);

        Assert.assertTrue(sdk.storableRemove(ctx, storable));

        files = sdk.storableList(ctx, storable.storagePrefix(), 0);
        Assert.assertNotNull(files);
        Assert.assertEquals(0, files.size());
    }

    @Test
    public void core_pushTwo() {
        Storable storable1 = storable(), storable2 = storable();

        List<Long> files = sdk.storableList(ctx, storable1.storagePrefix(), 0);
        Assert.assertNotNull(files);
        Assert.assertEquals(0, files.size());

        Assert.assertTrue(sdk.storableWrite(ctx, storable1));
        Assert.assertTrue(sdk.storableWrite(ctx, storable2));

        files = sdk.storableList(ctx, storable1.storagePrefix(), 0);
        Assert.assertNotNull(files);
        Assert.assertEquals(2, files.size());

        Storable storable1Pop = storable(), storable2Pop = storable();
        Utils.reflectiveSetField(storable1Pop, "id", storable1.storageId());
        Utils.reflectiveSetField(storable2Pop, "id", storable2.storageId());

        Assert.assertTrue(sdk.storablePop(ctx, storable1Pop));

        files = sdk.storableList(ctx, storable1.storagePrefix(), 0);
        Assert.assertNotNull(files);
        Assert.assertEquals(1, files.size());

        Assert.assertTrue(sdk.storablePop(ctx, storable2Pop));

        Assert.assertEquals(storable1, storable1Pop);
        Assert.assertEquals(storable2, storable2Pop);
        Assert.assertNotSame(storable1Pop, storable2Pop);

        files = sdk.storableList(ctx, storable1.storagePrefix(), 0);
        Assert.assertNotNull(files);
        Assert.assertEquals(0, files.size());
    }

    @Test
    public void core_slicing() {
        Storable storable1 = storable(), storable2 = storable();
        Storable storable3 = storable(), storable4 = storable();

        List<Long> files = sdk.storableList(ctx, storable1.storagePrefix(), 0);
        Assert.assertNotNull(files);
        Assert.assertEquals(0, files.size());

        Assert.assertTrue(Storage.push(ctx, storable1));
        Assert.assertTrue(Storage.push(ctx, storable2));
        Assert.assertTrue(Storage.push(ctx, storable3));
        Assert.assertTrue(Storage.push(ctx, storable4));

        List<Long> ids = Storage.list(ctx, storable1.storagePrefix());
        Assert.assertNotNull(ids);
        Assert.assertEquals(ids.size(), 4);
        Assert.assertEquals(ids.get(0), storable1.storageId());
        Assert.assertEquals(ids.get(1), storable2.storageId());
        Assert.assertEquals(ids.get(2), storable3.storageId());
        Assert.assertEquals(ids.get(3), storable4.storageId());

        ids = Storage.list(ctx, storable1.storagePrefix(), 2);
        Assert.assertNotNull(ids);
        Assert.assertEquals(ids.size(), 2);
        Assert.assertEquals(ids.get(0), storable1.storageId());
        Assert.assertEquals(ids.get(1), storable2.storageId());

        ids = Storage.list(ctx, storable1.storagePrefix(), -2);
        Assert.assertNotNull(ids);
        Assert.assertEquals(ids.size(), 2);
        Assert.assertEquals(ids.get(0), storable4.storageId());
        Assert.assertEquals(ids.get(1), storable3.storageId());
    }

    @Test
    public void storage_pushPopList() {
        Storable storable1 = storable(), storable2 = storable();

        List<Long> ids = Storage.list(ctx, storable1.storagePrefix());
        Assert.assertNotNull(ids);
        Assert.assertEquals(ids.size(), 0);

        Assert.assertTrue(Storage.push(ctx, storable1));

        ids = Storage.list(ctx, storable1.storagePrefix());
        Assert.assertNotNull(ids);
        Assert.assertEquals(ids.size(), 1);
        Assert.assertEquals(ids.get(0), storable1.storageId());

        Assert.assertTrue(Storage.push(ctx, storable2));

        ids = Storage.list(ctx, storable1.storagePrefix());
        Assert.assertNotNull(ids);
        Assert.assertEquals(ids.size(), 2);
        Assert.assertEquals(ids.get(0), storable1.storageId());
        Assert.assertEquals(ids.get(1), storable2.storageId());

        Storable restored1 = storable(), restored2 = storable();
        Utils.reflectiveSetField(restored1, "id", storable1.storageId());
        Utils.reflectiveSetField(restored2, "id", storable2.storageId());

        restored1 = Storage.pop(ctx, restored1);
        restored2 = Storage.pop(ctx, restored2);

        Assert.assertNotNull(restored1);
        Assert.assertNotNull(restored2);

        Assert.assertEquals(storable1, restored1);
        Assert.assertEquals(storable2, restored2);

        ids = Storage.list(ctx, storable1.storagePrefix());
        Assert.assertNotNull(ids);
        Assert.assertEquals(ids.size(), 0);
    }


    @Test
    public void storage_readReadOne() {
        Storable storable1 = storable(), storable2 = storable();

        List<Long> ids = Storage.list(ctx, storable1.storagePrefix());
        Assert.assertNotNull(ids);
        Assert.assertEquals(ids.size(), 0);

        Assert.assertTrue(Storage.push(ctx, storable1));

        ids = Storage.list(ctx, storable1.storagePrefix());
        Assert.assertNotNull(ids);
        Assert.assertEquals(ids.size(), 1);
        Assert.assertEquals(ids.get(0), storable1.storageId());

        Assert.assertTrue(Storage.push(ctx, storable2));

        ids = Storage.list(ctx, storable1.storagePrefix());
        Assert.assertNotNull(ids);
        Assert.assertEquals(2, ids.size());
        Assert.assertEquals(ids.get(0), storable1.storageId());
        Assert.assertEquals(ids.get(1), storable2.storageId());

        Storable s1 = storable(), s2 = storable();
        Utils.reflectiveSetField(s1, "id", storable1.storageId());
        Utils.reflectiveSetField(s2, "id", storable2.storageId());

        Future<Storable> restored2 = Storage.readAsync(ctx, s2),
                 restored1 = Storage.readAsync(ctx, s1),
                 restored1TasksCopy = Storage.readAsync(ctx, s1),
                 restored2Copy = Storage.readAsync(ctx, s2);

        Assert.assertNotNull(restored1);
        Assert.assertNotNull(restored1TasksCopy);
        Assert.assertNotNull(restored2);
        Assert.assertSame(restored1, restored1TasksCopy);
        Assert.assertNotSame(restored1, restored2);

        Storable s1c = storable();
        Utils.reflectiveSetField(s1c, "id", storable1.storageId());

        Storable req = Storage.read(ctx, s1c);
        Assert.assertNotNull(req);
        Assert.assertEquals(storable1, req);

        ids = Storage.list(ctx, storable1.storagePrefix());
        Assert.assertNotNull(ids);
        Assert.assertEquals(2, ids.size());
        Assert.assertEquals(ids.get(0), storable1.storageId());
        Assert.assertEquals(ids.get(1), storable2.storageId());

        Storable s0 = storable();
        Utils.reflectiveSetField(s0, "id", 0L);

        req = Storage.readOne(ctx, s0, true);
        ids = Storage.list(ctx, storable1.storagePrefix());

        Assert.assertNotNull(req);
        Assert.assertEquals(storable1, req);
        Assert.assertEquals(2, ids.size());
        Assert.assertEquals(ids.get(0), storable1.storageId());
        Assert.assertEquals(ids.get(1), storable2.storageId());

        Assert.assertEquals(Boolean.TRUE, Storage.remove(ctx, req));
        ids = Storage.list(ctx, storable1.storagePrefix());

        Assert.assertEquals(1, ids.size());
        Assert.assertEquals(ids.get(0), storable2.storageId());

    }

    /*
        Storage performance test that is used on demand seldom
    */
//    @Test
//    public void storage_performanceTest() {
//        long time = System.nanoTime(), diff;
//        int count = 10000;
//
//        for (int i = 0; i < count; i++) {
//            Storable storable = storable();
//            Storage.push(storable);
//        }
//
//        diff = Device.nsToMs(System.nanoTime() - time);
//        Log.i("Time to push " + diff + " / " + count + " = " + Math.round(diff/count));
//
//        List<Long> ids = null;
//        count = 100;
//        time = System.nanoTime();
//        for (int i = 0; i < count; i++) {
//            ids = Storage.list(storable().storagePrefix());
//        }
//        diff = Device.nsToMs(System.nanoTime() - time);
//        Log.i("Time to list " + diff + " / " + count + " = " + Math.round(diff/count));
//
//        count = 10000;
//        Assert.assertNotNull(ids);
//        Assert.assertEquals(ids.size(), count);
//
//        Storable storable = null;
//        long time2 = System.nanoTime();
//        for (Long id : ids) {
//            storable = new SessionImpl(id);
//        }
//
//        time = System.nanoTime();
//        for (Long id : ids) {
//            storable = Storage.pop(new SessionImpl(id));
//        }
//
//        Assert.assertNotNull(storable);
//
//        diff = Device.nsToMs((System.nanoTime() - time) - (time - time2));
//        Log.i("Time to pop " + diff + " / " + count + " = " + Math.round(diff/count));
//
////        try {
////            JSONObject json = new JSONObject();
////            json.put("timestamp", System.currentTimeMillis());
////            json.put("hour", 1);
////            json.put("dow", 1);
////            json.put("dow", 1);
////
////            JSONObject eve = new JSONObject();
////            eve.put("key", "key1");
////            eve.put("count", 2);
////            eve.put("timestamp", System.currentTimeMillis());
////            eve.put("hour", 1);
////            eve.put("dow", 2);
////
////            Map<String, String> segmentation = new HashMap<>();
////            segmentation.put("k1", "v1");
////            segmentation.put("k2", "v2");
////            eve.put("segmentation", segmentation);
////            eve.put("dur", 3);
////            eve.put("sum", 4);
////
////            json.put("events", new JSONArray(Collections.singletonList(eve)));
////
////            String jsonString = json.toString();
////
////            SharedPreferences preferences = getContext().getSharedPreferences("test", Ctx.MODE_PRIVATE);
////            preferences.edit().clear().commit();
////
////            count = 10000;
////            time = System.nanoTime();
////            for (int i = 0; i < count; i++) {
////                preferences.edit().putString("session" + i, jsonString).commit();
////            }
////            diff = Utils.nsToMs((System.nanoTime() - time) - (time - time2));
////            System.out.println("Time to save in SharedPreferences " + diff + " / " + count + " = " + Math.round(diff/count));
////
////        } catch (JSONException e) {
////            e.printStackTrace();
////        }
////        try {
////            JSONObject eve = new JSONObject();
////            eve.put("key", "key1");
////            eve.put("count", 2);
////            eve.put("timestamp", System.currentTimeMillis());
////            eve.put("hour", 1);
////            eve.put("dow", 2);
////
////            Map<String, String> segmentation = new HashMap<>();
////            segmentation.put("k1", "v1");
////            segmentation.put("k2", "v2");
////            eve.put("segmentation", segmentation);
////            eve.put("dur", 3);
////            eve.put("sum", 4);
////
////            SharedPreferences preferences = getContext().getSharedPreferences("test", Ctx.MODE_PRIVATE);
////            preferences.edit().clear().commit();
////
////            count = 2000;
////            time = System.nanoTime();
////
////            long step = System.nanoTime();
////            for (int i = 0; i < count; i++) {
////                String eventsJSON = preferences.getString("events", null);
////                JSONArray array;
////                if (eventsJSON == null) {
////                    array = new JSONArray();
////                } else {
////                    array = new JSONArray(eventsJSON);
////                }
////                array.put(eve);
////
////                preferences.edit().putString("events", array.toString()).commit();
////
////                if (i % 10 == 0) {
////                    diff = Utils.nsToMs(System.nanoTime() - step);
////                    System.out.println("Time to save events step " + i + " in SharedPreferences " + diff + " / " + 10 + " = " + Math.round(diff/10));
////                    step = System.nanoTime();
////                }
////            }
////            diff = Utils.nsToMs(System.nanoTime() - time);
////            System.out.println("Time to save in SharedPreferences " + diff + " / " + count + " = " + Math.round(diff/count));
////
////        } catch (JSONException e) {
////            e.printStackTrace();
////        }
//    }

    private Storable storable() {
        return ModuleRequests.nonSessionRequest(ctx);
    }
}
