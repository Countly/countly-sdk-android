package ly.count.android.sdk;

import android.app.Activity;
import android.content.res.Configuration;
import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Lifecycle hooks reach the modules by iterating Countly's {@code modules} list, which is copy on write, so a teardown
 * on another thread can not break a dispatch in progress, and the dispatch gate plus the quiesce in teardown keep events
 * away from a dying instance. These tests pin that down: every module that overrides a hook receives it, a dispatch
 * survives a sibling module going away, and one registration feeds every live instance.
 */
@RunWith(AndroidJUnit4.class)
public class ModuleLifecycleDispatchTests {

    private Countly countly;

    @Before public void setUp() {
        TestUtils.getCountlyStore().clear();
        countly = new Countly().init(TestUtils.createBaseConfig());
    }

    @After public void cleanUp() {
        TestUtils.getCountlyStore().clear();
    }

    /** A module that only records the lifecycle hooks it receives. */
    private static class RecordingModule extends ModuleBase {
        final List<String> received = new ArrayList<>();

        /**
         * @param cly the instance the module belongs to
         * @param config the config the instance is initialised with
         */
        RecordingModule(Countly cly, CountlyConfig config) {
            super(cly, config);
        }

        @Override void halt() {
        }

        @Override void onActivityStarted(Activity activity, int updatedActivityCount) {
            received.add("started:" + updatedActivityCount);
        }

        @Override void callbackOnActivityResumed(Activity activity) {
            received.add("resumed");
        }

        @Override void onActivityStopped(int updatedActivityCount) {
            received.add("stopped:" + updatedActivityCount);
        }

        @Override void callbackOnActivityStopped(Activity activity) {
            received.add("callbackStopped");
        }

        @Override void onActivityDestroyed(@NonNull Activity activity) {
            received.add("destroyed");
        }

        @Override void onConfigurationChanged(Configuration newConfig) {
            received.add("configuration");
        }
    }

    /**
     * A module only has to override a hook to receive it: the dispatcher reaches every module in the list, a module
     * that exists only for tests included, with the activity count after the start or stop. Once the instance is
     * halted, nothing reaches it any more.
     */
    @Test
    public void everyModuleReceivesTheLifecycleHooksUntilHalt() {
        Countly recordingInstance = Countly.instance("dispatchRecording");
        CountlyConfig cc = TestUtils.createBaseConfig("deviceRecording");
        RecordingModule recorder = new RecordingModule(recordingInstance, cc);
        cc.testModuleListener = recorder;
        recordingInstance.init(cc);
        Assert.assertTrue(recordingInstance.modules.contains(recorder));
        recorder.received.clear();

        CountlyLifecycleDispatcher dispatcher = CountlyLifecycleDispatcher.getInstance();
        Activity activity = org.mockito.Mockito.mock(Activity.class);
        int countBefore = recordingInstance.getActivityCount();

        dispatcher.onActivityStarted(activity);
        dispatcher.onActivityResumed(activity);
        dispatcher.onActivityStopped(activity);
        dispatcher.onActivityDestroyed(activity);
        dispatcher.onConfigurationChanged(TestUtils.getContext().getResources().getConfiguration());

        Assert.assertEquals(Arrays.asList("started:" + (countBefore + 1), "resumed", "stopped:" + countBefore, "callbackStopped", "destroyed", "configuration"), recorder.received);
        Assert.assertEquals(countBefore, recordingInstance.getActivityCount());

        recordingInstance.halt();
        recorder.received.clear();
        dispatcher.onActivityStarted(activity);
        dispatcher.onActivityStopped(activity);
        Assert.assertTrue("a halted instance must receive nothing, got " + recorder.received, recorder.received.isEmpty());

        Countly.removeInstance("dispatchRecording");
    }

    /**
     * getCurrentRQ sizes its array to the whole queue and leaves a null hole for every request the filter
     * rejected, so matches have to be counted rather than read off .length.
     */
    private static int countRequestsWithKey(Countly countly, String key) {
        int found = 0;
        for (Map<String, String> request : TestUtils.getCurrentRQ(key, TestUtils.getCountlyStore(countly))) {
            if (request != null && request.containsKey(key)) {
                found++;
            }
        }
        return found;
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

        // Take this instance out of the process-wide dispatcher before touching anything. Other test classes
        // start and stop real activities, and an ambient event reaching this instance begins and ends extra
        // sessions - which made this test pass alone and fail inside the suite on an absolute count.
        CountlyLifecycleDispatcher.getInstance().removeInstanceAndQuiesce(foreground, new ModuleLog());

        // Initialising in the foreground already began a session and opened one activity, so onStopInternal
        // below takes the count to 0 - which is the only path that reaches endSessionInternal. Calling
        // onStartInternal first would make it 2 -> 1 and skip the branch entirely.
        Assert.assertTrue("a session must be running for this to exercise endSessionInternal",
            foreground.moduleSessions.sessionIsRunning());
        int endSessionsBefore = countRequestsWithKey(foreground, "end_session");

        // the interleaving: teardown has nulled the module fields while this dispatch is in flight
        foreground.moduleViews = null;

        // Called directly rather than through halt(), on purpose: teardown wraps its flush in a try/catch,
        // which would swallow the very NPE this test exists to catch.
        foreground.onStopInternal();

        // A missing sibling must skip only its own step. resetFirstView is what needs moduleViews, so it is
        // the only thing lost here - end_session still has to go out, because a session that begins and never
        // ends is a worse outcome than one that ends without its trailing bookkeeping.
        Assert.assertEquals("end_session must still be sent when only the views module is gone",
            endSessionsBefore + 1, countRequestsWithKey(foreground, "end_session"));

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

    /**
     * An instance joining the dispatcher is seeded with the started activities in the same step, before any event can
     * reach it. A stop delivered right after joining then finds the count it applies to, instead of a zero it would be
     * dropped at, which left the count one too high and the session open for good.
     */
    @Test
    public void addInstance_seedsTheActivityCountWhileJoining() {
        CountlyLifecycleDispatcher dispatcher = CountlyLifecycleDispatcher.getInstance();
        Assume.assumeTrue("the count is exact only when the provider registered the dispatcher", dispatcher.hasExactActivityCount());
        dispatcher.resetForTests();
        Activity activity = org.mockito.Mockito.mock(Activity.class);
        try {
            dispatcher.onActivityStarted(activity);
            dispatcher.onActivityStarted(activity);

            Countly seeded = new Countly();
            Assert.assertEquals(2, dispatcher.addInstance(seeded, true));
            Assert.assertEquals(2, seeded.getActivityCount());
            Assert.assertEquals(2, dispatcher.addInstance(seeded, true));
            Assert.assertEquals("joining again does not seed again", 2, seeded.getActivityCount());

            Countly unseeded = new Countly();
            dispatcher.addInstance(unseeded, false);
            Assert.assertEquals(0, unseeded.getActivityCount());
        } finally {
            dispatcher.resetForTests();
        }
    }
}
