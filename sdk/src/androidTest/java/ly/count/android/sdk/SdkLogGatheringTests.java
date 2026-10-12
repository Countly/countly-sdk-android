package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.json.JSONArray;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    private final static String consentGatherId = "consent_gather_id";

    //values that are user data, chosen so that finding one in an uploaded batch can not be a coincidence
    private final static String secretEventKey = "cly_secret_event_key";
    private final static String secretSegmentationValue = "cly_secret_segmentation_value";
    private final static String secretViewName = "cly_secret_view_name";
    private final static String secretUserProperty = "cly_secret_user_property";

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

    // ================ Consent ================

    /**
     * A gathered line needs the consent of the feature it comes from, decided when it is logged. For a user who
     * consented only to sessions, the session lines can go, while the ones quoting event keys, segmentation and view
     * names, and the ones that can quote any feature's data, have to stay on the device.
     *
     * Verifies that with gathering armed, consent required and only the sessions consent given:
     * 1. the session lines are uploaded
     * 2. no uploaded line quotes the event key, the segmentation or the view name, and no consent line is uploaded
     * 3. the local developer log still quotes the event key and the view name, since only the gathered copy is withheld
     * 4. the held lines are not dropped: once the events and users consent are given, the event lines and the consent
     * lines are uploaded, while the view lines keep waiting for the views consent
     */
    @Test
    public void logGathering_onlySessionsConsent_uploadsOnlyTheLinesItCovers() throws JSONException, InterruptedException {
        final List<String> localLogLines = new CopyOnWriteArrayList<>();
        final List<String> uploadedLines = new CopyOnWriteArrayList<>();

        countly = initGathering(new String[] { Countly.CountlyFeatureNames.sessions }, acceptingRequestQueue(uploadedLines), (logMessage, logLevel) -> localLogLines.add(logMessage));
        assertGathering(consentGatherId, logGatheringAllLevels, logGatheringMinBatchSize);

        recordLinesCarryingUserData();
        uploadEverythingGathered();

        assertSomeLineContains(uploadedLines, "[ModuleSessions]");
        assertNoLineContains(uploadedLines, secretEventKey, secretSegmentationValue, secretViewName, "[ModuleConsent]");
        assertSomeLineContains(localLogLines, secretEventKey);
        assertSomeLineContains(localLogLines, secretViewName);

        countly.consent().giveConsent(new String[] { Countly.CountlyFeatureNames.events, Countly.CountlyFeatureNames.users });
        uploadEverythingGathered();

        assertSomeLineContains(uploadedLines, secretEventKey);
        assertSomeLineContains(uploadedLines, secretSegmentationValue);
        assertSomeLineContains(uploadedLines, "[ModuleConsent]");
        assertNoLineContains(uploadedLines, secretViewName);
    }

    /**
     * The events consent lets the event lines go and nothing else: the user profile lines wait for the users consent,
     * the view lines for the views consent, and the lines that can quote anything for both the events and the users
     * consent. The view event the events module logs counts as a view line.
     *
     * Verifies that with gathering armed, consent required and only the events consent given, the event key is uploaded
     * while no user profile line, consent line or view name is.
     */
    @Test
    public void logGathering_onlyEventsConsent_uploadsOnlyTheEventLines() throws JSONException, InterruptedException {
        final List<String> uploadedLines = new CopyOnWriteArrayList<>();

        countly = initGathering(new String[] { Countly.CountlyFeatureNames.events }, acceptingRequestQueue(uploadedLines), null);
        assertGathering(consentGatherId, logGatheringAllLevels, logGatheringMinBatchSize);

        recordLinesCarryingUserData();
        uploadEverythingGathered();

        assertSomeLineContains(uploadedLines, secretEventKey);
        assertNoLineContains(uploadedLines, "[UserProfile]", "[ModuleConsent]", secretViewName);
    }

    /**
     * The users consent lets the user profile lines go and nothing else.
     *
     * Verifies that with gathering armed, consent required and only the users consent given, the user profile lines are
     * uploaded while no line quoting the event key or the segmentation, and no consent line, is.
     */
    @Test
    public void logGathering_onlyUsersConsent_uploadsOnlyTheUserProfileLines() throws JSONException, InterruptedException {
        final List<String> uploadedLines = new CopyOnWriteArrayList<>();

        countly = initGathering(new String[] { Countly.CountlyFeatureNames.users }, acceptingRequestQueue(uploadedLines), null);
        assertGathering(consentGatherId, logGatheringAllLevels, logGatheringMinBatchSize);

        recordLinesCarryingUserData();
        uploadEverythingGathered();

        assertSomeLineContains(uploadedLines, "[UserProfile]");
        assertNoLineContains(uploadedLines, secretEventKey, secretSegmentationValue, "[ModuleConsent]");
    }

    /**
     * With both the events and the users consent, the lines no single feature can be found for go as well, while a view
     * line still needs the views consent.
     *
     * Verifies that with gathering armed, consent required and only the events and users consent given, the event key
     * and the consent lines are uploaded while the view name is not.
     */
    @Test
    public void logGathering_eventsAndUsersConsent_uploadsAllButTheViewLines() throws JSONException, InterruptedException {
        final List<String> uploadedLines = new CopyOnWriteArrayList<>();

        countly = initGathering(new String[] { Countly.CountlyFeatureNames.events, Countly.CountlyFeatureNames.users }, acceptingRequestQueue(uploadedLines), null);
        assertGathering(consentGatherId, logGatheringAllLevels, logGatheringMinBatchSize);

        recordLinesCarryingUserData();
        uploadEverythingGathered();

        assertSomeLineContains(uploadedLines, secretEventKey);
        assertSomeLineContains(uploadedLines, "[ModuleConsent]");
        assertNoLineContains(uploadedLines, secretViewName);
    }

    /**
     * An integration that does not require consent counts as having consented to everything, so the consent has to be
     * invisible to it.
     *
     * Verifies that with gathering armed and consent not required at all, the gathered lines are uploaded, the event key
     * and the view name among them.
     */
    @Test
    public void logGathering_consentNotRequired_uploadsTheGatheredLines() throws JSONException, InterruptedException {
        final List<String> uploadedLines = new CopyOnWriteArrayList<>();

        countly = initGathering(null, acceptingRequestQueue(uploadedLines), null);
        assertGathering(consentGatherId, logGatheringAllLevels, logGatheringMinBatchSize);

        recordLinesCarryingUserData();
        uploadEverythingGathered();

        assertSomeLineContains(uploadedLines, secretEventKey);
        assertSomeLineContains(uploadedLines, secretViewName);
    }

    /**
     * The server can lift the consent requirement, and from then on every held line is consented.
     *
     * Verifies that with gathering armed, consent required and only the sessions consent given, the held event and view
     * lines are uploaded once a later server response no longer requires consent.
     */
    @Test
    public void logGathering_consentNoLongerRequired_releasesTheHeldLines() throws JSONException, InterruptedException {
        final List<String> uploadedLines = new CopyOnWriteArrayList<>();
        final String[] response = { gatheringResponse(true).build() };

        countly = initGathering(new String[] { Countly.CountlyFeatureNames.sessions }, response, acceptingRequestQueue(uploadedLines), null);
        assertGathering(consentGatherId, logGatheringAllLevels, logGatheringMinBatchSize);

        recordLinesCarryingUserData();
        uploadEverythingGathered();
        assertNoLineContains(uploadedLines, secretEventKey, secretViewName);

        pushServerResponse(response, gatheringResponse(false));
        uploadEverythingGathered();

        assertSomeLineContains(uploadedLines, secretEventKey);
        assertSomeLineContains(uploadedLines, secretViewName);
    }

    /**
     * A gather belongs to the user it was armed for. A device ID change without merge starts a new user, so what is
     * gathered goes out right away under the old device ID and gathering stops, while a merge keeps it going.
     *
     * Verifies that:
     * 1. a change with merge leaves gathering on
     * 2. a change without merge uploads the gathered lines while the old device ID is still the current one
     * 3. after it gathering is off in the configuration module and in the logger, and nothing more is uploaded
     */
    @Test
    public void logGathering_deviceIdChangeWithoutMerge_flushesUnderTheOldIdAndStops() throws JSONException, InterruptedException {
        final List<String> uploadedLines = new CopyOnWriteArrayList<>();
        final List<String> batchDeviceIds = new CopyOnWriteArrayList<>();

        //a batch larger than what is recorded below, so only the change itself can make those lines go out
        String response = gatheringResponse(false).logGatheringOn(consentGatherId, logGatheringAllLevels, logGatheringDefaultBatchSize).build();
        countly = initGathering(null, new String[] { response }, acceptingRequestQueue(uploadedLines, batchDeviceIds), null);
        assertGathering(consentGatherId, logGatheringAllLevels, logGatheringDefaultBatchSize);

        countly.deviceId().changeWithMerge("cly_merged_device_id");
        assertGathering(consentGatherId, logGatheringAllLevels, logGatheringDefaultBatchSize);
        uploadEverythingGathered();
        uploadedLines.clear();
        batchDeviceIds.clear();

        recordLinesCarryingUserData();
        countly.deviceId().changeWithoutMerge("cly_new_device_id");
        final int uploadedByTheChange = uploadedLines.size();
        Thread.sleep(1000);

        assertSomeLineContains(uploadedLines, secretEventKey);
        Assert.assertFalse(batchDeviceIds.isEmpty());
        for (String batchDeviceId : batchDeviceIds) {
            Assert.assertEquals("cly_merged_device_id", batchDeviceId);
        }
        Assert.assertEquals(uploadedByTheChange, uploadedLines.size());
        assertDecidedAgainstGathering();
        Assert.assertEquals(0, countly.L.heldLogLineCount());
    }

    /**
     * Lines waiting for their consent belong to the user the gather was armed for as well, so a device ID change without
     * merge drops them instead of leaving them for the consent of the next user.
     *
     * Verifies that with gathering armed, consent required and only the sessions consent given:
     * 1. the session lines go out under the old device ID
     * 2. after a change without merge nothing waits for consent any more and gathering is off
     * 3. the events and users consent of the next user uploads none of the event lines
     */
    @Test
    public void logGathering_deviceIdChangeWithoutMerge_dropsTheLinesAwaitingConsent() throws JSONException, InterruptedException {
        final List<String> uploadedLines = new CopyOnWriteArrayList<>();
        final List<String> batchDeviceIds = new CopyOnWriteArrayList<>();

        countly = initGathering(new String[] { Countly.CountlyFeatureNames.sessions }, new String[] { gatheringResponse(true).build() }, acceptingRequestQueue(uploadedLines, batchDeviceIds), null);
        assertGathering(consentGatherId, logGatheringAllLevels, logGatheringMinBatchSize);

        recordLinesCarryingUserData();
        Assert.assertTrue(countly.L.awaitingConsentLineCount() > 0);

        countly.deviceId().changeWithoutMerge("cly_new_device_id");
        Assert.assertEquals(0, countly.L.awaitingConsentLineCount());
        assertDecidedAgainstGathering();

        countly.consent().giveConsent(new String[] { Countly.CountlyFeatureNames.events, Countly.CountlyFeatureNames.users });
        Thread.sleep(1000);

        assertSomeLineContains(uploadedLines, "[ModuleSessions]");
        assertNoLineContains(uploadedLines, secretEventKey, secretSegmentationValue);
        for (String batchDeviceId : batchDeviceIds) {
            Assert.assertNotEquals("cly_new_device_id", batchDeviceId);
        }
    }

    /**
     * Lines captured while nothing is decided are the current user's too, so a device ID change without merge drops
     * them, leaving nothing of the previous user for a directive that arrives later.
     *
     * Verifies that with the configuration fetch still in flight, a change without merge decides against gathering and
     * leaves no line in the buffer or waiting for consent.
     */
    @Test
    public void logGathering_deviceIdChangeWithoutMergeWhileUndecided_dropsTheSpeculativeLines() {
        CountlyConfig config = TestUtils.createConsentCountlyConfig(false, null, null, acceptingRequestQueue(new CopyOnWriteArrayList<>())).setLoggingEnabled(false);
        config.immediateRequestGenerator = createPendingIRG(); // the fetch is still in flight, the server has not answered yet
        countly = new Countly().init(config);
        Assert.assertEquals(ModuleConfiguration.LogGatheringState.UNDECIDED, countly.L.logGatheringState);
        Assert.assertTrue(countly.L.heldLogLineCount() > 0);

        countly.deviceId().changeWithoutMerge("cly_new_device_id");

        assertDecidedAgainstGathering();
        Assert.assertEquals(0, countly.L.heldLogLineCount());
        Assert.assertEquals(0, countly.L.awaitingConsentLineCount());
    }

    /**
     * A gather is armed for the device the configuration was requested for. An answer that only arrives after a device
     * ID change without merge must not arm it for the next user, while the rest of the answer still applies.
     *
     * Verifies that with the init fetch still in flight, a change without merge followed by an answer that arms gathering
     * leaves gathering off, and the other settings of that answer are applied.
     */
    @Test
    public void logGathering_answerRequestedBeforeDeviceIdChange_doesNotArmTheNextUser() throws JSONException {
        final List<ImmediateRequestMaker.InternalImmediateRequestCallback> heldAnswers = new CopyOnWriteArrayList<>();
        CountlyConfig config = TestUtils.createConsentCountlyConfig(false, null, null, acceptingRequestQueue(new CopyOnWriteArrayList<>())).setLoggingEnabled(false);
        config.immediateRequestGenerator = createHeldIRG(heldAnswers);
        countly = new Countly().init(config);
        Assert.assertEquals(1, heldAnswers.size());

        countly.deviceId().changeWithoutMerge("cly_new_device_id");
        heldAnswers.get(0).callback(new JSONObject(gatheringResponse(false).requestQueueSize(1500).build()));

        assertDecidedAgainstGathering();
        Assert.assertEquals(1500, countly.moduleConfiguration.currentVMaxRequestQueueSize);
    }

    /**
     * A delivery can not send anything while tracking is off, so a full buffer must not make every further captured
     * line queue one, each of which would also log why it sent nothing. Once a server response turns tracking back on,
     * the held batches go out without waiting for the next timer tick.
     *
     * Verifies that with gathering armed, tracking disabled by the server and several batches worth of lines held:
     * 1. a delivery was attempted only a handful of times rather than once per captured line, and nothing was uploaded
     * 2. once a response turns tracking back on, the held lines are uploaded without any flush
     */
    @Test
    public void logGathering_linesHeldBackByTracking_queueNoDeliveryPerLineAndGoOutOnceItIsBack() throws JSONException, InterruptedException {
        final List<String> localLogLines = new CopyOnWriteArrayList<>();
        final List<String> uploadedLines = new CopyOnWriteArrayList<>();
        final String[] response = { gatheringResponse(false).tracking(false).build() };

        countly = initGathering(null, response, acceptingRequestQueue(uploadedLines), (logMessage, logLevel) -> localLogLines.add(logMessage));
        assertGathering(consentGatherId, logGatheringAllLevels, logGatheringMinBatchSize);

        for (int a = 0; a < 5; a++) {
            recordLinesCarryingUserData();
        }
        countly.L.flushGatheredLogs();
        Thread.sleep(2000);

        int heldBackDeliveries = 0;
        for (String line : localLogLines) {
            if (line.contains("tracking is disabled, keeping the gathered lines buffered")) {
                heldBackDeliveries++;
            }
        }
        Assert.assertTrue("held back deliveries:[" + heldBackDeliveries + "]", heldBackDeliveries >= 1 && heldBackDeliveries <= 5);
        Assert.assertTrue(uploadedLines.isEmpty());

        pushServerResponse(response, gatheringResponse(false));
        Thread.sleep(1000);

        assertSomeLineContains(uploadedLines, secretEventKey);
    }

    /**
     * A line's consent is found from the bracketed name it starts with, and for an events line about an internal event
     * from that event's key, any one of whose consents is enough. A name the SDK does not know, or no name at all, means
     * the line needs both the events and the users consent.
     */
    @Test
    public void logLineConsentFeatures_areFoundFromTheBracketedName() {
        assertLineConsent(new String[] { Countly.CountlyFeatureNames.events }, "[Events] Calling recordEvent: [" + secretEventKey + "]");
        assertLineConsent(new String[] { Countly.CountlyFeatureNames.events }, "[ModuleEvents] recordEventInternal, key:[" + secretEventKey + "] segmentation:[{screens=[home, [CLY]_view]}]");
        assertLineConsent(new String[] { Countly.CountlyFeatureNames.views }, "[ModuleEvents] recordEventInternal, key:[[CLY]_view] segmentation:[{name=" + secretViewName + "}]");
        assertLineConsent(new String[] { Countly.CountlyFeatureNames.starRating, Countly.CountlyFeatureNames.feedback }, "[ModuleEvents] recordEventInternal, key:[[CLY]_star_rating]");
        assertLineConsent(new String[] { Countly.CountlyFeatureNames.clicks, Countly.CountlyFeatureNames.scrolls }, "[ModuleEvents] recordEventInternal, key:[[CLY]_action]");
        assertLineConsent(new String[] { Countly.CountlyFeatureNames.views }, "[Views] Calling startView vn[" + secretViewName + "]");
        assertLineConsent(new String[] { Countly.CountlyFeatureNames.users }, "[UserProfile] Calling 'setProperty'");
        assertLineConsent(new String[] { Countly.CountlyFeatureNames.push }, "[CountlyPush, displayDialog] Showing the dialog");

        assertLineConsent(null, "[ModuleEvents] recordEventInternal, key:[[CLY]_unknown_internal]");
        assertLineConsent(null, "[CountlyStore] recordEventToEventQueue, key:[" + secretEventKey + "]");
        assertLineConsent(null, "[Connection Queue] sendUserData");
        assertLineConsent(null, "Halting Countly!");
        assertLineConsent(null, "[ModuleEvents");
        assertLineConsent(null, "");
    }

    // ================ Helper Methods ================

    /**
     * Initialises an instance that the server has armed for gathering, with the request queue mocked so that what the
     * logger hands over can be observed without any networking.
     *
     * @param givenConsent the features consent is given for, or null for an instance that does not require consent
     * @param rqp the request queue the gathered batches are handed to
     * @param logListener a listener for the local developer log, or null when it is not inspected
     * @return the initialised instance
     */
    private Countly initGathering(@Nullable String[] givenConsent, @NonNull RequestQueueProvider rqp, @Nullable ModuleLog.LogCallback logListener) throws JSONException {
        return initGathering(givenConsent, new String[] { gatheringResponse(givenConsent != null).build() }, rqp, logListener);
    }

    /**
     * Initialises an instance like {@link #initGathering(String[], RequestQueueProvider, ModuleLog.LogCallback)} does,
     * answering with whatever the holder carries, so that {@link #pushServerResponse(String[], ServerConfigBuilder)}
     * can swap the response later.
     *
     * @param givenConsent the features consent is given for, or null for an instance that does not require consent
     * @param response the holder of the configuration response
     * @param rqp the request queue the gathered batches are handed to
     * @param logListener a listener for the local developer log, or null when it is not inspected
     * @return the initialised instance
     */
    private Countly initGathering(@Nullable String[] givenConsent, @NonNull String[] response, @NonNull RequestQueueProvider rqp, @Nullable ModuleLog.LogCallback logListener) {
        CountlyConfig config = TestUtils.createConsentCountlyConfig(givenConsent != null, givenConsent, null, rqp).setLoggingEnabled(false);
        config.immediateRequestGenerator = createMutableIRG(response);
        config.setLogListener(logListener);

        return new Countly().init(config);
    }

    /**
     * A configuration response that arms gathering at every level with the smallest batch. The server behaviour settings
     * win over the developer's value, so the consent requirement has to match the one the instance is given.
     *
     * @param requiresConsent whether the response requires consent
     * @return the response, to build or to change further
     */
    private static ServerConfigBuilder gatheringResponse(boolean requiresConsent) throws JSONException {
        return new ServerConfigBuilder()
            .defaults()
            .consentRequired(requiresConsent)
            .logGatheringOn(consentGatherId, logGatheringAllLevels, logGatheringMinBatchSize);
    }

    /**
     * @param uploadedLines collects the message of every line of every batch handed over
     * @return a request queue that accepts every batch, so a line missing from the uploads can only be the logger's doing
     */
    private RequestQueueProvider acceptingRequestQueue(@NonNull final List<String> uploadedLines) {
        return acceptingRequestQueue(uploadedLines, new CopyOnWriteArrayList<>());
    }

    /**
     * @param uploadedLines collects the message of every line of every batch handed over
     * @param batchDeviceIds collects the device ID that was the current one when each batch was handed over
     * @return a request queue that accepts every batch, so a line missing from the uploads can only be the logger's doing
     */
    private RequestQueueProvider acceptingRequestQueue(@NonNull final List<String> uploadedLines, @NonNull final List<String> batchDeviceIds) {
        RequestQueueProvider rqp = mock(RequestQueueProvider.class);
        when(rqp.sendSdkLogs(anyString())).thenAnswer(invocation -> {
            //read without the instance lock, which the caller of a device ID change holds
            Countly instance = countly;
            batchDeviceIds.add(instance == null ? "" : instance.moduleDeviceId.deviceIdInstance.getCurrentId());

            String payload = invocation.getArgument(0);
            JSONArray lines = new JSONObject(payload).getJSONArray(ModuleLog.keyBatchLines);
            for (int a = 0; a < lines.length(); a++) {
                uploadedLines.add(lines.getJSONObject(a).getString(ModuleLog.keyLineMessage));
            }
            return true;
        });
        return rqp;
    }

    /**
     * Calls the public API with values that are user data, so that the lines the SDK logs about those calls quote
     * them. The calls go through whether or not their consent is given: what is under test is the gathered copy.
     */
    private void recordLinesCarryingUserData() {
        countly.events().recordEvent(secretEventKey, TestUtils.map("secret", secretSegmentationValue));
        countly.views().startView(secretViewName);
        countly.userProfile().setProperty("secret", secretUserProperty);
    }

    /**
     * Flushes until the buffer is empty, so that a line missing from the uploads was held back rather than left behind.
     * Delivery runs on its own thread, so each flush is only a request to upload.
     */
    private void uploadEverythingGathered() throws InterruptedException {
        for (int a = 0; a < 20 && countly.L.heldLogLineCount() > 0; a++) {
            countly.L.flushGatheredLogs();
            Thread.sleep(250);
        }
        Thread.sleep(250); // a batch taken out of the buffer may still be on its way to the queue
        Assert.assertEquals(0, countly.L.heldLogLineCount());
    }

    /**
     * Asserts that at least one of the given strings quotes the fragment.
     *
     * @param lines the log lines to look through
     * @param fragment the value that has to be found in one of them
     */
    private void assertSomeLineContains(List<String> lines, String fragment) {
        for (String line : lines) {
            if (line.contains(fragment)) {
                return;
            }
        }
        Assert.fail("no line carried the fragment:[" + fragment + "], lines:[" + lines.size() + "]");
    }

    /**
     * Asserts the consent a log line needs.
     *
     * @param expected the features any one of whose consent lets the line go, or null for the events and users consent
     * @param line the line as it is logged
     */
    private void assertLineConsent(@Nullable String[] expected, String line) {
        Assert.assertArrayEquals(line, expected, ModuleLog.logLineConsentFeatures(line));
    }

    /**
     * Asserts that none of the given strings quotes any of the fragments.
     *
     * @param lines the log lines to look through
     * @param fragments the values none of them may carry
     */
    private void assertNoLineContains(List<String> lines, String... fragments) {
        for (String line : lines) {
            for (String fragment : fragments) {
                Assert.assertFalse("a line carried the fragment:[" + fragment + "], line:[" + line + "]", line.contains(fragment));
            }
        }
    }

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

    /**
     * An immediate request generator that holds back the answer to every '/o/sdk' request, the configuration fetch among
     * them, so the test decides when, and with what, the server answers.
     *
     * @param heldAnswers collects the callback of each configuration request
     * @return the generator
     */
    private static ImmediateRequestGenerator createHeldIRG(final List<ImmediateRequestMaker.InternalImmediateRequestCallback> heldAnswers) {
        return new ImmediateRequestGenerator() {
            @Override public ImmediateRequestI CreateImmediateRequestMaker() {
                return (requestData, customEndpoint, cp, requestShouldBeDelayed, networkingIsEnabled, callback, log) -> {
                    if ("/o/sdk".equals(customEndpoint)) {
                        heldAnswers.add(callback);
                    }
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
        assertNoLineContains(warningsAndErrors, fragments);
    }
}
