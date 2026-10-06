package ly.count.android.sdk.hub;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import ly.count.android.sdk.Countly;
import ly.count.android.sdk.CountlyConfig;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Exercises the hub across a real process boundary: the test app is the client, {@link TestHubService}
 * runs in the app's ":hub" process, and a mock server stands in for Countly.
 */
@RunWith(AndroidJUnit4.class)
public class HubIntegrationTest {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final List<RecordedRequest> received = new CopyOnWriteArrayList<>();
    private static MockWebServer server;

    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @BeforeClass
    public static void startServer() throws IOException {
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                received.add(request);
                return new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody("{\"result\":\"Success\"}");
            }
        });
        server.start();
        String url = server.url("/").toString();
        TestHubService.writeServerUrl(InstrumentationRegistry.getInstrumentation().getTargetContext(), url.substring(0, url.length() - 1));
    }

    @AfterClass
    public static void stopServer() throws IOException {
        server.shutdown();
    }

    @After
    public void haltSdk() {
        Countly.sharedInstance().halt();
    }

    private HubChannel channel() {
        return new HubChannel(new HubClientConfig(context, context.getPackageName()));
    }

    private static HubRequest get(String path, String query) {
        return new HubRequest("GET", path, query, Collections.<String, String>emptyMap(), null, 30_000);
    }

    private static RecordedRequest awaitRequest(String marker, long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            for (RecordedRequest request : received) {
                String path = request.getPath() == null ? "" : URLDecoder.decode(request.getPath(), "UTF-8");
                String body = URLDecoder.decode(request.getBody().clone().readString(UTF_8), "UTF-8");
                if (path.contains(marker) || body.contains(marker)) {
                    return request;
                }
            }
            Thread.sleep(50);
        }
        return null;
    }

    /**
     * A request handed over Binder reaches the server with its path and query, and the server's
     * response comes back to the calling process.
     */
    @Test
    public void channel_relaysARequestAcrossProcesses() throws Exception {
        HubResponse response = channel().exchange(get("/i", "app_key=" + TestHubService.APP_KEY + "&device_id=d&marker=relay1"));

        Assert.assertEquals(200, response.getStatus());
        Assert.assertEquals("{\"result\":\"Success\"}", new String(response.getBody(), UTF_8));
        RecordedRequest recorded = awaitRequest("marker=relay1", 5_000);
        Assert.assertNotNull(recorded);
        Assert.assertEquals("GET", recorded.getMethod());
        Assert.assertTrue(recorded.getPath().startsWith("/i?app_key=" + TestHubService.APP_KEY));
    }

    /**
     * A body above the inline limit, like a crash report with a native dump, travels through a file
     * descriptor and arrives complete.
     */
    @Test
    public void channel_largeBodyArrivesComplete() throws Exception {
        char[] filler = new char[1024 * 1024];
        Arrays.fill(filler, 'x');
        byte[] body = ("app_key=" + TestHubService.APP_KEY + "&device_id=d&marker=large1&crash=" + new String(filler)).getBytes(UTF_8);
        HubRequest request = new HubRequest("POST", "/i", null, Collections.singletonMap("Content-Type", "application/x-www-form-urlencoded"), body, 30_000);

        HubResponse response = channel().exchange(request);

        Assert.assertEquals(200, response.getStatus());
        RecordedRequest recorded = awaitRequest("marker=large1", 10_000);
        Assert.assertNotNull(recorded);
        Assert.assertEquals(body.length, recorded.getBodySize());
    }

    /**
     * Data for an app key the app is not allowed to use is refused by the hub and never reaches the server.
     */
    @Test
    public void channel_refusesAnotherAppsKey() throws Exception {
        HubResponse response = channel().exchange(get("/i", "app_key=SOMEONE_ELSES_KEY&device_id=d&marker=foreign1"));

        Assert.assertEquals(403, response.getStatus());
        Assert.assertNull(awaitRequest("marker=foreign1", 1_000));
    }

    /**
     * A path outside the Countly SDK paths is refused and never reaches the server.
     */
    @Test
    public void channel_refusesAPathOutsideCountly() throws Exception {
        HubResponse response = channel().exchange(get("/o/apps/mine", "app_key=" + TestHubService.APP_KEY + "&marker=path1"));

        Assert.assertEquals(403, response.getStatus());
        Assert.assertNull(awaitRequest("marker=path1", 1_000));
    }

    /**
     * Without a hub in the given package the request fails at once, so the SDK keeps it for later.
     */
    @Test
    public void channel_missingHub_failsFast() {
        HubChannel missing = new HubChannel(new HubClientConfig(context, "ly.count.android.sdk.hub.nothere").setBindTimeoutMillis(2_000));
        long before = System.currentTimeMillis();
        try {
            missing.exchange(get("/i", "app_key=" + TestHubService.APP_KEY));
            Assert.fail("expected an IOException without a hub");
        } catch (IOException expected) {
            Assert.assertTrue(System.currentTimeMillis() - before < 2_000);
        }
    }

    /**
     * Requests relayed one after another share one connection to the server: each is the next request
     * on the connection the previous one used.
     */
    @Test
    public void channel_consecutiveRequestsShareOneServerConnection() throws Exception {
        HubChannel channel = channel();
        int[] sequence = new int[3];
        for (int i = 0; i < 3; i++) {
            channel.exchange(get("/i", "app_key=" + TestHubService.APP_KEY + "&device_id=d&marker=conn" + i + "x"));
            RecordedRequest recorded = awaitRequest("marker=conn" + i + "x", 5_000);
            Assert.assertNotNull(recorded);
            sequence[i] = recorded.getSequenceNumber();
        }
        Assert.assertEquals(sequence[0] + 1, sequence[1]);
        Assert.assertEquals(sequence[1] + 1, sequence[2]);
    }

    /**
     * The Countly SDK, configured with the hub, delivers a recorded event to the server through the
     * hub process.
     */
    @Test
    public void sdk_deliversEventsThroughTheHub() throws Exception {
        context.getSharedPreferences("COUNTLY_STORE", Context.MODE_PRIVATE).edit().clear().commit();
        CountlyConfig config = new CountlyConfig(context, TestHubService.APP_KEY, "https://countly.example.invalid")
            .setDeviceId("hub-test-device")
            .setLoggingEnabled(true);
        CountlyHub.useHub(config, new HubClientConfig(context, context.getPackageName()));
        Countly.sharedInstance().init(config);

        Countly.sharedInstance().events().recordEvent("hub_test_event");
        Countly.sharedInstance().requestQueue().attemptToSendStoredRequests();

        RecordedRequest recorded = awaitRequest("hub_test_event", 15_000);
        Assert.assertNotNull("the event did not reach the server through the hub", recorded);
        String request = URLDecoder.decode(recorded.getPath(), "UTF-8") + URLDecoder.decode(recorded.getBody().clone().readString(UTF_8), "UTF-8");
        Assert.assertTrue(request.contains("app_key=" + TestHubService.APP_KEY));
        Assert.assertTrue(request.contains("device_id=hub-test-device"));
        Assert.assertTrue(request.contains("checksum256="));
    }
}
