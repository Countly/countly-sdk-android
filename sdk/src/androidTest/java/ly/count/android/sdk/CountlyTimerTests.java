package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.concurrent.ExecutorService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

@RunWith(AndroidJUnit4.class)
public class CountlyTimerTests {

    private CountlyTimer countlyTimer;
    private ModuleLog mockLog;

    @Before
    public void setUp() {
        countlyTimer = new CountlyTimer();
        mockLog = Mockito.mock(ModuleLog.class);
        CountlyTimer.TIMER_DELAY_MS = 0;
    }

    @After
    public void tearDown() {
        countlyTimer.stopTimer(mockLog);
        Assert.assertNull(countlyTimer.timerService);
    }

    @Test
    public void validateInitialValues() {
        Assert.assertNull(countlyTimer.timerService);
        Assert.assertEquals(0, CountlyTimer.TIMER_DELAY_MS);
    }

    @Test
    public void startTimer_validDelay() {
        Runnable mockRunnable = Mockito.mock(Runnable.class);

        countlyTimer.startTimer(1, mockRunnable, mockLog);
        Mockito.verify(mockLog).i("[CountlyTimer] startTimer, Starting timer timerDelay: [1000 ms], initialDelay: [0 ms]");
    }

    @Test
    public void startTimer_invalidDelay() {
        Runnable mockRunnable = Mockito.mock(Runnable.class);

        countlyTimer.startTimer(-1, mockRunnable, mockLog);
        Mockito.verify(mockLog).i("[CountlyTimer] startTimer, Starting timer timerDelay: [1000 ms], initialDelay: [0 ms]");
    }

    @Test
    public void startTimer() {
        Runnable mockRunnable = Mockito.mock(Runnable.class);

        countlyTimer.startTimer(99, mockRunnable, mockLog);
        Mockito.verify(mockLog).i("[CountlyTimer] startTimer, Starting timer timerDelay: [99000 ms], initialDelay: [0 ms]");
    }

    @Test
    public void startTimer_withTimerDelayMS() {
        CountlyTimer.TIMER_DELAY_MS = 500;
        Runnable mockRunnable = Mockito.mock(Runnable.class);

        countlyTimer.startTimer(1, mockRunnable, mockLog);
        Mockito.verify(mockLog).i("[CountlyTimer] startTimer, Starting timer timerDelay: [500 ms], initialDelay: [0 ms]");
    }

    /**
     * Test that the timer is stopped when a new timer is started
     * This is to prevent multiple timers from running at the same time
     * And it is not reusing the previous timer
     */
    @Test
    public void startTimer_reuseTimer() {
        countlyTimer.stopTimer(mockLog);

        Assert.assertNull(countlyTimer.timerService);

        Runnable mockRunnable = Mockito.mock(Runnable.class);
        countlyTimer.startTimer(1, mockRunnable, mockLog);

        Assert.assertNotNull(countlyTimer.timerService);
        ExecutorService timerService = countlyTimer.timerService;

        countlyTimer.startTimer(2, mockRunnable, mockLog);
        Assert.assertNotEquals(timerService, countlyTimer.timerService);
    }

    @Test
    public void stopTimer() {
        countlyTimer.startTimer(1, Mockito.mock(Runnable.class), mockLog);
        countlyTimer.stopTimer(mockLog);
        Mockito.verify(mockLog).i("[CountlyTimer] stopTimer, Stopping timer");
    }

    @Test
    public void stopTimer_nullTimer() {
        countlyTimer.stopTimer(mockLog);
        Mockito.verify(mockLog, Mockito.never()).i("[CountlyTimer] stopTimer, Stopping timer");
        Mockito.verify(mockLog).d("[CountlyTimer] stopTimer, Timer already stopped");
    }
}