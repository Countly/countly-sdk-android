package ly.count.android.sdk;

import org.junit.runner.Description;
import org.junit.runner.notification.RunListener;

/**
 * Keeps instrumented tests isolated from each other.
 *
 * <p>Most tests create a detached instance via {@code new Countly().init(...)} but their setUp/tearDown
 * only halt {@code Countly.sharedInstance()} (a different, unused instance). The detached instance
 * therefore keeps its session-update timer scheduled after the test ends, and every tick runs
 * {@code onTimer()} against the process-shared {@link CountlyStore} — adding or removing requests
 * while an unrelated later test is asserting queue contents. That is the source of the flaky
 * "expected:&lt;N&gt; but was:&lt;N±1&gt;" / "was:&lt;null&gt;" failures seen across the session, view,
 * user-profile and log tests.
 *
 * <p>This listener enables lightweight instance tracking for the whole run and, after every test,
 * halts any instance the test left behind (which stops its timer). It is wired in by
 * {@code InstrumentationTestRunner} so no per-test-class change is required.
 */
public class CountlyInstanceLeakCleanup extends RunListener {

    @Override
    public void testRunStarted(Description description) {
        Countly.instanceTrackingForTests = true;
    }

    @Override
    public void testFinished(Description description) {
        Countly.haltTrackedInstances();
    }
}
