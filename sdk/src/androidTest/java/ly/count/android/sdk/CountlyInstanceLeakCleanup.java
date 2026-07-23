package ly.count.android.sdk;

import org.junit.runner.Description;
import org.junit.runner.notification.RunListener;

/**
 * Keeps instrumented tests isolated from each other by resetting the process-wide state the SDK
 * leaks between tests. The whole suite runs in one process, so state a test leaves behind is visible
 * to later tests and causes order-dependent, flaky failures (the classic "expected:&lt;N&gt; but
 * was:&lt;N±1&gt;" / "was:&lt;null&gt;" pattern across the session, view, user-profile and store tests).
 *
 * <p>Three leaks are reset:
 * <ul>
 *   <li><b>Instances / timers.</b> Most tests create a detached {@code new Countly().init(...)} but
 *       only halt {@code Countly.sharedInstance()} (a different, unused instance), so the detached
 *       instance keeps its session-update timer scheduled and its {@code onTimer()} ticks keep
 *       mutating the shared {@link CountlyStore} during later tests. After each test every instance a
 *       test left initialized is halted (which stops its timer and empties its modules, so its still
 *       -registered Activity/lifecycle callbacks become no-ops).</li>
 *   <li><b>Foreground state.</b> {@code ProcessLifecycleOwner} keeps reporting "started" for a while
 *       after a prior test's Activity stops, so a later test that inits in the default lifecycle mode
 *       is non-deterministically seen as foreground and auto-begins a session. Each test starts from a
 *       deterministic background default (a test that needs foreground injects its own observer).</li>
 *   <li><b>Held Activity.</b> {@link CountlyActivityHolder} is a process-wide singleton holding the
 *       last Activity; it is cleared so a prior test's Activity does not seed a later init.</li>
 * </ul>
 *
 * Wired in by {@code InstrumentationTestRunner}, so no per-test-class change is required.
 */
public class CountlyInstanceLeakCleanup extends RunListener {

    @Override
    public void testRunStarted(Description description) {
        Countly.instanceTrackingForTests = true;
    }

    @Override
    public void testStarted(Description description) {
        resetLeakedProcessState();
    }

    @Override
    public void testFinished(Description description) {
        Countly.haltTrackedInstances();
        resetLeakedProcessState();
    }

    private static void resetLeakedProcessState() {
        // Default each test to background so a leaked "started" ProcessLifecycleOwner state cannot
        // make an init auto-begin a foreground session. Tests that need foreground inject their own
        // CountlyConfig.lifecycleObserver, which is consulted directly and ignores this override.
        Countly.lifecycleStateOverrideForTests = false;
        CountlyActivityHolder.getInstance().resetForTests();
    }
}
