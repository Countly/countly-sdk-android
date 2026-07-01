package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.net.URLDecoder;
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
     * request and the module must reflect the disabled state.
     */
    @Test
    public void init_healthCheckDisabled_doesNotSend() {
        CapturingIRG irg = new CapturingIRG();
        Countly countly = initWith(irg, TestUtils.createBaseConfig().disableHealthCheck());

        Assert.assertEquals(0, irg.hcCallCount);
        Assert.assertFalse(countly.moduleHealthCheck.healthCheckEnabled);
        Assert.assertFalse(countly.moduleHealthCheck.healthCheckSent);
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
     * are untouched by a mocked init and must round-trip exactly.
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
        TestUtils.getCountlyStore().setHealthCheckCounterState(seed.toString());

        CapturingIRG irg = new CapturingIRG();
        initWith(irg, TestUtils.createBaseConfig());

        JSONObject hc = decodeHcParam(irg.lastHcRequestData);
        Assert.assertTrue(hc.getInt("el") >= 2);  // error count (seed + any logged on init)
        Assert.assertTrue(hc.getInt("wl") >= 3);  // warning count (seed + any logged on init)
        Assert.assertEquals(404, hc.getInt("sc")); // status code
        Assert.assertEquals("boom", hc.getString("em")); // error message
        Assert.assertEquals(5, hc.getInt("bom")); // backoff request count
        Assert.assertEquals(1, hc.getInt("cbom")); // consecutive backoff count
    }

    /**
     * A successful response ("result" present) must clear the in-memory counters and wipe the
     * persisted state, so the next session starts from a clean slate.
     */
    @Test
    public void successResponse_clearsAndSavesCounters() throws JSONException {
        CapturingIRG irg = new CapturingIRG();
        Countly countly = initWith(irg, TestUtils.createBaseConfig());

        HealthCheckCounter counter = countly.moduleHealthCheck.hCounter;
        counter.logWarning();
        counter.logError();
        counter.saveState();
        Assert.assertFalse(TestUtils.getCountlyStore().getHealthCheckCounterState().isEmpty());

        irg.hcCallback.callback(new JSONObject("{\"result\":\"Success\"}"));

        Assert.assertEquals(0, counter.countLogWarning);
        Assert.assertEquals(0, counter.countLogError);
        Assert.assertTrue(TestUtils.getCountlyStore().getHealthCheckCounterState().isEmpty());
    }

    /**
     * A null response (no connection) means the send failed, so counters must be preserved
     * to be retried on the next session rather than silently dropped.
     */
    @Test
    public void nullResponse_keepsCounters() {
        CapturingIRG irg = new CapturingIRG();
        Countly countly = initWith(irg, TestUtils.createBaseConfig());

        // use errors: the null-response branch itself logs a warning, which would skew a warning delta
        HealthCheckCounter counter = countly.moduleHealthCheck.hCounter;
        long baseline = counter.countLogError;
        counter.logError();
        counter.logError();

        irg.hcCallback.callback(null);

        // a failed send must not clear the counters
        Assert.assertEquals(baseline + 2, counter.countLogError);
    }

    /**
     * A malformed response (no "result" field) must not be treated as success, so the
     * counters must be kept rather than cleared.
     */
    @Test
    public void responseWithoutResult_keepsCounters() throws JSONException {
        CapturingIRG irg = new CapturingIRG();
        Countly countly = initWith(irg, TestUtils.createBaseConfig());

        HealthCheckCounter counter = countly.moduleHealthCheck.hCounter;
        counter.logError();

        irg.hcCallback.callback(new JSONObject("{\"foo\":\"bar\"}"));

        Assert.assertEquals(1, counter.countLogError);
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
}
