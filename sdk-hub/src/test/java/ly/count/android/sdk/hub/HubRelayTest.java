package ly.count.android.sdk.hub;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class HubRelayTest {
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private final HubAllowedApp navigation = new HubAllowedApp("com.example.navigation", "NAV_KEY");
    private HubRelay relay;

    @After
    public void tearDown() {
        if (relay != null) {
            relay.shutdown();
        }
    }

    private static HubRequest request(String appKey, int timeoutMillis) {
        return new HubRequest("GET", "/i", "app_key=" + appKey, Collections.<String, String>emptyMap(), null, timeoutMillis);
    }

    private static HubResponse success() {
        return new HubResponse(200, Collections.<String, String>emptyMap(), "{\"result\":\"Success\"}".getBytes(UTF_8));
    }

    private HubRelay relayWith(HubUplink uplink, int maxQueued) {
        relay = new HubRelay(new RequestGate(new HashSet<>(HubConfig.DEFAULT_ALLOWED_PATHS), 1024), uplink, maxQueued);
        return relay;
    }

    /**
     * An accepted request is sent through the uplink and its response returned and counted.
     */
    @Test
    public void acceptedRequest_isDelivered() throws IOException {
        AtomicInteger sent = new AtomicInteger();
        relayWith(request -> {
            sent.incrementAndGet();
            return success();
        }, 10);

        HubResponse response = relay.relay(navigation, request("NAV_KEY", 30_000));

        Assert.assertEquals(200, response.getStatus());
        Assert.assertEquals(1, sent.get());
        Assert.assertEquals(1, relay.stats.delivered("com.example.navigation"));
    }

    /**
     * A refused request never reaches the uplink, and the app gets a 403 with the reason.
     */
    @Test
    public void refusedRequest_neverReachesTheUplink() throws IOException {
        AtomicInteger sent = new AtomicInteger();
        relayWith(request -> {
            sent.incrementAndGet();
            return success();
        }, 10);

        HubResponse response = relay.relay(navigation, request("MUSIC_KEY", 30_000));

        Assert.assertEquals(403, response.getStatus());
        Assert.assertTrue(new String(response.getBody(), UTF_8).contains("app_key is not allowed"));
        Assert.assertEquals(0, sent.get());
        Assert.assertEquals(1, relay.stats.refused("com.example.navigation"));
    }

    /**
     * An uplink failure reaches the app as an IOException, which its SDK treats like a network failure.
     */
    @Test
    public void uplinkFailure_reachesTheApp() {
        relayWith(request -> {
            throw new IOException("no route to host");
        }, 10);

        try {
            relay.relay(navigation, request("NAV_KEY", 30_000));
            Assert.fail("expected the uplink failure");
        } catch (IOException expected) {
            Assert.assertEquals("no route to host", expected.getMessage());
        }
    }

    /**
     * Requests are sent one at a time: a second request waits until the first one is answered.
     */
    @Test
    public void requests_areSentOneAtATime() throws Exception {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        relayWith(request -> {
            int now = inFlight.incrementAndGet();
            maxInFlight.set(Math.max(maxInFlight.get(), now));
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
            }
            inFlight.decrementAndGet();
            return success();
        }, 10);

        Thread[] callers = new Thread[5];
        for (int i = 0; i < callers.length; i++) {
            callers[i] = new Thread(() -> {
                try {
                    relay.relay(navigation, request("NAV_KEY", 30_000));
                } catch (IOException ignored) {
                }
            });
            callers[i].start();
        }
        for (Thread caller : callers) {
            caller.join(10_000);
        }

        Assert.assertEquals(1, maxInFlight.get());
        Assert.assertEquals(5, relay.stats.delivered("com.example.navigation"));
    }

    /**
     * When the uplink queue is full, a further request fails right away instead of waiting.
     */
    @Test
    public void fullQueue_failsRightAway() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        relayWith(request -> {
            started.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
            return success();
        }, 1);

        Thread first = new Thread(() -> {
            try {
                relay.relay(navigation, request("NAV_KEY", 30_000));
            } catch (IOException ignored) {
            }
        });
        Thread second = new Thread(() -> {
            try {
                relay.relay(navigation, request("NAV_KEY", 30_000));
            } catch (IOException ignored) {
            }
        });
        first.start();
        Assert.assertTrue(started.await(5, TimeUnit.SECONDS));
        second.start();
        long deadline = System.currentTimeMillis() + 5_000;
        while (relay.waitingCount() < 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }

        long before = System.currentTimeMillis();
        try {
            relay.relay(navigation, request("NAV_KEY", 30_000));
            Assert.fail("expected the hub to be busy");
        } catch (IOException expected) {
            Assert.assertEquals("the hub is busy", expected.getMessage());
            Assert.assertTrue(System.currentTimeMillis() - before < 1_000);
        } finally {
            release.countDown();
            first.join(5_000);
            second.join(5_000);
        }
    }

    /**
     * A request the server does not answer in time fails once the app's wait is over.
     */
    @Test
    public void slowServer_timesOut() {
        relayWith(request -> {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
            return success();
        }, 10);

        long before = System.currentTimeMillis();
        try {
            relay.relay(navigation, request("NAV_KEY", 1_000));
            Assert.fail("expected a timeout");
        } catch (IOException expected) {
            long waited = System.currentTimeMillis() - before;
            Assert.assertTrue("waited " + waited + " ms", waited >= 900 && waited < 4_000);
        }
    }

    /**
     * The refusal body is valid JSON even when the reason contains quotes.
     */
    @Test
    public void refusal_escapesTheReason() {
        HubResponse refusal = HubRelay.refusal(403, "path /i\"x is not relayed");
        Assert.assertEquals("{\"error\":\"path /i\\\"x is not relayed\"}", new String(refusal.getBody(), UTF_8));
    }
}
