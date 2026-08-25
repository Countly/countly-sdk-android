package ly.count.android.sdk;

import android.app.Activity;
import android.content.res.Configuration;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Makes the "wire a new override into the dispatcher" rule enforceable instead of aspirational.
 * <p>
 * Lifecycle hooks used to be delivered by iterating Countly's mutable {@code modules} list. Android
 * delivers them on the main thread while a teardown on another thread clears that list and nulls the
 * module fields, which killed a CI run with {@code ModuleViews.resetFirstView()} thrown out of
 * {@code Activity.onStop}. Dispatch therefore calls a fixed set of modules directly. That trades the race
 * for two silent failure modes this test closes:
 * <ol>
 *     <li>A module overrides a hook and is never added to the call site, so the hook does nothing.</li>
 *     <li>The call site's order drifts from the order init adds modules to {@code modules}, silently
 *     reordering side effects between modules that used to run in list order.</li>
 * </ol>
 * The expected order is derived from the live {@code modules} list of a real initialised instance, so it
 * tracks init order automatically rather than restating it.
 */
@RunWith(AndroidJUnit4.class)
public class ModuleLifecycleDispatchTests {

    // What Countly's dispatch path calls, hook by hook, IN CALL ORDER. Keep in sync with the call sites.
    private static final String[] WIRED_ON_ACTIVITY_STARTED = { "ModuleViews", "ModuleAPM", "ModuleFeedback", "ModuleContent" };
    private static final String[] WIRED_ON_ACTIVITY_STOPPED = { "ModuleViews", "ModuleFeedback", "ModuleContent", "ModuleHealthCheck" };
    private static final String[] WIRED_ON_ACTIVITY_DESTROYED = { "ModuleFeedback", "ModuleContent" };
    private static final String[] WIRED_ON_CONFIGURATION_CHANGED = { "ModuleViews" };
    private static final String[] WIRED_CALLBACK_ON_RESUMED = { "ModuleRatings", "ModuleAPM" };
    private static final String[] WIRED_CALLBACK_ON_STOPPED = { "ModuleAPM" };

    private Countly countly;

    @Before public void setUp() {
        TestUtils.getCountlyStore().clear();
        countly = new Countly().init(TestUtils.createBaseConfig());
    }

    @After public void cleanUp() {
        TestUtils.getCountlyStore().clear();
    }

    /** The order init adds modules to {@code modules}, filtered to the ones overriding this hook. */
    private List<String> expectedOrderFor(String methodName, Class<?>... paramTypes) {
        List<String> expected = new ArrayList<>();
        for (ModuleBase module : countly.modules) {
            if (overridesHook(module.getClass(), methodName, paramTypes)) {
                expected.add(module.getClass().getSimpleName());
            }
        }
        return expected;
    }

    /**
     * True when this module declares the hook itself, which for a direct ModuleBase subclass is exactly what
     * "overrides it" means. Scanning getDeclaredMethods rather than calling getDeclaredMethod keeps the
     * not-overridden case a plain false instead of an exception that has to be caught and ignored.
     */
    private static boolean overridesHook(Class<?> moduleClass, String methodName, Class<?>[] paramTypes) {
        for (Method declared : moduleClass.getDeclaredMethods()) {
            if (declared.getName().equals(methodName) && Arrays.equals(declared.getParameterTypes(), paramTypes)) {
                return true;
            }
        }
        return false;
    }

    private void assertWiring(String hook, String[] wired, String methodName, Class<?>... paramTypes) {
        List<String> expected = expectedOrderFor(methodName, paramTypes);
        List<String> actual = Arrays.asList(wired);
        Assert.assertEquals("The dispatcher's calls for " + hook + " must be exactly the modules that override"
            + " it, in the order init adds them to `modules`. A module missing here is a hook that silently"
            + " does nothing; a module listed that no longer overrides it is dead code; a different order"
            + " silently reorders side effects. Fix the call site (see the note on ModuleBase#" + hook + ")."
            + "\n  expected (live modules list): " + expected
            + "\n  wired at the call site:       " + actual, expected, actual);
    }

    @Test
    public void everyLifecycleOverrideIsWiredIntoTheDispatcherInModuleListOrder() {
        assertWiring("onActivityStarted", WIRED_ON_ACTIVITY_STARTED, "onActivityStarted", Activity.class, int.class);
        assertWiring("onActivityStopped", WIRED_ON_ACTIVITY_STOPPED, "onActivityStopped", int.class);
        assertWiring("onActivityDestroyed", WIRED_ON_ACTIVITY_DESTROYED, "onActivityDestroyed", Activity.class);
        assertWiring("onConfigurationChanged", WIRED_ON_CONFIGURATION_CHANGED, "onConfigurationChanged", Configuration.class);
        assertWiring("callbackOnActivityResumed", WIRED_CALLBACK_ON_RESUMED, "callbackOnActivityResumed", Activity.class);
        assertWiring("callbackOnActivityStopped", WIRED_CALLBACK_ON_STOPPED, "callbackOnActivityStopped", Activity.class);
    }

    /**
     * The CI crash, reproduced deterministically. The run died at test 108 of 1023 with
     * {@code ModuleViews.resetFirstView() on a null object reference} thrown out of {@code Activity.onStop}:
     * the main thread was inside {@code ModuleSessions.endSessionInternal} when a teardown on another thread
     * nulled {@code moduleViews}. Nulling the field by hand here is exactly that interleaving, without
     * needing to win a race. Modules must snapshot siblings reached through {@code _cly} and bail, so this
     * has to complete silently.
     */
    @Test
    public void aLifecycleStopWithANulledSiblingModuleDoesNotThrow() {
        Countly.lifecycleStateOverrideForTests = true;
        Countly foreground = Countly.instance("dispatchNulledSibling");
        foreground.init(TestUtils.createBaseConfig("deviceNulledSibling"));

        // Initialising in the foreground already began a session and opened one activity, so onStopInternal
        // below takes the count to 0 - which is the only path that reaches endSessionInternal. Calling
        // onStartInternal first would make it 2 -> 1 and skip the branch entirely.
        Assert.assertTrue("a session must be running for this to exercise endSessionInternal",
            foreground.moduleSessions.sessionIsRunning());

        // the interleaving: teardown has nulled the module fields while this dispatch is in flight
        foreground.moduleViews = null;

        // Called directly rather than through halt(), on purpose: teardown wraps its flush in a try/catch,
        // which would swallow the very NPE this test exists to catch.
        foreground.onStopInternal();

        // A missing sibling must skip only its own step. resetFirstView is what needs moduleViews, so it is
        // the only thing lost here - end_session still has to go out, because a session that begins and never
        // ends is a worse outcome than one that ends without its trailing bookkeeping.
        // getCurrentRQ sizes its array to the whole queue and leaves a null hole for every request the
        // filter rejected, so the matches have to be counted rather than read off .length
        Map<String, String>[] rq = TestUtils.getCurrentRQ("end_session", TestUtils.getCountlyStore(foreground));
        int endSessions = 0;
        for (Map<String, String> request : rq) {
            if (request != null && request.containsKey("end_session")) {
                endSessions++;
            }
        }
        Assert.assertEquals("end_session must still be sent when only the views module is gone",
            1, endSessions);

        // the event path reads _cly.moduleViews for the view names, so it needs the same treatment
        foreground.moduleEvents.recordEventInternal("someKey", null, 1, 0.0d, 0.0d, null, null);

        Countly.lifecycleStateOverrideForTests = false;
        Countly.removeInstance("dispatchNulledSibling");
    }

    /**
     * The point of the class: ONE registration against the Application feeds EVERY live instance, and a
     * torn-down instance drops out of it. Before the dispatcher each instance registered its own callbacks,
     * so N instances meant N registrations, and a halted instance kept receiving events until the
     * unregistration at the end of teardown - after its modules had already been nulled.
     */
    @Test
    public void oneRegistrationFansOutToEveryInstanceAndStopsAtTeardown() {
        CountlyLifecycleDispatcher dispatcher = CountlyLifecycleDispatcher.getInstance();
        Assert.assertTrue("init must have registered the dispatcher", dispatcher.isRegistered());

        Countly second = Countly.instance("dispatchFanOut");
        second.init(TestUtils.createBaseConfig("deviceFanOut"));
        Assert.assertFalse("no start seen yet by the default instance", countly.hasBeenCalledOnStart());
        Assert.assertFalse("no start seen yet by the named instance", second.hasBeenCalledOnStart());

        Activity activity = org.mockito.Mockito.mock(Activity.class);
        dispatcher.onActivityStarted(activity);

        // hasBeenCalledOnStart is set at the end of onStartInternal, so it proves the event reached each
        // instance and ran the whole method - not just that the dispatcher held a reference
        Assert.assertTrue("the default instance must have seen onActivityStarted", countly.hasBeenCalledOnStart());
        Assert.assertTrue("the named instance must have seen the same event from the one registration", second.hasBeenCalledOnStart());

        second.halt();
        Assert.assertFalse("a halted instance must not still be initialised", second.isInitialized());

        // The events below used to crash the process: they reached an instance whose module fields were
        // already null, and the resulting NPE escaped Activity.onStop. They must now be silent no-ops.
        dispatcher.onActivityStarted(activity);
        dispatcher.onActivityResumed(activity);
        dispatcher.onActivityStopped(activity);
        dispatcher.onActivityDestroyed(activity);
        dispatcher.onConfigurationChanged(TestUtils.getContext().getResources().getConfiguration());

        Countly.removeInstance("dispatchFanOut");
    }
}
