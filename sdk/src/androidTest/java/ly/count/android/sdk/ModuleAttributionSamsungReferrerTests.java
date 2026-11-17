package ly.count.android.sdk;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.mockito.Mockito.mock;

/**
 * The Samsung Galaxy Store install referrer, and the store client handling it shares with HUAWEI's.
 * <p>
 * Request contract (Countly/countly-platform, the referrer signal's store field):
 * attribution={"type":"referrer","store":"samsung","value":raw,"click_ts":seconds,"install_ts":seconds}.
 * Response codes of Samsung's (and HUAWEI's) library: SERVICE_DISCONNECTED -1, OK 0, SERVICE_UNAVAILABLE 1,
 * FEATURE_NOT_SUPPORTED 2 (Galaxy Store older than 4.5.20.1), DEVELOPER_ERROR 3.
 */
@RunWith(AndroidJUnit4.class)
public class ModuleAttributionSamsungReferrerTests {
    static final String SAMSUNG_RAW = "utm_source=galaxy_store&utm_medium=cpi&utm_campaign=spring_gs";
    static final String PLAY_RAW = "utm_source=google&utm_campaign=spring";

    /**
     * Stands in for the Samsung library's client: answers the setup with a response code
     */
    static class FakeClient implements StoreInstallReferrerReader.StoreClient {
        int responseCode = StoreInstallReferrerReader.RESPONSE_OK;
        boolean disconnectInstead = false;
        boolean disconnectAfter = false;
        boolean referrerThrows = false;
        InstallReferrerReader.Details details = new InstallReferrerReader.Details(SAMSUNG_RAW, 1_700_000_000L, 1_700_000_045L, 0, 0, null);
        int ends = 0;

        @Override public void start(@NonNull StoreInstallReferrerReader.StoreListener listener) {
            if (disconnectInstead) {
                listener.onDisconnected();
                return;
            }
            listener.onSetupFinished(responseCode);
            if (disconnectAfter) {
                listener.onDisconnected();
            }
        }

        @Nullable @Override public InstallReferrerReader.Details referrer() throws Exception {
            if (referrerThrows) {
                throw new IllegalStateException("store process died");
            }
            return details;
        }

        @Override public void end() {
            ends++;
        }
    }

    /**
     * The real StoreInstallReferrerReader flow with a fake client and a store that is or is not on the device
     */
    static class FakeStoreReader extends StoreInstallReferrerReader {
        final FakeClient client;
        boolean storeOnDevice = true;
        int clientsCreated = 0;

        FakeStoreReader(FakeClient client) {
            this.client = client;
        }

        @NonNull @Override String clientClass() {
            //a class that is always there: the "library" is present
            return "java.lang.String";
        }

        @Override boolean storePresent(@NonNull Context context) {
            return storeOnDevice;
        }

        @NonNull @Override StoreClient newClient(@NonNull Context context) {
            clientsCreated++;
            return client;
        }
    }

    List<String> results;

    @Before
    public void setUp() {
        new CountlyStore(TestUtils.getContext(), mock(ModuleLog.class)).clear();
        results = new ArrayList<>();
    }

    InstallReferrerReader.Callback recorder() {
        return new InstallReferrerReader.Callback() {
            @Override public void onResult(int status, InstallReferrerReader.Details details) {
                results.add(status + "|" + (details == null ? null : details.referrer));
            }
        };
    }

    // ------------------------------------------------------------------ the store client, each response code

    /**
     * OK: the referrer and both times are read, the connection is ended, the callback comes once even if the
     * connection is lost afterwards
     */
    @Test
    public void client_ok() {
        FakeClient client = new FakeClient();
        client.disconnectAfter = true;
        FakeStoreReader reader = new FakeStoreReader(client);
        Assert.assertTrue(reader.isAvailable());
        reader.read(TestUtils.getContext(), recorder());

        Assert.assertEquals(1, results.size());
        Assert.assertEquals(InstallReferrerReader.STATUS_OK + "|" + SAMSUNG_RAW, results.get(0));
        Assert.assertEquals(1, client.ends);
    }

    /**
     * SERVICE_UNAVAILABLE (1) and a lost connection (-1, or onInstallReferrerServiceDisconnected) are asked again
     * on a later launch
     */
    @Test
    public void client_serviceUnavailableAndDisconnected_retry() {
        for (int code : new int[] { StoreInstallReferrerReader.RESPONSE_SERVICE_UNAVAILABLE, StoreInstallReferrerReader.RESPONSE_SERVICE_DISCONNECTED }) {
            FakeClient client = new FakeClient();
            client.responseCode = code;
            new FakeStoreReader(client).read(TestUtils.getContext(), recorder());
            Assert.assertEquals(1, client.ends);
        }
        FakeClient lost = new FakeClient();
        lost.disconnectInstead = true;
        new FakeStoreReader(lost).read(TestUtils.getContext(), recorder());

        Assert.assertEquals(3, results.size());
        for (String result : results) {
            Assert.assertEquals(InstallReferrerReader.STATUS_RETRY + "|null", result);
        }
    }

    /**
     * FEATURE_NOT_SUPPORTED (2, a Galaxy Store older than 4.5.20.1) and DEVELOPER_ERROR (3) never answer
     */
    @Test
    public void client_featureNotSupportedAndDeveloperError_notSupported() {
        for (int code : new int[] { StoreInstallReferrerReader.RESPONSE_FEATURE_NOT_SUPPORTED, StoreInstallReferrerReader.RESPONSE_DEVELOPER_ERROR }) {
            FakeClient client = new FakeClient();
            client.responseCode = code;
            new FakeStoreReader(client).read(TestUtils.getContext(), recorder());
            Assert.assertEquals(1, client.ends);
        }
        Assert.assertEquals(2, results.size());
        for (String result : results) {
            Assert.assertEquals(InstallReferrerReader.STATUS_NOT_SUPPORTED + "|null", result);
        }
    }

    /**
     * Reading after OK fails: asked again later, the connection is still ended
     */
    @Test
    public void client_referrerThrows_retry() {
        FakeClient client = new FakeClient();
        client.referrerThrows = true;
        new FakeStoreReader(client).read(TestUtils.getContext(), recorder());
        Assert.assertEquals(InstallReferrerReader.STATUS_RETRY + "|null", results.get(0));
        Assert.assertEquals(1, client.ends);
    }

    /**
     * No Galaxy Store on the device: no client is created, and the store is never asked again
     */
    @Test
    public void client_noStoreOnDevice_notSupported() {
        FakeStoreReader reader = new FakeStoreReader(new FakeClient());
        reader.storeOnDevice = false;
        reader.read(TestUtils.getContext(), recorder());
        Assert.assertEquals(InstallReferrerReader.STATUS_NOT_SUPPORTED + "|null", results.get(0));
        Assert.assertEquals(0, reader.clientsCreated);
    }

    /**
     * The real reader in this test APK, which does not include Samsung's library
     */
    @Test
    public void realReader_withoutLibrary() {
        SamsungInstallReferrerReader reader = new SamsungInstallReferrerReader();
        Assert.assertFalse(reader.isAvailable());
        Assert.assertEquals("com.samsung.android.sdk.sinstallreferrer.api.InstallReferrerClient", reader.clientClass());
        boolean store = reader.storePresent(TestUtils.getContext());
        reader.read(TestUtils.getContext(), recorder());
        //without Galaxy Store: not supported; with it (a Samsung phone): the missing library classes are caught
        Assert.assertEquals((store ? InstallReferrerReader.STATUS_RETRY : InstallReferrerReader.STATUS_NOT_SUPPORTED) + "|null", results.get(0));
    }

    // ------------------------------------------------------------------ the signal

    CountlyConfig config(InstallReferrerReader play, InstallReferrerReader samsung) {
        CountlyConfig cc = TestUtils.createAttributionCountlyConfig(false, null, null, null, null, null, null);
        cc.installReferrerReader = play;
        cc.samsungReferrerReader = samsung;
        return cc;
    }

    static ModuleAttributionHuaweiReferrerTests.FakeStoreReader noPlay() {
        ModuleAttributionHuaweiReferrerTests.FakeStoreReader play = ModuleAttributionHuaweiReferrerTests.play();
        play.available = false;
        return play;
    }

    static List<String> sentAttribution() {
        List<String> out = new ArrayList<>();
        for (Map<String, String> request : TestUtils.getCurrentRQ()) {
            if (request != null && request.containsKey("attribution")) {
                out.add(request.get("attribution"));
            }
        }
        return out;
    }

    static void assertSamsungSignal(JSONObject signal) throws Exception {
        Assert.assertEquals("referrer", signal.getString("type"));
        Assert.assertEquals("samsung", signal.getString("store"));
        Assert.assertEquals(SAMSUNG_RAW, signal.getString("value"));
        Assert.assertEquals(1_700_000_000L, signal.getLong("click_ts"));
        Assert.assertEquals(1_700_000_045L, signal.getLong("install_ts"));
        Assert.assertEquals(5, signal.length());
    }

    /**
     * Sent once, with store samsung and Samsung's two times as given
     */
    @Test
    public void samsung_sentOnce() throws Exception {
        FakeStoreReader samsung = new FakeStoreReader(new FakeClient());
        new Countly().init(config(noPlay(), samsung));
        new Countly().init(config(noPlay(), samsung));

        List<String> sent = sentAttribution();
        Assert.assertEquals(1, sent.size());
        assertSamsungSignal(new JSONObject(sent.get(0)));
        Assert.assertEquals(1, samsung.clientsCreated);
    }

    /**
     * SERVICE_UNAVAILABLE on two launches, then OK on the third: sent then, once
     */
    @Test
    public void samsung_retryThenSuccess() throws Exception {
        FakeClient client = new FakeClient();
        client.responseCode = StoreInstallReferrerReader.RESPONSE_SERVICE_UNAVAILABLE;
        FakeStoreReader samsung = new FakeStoreReader(client);
        new Countly().init(config(noPlay(), samsung));
        new Countly().init(config(noPlay(), samsung));
        Assert.assertEquals(0, sentAttribution().size());

        client.responseCode = StoreInstallReferrerReader.RESPONSE_OK;
        new Countly().init(config(noPlay(), samsung));
        new Countly().init(config(noPlay(), samsung));
        Assert.assertEquals(3, samsung.clientsCreated);
        List<String> sent = sentAttribution();
        Assert.assertEquals(1, sent.size());
        assertSamsungSignal(new JSONObject(sent.get(0)));
    }

    /**
     * SERVICE_UNAVAILABLE on every launch: asked on 3 launches, as Samsung recommends, then not again
     */
    @Test
    public void samsung_retryThenGiveUp() {
        FakeClient client = new FakeClient();
        client.responseCode = StoreInstallReferrerReader.RESPONSE_SERVICE_UNAVAILABLE;
        FakeStoreReader samsung = new FakeStoreReader(client);
        for (int i = 0; i < 5; i++) {
            new Countly().init(config(noPlay(), samsung));
        }
        Assert.assertEquals(ModuleAttribution.MAX_REFERRER_ATTEMPTS, samsung.clientsCreated);
        Assert.assertEquals(0, sentAttribution().size());
    }

    /**
     * FEATURE_NOT_SUPPORTED and an empty referrer: nothing sent, never asked again
     */
    @Test
    public void samsung_notSupportedAndEmpty_askedOnce() {
        FakeClient old = new FakeClient();
        old.responseCode = StoreInstallReferrerReader.RESPONSE_FEATURE_NOT_SUPPORTED;
        FakeStoreReader samsung = new FakeStoreReader(old);
        new Countly().init(config(noPlay(), samsung));
        new Countly().init(config(noPlay(), samsung));
        Assert.assertEquals(1, samsung.clientsCreated);

        new CountlyStore(TestUtils.getContext(), mock(ModuleLog.class)).clear();
        FakeClient empty = new FakeClient();
        empty.details = new InstallReferrerReader.Details("");
        FakeStoreReader emptyReader = new FakeStoreReader(empty);
        new Countly().init(config(noPlay(), emptyReader));
        new Countly().init(config(noPlay(), emptyReader));
        Assert.assertEquals(1, emptyReader.clientsCreated);
        Assert.assertEquals(0, sentAttribution().size());
    }

    /**
     * Every store answers on a first launch: Play, Meta, HUAWEI and Samsung in one request, Play first
     */
    @Test
    public void samsung_withAllStoresInOneRequest() throws Exception {
        CountlyConfig cc = config(ModuleAttributionHuaweiReferrerTests.play(), new FakeStoreReader(new FakeClient()));
        cc.huaweiReferrerReader = ModuleAttributionHuaweiReferrerTests.huawei();
        cc.metaReferrerReader = new ModuleAttributionMetaReferrerTests.FakeMetaReader();
        cc.setFacebookAppId(ModuleAttributionMetaReferrerTests.FB_APP_ID);
        new Countly().init(cc);

        List<String> sent = sentAttribution();
        Assert.assertEquals(1, sent.size());
        JSONArray list = new JSONArray(sent.get(0));
        Assert.assertEquals(4, list.length());
        Assert.assertEquals(PLAY_RAW, list.getJSONObject(0).getString("value"));
        Assert.assertEquals("meta_referrer", list.getJSONObject(1).getString("type"));
        Assert.assertEquals("huawei", list.getJSONObject(2).getString("store"));
        assertSamsungSignal(list.getJSONObject(3));
    }
}
