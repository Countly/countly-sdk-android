package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.TimeZone;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class UtilsTimeTests {

    @Before
    public void setUp() {
        Countly.sharedInstance().setLoggingEnabled(true);
    }

    @After
    public void tearDown() {
    }

    @Test(expected = AssertionError.class)
    public void invalidGet() {
        UtilsTime.Instant.get(-1L);
    }

    /**
     * The day of week and the hour are those of the device's time zone, Sunday being 0. The zone is pinned, so the
     * expectations hold on any machine.
     */
    @Test
    public void testInstant() {
        final long timestamp = 1_579_463_653_876L; // 2020-01-19 19:54:13.876 UTC, a Sunday
        final TimeZone deviceZone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            UtilsTime.Instant utc = UtilsTime.Instant.get(timestamp);
            Assert.assertEquals(timestamp, utc.timestampMs);
            Assert.assertEquals(0, utc.dow);
            Assert.assertEquals(19, utc.hour);

            //nine hours ahead it is already Monday morning
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            UtilsTime.Instant tokyo = UtilsTime.Instant.get(timestamp);
            Assert.assertEquals(timestamp, tokyo.timestampMs);
            Assert.assertEquals(1, tokyo.dow);
            Assert.assertEquals(4, tokyo.hour);
        } finally {
            TimeZone.setDefault(deviceZone);
        }
    }

    @Test
    public void testSeconds() {
        long tms = UtilsTime.currentTimestampMs();
        int tsec = UtilsTime.currentTimestampSeconds();

        long diff = tms / 1000 - tsec;
        Assert.assertTrue(diff < 1);
    }

    @Test
    public void testDiff() throws InterruptedException {
        long realStart = System.nanoTime();
        long tms = UtilsTime.currentTimestampMs();
        Thread.sleep(250);
        long tms2 = UtilsTime.currentTimestampMs();
        long realElapsedMs = (System.nanoTime() - realStart) / 1_000_000L;

        //held against the time that really passed: unique timestamps run up to nine draws ahead and sleeps overshoot
        Assert.assertTrue("diff:[" + (tms2 - tms) + "]", tms2 - tms >= 240);
        Assert.assertTrue("diff:[" + (tms2 - tms) + "], elapsed:[" + realElapsedMs + "]", tms2 - tms <= realElapsedMs + 10);
    }
}
