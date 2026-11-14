package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.GZIPInputStream;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Request compression, checked on the requests a local server receives: the settings request asks for "gzip", the
 * server's "gzip": true answer turns compression on for request bodies of at least 1 KB, which then go to the
 * endpoint with "content_encoding=gzip" and a gzip body, and a refused compressed request turns it off for the run.
 */
@RunWith(AndroidJUnit4.class)
public class RequestCompressionTests {
    private static final String BIG_VALUE = new String(new char[2000]).replace('\0', 'a');

    private final List<TestServer> servers = new ArrayList<>();

    /** Starts each test with no stored data and the default instance stopped. */
    @Before
    public void setUp() {
        TestUtils.getCountlyStore().clear();
        Countly.sharedInstance().halt();
    }

    /** Stops the default instance and the local servers and clears the stored data. */
    @After
    public void tearDown() throws IOException {
        Countly.sharedInstance().halt();
        for (TestServer server : servers) {
            server.server.shutdown();
        }
        TestUtils.getCountlyStore().clear();
    }

    /**
     * Without "enableRequestCompression" the settings request does not ask for gzip and a big request goes plain,
     * even to a server that would read gzip.
     */
    @Test
    public void compressionOff_settingsRequestDoesNotAskAndRequestsArePlain() throws Exception {
        TestServer server = startServer(true);
        Countly countly = new Countly().init(config(server.url));
        Assert.assertTrue(server.waitFor("method=sc"));

        addDirectRequest(countly, "big_plain", BIG_VALUE);

        Received request = server.waitForRequest("big_plain");
        Assert.assertFalse(server.first("method=sc").body.contains("gzip=1"));
        Assert.assertEquals("/i", request.path);
        Assert.assertFalse(request.compressed);
        Assert.assertFalse(countly.moduleConfiguration.getRequestCompressionEnabled());
    }

    /**
     * With compression on and a server that answers "gzip": true, a request body of at least 1 KB goes to
     * "/i?content_encoding=gzip" as gzip, smaller than the plain body, which it decodes to.
     */
    @Test
    public void compressionOn_serverReadsGzip_bigRequestIsCompressed() throws Exception {
        TestServer server = startServer(true);
        Countly countly = new Countly().init(config(server.url).enableRequestCompression());
        Assert.assertTrue(server.waitFor("method=sc"));
        Assert.assertTrue(server.first("method=sc").body.contains("gzip=1"));
        Assert.assertTrue(waitUntil(() -> countly.moduleConfiguration.getRequestCompressionEnabled()));

        addDirectRequest(countly, "big_compressed", BIG_VALUE);

        Received request = server.waitForRequest("big_compressed");
        Assert.assertEquals("/i?content_encoding=gzip", request.path);
        Assert.assertTrue(request.compressed);
        Assert.assertEquals((byte) 0x1f, request.raw[0]);
        Assert.assertEquals((byte) 0x8b, request.raw[1]);
        Assert.assertTrue(request.raw.length < request.body.length());
        Assert.assertEquals("application/x-www-form-urlencoded", request.contentType);
        Assert.assertTrue(request.body.contains("app_key=" + TestUtils.commonAppKey));
        Assert.assertTrue(request.body.contains("payload=" + BIG_VALUE));
        Assert.assertTrue(waitForEmptyQueue());
    }

    /** A request body under 1 KB goes plain while compression is active. */
    @Test
    public void compressionOn_smallRequestStaysPlain() throws Exception {
        TestServer server = startServer(true);
        Countly countly = new Countly().init(config(server.url).enableRequestCompression());
        Assert.assertTrue(waitUntil(() -> countly.moduleConfiguration.getRequestCompressionEnabled()));

        addDirectRequest(countly, "small_plain", "x");

        Received request = server.waitForRequest("small_plain");
        Assert.assertEquals("/i", request.path);
        Assert.assertFalse(request.compressed);
    }

    /** A server whose settings answer has no "gzip" key gets plain requests, so older servers keep working. */
    @Test
    public void compressionOn_serverDoesNotSayGzip_requestsArePlain() throws Exception {
        TestServer server = startServer(false);
        Countly countly = new Countly().init(config(server.url).enableRequestCompression());
        Assert.assertTrue(server.waitFor("method=sc", "gzip=1"));
        Assert.assertTrue(waitUntil(() -> server.answeredSettings > 0));

        addDirectRequest(countly, "big_old_server", BIG_VALUE);

        Received request = server.waitForRequest("big_old_server");
        Assert.assertEquals("/i", request.path);
        Assert.assertFalse(request.compressed);
        Assert.assertFalse(countly.moduleConfiguration.getRequestCompressionEnabled());
    }

    /**
     * A compressed request the server refuses keeps its place in the queue, compression stops for the rest of the
     * run, and the retry and later requests go plain.
     */
    @Test
    public void compressedRequestRefused_retryAndLaterRequestsArePlain() throws Exception {
        TestServer server = startServer(true);
        server.refuseNextCompressed = true;
        Countly countly = new Countly().init(config(server.url).enableRequestCompression());
        Assert.assertTrue(waitUntil(() -> countly.moduleConfiguration.getRequestCompressionEnabled()));

        addDirectRequest(countly, "big_refused", BIG_VALUE);

        Received refused = server.waitForRequest("big_refused");
        Assert.assertTrue(refused.compressed);
        Assert.assertTrue(waitUntil(() -> countly.moduleConfiguration.requestCompressionBlocked.get()));
        Assert.assertFalse(countly.moduleConfiguration.getRequestCompressionEnabled());
        Assert.assertTrue(Arrays.toString(TestUtils.getCountlyStore().getRequests()).contains("big_refused"));

        countly.requestQueue().attemptToSendStoredRequests();
        Assert.assertTrue(waitUntil(() -> server.matching("big_refused").size() == 2));
        Assert.assertFalse(server.matching("big_refused").get(1).compressed);
        Assert.assertTrue(waitForEmptyQueue());

        addDirectRequest(countly, "big_after_refusal", BIG_VALUE);
        Assert.assertFalse(server.waitForRequest("big_after_refusal").compressed);
    }

    /** The "gzip" answer is read live and never stored with the behavior settings. */
    @Test
    public void gzipAnswer_isNotStored() throws Exception {
        TestServer server = startServer(true);
        Countly countly = new Countly().init(config(server.url).enableRequestCompression());
        Assert.assertTrue(waitUntil(() -> countly.moduleConfiguration.getRequestCompressionEnabled()));

        String stored = TestUtils.getCountlyStore().getServerConfig();
        Assert.assertNotNull(stored);
        Assert.assertFalse(stored, new JSONObject(stored).has("gzip"));
    }

    /** After the server URL changes, requests go plain until the new server says it reads gzip. */
    @Test
    public void serverUrlChange_newServerWithoutGzipGetsPlainRequests() throws Exception {
        TestServer serverA = startServer(true);
        TestServer serverB = startServer(false);
        Countly countly = new Countly().init(config(serverA.url).enableRequestCompression());
        Assert.assertTrue(waitUntil(() -> countly.moduleConfiguration.getRequestCompressionEnabled()));

        countly.requestQueue().changeServerUrl(serverB.url);
        Assert.assertFalse(countly.moduleConfiguration.getRequestCompressionEnabled());
        addDirectRequest(countly, "big_server_b", BIG_VALUE);

        Received request = serverB.waitForRequest("big_server_b");
        Assert.assertEquals("/i", request.path);
        Assert.assertFalse(request.compressed);
    }

    /** Only "gzip": true turns compression on, and the key is removed from the answer it was read from. */
    @Test
    public void extractGzipFlag_onlyTrueCounts() throws JSONException {
        JSONObject on = new JSONObject("{\"v\":2,\"t\":1,\"c\":{},\"gzip\":true}");
        Assert.assertTrue(ModuleConfiguration.extractGzipFlag(on));
        Assert.assertFalse(on.has("gzip"));

        Assert.assertFalse(ModuleConfiguration.extractGzipFlag(new JSONObject("{\"gzip\":\"true\"}")));
        Assert.assertFalse(ModuleConfiguration.extractGzipFlag(new JSONObject("{\"gzip\":1}")));
        Assert.assertFalse(ModuleConfiguration.extractGzipFlag(new JSONObject("{\"gzip\":false}")));
        Assert.assertFalse(ModuleConfiguration.extractGzipFlag(new JSONObject("{\"v\":2}")));
        Assert.assertFalse(ModuleConfiguration.extractGzipFlag(null));
    }

    /** Bodies under 1 KB are not compressed; bigger ones become gzip that decodes back to the body. */
    @Test
    public void gzipRequestBody_threshold() throws IOException {
        ModuleLog log = new ModuleLog();
        String small = new String(new char[UtilsNetworking.REQUEST_COMPRESSION_MIN_BYTES - 1]).replace('\0', 'b');
        String big = new String(new char[UtilsNetworking.REQUEST_COMPRESSION_MIN_BYTES]).replace('\0', 'b');

        Assert.assertEquals(0, UtilsNetworking.gzipRequestBody(small, log).length);
        byte[] compressed = UtilsNetworking.gzipRequestBody(big, log);
        Assert.assertTrue(compressed.length > 0);
        Assert.assertTrue(compressed.length < big.length());
        Assert.assertEquals(big, gunzip(compressed));
    }

    private CountlyConfig config(@NonNull String serverUrl) {
        return TestUtils.createBaseConfig().setServerURL(serverUrl).setHttpPostForced(true)
            .setTrackOrientationChanges(false).enableManualSessionControl();
    }

    /** Queues a direct request that carries the given marker and payload, and asks the queue to send it. */
    private static void addDirectRequest(@NonNull Countly countly, @NonNull String marker, @NonNull String payload) {
        Map<String, String> request = new HashMap<>();
        request.put("marker", marker);
        request.put("payload", payload);
        countly.requestQueue().addDirectRequest(request);
        countly.requestQueue().attemptToSendStoredRequests();
    }

    private static String gunzip(@NonNull byte[] compressed) throws IOException {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
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

    private TestServer startServer(boolean readsGzip) throws IOException {
        TestServer testServer = new TestServer(readsGzip);
        servers.add(testServer);
        return testServer;
    }

    /** A request as the local server received it, with its body decoded when it was compressed. */
    private static class Received {
        final String path;
        final String contentType;
        final byte[] raw;
        final boolean compressed;
        final String body;

        Received(@NonNull String path, String contentType, @NonNull byte[] raw) throws IOException {
            this.path = path;
            this.contentType = contentType;
            this.raw = raw;
            this.compressed = path.contains("content_encoding=gzip");
            this.body = compressed ? gunzip(raw) : new String(raw, StandardCharsets.UTF_8);
        }
    }

    /**
     * A local server that records every request. It answers the behavior settings request with "gzip": true when
     * it reads gzip and the request asked for it, and every other request with success, except the first compressed
     * request while "refuseNextCompressed" is set, which gets 400.
     */
    private static class TestServer {
        final MockWebServer server = new MockWebServer();
        final List<Received> received = new CopyOnWriteArrayList<>();
        final String url;
        volatile boolean refuseNextCompressed = false;
        volatile int answeredSettings = 0;

        TestServer(final boolean readsGzip) throws IOException {
            server.setDispatcher(new Dispatcher() {
                @NonNull @Override public MockResponse dispatch(@NonNull RecordedRequest request) {
                    Received r;
                    try {
                        r = new Received(request.getPath(), request.getHeader("Content-Type"), request.getBody().readByteArray());
                    } catch (IOException e) {
                        return new MockResponse().setResponseCode(400).setBody("{\"result\":\"Request body is not valid gzip\"}");
                    }
                    received.add(r);

                    if (r.body.contains("method=sc")) {
                        answeredSettings++;
                        String gzip = readsGzip && r.body.contains("gzip=1") ? ",\"gzip\":true" : "";
                        return new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                            .setBody("{\"v\":2,\"t\":" + System.currentTimeMillis() + ",\"c\":{}" + gzip + "}");
                    }
                    if (r.compressed && refuseNextCompressed) {
                        refuseNextCompressed = false;
                        return new MockResponse().setResponseCode(400).setHeader("Content-Type", "application/json")
                            .setBody("{\"result\":\"Missing parameter \\\"app_key\\\" or \\\"device_id\\\"\"}");
                    }
                    return new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                        .setBody("{\"result\":\"Success\"}");
                }
            });
            server.start();
            String serverUrl = server.url("/").toString();
            url = serverUrl.substring(0, serverUrl.length() - 1);
        }

        /** Waits up to 30 seconds for a request whose body contains every marker. */
        boolean waitFor(@NonNull String... markers) throws Exception {
            return waitUntil(() -> !matching(markers).isEmpty());
        }

        /** Waits up to 30 seconds for the first request whose body contains the marker, and returns it. */
        @NonNull Received waitForRequest(@NonNull String marker) throws Exception {
            Assert.assertTrue("no request with " + marker, waitFor(marker));
            return matching(marker).get(0);
        }

        /** The first received request whose body contains the marker, or null. */
        Received first(@NonNull String marker) {
            List<Received> found = matching(marker);
            return found.isEmpty() ? null : found.get(0);
        }

        /** The received requests whose body contains every marker, in the order they arrived. */
        List<Received> matching(@NonNull String... markers) {
            List<Received> found = new ArrayList<>();
            for (Received r : received) {
                boolean all = true;
                for (String marker : markers) {
                    if (!r.body.contains(marker)) {
                        all = false;
                        break;
                    }
                }
                if (all) {
                    found.add(r);
                }
            }
            return found;
        }
    }
}
