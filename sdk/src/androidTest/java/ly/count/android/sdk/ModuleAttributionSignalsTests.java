package ly.count.android.sdk;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The raw attribution signals: install referrer, deep links and build channel, sent as
 * attribution={"type":..,"value":..} on /i, and the deep link callback.
 */
@RunWith(AndroidJUnit4.class)
public class ModuleAttributionSignalsTests {
    static final String RAW_REFERRER = "utm_source=google&utm_medium=cpc&utm_campaign=spring&cly_clk=c1a2b3&dl=%2Fproduct%2F42";
    static final String APP_ID = "5f2a0c3b9d1e4a6b7c8d9e0f";

    CountlyStore countlyStore;
    List<String> callbacks;

    /**
     * Stands in for the Play Install Referrer library.
     */
    static class FakeReader implements InstallReferrerReader {
        boolean available = true;
        int status = STATUS_OK;
        String referrer = RAW_REFERRER;
        int reads = 0;

        @Override public boolean isAvailable() {
            return available;
        }

        @Override public void read(@NonNull Context context, @NonNull Callback callback) {
            reads++;
            callback.onResult(status, referrer);
        }
    }

    @Before
    public void setUp() {
        countlyStore = new CountlyStore(TestUtils.getContext(), mock(ModuleLog.class));
        countlyStore.clear();
        callbacks = new ArrayList<>();
    }

    CountlyConfig config(RequestQueueProvider rqp, InstallReferrerReader reader) {
        CountlyConfig cc = TestUtils.createAttributionCountlyConfig(false, null, null, rqp, null, null, null);
        cc.installReferrerReader = reader;
        cc.setDeepLinkCallback((type, path, url) -> callbacks.add(type + "|" + path + "|" + url));
        return cc;
    }

    CountlyConfig consentConfig(RequestQueueProvider rqp, InstallReferrerReader reader, String[] consent) {
        CountlyConfig cc = config(rqp, reader);
        cc.setRequiresConsent(true).setConsentEnabled(consent);
        return cc;
    }

    /**
     * The attribution params of every request in the real queue, as parsed JSON.
     */
    List<JSONObject> queuedSignals() throws Exception {
        List<JSONObject> signals = new ArrayList<>();
        for (Map<String, String> request : TestUtils.getCurrentRQ()) {
            if (request != null && request.containsKey("attribution")) {
                signals.add(new JSONObject(request.get("attribution")));
            }
        }
        return signals;
    }

    Activity activityWith(Intent intent) {
        Activity activity = mock(Activity.class);
        when(activity.getIntent()).thenReturn(intent);
        return activity;
    }

    /**
     * The launcher activity resumes without a link, as when the user taps the app icon.
     */
    Countly launch(Countly countly) {
        countly.dispatchActivityResumed(activityWith(new Intent(Intent.ACTION_MAIN)));
        return countly;
    }

    // ------------------------------------------------------------------ install referrer

    /**
     * On first launch the raw referrer is sent once, as {"type":"referrer","value":<raw>} in the real request
     * queue; a second launch does not read or send it again.
     */
    @Test
    public void referrer_sentOnceWithRawValue() throws Exception {
        FakeReader reader = new FakeReader();
        new Countly().init(config(null, reader));

        List<JSONObject> signals = queuedSignals();
        Assert.assertEquals(1, signals.size());
        Assert.assertEquals("referrer", signals.get(0).getString("type"));
        Assert.assertEquals(RAW_REFERRER, signals.get(0).getString("value"));
        Assert.assertEquals(2, signals.get(0).length());
        Assert.assertEquals(1, reader.reads);

        //second launch, same storage
        new Countly().init(config(null, reader));
        Assert.assertEquals(1, reader.reads);
        Assert.assertEquals(1, queuedSignals().size());
    }

    /**
     * The referrer's dl is reported once as a deferred deep link on first launch, never on later launches.
     */
    @Test
    public void referrer_deferredDeepLinkCallback() {
        RequestQueueProvider rqp = mock(RequestQueueProvider.class);
        FakeReader reader = new FakeReader();
        launch(new Countly().init(config(rqp, reader)));

        verify(rqp, times(1)).sendAttributionSignal("referrer", RAW_REFERRER);
        Assert.assertEquals(1, callbacks.size());
        Assert.assertEquals("DEFERRED|/product/42|" + RAW_REFERRER, callbacks.get(0));

        launch(new Countly().init(config(rqp, reader)));
        Assert.assertEquals(1, callbacks.size());
    }

    /**
     * A plain UTM referrer (Google Ads, partners) is sent; with no dl, the first launch reports NONE.
     */
    @Test
    public void referrer_utmOnly_noneCallback() {
        RequestQueueProvider rqp = mock(RequestQueueProvider.class);
        FakeReader reader = new FakeReader();
        reader.referrer = "utm_source=google-play&utm_medium=organic";
        launch(new Countly().init(config(rqp, reader)));

        verify(rqp, times(1)).sendAttributionSignal("referrer", "utm_source=google-play&utm_medium=organic");
        Assert.assertEquals(1, callbacks.size());
        Assert.assertEquals("NONE|null|null", callbacks.get(0));
    }

    /**
     * Without the installreferrer library in the app (the case in this test APK: the SDK only compiles
     * against it), the default reader reports it unavailable, init runs, nothing is sent and the first
     * launch reports NONE.
     */
    @Test
    public void referrer_libraryAbsent() {
        Assert.assertFalse(new PlayInstallReferrerReader().isAvailable());

        RequestQueueProvider rqp = mock(RequestQueueProvider.class);
        Countly countly = launch(new Countly().init(config(rqp, null)));

        Assert.assertTrue(countly.moduleAttribution.installReferrerReader instanceof PlayInstallReferrerReader);
        verify(rqp, never()).sendAttributionSignal(anyString(), anyString());
        Assert.assertEquals(1, callbacks.size());
        Assert.assertEquals("NONE|null|null", callbacks.get(0));

        //later launches stay quiet
        launch(new Countly().init(config(rqp, null)));
        Assert.assertEquals(1, callbacks.size());
    }

    /**
     * A store that can never answer is not asked again.
     */
    @Test
    public void referrer_notSupported() {
        RequestQueueProvider rqp = mock(RequestQueueProvider.class);
        FakeReader reader = new FakeReader();
        reader.status = InstallReferrerReader.STATUS_NOT_SUPPORTED;
        launch(new Countly().init(config(rqp, reader)));
        launch(new Countly().init(config(rqp, reader)));

        Assert.assertEquals(1, reader.reads);
        verify(rqp, never()).sendAttributionSignal(anyString(), anyString());
        Assert.assertEquals(1, callbacks.size());
        Assert.assertEquals("NONE|null|null", callbacks.get(0));
    }

    /**
     * A store that keeps saying "try later" is asked on 3 launches, then no more; the first launch is
     * resolved (NONE) when it gives up.
     */
    @Test
    public void referrer_retryThenGiveUp() {
        RequestQueueProvider rqp = mock(RequestQueueProvider.class);
        FakeReader reader = new FakeReader();
        reader.status = InstallReferrerReader.STATUS_RETRY;

        launch(new Countly().init(config(rqp, reader)));
        launch(new Countly().init(config(rqp, reader)));
        Assert.assertEquals(2, reader.reads);
        Assert.assertEquals(0, callbacks.size());

        launch(new Countly().init(config(rqp, reader)));
        Assert.assertEquals(3, reader.reads);
        Assert.assertEquals(1, callbacks.size());
        Assert.assertEquals("NONE|null|null", callbacks.get(0));

        launch(new Countly().init(config(rqp, reader)));
        Assert.assertEquals(3, reader.reads);
        verify(rqp, never()).sendAttributionSignal(anyString(), anyString());
    }

    /**
     * A retry that later succeeds sends the referrer.
     */
    @Test
    public void referrer_retryThenSuccess() {
        RequestQueueProvider rqp = mock(RequestQueueProvider.class);
        FakeReader reader = new FakeReader();
        reader.status = InstallReferrerReader.STATUS_RETRY;
        new Countly().init(config(rqp, reader));

        reader.status = InstallReferrerReader.STATUS_OK;
        launch(new Countly().init(config(rqp, reader)));
        verify(rqp, times(1)).sendAttributionSignal("referrer", RAW_REFERRER);
        Assert.assertEquals("DEFERRED|/product/42|" + RAW_REFERRER, callbacks.get(0));
    }

    // ------------------------------------------------------------------ deep links

    /**
     * recordDeepLink sends the full URL as a deeplink signal (real queue) and reports OPENED with the path
     * after the campaign code.
     */
    @Test
    public void deepLink_recorded_realQueue() throws Exception {
        FakeReader reader = new FakeReader();
        reader.referrer = "";
        Countly countly = new Countly().init(config(null, reader));
        callbacks.clear();

        String url = "https://links.example.com/at/" + APP_ID + "/spring24/product/42?utm_source=newsletter";
        countly.attribution().recordDeepLink(Uri.parse(url));

        List<JSONObject> signals = queuedSignals();
        Assert.assertEquals(1, signals.size());
        Assert.assertEquals("deeplink", signals.get(0).getString("type"));
        Assert.assertEquals(url, signals.get(0).getString("value"));
        Assert.assertEquals(1, callbacks.size());
        Assert.assertEquals("OPENED|/product/42|" + url, callbacks.get(0));
    }

    /**
     * An opened link with a dl parameter routes to dl, also over the path after the campaign code; the full
     * URL is sent unchanged.
     */
    @Test
    public void deepLink_openedWithDl() {
        RequestQueueProvider rqp = mock(RequestQueueProvider.class);
        FakeReader reader = new FakeReader();
        reader.referrer = "";
        Countly countly = new Countly().init(config(rqp, reader));
        callbacks.clear();

        String url = "https://links.example.com/at/spring24/ignored?dl=%2Fpromo%2Fspring&utm_source=ig";
        countly.attribution().recordDeepLink(Uri.parse(url));

        verify(rqp, times(1)).sendAttributionSignal("deeplink", url);
        Assert.assertEquals(1, callbacks.size());
        Assert.assertEquals("OPENED|/promo/spring|" + url, callbacks.get(0));
    }

    /**
     * A link in the launch intent goes into the real request queue as the full URL.
     */
    @Test
    public void deepLink_launchIntent_realQueue() throws Exception {
        FakeReader reader = new FakeReader();
        reader.referrer = "";
        Countly countly = new Countly().init(config(null, reader));

        String url = "https://links.example.com/at/" + APP_ID + "/spring24/product/7?cly_clk=k1&utm_campaign=spring";
        countly.dispatchActivityResumed(activityWith(new Intent(Intent.ACTION_VIEW, Uri.parse(url))));

        List<JSONObject> signals = queuedSignals();
        Assert.assertEquals(1, signals.size());
        Assert.assertEquals("deeplink", signals.get(0).getString("type"));
        Assert.assertEquals(url, signals.get(0).getString("value"));
        Assert.assertTrue(callbacks.contains("OPENED|/product/7|" + url));
    }

    /**
     * recordDeepLink sends whatever link the app passes, also one that is not an attribution link.
     */
    @Test
    public void deepLink_manualSendsAnyLink() {
        RequestQueueProvider rqp = mock(RequestQueueProvider.class);
        Countly countly = new Countly().init(config(rqp, new FakeReader()));

        countly.attribution().recordDeepLink(Uri.parse("myapp://settings/profile"));
        verify(rqp, times(1)).sendAttributionSignal("deeplink", "myapp://settings/profile");
        Assert.assertTrue(callbacks.contains("OPENED|/profile|myapp://settings/profile"));

        countly.attribution().recordDeepLink(null);
        verify(rqp, times(1)).sendAttributionSignal(eq("deeplink"), anyString());
    }

    /**
     * The launch intent of an activity opened by a smart link is captured once: resuming the same
     * activity (or a recreated one with the same intent) does not record it again. On first launch it
     * replaces the DEFERRED / NONE report.
     */
    @Test
    public void deepLink_launchIntentCapturedOnce() {
        RequestQueueProvider rqp = mock(RequestQueueProvider.class);
        FakeReader reader = new FakeReader();
        String url = "https://links.example.com/at/spring24/checkout?cly_clk=zz9";
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));

        CountlyConfig cc = config(rqp, reader);
        cc.setInitialActivity(activityWith(intent));
        Countly countly = new Countly().init(cc);

        verify(rqp, times(1)).sendAttributionSignal("deeplink", url);
        //the referrer is still sent, but the link that opened the app wins the callback
        verify(rqp, times(1)).sendAttributionSignal("referrer", RAW_REFERRER);
        Assert.assertEquals(1, callbacks.size());
        Assert.assertEquals("OPENED|/checkout|" + url, callbacks.get(0));

        countly.dispatchActivityResumed(activityWith(intent));
        countly.dispatchActivityResumed(activityWith(intent));
        verify(rqp, times(1)).sendAttributionSignal("deeplink", url);
        Assert.assertEquals(1, callbacks.size());
    }

    /**
     * An app's own link opened from the launch intent fires the callback but is not sent: it could carry a
     * one time token. Links with utm_* or cly_clk are sent. Non-link intents are ignored.
     */
    @Test
    public void deepLink_launchIntentFiltering() {
        RequestQueueProvider rqp = mock(RequestQueueProvider.class);
        FakeReader reader = new FakeReader();
        reader.referrer = "";
        Countly countly = new Countly().init(config(rqp, reader));
        callbacks.clear();

        String own = "https://app.example.com/reset-password?token=secret";
        countly.dispatchActivityResumed(activityWith(new Intent(Intent.ACTION_VIEW, Uri.parse(own))));
        verify(rqp, never()).sendAttributionSignal(eq("deeplink"), anyString());
        Assert.assertEquals("OPENED|/reset-password|" + own, callbacks.get(0));

        String utm = "https://app.example.com/sale?utm_source=mail&utm_campaign=bf";
        countly.dispatchActivityResumed(activityWith(new Intent(Intent.ACTION_VIEW, Uri.parse(utm))));
        verify(rqp, times(1)).sendAttributionSignal("deeplink", utm);

        countly.dispatchActivityResumed(activityWith(new Intent(Intent.ACTION_VIEW, Uri.parse("content://media/external/images/1"))));
        countly.dispatchActivityResumed(activityWith(new Intent(Intent.ACTION_MAIN)));
        countly.dispatchActivityResumed(activityWith(null));
        Assert.assertEquals(2, callbacks.size());
        verify(rqp, times(1)).sendAttributionSignal(eq("deeplink"), anyString());
    }

    /**
     * Where a link routes to.
     */
    @Test
    public void deepLink_paths() {
        Assert.assertEquals("/product/42", ModuleAttribution.deepLinkPath(Uri.parse("https://h/at/" + APP_ID + "/code1/product/42")));
        Assert.assertEquals("/product/42", ModuleAttribution.deepLinkPath(Uri.parse("https://h/at/code1/product/42")));
        Assert.assertNull(ModuleAttribution.deepLinkPath(Uri.parse("https://h/at/" + APP_ID + "/code1")));
        Assert.assertNull(ModuleAttribution.deepLinkPath(Uri.parse("https://h/at/code1")));
        Assert.assertEquals("/promo", ModuleAttribution.deepLinkPath(Uri.parse("https://h/at/code1/product?dl=%2Fpromo")));
        Assert.assertEquals("/shop/item", ModuleAttribution.deepLinkPath(Uri.parse("https://h/shop/item?x=1")));
        Assert.assertNull(ModuleAttribution.deepLinkPath(Uri.parse("mailto:a@b.c")));

        Assert.assertEquals("/product/42", ModuleAttribution.deepLinkFromReferrer(RAW_REFERRER));
        Assert.assertNull(ModuleAttribution.deepLinkFromReferrer("utm_source=x"));
        Assert.assertNull(ModuleAttribution.deepLinkFromReferrer(""));

        Assert.assertTrue(ModuleAttribution.isAttributionLink(Uri.parse("https://h/at/code1")));
        Assert.assertTrue(ModuleAttribution.isAttributionLink(Uri.parse("https://h/x?countly_cid=1")));
        Assert.assertFalse(ModuleAttribution.isAttributionLink(Uri.parse("https://h/attic/x")));
        Assert.assertFalse(ModuleAttribution.isAttributionLink(Uri.parse("https://h/x?dl=%2Fy")));
    }

    // ------------------------------------------------------------------ channel

    /**
     * The build channel is sent once (real queue), and again only when it changes.
     */
    @Test
    public void channel_sentOnce() throws Exception {
        FakeReader reader = new FakeReader();
        reader.referrer = "";

        new Countly().init(config(null, reader).setAttributionChannel("huawei_appgallery"));
        new Countly().init(config(null, reader).setAttributionChannel("huawei_appgallery"));

        List<JSONObject> signals = queuedSignals();
        Assert.assertEquals(1, signals.size());
        Assert.assertEquals("channel", signals.get(0).getString("type"));
        Assert.assertEquals("huawei_appgallery", signals.get(0).getString("value"));

        new Countly().init(config(null, reader).setAttributionChannel("xiaomi"));
        signals = queuedSignals();
        Assert.assertEquals(2, signals.size());
        Assert.assertEquals("xiaomi", signals.get(1).getString("value"));

        //no channel: nothing
        new Countly().init(config(null, reader));
        Assert.assertEquals(2, queuedSignals().size());
    }

    // ------------------------------------------------------------------ first launch waits for the launch intent

    /**
     * Init in Application.onCreate, before any activity, without the referrer library: the first launch is not
     * reported until the launch activity was looked at, so a launch link gives exactly one OPENED and no NONE.
     */
    @Test
    public void firstLaunch_waitsForLaunchIntent_opened() {
        RequestQueueProvider rqp = mock(RequestQueueProvider.class);
        Countly countly = new Countly().init(config(rqp, null));
        Assert.assertEquals(0, callbacks.size());

        String url = "https://links.example.com/at/spring24/checkout?cly_clk=zz9";
        countly.dispatchActivityResumed(activityWith(new Intent(Intent.ACTION_VIEW, Uri.parse(url))));
        Assert.assertEquals(1, callbacks.size());
        Assert.assertEquals("OPENED|/checkout|" + url, callbacks.get(0));

        launch(countly);
        Assert.assertEquals(1, callbacks.size());
    }

    /**
     * A referrer that answers before any activity: DEFERRED is reported when the launch activity resumes
     * without a link, and only then.
     */
    @Test
    public void firstLaunch_waitsForLaunchIntent_deferred() {
        RequestQueueProvider rqp = mock(RequestQueueProvider.class);
        Countly countly = new Countly().init(config(rqp, new FakeReader()));
        verify(rqp, times(1)).sendAttributionSignal("referrer", RAW_REFERRER);
        Assert.assertEquals(0, callbacks.size());

        launch(countly);
        Assert.assertEquals(1, callbacks.size());
        Assert.assertEquals("DEFERRED|/product/42|" + RAW_REFERRER, callbacks.get(0));
    }

    /**
     * A referrer that answers before any activity, and the app was opened by a link: only OPENED.
     */
    @Test
    public void firstLaunch_deferredThenLaunchLink_onlyOpened() {
        RequestQueueProvider rqp = mock(RequestQueueProvider.class);
        Countly countly = new Countly().init(config(rqp, new FakeReader()));

        String url = "https://links.example.com/at/spring24/sale";
        countly.dispatchActivityResumed(activityWith(new Intent(Intent.ACTION_VIEW, Uri.parse(url))));
        Assert.assertEquals(1, callbacks.size());
        Assert.assertEquals("OPENED|/sale|" + url, callbacks.get(0));
    }

    /**
     * A first process with no activity (started for a push, say) keeps the first launch pending; the next
     * process reports it once its launch activity resumes.
     */
    @Test
    public void firstLaunch_pendingAcrossProcesses() {
        RequestQueueProvider rqp = mock(RequestQueueProvider.class);
        FakeReader reader = new FakeReader();
        new Countly().init(config(rqp, reader));
        Assert.assertEquals(0, callbacks.size());

        Countly second = new Countly().init(config(rqp, reader));
        Assert.assertEquals(1, reader.reads);
        Assert.assertEquals(0, callbacks.size());
        launch(second);
        Assert.assertEquals(1, callbacks.size());
        Assert.assertEquals("DEFERRED|/product/42|" + RAW_REFERRER, callbacks.get(0));

        launch(new Countly().init(config(rqp, reader)));
        Assert.assertEquals(1, callbacks.size());
    }

    // ------------------------------------------------------------------ explicit storage mode

    /**
     * In explicit storage mode the send-once markers are stored together with the queued signals: before the
     * write both are only in memory, so a process death loses both and the next launch sends them again;
     * after the write both are stored.
     */
    @Test
    public void explicitStorage_markersStoredWithSignals() throws Exception {
        FakeReader reader = new FakeReader();
        Countly first = new Countly().init(config(null, reader).setAttributionChannel("partner_x").enableExplicitStorageMode());
        Assert.assertEquals(1, reader.reads);

        //what a new process would find on disk now
        CountlyStore disk = new CountlyStore(TestUtils.getContext(), mock(ModuleLog.class));
        Assert.assertEquals("", disk.getAttributionState());
        Assert.assertEquals(0, queuedSignals().size());

        //the process dies before the write: the next launch reads and sends again
        Countly second = new Countly().init(config(null, reader).setAttributionChannel("partner_x").enableExplicitStorageMode());
        Assert.assertEquals(2, reader.reads);

        second.requestQueue().esWriteCachesToPersistence();
        disk = new CountlyStore(TestUtils.getContext(), mock(ModuleLog.class));
        JSONObject state = new JSONObject(disk.getAttributionState());
        Assert.assertTrue(state.getBoolean(ModuleAttribution.STATE_REFERRER_DONE));
        Assert.assertEquals("partner_x", state.getString(ModuleAttribution.STATE_CHANNEL));
        List<JSONObject> signals = queuedSignals();
        Assert.assertEquals(2, signals.size());

        //stored: a third launch sends nothing more
        new Countly().init(config(null, reader).setAttributionChannel("partner_x").enableExplicitStorageMode());
        Assert.assertEquals(2, reader.reads);
        Assert.assertEquals(2, queuedSignals().size());
    }

    // ------------------------------------------------------------------ consent

    /**
     * Without the attribution consent nothing is read or sent (deep links still reach the callback); giving
     * the consent later sends the referrer and the channel.
     */
    @Test
    public void consent_gatesEverything() {
        RequestQueueProvider rqp = mock(RequestQueueProvider.class);
        FakeReader reader = new FakeReader();
        CountlyConfig cc = consentConfig(rqp, reader, new String[] { Countly.CountlyFeatureNames.sessions });
        cc.setAttributionChannel("partner_x");
        Countly countly = new Countly().init(cc);

        Assert.assertEquals(0, reader.reads);
        countly.attribution().recordDeepLink(Uri.parse("https://h/at/code1/p"));
        verify(rqp, never()).sendAttributionSignal(anyString(), anyString());
        Assert.assertEquals(1, callbacks.size());
        Assert.assertEquals("OPENED|/p|https://h/at/code1/p", callbacks.get(0));

        countly.consent().giveConsent(new String[] { Countly.CountlyFeatureNames.attribution });
        Assert.assertEquals(1, reader.reads);
        verify(rqp, times(1)).sendAttributionSignal("referrer", RAW_REFERRER);
        verify(rqp, times(1)).sendAttributionSignal("channel", "partner_x");
        //the link that opened the app in this process wins over the deferred one
        Assert.assertEquals(1, callbacks.size());
    }

    /**
     * The request queue itself drops attribution signals without consent (real queue).
     */
    @Test
    public void consent_realQueueDropsSignal() throws Exception {
        FakeReader reader = new FakeReader();
        Countly countly = new Countly().init(consentConfig(null, reader, new String[] { Countly.CountlyFeatureNames.sessions }));
        countly.attribution().recordDeepLink(Uri.parse("https://h/at/code1"));
        Assert.assertEquals(0, queuedSignals().size());

        countly.consent().giveConsent(new String[] { Countly.CountlyFeatureNames.attribution });
        countly.attribution().recordDeepLink(Uri.parse("https://h/at/code1"));
        //the referrer and the deep link
        Assert.assertEquals(2, queuedSignals().size());
    }

    // ------------------------------------------------------------------ old calls

    /**
     * The existing calls produce exactly the requests they did before, and no attribution param.
     */
    @Test
    public void oldCalls_unchanged_realQueue() {
        FakeReader reader = new FakeReader();
        reader.available = false;
        Countly countly = new Countly().init(config(null, reader));

        countly.attribution().recordDirectAttribution("countly", "{\"cid\":\"abc\",\"cuid\":\"123\"}");
        Map<String, String> indirect = new java.util.HashMap<>();
        indirect.put(AttributionIndirectKey.AdvertisingID, "gaid-1");
        countly.attribution().recordIndirectAttribution(indirect);

        Map<String, String>[] rq = TestUtils.getCurrentRQ();
        Assert.assertEquals(2, rq.length);
        Assert.assertEquals("abc", rq[0].get("campaign_id"));
        Assert.assertEquals("123", rq[0].get("campaign_user"));
        Assert.assertFalse(rq[0].containsKey("attribution"));
        Assert.assertEquals("{\"adid\":\"gaid-1\"}", rq[1].get("aid"));
        Assert.assertFalse(rq[1].containsKey("attribution"));
    }
}
