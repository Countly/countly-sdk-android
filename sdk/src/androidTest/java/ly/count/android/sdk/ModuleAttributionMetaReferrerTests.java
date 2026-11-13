package ly.count.android.sdk;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.mockito.Mockito.mock;

/**
 * Phase 2 signals of the Android SDK: the Meta Install Referrer and the Play referrer timestamps.
 * <p>
 * Request contract (Countly/countly-platform, plugins/attribution/api/parts/acquisition.js parseOneSignal):
 * attribution={"type":"meta_referrer","value":raw,"ct":0|1,"ts":seconds}, and the Play referrer with
 * click_ts, install_ts, click_ts_server, install_ts_server (seconds); several first launch signals go as a list.
 */
@RunWith(AndroidJUnit4.class)
public class ModuleAttributionMetaReferrerTests {
    static final String FB_APP_ID = "1234567890123456";
    static final String META_RAW = "{\"utm_campaign\":\"fb4a\",\"utm_content\":{\"source\":{\"data\":\"afe56cf6\",\"nonce\":\"b0ed4b7a\"},\"a\":\"1\"}}";
    static final String PLAY_RAW = "utm_source=google&utm_campaign=spring";
    static final String[] ALL_BUT_ATTRIBUTION = { Countly.CountlyFeatureNames.sessions, Countly.CountlyFeatureNames.events };

    CountlyStore countlyStore;

    /**
     * Stands in for the Meta apps
     */
    static class FakeMetaReader implements MetaReferrerReader {
        Result result = new Result(META_RAW, 1, 1_700_000_000L);
        int reads = 0;
        String askedAppId;

        @Override public void read(@NonNull Context context, @NonNull String facebookAppId, @NonNull Callback callback) {
            reads++;
            askedAppId = facebookAppId;
            callback.onResult(result);
        }
    }

    /**
     * Stands in for the Play Install Referrer library, with timestamps
     */
    static class DetailsReader implements InstallReferrerReader {
        boolean available = true;
        int status = STATUS_OK;
        Details details = new Details(PLAY_RAW, 1_700_000_100L, 1_700_000_160L, 1_700_000_101L, 1_700_000_158L, "26.1.6");
        int reads = 0;

        @Override public boolean isAvailable() {
            return available;
        }

        @Override public void read(@NonNull Context context, @NonNull Callback callback) {
            reads++;
            callback.onResult(status, details);
        }
    }

    @Before
    public void setUp() {
        countlyStore = new CountlyStore(TestUtils.getContext(), mock(ModuleLog.class));
        countlyStore.clear();
        FakeMetaReferrerProvider.reset();
    }

    @After
    public void tearDown() {
        FakeMetaReferrerProvider.reset();
    }

    CountlyConfig config(InstallReferrerReader play, MetaReferrerReader meta, String fbAppId) {
        CountlyConfig cc = TestUtils.createAttributionCountlyConfig(false, null, null, null, null, null, null);
        cc.installReferrerReader = play;
        cc.metaReferrerReader = meta;
        cc.setFacebookAppId(fbAppId);
        return cc;
    }

    /**
     * The attribution param of every request in the real queue, as sent (a JSON object or list)
     */
    static List<String> queuedAttribution() {
        List<String> out = new ArrayList<>();
        for (Map<String, String> request : TestUtils.getCurrentRQ()) {
            if (request != null && request.containsKey("attribution")) {
                out.add(request.get("attribution"));
            }
        }
        return out;
    }

    static void assertMetaSignal(JSONObject signal, int ct, long ts) throws Exception {
        Assert.assertEquals("meta_referrer", signal.getString("type"));
        Assert.assertEquals(META_RAW, signal.getString("value"));
        Assert.assertEquals(ct, signal.getInt("ct"));
        Assert.assertEquals(ts, signal.getLong("ts"));
        Assert.assertEquals(4, signal.length());
    }

    static void assertPlaySignalWithTimes(JSONObject signal) throws Exception {
        Assert.assertEquals("referrer", signal.getString("type"));
        Assert.assertEquals(PLAY_RAW, signal.getString("value"));
        Assert.assertEquals(1_700_000_100L, signal.getLong("click_ts"));
        Assert.assertEquals(1_700_000_160L, signal.getLong("install_ts"));
        Assert.assertEquals(1_700_000_101L, signal.getLong("click_ts_server"));
        Assert.assertEquals(1_700_000_158L, signal.getLong("install_ts_server"));
        Assert.assertEquals("26.1.6", signal.getString("install_version"));
        Assert.assertEquals(7, signal.length());
    }

    // ------------------------------------------------------------------ Play referrer timestamps

    /**
     * The Play referrer carries Play's timestamps under the names the server parses, and the install version
     */
    @Test
    public void playReferrer_sentWithTimestamps() throws Exception {
        DetailsReader play = new DetailsReader();
        new Countly().init(config(play, null, null));

        List<String> sent = queuedAttribution();
        Assert.assertEquals(1, sent.size());
        assertPlaySignalWithTimes(new JSONObject(sent.get(0)));

        new Countly().init(config(play, null, null));
        Assert.assertEquals(1, play.reads);
        Assert.assertEquals(1, queuedAttribution().size());
    }

    /**
     * Timestamps the store did not give (0) are left out; the server pair alone is enough for the flags
     */
    @Test
    public void playReferrer_missingTimestampsOmitted() throws Exception {
        DetailsReader play = new DetailsReader();
        play.details = new InstallReferrerReader.Details(PLAY_RAW, 0, 0, 1_700_000_101L, 1_700_000_158L, null);
        new Countly().init(config(play, null, null));

        JSONObject signal = new JSONObject(queuedAttribution().get(0));
        Assert.assertEquals(1_700_000_101L, signal.getLong("click_ts_server"));
        Assert.assertEquals(1_700_000_158L, signal.getLong("install_ts_server"));
        Assert.assertFalse(signal.has("click_ts"));
        Assert.assertFalse(signal.has("install_ts"));
        Assert.assertFalse(signal.has("install_version"));
        Assert.assertEquals(4, signal.length());
    }

    /**
     * Without any timestamp the request is exactly what the SDK sent before: {"type":"referrer","value":raw}
     */
    @Test
    public void playReferrer_noTimestamps_unchanged() throws Exception {
        DetailsReader play = new DetailsReader();
        play.details = new InstallReferrerReader.Details(PLAY_RAW);
        new Countly().init(config(play, null, null));

        Assert.assertEquals("{\"type\":\"referrer\",\"value\":\"" + PLAY_RAW + "\"}", queuedAttribution().get(0));
    }

    // ------------------------------------------------------------------ Meta Install Referrer

    /**
     * Facebook app id set: the Meta referrer and the Play referrer go in one request, as a list, so the server
     * picks the strongest; neither is read or sent again on the next launch
     */
    @Test
    public void meta_sentWithPlayReferrerInOneRequest() throws Exception {
        DetailsReader play = new DetailsReader();
        FakeMetaReader meta = new FakeMetaReader();
        new Countly().init(config(play, meta, FB_APP_ID));

        List<String> sent = queuedAttribution();
        Assert.assertEquals(1, sent.size());
        JSONArray list = new JSONArray(sent.get(0));
        Assert.assertEquals(2, list.length());
        assertPlaySignalWithTimes(list.getJSONObject(0));
        assertMetaSignal(list.getJSONObject(1), 1, 1_700_000_000L);
        Assert.assertEquals(FB_APP_ID, meta.askedAppId);

        new Countly().init(config(play, meta, FB_APP_ID));
        Assert.assertEquals(1, meta.reads);
        Assert.assertEquals(1, play.reads);
        Assert.assertEquals(1, queuedAttribution().size());
    }

    /**
     * Without the Play referrer library the Meta referrer goes alone, as one object; a view keeps ct 0
     */
    @Test
    public void meta_aloneWithoutPlayLibrary_view() throws Exception {
        DetailsReader play = new DetailsReader();
        play.available = false;
        FakeMetaReader meta = new FakeMetaReader();
        meta.result = new MetaReferrerReader.Result(META_RAW, 0, 1_700_000_050L);
        new Countly().init(config(play, meta, FB_APP_ID));

        List<String> sent = queuedAttribution();
        Assert.assertEquals(1, sent.size());
        assertMetaSignal(new JSONObject(sent.get(0)), 0, 1_700_000_050L);
    }

    /**
     * The Play store cannot answer this launch: the Meta referrer is not held back for a later launch
     */
    @Test
    public void meta_aloneWhenPlayRetries() throws Exception {
        DetailsReader play = new DetailsReader();
        play.status = InstallReferrerReader.STATUS_RETRY;
        FakeMetaReader meta = new FakeMetaReader();
        new Countly().init(config(play, meta, FB_APP_ID));

        List<String> sent = queuedAttribution();
        Assert.assertEquals(1, sent.size());
        assertMetaSignal(new JSONObject(sent.get(0)), 1, 1_700_000_000L);

        //the Play referrer answers on the next launch and goes alone; Meta is not read again
        play.status = InstallReferrerReader.STATUS_OK;
        new Countly().init(config(play, meta, FB_APP_ID));
        Assert.assertEquals(1, meta.reads);
        sent = queuedAttribution();
        Assert.assertEquals(2, sent.size());
        assertPlaySignalWithTimes(new JSONObject(sent.get(1)));
    }

    /**
     * The app adds the Facebook app id in an update, after the Play referrer was sent: Meta is read once and sent alone
     */
    @Test
    public void meta_afterPlayReferrerWasSent() throws Exception {
        DetailsReader play = new DetailsReader();
        new Countly().init(config(play, null, null));
        Assert.assertEquals(1, queuedAttribution().size());

        FakeMetaReader meta = new FakeMetaReader();
        new Countly().init(config(play, meta, FB_APP_ID));
        Assert.assertEquals(1, play.reads);
        List<String> sent = queuedAttribution();
        Assert.assertEquals(2, sent.size());
        assertMetaSignal(new JSONObject(sent.get(1)), 1, 1_700_000_000L);
    }

    /**
     * No Meta app answered: nothing extra is sent and Meta is not asked again
     */
    @Test
    public void meta_noAnswer_nothingSent_readOnce() throws Exception {
        DetailsReader play = new DetailsReader();
        play.available = false;
        FakeMetaReader meta = new FakeMetaReader();
        meta.result = null;
        new Countly().init(config(play, meta, FB_APP_ID));
        new Countly().init(config(play, meta, FB_APP_ID));

        Assert.assertEquals(0, queuedAttribution().size());
        Assert.assertEquals(1, meta.reads);
    }

    /**
     * Without a Facebook app id Meta is never asked, and the Play referrer goes as before
     */
    @Test
    public void meta_offWithoutFacebookAppId() throws Exception {
        DetailsReader play = new DetailsReader();
        FakeMetaReader meta = new FakeMetaReader();
        new Countly().init(config(play, meta, null));
        new Countly().init(config(play, meta, ""));

        Assert.assertEquals(0, meta.reads);
        List<String> sent = queuedAttribution();
        Assert.assertEquals(1, sent.size());
        assertPlaySignalWithTimes(new JSONObject(sent.get(0)));
    }

    /**
     * Without the attribution consent Meta is not asked; when it is given, it is read and sent
     */
    @Test
    public void meta_consentGated() throws Exception {
        DetailsReader play = new DetailsReader();
        play.available = false;
        FakeMetaReader meta = new FakeMetaReader();
        CountlyConfig cc = config(play, meta, FB_APP_ID);
        cc.setRequiresConsent(true).setConsentEnabled(ALL_BUT_ATTRIBUTION);
        Countly countly = new Countly().init(cc);

        Assert.assertEquals(0, meta.reads);
        Assert.assertEquals(0, queuedAttribution().size());

        countly.consent().giveConsent(new String[] { Countly.CountlyFeatureNames.attribution });
        Assert.assertEquals(1, meta.reads);
        List<String> sent = queuedAttribution();
        Assert.assertEquals(1, sent.size());
        assertMetaSignal(new JSONObject(sent.get(0)), 1, 1_700_000_000L);
    }

    /**
     * The real reader against real providers (this test app's): the SDK asks content://<authority>/<app id>,
     * never blocks init, and sends what the provider answered
     */
    @Test
    public void meta_realReader_endToEnd() throws Exception {
        FakeMetaReferrerProvider.answers.put(FakeMetaReferrerProvider.AUTHORITY_A, new Object[] { META_RAW, 1, 1_700_000_000L });
        FakeMetaReferrerProvider.delayMs = 300;
        DetailsReader play = new DetailsReader();
        play.available = false;
        CountlyConfig cc = config(play, testReader(), FB_APP_ID);

        long start = System.currentTimeMillis();
        new Countly().init(cc);
        Assert.assertTrue("init waited for the provider", System.currentTimeMillis() - start < 300);

        List<String> sent = waitForAttribution(1);
        Assert.assertEquals(1, sent.size());
        assertMetaSignal(new JSONObject(sent.get(0)), 1, 1_700_000_000L);
        Assert.assertEquals(FB_APP_ID, FakeMetaReferrerProvider.asked.get(FakeMetaReferrerProvider.AUTHORITY_A));
    }

    // ------------------------------------------------------------------ the reader

    ContentProviderMetaReferrerReader testReader() {
        String pkg = TestUtils.getContext().getPackageName();
        return new ContentProviderMetaReferrerReader(new String[][] {
            { FakeMetaReferrerProvider.AUTHORITY_A, pkg },
            { FakeMetaReferrerProvider.AUTHORITY_B, pkg },
            { "ly.count.android.sdk.test.metareferrer.not_installed", pkg },
        }, mock(ModuleLog.class));
    }

    MetaReferrerReader.Result readAndWait(ContentProviderMetaReferrerReader reader) throws Exception {
        final AtomicReference<MetaReferrerReader.Result> answer = new AtomicReference<>();
        final CountDownLatch done = new CountDownLatch(1);
        reader.read(TestUtils.getContext(), FB_APP_ID, result -> {
            answer.set(result);
            done.countDown();
        });
        Assert.assertTrue(done.await(10, TimeUnit.SECONDS));
        return answer.get();
    }

    /**
     * The provider's three columns are read; a provider that is not installed is skipped
     */
    @Test
    public void reader_readsColumns() throws Exception {
        FakeMetaReferrerProvider.answers.put(FakeMetaReferrerProvider.AUTHORITY_A, new Object[] { META_RAW, 0, 1_700_000_000L });
        MetaReferrerReader.Result result = readAndWait(testReader());
        Assert.assertNotNull(result);
        Assert.assertEquals(META_RAW, result.installReferrer);
        Assert.assertEquals(0, result.isCt);
        Assert.assertEquals(1_700_000_000L, result.actualTimestamp);
        Assert.assertEquals(FB_APP_ID, FakeMetaReferrerProvider.asked.get(FakeMetaReferrerProvider.AUTHORITY_A));
    }

    /**
     * Two apps answer: the latest interaction wins
     */
    @Test
    public void reader_latestWins() throws Exception {
        FakeMetaReferrerProvider.answers.put(FakeMetaReferrerProvider.AUTHORITY_A, new Object[] { "older", 1, 1_700_000_000L });
        FakeMetaReferrerProvider.answers.put(FakeMetaReferrerProvider.AUTHORITY_B, new Object[] { "newer", 0, 1_700_000_500L });
        MetaReferrerReader.Result result = readAndWait(testReader());
        Assert.assertEquals("newer", result.installReferrer);
        Assert.assertEquals(0, result.isCt);
    }

    /**
     * No row, an empty referrer, or missing is_ct and actual_timestamp
     */
    @Test
    public void reader_emptyAndMissingColumns() throws Exception {
        Assert.assertNull(readAndWait(testReader()));

        FakeMetaReferrerProvider.answers.put(FakeMetaReferrerProvider.AUTHORITY_A, new Object[] { " ", 1, 1_700_000_000L });
        Assert.assertNull(readAndWait(testReader()));

        FakeMetaReferrerProvider.answers.put(FakeMetaReferrerProvider.AUTHORITY_A, new Object[] { META_RAW, null, null });
        MetaReferrerReader.Result result = readAndWait(testReader());
        Assert.assertEquals(1, result.isCt);
        Assert.assertEquals(0, result.actualTimestamp);
    }

    /**
     * A provider that belongs to another package than the Meta app's is not read
     */
    @Test
    public void reader_wrongPackageNotRead() throws Exception {
        FakeMetaReferrerProvider.answers.put(FakeMetaReferrerProvider.AUTHORITY_A, new Object[] { META_RAW, 1, 1_700_000_000L });
        ContentProviderMetaReferrerReader reader = new ContentProviderMetaReferrerReader(new String[][] {
            { FakeMetaReferrerProvider.AUTHORITY_A, "com.facebook.katana" },
        }, mock(ModuleLog.class));
        Assert.assertNull(readAndWait(reader));
        Assert.assertFalse(FakeMetaReferrerProvider.asked.containsKey(FakeMetaReferrerProvider.AUTHORITY_A));
    }

    /**
     * A provider that hangs: the callback comes with no result after the timeout
     */
    @Test
    public void reader_timeout() throws Exception {
        FakeMetaReferrerProvider.answers.put(FakeMetaReferrerProvider.AUTHORITY_A, new Object[] { META_RAW, 1, 1_700_000_000L });
        FakeMetaReferrerProvider.delayMs = 3000;
        ContentProviderMetaReferrerReader reader = testReader();
        reader.timeoutMs = 200;
        long start = System.currentTimeMillis();
        Assert.assertNull(readAndWait(reader));
        Assert.assertTrue(System.currentTimeMillis() - start < 2000);
    }

    /**
     * The SDK's own reader asks the three Meta apps
     */
    @Test
    public void reader_metaAuthorities() {
        Assert.assertEquals(3, ContentProviderMetaReferrerReader.META_PROVIDERS.length);
        Assert.assertArrayEquals(new String[] { "com.facebook.katana.provider.InstallReferrerProvider", "com.facebook.katana" }, ContentProviderMetaReferrerReader.META_PROVIDERS[0]);
        Assert.assertArrayEquals(new String[] { "com.instagram.contentprovider.InstallReferrerProvider", "com.instagram.android" }, ContentProviderMetaReferrerReader.META_PROVIDERS[1]);
        Assert.assertArrayEquals(new String[] { "com.facebook.lite.provider.InstallReferrerProvider", "com.facebook.lite" }, ContentProviderMetaReferrerReader.META_PROVIDERS[2]);
    }

    static List<String> waitForAttribution(int count) throws InterruptedException {
        long until = System.currentTimeMillis() + 5000;
        List<String> sent = queuedAttribution();
        while (sent.size() < count && System.currentTimeMillis() < until) {
            Thread.sleep(50);
            sent = queuedAttribution();
        }
        return sent;
    }
}
