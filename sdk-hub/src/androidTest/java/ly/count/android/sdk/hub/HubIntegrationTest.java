package ly.count.android.sdk.hub;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
    private static final int LARGE_REPLY_PADDING = 300 * 1024;
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
                String path = request.getPath() == null ? "" : request.getPath();
                MockResponse response = new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json");
                if (path.contains("marker=bigreply")) {
                    char[] padding = new char[LARGE_REPLY_PADDING];
                    Arrays.fill(padding, 'x');
                    return response.setBody("{\"result\":\"Success\",\"pad\":\"" + new String(padding) + "\"}");
                }
                if (path.contains("marker=slow")) {
                    return response.setHeadersDelay(8, TimeUnit.SECONDS).setBody("{\"result\":\"Success\"}");
                }
                return response.setBody("{\"result\":\"Success\"}");
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

    private static HubRequest post(byte[] body) {
        return new HubRequest("POST", "/i", null, Collections.singletonMap("Content-Type", "application/x-www-form-urlencoded"), body, 30_000);
    }

    private static byte[] crashBody(String marker, int paddingBytes) {
        char[] padding = new char[paddingBytes];
        Arrays.fill(padding, 'x');
        return ("app_key=" + TestHubService.APP_KEY + "&device_id=d&marker=" + marker + "&crash=" + new String(padding)).getBytes(UTF_8);
    }

    /**
     * @return the request's path and body, raw and URL-decoded, for searching
     */
    private static String text(RecordedRequest request) {
        String raw = (request.getPath() == null ? "" : request.getPath()) + "\n" + request.getBody().clone().readString(UTF_8);
        try {
            return raw + "\n" + URLDecoder.decode(raw, "UTF-8");
        } catch (IllegalArgumentException | UnsupportedEncodingException e) {
            return raw;
        }
    }

    private static RecordedRequest awaitRequest(String marker, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        do {
            for (RecordedRequest request : received) {
                if (text(request).contains(marker)) {
                    return request;
                }
            }
            Thread.sleep(50);
        } while (System.currentTimeMillis() < deadline);
        return null;
    }

    private void initSdk(String serverUrl, String deviceId) {
        context.getSharedPreferences("COUNTLY_STORE", Context.MODE_PRIVATE).edit().clear().commit();
        CountlyConfig config = new CountlyConfig(context, TestHubService.APP_KEY, serverUrl)
            .setDeviceId(deviceId)
            .setLoggingEnabled(true);
        CountlyHub.useHub(config, new HubClientConfig(context, context.getPackageName()));
        Countly.sharedInstance().init(config);
    }

    private void killHubProcess() {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningAppProcessInfo process : activityManager.getRunningAppProcesses()) {
            if ((context.getPackageName() + ":hub").equals(process.processName)) {
                android.os.Process.killProcess(process.pid);
                return;
            }
        }
        Assert.fail("the hub process is not running");
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
     * A body above the inline limit, like a crash report with a native dump, travels in shared memory,
     * or through a file descriptor before Android 8.1, and arrives byte for byte.
     */
    @Test
    public void channel_largeBodyArrivesComplete() throws Exception {
        byte[] body = crashBody("large1", 1024 * 1024);

        HubResponse response = channel().exchange(post(body));

        Assert.assertEquals(200, response.getStatus());
        RecordedRequest recorded = awaitRequest("marker=large1", 10_000);
        Assert.assertNotNull(recorded);
        Assert.assertArrayEquals(body, recorded.getBody().clone().readByteArray());
    }

    /**
     * A response above the inline limit, like a large remote config, comes back complete.
     */
    @Test
    public void channel_largeReplyArrivesComplete() throws Exception {
        HubResponse response = channel().exchange(get("/o/sdk", "method=rc&app_key=" + TestHubService.APP_KEY + "&marker=bigreply1"));

        Assert.assertEquals(200, response.getStatus());
        String body = new String(response.getBody(), UTF_8);
        Assert.assertEquals(LARGE_REPLY_PADDING + "{\"result\":\"Success\",\"pad\":\"\"}".length(), body.length());
        Assert.assertTrue(body.startsWith("{\"result\":\"Success\",\"pad\":\"xxx"));
        Assert.assertTrue(body.endsWith("xxx\"}"));
    }

    /**
     * A body above the hub's limit is refused for good, without reaching the server, so the SDK drops it.
     */
    @Test
    public void channel_oversizedBody_isRefusedForGood() throws Exception {
        HubResponse response = channel().exchange(post(crashBody("oversized1", 4 * 1024 * 1024 + 1024)));

        Assert.assertEquals(413, response.getStatus());
        Assert.assertNotNull(response.getHeader(HubProtocol.FINAL_REFUSAL_HEADER));
        Assert.assertNull(awaitRequest("marker=oversized1", 1_000));
    }

    /**
     * Data for an app key the app is not allowed to use is refused and never reaches the server. The
     * refusal is not final, so the SDK keeps the data until the hub allows the key.
     */
    @Test
    public void channel_refusesAnotherAppsKey_notForGood() throws Exception {
        HubResponse response = channel().exchange(get("/i", "app_key=SOMEONE_ELSES_KEY&device_id=d&marker=foreign1"));

        Assert.assertEquals(403, response.getStatus());
        Assert.assertNull(response.getHeader(HubProtocol.FINAL_REFUSAL_HEADER));
        Assert.assertNull(awaitRequest("marker=foreign1", 1_000));
    }

    /**
     * A path outside the Countly SDK paths is refused for good and never reaches the server.
     */
    @Test
    public void channel_refusesAPathOutsideCountly_forGood() throws Exception {
        HubResponse response = channel().exchange(get("/o/apps/mine", "app_key=" + TestHubService.APP_KEY + "&marker=path1"));

        Assert.assertEquals(400, response.getStatus());
        Assert.assertEquals("path /o/apps/mine is not relayed", response.getHeader(HubProtocol.FINAL_REFUSAL_HEADER));
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
     * Many requests of one app at the same moment, as on a vehicle start, are all admitted and
     * delivered: waiting for the network holds no binder thread of either process.
     */
    @Test
    public void channel_burstOfRequests_isAllDelivered() throws Exception {
        final HubChannel channel = channel();
        final int count = 25;
        final CountDownLatch done = new CountDownLatch(count);
        final AtomicInteger delivered = new AtomicInteger();
        final List<String> failures = new CopyOnWriteArrayList<>();
        for (int i = 0; i < count; i++) {
            final String query = "app_key=" + TestHubService.APP_KEY + "&device_id=d&marker=burst" + i + "x";
            new Thread(() -> {
                try {
                    if (channel.exchange(get("/i", query)).getStatus() == 200) {
                        delivered.incrementAndGet();
                    }
                } catch (IOException e) {
                    failures.add(e.getMessage());
                } finally {
                    done.countDown();
                }
            }).start();
        }

        Assert.assertTrue(done.await(60, TimeUnit.SECONDS));
        Assert.assertEquals("failures: " + failures, count, delivered.get());
    }

    /**
     * A request waiting for its response fails within moments when the hub process dies, instead of
     * waiting out its full time, and the next request binds to the restarted hub.
     */
    @Test
    public void channel_hubDiesMidRequest_failsFastAndRecovers() throws Exception {
        final HubChannel channel = channel();
        Assert.assertEquals(200, channel.exchange(get("/i", "app_key=" + TestHubService.APP_KEY + "&device_id=d&marker=warmup1")).getStatus());
        final AtomicReference<Throwable> outcome = new AtomicReference<>();
        final CountDownLatch finished = new CountDownLatch(1);
        new Thread(() -> {
            try {
                channel.exchange(get("/i", "app_key=" + TestHubService.APP_KEY + "&device_id=d&marker=slow1"));
            } catch (Throwable t) {
                outcome.set(t);
            } finally {
                finished.countDown();
            }
        }).start();
        Assert.assertNotNull(awaitRequest("marker=slow1", 5_000));

        killHubProcess();

        Assert.assertTrue("the waiting request did not fail when the hub died", finished.await(5, TimeUnit.SECONDS));
        Assert.assertTrue(String.valueOf(outcome.get()), outcome.get() instanceof IOException);

        HubResponse response = null;
        long deadline = System.currentTimeMillis() + 30_000;
        while (response == null && System.currentTimeMillis() < deadline) {
            try {
                response = channel.exchange(get("/i", "app_key=" + TestHubService.APP_KEY + "&device_id=d&marker=after1"));
            } catch (IOException notYet) {
                Thread.sleep(250);
            }
        }
        Assert.assertNotNull("the channel did not recover after the hub restarted", response);
        Assert.assertEquals(200, response.getStatus());
    }

    /**
     * The Countly SDK, configured with the hub, delivers a recorded event to the server through the
     * hub process.
     */
    @Test
    public void sdk_deliversEventsThroughTheHub() throws Exception {
        initSdk("https://countly.example.invalid", "hub-test-device");

        Countly.sharedInstance().events().recordEvent("hub_test_event");
        Countly.sharedInstance().requestQueue().attemptToSendStoredRequests();

        RecordedRequest recorded = awaitRequest("hub_test_event", 15_000);
        Assert.assertNotNull("the event did not reach the server through the hub", recorded);
        String request = text(recorded);
        Assert.assertTrue(request.contains("app_key=" + TestHubService.APP_KEY));
        Assert.assertTrue(request.contains("device_id=hub-test-device"));
        Assert.assertTrue(request.contains("checksum256="));
    }

    /**
     * A crash report, which the SDK always posts, reaches the server through the hub.
     */
    @Test
    public void sdk_crashReportGoesThroughTheHub() throws Exception {
        initSdk("https://countly.example.invalid", "hub-crash-device");

        Countly.sharedInstance().crashes().recordHandledException(new IllegalStateException("hub_crash_marker"));
        Countly.sharedInstance().requestQueue().attemptToSendStoredRequests();

        RecordedRequest recorded = awaitRequest("hub_crash_marker", 15_000);
        Assert.assertNotNull("the crash report did not reach the server through the hub", recorded);
        Assert.assertEquals("POST", recorded.getMethod());
        Assert.assertEquals("/i", recorded.getRequestUrl().encodedPath());
        Assert.assertTrue(text(recorded).contains("crash="));
    }

    /**
     * A profile picture is uploaded as multipart form data; a file name that is not ASCII reaches the
     * server unchanged, together with the file.
     */
    @Test
    public void sdk_profilePictureWithANonAsciiFileName_goesThroughTheHub() throws Exception {
        String fileName = "zdjęcie_ą.jpg";
        File picture = new File(context.getCacheDir(), fileName);
        try (FileOutputStream out = new FileOutputStream(picture)) {
            out.write("hub-picture-bytes".getBytes(UTF_8));
        }
        initSdk("https://countly.example.invalid", "hub-picture-device");

        Map<String, Object> properties = new HashMap<>();
        properties.put("name", "hub_picture_marker");
        properties.put("picturePath", picture.getAbsolutePath());
        Countly.sharedInstance().userProfile().setProperties(properties);
        Countly.sharedInstance().userProfile().save();
        Countly.sharedInstance().requestQueue().attemptToSendStoredRequests();

        RecordedRequest recorded = awaitRequest("hub_picture_marker", 15_000);
        Assert.assertNotNull("the profile picture did not reach the server through the hub", recorded);
        Assert.assertTrue(recorded.getHeader("Content-Type").startsWith("multipart/form-data; boundary="));
        String body = recorded.getBody().clone().readString(UTF_8);
        Assert.assertTrue(body.contains("filename=\"" + fileName + "\""));
        Assert.assertTrue(body.contains("hub-picture-bytes"));
    }

    /**
     * A binding made directly to the hub service, to hand it requests the hub client would never build.
     */
    private static final class DirectBinding implements ServiceConnection {
        final CountDownLatch connected = new CountDownLatch(1);
        volatile ICountlyHub hub;

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            hub = ICountlyHub.Stub.asInterface(service);
            connected.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    }

    /**
     * Ignores responses; the direct calls are answered in their reply.
     */
    private static final class IgnoringCallback extends IHubCallback.Stub {
        @Override
        public void onResponse(Bundle response) {
        }
    }

    private DirectBinding bindDirectly() throws InterruptedException {
        DirectBinding binding = new DirectBinding();
        Intent intent = new Intent(HubProtocol.ACTION_BIND).setPackage(context.getPackageName());
        Assert.assertTrue(context.bindService(intent, binding, Context.BIND_AUTO_CREATE));
        Assert.assertTrue(binding.connected.await(10, TimeUnit.SECONDS));
        return binding;
    }

    /**
     * A body the hub cannot read at the moment, here a file opened for writing only, is not refused for
     * good: the app is told to try again later, so its SDK keeps the request.
     */
    @Test
    public void direct_bodyThatCannotBeReadNow_isTurnedAwayForNow() throws Exception {
        DirectBinding binding = bindDirectly();
        File file = new File(context.getCacheDir(), "hub-write-only.body");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(crashBody("unreadable1", 100 * 1024));
        }
        ParcelFileDescriptor writeOnly = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_WRITE_ONLY);
        try {
            Bundle request = HubBundles.requestToBundle(post(null));
            request.putParcelable(HubProtocol.KEY_BODY_FILE, writeOnly);

            Bundle reply = binding.hub.submit(request, new IgnoringCallback());

            Assert.assertFalse(HubBundles.isAccepted(reply));
            Assert.assertEquals("the request body could not be read", reply.getString(HubProtocol.KEY_FAILURE));
            Assert.assertNull(awaitRequest("marker=unreadable1", 1_000));
        } finally {
            writeOnly.close();
            context.unbindService(binding);
            Assert.assertTrue(file.delete());
        }
    }

    /**
     * A request in a reply mode only a newer hub knows, as a newer client could send it, is refused but
     * not for good, so the app's SDK keeps it until the host app is updated.
     */
    @Test
    public void direct_replyModeOfANewerHub_isRefusedNotForGood() throws Exception {
        DirectBinding binding = bindDirectly();
        try {
            Bundle request = HubBundles.requestToBundle(get("/i", "app_key=" + TestHubService.APP_KEY + "&device_id=d&marker=mode1"));
            request.putInt(HubProtocol.KEY_REPLY_WHEN, 2);

            HubResponse response = HubBundles.responseFromBundle(binding.hub.submit(request, new IgnoringCallback()), HubChannel.MAX_RESPONSE_BYTES);

            Assert.assertEquals(501, response.getStatus());
            Assert.assertNull(response.getHeader(HubProtocol.FINAL_REFUSAL_HEADER));
            Assert.assertNull(awaitRequest("marker=mode1", 1_000));
        } finally {
            context.unbindService(binding);
        }
    }

    /**
     * The requests of content and feedback widgets, which the hub does not support, are refused for
     * good and never reach the server.
     */
    @Test
    public void channel_refusesUiFeatureRequests_forGood() throws Exception {
        HubChannel channel = channel();

        HubResponse widget = channel.exchange(get("/o/surveys/nps/widget", "widget_id=hubwidget1&shown=1&marker=ui1"));
        HubResponse content = channel.exchange(get("/o/sdk/content", "app_key=" + TestHubService.APP_KEY + "&marker=ui2"));

        Assert.assertEquals(400, widget.getStatus());
        Assert.assertEquals("path /o/surveys/nps/widget is not relayed", widget.getHeader(HubProtocol.FINAL_REFUSAL_HEADER));
        Assert.assertEquals(400, content.getStatus());
        Assert.assertEquals("path /o/sdk/content is not relayed", content.getHeader(HubProtocol.FINAL_REFUSAL_HEADER));
        Assert.assertNull(awaitRequest("marker=ui", 1_000));
    }

    /**
     * A server URL with a path prefix reaches the server's own paths: the hub sends to its configured
     * server URL and drops the prefix.
     */
    @Test
    public void sdk_serverUrlWithAPathPrefix_reachesTheServerPaths() throws Exception {
        initSdk("https://countly.example.invalid/countly", "hub-prefix-device");

        Countly.sharedInstance().events().recordEvent("hub_prefix_event");
        Countly.sharedInstance().requestQueue().attemptToSendStoredRequests();

        RecordedRequest recorded = awaitRequest("hub_prefix_event", 15_000);
        Assert.assertNotNull("the event did not reach the server through the hub", recorded);
        Assert.assertEquals("/i", recorded.getRequestUrl().encodedPath());
    }

    /**
     * A queued request the hub refuses for good is dropped by the SDK, and the requests queued after it
     * are still delivered instead of waiting behind it forever.
     */
    @Test
    public void sdk_requestRefusedForGood_isDroppedAndTheQueueMovesOn() throws Exception {
        initSdk("https://countly.example.invalid", "hub-drop-device");
        char[] padding = new char[4 * 1024 * 1024 + 512 * 1024];
        Arrays.fill(padding, 'x');
        Map<String, String> oversized = new HashMap<>();
        oversized.put("hub_oversized_marker", new String(padding));

        Countly.sharedInstance().requestQueue().addDirectRequest(oversized);
        Countly.sharedInstance().events().recordEvent("hub_after_oversized");
        Countly.sharedInstance().requestQueue().attemptToSendStoredRequests();

        Assert.assertNotNull("the request after the refused one was held back", awaitRequest("hub_after_oversized", 30_000));
        Assert.assertNull(awaitRequest("hub_oversized_marker", 0));
    }
}
