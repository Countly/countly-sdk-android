package ly.count.android.sdk.hub;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class HttpsUplinkTest {
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private MockWebServer server;
    private HttpsUplink uplink;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String serverUrl = server.url("/").toString();
        uplink = new HttpsUplink(serverUrl.substring(0, serverUrl.length() - 1), null, 5_000, 1024);
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    /**
     * A GET request goes to the configured server with its path, query and headers, minus the
     * hop-by-hop headers, and the server's response comes back as it is.
     */
    @Test
    public void get_isForwardedToTheConfiguredServer() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody("{\"result\":\"Success\"}"));
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Custom", "v");
        headers.put("Connection", "close");
        headers.put("Host", "elsewhere.example");

        HubResponse response = uplink.send(new HubRequest("GET", "/i", "app_key=k&device_id=d", headers, null, 30_000));

        RecordedRequest recorded = server.takeRequest(5, TimeUnit.SECONDS);
        Assert.assertNotNull(recorded);
        Assert.assertEquals("GET", recorded.getMethod());
        Assert.assertEquals("/i?app_key=k&device_id=d", recorded.getPath());
        Assert.assertEquals("v", recorded.getHeader("X-Custom"));
        Assert.assertNotEquals("elsewhere.example", recorded.getHeader("Host"));
        Assert.assertNotEquals("close", recorded.getHeader("Connection"));
        Assert.assertEquals(200, response.getStatus());
        Assert.assertEquals("{\"result\":\"Success\"}", new String(response.getBody(), UTF_8));
        Assert.assertEquals("application/json", response.getHeader("content-type"));
    }

    /**
     * A POST body is forwarded byte for byte.
     */
    @Test
    public void post_bodyIsForwarded() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"result\":\"Success\"}"));
        byte[] body = "app_key=k&crash=%7B%7D".getBytes(UTF_8);

        uplink.send(new HubRequest("POST", "/i", null, Collections.singletonMap("Content-Type", "application/x-www-form-urlencoded"), body, 30_000));

        RecordedRequest recorded = server.takeRequest(5, TimeUnit.SECONDS);
        Assert.assertNotNull(recorded);
        Assert.assertEquals("POST", recorded.getMethod());
        Assert.assertEquals("/i", recorded.getPath());
        Assert.assertArrayEquals(body, recorded.getBody().readByteArray());
        Assert.assertEquals("application/x-www-form-urlencoded", recorded.getHeader("Content-Type"));
    }

    /**
     * Consecutive requests travel over one connection: the server sees them as the first, second and
     * third request on the same connection.
     */
    @Test
    public void consecutiveRequests_reuseOneConnection() throws Exception {
        for (int i = 0; i < 3; i++) {
            server.enqueue(new MockResponse().setBody("{\"result\":\"Success\"}"));
        }

        for (int i = 0; i < 3; i++) {
            uplink.send(new HubRequest("GET", "/i", "app_key=k&n=" + i, Collections.<String, String>emptyMap(), null, 30_000));
        }

        int[] sequence = new int[3];
        for (int i = 0; i < 3; i++) {
            RecordedRequest recorded = server.takeRequest(5, TimeUnit.SECONDS);
            Assert.assertNotNull(recorded);
            sequence[i] = recorded.getSequenceNumber();
        }
        Assert.assertEquals("[0, 1, 2]", Arrays.toString(sequence));
    }

    /**
     * An error status is a response, not a failure: its status and body go back to the app, whose SDK
     * decides to retry.
     */
    @Test
    public void errorStatus_isReturnedWithItsBody() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(400).setBody("{\"result\":\"App does not exist\"}"));

        HubResponse response = uplink.send(new HubRequest("GET", "/i", "app_key=k", Collections.<String, String>emptyMap(), null, 30_000));

        Assert.assertEquals(400, response.getStatus());
        Assert.assertEquals("{\"result\":\"App does not exist\"}", new String(response.getBody(), UTF_8));
    }

    /**
     * Redirects are not followed, so a request never ends up at a server other than the configured one.
     */
    @Test
    public void redirect_isNotFollowed() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(302).setHeader("Location", "https://elsewhere.example/i"));

        HubResponse response = uplink.send(new HubRequest("GET", "/i", "app_key=k", Collections.<String, String>emptyMap(), null, 30_000));

        Assert.assertEquals(302, response.getStatus());
        Assert.assertEquals(1, server.getRequestCount());
    }

    /**
     * A response larger than the limit is a failure.
     */
    @Test
    public void oversizedResponse_fails() {
        char[] filler = new char[4096];
        Arrays.fill(filler, 'x');
        server.enqueue(new MockResponse().setBody(new String(filler)));

        try {
            uplink.send(new HubRequest("GET", "/o/sdk", "app_key=k", Collections.<String, String>emptyMap(), null, 30_000));
            Assert.fail("expected an IOException for a response above the limit");
        } catch (IOException expected) {
            Assert.assertTrue(expected.getMessage().contains("larger than"));
        }
    }

    /**
     * An unreachable server is a failure.
     */
    @Test
    public void unreachableServer_fails() throws IOException {
        server.shutdown();
        try {
            uplink.send(new HubRequest("GET", "/i", "app_key=k", Collections.<String, String>emptyMap(), null, 30_000));
            Assert.fail("expected an IOException for an unreachable server");
        } catch (IOException expected) {
            Assert.assertNotNull(expected);
        }
    }
}
