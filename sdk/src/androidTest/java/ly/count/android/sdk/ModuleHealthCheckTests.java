package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ModuleHealthCheckTests {

    @Before
    public void setUp() {
        TestUtils.getCountlyStore().clear();
        Countly.sharedInstance().halt();
    }

    @After
    public void tearDown() {
        TestUtils.getCountlyStore().clear();
        Countly.sharedInstance().halt();
    }

    /**
     * A programmable {@link ImmediateRequestGenerator} that stands in for real networking.
     * It records every health check request (the one carrying the "&hc=" param), the callback
     * the module registered, and how many health checks were attempted. Non health-check
     * requests (e.g. server config) are ignored so they cannot interfere with assertions.
     */
    private static class CapturingIRG implements ImmediateRequestGenerator {
        int hcCallCount = 0;
        String lastHcRequestData = null;
        String lastHcEndpoint = null;
        ImmediateRequestMaker.InternalImmediateRequestCallback hcCallback = null;

        @Override public ImmediateRequestI CreateImmediateRequestMaker() {
            return (requestData, customEndpoint, cp, requestShouldBeDelayed, networkingIsEnabled, callback, log) -> {
                if (requestData != null && requestData.contains("&hc=")) {
                    hcCallCount++;
                    lastHcRequestData = requestData;
                    lastHcEndpoint = customEndpoint;
                    hcCallback = callback;
                }
            };
        }

        @Override public ImmediateRequestI CreatePreflightRequestMaker() {
            return null;
        }
    }

    private Countly initWith(CapturingIRG irg, CountlyConfig config) {
        config.immediateRequestGenerator = irg;
        return new Countly().init(config); // initFinished() -> sendHealthCheck()
    }

    /**
     * The base config with sessions handled by the test, so no automatic session interferes with the counts.
     */
    private CountlyConfig manualSessionsConfig() {
        return TestUtils.createBaseConfig().enableManualSessionControl();
    }

    /**
     * Decodes the "&hc=" request parameter (URL-encoded JSON) back into a JSONObject
     * so the encoded counters can be asserted on.
     */
    private JSONObject decodeHcParam(String requestData) throws Exception {
        int idx = requestData.indexOf("&hc=");
        Assert.assertTrue("request must carry the &hc= param", idx >= 0);
        String encoded = requestData.substring(idx + "&hc=".length());
        return new JSONObject(URLDecoder.decode(encoded, "UTF-8"));
    }

    /**
     * The count of a method usage key, 0 when it was not counted.
     */
    private int usage(HealthCheckCounter counter, String area, String method) {
        synchronized (counter) {
            Map<String, Integer> methods = counter.methodUsage.get(area);
            Integer count = methods == null ? null : methods.get(method);
            return count == null ? 0 : count;
        }
    }

    /**
     * The count of a log code, 0 when it was not recorded.
     */
    private int code(HealthCheckCounter counter, String code) {
        synchronized (counter) {
            Integer count = counter.logCodes.get(code);
            return count == null ? 0 : count;
        }
    }

    /**
     * A default init with health check enabled must send exactly one health check request,
     * to the "/i" endpoint, carrying the "&hc=" param, and mark the module as having sent it.
     */
    @Test
    public void init_healthCheckEnabled_sendsSingleRequestToIEndpoint() {
        CapturingIRG irg = new CapturingIRG();
        Countly countly = initWith(irg, TestUtils.createBaseConfig());

        Assert.assertEquals(1, irg.hcCallCount);
        Assert.assertEquals("/i", irg.lastHcEndpoint);
        Assert.assertTrue(irg.lastHcRequestData.contains("&hc="));
        Assert.assertTrue(countly.moduleHealthCheck.healthCheckSent);
    }

    /**
     * When the health check is disabled via config, init must not attempt any health check
     * request and the module must reflect the disabled state, while the counters keep counting.
     */
    @Test
    public void init_healthCheckDisabled_doesNotSend() {
        CapturingIRG irg = new CapturingIRG();
        Countly countly = initWith(irg, manualSessionsConfig().disableHealthCheck());

        Assert.assertEquals(0, irg.hcCallCount);
        Assert.assertFalse(countly.moduleHealthCheck.healthCheckEnabled);
        Assert.assertFalse(countly.moduleHealthCheck.healthCheckSent);

        countly.events().recordEvent("counted_while_disabled");
        Assert.assertEquals(1, usage(countly.moduleHealthCheck.hCounter, "events", "record"));
    }

    /**
     * In temporary device ID mode the health check must be aborted before sending, and
     * because it never passes the guards the "sent" flag must stay false.
     */
    @Test
    public void init_temporaryDeviceIdMode_doesNotSend() {
        // No explicit device ID: an explicit ID would take precedence over temporary mode.
        CountlyConfig config = new CountlyConfig(TestUtils.getApplication(), TestUtils.commonAppKey, TestUtils.commonURL)
            .setLoggingEnabled(true)
            .enableTemporaryDeviceIdMode();
        CapturingIRG irg = new CapturingIRG();
        Countly countly = initWith(irg, config);

        Assert.assertEquals(0, irg.hcCallCount);
        Assert.assertTrue(countly.moduleHealthCheck.healthCheckEnabled);
        Assert.assertFalse(countly.moduleHealthCheck.healthCheckSent);
    }

    /**
     * The "already sent" guard must make repeated sendHealthCheck() calls no-ops so the SDK
     * never sends more than one health check per session.
     */
    @Test
    public void sendHealthCheck_calledAgain_isNoOp() {
        CapturingIRG irg = new CapturingIRG();
        Countly countly = initWith(irg, TestUtils.createBaseConfig());
        Assert.assertEquals(1, irg.hcCallCount);

        countly.moduleHealthCheck.sendHealthCheck();

        Assert.assertEquals(1, irg.hcCallCount);
    }

    /**
     * The persisted counter state must be loaded on init and encoded into the outgoing "&hc="
     * param. Warning/error counts also pick up entries the SDK logs during init, so those are
     * asserted as lower bounds; the network-driven fields (status code, error message, backoff)
     * are untouched by a mocked init and must round-trip exactly. Method usage and log codes are
     * restored too.
     */
    @Test
    public void requestParam_encodesPersistedCounters() throws Exception {
        JSONObject seed = new JSONObject();
        seed.put("LWar", 3);
        seed.put("LErr", 2);
        seed.put("RStatC", 404);
        seed.put("REMsg", "boom");
        seed.put("BReq", 5);
        seed.put("CBReq", 1);
        seed.put("MU", new JSONObject().put("events", new JSONObject().put("record", 7)));
        seed.put("LC", new JSONObject().put("w701", 2));
        TestUtils.getCountlyStore().setHealthCheckCounterState(seed.toString());

        CapturingIRG irg = new CapturingIRG();
        initWith(irg, manualSessionsConfig());

        JSONObject hc = decodeHcParam(irg.lastHcRequestData);
        Assert.assertTrue(hc.getInt("el") >= 2);  // error count (seed + any logged on init)
        Assert.assertTrue(hc.getInt("wl") >= 3);  // warning count (seed + any logged on init)
        Assert.assertEquals(404, hc.getInt("sc")); // status code
        Assert.assertEquals("boom", hc.getString("em")); // error message
        Assert.assertEquals(5, hc.getInt("bom")); // backoff request count
        Assert.assertEquals(1, hc.getInt("cbom")); // consecutive backoff count
        Assert.assertEquals(7, hc.getJSONObject("fu").getJSONObject("events").getInt("record"));
        Assert.assertEquals(2, hc.getJSONObject("lc").getInt("w701"));
    }

    /**
     * "fu" and "lc" are always part of the health check, as empty objects when nothing was counted,
     * so the server can tell an SDK that reports them from one that does not.
     */
    @Test
    public void requestParam_alwaysCarriesFuAndLc() throws Exception {
        CapturingIRG irg = new CapturingIRG();
        initWith(irg, manualSessionsConfig());

        JSONObject hc = decodeHcParam(irg.lastHcRequestData);
        Assert.assertNotNull(hc.optJSONObject("fu"));
        Assert.assertNotNull(hc.optJSONObject("lc"));
    }

    /**
     * An accepted answer takes away only what the health check reported. What was counted while
     * it was on its way stays for the next health check, also in storage.
     */
    @Test
    public void successResponse_takesAwayOnlyWhatWasReported() throws Exception {
        TestUtils.getCountlyStore().setHealthCheckCounterState(new JSONObject().put("LWar", 4).put("LErr", 3).put("LC", new JSONObject().put("w701", 1)).toString());
        CapturingIRG irg = new CapturingIRG();
        Countly countly = initWith(irg, manualSessionsConfig());
        JSONObject sent = decodeHcParam(irg.lastHcRequestData);

        HealthCheckCounter counter = countly.moduleHealthCheck.hCounter;
        long warningsSoFar = counter.countLogWarning;
        long errorsSoFar = counter.countLogError;
        counter.logWarning();
        counter.logError();
        counter.recordLogCode("w701");
        counter.recordLogCode("w722");

        irg.hcCallback.callback(new JSONObject("{\"result\":\"Success\"}"));

        Assert.assertEquals(warningsSoFar + 1 - sent.getLong("wl"), counter.countLogWarning);
        Assert.assertEquals(errorsSoFar + 1 - sent.getLong("el"), counter.countLogError);
        Assert.assertEquals(1, sent.getJSONObject("lc").getInt("w701"));
        Assert.assertEquals(1, code(counter, "w701"));
        Assert.assertEquals(1, code(counter, "w722"));

        JSONObject stored = new JSONObject(TestUtils.getCountlyStore().getHealthCheckCounterState());
        Assert.assertEquals(counter.countLogWarning, stored.getLong("LWar"));
        Assert.assertEquals(1, stored.getJSONObject("LC").getInt("w722"));
    }

    /**
     * The last failed request is cleared after a delivered health check only while it is still the
     * one that was reported.
     */
    @Test
    public void successResponse_keepsAFailedRequestNewerThanTheReport() throws Exception {
        TestUtils.getCountlyStore().setHealthCheckCounterState(new JSONObject().put("RStatC", 500).put("REMsg", "first").toString());
        CapturingIRG irg = new CapturingIRG();
        Countly countly = initWith(irg, manualSessionsConfig());

        HealthCheckCounter counter = countly.moduleHealthCheck.hCounter;
        counter.logFailedNetworkRequest(503, "second");
        irg.hcCallback.callback(new JSONObject("{\"result\":\"Success\"}"));

        Assert.assertEquals(503, counter.statusCode);
        Assert.assertEquals("second", counter.errorMessage);
    }

    /**
     * A null response (no connection) means the send failed, so counters must be preserved
     * to be retried on the next session rather than silently dropped. The failure itself is not
     * logged as a warning or an error, so it does not count itself.
     */
    @Test
    public void nullResponse_keepsCounters() {
        CapturingIRG irg = new CapturingIRG();
        Countly countly = initWith(irg, manualSessionsConfig());

        HealthCheckCounter counter = countly.moduleHealthCheck.hCounter;
        long errors = counter.countLogError;
        long warnings = counter.countLogWarning;
        counter.logError();
        counter.logError();

        irg.hcCallback.callback(null);

        // a failed send must not clear the counters, and must not add to them
        Assert.assertEquals(errors + 2, counter.countLogError);
        Assert.assertEquals(warnings, counter.countLogWarning);
    }

    /**
     * A malformed response (no "result" field) must not be treated as success, so the
     * counters must be kept rather than cleared.
     */
    @Test
    public void responseWithoutResult_keepsCounters() throws JSONException {
        CapturingIRG irg = new CapturingIRG();
        Countly countly = initWith(irg, manualSessionsConfig());

        HealthCheckCounter counter = countly.moduleHealthCheck.hCounter;
        long errors = counter.countLogError;
        counter.logError();

        irg.hcCallback.callback(new JSONObject("{\"foo\":\"bar\"}"));

        Assert.assertEquals(errors + 1, counter.countLogError);
    }

    /**
     * Regression: a successful response that arrives after the SDK has been halted (an
     * init/halt/init reinit cycle on a slow network) must not crash with an NPE when it tries
     * to reset the now-null health counter.
     */
    @Test
    public void successResponse_afterHalt_doesNotCrash() throws JSONException {
        CapturingIRG irg = new CapturingIRG();
        Countly countly = initWith(irg, TestUtils.createBaseConfig());
        ModuleHealthCheck module = countly.moduleHealthCheck;
        Assert.assertNotNull(irg.hcCallback);

        module.halt(); // simulate the reinit race: halted while the request was in flight
        Assert.assertNull(module.hCounter);

        irg.hcCallback.callback(new JSONObject("{\"result\":\"Success\"}")); // must not throw
    }

    /**
     * Regression: an activity-stopped lifecycle callback that fires after the module has been
     * halted must not crash when it tries to persist the now-null counter.
     */
    @Test
    public void onActivityStopped_afterHalt_doesNotCrash() {
        CapturingIRG irg = new CapturingIRG();
        Countly countly = initWith(irg, TestUtils.createBaseConfig());
        ModuleHealthCheck module = countly.moduleHealthCheck;

        module.halt();
        Assert.assertNull(module.hCounter);

        module.onActivityStopped(0); // must not throw
    }

    /**
     * When not halted, an activity-stopped callback must persist the current counters so they
     * survive a process death between sessions.
     */
    @Test
    public void onActivityStopped_persistsCounterState() throws Exception {
        CapturingIRG irg = new CapturingIRG();
        Countly countly = initWith(irg, TestUtils.createBaseConfig());

        HealthCheckCounter counter = countly.moduleHealthCheck.hCounter;
        counter.logWarning();
        long expected = counter.countLogWarning;
        countly.moduleHealthCheck.onActivityStopped(0);

        String stored = TestUtils.getCountlyStore().getHealthCheckCounterState();
        Assert.assertFalse(stored.isEmpty());
        Assert.assertTrue(expected >= 1);
        Assert.assertEquals(expected, new JSONObject(stored).getLong("LWar")); // persists the live value
    }

    /**
     * The session timer and the end of a session store the counters, so an app killed in the
     * foreground keeps what was counted.
     */
    @Test
    public void timerAndSessionEnd_persistCounterState() throws Exception {
        CapturingIRG irg = new CapturingIRG();
        Countly countly = initWith(irg, manualSessionsConfig());
        HealthCheckCounter counter = countly.moduleHealthCheck.hCounter;

        counter.recordLogCode("w700");
        countly.onTimer();
        Assert.assertEquals(1, new JSONObject(TestUtils.getCountlyStore().getHealthCheckCounterState()).getJSONObject("LC").getInt("w700"));

        countly.sessions().beginSession();
        counter.recordLogCode("w701");
        countly.sessions().endSession();
        Assert.assertEquals(1, new JSONObject(TestUtils.getCountlyStore().getHealthCheckCounterState()).getJSONObject("LC").getInt("w701"));
    }

    /**
     * Every listed method of the session, event, view and crash features is counted once, at its
     * public entry point, whichever convenience form the app uses.
     */
    @Test
    public void methodUsage_publicCallsAreCounted() {
        CapturingIRG irg = new CapturingIRG();
        Countly countly = initWith(irg, manualSessionsConfig());
        HealthCheckCounter counter = countly.moduleHealthCheck.hCounter;
        counter.clearValues();

        countly.sessions().beginSession();
        countly.sessions().updateSession();
        countly.sessions().endSession();

        countly.events().recordEvent("a");
        countly.events().recordEvent("b", 2);
        countly.events().recordEvent("c", new HashMap<>(), 1, 0, 0);
        countly.events().startEvent("timed");
        countly.events().endEvent("timed");
        countly.events().startEvent("cancelled");
        countly.events().cancelEvent("cancelled");

        String viewId = countly.views().startView("screen");
        countly.views().pauseViewWithID(viewId);
        countly.views().resumeViewWithID(viewId);
        countly.views().stopViewWithID(viewId);
        countly.views().startAutoStoppedView("auto");
        countly.views().stopAllViews(null);

        countly.crashes().addCrashBreadcrumb("crumb");
        countly.crashes().recordHandledException(new Exception("handled"));

        Assert.assertEquals(1, usage(counter, "sessions", "begin"));
        Assert.assertEquals(1, usage(counter, "sessions", "update"));
        Assert.assertEquals(1, usage(counter, "sessions", "end"));
        Assert.assertEquals(3, usage(counter, "events", "record"));
        Assert.assertEquals(2, usage(counter, "events", "start"));
        Assert.assertEquals(1, usage(counter, "events", "end"));
        Assert.assertEquals(1, usage(counter, "events", "cancel"));
        Assert.assertEquals(1, usage(counter, "views", "start"));
        Assert.assertEquals(1, usage(counter, "views", "pause"));
        Assert.assertEquals(1, usage(counter, "views", "resume"));
        Assert.assertEquals(1, usage(counter, "views", "stop"));
        Assert.assertEquals(1, usage(counter, "views", "startAuto"));
        Assert.assertEquals(1, usage(counter, "views", "stopAll"));
        Assert.assertEquals(1, usage(counter, "crashes", "log"));
        Assert.assertEquals(1, usage(counter, "crashes", "record"));
        Assert.assertEquals("an ended timed event is not counted as a record call", 3, usage(counter, "events", "record"));
    }

    /**
     * While consent is required, a call of a feature without consent is not counted, and neither is
     * a log code the call records. Once the consent is given, calls of that feature are counted.
     */
    @Test
    public void methodUsage_followsTheConsentOfEachFeature() {
        CapturingIRG irg = new CapturingIRG();
        Countly countly = initWith(irg, manualSessionsConfig().setRequiresConsent(true));
        HealthCheckCounter counter = countly.moduleHealthCheck.hCounter;
        counter.clearValues();

        countly.events().recordEvent("blocked");
        countly.events().recordEvent("");
        countly.views().startView("blocked");
        Assert.assertEquals(0, usage(counter, "events", "record"));
        Assert.assertEquals(0, usage(counter, "views", "start"));
        Assert.assertEquals(0, code(counter, "w701"));

        countly.consent().giveConsent(new String[] { Countly.CountlyFeatureNames.events });
        countly.events().recordEvent("allowed");
        countly.views().startView("still_blocked");
        Assert.assertEquals(1, usage(counter, "events", "record"));
        Assert.assertEquals(0, usage(counter, "views", "start"));
    }

    /**
     * Invalid or out of order calls of the session, event and view features record their log codes,
     * as warnings.
     */
    @Test
    public void logCodes_invalidCallsRecordTheirCodes() {
        CapturingIRG irg = new CapturingIRG();
        Countly countly = initWith(irg, manualSessionsConfig());
        HealthCheckCounter counter = countly.moduleHealthCheck.hCounter;
        counter.clearValues();

        countly.events().recordEvent("");
        countly.events().recordPastEvent("no_timestamp", null, 0);
        countly.events().startEvent("timed");
        countly.events().startEvent("timed");
        countly.events().endEvent("not_running");
        countly.events().cancelEvent("not_running");

        countly.views().startView("");
        countly.views().stopViewWithID("missing_view_id");
        String viewId = countly.views().startView("screen");
        countly.views().pauseViewWithID(viewId);
        countly.views().pauseViewWithID(viewId);

        countly.sessions().updateSession();
        countly.sessions().beginSession();
        countly.sessions().beginSession();

        Assert.assertEquals(1, code(counter, "w701"));
        Assert.assertEquals(1, code(counter, "w750"));
        Assert.assertEquals(1, code(counter, "w722"));
        Assert.assertEquals(1, code(counter, "w720"));
        Assert.assertEquals(1, code(counter, "w721"));
        Assert.assertEquals(1, code(counter, "w710"));
        Assert.assertEquals(1, code(counter, "w730"));
        Assert.assertEquals(1, code(counter, "w731"));
        Assert.assertEquals(2, code(counter, "w760"));
        Assert.assertEquals("these are warnings, not errors", 0, counter.countLogError);
    }

    /**
     * A manual session call while the SDK tracks sessions automatically is ignored and reported as
     * w740.
     */
    @Test
    public void logCodes_manualSessionCallDuringAutomaticTrackingRecordsW740() {
        CapturingIRG irg = new CapturingIRG();
        Countly countly = initWith(irg, TestUtils.createBaseConfig());
        HealthCheckCounter counter = countly.moduleHealthCheck.hCounter;
        counter.clearValues();

        countly.sessions().beginSession();
        countly.sessions().endSession();

        Assert.assertEquals(2, code(counter, "w740"));
        Assert.assertEquals(1, usage(counter, "sessions", "begin"));
    }

    /**
     * A call made before init is reported as w101 once the health check module exists, and an
     * internal limit below 1 as w121.
     */
    @Test
    public void logCodes_callsBeforeInitAndInvalidLimitsAreReported() {
        Countly countly = new Countly();
        Assert.assertNull(countly.events());

        CountlyConfig config = manualSessionsConfig();
        config.sdkInternalLimits.setMaxKeyLength(0);
        config.immediateRequestGenerator = new CapturingIRG();
        countly.init(config);

        HealthCheckCounter counter = countly.moduleHealthCheck.hCounter;
        Assert.assertEquals(1, code(counter, "w101"));
        Assert.assertEquals(1, code(counter, "w121"));
    }

    /**
     * The longest run of backoffs is raised at every backoff, w320 is recorded for each backoff and
     * w321 for each one that continues a run. Only an answer in time ends a run; saving does not.
     */
    @Test
    public void backoff_longestRunIsKeptWhileItGrows() {
        CapturingIRG irg = new CapturingIRG();
        Countly countly = initWith(irg, manualSessionsConfig());
        HealthCheckCounter counter = countly.moduleHealthCheck.hCounter;
        counter.clearValues();

        counter.logBackoffRequest();
        counter.logBackoffRequest();
        counter.saveState();
        counter.logBackoffRequest();
        Assert.assertEquals(3, counter.consecutiveBackoffRequest);

        counter.logConsecutiveBackoffRequest();
        counter.logBackoffRequest();

        Assert.assertEquals(4, counter.countBackoffRequest);
        Assert.assertEquals(3, counter.consecutiveBackoffRequest);
        Assert.assertEquals(4, code(counter, "w320"));
        Assert.assertEquals(2, code(counter, "w321"));
    }

    /**
     * A stored state that is not valid JSON is reset and reported as e503.
     */
    @Test
    public void malformedStoredState_isResetAndReportedAsE503() {
        TestUtils.getCountlyStore().setHealthCheckCounterState("{not json");
        CapturingIRG irg = new CapturingIRG();
        Countly countly = initWith(irg, manualSessionsConfig());

        Assert.assertEquals(1, code(countly.moduleHealthCheck.hCounter, "e503"));
    }

    /**
     * An automatically detected crash is counted in storage without the health check counters, and
     * the next start reports it as crashes / record:a and sets the stored count back to 0.
     */
    @Test
    public void automaticCrashOfThePreviousRun_isReportedAsRecordA() {
        TestUtils.getCountlyStore().incrementAutomaticCrashCount();
        TestUtils.getCountlyStore().incrementAutomaticCrashCount();
        CapturingIRG irg = new CapturingIRG();
        Countly countly = initWith(irg, manualSessionsConfig());

        Assert.assertEquals(2, usage(countly.moduleHealthCheck.hCounter, "crashes", "record:a"));
        Assert.assertEquals(0, TestUtils.getCountlyStore().takeAutomaticCrashCount());
    }

    /**
     * While an automatic crash is reported, nothing is counted, so the crash path never calls into
     * the counters.
     */
    @Test
    public void stopRecordingForCrash_ignoresEveryCount() {
        CapturingIRG irg = new CapturingIRG();
        Countly countly = initWith(irg, manualSessionsConfig());
        HealthCheckCounter counter = countly.moduleHealthCheck.hCounter;
        counter.clearValues();

        counter.stopRecordingForCrash();
        counter.logWarning();
        counter.logError();
        counter.recordLogCode("w700");
        countly.events().recordEvent("during_crash");

        Assert.assertEquals(0, counter.countLogWarning);
        Assert.assertEquals(0, counter.countLogError);
        Assert.assertEquals(0, code(counter, "w700"));
        Assert.assertEquals(0, usage(counter, "events", "record"));
    }

    /**
     * While consent is required, the health check carries the app version only with the metrics
     * consent. The health check itself needs no consent.
     */
    @Test
    public void metrics_needTheMetricsConsent() {
        CapturingIRG withoutConsent = new CapturingIRG();
        initWith(withoutConsent, manualSessionsConfig().setRequiresConsent(true));
        Assert.assertEquals(1, withoutConsent.hcCallCount);
        Assert.assertFalse(withoutConsent.lastHcRequestData.contains("&metrics="));

        TestUtils.getCountlyStore().clear();
        CapturingIRG withConsent = new CapturingIRG();
        initWith(withConsent, manualSessionsConfig().setRequiresConsent(true).setConsentEnabled(new String[] { Countly.CountlyFeatureNames.metrics }));
        Assert.assertTrue(withConsent.lastHcRequestData.contains("&metrics="));
    }

    /**
     * A count stops growing at 65535 until it is reported.
     */
    @Test
    public void counts_stopAtTheCap() {
        CapturingIRG irg = new CapturingIRG();
        Countly countly = initWith(irg, manualSessionsConfig());
        HealthCheckCounter counter = countly.moduleHealthCheck.hCounter;
        counter.clearValues();

        counter.recordLogCode("w700", 70000);

        Assert.assertEquals(HealthCheckCounter.countCap, code(counter, "w700"));
    }
}
