package ly.count.android.sdk;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import android.content.Context;
import android.os.Looper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test measuring Remote Config access latency under artificial contention on the global Countly lock.
 * After narrowing synchronization in RemoteConfig getters, these calls should NOT block for the full
 * duration that another thread holds Countly's global monitor.
 */
@RunWith(AndroidJUnit4.class)
public class RemoteConfigAnrTest {

    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        CountlyConfig cc = new CountlyConfig(context, "app", "https://server.url")
                .setLoggingEnabled(true);
        Countly.sharedInstance().init(cc);
    }

    @After
    public void tearDown() {
        Countly.sharedInstance().halt();
    }

    @Test
    public void testRemoteConfigAccessUnderContention() throws InterruptedException {
        // Baseline latencies
        long baselineGetValue = measureLatency(() -> Countly.sharedInstance().remoteConfig().getValue("test_key"));
        long baselineGetValues = measureLatency(() -> Countly.sharedInstance().remoteConfig().getValues());
        long baselineGetValueAndEnroll = measureLatency(() -> Countly.sharedInstance().remoteConfig().getValueAndEnroll("test_key_2"));
        long baselineGetAllValuesAndEnroll = measureLatency(() -> Countly.sharedInstance().remoteConfig().getAllValuesAndEnroll());

        // Create contention by holding global Countly lock for 4 seconds
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        Thread locker = new Thread(() -> {
            synchronized (Countly.sharedInstance()) {
                lockAcquired.countDown();
                try { Thread.sleep(4000); } catch (InterruptedException ignored) {}
                releaseLock.countDown();
            }
        }, "CountlyGlobalLockHolder");
        locker.start();
        assertTrue("Background thread failed to acquire Countly lock", lockAcquired.await(1, TimeUnit.SECONDS));

        long contestedGetValue = measureLatency(() -> Countly.sharedInstance().remoteConfig().getValue("test_key"));
        long contestedGetValues = measureLatency(() -> Countly.sharedInstance().remoteConfig().getValues());
        long contestedGetValueAndEnroll = measureLatency(() -> Countly.sharedInstance().remoteConfig().getValueAndEnroll("test_key_2"));
        long contestedGetAllValuesAndEnroll = measureLatency(() -> Countly.sharedInstance().remoteConfig().getAllValuesAndEnroll());

        releaseLock.await(5, TimeUnit.SECONDS);

        System.out.println("[RCContention] baseline getValue=" + baselineGetValue + "ms, contested=" + contestedGetValue + "ms");
        System.out.println("[RCContention] baseline getValues=" + baselineGetValues + "ms, contested=" + contestedGetValues + "ms");
        System.out.println("[RCContention] baseline getValueAndEnroll=" + baselineGetValueAndEnroll + "ms, contested=" + contestedGetValueAndEnroll + "ms");
        System.out.println("[RCContention] baseline getAllValuesAndEnroll=" + baselineGetAllValuesAndEnroll + "ms, contested=" + contestedGetAllValuesAndEnroll + "ms");

        // Reasonable baseline expectations (< 500ms each)
        assertTrue("Baseline getValue high", baselineGetValue < 500);
        assertTrue("Baseline getValues high", baselineGetValues < 500);
        assertTrue("Baseline getValueAndEnroll high", baselineGetValueAndEnroll < 500);
        assertTrue("Baseline getAllValuesAndEnroll high", baselineGetAllValuesAndEnroll < 500);

        // Contested calls should not block near 4s. Ensure < 1500ms and not >=3000ms.
        assertTrue("Contested getValue too high: " + contestedGetValue, contestedGetValue < 1500);
        assertTrue("Contested getValues too high: " + contestedGetValues, contestedGetValues < 1500);
        assertTrue("Contested getValueAndEnroll too high: " + contestedGetValueAndEnroll, contestedGetValueAndEnroll < 1500);
        assertTrue("Contested getAllValuesAndEnroll too high: " + contestedGetAllValuesAndEnroll, contestedGetAllValuesAndEnroll < 1500);

        assertFalse("getValue appears blocked, ANR adjacent", contestedGetValue >= 3000);
        assertFalse("getValues appears blocked, ANR adjacent", contestedGetValues >= 3000);
        assertFalse("getValueAndEnroll appears blocked, ANR adjacent", contestedGetValueAndEnroll >= 3000);
        assertFalse("getAllValuesAndEnroll appears blocked, ANR adjacent", contestedGetAllValuesAndEnroll >= 3000);

        // Relative inflation guard: contested no more than 10x baseline (allows some noise)
        assertTrue("getValue inflation too large", contestedGetValue <= Math.max(50, baselineGetValue * 10));
        assertTrue("getValues inflation too large", contestedGetValues <= Math.max(50, baselineGetValues * 10));
        assertTrue("getValueAndEnroll inflation too large", contestedGetValueAndEnroll <= Math.max(50, baselineGetValueAndEnroll * 10));
        assertTrue("getAllValuesAndEnroll inflation too large", contestedGetAllValuesAndEnroll <= Math.max(50, baselineGetAllValuesAndEnroll * 10));
    }

    private long measureLatency(Runnable r) {
        AtomicLong start = new AtomicLong();
        AtomicLong end = new AtomicLong();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            start.set(System.currentTimeMillis());
            r.run();
            end.set(System.currentTimeMillis());
        });
        return end.get() - start.get();
    }

    @Test
    public void stressTestRemoteConfigAccessUnderContention() throws InterruptedException {
        final int ITERS = 100;

        // Warm-up baseline (single call per method) to initialize any lazy structures
        measureLatency(() -> Countly.sharedInstance().remoteConfig().getValue("stress_key"));
        measureLatency(() -> Countly.sharedInstance().remoteConfig().getValues());

        long maxGetValue = 0, maxGetValues = 0;
        long sumGetValue = 0, sumGetValues = 0;

        // Start contention thread holding global lock intermittently
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(1);
        Thread contender = new Thread(() -> {
            startSignal.countDown();
            long endTime = System.currentTimeMillis() + 4000; // run for ~4s
            while (System.currentTimeMillis() < endTime) {
                synchronized (Countly.sharedInstance()) {
                    try { Thread.sleep(80); } catch (InterruptedException ignored) {}
                }
                try { Thread.sleep(20); } catch (InterruptedException ignored) {}
            }
            doneSignal.countDown();
        }, "CountlyIntermittentLocker");
        contender.start();
        assertTrue("Failed to start contention thread", startSignal.await(1, TimeUnit.SECONDS));

        for (int i = 0; i < ITERS; i++) {
            long gv = measureLatency(() -> Countly.sharedInstance().remoteConfig().getValue("stress_key"));
            long gvs = measureLatency(() -> Countly.sharedInstance().remoteConfig().getValues());

            sumGetValue += gv;
            sumGetValues += gvs;
            if (gv > maxGetValue) maxGetValue = gv;
            if (gvs > maxGetValues) maxGetValues = gvs;
        }

        doneSignal.await(5, TimeUnit.SECONDS);

        double avgGetValue = sumGetValue / (double) ITERS;
        double avgGetValues = sumGetValues / (double) ITERS;

        System.out.println("[RCStress] iterations=" + ITERS +
            " getValue avg=" + avgGetValue + "ms max=" + maxGetValue +
            " | getValues avg=" + avgGetValues + "ms max=" + maxGetValues);

        // average should be under 200ms, max should not exceed 1000ms under intermittent contention.
        assertTrue("getValue average too high: " + avgGetValue, avgGetValue < 200);
        assertTrue("getValues average too high: " + avgGetValues, avgGetValues < 200);
        assertTrue("getValue max spike too large: " + maxGetValue, maxGetValue < 1000);
        assertTrue("getValues max spike too large: " + maxGetValues, maxGetValues < 1000);
    }
}
