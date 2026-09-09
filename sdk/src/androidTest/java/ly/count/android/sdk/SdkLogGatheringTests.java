package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static ly.count.android.sdk.ModuleConfiguration.keyRConfig;
import static ly.count.android.sdk.ModuleConfiguration.keyRLogGathering;
import static ly.count.android.sdk.ModuleConfiguration.keyRTimestamp;
import static ly.count.android.sdk.ModuleConfiguration.keyRVersion;
import static ly.count.android.sdk.ModuleConfiguration.logGatheringAllLevels;
import static ly.count.android.sdk.ModuleConfiguration.logGatheringDefaultBatchSize;
import static ly.count.android.sdk.ModuleConfiguration.logGatheringMaxBufferedLines;
import static ly.count.android.sdk.ModuleConfiguration.logGatheringMinBatchSize;

/**
 * Covers the per user SDK log gathering directive, the top level 'lg' key of the configuration response, and the
 * tolerance of the response parsing towards top level keys this SDK does not implement.
 *
 * The directive is parsed by {@link ModuleConfiguration} and acted on by {@link ModuleLog}, which owns the capture
 * because it is alive before any module is.
 *
 * The response shape these tests build is:
 * {"v":2,"t":1786630328494,"c":{ ...the settings... },"lg":{"e":true,"i":"gatherId","l":"ewidv","b":100},"ct":1}
 *
 * 'lg' and 'ct' are siblings of 'c', not members of it. 'ct' is the connection test flag of a server side feature this
 * SDK does not implement, so it stands in here for any top level key that arrives and has to be ignored gracefully.
 */
@RunWith(AndroidJUnit4.class)
public class SdkLogGatheringTests {
    /**
     * A top level key of a server feature this SDK does not implement. Deliberately not a constant of the SDK: the
     * point of these tests is what happens to keys the SDK has never heard of.
     */
    private final static String keyUnsupportedConnectionTest = "ct";
    private final static String keyUnsupportedFuture = "someFutureFeature";

    private CountlyStore countlyStore;
    private Countly countly;

    @Before
    public void setUp() {
        countlyStore = TestUtils.getCountlyStore();
        countlyStore.clear();
        Countly.sharedInstance().halt();
    }

    @After
    public void tearDown() {
        if (countly != null) {
            countly.halt();
            countly = null;
        }
        TestUtils.getCountlyStore().clear();
        Countly.sharedInstance().halt();
    }

    // ================ Top Level Key Tolerance ================

    /**
     * The regression that matters most: a response that carries top level keys beyond 'v', 't' and 'c' must still be
     * accepted, because rejecting it would silently discard every single SBS setting.
     *
     * Verifies that with two unrecognised siblings and the 'lg' directive present:
     * 1. the response is accepted and stored
     * 2. every inner setting of 'c' is applied, tracking flags, intervals, limits and filters alike
     * 3. the SDK never complains that the configuration was ignored
     */
    @Test
    public void serverResponse_withExtraTopLevelKeys_isAcceptedAndInnerSettingsApply() throws JSONException, InterruptedException {
        final List<String> warningsAndErrors = new CopyOnWriteArrayList<>();

        ServerConfigBuilder builder = new ServerConfigBuilder()
            .defaults()
            //move every interesting inner setting off its default, so that "applied" can not be confused with
            //"left alone because the whole response was thrown away"
            .tracking(false)
            .viewTracking(false)
            .customEventTracking(false)
            .contentZone(true)
            .locationTracking(false)
            .refreshContentZone(false)
            .serverConfigUpdateInterval(8)
            .requestQueueSize(2000)
            .eventQueueSize(200)
            .sessionUpdateInterval(120)
            .contentZoneInterval(60)
            .dropOldRequestTime(1)
            .keyLengthLimit(89)
            .valueSizeLimit(43)
            .segmentationValuesLimit(25)
            .breadcrumbLimit(90)
            .traceLengthLimit(78)
            .traceLinesLimit(89)
            .userPropertyCacheLimit(67)
            //and now the siblings of 'c'
            .logGatheringOn("gather_id_1", "ew", 25)
            .topLevelKey(keyUnsupportedConnectionTest, 1)
            .topLevelKey(keyUnsupportedFuture, "a value this SDK has never heard of");

        CountlyConfig config = TestUtils.createBaseConfig().setLoggingEnabled(false);
        config.immediateRequestGenerator = ModuleConfigurationTests.createIRGForSpecificResponse(builder.build());
        config.setLogListener(collectWarningsAndErrors(warningsAndErrors));

        countly = new Countly().init(config);
        Thread.sleep(2000); // simulate sdk initialization delay

        Assert.assertNotNull(countlyStore.getServerConfig());
        builder.validateAgainst(countly);

        //the directive that travelled next to the unrecognised keys was parsed too
        assertGathering("gather_id_1", "ew", 25);

        assertNoComplaintAbout(warningsAndErrors, "Config will be ignored", "expected number of keys",
            "[" + keyUnsupportedConnectionTest + "]", "[" + keyUnsupportedFuture + "]");
    }

    /**
     * An unsupported top level key is dropped quietly.
     *
     * Verifies that:
     * 1. it is not written to storage, while 'v', 't', 'c' and 'lg' are
     * 2. it is not warned about, it is not an error
     * 3. it does not stop the 'lg' directive of the very same response from being parsed and applied
     */
    @Test
    public void serverResponse_unsupportedTopLevelKey_isNotStoredAndDoesNotBlockLogGathering() throws JSONException {
        final List<String> warningsAndErrors = new CopyOnWriteArrayList<>();

        String response = new ServerConfigBuilder()
            .defaults()
            .logGatheringOn("gather_id_2", "ewi", 30)
            .topLevelKey(keyUnsupportedConnectionTest, 1)
            .build();

        CountlyConfig config = TestUtils.createBaseConfig().setLoggingEnabled(false);
        config.immediateRequestGenerator = ModuleConfigurationTests.createIRGForSpecificResponse(response);
        config.setLogListener(collectWarningsAndErrors(warningsAndErrors));

        countly = new Countly().init(config);

        JSONObject stored = storedConfig();
        Assert.assertTrue(stored.has(keyRVersion));
        Assert.assertTrue(stored.has(keyRTimestamp));
        Assert.assertTrue(stored.has(keyRConfig));
        Assert.assertTrue(stored.has(keyRLogGathering));
        Assert.assertFalse(stored.has(keyUnsupportedConnectionTest));
        Assert.assertEquals(4, stored.length()); // and nothing else was kept either

        assertNoComplaintAbout(warningsAndErrors, "[" + keyUnsupportedConnectionTest + "]", "Config will be ignored");

        assertGathering("gather_id_2", "ewi", 30);
    }

    // ================ Log Gathering Directive Parsing ================

    /**
     * The happy path of the directive and the way back out of it.
     *
     * Verifies that:
     * 1. "lg":{"e":true,"i":...,"l":...,"b":...} turns gathering on with exactly those values, in the configuration
     * module and in the logger that reads them from it
     * 2. the directive is stored as it arrived, as part of the configuration. Note a restart deliberately does NOT
     * act on it: only a live server response decides gathering, so a stored directive is inert and is asserted here
     * purely to show the merge kept the top level key rather than dropping it
     * 3. "lg":{"e":false} turns it back off and resets the id, the levels and the batch size, so no stale gather id
     * can be echoed back to the server
     */
    @Test
    public void logGatheringDirective_enabledThenDisabled_isAppliedBothWays() throws JSONException {
        final String[] response = { new ServerConfigBuilder().defaults().logGatheringOn("gather_id_3", "ewd", 40).build() };

        countly = initWithMutableServerResponse(response);

        assertGathering("gather_id_3", "ewd", 40);

        JSONObject storedDirective = storedConfig().getJSONObject(keyRLogGathering);
        Assert.assertTrue(storedDirective.getBoolean("e"));
        Assert.assertEquals("gather_id_3", storedDirective.getString("i"));
        Assert.assertEquals("ewd", storedDirective.getString("l"));
        Assert.assertEquals(40, storedDirective.getInt("b"));

        pushServerResponse(response, new ServerConfigBuilder().defaults().logGatheringOff());

        assertDecidedAgainstGathering();
        Assert.assertFalse(storedConfig().getJSONObject(keyRLogGathering).getBoolean("e"));
    }

    /**
     * Every shape of the directive that can not be acted on has to end up as "not gathering" rather than as a
     * half armed gather. Without a gather id the server rejects every uploaded batch, so an enabling directive
     * without a usable one is worse than no directive at all.
     *
     * Verifies, with a working directive applied in between each of them so that the assertions can not pass
     * vacuously, that gathering is not enabled by:
     * 1. "e":true with no "i" at all
     * 2. "e":true with a blank "i"
     * 3. "e":true with an "i" that is not a string
     * 4. "e" that is not a boolean, even next to a perfectly good id
     */
    @Test
    public void logGatheringDirective_withoutUsableGatherId_doesNotEnableGathering() throws JSONException {
        final String[] response = { new ServerConfigBuilder().defaults().logGatheringOn("gather_id_4", "ew", 20).build() };

        countly = initWithMutableServerResponse(response);
        assertGathering("gather_id_4", "ew", 20);

        pushServerResponse(response, new ServerConfigBuilder().defaults().logGatheringDirective(true, null, "ew", 20));
        assertDecidedAgainstGathering();

        pushServerResponse(response, new ServerConfigBuilder().defaults().logGatheringOn("gather_id_4", "ew", 20));
        assertGathering("gather_id_4", "ew", 20);

        pushServerResponse(response, new ServerConfigBuilder().defaults().logGatheringDirective(true, "   ", "ew", 20));
        assertDecidedAgainstGathering();

        pushServerResponse(response, new ServerConfigBuilder().defaults().logGatheringOn("gather_id_4", "ew", 20));
        assertGathering("gather_id_4", "ew", 20);

        pushServerResponse(response, new ServerConfigBuilder().defaults().logGatheringDirective(true, 42, "ew", 20));
        assertDecidedAgainstGathering();

        pushServerResponse(response, new ServerConfigBuilder().defaults().logGatheringOn("gather_id_4", "ew", 20));
        assertGathering("gather_id_4", "ew", 20);

        pushServerResponse(response, new ServerConfigBuilder().defaults().logGatheringDirective("true", "gather_id_4", "ew", 20));
        assertDecidedAgainstGathering();
    }

    /**
     * The levels and the batch size are sanitized rather than trusted, because they drive a network path.
     *
     * Verifies that:
     * 1. a junk, missing or non string "l" falls back to every level
     * 2. the level characters are lower cased, deduplicated and kept in the order the server sent them
     * 3. characters this SDK does not know are dropped while the usable ones survive
     * 4. "b" is clamped into [10, 500] and falls back to 100 when it is missing or not a number
     */
    @Test
    public void logGatheringDirective_levelsAndBatchSize_areSanitized() throws JSONException {
        final String[] response = { new ServerConfigBuilder().defaults().logGatheringOn("gather_id_5", "xyz!", 100).build() };

        countly = initWithMutableServerResponse(response);
        assertGathering("gather_id_5", logGatheringAllLevels, 100); // nothing usable in the levels, so all of them

        //every step below moves the levels away from the previous ones, so no assertion can pass just because the
        //state happened to be right already

        pushServerResponse(response, new ServerConfigBuilder().defaults().logGatheringOn("gather_id_5", "ew", 100));
        assertGathering("gather_id_5", "ew", 100);

        pushServerResponse(response, new ServerConfigBuilder().defaults().logGatheringDirective(true, "gather_id_5", null, 100));
        assertGathering("gather_id_5", logGatheringAllLevels, 100); // no levels at all, so all of them

        pushServerResponse(response, new ServerConfigBuilder().defaults().logGatheringOn("gather_id_5", "ew", 100));
        assertGathering("gather_id_5", "ew", 100);

        pushServerResponse(response, new ServerConfigBuilder().defaults().logGatheringDirective(true, "gather_id_5", 5, 100));
        assertGathering("gather_id_5", logGatheringAllLevels, 100); // levels that are not even a string

        pushServerResponse(response, new ServerConfigBuilder().defaults().logGatheringOn("gather_id_5", "EWID", 100));
        assertGathering("gather_id_5", "ewid", 100); // lower cased

        pushServerResponse(response, new ServerConfigBuilder().defaults().logGatheringOn("gather_id_5", "wweeww", 100));
        assertGathering("gather_id_5", "we", 100); // deduplicated, in the order the server sent them

        pushServerResponse(response, new ServerConfigBuilder().defaults().logGatheringOn("gather_id_5", "vq d", 100));
        assertGathering("gather_id_5", "vd", 100); // the unknown characters go, the known ones stay

        pushServerResponse(response, new ServerConfigBuilder().defaults().logGatheringOn("gather_id_5", "ew", 1));
        assertGathering("gather_id_5", "ew", logGatheringMinBatchSize); // clamped up

        pushServerResponse(response, new ServerConfigBuilder().defaults().logGatheringOn("gather_id_5", "ew", 9999));
        assertGathering("gather_id_5", "ew", logGatheringMaxBufferedLines); // clamped down

        pushServerResponse(response, new ServerConfigBuilder().defaults().logGatheringOn("gather_id_5", "ew", 250));
        assertGathering("gather_id_5", "ew", 250); // a value inside the range is used as it is

        pushServerResponse(response, new ServerConfigBuilder().defaults().logGatheringDirective(true, "gather_id_5", "ew", "250"));
        assertGathering("gather_id_5", "ew", logGatheringDefaultBatchSize); // a batch size that is not a number

        pushServerResponse(response, new ServerConfigBuilder().defaults().logGatheringOn("gather_id_5", "ew", 250));
        assertGathering("gather_id_5", "ew", 250);

        pushServerResponse(response, new ServerConfigBuilder().defaults().logGatheringDirective(true, "gather_id_5", "ew", null));
        assertGathering("gather_id_5", "ew", logGatheringDefaultBatchSize); // no batch size at all
    }

    // ================ Configuration Merge ================

    /**
     * The top level keys are carried across a configuration merge by hand, one by one, so a key that is not on that
     * list is lost. 'lg' has to be on it.
     *
     * Verifies that:
     * 1. a later response that carries a small inner 'c' merges into the stored one instead of replacing it
     * 2. the 'lg' directive survives that merge
     * 3. a later response that carries no 'lg' at all does NOT leave the previous directive behind, because an
     * outdated gather id would keep being echoed to a server that has forgotten about it
     */
    @Test
    public void logGathering_survivesConfigMerge_andIsDroppedWhenTheNextResponseOmitsIt() throws JSONException {
        final String[] response = {
            new ServerConfigBuilder()
                .defaults()
                .requestQueueSize(1500)
                .logGatheringOn("gather_id_6", "ew", 25)
                .topLevelKey(keyUnsupportedConnectionTest, 1)
                .build()
        };

        countly = initWithMutableServerResponse(response);

        assertGathering("gather_id_6", "ew", 25);
        Assert.assertEquals(1500, countly.moduleConfiguration.currentVMaxRequestQueueSize);

        //a response with a single inner setting in it, and the same directive next to it
        pushServerResponse(response, new ServerConfigBuilder().tracking(false).logGatheringOn("gather_id_6", "ew", 25));

        Assert.assertFalse(countly.moduleConfiguration.getTrackingEnabled());
        Assert.assertEquals(1500, countly.moduleConfiguration.currentVMaxRequestQueueSize); // merged, not replaced
        assertGathering("gather_id_6", "ew", 25); // and the directive came along with the merge
        Assert.assertTrue(storedConfig().has(keyRLogGathering));

        //the same again, only this time the server sends no directive at all
        pushServerResponse(response, new ServerConfigBuilder().tracking(true).withoutLogGathering());

        Assert.assertTrue(countly.moduleConfiguration.getTrackingEnabled());
        Assert.assertEquals(1500, countly.moduleConfiguration.currentVMaxRequestQueueSize); // still merged
        Assert.assertFalse(storedConfig().has(keyRLogGathering)); // the directive is gone, not remembered
        assertDecidedAgainstGathering();
    }

    // ================ The Tri State ================

    /**
     * A stored directive decides NOTHING, even when it arms gathering. Only a live server response does. Without this
     * a relaunch would resume a gather the server may already have stopped, and would upload lines belonging to a run
     * the operator never asked about: the rule is that a run only ever uploads the lines it gathered itself.
     *
     * Verifies that with an ARMED directive already in the stored configuration and a server that does not answer:
     * 1. the state is undecided rather than gathering, and the stored gather id is not adopted
     * 2. capture is still running speculatively and holding the init lines, so a later server directive can adopt them
     */
    @Test
    public void logGathering_storedConfigWithArmedDirective_isIgnoredAndStaysUndecided() throws JSONException {
        countlyStore.setServerConfig(new ServerConfigBuilder().defaults().logGatheringOn("stored_gather_id", "ewidv", 40).build());

        CountlyConfig config = TestUtils.createBaseConfig().setLoggingEnabled(false);
        config.immediateRequestGenerator = createPendingIRG(); // the fetch is still in flight, the server has not answered yet
        countly = new Countly().init(config);

        Assert.assertEquals(ModuleConfiguration.LogGatheringState.UNDECIDED, countly.moduleConfiguration.getLogGatheringState());
        Assert.assertNull(countly.moduleConfiguration.getLogGatheringId()); // the stored id must not leak through
        Assert.assertEquals(logGatheringDefaultBatchSize, countly.moduleConfiguration.getLogGatheringBatchSize());

        Assert.assertEquals(ModuleConfiguration.LogGatheringState.UNDECIDED, countly.L.logGatheringState);
        Assert.assertFalse(countly.L.isGatheringLogs());
        Assert.assertTrue(countly.L.isCapturingLogs());
        Assert.assertTrue(countly.L.heldLogLineCount() > 0);
    }

    /**
     * An absent directive means two different things and they must not be conflated. This is the stored side of it:
     * nothing has decided anything yet, so the SDK keeps capturing and waits for the server.
     *
     * Verifies that with a stored configuration that carries no 'lg' and a server that does not answer:
     * 1. the state is undecided, not "off"
     * 2. gathering is not on, and no gather id, level set or batch size is invented
     * 3. the lines logged during init are being held, which is the entire reason the undecided state exists
     */
    @Test
    public void logGathering_storedConfigWithoutDirective_staysUndecidedAndKeepsCapturing() throws JSONException {
        countlyStore.setServerConfig(new ServerConfigBuilder().defaults().build());

        CountlyConfig config = TestUtils.createBaseConfig().setLoggingEnabled(false);
        config.immediateRequestGenerator = createPendingIRG(); // the fetch is still in flight, the server has not answered yet
        countly = new Countly().init(config);

        Assert.assertEquals(ModuleConfiguration.LogGatheringState.UNDECIDED, countly.moduleConfiguration.currentVLogGatheringState);
        Assert.assertEquals(ModuleConfiguration.LogGatheringState.UNDECIDED, countly.moduleConfiguration.getLogGatheringState());
        Assert.assertNull(countly.moduleConfiguration.getLogGatheringId());
        Assert.assertEquals(logGatheringAllLevels, countly.moduleConfiguration.getLogGatheringLevels());
        Assert.assertEquals(logGatheringDefaultBatchSize, countly.moduleConfiguration.getLogGatheringBatchSize());

        Assert.assertEquals(ModuleConfiguration.LogGatheringState.UNDECIDED, countly.L.logGatheringState);
        Assert.assertFalse(countly.L.isGatheringLogs());
        Assert.assertTrue(countly.L.isCapturingLogs()); // still capturing, speculatively
        Assert.assertTrue(countly.L.heldLogLineCount() > 0); // and holding what init logged
    }

    /**
     * A stored configuration decides nothing, not even when it carries an armed directive: the only lines ever uploaded
     * are the ones gathered during this run, so every run starts undecided, captures its init lines speculatively and
     * waits for the live server answer. The stored directive itself is carried across the reload untouched.
     */
    @Test
    public void logGathering_storedConfigWithDirective_staysUndecidedUntilTheServerAnswers() throws JSONException {
        countlyStore.setServerConfig(new ServerConfigBuilder().defaults().logGatheringOn("gather_id_7", "ewi", 35).build());

        CountlyConfig config = TestUtils.createBaseConfig().setLoggingEnabled(false);
        config.immediateRequestGenerator = createPendingIRG(); // the fetch is still in flight, the server has not answered yet
        countly = new Countly().init(config);

        Assert.assertEquals(ModuleConfiguration.LogGatheringState.UNDECIDED, countly.moduleConfiguration.currentVLogGatheringState);
        Assert.assertNull(countly.moduleConfiguration.getLogGatheringId());
        Assert.assertFalse(countly.L.isGatheringLogs());
        Assert.assertTrue(countly.L.isCapturingLogs()); // speculatively, waiting for the server
        Assert.assertTrue(countly.L.heldLogLineCount() > 0); // holding what init logged
        Assert.assertTrue(storedConfig().has(keyRLogGathering)); // and the stored directive survived the reload
    }

    /**
     * A fetch that fails is a decision too: no directive can arrive this run, and nothing else would ever tell the
     * logger to let go of the lines it is holding. This matches the iOS SDK, which decides off on a failed fetch.
     */
    @Test
    public void logGathering_failedFetch_decidesGatheringOff() {
        CountlyConfig config = TestUtils.createBaseConfig().setLoggingEnabled(false);
        config.immediateRequestGenerator = ModuleConfigurationTests.createIRGForSpecificResponse(null); // the fetch fails
        countly = new Countly().init(config);

        Assert.assertEquals(ModuleConfiguration.LogGatheringState.NOT_GATHERING, countly.moduleConfiguration.getLogGatheringState());
        assertDecidedAgainstGathering();
        Assert.assertEquals(0, countly.L.heldLogLineCount());
        Assert.assertFalse(countly.L.isCapturingLogs());
    }

    /**
     * The server side of the absent directive: a response that carries no 'lg' is the server saying no, so the state
     * has to become decided and everything held speculatively has to go.
     */
    @Test
    public void logGathering_serverResponseWithoutDirective_decidesGatheringOff() throws JSONException {
        countly = initWithServerResponse(new ServerConfigBuilder().defaults().build());

        Assert.assertNotNull(countlyStore.getServerConfig());
        Assert.assertEquals(ModuleConfiguration.LogGatheringState.NOT_GATHERING, countly.moduleConfiguration.currentVLogGatheringState);
        assertDecidedAgainstGathering();
        Assert.assertEquals(0, countly.L.heldLogLineCount()); // the speculative buffer was dropped
    }

    // ================ Helper Methods ================

    /**
     * Asserts the whole directive twice over: as the configuration module parsed it, and as the logger applied it.
     * They are separate mirrors of the same directive, and only the logger's one decides what is captured.
     */
    private void assertGathering(String expectedId, String expectedLevels, int expectedBatchSize) {
        Assert.assertEquals(ModuleConfiguration.LogGatheringState.GATHERING, countly.moduleConfiguration.currentVLogGatheringState);
        Assert.assertEquals(ModuleConfiguration.LogGatheringState.GATHERING, countly.moduleConfiguration.getLogGatheringState());
        Assert.assertEquals(expectedId, countly.moduleConfiguration.getLogGatheringId());
        Assert.assertEquals(expectedLevels, countly.moduleConfiguration.getLogGatheringLevels());
        Assert.assertEquals(expectedBatchSize, countly.moduleConfiguration.getLogGatheringBatchSize());

        Assert.assertEquals(ModuleConfiguration.LogGatheringState.GATHERING, countly.L.logGatheringState);
        Assert.assertTrue(countly.L.isGatheringLogs());
        Assert.assertTrue(countly.L.isCapturingLogs());
        Assert.assertEquals(expectedId, countly.L.logGatherId);
        Assert.assertEquals(expectedLevels, countly.L.logGatherLevels);
        Assert.assertEquals(expectedBatchSize, countly.L.logGatherBatchSize);
    }

    /**
     * Asserts that a directive was seen and it decided against gathering: nothing is gathered, nothing is captured
     * any more, and no part of a previous gather is left behind.
     */
    private void assertDecidedAgainstGathering() {
        Assert.assertEquals(ModuleConfiguration.LogGatheringState.NOT_GATHERING, countly.moduleConfiguration.currentVLogGatheringState);
        Assert.assertEquals(ModuleConfiguration.LogGatheringState.NOT_GATHERING, countly.moduleConfiguration.getLogGatheringState());
        Assert.assertNull(countly.moduleConfiguration.getLogGatheringId());
        Assert.assertEquals(logGatheringAllLevels, countly.moduleConfiguration.getLogGatheringLevels());
        Assert.assertEquals(logGatheringDefaultBatchSize, countly.moduleConfiguration.getLogGatheringBatchSize());

        Assert.assertEquals(ModuleConfiguration.LogGatheringState.NOT_GATHERING, countly.L.logGatheringState);
        Assert.assertFalse(countly.L.isGatheringLogs());
        Assert.assertFalse(countly.L.isCapturingLogs());
        Assert.assertNull(countly.L.logGatherId);
    }

    private Countly initWithServerResponse(String response) {
        CountlyConfig config = TestUtils.createBaseConfig().setLoggingEnabled(false);
        config.immediateRequestGenerator = ModuleConfigurationTests.createIRGForSpecificResponse(response);
        return new Countly().init(config);
    }

    private Countly initWithMutableServerResponse(String[] responseHolder) {
        CountlyConfig config = TestUtils.createBaseConfig().setLoggingEnabled(false);
        config.immediateRequestGenerator = createMutableIRG(responseHolder);
        return new Countly().init(config);
    }

    /**
     * Hands the SDK the next configuration response and makes it fetch, so that several responses can be pushed
     * through one instance and the merge of one into the next can be observed.
     */
    private void pushServerResponse(String[] responseHolder, ServerConfigBuilder builder) throws JSONException {
        responseHolder[0] = builder.build();
        countly.moduleConfiguration.fetchConfigFromServer(countly.config_);
    }

    /**
     * Immediate request generator that answers with whatever is in the holder at the moment of the request, so the
     * response can be swapped between fetches
     */
    /** An immediate request that never completes: the server has not answered, so nothing is decided yet. */
    private static ImmediateRequestGenerator createPendingIRG() {
        return new ImmediateRequestGenerator() {
            @Override public ImmediateRequestI CreateImmediateRequestMaker() {
                return (requestData, customEndpoint, cp, requestShouldBeDelayed, networkingIsEnabled, callback, log) -> {
                    //never calls back
                };
            }

            @Override public ImmediateRequestI CreatePreflightRequestMaker() {
                return null;
            }
        };
    }

    private static ImmediateRequestGenerator createMutableIRG(final String[] responseHolder) {
        return new ImmediateRequestGenerator() {
            @Override public ImmediateRequestI CreateImmediateRequestMaker() {
                return new ImmediateRequestI() {
                    @Override
                    public void doWork(String requestData, String customEndpoint, ConnectionProcessor cp, boolean requestShouldBeDelayed, boolean networkingIsEnabled, ImmediateRequestMaker.InternalImmediateRequestCallback callback, ModuleLog log) {
                        String response = responseHolder[0];
                        if (response == null) {
                            callback.callback(null);
                            return;
                        }

                        try {
                            callback.callback(new JSONObject(response));
                        } catch (JSONException e) {
                            throw new IllegalArgumentException("the test pushed a response that is not valid JSON: " + response, e);
                        }
                    }
                };
            }

            @Override public ImmediateRequestI CreatePreflightRequestMaker() {
                return null;
            }
        };
    }

    private JSONObject storedConfig() throws JSONException {
        String stored = countlyStore.getServerConfig();
        Assert.assertNotNull(stored);
        return new JSONObject(stored);
    }

    private ModuleLog.LogCallback collectWarningsAndErrors(final List<String> target) {
        return new ModuleLog.LogCallback() {
            @Override public void LogHappened(String logMessage, ModuleLog.LogLevel logLevel) {
                if (logLevel == ModuleLog.LogLevel.Warning || logLevel == ModuleLog.LogLevel.Error) {
                    target.add(logMessage);
                }
            }
        };
    }

    /**
     * Ignoring a top level key has to be quiet. A warning or an error about one would tell every integrator that
     * something is wrong with a response that is perfectly fine.
     */
    private void assertNoComplaintAbout(List<String> warningsAndErrors, String... fragments) {
        for (String logMessage : new ArrayList<>(warningsAndErrors)) {
            for (String fragment : fragments) {
                Assert.assertFalse("the SDK complained about something it should have tolerated quietly, fragment:[" + fragment + "], log line:[" + logMessage + "]",
                    logMessage.contains(fragment));
            }
        }
    }
}
