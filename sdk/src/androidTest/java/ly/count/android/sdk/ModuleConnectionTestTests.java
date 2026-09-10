package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Connection test battery: the server arms one device through a bare {@code ct} flag on the live server
 * config response, the SDK probes every endpoint it depends on with parameterless GETs, grades them, and
 * queues a single {@code ct_results} report.
 * <p>
 * Probes hit a real {@link MockWebServer} so the transport (no redirect following, no custom headers,
 * timeouts, path prefix handling) is exercised for real. The {@code sc} response itself is injected through
 * the immediate request generator, as the rest of the server config suite does.
 */
@RunWith(AndroidJUnit4.class)
public class ModuleConnectionTestTests {
    private static final String PREFIX = "/countly";
    private static final String[] NAMED = { "ctA", "ctB" };

    private static final String[] EXPECTED_ORDER = { "core", "core-write", "sc", "rc", "ab", "feedback", "feedback-widget", "feedback-submit", "content", "feedback-page", "feedback-assets", "content-page" };

    private MockWebServer server;
    private Countly countly;

    @Before
    public void setUp() throws IOException {
        resetAll();
        server = new MockWebServer();
        server.setDispatcher(healthyDispatcher(new HashMap<>()));
        server.start();
    }

    @After
    public void tearDown() throws IOException {
        resetAll();
        server.shutdown();
    }

    private void resetAll() {
        Countly.sharedInstance().halt();
        TestUtils.getCountlyStore().clear();
        for (String name : NAMED) {
            Countly existing = Countly.getInstance(name);
            if (existing != null) {
                existing.halt();
            }
            new CountlyStore(TestUtils.getContext(), new ModuleLog(), false, CountlyStore.sanitizeNamespace(name)).clear();
        }
    }

    // ================ Fixtures ================

    /** The mock server's URL with a reverse-proxy style path prefix and no trailing slash. */
    private String prefixedUrl() {
        String url = server.url(PREFIX).toString();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private static String scResponse(@Nullable Object ct, @Nullable String innerConfig) {
        String inner = innerConfig == null ? "{\"tracking\":true}" : innerConfig;
        String ctPart = ct == null ? "" : ",\"ct\":" + ct;
        return "{\"v\":1,\"t\":1786273877636,\"c\":" + inner + ctPart + "}";
    }

    private CountlyConfig config(String serverUrl, String appKey, String deviceId, String scResponse) {
        CountlyConfig cc = new CountlyConfig(TestUtils.getApplication(), appKey, serverUrl)
            .setDeviceId(deviceId)
            .setLoggingEnabled(true);
        cc.immediateRequestGenerator = ModuleConfigurationTests.createIRGForSpecificResponse(scResponse);
        return cc;
    }

    /** Immediate request generator whose response can be changed after init; null means "no response". */
    private static ImmediateRequestGenerator switchableIRG(final String[] holder) {
        return new ImmediateRequestGenerator() {
            @Override public ImmediateRequestI CreateImmediateRequestMaker() {
                return new ImmediateRequestI() {
                    @Override public void doWork(String requestData, String customEndpoint, ConnectionProcessor cp, boolean requestShouldBeDelayed, boolean networkingIsEnabled, ImmediateRequestMaker.InternalImmediateRequestCallback callback, ModuleLog log) {
                        String response = holder[0];
                        if (response == null) {
                            callback.callback(null);
                            return;
                        }
                        try {
                            callback.callback(new JSONObject(response));
                        } catch (JSONException e) {
                            callback.callback(null);
                        }
                    }
                };
            }

            @Override public ImmediateRequestI CreatePreflightRequestMaker() {
                return null;
            }
        };
    }

    private static String pathOnly(RecordedRequest request) {
        String path = request.getPath();
        if (path == null) {
            return "";
        }
        int q = path.indexOf('?');
        return q < 0 ? path : path.substring(0, q);
    }

    private static boolean isSdkRequest(RecordedRequest request) {
        String path = request.getPath();
        String body = request.getBody().clone().readUtf8();
        return (path != null && path.contains("app_key=")) || body.contains("app_key=");
    }

    /**
     * Healthy server: ping and the asset/page routes answer 2xx, everything else rejects with the
     * "missing app_key" 400. SDK requests (the ones carrying app_key) are accepted so the report is delivered.
     * Per-path overrides let a test break individual endpoints.
     */
    private static Dispatcher healthyDispatcher(final Map<String, MockResponse> overrides) {
        return new Dispatcher() {
            @NonNull @Override public MockResponse dispatch(@NonNull RecordedRequest request) {
                if (isSdkRequest(request)) {
                    return json(200, "{\"result\":\"Success\"}");
                }
                String path = pathOnly(request);
                MockResponse override = overrides.get(path);
                if (override != null) {
                    return override;
                }
                if (path.equals(PREFIX + "/o/ping")) {
                    return json(200, "{\"result\":\"Success\"}");
                }
                if (path.endsWith("/ct-probe.png")) {
                    return new MockResponse().setResponseCode(200).setHeader("Content-Type", "image/png").setBody("png");
                }
                if (path.startsWith(PREFIX + "/feedback/") || path.equals(PREFIX + "/_external/content/")) {
                    return new MockResponse().setResponseCode(200).setHeader("Content-Type", "text/html").setBody("<html></html>");
                }
                return json(400, "{\"result\":\"Missing parameter \\\"app_key\\\" or \\\"device_id\\\"\"}");
            }
        };
    }

    private static MockResponse json(int code, String body) {
        return new MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body);
    }

    /** Drains the recorded requests, returning the report request(s) and the probes seen so far. */
    private static class Recorded {
        final List<RecordedRequest> probes = new ArrayList<>();
        final List<RecordedRequest> sdkRequests = new ArrayList<>();
        final List<JSONObject> reports = new ArrayList<>();
    }

    private Recorded awaitReports(int expectedReports, long timeoutMs) throws Exception {
        Recorded recorded = new Recorded();
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            RecordedRequest request = server.takeRequest(200, TimeUnit.MILLISECONDS);
            if (request == null) {
                if (recorded.reports.size() >= expectedReports) {
                    break;
                }
                continue;
            }
            if (isSdkRequestPeek(request)) {
                recorded.sdkRequests.add(request);
                JSONObject report = extractReport(request);
                if (report != null) {
                    recorded.reports.add(report);
                }
            } else {
                recorded.probes.add(request);
            }
        }
        return recorded;
    }

    private static boolean isSdkRequestPeek(RecordedRequest request) {
        String path = request.getPath();
        return (path != null && path.contains("app_key=")) || request.getBody().clone().readUtf8().contains("app_key=");
    }

    private static Map<String, String> sdkParams(RecordedRequest request) {
        String path = request.getPath();
        String query = "";
        if (path != null && path.contains("?")) {
            query = path.substring(path.indexOf('?') + 1);
        }
        String body = request.getBody().clone().readUtf8();
        String raw = body.contains("app_key=") ? body : query;
        Map<String, String> params = new HashMap<>();
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                params.put(UtilsNetworking.urlDecodeString(pair), "");
            } else {
                params.put(UtilsNetworking.urlDecodeString(pair.substring(0, eq)), UtilsNetworking.urlDecodeString(pair.substring(eq + 1)));
            }
        }
        return params;
    }

    private static @Nullable JSONObject extractReport(RecordedRequest request) throws JSONException {
        Map<String, String> params = sdkParams(request);
        if (!params.containsKey("ct_results")) {
            return null;
        }
        return new JSONObject(params.get("ct_results"));
    }

    private static Map<String, JSONObject> rowsByFeature(JSONObject report) throws JSONException {
        JSONArray results = report.getJSONArray("results");
        Map<String, JSONObject> map = new HashMap<>();
        for (int i = 0; i < results.length(); i++) {
            JSONObject row = results.getJSONObject(i);
            map.put(row.getString("f"), row);
        }
        return map;
    }

    private static void assertRowOrder(JSONObject report) throws JSONException {
        JSONArray results = report.getJSONArray("results");
        Assert.assertEquals(EXPECTED_ORDER.length, results.length());
        for (int i = 0; i < EXPECTED_ORDER.length; i++) {
            Assert.assertEquals(EXPECTED_ORDER[i], results.getJSONObject(i).getString("f"));
        }
    }

    private static void assertRow(JSONObject row, boolean ok, int st, @Nullable String e, @Nullable Integer n) throws JSONException {
        String label = row.getString("f");
        Assert.assertEquals(label + " ok", ok, row.getBoolean("ok"));
        Assert.assertEquals(label + " st", st, row.getInt("st"));
        Assert.assertTrue(label + " ms", row.getLong("ms") >= 0);
        if (e == null) {
            Assert.assertFalse(label + " should have no e", row.has("e"));
        } else {
            Assert.assertEquals(label + " e", e, row.getString("e"));
        }
        if (n == null) {
            Assert.assertFalse(label + " should have no n", row.has("n"));
        } else {
            Assert.assertEquals(label + " n", (int) n, row.getInt("n"));
        }
        Assert.assertFalse(label + " must not be marked unsupported", row.has("sk"));
    }

    private void awaitBatteryIdle(Countly instance, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            ModuleConnectionTest module = instance.moduleConnectionTest;
            if (module == null || !module.batteryRunning.get()) {
                return;
            }
            Thread.sleep(50);
        }
        Assert.fail("battery did not finish in time");
    }

    // ================ Full flow ================

    /**
     * Healthy server behind a path prefix. The armed flag on the live sc response runs all twelve rows,
     * every probe is a parameterless GET resolved against the prefixed URL with no custom headers, the
     * report is one dedicated request with the SDK identity, rows are in table order, and the flag never
     * reaches the cached server config.
     */
    @Test
    public void armed_healthyServer_reportsAllRowsReachable() throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Countly-Test", "yes");
        CountlyConfig cc = config(prefixedUrl(), "appKeyCt", "deviceCt", scResponse(1, "{\"tracking\":true,\"vt\":false}"))
            .addCustomNetworkRequestHeaders(headers);
        countly = new Countly().init(cc);

        Recorded recorded = awaitReports(1, 15_000);
        Assert.assertEquals(1, recorded.reports.size());
        JSONObject report = recorded.reports.get(0);

        // identity + order
        Assert.assertTrue(report.getLong("ts") > 0);
        Assert.assertEquals(countly.COUNTLY_SDK_NAME, report.getJSONObject("sdk").getString("name"));
        Assert.assertEquals(countly.COUNTLY_SDK_VERSION_STRING, report.getJSONObject("sdk").getString("version"));
        assertRowOrder(report);

        Map<String, JSONObject> rows = rowsByFeature(report);
        assertRow(rows.get("core"), true, 200, null, null);
        assertRow(rows.get("core-write"), true, 400, null, null);
        assertRow(rows.get("sc"), true, 200, null, null);
        assertRow(rows.get("rc"), true, 400, null, null);
        assertRow(rows.get("ab"), true, 400, null, null);
        assertRow(rows.get("feedback"), true, 400, null, null);
        assertRow(rows.get("feedback-widget"), true, 400, null, 3);
        assertRow(rows.get("feedback-submit"), true, 400, null, null);
        assertRow(rows.get("content"), true, 400, null, null);
        assertRow(rows.get("feedback-page"), true, 200, null, 3);
        assertRow(rows.get("feedback-assets"), true, 200, null, 2);
        assertRow(rows.get("content-page"), true, 200, null, null);

        // 16 probes: bare GETs, prefixed, marked, carrying no identity, but the SDK's custom headers, so a gateway
        // that needs them sees the probe the way it sees real traffic
        Assert.assertEquals(16, recorded.probes.size());
        List<String> probedPaths = new ArrayList<>();
        for (RecordedRequest probe : recorded.probes) {
            Assert.assertEquals("GET", probe.getMethod());
            String path = probe.getPath();
            Assert.assertNotNull(path);
            Assert.assertTrue(path, path.startsWith(PREFIX + "/"));
            Assert.assertTrue(path, path.contains("ct=1"));
            Assert.assertTrue(path, path.contains("_="));
            Assert.assertFalse(path, path.contains("app_key"));
            Assert.assertFalse(path, path.contains("device_id"));
            Assert.assertEquals("yes", probe.getHeader("X-Countly-Test"));
            Assert.assertEquals(0, probe.getBodySize());
            probedPaths.add(pathOnly(probe));
        }
        for (ModuleConnectionTest.ProbeRow row : ModuleConnectionTest.ROWS) {
            for (String p : row.paths) {
                String expected = PREFIX + (p.contains("?") ? p.substring(0, p.indexOf('?')) : p);
                Assert.assertTrue(expected, probedPaths.contains(expected));
            }
        }
        // method selectors survive as routing details
        boolean sawRc = false;
        for (RecordedRequest probe : recorded.probes) {
            if (probe.getPath() != null && probe.getPath().contains("/o/sdk?method=rc&")) {
                sawRc = true;
            }
        }
        Assert.assertTrue(sawRc);

        // the report is its own request with the normal identity params and the custom header
        RecordedRequest reportRequest = null;
        for (RecordedRequest r : recorded.sdkRequests) {
            if (sdkParams(r).containsKey("ct_results")) {
                reportRequest = r;
            }
        }
        Assert.assertNotNull(reportRequest);
        Map<String, String> params = sdkParams(reportRequest);
        Assert.assertEquals("appKeyCt", params.get("app_key"));
        Assert.assertEquals("deviceCt", params.get("device_id"));
        Assert.assertEquals(countly.COUNTLY_SDK_VERSION_STRING, params.get("sdk_version"));
        Assert.assertFalse(params.containsKey("events"));
        Assert.assertFalse(params.containsKey("begin_session"));
        Assert.assertEquals("yes", reportRequest.getHeader("X-Countly-Test"));

        // the flag was stripped before caching and the rest of the config still applied
        String stored = TestUtils.getCountlyStore().getServerConfig();
        Assert.assertNotNull(stored);
        Assert.assertFalse(stored, stored.contains("\"ct\""));
        Assert.assertFalse(countly.moduleConfiguration.getViewTrackingEnabled());
        Assert.assertFalse(countly.moduleConnectionTest.batteryRunning.get());
    }

    /**
     * Broken server: each failure class is graded to its own reason, redirects are read as 3xx and never
     * followed, ping and asset rows need a real 2xx, and a combined row keeps probing after its first failure
     * while reporting the first failing status.
     */
    @Test
    public void armed_brokenServer_gradesEachFailure() throws Exception {
        Map<String, MockResponse> overrides = new HashMap<>();
        overrides.put(PREFIX + "/o/ping", json(404, "{\"result\":\"DB Error\"}"));
        overrides.put(PREFIX + "/i", new MockResponse().setResponseCode(403).setBody("forbidden"));
        overrides.put(PREFIX + "/o/sdk", json(500, "{\"result\":\"boom\"}"));
        overrides.put(PREFIX + "/o/surveys/survey/widget", new MockResponse().setResponseCode(302).setHeader("Location", PREFIX + "/o/ping"));
        overrides.put(PREFIX + "/star-rating/images/ct-probe.png", new MockResponse().setResponseCode(404).setBody("nope"));
        overrides.put(PREFIX + "/feedback/rating", new MockResponse().setResponseCode(502).setBody("bad gateway"));
        server.setDispatcher(healthyDispatcher(overrides));

        countly = new Countly().init(config(prefixedUrl(), "appKeyCt", "deviceCt", scResponse(true, null)));

        Recorded recorded = awaitReports(1, 15_000);
        Assert.assertEquals(1, recorded.reports.size());
        JSONObject report = recorded.reports.get(0);
        assertRowOrder(report);
        Map<String, JSONObject> rows = rowsByFeature(report);

        assertRow(rows.get("core"), false, 404, "HTTP 404", null);
        assertRow(rows.get("core-write"), false, 403, "HTTP 403", null);
        assertRow(rows.get("sc"), true, 200, null, null);
        // every /o/sdk selector shares the 500 override
        assertRow(rows.get("rc"), false, 500, "HTTP 500", null);
        assertRow(rows.get("ab"), false, 500, "HTTP 500", null);
        assertRow(rows.get("feedback"), false, 500, "HTTP 500", null);
        assertRow(rows.get("feedback-widget"), false, 302, "redirected", 3);
        assertRow(rows.get("feedback-submit"), true, 400, null, null);
        assertRow(rows.get("content"), true, 400, null, null);
        assertRow(rows.get("feedback-page"), false, 502, "HTTP 502", 3);
        assertRow(rows.get("feedback-assets"), false, 404, "HTTP 404", 2);
        assertRow(rows.get("content-page"), true, 200, null, null);

        // all 16 probes still issued (combined rows do not stop at the first failure), and the redirect was
        // not followed: /o/ping was requested exactly once, by its own row
        Assert.assertEquals(16, recorded.probes.size());
        int pingCount = 0;
        for (RecordedRequest probe : recorded.probes) {
            if (pathOnly(probe).equals(PREFIX + "/o/ping")) {
                pingCount++;
            }
        }
        Assert.assertEquals(1, pingCount);
    }

    /**
     * No flag, an explicit zero, a false, and an empty string all mean "do nothing": no probe is issued
     * and no report is queued, while the config itself is still accepted.
     */
    @Test
    public void notArmed_variants_doNothing() throws Exception {
        Object[] variants = { null, 0, false, "\"\"", "\"0\"", "\"false\"" };
        for (Object variant : variants) {
            resetAll();
            countly = new Countly().init(config(prefixedUrl(), "appKeyCt", "deviceCt", scResponse(variant, "{\"vt\":false}")));
            Thread.sleep(700);
            awaitBatteryIdle(countly, 2_000);

            Recorded recorded = awaitReports(0, 500);
            Assert.assertEquals("variant " + variant, 0, recorded.probes.size());
            Assert.assertEquals("variant " + variant, 0, recorded.reports.size());
            Assert.assertFalse("variant " + variant, countly.moduleConfiguration.getViewTrackingEnabled());
            String stored = TestUtils.getCountlyStore().getServerConfig();
            Assert.assertNotNull(stored);
            Assert.assertFalse(stored, stored.contains("\"ct\""));
        }
    }

    /**
     * The server config that arms the test also switches networking off: the SDK honours the setting and
     * runs no battery, so an operator who disabled networking does not get probe traffic either.
     */
    @Test
    public void armed_networkingDisabled_skipsBattery() throws Exception {
        countly = new Countly().init(config(prefixedUrl(), "appKeyCt", "deviceCt", scResponse(1, "{\"networking\":false}")));
        Thread.sleep(700);
        awaitBatteryIdle(countly, 2_000);

        Recorded recorded = awaitReports(0, 500);
        Assert.assertEquals(0, recorded.probes.size());
        Assert.assertEquals(0, recorded.reports.size());
        Assert.assertFalse(countly.moduleConfiguration.getNetworkingEnabled());
    }

    /**
     * Server unreachable: every probed row is a transport error with status 0, the sc row still carries its
     * measurement, and the report is queued (it cannot be sent, so it stays in the request queue).
     */
    @Test
    public void armed_serverDown_reportsTransportErrorsAndQueuesReport() throws Exception {
        String deadUrl = prefixedUrl();
        server.shutdown();

        countly = new Countly().init(config(deadUrl, "appKeyCt", "deviceCt", scResponse(1, null)));
        awaitBatteryIdle(countly, 15_000);

        Map<String, String>[] rq = TestUtils.getCurrentRQ(countly);
        JSONObject report = null;
        int reportCount = 0;
        for (Map<String, String> request : rq) {
            if (request != null && request.containsKey("ct_results")) {
                report = new JSONObject(request.get("ct_results"));
                reportCount++;
                Assert.assertEquals("appKeyCt", request.get("app_key"));
                Assert.assertEquals("deviceCt", request.get("device_id"));
            }
        }
        Assert.assertEquals(1, reportCount);
        Assert.assertNotNull(report);
        assertRowOrder(report);

        Map<String, JSONObject> rows = rowsByFeature(report);
        for (String feature : EXPECTED_ORDER) {
            JSONObject row = rows.get(feature);
            if (feature.equals("sc")) {
                assertRow(row, true, 200, null, null);
                continue;
            }
            Assert.assertFalse(feature, row.getBoolean("ok"));
            Assert.assertEquals(feature, 0, row.getInt("st"));
            Assert.assertEquals(feature, "transport error", row.getString("e"));
        }
        Assert.assertEquals(3, rows.get("feedback-widget").getInt("n"));
        Assert.assertEquals(2, rows.get("feedback-assets").getInt("n"));

        // re-create the server so tearDown can shut it down cleanly
        server = new MockWebServer();
        server.start();
    }

    /**
     * A server that accepts the connection but never answers: the per-request deadline turns into a
     * "timeout" row with status 0 and at least the deadline in ms, while the remaining rows are unaffected.
     */
    @Test
    public void armed_silentEndpoint_reportsTimeout() throws Exception {
        Map<String, MockResponse> overrides = new HashMap<>();
        overrides.put(PREFIX + "/o/sdk/content", new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        server.setDispatcher(healthyDispatcher(overrides));

        // the sc response is delivered synchronously from init, before a test could shrink the deadline, so
        // init with no config response first, then arm and fetch by hand
        final String[] scHolder = { null };
        CountlyConfig cc = new CountlyConfig(TestUtils.getApplication(), "appKeyCt", prefixedUrl())
            .setDeviceId("deviceCt")
            .setLoggingEnabled(true);
        cc.immediateRequestGenerator = switchableIRG(scHolder);
        countly = new Countly().init(cc);
        // generous enough for a cold emulator socket to the healthy endpoints, short enough to keep the test quick
        countly.moduleConnectionTest.perRequestTimeoutMs = 2000;
        scHolder[0] = scResponse(1, null);
        countly.moduleConfiguration.fetchConfigFromServer(cc);

        Recorded recorded = awaitReports(1, 15_000);
        Assert.assertEquals(1, recorded.reports.size());
        Map<String, JSONObject> rows = rowsByFeature(recorded.reports.get(0));

        JSONObject content = rows.get("content");
        assertRow(content, false, 0, "timeout", null);
        Assert.assertTrue(content.getLong("ms") >= 2000);
        assertRow(rows.get("core"), true, 200, null, null);
        assertRow(rows.get("content-page"), true, 200, null, null);
    }

    // ================ Battery control ================

    /**
     * Fake transport that blocks each probe on a latch and counts probes, so concurrency and halting can be
     * driven deterministically.
     */
    private static class GatedTransport implements ModuleConnectionTest.ProbeTransport {
        final CountDownLatch gate = new CountDownLatch(1);
        final CountDownLatch firstProbeStarted = new CountDownLatch(1);
        final AtomicInteger probes = new AtomicInteger();
        final int status;

        GatedTransport(int status) {
            this.status = status;
        }

        @Override public @NonNull ModuleConnectionTest.ProbeOutcome probe(@NonNull String url, int timeoutMs, @Nullable ConnectionProcessor cp) {
            probes.incrementAndGet();
            firstProbeStarted.countDown();
            try {
                gate.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
            ModuleConnectionTest.ProbeOutcome outcome = new ModuleConnectionTest.ProbeOutcome();
            outcome.status = status;
            outcome.ms = 1;
            return outcome;
        }
    }

    private Countly initWithoutBattery(String appKey, String deviceId) {
        CountlyConfig cc = config(prefixedUrl(), appKey, deviceId, scResponse(null, null));
        cc.disableSDKBehaviorSettingsUpdates();
        return new Countly().init(cc);
    }

    /**
     * One battery at a time: a second armed delivery while one is in flight is ignored, but a delivery
     * after completion runs again, since a re-delivery means the server never got the first report.
     */
    @Test
    public void startBattery_secondDeliveryIgnoredWhileRunning_reRunsAfterCompletion() throws Exception {
        countly = initWithoutBattery("appKeyCt", "deviceCt");
        ModuleConnectionTest module = countly.moduleConnectionTest;
        GatedTransport transport = new GatedTransport(400);
        module.transport = transport;

        module.startBattery(12);
        Assert.assertTrue(transport.firstProbeStarted.await(5, TimeUnit.SECONDS));
        Assert.assertTrue(module.batteryRunning.get());

        module.startBattery(13);
        module.startBattery(14);
        Thread.sleep(200);
        Assert.assertEquals(1, transport.probes.get());

        transport.gate.countDown();
        awaitBatteryIdle(countly, 10_000);
        Assert.assertEquals(16, transport.probes.get());

        Recorded recorded = awaitReports(1, 10_000);
        Assert.assertEquals(1, recorded.reports.size());
        Assert.assertEquals(12, rowsByFeature(recorded.reports.get(0)).get("sc").getLong("ms"));

        // re-delivery after completion runs again
        GatedTransport second = new GatedTransport(400);
        second.gate.countDown();
        module.transport = second;
        module.startBattery(99);
        awaitBatteryIdle(countly, 10_000);
        Assert.assertEquals(16, second.probes.get());
        recorded = awaitReports(1, 10_000);
        Assert.assertEquals(1, recorded.reports.size());
        Assert.assertEquals(99, rowsByFeature(recorded.reports.get(0)).get("sc").getLong("ms"));
    }

    /**
     * Halting the instance mid-battery drops the report instead of queueing it into a torn down instance,
     * and a later delivery to the halted module is refused.
     */
    @Test
    public void halt_duringBattery_dropsReport() throws Exception {
        countly = initWithoutBattery("appKeyCt", "deviceCt");
        ModuleConnectionTest module = countly.moduleConnectionTest;
        GatedTransport transport = new GatedTransport(400);
        module.transport = transport;
        final AtomicInteger sent = new AtomicInteger();
        module.requestQueueProvider = new CountingRequestQueue(sent);

        module.startBattery(5);
        Assert.assertTrue(transport.firstProbeStarted.await(5, TimeUnit.SECONDS));
        module.halt();
        transport.gate.countDown();

        long deadline = System.currentTimeMillis() + 10_000;
        while (module.batteryRunning.get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        Assert.assertFalse(module.batteryRunning.get());
        Assert.assertEquals(0, sent.get());
        // the battery stops at the next row boundary once halted
        Assert.assertTrue(transport.probes.get() < 16);

        module.startBattery(6);
        Thread.sleep(200);
        Assert.assertFalse(module.batteryRunning.get());
        Assert.assertEquals(0, sent.get());
    }

    /**
     * Battery deadline: with a tiny per-request budget and a slow transport, rows the deadline is reached
     * before are reported as "not run: battery cap" with status 0, in table order, and the report still goes out.
     */
    @Test
    public void batteryCap_marksUnreachedRowsNotRun() throws Exception {
        countly = initWithoutBattery("appKeyCt", "deviceCt");
        ModuleConnectionTest module = countly.moduleConnectionTest;
        module.perRequestTimeoutMs = 10;
        module.batteryCapExtraMs = 0;
        // cap = 16 * 10 + 0 = 160 ms, each probe takes ~120 ms
        module.transport = new ModuleConnectionTest.ProbeTransport() {
            @Override public @NonNull ModuleConnectionTest.ProbeOutcome probe(@NonNull String url, int timeoutMs, @Nullable ConnectionProcessor cp) {
                try {
                    Thread.sleep(120);
                } catch (InterruptedException ignored) {
                }
                ModuleConnectionTest.ProbeOutcome outcome = new ModuleConnectionTest.ProbeOutcome();
                // ping and the asset routes need a 2xx to be graded reachable
                outcome.status = url.contains("/o/ping") || url.contains(".png") ? 200 : 400;
                outcome.ms = 120;
                return outcome;
            }
        };

        List<ModuleConnectionTest.ResultRow> rows = module.runBattery(7);
        Assert.assertEquals(12, rows.size());
        Assert.assertEquals("core", rows.get(0).feature);
        Assert.assertTrue(rows.get(0).ok);
        Assert.assertEquals("core-write", rows.get(1).feature);
        Assert.assertTrue(rows.get(1).ok);
        Assert.assertEquals("sc", rows.get(2).feature);
        Assert.assertEquals(7, rows.get(2).ms);

        boolean sawNotRun = false;
        for (int i = 3; i < rows.size(); i++) {
            ModuleConnectionTest.ResultRow row = rows.get(i);
            Assert.assertEquals(EXPECTED_ORDER[i], row.feature);
            if (sawNotRun) {
                Assert.assertEquals(row.feature, "not run: battery cap", row.error);
            }
            if ("not run: battery cap".equals(row.error)) {
                sawNotRun = true;
                Assert.assertFalse(row.ok);
                Assert.assertEquals(0, row.status);
                Assert.assertEquals(0, row.ms);
            }
        }
        Assert.assertTrue(sawNotRun);
        Assert.assertEquals("not run: battery cap", rows.get(11).error);

        // a "not run" row serialises with ok/st/ms/e and no n
        JSONObject json = ModuleConnectionTest.rowToJson(rows.get(11));
        Assert.assertFalse(json.getBoolean("ok"));
        Assert.assertEquals(0, json.getInt("st"));
        Assert.assertEquals("not run: battery cap", json.getString("e"));
        Assert.assertFalse(json.has("n"));
    }

    /** Minimal request queue stand-in that only counts report submissions. */
    private static class CountingRequestQueue extends ConnectionQueue {
        final AtomicInteger sent;

        CountingRequestQueue(AtomicInteger sent) {
            this.sent = sent;
        }

        @Override public void sendConnectionTestResults(@NonNull String resultsJson) {
            sent.incrementAndGet();
        }

        @Override public ConnectionProcessor createConnectionProcessor() {
            return null;
        }
    }

    // ================ Multi instance ================

    /**
     * Two named instances against two path prefixes on the same server, both armed: each runs its own
     * battery against its own URL and reports through its own queue with its own identity.
     */
    @Test
    public void multiInstance_eachArmedInstanceRunsAndReportsIndependently() throws Exception {
        final String prefixA = "/tenantA";
        final String prefixB = "/tenantB";
        server.setDispatcher(new Dispatcher() {
            @NonNull @Override public MockResponse dispatch(@NonNull RecordedRequest request) {
                if (isSdkRequest(request)) {
                    return json(200, "{\"result\":\"Success\"}");
                }
                String path = pathOnly(request);
                if (path.startsWith(prefixB + "/o/ping")) {
                    return json(404, "{\"result\":\"DB Error\"}");
                }
                if (path.endsWith("/o/ping")) {
                    return json(200, "{\"result\":\"Success\"}");
                }
                if (path.endsWith("/ct-probe.png") || path.contains("/feedback/") || path.endsWith("/_external/content/")) {
                    return new MockResponse().setResponseCode(200).setBody("ok");
                }
                return json(400, "{\"result\":\"Missing parameter\"}");
            }
        });

        String base = server.url("/").toString();
        base = base.substring(0, base.length() - 1);

        Countly a = Countly.instance(NAMED[0]);
        a.init(config(base + prefixA, "appKeyA", "deviceA", scResponse(1, null)));
        Countly b = Countly.instance(NAMED[1]);
        b.init(config(base + prefixB, "appKeyB", "deviceB", scResponse(1, null)));

        Recorded recorded = awaitReports(2, 20_000);
        Assert.assertEquals(2, recorded.reports.size());
        Assert.assertEquals(32, recorded.probes.size());

        int probesA = 0;
        int probesB = 0;
        for (RecordedRequest probe : recorded.probes) {
            String path = pathOnly(probe);
            if (path.startsWith(prefixA + "/")) {
                probesA++;
            } else if (path.startsWith(prefixB + "/")) {
                probesB++;
            } else {
                Assert.fail("probe outside both prefixes: " + path);
            }
        }
        Assert.assertEquals(16, probesA);
        Assert.assertEquals(16, probesB);

        JSONObject reportA = null;
        JSONObject reportB = null;
        for (RecordedRequest r : recorded.sdkRequests) {
            Map<String, String> params = sdkParams(r);
            if (!params.containsKey("ct_results")) {
                continue;
            }
            String path = r.getPath();
            Assert.assertNotNull(path);
            if ("appKeyA".equals(params.get("app_key"))) {
                Assert.assertEquals("deviceA", params.get("device_id"));
                Assert.assertTrue(path, path.startsWith(prefixA + "/i"));
                reportA = new JSONObject(params.get("ct_results"));
            } else if ("appKeyB".equals(params.get("app_key"))) {
                Assert.assertEquals("deviceB", params.get("device_id"));
                Assert.assertTrue(path, path.startsWith(prefixB + "/i"));
                reportB = new JSONObject(params.get("ct_results"));
            }
        }
        Assert.assertNotNull(reportA);
        Assert.assertNotNull(reportB);
        assertRowOrder(reportA);
        assertRowOrder(reportB);
        assertRow(rowsByFeature(reportA).get("core"), true, 200, null, null);
        assertRow(rowsByFeature(reportB).get("core"), false, 404, "HTTP 404", null);

        // the default instance was never armed and holds no report
        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);
    }

    // ================ Pure logic ================

    /** Grading table from the spec, including the strict 2xx rule for rows 1 and 11. */
    @Test
    public void grade_followsSpecTable() {
        Object[][] cases = {
            // status, failure, requiresSuccess, expected e (null = reachable)
            { 200, null, false, null },
            { 204, null, false, null },
            { 400, null, false, null },
            { 404, null, false, null },
            { 403, null, false, "HTTP 403" },
            { 500, null, false, "HTTP 500" },
            { 502, null, false, "HTTP 502" },
            { 301, null, false, "redirected" },
            { 302, null, false, "redirected" },
            { 307, null, false, "redirected" },
            { 0, "timeout", false, "timeout" },
            { 0, "transport error", false, "transport error" },
            { 0, null, false, "transport error" },
            { 100, null, false, "HTTP 100" },
            // strict rows
            { 200, null, true, null },
            { 400, null, true, "HTTP 400" },
            { 404, null, true, "HTTP 404" },
            { 403, null, true, "HTTP 403" },
            { 302, null, true, "redirected" },
            { 0, "timeout", true, "timeout" },
        };
        for (Object[] c : cases) {
            ModuleConnectionTest.ProbeOutcome outcome = new ModuleConnectionTest.ProbeOutcome();
            outcome.status = (int) c[0];
            outcome.failure = (String) c[1];
            Assert.assertEquals("status " + c[0] + " strict " + c[2], c[3], ModuleConnectionTest.grade(outcome, (boolean) c[2]));
        }
    }

    /** URL resolution keeps the path prefix, tolerates trailing slashes, and joins the query correctly. */
    @Test
    public void buildProbeUrl_resolvesAgainstConfiguredUrl() {
        String plain = ModuleConnectionTest.buildProbeUrl("https://x.com", "/o/ping");
        Assert.assertTrue(plain, plain.startsWith("https://x.com/o/ping?ct=1&_="));

        String prefixed = ModuleConnectionTest.buildProbeUrl("https://x.com/countly/", "/o/ping");
        Assert.assertTrue(prefixed, prefixed.startsWith("https://x.com/countly/o/ping?ct=1&_="));

        String doubleSlash = ModuleConnectionTest.buildProbeUrl("https://x.com/countly//", "o/ping");
        Assert.assertTrue(doubleSlash, doubleSlash.startsWith("https://x.com/countly/o/ping?ct=1&_="));

        String withQuery = ModuleConnectionTest.buildProbeUrl("https://x.com", "/o/sdk?method=rc");
        Assert.assertTrue(withQuery, withQuery.startsWith("https://x.com/o/sdk?method=rc&ct=1&_="));
        Assert.assertEquals(1, withQuery.length() - withQuery.replace("?", "").length());
    }

    /** The armed flag is read once and removed, for every JSON shape the server might use. */
    @Test
    public void extractConnectionTestFlag_readsAndStrips() throws JSONException {
        Object[][] cases = {
            { "{\"v\":1,\"ct\":1}", true },
            { "{\"v\":1,\"ct\":true}", true },
            { "{\"v\":1,\"ct\":\"1\"}", true },
            { "{\"v\":1,\"ct\":2.5}", true },
            { "{\"v\":1,\"ct\":{}}", true },
            { "{\"v\":1,\"ct\":0}", false },
            { "{\"v\":1,\"ct\":false}", false },
            { "{\"v\":1,\"ct\":\"\"}", false },
            { "{\"v\":1,\"ct\":\"0\"}", false },
            { "{\"v\":1,\"ct\":\"false\"}", false },
            { "{\"v\":1,\"ct\":null}", false },
            { "{\"v\":1}", false },
        };
        for (Object[] c : cases) {
            JSONObject response = new JSONObject((String) c[0]);
            Assert.assertEquals((String) c[0], c[1], ModuleConfiguration.extractConnectionTestFlag(response));
            Assert.assertFalse((String) c[0], response.has("ct"));
            Assert.assertEquals(1, response.getInt("v"));
        }
        Assert.assertFalse(ModuleConfiguration.extractConnectionTestFlag(null));
    }

    /**
     * Report caps: at most 32 rows, every e truncated to 256 characters, and when the report is still over
     * 8 KB the diagnostic e fields are dropped while "opaque" survives as the confidence qualifier.
     */
    @Test
    public void buildReport_enforcesCaps() throws Exception {
        countly = initWithoutBattery("appKeyCt", "deviceCt");
        ModuleConnectionTest module = countly.moduleConnectionTest;

        StringBuilder longError = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            longError.append('x');
        }

        // 40 rows, each with a 300 char error: 40 * ~330 B is over 8 KB, so e is dropped, then 32 rows fit
        List<ModuleConnectionTest.ResultRow> rows = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            ModuleConnectionTest.ResultRow row = new ModuleConnectionTest.ResultRow("row" + i);
            row.ok = false;
            row.status = 403;
            row.ms = i;
            row.error = longError.toString();
            rows.add(row);
        }
        ModuleConnectionTest.ResultRow opaque = new ModuleConnectionTest.ResultRow("opaque-row");
        opaque.ok = true;
        opaque.status = 0;
        opaque.ms = 3;
        opaque.error = "opaque";
        rows.set(5, opaque);

        JSONObject report = new JSONObject(module.buildReport(rows));
        JSONArray results = report.getJSONArray("results");
        Assert.assertEquals(32, results.length());
        Assert.assertTrue(ModuleConnectionTest.utf8Length(report.toString()) <= 8 * 1024);
        Assert.assertEquals(countly.COUNTLY_SDK_NAME, report.getJSONObject("sdk").getString("name"));
        for (int i = 0; i < results.length(); i++) {
            JSONObject row = results.getJSONObject(i);
            if (i == 5) {
                Assert.assertEquals("opaque-row", row.getString("f"));
                Assert.assertEquals("opaque", row.getString("e"));
            } else {
                Assert.assertEquals("row" + i, row.getString("f"));
                Assert.assertFalse(row.has("e"));
            }
        }

        // a small report keeps its e, truncated to 256
        List<ModuleConnectionTest.ResultRow> few = new ArrayList<>();
        ModuleConnectionTest.ResultRow one = new ModuleConnectionTest.ResultRow("core");
        one.ok = false;
        one.status = 500;
        one.ms = 10;
        one.error = longError.toString();
        few.add(one);
        JSONObject small = new JSONObject(module.buildReport(few));
        JSONObject row = small.getJSONArray("results").getJSONObject(0);
        Assert.assertEquals(256, row.getString("e").length());
        Assert.assertEquals(500, row.getInt("st"));
        Assert.assertFalse(row.has("n"));
        Assert.assertTrue(small.getLong("ts") > 0);
    }

    /** The sc row is omitted, not invented, when no latency measurement exists. */
    @Test
    public void runBattery_noScLatency_omitsScRow() {
        countly = initWithoutBattery("appKeyCt", "deviceCt");
        ModuleConnectionTest module = countly.moduleConnectionTest;
        module.transport = new ModuleConnectionTest.ProbeTransport() {
            @Override public @NonNull ModuleConnectionTest.ProbeOutcome probe(@NonNull String url, int timeoutMs, @Nullable ConnectionProcessor cp) {
                ModuleConnectionTest.ProbeOutcome outcome = new ModuleConnectionTest.ProbeOutcome();
                outcome.status = 400;
                outcome.ms = 2;
                return outcome;
            }
        };

        List<ModuleConnectionTest.ResultRow> rows = module.runBattery(-1);
        Assert.assertEquals(11, rows.size());
        for (ModuleConnectionTest.ResultRow row : rows) {
            Assert.assertNotEquals("sc", row.feature);
        }
        // combined row sums latency and counts paths
        ModuleConnectionTest.ResultRow widget = rows.get(5);
        Assert.assertEquals("feedback-widget", widget.feature);
        Assert.assertEquals(6, widget.ms);
        Assert.assertEquals(3, widget.pathCount);
        Assert.assertTrue(widget.ok);
        Assert.assertEquals(400, widget.status);
    }
}
