package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;
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

    @Test
    public void testInstant() {
        UtilsTime.Instant i1 = UtilsTime.Instant.get(1_579_463_653_876L);
        Assert.assertEquals(0, i1.dow); // TODO: "expected:<0> but was:<1>"
        Assert.assertEquals(1_579_463_653_876L, i1.timestampMs);

        //weird stuff to account for timezones and daylight saving
        int diff = Math.abs((int) (i1.hour - (18 + TimeUnit.HOURS.convert(Calendar.getInstance().getTimeZone().getRawOffset(), TimeUnit.MILLISECONDS))));
        Assert.assertTrue(diff <= 1);
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
        long tms = UtilsTime.currentTimestampMs();
        Thread.sleep(250);

        long tms2 = UtilsTime.currentTimestampMs();

        Assert.assertTrue(tms2 - tms < 260);
    }
}
