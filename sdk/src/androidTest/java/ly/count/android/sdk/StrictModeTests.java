package ly.count.android.sdk;

import android.net.TrafficStats;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Runs SDK flows under StrictMode detectAll, the configuration integrators use to review the SDK,
 * and checks the violations it reports are not the SDK's.
 */
@RunWith(AndroidJUnit4.class)
public class StrictModeTests {
    private static final int APP_SOCKET_TAG = 0x1234;

    private StrictModeRecorder recorder;
    private MockWebServer server;

    /**
     * Every test starts with an empty request queue.
     */
    @Before
    public void setUp() {
        TestUtils.getCountlyStore().clear();
    }

    /**
     * Halts the SDK, clears the thread's socket tag, restores the StrictMode policies and stops the
     * server.
     */
    @After
    public void tearDown() throws IOException {
        Countly.sharedInstance().halt();
        TrafficStats.clearThreadStatsTag();
        if (recorder != null) {
            recorder.stop();
            recorder = null;
        }
        if (server != null) {
            server.shutdown();
            server = null;
        }
    }

    /**
     * The request queue's begin_session request and the immediate health check request made at
     * init both reach the server, and StrictMode reports none of their sockets as untagged.
     */
    @Test
    public void requestSockets_areTagged() throws Exception {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P);
        server = startServer();
        recorder = StrictModeRecorder.startVm("UntaggedSocketViolation", () -> openUntaggedSocket(server));

        Countly countly = Countly.sharedInstance().init(configFor(server).enableManualSessionControl());
        countly.sessions().beginSession();

        List<String> received = takeRequestsUntil(server, 10_000, "begin_session=1", "hc=");
        Assert.assertTrue("begin_session not received: " + received, anyContains(received, "begin_session=1"));
        Assert.assertTrue("health check not received: " + received, anyContains(received, "hc="));
        List<String> sdkViolations = recorder.violationsThrough("ly.count.android.sdk.");
        Assert.assertTrue("untagged SDK sockets: " + sdkViolations, sdkViolations.isEmpty());
    }

    /**
     * Immediate and preflight requests run on a thread that already carries a socket tag, or none,
     * leave the thread with that same tag afterwards, and their own sockets are still tagged while
     * they run.
     */
    @Test
    public void workerRequests_restoreTheThreadSocketTag() throws Exception {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P);
        server = startServer();
        Countly countly = new Countly().init(configFor(server));
        ConnectionProcessor cp = countly.connectionQueue_.createConnectionProcessor();
        recorder = StrictModeRecorder.startVm("UntaggedSocketViolation", () -> openUntaggedSocket(server));

        TrafficStats.clearThreadStatsTag();
        new ImmediateRequestMaker().doInBackground("app_key=" + TestUtils.commonAppKey + "&hc=untagged", null, cp, false, true, null, new ModuleLog());
        new PreflightRequestMaker().doInBackground(server.url("/preflight-untagged").toString(), null, cp, false, true, null, new ModuleLog());
        Assert.assertEquals(-1, TrafficStats.getThreadStatsTag());

        TrafficStats.setThreadStatsTag(APP_SOCKET_TAG);
        new ImmediateRequestMaker().doInBackground("app_key=" + TestUtils.commonAppKey + "&hc=tagged", null, cp, false, true, null, new ModuleLog());
        new PreflightRequestMaker().doInBackground(server.url("/preflight-tagged").toString(), null, cp, false, true, null, new ModuleLog());
        Assert.assertEquals(APP_SOCKET_TAG, TrafficStats.getThreadStatsTag());

        List<String> received = takeRequestsUntil(server, 5_000, "hc=untagged", "preflight-untagged", "hc=tagged", "preflight-tagged");
        Assert.assertTrue("requests not received: " + received, allSeen(received, "hc=untagged", "preflight-untagged", "hc=tagged", "preflight-tagged"));
        List<String> sdkViolations = recorder.violationsThrough("ly.count.android.sdk.");
        Assert.assertTrue("untagged SDK sockets: " + sdkViolations, sdkViolations.isEmpty());
    }

    /**
     * Init resolves the crash metrics that read the disk in the background, so a crash recorded on
     * a StrictMode thread reads none of them from disk there, and the crash still carries them.
     */
    @Test
    public void crashMetrics_areNotReadFromDiskOnTheRecordingThread() throws Exception {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P);
        DeviceInfo.resetDiskBackedMetricsForTests();
        Countly countly = new Countly().init(TestUtils.createBaseConfig());
        Utils.runInBackground(() -> { }).get(5, TimeUnit.SECONDS);

        recorder = StrictModeRecorder.startThread("DiskReadViolation", () -> new File(TestUtils.getContext().getFilesDir(), "strict-mode-probe").exists());
        countly.crashes().recordHandledException(new Exception("recorded on a StrictMode thread"));
        List<String> deviceInfoReads = recorder.violationsThrough("ly.count.android.sdk.DeviceInfo");
        recorder.stop();
        recorder = null;

        Assert.assertTrue("disk reads while collecting crash metrics: " + deviceInfoReads, deviceInfoReads.isEmpty());
        JSONObject crash = null;
        for (Map<String, String> request : TestUtils.getCurrentRQ()) {
            if (request.containsKey("crash")) {
                crash = new JSONObject(request.get("crash"));
            }
        }
        Assert.assertNotNull(crash);
        Assert.assertTrue(crash.getString("_root").equals("true") || crash.getString("_root").equals("false"));
        Assert.assertTrue(Long.parseLong(crash.getString("_ram_total")) > 0);
    }

    /**
     * Starts a server that answers every request with a successful, empty JSON result. Each
     * response closes its connection, so every request opens a new socket that StrictMode checks
     * instead of reusing a pooled one.
     */
    private static MockWebServer startServer() throws IOException {
        MockWebServer mockServer = new MockWebServer();
        mockServer.setDispatcher(new Dispatcher() {
            @NonNull @Override public MockResponse dispatch(@NonNull RecordedRequest recordedRequest) {
                return new MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setHeader("Connection", "close")
                    .setBody("{\"result\":\"Success\"}");
            }
        });
        mockServer.start();
        return mockServer;
    }

    /**
     * A base config pointing at the given server instead of the unreachable test URL.
     */
    private static CountlyConfig configFor(@NonNull MockWebServer mockServer) {
        return TestUtils.createBaseConfig().setServerURL(mockServer.url("/").toString());
    }

    /**
     * Opens and closes a socket to the server without a thread tag, which StrictMode reports.
     */
    private static void openUntaggedSocket(@NonNull MockWebServer mockServer) {
        TrafficStats.clearThreadStatsTag();
        try (Socket socket = new Socket(mockServer.getHostName(), mockServer.getPort())) {
            Assert.assertTrue(socket.isConnected());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Collects the path and body of each request the server receives, until every marker was seen
     * in one of them or the timeout passes.
     */
    private static List<String> takeRequestsUntil(@NonNull MockWebServer mockServer, long timeoutMs, @NonNull String... markers) throws InterruptedException {
        List<String> received = new ArrayList<>();
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!allSeen(received, markers)) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                break;
            }
            RecordedRequest request = mockServer.takeRequest(remaining, TimeUnit.MILLISECONDS);
            if (request == null) {
                break;
            }
            received.add(request.getPath() + " " + request.getBody().readUtf8());
        }
        return received;
    }

    /**
     * Whether every marker appears in at least one of the received requests.
     */
    private static boolean allSeen(@NonNull List<String> received, @NonNull String... markers) {
        for (String marker : markers) {
            if (!anyContains(received, marker)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether any of the received requests contains the marker.
     */
    private static boolean anyContains(@NonNull List<String> received, @NonNull String marker) {
        for (String request : received) {
            if (request.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
