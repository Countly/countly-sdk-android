package ly.count.android.sdk;

import android.app.Activity;
import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.mockito.Mockito.mock;

/**
 * "requestQueue().changeServerUrl" and "requestQueue().changeAppKey", checked on the requests they produce: the queued
 * requests in storage, the requests a local server receives, and the immediate requests.
 */
@RunWith(AndroidJUnit4.class)
public class ChangeServerUrlAndAppKeyTests {
    private static final String NEW_APP_KEY = "new_app_key";

    private final List<TestServer> servers = new ArrayList<>();
    private CapturingIRG irg;

    /** Starts each test with no stored data, the default instance stopped and a fresh immediate request capture. */
    @Before
    public void setUp() {
        TestUtils.getCountlyStore().clear();
        Countly.sharedInstance().halt();
        irg = new CapturingIRG();
    }

    /** Stops the default instance, lets held requests go, stops the local servers and clears the stored data. */
    @After
    public void tearDown() throws IOException {
        Countly.sharedInstance().halt();
        for (TestServer server : servers) {
            server.gate.countDown();
            server.server.shutdown();
        }
        TestUtils.getCountlyStore().clear();
    }

    /**
     * A request queued while the first server fails goes to the new server after the change, and so do the requests
     * recorded later and the behavior settings fetch. Trailing "/" characters are removed, so paths are not doubled.
     */
    @Test
    public void changeServerUrl_queuedAndLaterRequestsGoToTheNewServer() throws Exception {
        TestServer serverA = startServer(503);
        TestServer serverB = startServer(200);
        Countly countly = new Countly().init(config(serverA.url).enableManualSessionControl());

        countly.sessions().beginSession();
        Assert.assertTrue(serverA.waitFor("begin_session=1"));
        Assert.assertTrue(waitForProcessorToFinish(countly));
        Assert.assertEquals(1, TestUtils.getCurrentRQ().length);

        irg.requests.clear();
        countly.requestQueue().changeServerUrl(serverB.url + "///");
        Assert.assertEquals(serverB.url, countly.moduleRequestQueue.serverURL);

        countly.events().recordEvent("after_change");
        countly.requestQueue().attemptToSendStoredRequests();

        Assert.assertTrue(serverB.waitFor("begin_session=1"));
        Assert.assertTrue(serverB.waitFor("after_change"));
        Assert.assertTrue(waitForEmptyQueue());
        for (String request : serverB.received) {
            Assert.assertTrue(request, request.startsWith("/i?app_key=" + TestUtils.commonAppKey + "&"));
        }
        Assert.assertTrue(serverB.received.get(0).contains("begin_session=1"));
        Assert.assertEquals(1, irg.matching("method=sc", "@" + serverB.url).size());
        Assert.assertEquals(0, logCode(countly, "e102"));
    }

    /**
     * A processor that is already draining the queue sends the next request to the new server, and a request that is
     * on its way when the URL changes finishes at the old server, while its retry goes to the new one.
     */
    @Test
    public void changeServerUrl_runningDrainAndRetriesFollowTheChange() throws Exception {
        TestServer serverA = startServer(200);
        TestServer serverB = startServer(200);
        Countly countly = new Countly().init(config(serverA.url).enableManualSessionControl());

        serverA.close();
        countly.sessions().beginSession();
        countly.events().recordEvent("queued_behind");
        countly.moduleRequestQueue.sendEventsIfNeeded(true);
        Assert.assertTrue(serverA.waitFor("begin_session=1"));

        countly.requestQueue().changeServerUrl(serverB.url);
        serverA.gate.countDown();

        Assert.assertTrue(serverB.waitFor("queued_behind"));
        Assert.assertTrue(waitForEmptyQueue());
        Assert.assertEquals(1, serverA.received.size());
        Assert.assertEquals(1, serverB.received.size());

        TestServer serverC = startServer(503);
        serverC.close();
        countly.requestQueue().changeServerUrl(serverC.url);
        countly.events().recordEvent("in_flight");
        countly.requestQueue().attemptToSendStoredRequests();
        Assert.assertTrue(serverC.waitFor("in_flight"));

        countly.requestQueue().changeServerUrl(serverB.url);
        serverC.gate.countDown();
        Assert.assertTrue(waitForProcessorToFinish(countly));
        countly.requestQueue().attemptToSendStoredRequests();

        Assert.assertTrue(serverB.waitFor("in_flight"));
        Assert.assertTrue(waitForEmptyQueue());
        Assert.assertEquals(1, serverC.received.size());
    }

    /**
     * Null, empty, relative, non http(s) and host-less URLs are ignored with an e102 each and the URL stays. With
     * certificate pinning, an http URL is ignored too, while an https one is taken without its trailing "/".
     */
    @Test
    public void changeServerUrl_invalidUrlsAreIgnored() throws Exception {
        Countly countly = new Countly().init(config(TestUtils.commonURL));
        String[] invalidUrls = { null, "", "not a url", "ftp://x", "http://", "http:///", "/" };
        for (String invalidUrl : invalidUrls) {
            countly.requestQueue().changeServerUrl(invalidUrl);
            Assert.assertEquals(TestUtils.commonURL, countly.moduleRequestQueue.serverURL);
        }
        Assert.assertEquals(invalidUrls.length, logCode(countly, "e102"));
        Assert.assertEquals(1, irg.matching("method=sc").size());
        countly.halt();

        CountlyConfig pinnedConfig = config("https://test.count.ly")
            .enableCertificatePinning(new String[] { "pin" })
            .setCustomSSLSocketFactory((SSLSocketFactory) SSLSocketFactory.getDefault());
        Countly pinned = new Countly().init(pinnedConfig);

        pinned.requestQueue().changeServerUrl("http://other.count.ly");
        Assert.assertEquals("https://test.count.ly", pinned.moduleRequestQueue.serverURL);
        Assert.assertEquals(1, logCode(pinned, "e102"));

        pinned.requestQueue().changeServerUrl("https://other.count.ly/");
        Assert.assertEquals("https://other.count.ly", pinned.moduleRequestQueue.serverURL);
        Assert.assertEquals(1, logCode(pinned, "e102"));
    }

    /**
     * After the change, the catch up for the new server sends the consent state, fetches the behavior settings and
     * downloads the remote config from it. Stored remote config values stay when that download fails.
     */
    @Test
    public void changeServerUrl_catchesTheNewServerUp() throws Exception {
        irg.rcAnswer = new JSONObject().put("stored_key", "stored_value");
        CountlyConfig config = config(TestUtils.commonURL).setRequiresConsent(true)
            .setConsentEnabled(new String[] { Countly.CountlyFeatureNames.sessions, Countly.CountlyFeatureNames.remoteConfig })
            .enableRemoteConfigAutomaticTriggers();
        Countly countly = new Countly().init(config);
        Assert.assertEquals("stored_value", countly.remoteConfig().getValue("stored_key").value);

        irg.requests.clear();
        irg.rcAnswer = null;
        countly.requestQueue().changeServerUrl("https://new.count.ly");

        Assert.assertEquals(1, irg.matching("method=sc", "@https://new.count.ly").size());
        Assert.assertEquals(1, irg.matching("method=rc", "@https://new.count.ly").size());
        Assert.assertEquals(2, irg.requests.size());
        Assert.assertEquals("stored_value", countly.remoteConfig().getValue("stored_key").value);

        Map<String, String>[] rq = TestUtils.getCurrentRQ();
        Map<String, String> consentRequest = rq[rq.length - 1];
        Assert.assertEquals(TestUtils.commonAppKey, consentRequest.get("app_key"));
        JSONObject consent = new JSONObject(consentRequest.get("consent"));
        Assert.assertTrue(consent.getBoolean(Countly.CountlyFeatureNames.sessions));
        Assert.assertFalse(consent.getBoolean(Countly.CountlyFeatureNames.events));
    }

    /**
     * Push images follow the URL change: a resource on the new server's host goes through the instance's SSL socket
     * factory, one on the old host no longer does.
     */
    @Test
    public void changeServerUrl_pushImagesFollowTheNewHost() throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, null, null);
        SSLSocketFactory customFactory = context.getSocketFactory();
        Countly.sharedInstance().init(config("https://test.count.ly").setCustomSSLSocketFactory(customFactory));

        Countly.sharedInstance().requestQueue().changeServerUrl("https://images.count.ly");

        HttpsURLConnection newHost = (HttpsURLConnection) UtilsNetworking.openResourceConnection(new URL("https://images.count.ly/a.png"));
        HttpsURLConnection oldHost = (HttpsURLConnection) UtilsNetworking.openResourceConnection(new URL("https://test.count.ly/a.png"));
        Assert.assertSame(customFactory, newHost.getSSLSocketFactory());
        Assert.assertNotSame(customFactory, oldHost.getSSLSocketFactory());
    }

    /**
     * The view, the events and the user profile changes recorded before the change, and the end of the session, go
     * with the old key; the new session and the restarted view go with the new key. The device ID stays. Setting a
     * user property packs the events recorded before it, and recording the view end saves the pending property first.
     */
    @Test
    public void changeAppKey_closesTheOldAppAndBeginsTheNewOne() throws Exception {
        Countly countly = new Countly().init(config(TestUtils.commonURL).enableManualSessionControl());
        countly.sessions().beginSession();
        countly.views().startView("view_a");
        countly.events().recordEvent("before_change");
        countly.userProfile().setProperty("name", "Old App User");

        countly.requestQueue().changeAppKey(NEW_APP_KEY);
        Assert.assertEquals(NEW_APP_KEY, countly.moduleRequestQueue.appKey);

        countly.events().recordEvent("after_change");
        countly.moduleRequestQueue.sendEventsIfNeeded(true);

        Map<String, String>[] rq = TestUtils.getCurrentRQ();
        Assert.assertEquals(Arrays.toString(rq), 7, rq.length);
        assertRequest(rq[0], TestUtils.commonAppKey, "begin_session");
        assertEvents(rq[1], TestUtils.commonAppKey, "+view_a start", "before_change");
        assertRequest(rq[2], TestUtils.commonAppKey, "user_details");
        assertEvents(rq[3], TestUtils.commonAppKey, "-view_a");
        assertRequest(rq[4], TestUtils.commonAppKey, "end_session");
        assertRequest(rq[5], NEW_APP_KEY, "begin_session");
        assertEvents(rq[6], NEW_APP_KEY, "+view_a start", "after_change");
        Assert.assertTrue(countly.moduleSessions.sessionIsRunning());
        Assert.assertEquals(1, irg.matching("method=sc", "app_key=" + NEW_APP_KEY).size());
    }

    /**
     * With automatic sessions and views in the foreground, the activity's view and the session end under the old key
     * and start again under the new one.
     */
    @Test
    public void changeAppKey_automaticSessionAndViewStartAgainUnderTheNewKey() throws Exception {
        Countly countly = new Countly().init(config(TestUtils.commonURL).enableAutomaticViewTracking());
        Activity activity = mock(Activity.class);
        String viewName = activity.getClass().getName();
        countly.dispatchActivityStarted(activity);

        countly.requestQueue().changeAppKey(NEW_APP_KEY);
        countly.moduleRequestQueue.sendEventsIfNeeded(true);

        Map<String, String>[] rq = TestUtils.getCurrentRQ();
        Assert.assertEquals(Arrays.toString(rq), 5, rq.length);
        assertRequest(rq[0], TestUtils.commonAppKey, "begin_session");
        assertEvents(rq[1], TestUtils.commonAppKey, "+" + viewName + " start", "-" + viewName);
        assertRequest(rq[2], TestUtils.commonAppKey, "end_session");
        assertRequest(rq[3], NEW_APP_KEY, "begin_session");
        assertEvents(rq[4], NEW_APP_KEY, "+" + viewName + " start");
    }

    /**
     * In the background, a view stopped for the background is not ended a second time: its end goes with the old key,
     * and it starts again under the new key when the app comes back, together with the new session.
     */
    @Test
    public void changeAppKey_inTheBackgroundViewsStartAgainWithTheForeground() throws Exception {
        Countly countly = new Countly().init(config(TestUtils.commonURL));
        Activity activity = mock(Activity.class);
        countly.dispatchActivityStarted(activity);
        countly.views().startView("manual_view");
        countly.dispatchActivityStopped(activity);

        countly.requestQueue().changeAppKey(NEW_APP_KEY);
        countly.dispatchActivityStarted(activity);
        countly.moduleRequestQueue.sendEventsIfNeeded(true);

        Map<String, String>[] rq = TestUtils.getCurrentRQ();
        Assert.assertEquals(Arrays.toString(rq), 6, rq.length);
        assertRequest(rq[0], TestUtils.commonAppKey, "begin_session");
        assertEvents(rq[1], TestUtils.commonAppKey, "+manual_view start");
        assertRequest(rq[2], TestUtils.commonAppKey, "end_session");
        assertEvents(rq[3], TestUtils.commonAppKey, "-manual_view");
        assertRequest(rq[4], NEW_APP_KEY, "begin_session");
        assertEvents(rq[5], NEW_APP_KEY, "+manual_view start");
    }

    /**
     * Null, empty and placeholder keys are ignored with an e103 each; the current key is ignored without one. Neither
     * ends the session.
     */
    @Test
    public void changeAppKey_invalidPlaceholderAndSameKeysAreIgnored() {
        Countly countly = new Countly().init(config(TestUtils.commonURL).enableManualSessionControl());
        countly.sessions().beginSession();

        String[] invalidKeys = { null, "", "YOUR_APP_KEY" };
        for (String invalidKey : invalidKeys) {
            countly.requestQueue().changeAppKey(invalidKey);
            Assert.assertEquals(TestUtils.commonAppKey, countly.moduleRequestQueue.appKey);
        }
        Assert.assertEquals(invalidKeys.length, logCode(countly, "e103"));

        countly.requestQueue().changeAppKey(TestUtils.commonAppKey);
        Assert.assertEquals(invalidKeys.length, logCode(countly, "e103"));

        Assert.assertEquals(1, TestUtils.getCurrentRQ().length);
        Assert.assertTrue(countly.moduleSessions.sessionIsRunning());
        Assert.assertEquals(1, irg.matching("method=sc").size());
    }

    /**
     * Queued requests keep the key they were created with. "overwriteAppKeys" moves them to the current key and
     * "eraseWrongAppKeyRequests" drops the ones with another key.
     */
    @Test
    public void changeAppKey_queuedRequestsKeepTheirKeyUntilTheQueueHelpers() {
        Countly countly = new Countly().init(config(TestUtils.commonURL).enableManualSessionControl());
        addMarker(countly, "first");
        countly.requestQueue().changeAppKey(NEW_APP_KEY);
        addMarker(countly, "second");

        Map<String, String>[] rq = TestUtils.getCurrentRQ();
        Assert.assertEquals(2, rq.length);
        Assert.assertEquals(TestUtils.commonAppKey, rq[0].get("app_key"));
        Assert.assertEquals(NEW_APP_KEY, rq[1].get("app_key"));

        countly.requestQueue().overwriteAppKeys();
        rq = TestUtils.getCurrentRQ();
        Assert.assertEquals(2, rq.length);
        Assert.assertEquals(NEW_APP_KEY, rq[0].get("app_key"));
        Assert.assertEquals(NEW_APP_KEY, rq[1].get("app_key"));

        countly.requestQueue().changeAppKey("third_app_key");
        addMarker(countly, "third");
        countly.requestQueue().eraseWrongAppKeyRequests();
        rq = TestUtils.getCurrentRQ();
        Assert.assertEquals(1, rq.length);
        Assert.assertEquals("third_app_key", rq[0].get("app_key"));
        Assert.assertEquals("third", rq[0].get("marker"));
    }

    /**
     * After the change, the new app gets the consent state, its behavior settings are fetched, and the remote config
     * values of the old app are cleared before the new app's values are downloaded, all with the new key. The download
     * fails here, so only the clear can empty the store. With value caching, the old values are kept but marked as cached.
     */
    @Test
    public void changeAppKey_catchesTheNewAppUp() throws Exception {
        irg.rcAnswer = new JSONObject().put("old_key", "old_value");
        CountlyConfig config = config(TestUtils.commonURL).enableManualSessionControl().setRequiresConsent(true)
            .setConsentEnabled(new String[] { Countly.CountlyFeatureNames.sessions, Countly.CountlyFeatureNames.remoteConfig })
            .enableRemoteConfigAutomaticTriggers();
        Countly countly = new Countly().init(config);
        Assert.assertEquals("old_value", countly.remoteConfig().getValue("old_key").value);

        irg.requests.clear();
        irg.rcAnswer = null;
        countly.requestQueue().changeAppKey(NEW_APP_KEY);

        Assert.assertEquals(1, irg.matching("/o/sdk?", "method=sc", "app_key=" + NEW_APP_KEY).size());
        Assert.assertEquals(1, irg.matching("/o/sdk?", "method=rc", "app_key=" + NEW_APP_KEY).size());
        Assert.assertTrue(countly.remoteConfig().getValues().isEmpty());

        Map<String, String>[] rq = TestUtils.getCurrentRQ();
        Map<String, String> consentRequest = rq[rq.length - 1];
        Assert.assertEquals(NEW_APP_KEY, consentRequest.get("app_key"));
        Assert.assertTrue(new JSONObject(consentRequest.get("consent")).getBoolean(Countly.CountlyFeatureNames.remoteConfig));
        countly.halt();

        irg.rcAnswer = new JSONObject().put("old_key", "old_value");
        Countly caching = new Countly().init(config(TestUtils.commonURL).enableRemoteConfigValueCaching());
        caching.remoteConfig().downloadAllKeys(null);
        Assert.assertTrue(caching.remoteConfig().getValue("old_key").isCurrentUsersData);

        caching.requestQueue().changeAppKey(NEW_APP_KEY);
        RCData cachedValue = caching.remoteConfig().getValue("old_key");
        Assert.assertEquals("old_value", cachedValue.value);
        Assert.assertFalse(cachedValue.isCurrentUsersData);
    }

    /**
     * With view restart disabled for manual views, the view ends under the old key and does not start again.
     */
    @Test
    public void changeAppKey_withViewRestartDisabledViewsOnlyStop() throws Exception {
        Countly countly = new Countly().init(config(TestUtils.commonURL).enableManualSessionControl().disableViewRestartForManualRecording());
        countly.sessions().beginSession();
        countly.views().startView("manual_view");

        countly.requestQueue().changeAppKey(NEW_APP_KEY);
        countly.moduleRequestQueue.sendEventsIfNeeded(true);

        Map<String, String>[] rq = TestUtils.getCurrentRQ();
        Assert.assertEquals(Arrays.toString(rq), 4, rq.length);
        assertRequest(rq[0], TestUtils.commonAppKey, "begin_session");
        assertEvents(rq[1], TestUtils.commonAppKey, "+manual_view start", "-manual_view");
        assertRequest(rq[2], TestUtils.commonAppKey, "end_session");
        assertRequest(rq[3], NEW_APP_KEY, "begin_session");
        Assert.assertTrue(countly.moduleViews.viewDataMap.isEmpty());
    }

    /**
     * Behavior settings and remote config answers to requests made before a key or URL change arrive after it and are
     * not applied: the new app's values stay and tracking stays enabled.
     */
    @Test
    public void answersRequestedBeforeAChangeAreNotApplied() throws Exception {
        irg.defer = true;
        Countly countly = new Countly().init(config(TestUtils.commonURL).enableRemoteConfigAutomaticTriggers());
        irg.defer = false;
        irg.rcAnswer = new JSONObject().put("new_key", "new_value");
        countly.requestQueue().changeAppKey(NEW_APP_KEY);
        Assert.assertEquals("new_value", countly.remoteConfig().getValue("new_key").value);

        Assert.assertEquals(1, irg.answerDeferred("method=sc", new ServerConfigBuilder().tracking(false).buildJson()));
        Assert.assertEquals(1, irg.answerDeferred("method=rc", new JSONObject().put("old_key", "old_value")));
        Assert.assertTrue(countly.moduleConfiguration.getTrackingEnabled());
        Assert.assertEquals(1, countly.remoteConfig().getValues().size());
        Assert.assertNull(countly.remoteConfig().getValue("old_key").value);

        irg.defer = true;
        countly.remoteConfig().downloadAllKeys(null);
        irg.defer = false;
        irg.rcAnswer = new JSONObject().put("url_key", "url_value");
        countly.requestQueue().changeServerUrl("https://new.count.ly");
        Assert.assertEquals(1, irg.answerDeferred("method=rc", new JSONObject().put("old_key", "old_value")));
        Assert.assertEquals("url_value", countly.remoteConfig().getValue("url_key").value);
        Assert.assertNull(countly.remoteConfig().getValue("old_key").value);
    }

    /**
     * The push token this instance sent is sent again after a URL change, and after a key change under the new key.
     */
    @Test
    public void pushTokenIsSentAgainAfterAChange() throws Exception {
        long tokenDelay = ConnectionQueue.tokenRequestDelayMs;
        ConnectionQueue.tokenRequestDelayMs = 10;
        try {
            Countly countly = new Countly().init(config(TestUtils.commonURL));
            countly.onRegistrationId("push_token", Countly.CountlyMessagingProvider.FCM);
            Assert.assertTrue(waitUntil(() -> tokenRequests().size() >= 1));

            countly.requestQueue().changeServerUrl("https://new.count.ly");
            Assert.assertTrue(waitUntil(() -> tokenRequests().size() >= 2));
            countly.requestQueue().changeAppKey(NEW_APP_KEY);
            Assert.assertTrue(waitUntil(() -> tokenRequests().size() >= 3));

            List<Map<String, String>> tokenRequests = tokenRequests();
            Assert.assertEquals(TestUtils.commonAppKey, tokenRequests.get(0).get("app_key"));
            Assert.assertEquals(TestUtils.commonAppKey, tokenRequests.get(1).get("app_key"));
            Assert.assertEquals(NEW_APP_KEY, tokenRequests.get(2).get("app_key"));
            for (Map<String, String> tokenRequest : tokenRequests) {
                Assert.assertEquals("push_token", tokenRequest.get("android_token"));
            }
        } finally {
            ConnectionQueue.tokenRequestDelayMs = tokenDelay;
        }
    }

    /**
     * Before init there is no handle. A handle kept from before a halt is ignored with a w101, and the next init uses
     * the URL and key of the config again, as neither change is stored.
     */
    @Test
    public void notInitializedStaleHandleAndNextInit() {
        Countly countly = new Countly();
        Assert.assertNull(countly.requestQueue());

        countly.init(config(TestUtils.commonURL));
        ModuleRequestQueue.RequestQueue handle = countly.requestQueue();
        handle.changeServerUrl("https://new.count.ly");
        handle.changeAppKey(NEW_APP_KEY);
        Assert.assertEquals("https://new.count.ly", countly.moduleRequestQueue.serverURL);
        Assert.assertEquals(NEW_APP_KEY, countly.moduleRequestQueue.appKey);
        countly.halt();

        handle.changeAppKey("ignored_app_key");
        handle.changeServerUrl("https://ignored.count.ly");

        countly.init(config(TestUtils.commonURL));
        Assert.assertEquals(TestUtils.commonURL, countly.moduleRequestQueue.serverURL);
        Assert.assertEquals(TestUtils.commonAppKey, countly.moduleRequestQueue.appKey);

        int w101Before = logCode(countly, "w101");
        handle.changeAppKey("ignored_app_key");
        handle.changeServerUrl("https://ignored.count.ly");
        Assert.assertEquals(w101Before + 2, logCode(countly, "w101"));
        Assert.assertEquals(TestUtils.commonURL, countly.moduleRequestQueue.serverURL);
        Assert.assertEquals(TestUtils.commonAppKey, countly.moduleRequestQueue.appKey);
    }

    /**
     * Queue helpers called while a request is on its way: the request is not stopped, and nothing else that was queued
     * before is sent after "flushQueues", or after "eraseWrongAppKeyRequests" dropped it for its old key.
     */
    @Test
    public void queueHelpersWhileARequestIsOnItsWay() throws Exception {
        TestServer server = startServer(200);
        Countly countly = new Countly().init(config(server.url).enableManualSessionControl());

        server.close();
        countly.sessions().beginSession();
        Assert.assertTrue(server.waitFor("begin_session=1"));
        countly.events().recordEvent("flushed");
        countly.moduleRequestQueue.sendEventsIfNeeded(true);
        countly.requestQueue().flushQueues();
        server.gate.countDown();

        Assert.assertTrue(waitForProcessorToFinish(countly));
        countly.events().recordEvent("after_flush");
        countly.requestQueue().attemptToSendStoredRequests();
        Assert.assertTrue(server.waitFor("after_flush"));
        Assert.assertTrue(waitForEmptyQueue());

        server.close();
        addMarker(countly, "in_flight");
        countly.requestQueue().attemptToSendStoredRequests();
        Assert.assertTrue(server.waitFor("in_flight"));
        countly.requestQueue().changeAppKey(NEW_APP_KEY);
        countly.requestQueue().eraseWrongAppKeyRequests();
        server.gate.countDown();

        Assert.assertTrue(server.waitFor("app_key=" + NEW_APP_KEY + "&", "begin_session=1&"));
        Assert.assertTrue(waitForEmptyQueue());
        Assert.assertEquals(0, server.matching("flushed").size());
        Assert.assertEquals(0, server.matching("app_key=" + TestUtils.commonAppKey + "&", "end_session=1").size());
        Assert.assertEquals(1, server.matching("in_flight").size());
    }

    /** The base config for the given server, with orientation tracking off and the immediate requests captured. */
    private CountlyConfig config(@NonNull String serverUrl) {
        CountlyConfig config = TestUtils.createBaseConfig().setServerURL(serverUrl).setTrackOrientationChanges(false);
        config.immediateRequestGenerator = irg;
        return config;
    }

    /** Queues a direct request that carries the given marker. */
    private static void addMarker(@NonNull Countly countly, @NonNull String marker) {
        Map<String, String> request = new HashMap<>();
        request.put("marker", marker);
        countly.requestQueue().addDirectRequest(request);
    }

    /** Checks the app key and the device ID of a queued request, and that it carries the given parameter. */
    private static void assertRequest(@NonNull Map<String, String> request, @NonNull String appKey, @NonNull String param) {
        Assert.assertEquals(appKey, request.get("app_key"));
        Assert.assertEquals(TestUtils.commonDeviceId, request.get("device_id"));
        Assert.assertTrue("missing " + param + " in " + request, request.containsKey(param));
    }

    /** Checks an events request as "assertRequest" does, and its events as "eventSummary" lists them. */
    private static void assertEvents(@NonNull Map<String, String> request, @NonNull String appKey, @NonNull String... events) throws JSONException {
        assertRequest(request, appKey, "events");
        Assert.assertEquals(Arrays.asList(events), eventSummary(request));
    }

    /**
     * The events of a request in the order they were recorded: "+name" for a view start, with " start" for the first
     * view of a session, "-name" for a view end and the key for any other event.
     */
    private static List<String> eventSummary(@NonNull Map<String, String> request) throws JSONException {
        JSONArray events = new JSONArray(request.get("events"));
        List<String> summary = new ArrayList<>();
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.getJSONObject(i);
            if (!ModuleViews.VIEW_EVENT_KEY.equals(event.getString("key"))) {
                summary.add(event.getString("key"));
                continue;
            }
            JSONObject segmentation = event.getJSONObject("segmentation");
            if (segmentation.has("visit")) {
                summary.add("+" + segmentation.getString("name") + (segmentation.has("start") ? " start" : ""));
            } else {
                summary.add("-" + segmentation.getString("name"));
            }
        }
        return summary;
    }

    /** The queued push token requests, in queue order. */
    private static List<Map<String, String>> tokenRequests() {
        List<Map<String, String>> tokenRequests = new ArrayList<>();
        for (Map<String, String> request : TestUtils.getCurrentRQ()) {
            if (request != null && request.containsKey("token_session")) {
                tokenRequests.add(request);
            }
        }
        return tokenRequests;
    }

    /** How often the instance recorded the given health check log code. */
    private static int logCode(@NonNull Countly countly, @NonNull String code) {
        HealthCheckCounter counter = countly.moduleHealthCheck.hCounter;
        synchronized (counter) {
            Integer count = counter.logCodes.get(code);
            return count == null ? 0 : count;
        }
    }

    /** Waits up to 30 seconds for the condition to hold. */
    private static boolean waitUntil(@NonNull Callable<Boolean> condition) throws Exception {
        for (int i = 0; i < 300; i++) {
            if (condition.call()) {
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }

    /** Waits up to 30 seconds for the request queue to be empty. */
    private static boolean waitForEmptyQueue() throws Exception {
        return waitUntil(() -> TestUtils.getCountlyStore().getRequests().length == 0);
    }

    /** Waits up to 30 seconds for the running request queue processor to finish. */
    private static boolean waitForProcessorToFinish(@NonNull Countly countly) throws Exception {
        return waitUntil(() -> {
            Future<?> future = countly.connectionQueue_.getConnectionProcessorFuture();
            return future == null || future.isDone();
        });
    }

    /** Starts a server that answers with the given code, and stops it after the test. */
    private TestServer startServer(int responseCode) throws IOException {
        TestServer testServer = new TestServer(responseCode);
        servers.add(testServer);
        return testServer;
    }

    /**
     * A local server that records every request and answers with the given code. While its gate is closed, a
     * request waits in the server, so it stays on its way.
     */
    private static class TestServer {
        final MockWebServer server = new MockWebServer();
        final List<String> received = new CopyOnWriteArrayList<>();
        final String url;
        volatile CountDownLatch gate = new CountDownLatch(0);

        /** Starts the server, answering with the given code. */
        TestServer(final int responseCode) throws IOException {
            server.setDispatcher(new Dispatcher() {
                @NonNull @Override public MockResponse dispatch(@NonNull RecordedRequest request) throws InterruptedException {
                    received.add(request.getPath() + " " + request.getBody().readUtf8());
                    gate.await(30, TimeUnit.SECONDS);
                    return new MockResponse().setResponseCode(responseCode).setHeader("Content-Type", "application/json")
                        .setBody(responseCode == 200 ? "{\"result\":\"Success\"}" : "{}");
                }
            });
            server.start();
            String serverUrl = server.url("/").toString();
            url = serverUrl.substring(0, serverUrl.length() - 1);
        }

        /** Holds every later request in the server until the gate is opened. */
        void close() {
            gate = new CountDownLatch(1);
        }

        /** Waits up to 30 seconds for a request that contains every marker. */
        boolean waitFor(@NonNull String... markers) throws Exception {
            return waitUntil(() -> !matching(markers).isEmpty());
        }

        /** The received requests that contain every marker. */
        List<String> matching(@NonNull String... markers) {
            return CapturingIRG.filter(received, markers);
        }
    }

    /**
     * Records the immediate requests with their endpoint and the server URL they are sent to, and answers the remote
     * config download with "rcAnswer"; every other immediate request gets no answer. While "defer" is set, requests
     * are held back until "answerDeferred" answers them.
     */
    private static class CapturingIRG implements ImmediateRequestGenerator {
        final List<String> requests = new CopyOnWriteArrayList<>();
        final List<Object[]> deferred = new CopyOnWriteArrayList<>();
        volatile JSONObject rcAnswer = null;
        volatile boolean defer = false;

        @Override public ImmediateRequestI CreateImmediateRequestMaker() {
            return (requestData, customEndpoint, cp, requestShouldBeDelayed, networkingIsEnabled, callback, log) -> {
                requests.add(customEndpoint + "?" + requestData + " @" + cp.getServerURL());
                if (defer) {
                    deferred.add(new Object[] { requestData, callback });
                    return;
                }
                callback.callback(requestData.contains("method=rc") ? rcAnswer : null);
            };
        }

        /** Answers the held back requests that contain the marker, and returns how many there were. */
        int answerDeferred(@NonNull String marker, @NonNull JSONObject answer) {
            int answered = 0;
            for (Object[] request : deferred) {
                if (((String) request[0]).contains(marker)) {
                    deferred.remove(request);
                    ((ImmediateRequestMaker.InternalImmediateRequestCallback) request[1]).callback(answer);
                    answered++;
                }
            }
            return answered;
        }

        @Override public ImmediateRequestI CreatePreflightRequestMaker() {
            return null;
        }

        /** The immediate requests that contain every marker. */
        List<String> matching(@NonNull String... markers) {
            return filter(requests, markers);
        }

        /** The requests that contain every marker. */
        static List<String> filter(@NonNull List<String> requests, @NonNull String... markers) {
            List<String> matches = new ArrayList<>();
            for (String request : requests) {
                boolean all = true;
                for (String marker : markers) {
                    all &= request.contains(marker);
                }
                if (all) {
                    matches.add(request);
                }
            }
            return matches;
        }
    }
}
