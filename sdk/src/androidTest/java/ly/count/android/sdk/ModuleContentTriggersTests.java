package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
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

/**
 * Content triggers: the "jt" key of the behavior settings answer, and the deprecated journey trigger events and views
 * that apply without it. A matched trigger sends the queued requests and then asks for content, up to 3 times.
 */
@RunWith(AndroidJUnit4.class)
public class ModuleContentTriggersTests {
    private static final String NO_CONTENT = "{\"result\":\"No content block found!\"}";
    private static final String CONTENT = "{\"html\":\"https://countly.com\",\"geo\":{\"p\":{\"x\":0,\"y\":0,\"w\":100,\"h\":100},\"l\":{\"x\":0,\"y\":0,\"w\":100,\"h\":100}}}";
    private static final String KIND_SC = "sc";
    private static final String KIND_EVENTS = "events";
    private static final String KIND_CONTENT = "content";
    private static final String KIND_OTHER = "other";

    private final List<String> received = new CopyOnWriteArrayList<>();
    private final List<String> receivedEvents = new CopyOnWriteArrayList<>();
    private final List<String> behaviorSettingsRequests = new CopyOnWriteArrayList<>();

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
     * Decides the mocked server's answer to one request
     */
    interface ServerAnswer {
        void answer(@NonNull String kind, @NonNull MockResponse response);
    }

    /**
     * The part of a test that runs after init
     */
    interface TestBody {
        void run(@NonNull Countly countly) throws Exception;
    }

    /**
     * @param lists pairs of list name and values, for example "e", new String[] { "purchase" }
     * @return a "jt" object
     */
    private static JSONObject jt(Object... lists) throws JSONException {
        JSONObject jt = new JSONObject();
        for (int i = 0; i < lists.length; i += 2) {
            jt.put((String) lists[i], new JSONArray((String[]) lists[i + 1]));
        }
        return jt;
    }

    /**
     * Runs a test against a mocked server that answers the behavior settings request with the given answer, every
     * content request with "no content" and everything else with success, unless the test's answer changes it
     *
     * @param behaviorSettings the behavior settings answer
     * @param answer changes single answers, may be null
     * @param configure changes the init config, may be null
     * @param body the test
     */
    private void runWithServer(@NonNull String behaviorSettings, ServerAnswer answer, Consumer<CountlyConfig> configure, @NonNull TestBody body) throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(new Dispatcher() {
                @NonNull @Override public MockResponse dispatch(@NonNull RecordedRequest request) {
                    String path = request.getPath() == null ? "" : request.getPath();
                    String requestBody = request.getBody().readUtf8();
                    String kind;
                    if (path.startsWith("/o/sdk/content")) {
                        kind = KIND_CONTENT;
                    } else if (path.contains("method=sc") || requestBody.contains("method=sc")) {
                        kind = KIND_SC;
                        behaviorSettingsRequests.add(path + "&" + requestBody);
                    } else if (path.contains("events=") || requestBody.contains("events=")) {
                        kind = KIND_EVENTS;
                        receivedEvents.add(path + "&" + requestBody);
                    } else {
                        kind = KIND_OTHER;
                    }
                    received.add(kind);

                    MockResponse response = new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody("{\"result\":\"Success\"}");
                    if (KIND_SC.equals(kind)) {
                        response.setBody(behaviorSettings);
                    } else if (KIND_CONTENT.equals(kind)) {
                        response.setBody(NO_CONTENT);
                    }
                    if (answer != null) {
                        answer.answer(kind, response);
                    }
                    return response;
                }
            });
            server.start();
            String serverUrl = server.url("/").toString();
            serverUrl = serverUrl.substring(0, serverUrl.length() - 1);

            CountlyConfig config = TestUtils.createBaseConfig().enableManualSessionControl();
            config.metricProviderOverride = new MockedMetricProvider();
            config.setServerURL(serverUrl);
            if (configure != null) {
                configure.accept(config);
            }
            Countly.sharedInstance().init(config);
            Countly.sharedInstance().moduleContent.CONTENT_START_DELAY_MS = 0;
            Thread.sleep(1000);

            body.run(Countly.sharedInstance());
            server.shutdown();
        }
    }

    private int count(@NonNull String kind) {
        int count = 0;
        for (String received : received) {
            if (kind.equals(received)) {
                count++;
            }
        }
        return count;
    }

    private static String behaviorSettings(JSONObject contentTriggers) throws JSONException {
        ServerConfigBuilder builder = new ServerConfigBuilder().defaults();
        if (contentTriggers != null) {
            builder.contentTriggers(contentTriggers);
        }
        return builder.build();
    }

    /**
     * The behavior settings request asks for the content triggers with jt=1
     */
    @Test
    public void behaviorSettingsRequest_asksForContentTriggers() throws Exception {
        runWithServer(behaviorSettings(null), null, null, countly -> {
            Assert.assertFalse(behaviorSettingsRequests.isEmpty());
            Assert.assertTrue(behaviorSettingsRequests.get(0).contains("jt=1"));
        });
    }

    /**
     * With disableAutoContentTriggers, the SDK neither asks for the content triggers nor follows ones that arrive:
     * a listed event stays held and no content is asked for
     */
    @Test
    public void disableAutoContentTriggers_neitherAsksNorFollows() throws Exception {
        runWithServer(behaviorSettings(jt("e", new String[] { "purchase" })), null, config -> config.content.disableAutoContentTriggers(), countly -> {
            Assert.assertFalse(behaviorSettingsRequests.get(0).contains("jt=1"));
            Assert.assertNull(countly.moduleConfiguration.getContentTriggers());

            countly.contents().enterContentZone();
            Thread.sleep(500);
            int contentRequests = count(KIND_CONTENT);

            countly.events().recordEvent("purchase");
            Thread.sleep(2000);
            Assert.assertEquals(contentRequests, count(KIND_CONTENT));
            Assert.assertEquals(1, TestUtils.getCountlyStore().getEventQueueSize());
        });
    }

    /**
     * Content triggers given with the behavior settings at init are used and stored with them, also when settings
     * requests are disabled
     */
    @Test
    public void contentTriggers_providedAtInit_usedAndStored() throws JSONException {
        CountlyConfig config = TestUtils.createBaseConfig().enableManualSessionControl();
        config.setSDKBehaviorSettings(behaviorSettings(jt("e", new String[] { "purchase" }, "v", new String[] { "Checkout" }, "p", new String[] { "custom.plan" })));
        config.disableSDKBehaviorSettingsUpdates();
        Countly.sharedInstance().init(config);

        ContentTriggers contentTriggers = Countly.sharedInstance().moduleConfiguration.getContentTriggers();
        Assert.assertNotNull(contentTriggers);
        Assert.assertTrue(contentTriggers.keys.contains("purchase"));
        Assert.assertTrue(contentTriggers.views.contains("Checkout"));
        Assert.assertTrue(contentTriggers.properties.contains("custom.plan"));
        Assert.assertTrue(TestUtils.getCountlyStore().getServerConfig().contains("\"jt\""));
    }

    /**
     * A list the server leaves out is an empty one, and a value of the wrong type is ignored
     */
    @Test
    public void contentTriggers_parse_absentListsAndWrongTypes() throws JSONException {
        ModuleLog L = new ModuleLog();
        ContentTriggers onlyEvents = ContentTriggers.parse(new JSONObject("{\"e\":[\"purchase\",\"\",5]}"), L);
        Assert.assertNotNull(onlyEvents);
        Assert.assertEquals(1, onlyEvents.keys.size());
        Assert.assertTrue(onlyEvents.views.isEmpty());
        Assert.assertTrue(onlyEvents.properties.isEmpty());

        ContentTriggers empty = ContentTriggers.parse(new JSONObject("{}"), L);
        Assert.assertNotNull(empty);
        Assert.assertTrue(empty.keys.isEmpty());

        ContentTriggers wrongList = ContentTriggers.parse(new JSONObject("{\"e\":\"purchase\",\"v\":[\"Checkout\"]}"), L);
        Assert.assertNotNull(wrongList);
        Assert.assertTrue(wrongList.keys.isEmpty());
        Assert.assertTrue(wrongList.views.contains("Checkout"));

        Assert.assertNull(ContentTriggers.parse(null, L));
        Assert.assertNull(ContentTriggers.parse("purchase", L));
    }

    /**
     * A listed event, while the app wants the content zone, sends the held events first and then asks for content 3
     * times, 1 second apart, when the server has no content
     */
    @Test
    public void contentTriggers_listedEvent_sendsTheQueueThenAsksForContent() throws Exception {
        runWithServer(behaviorSettings(jt("e", new String[] { "purchase" })), null, null, countly -> {
            countly.contents().enterContentZone();
            Thread.sleep(1000);
            received.clear();

            countly.events().recordEvent("browse");
            countly.events().recordEvent("purchase");
            Thread.sleep(4500);

            Assert.assertEquals(0, TestUtils.getCurrentRQ().length);
            Assert.assertEquals(1, count(KIND_EVENTS));
            Assert.assertTrue(receivedEvents.get(0).contains("browse"));
            Assert.assertTrue(receivedEvents.get(0).contains("purchase"));
            Assert.assertEquals(3, count(KIND_CONTENT));
            Assert.assertTrue(received.indexOf(KIND_EVENTS) < received.indexOf(KIND_CONTENT));
        });
    }

    /**
     * Content triggers apply only while the app wants the content zone
     */
    @Test
    public void contentTriggers_zoneNotWanted_asksForNothing() throws Exception {
        runWithServer(behaviorSettings(jt("e", new String[] { "purchase" })), null, null, countly -> {
            countly.events().recordEvent("purchase");
            Thread.sleep(2500);

            Assert.assertEquals(0, count(KIND_CONTENT));
            Assert.assertEquals(0, count(KIND_EVENTS));
            Assert.assertEquals(1, TestUtils.getCountlyStore().getEventQueueSize());
        });
    }

    /**
     * An answer with content triggers, even an empty one, makes the SDK ignore the journey trigger events
     */
    @Test
    public void contentTriggers_emptyAnswer_journeyTriggerEventsIgnored() throws Exception {
        String settings = new ServerConfigBuilder().defaults().journeyTriggerEvents(java.util.Collections.singleton("purchase")).contentTriggers(new JSONObject()).build();
        runWithServer(settings, null, null, countly -> {
            countly.contents().enterContentZone();
            Thread.sleep(1000);
            int contentRequests = count(KIND_CONTENT);

            countly.events().recordEvent("purchase");
            Thread.sleep(2500);

            Assert.assertEquals(contentRequests, count(KIND_CONTENT));
            Assert.assertEquals(1, TestUtils.getCountlyStore().getEventQueueSize());
        });
    }

    /**
     * Without content triggers (an older server), a journey trigger event sends the queue and asks for content, also
     * while the app doesn't want the content zone, and it doesn't enter the zone
     */
    @Test
    public void journeyTriggerEvents_withoutContentTriggers_askForContent() throws Exception {
        String settings = new ServerConfigBuilder().defaults().journeyTriggerEvents(java.util.Collections.singleton("purchase")).build();
        runWithServer(settings, null, null, countly -> {
            countly.events().recordEvent("browse");
            Thread.sleep(1000);
            Assert.assertEquals(1, TestUtils.getCountlyStore().getEventQueueSize());

            countly.events().recordEvent("purchase");
            Thread.sleep(4500);

            Assert.assertEquals(1, count(KIND_EVENTS));
            Assert.assertEquals(3, count(KIND_CONTENT));

            Thread.sleep(2000);
            Assert.assertEquals(3, count(KIND_CONTENT));
        });
    }

    /**
     * A journey trigger view starts a round for the start and for the end of the view, each with its own gap
     */
    @Test
    public void journeyTriggerViews_startAndEnd_eachStartsARound() throws Exception {
        String settings = new ServerConfigBuilder().defaults().journeyTriggerViews(java.util.Collections.singleton("Checkout")).build();
        runWithServer(settings, null, null, countly -> {
            countly.views().startView("Checkout");
            Thread.sleep(4500);
            Assert.assertEquals(3, count(KIND_CONTENT));

            countly.views().stopViewWithName("Checkout");
            Thread.sleep(4500);
            Assert.assertEquals(6, count(KIND_CONTENT));
        });
    }

    /**
     * The same trigger inside its gap neither sends the queue nor asks for content
     */
    @Test
    public void contentTriggers_sameTriggerInsideItsGap_startsNoSecondRound() throws Exception {
        runWithServer(behaviorSettings(jt("e", new String[] { "purchase" })), null, null, countly -> {
            countly.contents().enterContentZone();
            Thread.sleep(1000);
            received.clear();

            countly.events().recordEvent("purchase");
            Thread.sleep(4500);
            Assert.assertEquals(3, count(KIND_CONTENT));

            countly.events().recordEvent("purchase");
            Thread.sleep(2500);
            Assert.assertEquals(3, count(KIND_CONTENT));
            Assert.assertEquals(1, count(KIND_EVENTS));
            Assert.assertEquals(1, TestUtils.getCountlyStore().getEventQueueSize());
        });
    }

    /**
     * A trigger during a running round doesn't send the queue early; one more round follows the running one
     */
    @Test
    public void contentTriggers_triggerDuringARound_oneMoreRoundFollows() throws Exception {
        runWithServer(behaviorSettings(jt("e", new String[] { "first", "second" })), (kind, response) -> {
            if (KIND_EVENTS.equals(kind)) {
                response.setBodyDelay(1500, TimeUnit.MILLISECONDS);
            }
        }, null, countly -> {
            countly.contents().enterContentZone();
            Thread.sleep(1000);

            countly.events().recordEvent("first");
            Thread.sleep(300);
            countly.events().recordEvent("second");
            Thread.sleep(6000);

            Assert.assertEquals(2, count(KIND_EVENTS));
            Assert.assertTrue(receivedEvents.get(0).contains("first"));
            Assert.assertFalse(receivedEvents.get(0).contains("second"));
            Assert.assertTrue(receivedEvents.get(1).contains("second"));
        });
    }

    /**
     * A listed user property starts a round, an unlisted one doesn't
     */
    @Test
    public void contentTriggers_listedUserProperty_startsARound() throws Exception {
        runWithServer(behaviorSettings(jt("p", new String[] { "custom.plan" })), null, null, countly -> {
            countly.contents().enterContentZone();
            Thread.sleep(1000);
            received.clear();

            countly.userProfile().setProperty("color", "blue");
            countly.userProfile().save();
            Thread.sleep(2500);
            Assert.assertEquals(0, count(KIND_CONTENT));

            countly.userProfile().setProperty("plan", "pro");
            countly.userProfile().save();
            Thread.sleep(4500);
            Assert.assertEquals(3, count(KIND_CONTENT));
        });
    }

    /**
     * A handled exception the app records is a "[CLY]_crash" trigger
     */
    @Test
    public void contentTriggers_handledException_startsARound() throws Exception {
        runWithServer(behaviorSettings(jt("e", new String[] { "[CLY]_crash" })), null, null, countly -> {
            countly.contents().enterContentZone();
            Thread.sleep(1000);
            received.clear();

            countly.crashes().recordHandledException(new Exception("content trigger test"));
            Thread.sleep(4500);
            Assert.assertEquals(3, count(KIND_CONTENT));
        });
    }

    /**
     * A session that begins before the first behavior settings answer of the run still starts a round when the
     * answer lists "[CLY]_session"
     */
    @Test
    public void contentTriggers_sessionBeforeTheFirstAnswer_startsARoundWhenItArrives() throws Exception {
        runWithServer(behaviorSettings(jt("e", new String[] { "[CLY]_session" })), (kind, response) -> {
            if (KIND_SC.equals(kind)) {
                response.setBodyDelay(2500, TimeUnit.MILLISECONDS);
            }
        }, null, countly -> {
            Assert.assertNull(countly.moduleConfiguration.getContentTriggers());
            countly.contents().enterContentZone();
            countly.sessions().beginSession();
            Thread.sleep(6000);

            Assert.assertNotNull(countly.moduleConfiguration.getContentTriggers());
            Assert.assertEquals(4, count(KIND_CONTENT));
        });
    }

    /**
     * When the queued requests can not be sent, the round ends without asking for content
     */
    @Test
    public void contentTriggers_queueNotSent_noContentRequest() throws Exception {
        runWithServer(behaviorSettings(jt("e", new String[] { "purchase" })), (kind, response) -> {
            if (KIND_EVENTS.equals(kind)) {
                response.setResponseCode(500).setBody("Internal Server Error");
            }
        }, null, countly -> {
            countly.contents().enterContentZone();
            Thread.sleep(1000);
            received.clear();

            countly.events().recordEvent("purchase");
            Thread.sleep(4500);

            Assert.assertTrue(count(KIND_EVENTS) >= 1);
            Assert.assertEquals(0, count(KIND_CONTENT));
        });
    }

    /**
     * No content is asked for while content is displayed
     */
    @Test
    public void contentTriggers_contentDisplayed_noContentRequest() throws Exception {
        runWithServer(behaviorSettings(jt("e", new String[] { "first", "second" })), (kind, response) -> {
            if (KIND_CONTENT.equals(kind)) {
                response.setBody(CONTENT);
            }
        }, null, countly -> {
            countly.events().recordEvent("first");
            countly.contents().enterContentZone();
            Thread.sleep(1500);
            received.clear();

            countly.events().recordEvent("second");
            Thread.sleep(3000);

            Assert.assertEquals(1, count(KIND_EVENTS));
            Assert.assertEquals(0, count(KIND_CONTENT));
            countly.contents().exitContentZone();
        });
    }

    /**
     * On a first start, when no behavior settings were stored at init, the first answer's "ecz" enters the content zone
     */
    @Test
    public void ecz_firstStart_firstAnswerEntersTheZone() throws Exception {
        runWithServer(new ServerConfigBuilder().defaults().contentZone(true).build(), null, null, countly -> {
            Thread.sleep(1500);
            Assert.assertTrue(count(KIND_CONTENT) >= 1);
        });
    }

    /**
     * When behavior settings were stored at init with "ecz" off, a later answer with "ecz" on doesn't enter the content
     * zone in this run
     */
    @Test
    public void ecz_laterAnswer_doesNotEnterTheZone() throws Exception {
        runWithServer(new ServerConfigBuilder().defaults().contentZone(true).build(), null,
            config -> config.setSDKBehaviorSettings(behaviorSettingsUnchecked(new ServerConfigBuilder().defaults().contentZone(false))), countly -> {
                Thread.sleep(2000);
                Assert.assertEquals(0, count(KIND_CONTENT));
            });
    }

    /**
     * A behavior settings answer with "ecz" off doesn't exit a content zone the app entered: content triggers still
     * apply after it
     */
    @Test
    public void ecz_answerWithEczOff_keepsTheZoneTheAppEntered() throws Exception {
        runWithServer(behaviorSettings(jt("e", new String[] { "purchase" })), (kind, response) -> {
            if (KIND_SC.equals(kind)) {
                response.setBodyDelay(1500, TimeUnit.MILLISECONDS);
            }
        }, null, countly -> {
            countly.contents().enterContentZone();
            Thread.sleep(2000);
            Assert.assertNotNull(countly.moduleConfiguration.getContentTriggers());
            received.clear();

            countly.events().recordEvent("purchase");
            Thread.sleep(4500);
            Assert.assertEquals(3, count(KIND_CONTENT));
        });
    }

    private static String behaviorSettingsUnchecked(ServerConfigBuilder builder) {
        try {
            return builder.build();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * The gap is kept per trigger and stops a second round only inside its time
     */
    @Test
    public void claimGap_keepsTheTimeOfEachTrigger() {
        ContentTriggers.Match purchase = new ContentTriggers.Match(java.util.Collections.singletonList("purchase"), ContentTriggers.GAP_SPECIFIC_MS);
        String stored = ContentTriggers.claimGap(purchase, "", 1_000_000L);
        Assert.assertNotNull(stored);
        Assert.assertNull(ContentTriggers.claimGap(purchase, stored, 1_000_000L + 9_999L));
        Assert.assertNotNull(ContentTriggers.claimGap(purchase, stored, 1_000_000L + 10_000L));
        Assert.assertNotNull(ContentTriggers.claimGap(purchase, stored, 1_000_000L - 1L));

        ContentTriggers.Match other = new ContentTriggers.Match(java.util.Collections.singletonList("other"), ContentTriggers.GAP_SPECIFIC_MS);
        Assert.assertNotNull(ContentTriggers.claimGap(other, stored, 1_000_000L + 1L));
    }

    /**
     * Matching: listed names are specific, any view, any user property and any error wait the long gap, and the end of
     * a view has its own gap
     */
    @Test
    public void match_gapsAndKeys() throws JSONException {
        ContentTriggers contentTriggers = ContentTriggers.parse(jt("e", new String[] { "purchase", "[CLY]_view", "[CLY]_property_update" }, "v", new String[] { "Checkout" }), new ModuleLog());
        java.util.Set<String> none = java.util.Collections.emptySet();

        ContentTriggers.Match view = ContentTriggers.match(contentTriggers, true, none, none, ContentTriggers.Trigger.view("Checkout", false));
        Assert.assertNotNull(view);
        Assert.assertEquals("v:Checkout", view.gapKeys.get(0));
        Assert.assertEquals(ContentTriggers.GAP_SPECIFIC_MS, view.gapMs);

        ContentTriggers.Match viewEnd = ContentTriggers.match(contentTriggers, true, none, none, ContentTriggers.Trigger.view("Checkout", true));
        Assert.assertNotNull(viewEnd);
        Assert.assertEquals("v:Checkout:end", viewEnd.gapKeys.get(0));

        ContentTriggers.Match anyView = ContentTriggers.match(contentTriggers, true, none, none, ContentTriggers.Trigger.view("Home", false));
        Assert.assertNotNull(anyView);
        Assert.assertEquals(ContentTriggers.GAP_NON_SPECIFIC_MS, anyView.gapMs);

        ContentTriggers.Match anyProperty = ContentTriggers.match(contentTriggers, true, none, none, ContentTriggers.Trigger.userProperties(java.util.Collections.singletonList("custom.color")));
        Assert.assertNotNull(anyProperty);
        Assert.assertEquals(ContentTriggers.GAP_NON_SPECIFIC_MS, anyProperty.gapMs);

        Assert.assertNull(ContentTriggers.match(contentTriggers, false, none, none, ContentTriggers.Trigger.event("purchase", true)));
        Assert.assertNull(ContentTriggers.match(contentTriggers, true, none, none, ContentTriggers.Trigger.event("browse", true)));

        java.util.Set<String> jte = java.util.Collections.singleton("[CLY]_custom_like");
        Assert.assertNull(ContentTriggers.match(null, true, jte, none, ContentTriggers.Trigger.event("[CLY]_custom_like", false)));
        Assert.assertNotNull(ContentTriggers.match(null, false, jte, none, ContentTriggers.Trigger.event("[CLY]_custom_like", true)));
    }
}
