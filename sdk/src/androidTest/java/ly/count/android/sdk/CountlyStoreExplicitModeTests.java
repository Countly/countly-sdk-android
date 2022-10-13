package ly.count.android.sdk;

import android.content.SharedPreferences;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

@RunWith(AndroidJUnit4.class)
public class CountlyStoreExplicitModeTests {
   CountlyStore store;
   StorageProvider sp;
   final String countlyStoreName = "COUNTLY_STORE";

   //todo make sure temp ID requests don't get stuck in the queue

   // RQ notes
   // read
   // String[] getRequests()
   // String getRequestQueueRaw()

   // write
   // void addRequest(final String requestStr, final boolean writeInSync)
   // deleteOldestRequest()
   // removeRequest(final String requestStr)
   // replaceRequestList(final List<String> newConns)

   //EQ notes
   // read
   // getEvents

   // write
   // setEventData
   // addEvent

   @Before
   public void setUp() {
      Countly.sharedInstance().setLoggingEnabled(true);
      store = new CountlyStore(getContext(), mock(ModuleLog.class), false);
      sp = store;
      store.clear();
   }

   @After
   public void tearDown() {
      store.clear();
   }

   void validateRQArrays(String[] targetReqArray, String[] targetESArray, CountlyStore storeRegular, CountlyStore storeExplicit) {
      String targetReg = Utils.joinCountlyStore(Arrays.asList(targetReqArray), CountlyStore.DELIMITER);
      String targetES = Utils.joinCountlyStore(Arrays.asList(targetESArray), CountlyStore.DELIMITER);

      assertEquals(targetReg, storeRegular.getRequestQueueRaw());
      assertEquals(targetReqArray, storeRegular.getRequests());
      assertEquals(targetES, storeExplicit.getRequestQueueRaw());
      assertEquals(targetESArray, storeExplicit.getRequests());
   }

   @Test
   public void rqAddRequestSetGetWriteCache() {
      CountlyStore emStore = new CountlyStore(getContext(), mock(ModuleLog.class), true);

      validateRQArrays(new String[] {}, new String[] {}, store, emStore);

      emStore.addRequest("abc", true);
      emStore.addRequest("123", false);

      validateRQArrays(new String[] {}, new String[] { "abc", "123" }, store, emStore);

      emStore.esWriteCacheToStorage();

      validateRQArrays(new String[] { "abc", "123" }, new String[] { "abc", "123" }, store, emStore);
   }

   @Test
   public void rqDeleteOldestRequest() {
      store.addRequest("a", true);
      store.addRequest("b", false);
      store.addRequest("c", false);

      CountlyStore emStore = new CountlyStore(getContext(), mock(ModuleLog.class), true);

      validateRQArrays(new String[] { "a", "b", "c" }, new String[] { "a", "b", "c" }, store, emStore);

      emStore.deleteOldestRequest();

      validateRQArrays(new String[] { "a", "b", "c" }, new String[] { "b", "c" }, store, emStore);

      emStore.deleteOldestRequest();

      validateRQArrays(new String[] { "a", "b", "c" }, new String[] { "c" }, store, emStore);

      emStore.esWriteCacheToStorage();

      validateRQArrays(new String[] { "c" }, new String[] { "c" }, store, emStore);
   }

   @Test
   public void rqRemoveRequest() {
      store.addRequest("a", true);
      store.addRequest("b", false);
      store.addRequest("c", false);

      CountlyStore emStore = new CountlyStore(getContext(), mock(ModuleLog.class), true);

      validateRQArrays(new String[] { "a", "b", "c" }, new String[] { "a", "b", "c" }, store, emStore);

      emStore.removeRequest("b");

      validateRQArrays(new String[] { "a", "b", "c" }, new String[] { "a", "c" }, store, emStore);

      emStore.esWriteCacheToStorage();

      validateRQArrays(new String[] { "a", "c" }, new String[] { "a", "c" }, store, emStore);
   }

   @Test
   public void rqReplaceRequestList() {
      store.addRequest("a", true);
      store.addRequest("b", false);
      store.addRequest("c", false);

      CountlyStore emStore = new CountlyStore(getContext(), mock(ModuleLog.class), true);

      validateRQArrays(new String[] { "a", "b", "c" }, new String[] { "a", "b", "c" }, store, emStore);

      List<String> newR = new ArrayList<>();
      newR.add("1");
      newR.add("2");
      newR.add("3");
      newR.add("4");

      emStore.replaceRequestList(newR);

      validateRQArrays(new String[] { "a", "b", "c" }, new String[] { "1", "2", "3", "4" }, store, emStore);

      emStore.esWriteCacheToStorage();

      validateRQArrays(new String[] { "1", "2", "3", "4" }, new String[] { "1", "2", "3", "4" }, store, emStore);
   }

   void populateEvents(String [] events, CountlyStore store) {
      StringBuilder sb = new StringBuilder();
      for(int a = 0 ; a < events.length ; a++) {
         if(a != 0) {
            sb.append(CountlyStore.DELIMITER);
         }

         sb.append(events[a]);
      }

      store.setEventData(sb.toString());
   }

   void validateEQArrays(String[] targetRegArray, String[] targetESArray, CountlyStore storeRegular, CountlyStore storeExplicit) {
      assertEquals(targetRegArray, storeRegular.getEvents());
      assertEquals(targetESArray, storeExplicit.getEvents());
   }

   @Test
   public void eqGetEvents() {
      populateEvents(new String[] {"1", "2", "3"}, store);

      CountlyStore emStore = new CountlyStore(getContext(), mock(ModuleLog.class), true);

      validateEQArrays(new String[] {"1", "2", "3"}, new String[] {"1", "2", "3"}, store, emStore);

      populateEvents(new String[] {"a", "b", "c"}, emStore);

      validateEQArrays(new String[] {"1", "2", "3"}, new String[] {"a", "b", "c"}, store, emStore);
   }

   @Test
   public void basicFlow() {
      assertEquals(0, store.getEvents().length);
      assertEquals(0, store.getRequests().length);

      Countly countly = new Countly();
      CountlyConfig config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableExplicitStorageMode();
      countly.init(config);

      countly.events().recordEvent("aa");
      countly.events().recordEvent("aabb");
      countly.events().recordEvent("aabbcc");

      assertEquals(0, store.getEvents().length);
      assertEquals(0, store.getRequests().length);

      countly.requestQueue().attemptToSendStoredRequests();

      countly.events().recordEvent("2aabb");
      countly.events().recordEvent("3aabbcc");

      assertEquals(0, store.getEvents().length);
      assertEquals(0, store.getRequests().length);

      countly.requestQueue().esWriteCachesToPersistence();

      assertTrue(store.getEvents().length == 2);
      assertTrue(store.getRequests().length == 1);

      countly.requestQueue().attemptToSendStoredRequests();

      assertTrue(store.getEvents().length == 2);
      assertTrue(store.getRequests().length == 1);

      countly.requestQueue().esWriteCachesToPersistence();

      assertTrue(store.getEvents().length == 0);
      assertTrue(store.getRequests().length == 2);

   }
}
