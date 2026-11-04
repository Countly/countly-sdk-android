package ly.count.android.sdk.hub;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class HubRelayTest {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String NAVIGATION = "com.example.navigation";
    private static final String MUSIC = "com.example.music";

    private final HubAllowedApp navigation = new HubAllowedApp(NAVIGATION, "NAV_KEY");
    private final HubAllowedApp music = new HubAllowedApp(MUSIC, "MUSIC_KEY");
    private HubRelay relay;

    @After
    public void tearDown() {
        if (relay != null) {
            relay.shutdown();
        }
    }

    /**
     * Receives the outcome of one request.
     */
    private static final class Outcome implements HubRelay.Completion {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicInteger calls = new AtomicInteger();
        volatile HubResponse response;
        volatile String failure;

        @Override
        public void complete(HubResponse response, String failure) {
            this.response = response;
            this.failure = failure;
            calls.incrementAndGet();
            done.countDown();
        }

        Outcome await() throws InterruptedException {
            Assert.assertTrue("no outcome arrived", done.await(10, TimeUnit.SECONDS));
            return this;
        }
    }

    /**
     * Holds every request until released, recording the query of each one it was given.
     */
    private static final class GatedUplink implements HubUplink {
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch started = new CountDownLatch(1);
        final List<String> sent = Collections.synchronizedList(new ArrayList<String>());

        @Override
        public HubResponse send(HubRequest request) throws IOException {
            sent.add(request.getQuery());
            started.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) {
                    throw new IOException("not released");
                }
            } catch (InterruptedException e) {
                throw new IOException("interrupted");
            }
            return success();
        }
    }

    private static HubRequest request(String query, int timeoutMillis) {
        return new HubRequest("GET", "/i", query, Collections.<String, String>emptyMap(), null, timeoutMillis);
    }

    private static HubRequest post(String appKey, int bodyBytes) {
        char[] filler = new char[bodyBytes - 2];
        Arrays.fill(filler, 'x');
        return new HubRequest("POST", "/i", "app_key=" + appKey, Collections.singletonMap("Content-Type", "application/x-www-form-urlencoded"),
            ("x=" + new String(filler)).getBytes(UTF_8), 30_000);
    }

    private static HubResponse success() {
        return new HubResponse(200, Collections.<String, String>emptyMap(), "{\"result\":\"Success\"}".getBytes(UTF_8));
    }

    private HubRelay relayWith(HubUplink uplink, int maxQueued, int maxPerApp, long maxBodyBytes, long backgroundMaxWaitMillis) {
        RequestGate gate = new RequestGate(new HashSet<>(HubConfig.DEFAULT_ALLOWED_PATHS), 1024);
        relay = new HubRelay(gate, uplink, new HubRelay.Limits(maxQueued, maxPerApp, maxBodyBytes, 30_000, backgroundMaxWaitMillis), Thread::new);
        return relay;
    }

    private HubRelay relayWith(HubUplink uplink, int maxQueued, int maxPerApp, long maxBodyBytes) {
        return relayWith(uplink, maxQueued, maxPerApp, maxBodyBytes, HubRelay.DEFAULT_BACKGROUND_MAX_WAIT_MILLIS);
    }

    private HubRelay relayWith(HubUplink uplink) {
        return relayWith(uplink, 10, 10, 1024 * 1024);
    }

    private void awaitWaiting(int count) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (relay.waitingCount() < count && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        Assert.assertEquals(count, relay.waitingCount());
    }

    /**
     * An admitted request returns at once, is sent to the allowed path it ends with, and its response
     * reaches the completion exactly once.
     */
    @Test
    public void admittedRequest_isDeliveredToTheCompletion() throws Exception {
        List<String> paths = Collections.synchronizedList(new ArrayList<String>());
        relayWith(request -> {
            paths.add(request.getPath());
            return success();
        });
        Outcome outcome = new Outcome();

        Assert.assertNull(relay.submit(navigation, request("app_key=NAV_KEY", 30_000).withPath("/countly/i"), outcome));

        outcome.await();
        Assert.assertEquals(200, outcome.response.getStatus());
        Assert.assertNull(outcome.failure);
        Assert.assertEquals(Collections.singletonList("/i"), paths);
        Assert.assertEquals(1, relay.stats.delivered(NAVIGATION));
        Thread.sleep(50);
        Assert.assertEquals(1, outcome.calls.get());
    }

    /**
     * A request with another app's key never reaches the uplink. The app gets a 403 that is not final,
     * so its SDK keeps the request until the hub's list of keys allows it.
     */
    @Test
    public void appKeyRefusal_isNotFinal() throws Exception {
        AtomicInteger sent = new AtomicInteger();
        relayWith(request -> {
            sent.incrementAndGet();
            return success();
        });
        Outcome outcome = new Outcome();

        HubResponse refusal = relay.submit(navigation, request("app_key=MUSIC_KEY", 30_000), outcome);

        Assert.assertNotNull(refusal);
        Assert.assertEquals(403, refusal.getStatus());
        Assert.assertNull(refusal.getHeader(HubProtocol.FINAL_REFUSAL_HEADER));
        Assert.assertEquals("{\"error\":\"app_key is not allowed for com.example.navigation\"}", new String(refusal.getBody(), UTF_8));
        Assert.assertEquals(0, sent.get());
        Assert.assertEquals(1, relay.stats.refused(NAVIGATION));
        Assert.assertEquals(1, outcome.done.getCount());
    }

    /**
     * A request that can never be relayed is refused with the final refusal header, which tells the
     * SDK to drop it instead of retrying it forever.
     */
    @Test
    public void unrelayableRequest_isRefusedForGood() throws Exception {
        relayWith(request -> success());

        HubResponse refusal = relay.submit(navigation, request("app_key=NAV_KEY", 30_000).withPath("/o/apps/mine"), new Outcome());

        Assert.assertNotNull(refusal);
        Assert.assertEquals(400, refusal.getStatus());
        Assert.assertEquals("path /o/apps/mine is not relayed", refusal.getHeader(HubProtocol.FINAL_REFUSAL_HEADER));
        Assert.assertEquals("application/json", refusal.getHeader("Content-Type"));
    }

    /**
     * An uplink failure reaches the completion as a failure, which the app's SDK treats like a network
     * failure, and the uplink goes on with the next request.
     */
    @Test
    public void uplinkFailure_reachesTheCompletion() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        relayWith(request -> {
            if (calls.incrementAndGet() == 1) {
                throw new IOException("no route to host");
            }
            return success();
        });
        Outcome first = new Outcome();
        Outcome second = new Outcome();

        relay.submit(navigation, request("app_key=NAV_KEY", 30_000), first);
        relay.submit(navigation, request("app_key=NAV_KEY", 30_000), second);

        Assert.assertEquals("no route to host", first.await().failure);
        Assert.assertNull(first.response);
        Assert.assertEquals(200, second.await().response.getStatus());
        Assert.assertEquals(1, relay.stats.failed(NAVIGATION));
    }

    /**
     * An uplink that crashes, or a completion that throws, does not stop the uplink thread.
     */
    @Test
    public void crashes_doNotStopTheUplink() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        relayWith(request -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("bug");
            }
            return success();
        });
        Outcome crashed = new Outcome();
        HubRelay.Completion throwing = (response, failure) -> {
            throw new IllegalStateException("bug in completion");
        };
        Outcome last = new Outcome();

        relay.submit(navigation, request("app_key=NAV_KEY", 30_000), crashed);
        relay.submit(navigation, request("app_key=NAV_KEY", 30_000), throwing);
        relay.submit(navigation, request("app_key=NAV_KEY", 30_000), last);

        Assert.assertEquals("the uplink failed", crashed.await().failure);
        Assert.assertEquals(200, last.await().response.getStatus());
        Assert.assertEquals(2, relay.stats.delivered(NAVIGATION));
    }

    /**
     * Requests are sent one at a time, in the order they were admitted.
     */
    @Test
    public void requests_areSentOneAtATime() throws Exception {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        List<String> order = Collections.synchronizedList(new ArrayList<String>());
        relayWith(request -> {
            int now = inFlight.incrementAndGet();
            maxInFlight.set(Math.max(maxInFlight.get(), now));
            order.add(request.getQuery());
            try {
                Thread.sleep(20);
            } catch (InterruptedException ignored) {
            }
            inFlight.decrementAndGet();
            return success();
        });

        Outcome[] outcomes = new Outcome[5];
        for (int i = 0; i < outcomes.length; i++) {
            outcomes[i] = new Outcome();
            relay.submit(navigation, request("app_key=NAV_KEY&n=" + i, 30_000), outcomes[i]);
        }
        for (Outcome outcome : outcomes) {
            Assert.assertEquals(200, outcome.await().response.getStatus());
        }

        Assert.assertEquals(1, maxInFlight.get());
        Assert.assertEquals(Arrays.asList("app_key=NAV_KEY&n=0", "app_key=NAV_KEY&n=1", "app_key=NAV_KEY&n=2", "app_key=NAV_KEY&n=3", "app_key=NAV_KEY&n=4"), order);
    }

    /**
     * Requests the app is waiting on, such as remote config, are sent before requests from the SDK's
     * request queue, which carry "rr"; requests of one kind keep their order.
     */
    @Test
    public void immediateRequests_goBeforeQueuedOnes() throws Exception {
        GatedUplink uplink = new GatedUplink();
        relayWith(uplink);
        Outcome first = new Outcome();
        relay.submit(navigation, request("app_key=NAV_KEY&first", 30_000), first);
        Assert.assertTrue(uplink.started.await(5, TimeUnit.SECONDS));

        Outcome[] outcomes = new Outcome[4];
        for (int i = 0; i < outcomes.length; i++) {
            outcomes[i] = new Outcome();
        }
        relay.submit(navigation, request("app_key=NAV_KEY&rr=2&q1", 30_000), outcomes[0]);
        relay.submit(navigation, request("app_key=NAV_KEY&rr=1&q2", 30_000), outcomes[1]);
        relay.submit(navigation, request("method=rc&app_key=NAV_KEY", 30_000), outcomes[2]);
        relay.submit(navigation, request("method=sc&app_key=NAV_KEY", 30_000), outcomes[3]);
        awaitWaiting(4);
        uplink.release.countDown();

        for (Outcome outcome : outcomes) {
            outcome.await();
        }
        Assert.assertEquals(Arrays.asList("app_key=NAV_KEY&first", "method=rc&app_key=NAV_KEY", "method=sc&app_key=NAV_KEY", "app_key=NAV_KEY&rr=2&q1", "app_key=NAV_KEY&rr=1&q2"), uplink.sent);
    }

    /**
     * A request from the SDK's request queue that has waited longer than the limit is sent before the
     * requests the apps are waiting on, so a steady stream of those cannot hold queued data back for good.
     */
    @Test
    public void queuedRequest_isNotHeldBackForever() throws Exception {
        GatedUplink uplink = new GatedUplink();
        relayWith(uplink, 10, 10, 1024 * 1024, 200);
        relay.submit(navigation, request("app_key=NAV_KEY&first", 30_000), new Outcome());
        Assert.assertTrue(uplink.started.await(5, TimeUnit.SECONDS));
        Outcome queued = new Outcome();
        relay.submit(navigation, request("app_key=NAV_KEY&rr=0&queued", 30_000), queued);
        Thread.sleep(300);

        Outcome[] immediate = new Outcome[3];
        for (int i = 0; i < immediate.length; i++) {
            immediate[i] = new Outcome();
            relay.submit(music, request("method=rc&app_key=MUSIC_KEY&n=" + i, 30_000), immediate[i]);
        }
        awaitWaiting(4);
        uplink.release.countDown();

        queued.await();
        for (Outcome outcome : immediate) {
            outcome.await();
        }
        Assert.assertEquals(Arrays.asList("app_key=NAV_KEY&first", "app_key=NAV_KEY&rr=0&queued", "method=rc&app_key=MUSIC_KEY&n=0", "method=rc&app_key=MUSIC_KEY&n=1",
            "method=rc&app_key=MUSIC_KEY&n=2"), uplink.sent);
    }

    /**
     * When the uplink queue is full, a further request is turned away right away.
     */
    @Test
    public void fullQueue_turnsAwayRightAway() throws Exception {
        GatedUplink uplink = new GatedUplink();
        relayWith(uplink, 1, 10, 1024 * 1024);
        relay.submit(navigation, request("app_key=NAV_KEY", 30_000), new Outcome());
        Assert.assertTrue(uplink.started.await(5, TimeUnit.SECONDS));
        relay.submit(navigation, request("app_key=NAV_KEY", 30_000), new Outcome());

        long before = System.currentTimeMillis();
        try {
            relay.submit(music, request("app_key=MUSIC_KEY", 30_000), new Outcome());
            Assert.fail("expected the hub to be busy");
        } catch (IOException expected) {
            Assert.assertEquals("the hub is busy", expected.getMessage());
            Assert.assertTrue(System.currentTimeMillis() - before < 1_000);
            Assert.assertEquals(1, relay.stats.busy(MUSIC));
        } finally {
            uplink.release.countDown();
        }
    }

    /**
     * One app cannot take every place: once it has its share waiting or in flight, its further requests
     * are turned away while other apps are still admitted. Its places come back once answered.
     */
    @Test
    public void perAppLimit_turnsAwayOnlyThatApp() throws Exception {
        GatedUplink uplink = new GatedUplink();
        relayWith(uplink, 10, 2, 1024 * 1024);
        Outcome inFlight = new Outcome();
        relay.submit(navigation, request("app_key=NAV_KEY", 30_000), inFlight);
        Assert.assertTrue(uplink.started.await(5, TimeUnit.SECONDS));
        Outcome waiting = new Outcome();
        relay.submit(navigation, request("app_key=NAV_KEY", 30_000), waiting);

        try {
            relay.submit(navigation, request("app_key=NAV_KEY", 30_000), new Outcome());
            Assert.fail("expected the third request of one app to be turned away");
        } catch (IOException expected) {
            Assert.assertEquals(1, relay.stats.busy(NAVIGATION));
        }
        Outcome other = new Outcome();
        Assert.assertNull(relay.submit(music, request("app_key=MUSIC_KEY", 30_000), other));

        uplink.release.countDown();
        inFlight.await();
        waiting.await();
        other.await();
        Outcome later = new Outcome();
        relay.submit(navigation, request("app_key=NAV_KEY", 30_000), later);
        Assert.assertEquals(200, later.await().response.getStatus());
    }

    /**
     * The body bytes held for waiting and in-flight requests are limited; a request that would go over
     * the limit is turned away, but a single large request is admitted when nothing else is held.
     */
    @Test
    public void bodyBudget_turnsAwayLargeRequests() throws Exception {
        GatedUplink uplink = new GatedUplink();
        relayWith(uplink, 10, 10, 100);
        Outcome large = new Outcome();
        Assert.assertNull(relay.submit(navigation, post("NAV_KEY", 500), large));
        Assert.assertTrue(uplink.started.await(5, TimeUnit.SECONDS));

        try {
            relay.submit(music, post("MUSIC_KEY", 10), new Outcome());
            Assert.fail("expected a request over the body budget to be turned away");
        } catch (IOException expected) {
            Assert.assertEquals("the hub is busy", expected.getMessage());
        }
        Assert.assertNull(relay.submit(music, request("app_key=MUSIC_KEY", 30_000), new Outcome()));

        uplink.release.countDown();
        large.await();
        Outcome after = new Outcome();
        long deadline = System.currentTimeMillis() + 5_000;
        while (true) {
            try {
                relay.submit(music, post("MUSIC_KEY", 50), after);
                break;
            } catch (IOException stillHeld) {
                Assert.assertTrue("the body budget was not released", System.currentTimeMillis() < deadline);
                Thread.sleep(10);
            }
        }
        Assert.assertEquals(200, after.await().response.getStatus());
    }

    /**
     * A request that waited past its budget is not sent: its caller has given up and retries, and
     * sending it anyway could store the same data twice.
     */
    @Test
    public void expiredRequest_isNotSent() throws Exception {
        GatedUplink uplink = new GatedUplink();
        relayWith(uplink);
        relay.submit(navigation, request("app_key=NAV_KEY&first", 30_000), new Outcome());
        Assert.assertTrue(uplink.started.await(5, TimeUnit.SECONDS));
        Outcome expiring = new Outcome();
        relay.submit(navigation, request("app_key=NAV_KEY&expiring", 1_000), expiring);

        Thread.sleep(1_300);
        uplink.release.countDown();

        Assert.assertEquals("the request waited longer than its budget and was not sent", expiring.await().failure);
        Assert.assertEquals(Collections.singletonList("app_key=NAV_KEY&first"), uplink.sent);
        Assert.assertEquals(1, relay.stats.expired(NAVIGATION));
    }

    /**
     * The time a caller is told to wait covers the request's budget in the queue, the uplink's connect
     * and read timeouts and a margin, with the budget kept between 1 second and 10 minutes.
     */
    @Test
    public void replyWithin_coversTheBudgetAndTheUplink() {
        relayWith(request -> success());

        Assert.assertEquals(60_000 + 60_000 + 10_000, relay.replyWithinMillis(request("app_key=NAV_KEY", 60_000)));
        Assert.assertEquals(1_000 + 60_000 + 10_000, relay.replyWithinMillis(request("app_key=NAV_KEY", 0)));
        Assert.assertEquals(600_000 + 60_000 + 10_000, relay.replyWithinMillis(request("app_key=NAV_KEY", Integer.MAX_VALUE)));
    }

    /**
     * Shutting down fails the waiting requests and the one in flight, and turns new ones away.
     */
    @Test
    public void shutdown_failsPendingRequests() throws Exception {
        GatedUplink uplink = new GatedUplink();
        relayWith(uplink);
        Outcome inFlight = new Outcome();
        relay.submit(navigation, request("app_key=NAV_KEY", 30_000), inFlight);
        Assert.assertTrue(uplink.started.await(5, TimeUnit.SECONDS));
        Outcome waiting = new Outcome();
        relay.submit(navigation, request("app_key=NAV_KEY", 30_000), waiting);
        awaitWaiting(1);

        relay.shutdown();

        Assert.assertEquals("the hub is stopping", waiting.await().failure);
        Assert.assertNotNull(inFlight.await().failure);
        try {
            relay.submit(navigation, request("app_key=NAV_KEY", 30_000), new Outcome());
            Assert.fail("expected a stopped hub to turn requests away");
        } catch (IOException expected) {
            Assert.assertEquals("the hub is stopping", expected.getMessage());
        }
    }

    /**
     * The refusal body is valid JSON even when the reason contains quotes or control characters, and
     * only a final refusal carries the final refusal header.
     */
    @Test
    public void refusal_escapesTheReason() {
        HubResponse refusal = HubRelay.refusal(403, "path /i\"x\n is not relayed", false);
        Assert.assertEquals("{\"error\":\"path /i\\\"x\\u000a is not relayed\"}", new String(refusal.getBody(), UTF_8));
        Assert.assertNull(refusal.getHeader(HubProtocol.FINAL_REFUSAL_HEADER));
        Assert.assertEquals("no", HubRelay.refusal(400, "no", true).getHeader(HubProtocol.FINAL_REFUSAL_HEADER));
    }
}
