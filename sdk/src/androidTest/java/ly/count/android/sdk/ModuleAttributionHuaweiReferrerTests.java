package ly.count.android.sdk;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.mockito.Mockito.mock;

/**
 * Phase 3 signals of the Android SDK: the HUAWEI AppGallery install referrer, and the attribution consent sent
 * with the signals.
 * <p>
 * Request contract (Countly/countly-platform, plugins/attribution/api/parts/acquisition.js parseOneSignal and
 * consent.js): attribution={"type":"referrer","store":"huawei","value":raw,"click_ts":seconds,"install_ts":seconds};
 * consent={"attribution":true} on the same request when the app requires consent.
 */
@RunWith(AndroidJUnit4.class)
public class ModuleAttributionHuaweiReferrerTests {
    static final String HUAWEI_RAW = "utm_source=huawei&utm_medium=cpi&utm_campaign=spring_hw";
    static final String PLAY_RAW = "utm_source=google&utm_campaign=spring";

    /**
     * Stands in for an install referrer library (HUAWEI's or Play's)
     */
    static class FakeStoreReader extends InstallReferrerReader {
        boolean available = true;
        int status = STATUS_OK;
        Details details;
        int reads = 0;

        FakeStoreReader(Details details) {
            this.details = details;
        }

        @Override boolean isAvailable() {
            return available;
        }

        @Override void read(@NonNull Context context, @NonNull Callback callback) {
            reads++;
            callback.onResult(status, status == STATUS_OK ? details : null);
        }
    }

    static FakeStoreReader huawei() {
        //HUAWEI gives milliseconds; the reader passes seconds on
        return new FakeStoreReader(new InstallReferrerReader.Details(HUAWEI_RAW, 1_700_000_000L, 1_700_000_090L, 0, 0, null));
    }

    static FakeStoreReader play() {
        return new FakeStoreReader(new InstallReferrerReader.Details(PLAY_RAW));
    }

    @Before
    public void setUp() {
        new CountlyStore(TestUtils.getContext(), mock(ModuleLog.class)).clear();
    }

    CountlyConfig config(InstallReferrerReader play, InstallReferrerReader huawei) {
        CountlyConfig cc = TestUtils.createAttributionCountlyConfig(false, null, null, null, null, null, null);
        cc.installReferrerReader = play;
        cc.huaweiReferrerReader = huawei;
        return cc;
    }

    static List<Map<String, String>> attributionRequests() {
        List<Map<String, String>> out = new ArrayList<>();
        for (Map<String, String> request : TestUtils.getCurrentRQ()) {
            if (request != null && request.containsKey("attribution")) {
                out.add(request);
            }
        }
        return out;
    }

    static void assertHuaweiSignal(JSONObject signal) throws Exception {
        Assert.assertEquals("referrer", signal.getString("type"));
        Assert.assertEquals("huawei", signal.getString("store"));
        Assert.assertEquals(HUAWEI_RAW, signal.getString("value"));
        Assert.assertEquals(1_700_000_000L, signal.getLong("click_ts"));
        Assert.assertEquals(1_700_000_090L, signal.getLong("install_ts"));
        Assert.assertEquals(5, signal.length());
    }

    // ------------------------------------------------------------------ HUAWEI AppGallery referrer

    /**
     * On a device without Play's referrer the HUAWEI referrer goes alone, once, with its store and both times
     */
    @Test
    public void huawei_sentOnce_alone() throws Exception {
        FakeStoreReader play = play();
        play.available = false;
        FakeStoreReader huawei = huawei();
        new Countly().init(config(play, huawei));
        new Countly().init(config(play, huawei));

        List<Map<String, String>> sent = attributionRequests();
        Assert.assertEquals(1, sent.size());
        assertHuaweiSignal(new JSONObject(sent.get(0).get("attribution")));
        Assert.assertEquals(1, huawei.reads);
    }

    /**
     * Both stores answer on a first launch: one request, the Play referrer first
     */
    @Test
    public void huawei_withPlayInOneRequest() throws Exception {
        new Countly().init(config(play(), huawei()));

        List<Map<String, String>> sent = attributionRequests();
        Assert.assertEquals(1, sent.size());
        JSONArray list = new JSONArray(sent.get(0).get("attribution"));
        Assert.assertEquals(2, list.length());
        Assert.assertEquals("{\"type\":\"referrer\",\"value\":\"" + PLAY_RAW + "\"}", list.getJSONObject(0).toString());
        assertHuaweiSignal(list.getJSONObject(1));
    }

    /**
     * Meta, HUAWEI and Play together: three signals in one list, Play first
     */
    @Test
    public void huawei_withMetaAndPlay() throws Exception {
        CountlyConfig cc = config(play(), huawei());
        cc.metaReferrerReader = new ModuleAttributionMetaReferrerTests.FakeMetaReader();
        cc.setFacebookAppId(ModuleAttributionMetaReferrerTests.FB_APP_ID);
        new Countly().init(cc);

        List<Map<String, String>> sent = attributionRequests();
        Assert.assertEquals(1, sent.size());
        JSONArray list = new JSONArray(sent.get(0).get("attribution"));
        Assert.assertEquals(3, list.length());
        Assert.assertEquals(PLAY_RAW, list.getJSONObject(0).getString("value"));
        Assert.assertEquals("meta_referrer", list.getJSONObject(1).getString("type"));
        assertHuaweiSignal(list.getJSONObject(2));
    }

    /**
     * Times HUAWEI did not give are left out; an empty referrer sends nothing and is not asked again
     */
    @Test
    public void huawei_missingTimes_andEmpty() throws Exception {
        FakeStoreReader play = play();
        play.available = false;
        FakeStoreReader huawei = new FakeStoreReader(new InstallReferrerReader.Details(HUAWEI_RAW));
        new Countly().init(config(play, huawei));
        JSONObject signal = new JSONObject(attributionRequests().get(0).get("attribution"));
        Assert.assertEquals("{\"type\":\"referrer\",\"store\":\"huawei\",\"value\":\"" + HUAWEI_RAW + "\"}", signal.toString());

        new CountlyStore(TestUtils.getContext(), mock(ModuleLog.class)).clear();
        FakeStoreReader empty = new FakeStoreReader(new InstallReferrerReader.Details(""));
        new Countly().init(config(play, empty));
        new Countly().init(config(play, empty));
        Assert.assertEquals(0, attributionRequests().size());
        Assert.assertEquals(1, empty.reads);
    }

    /**
     * Without the HUAWEI library nothing is asked and nothing is marked, so an app update that adds it still reads
     * it once; on a device without HMS Core (not supported) it is never asked again
     */
    @Test
    public void huawei_libraryAbsent_thenNotSupported() throws Exception {
        FakeStoreReader play = play();
        play.available = false;
        FakeStoreReader huawei = huawei();
        huawei.available = false;
        new Countly().init(config(play, huawei));
        Assert.assertEquals(0, huawei.reads);

        huawei.available = true;
        huawei.status = InstallReferrerReader.STATUS_NOT_SUPPORTED;
        new Countly().init(config(play, huawei));
        new Countly().init(config(play, huawei));
        Assert.assertEquals(1, huawei.reads);
        Assert.assertEquals(0, attributionRequests().size());
    }

    /**
     * HMS Core cannot answer now: asked again on later launches, 3 at most
     */
    @Test
    public void huawei_retryThenGiveUp() throws Exception {
        FakeStoreReader play = play();
        play.available = false;
        FakeStoreReader huawei = huawei();
        huawei.status = InstallReferrerReader.STATUS_RETRY;
        for (int i = 0; i < 5; i++) {
            new Countly().init(config(play, huawei));
        }
        Assert.assertEquals(ModuleAttribution.MAX_REFERRER_ATTEMPTS, huawei.reads);
        Assert.assertEquals(0, attributionRequests().size());
    }

    /**
     * Without the attribution consent HUAWEI is not asked; when it is given, it is read and sent
     */
    @Test
    public void huawei_consentGated() throws Exception {
        FakeStoreReader play = play();
        play.available = false;
        FakeStoreReader huawei = huawei();
        CountlyConfig cc = config(play, huawei);
        cc.setRequiresConsent(true).setConsentEnabled(ModuleAttributionMetaReferrerTests.ALL_BUT_ATTRIBUTION);
        Countly countly = new Countly().init(cc);
        Assert.assertEquals(0, huawei.reads);

        countly.consent().giveConsent(new String[] { Countly.CountlyFeatureNames.attribution });
        Assert.assertEquals(1, huawei.reads);
        Assert.assertEquals(1, attributionRequests().size());
    }

    /**
     * The real reader in this test APK: the HUAWEI library is not in it, and the emulator has no HMS Core,
     * so it reports unavailable and, asked anyway, answers not supported without binding anything
     */
    @Test
    public void huawei_realReader_noLibraryNoHms() {
        HuaweiInstallReferrerReader reader = new HuaweiInstallReferrerReader();
        Assert.assertFalse(reader.isAvailable());
        Assert.assertFalse(HuaweiInstallReferrerReader.hmsServicePresent(TestUtils.getContext()));

        AtomicInteger status = new AtomicInteger(-1);
        reader.read(TestUtils.getContext(), new InstallReferrerReader.Callback() {
            @Override public void onResult(int s, InstallReferrerReader.Details details) {
                status.set(s);
                Assert.assertNull(details);
            }
        });
        Assert.assertEquals(InstallReferrerReader.STATUS_NOT_SUPPORTED, status.get());
    }

    // ------------------------------------------------------------------ consent with the signal

    /**
     * When the app requires consent, every attribution signal carries consent={"attribution":true} in the same
     * request, which the server reads before the profile (consent.js)
     */
    @Test
    public void consent_sentWithSignals() throws Exception {
        FakeStoreReader play = play();
        play.available = false;
        CountlyConfig cc = config(play, huawei());
        cc.setRequiresConsent(true).setConsentEnabled(new String[] { Countly.CountlyFeatureNames.attribution });
        cc.setAttributionChannel("partner_x");
        new Countly().init(cc);

        List<Map<String, String>> sent = attributionRequests();
        Assert.assertEquals(2, sent.size());
        for (Map<String, String> request : sent) {
            Assert.assertEquals("{\"attribution\":true}", request.get("consent"));
        }
    }

    /**
     * When the app does not require consent, signals carry no consent: the SDK does not claim one the user never gave
     */
    @Test
    public void consent_notSentWhenNotRequired() throws Exception {
        FakeStoreReader play = play();
        play.available = false;
        new Countly().init(config(play, huawei()).setAttributionChannel("partner_x"));

        List<Map<String, String>> sent = attributionRequests();
        Assert.assertEquals(2, sent.size());
        for (Map<String, String> request : sent) {
            Assert.assertFalse(request.containsKey("consent"));
        }
    }
}
