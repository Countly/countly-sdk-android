package ly.count.android.sdk;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The single process-wide bridge between Android's lifecycle callbacks and the SDK's instances.
 * <p>
 * Android delivers {@link Application.ActivityLifecycleCallbacks} on the main thread, while
 * {@code halt()} / {@code removeInstance()} can be called from any thread the app chooses. Before this
 * class each Countly instance registered its own callbacks and dispatched by iterating its mutable
 * {@code modules} list, which gave two race outcomes against a concurrent teardown: a
 * {@code ConcurrentModificationException} while the list was being cleared, or a
 * {@code NullPointerException} once the module fields had been nulled. The second one is not theoretical -
 * it killed a CI run at test 108 of 1023 with
 * {@code ModuleViews.resetFirstView() on a null object reference} thrown out of {@code Activity.onStop},
 * which Android turns into a host-app crash.
 * <p>
 * Two properties fix that, and neither needs a lock on the dispatch path:
 * <ol>
 *     <li>ONE registration for the whole process, holding instances in a {@link CopyOnWriteArrayList}.
 *     Iteration is over an immutable snapshot, so a teardown removing an instance mid-dispatch can never
 *     provoke a {@code ConcurrentModificationException}, and N instances no longer mean N registrations
 *     against the {@code Application}.</li>
 *     <li>Teardown deregisters as its FIRST act, before it nulls anything. An event already in flight is
 *     stopped by the instance's own {@code tearingDown} gate; every later event never reaches it.</li>
 * </ol>
 * Registration happens from {@link CountlyInitProvider} before {@code Application.onCreate}, and again
 * (idempotently) from {@code Countly.init} so an app that removed the provider from its manifest still
 * gets lifecycle events.
 */
class CountlyLifecycleDispatcher implements Application.ActivityLifecycleCallbacks, ComponentCallbacks {

    private static final CountlyLifecycleDispatcher instance = new CountlyLifecycleDispatcher();

    // Copy-on-write: the main thread iterates this while any thread may be tearing an instance down.
    private final CopyOnWriteArrayList<Countly> instances = new CopyOnWriteArrayList<>();

    // Guards the one-time registration. Volatile because init can run on any thread while the provider
    // has already registered on the main thread.
    private volatile boolean registered = false;

    // Makes {count mutation + instance-list capture} atomic per started/stopped event, and
    // {addInstance + count snapshot} atomic against it. Held for nanoseconds (an int and a list
    // reference) - dispatch into instances always happens OUTSIDE this lock, so the main thread never
    // meaningfully blocks here.
    private final Object stateLock = new Object();

    private int startedActivityCount = 0;

    // True only when register() ran from CountlyInitProvider, i.e. before any activity could have
    // started. Only then is startedActivityCount the exact process-wide truth; a late registration
    // (provider stripped from the manifest, first registration at init time) has missed events and
    // callers must fall back to the ProcessLifecycleOwner heuristic.
    private volatile boolean countExactSinceProcessStart = false;

    private CountlyLifecycleDispatcher() {
    }

    static CountlyLifecycleDispatcher getInstance() {
        return instance;
    }

    /**
     * Registers this dispatcher against the Application exactly once per process. Safe to call repeatedly
     * and from any thread: the second and later calls do nothing.
     */
    void register(@Nullable Context context) {
        register(context, false);
    }

    /**
     * @param fromProvider true when called by {@link CountlyInitProvider} (before any activity can
     * exist), which is what makes the started-activity count exact from process start
     */
    void register(@Nullable Context context, boolean fromProvider) {
        if (registered || context == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        if (!(appContext instanceof Application)) {
            return;
        }
        synchronized (instance) {
            if (registered) {
                return;
            }
            Application application = (Application) appContext;
            application.registerActivityLifecycleCallbacks(this);
            application.registerComponentCallbacks(this);
            countExactSinceProcessStart = fromProvider;
            registered = true;
        }
    }

    boolean isRegistered() {
        return registered;
    }

    /**
     * Adds the instance and returns the started-activity count it should seed itself with, taken
     * atomically with joining: the instance will receive exactly the events after this snapshot,
     * never one that is also included in it.
     *
     * @return the exact number of currently started activities, or -1 when the dispatcher was not
     * registered before the first activity and the count is therefore not trustworthy
     */
    int addInstance(@NonNull Countly countly) {
        synchronized (stateLock) {
            if (!instances.contains(countly)) {
                instances.add(countly);
            }
            return countExactSinceProcessStart ? startedActivityCount : -1;
        }
    }

    /** True when {@link #register} ran before any activity could have started (provider path). */
    boolean hasExactActivityCount() {
        return countExactSinceProcessStart;
    }

    /** The number of currently started activities; only meaningful when {@link #hasExactActivityCount()}. */
    int getStartedActivityCount() {
        synchronized (stateLock) {
            return startedActivityCount;
        }
    }

    // Test support only: instrumented tests share one process and one dispatcher, so a test that
    // simulates lifecycle events must start from a known state without depending on every other test
    // having halted its instances.
    void resetForTests() {
        synchronized (stateLock) {
            instances.clear();
            startedActivityCount = 0;
        }
    }

    /**
     * Stops delivering lifecycle events to this instance. Teardown calls this before it nulls anything, so
     * that the window in which an event can reach a half-destroyed instance is closed rather than guarded.
     */
    void removeInstance(@NonNull Countly countly) {
        instances.remove(countly);
    }

    @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        CountlyActivityHolder.getInstance().setActivity(activity);
        for (Countly countly : instances) {
            countly.dispatchActivityCreated(activity);
        }
    }

    @Override public void onActivityStarted(@NonNull Activity activity) {
        CountlyActivityHolder.getInstance().setActivity(activity);
        //count and list snapshot move together: an instance added concurrently either is in the
        //snapshot and gets this event (its addInstance() count predates the increment) or is not and
        //has the event in its seeded count - never both, never neither
        Object[] toNotify;
        synchronized (stateLock) {
            startedActivityCount++;
            toNotify = instances.toArray();
        }
        for (Object countly : toNotify) {
            ((Countly) countly).dispatchActivityStarted(activity);
        }
    }

    @Override public void onActivityResumed(@NonNull Activity activity) {
        CountlyActivityHolder.getInstance().setActivity(activity);
        for (Countly countly : instances) {
            countly.dispatchActivityResumed(activity);
        }
    }

    @Override public void onActivityPaused(@NonNull Activity activity) {
        for (Countly countly : instances) {
            countly.dispatchActivityPaused(activity);
        }
    }

    @Override public void onActivityStopped(@NonNull Activity activity) {
        //see onActivityStarted for the count/snapshot pairing
        Object[] toNotify;
        synchronized (stateLock) {
            if (startedActivityCount > 0) {
                startedActivityCount--;
            }
            toNotify = instances.toArray();
        }
        for (Object countly : toNotify) {
            ((Countly) countly).dispatchActivityStopped(activity);
        }
    }

    @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        for (Countly countly : instances) {
            countly.dispatchActivitySaveInstanceState(activity);
        }
    }

    @Override public void onActivityDestroyed(@NonNull Activity activity) {
        CountlyActivityHolder.getInstance().clearActivity(activity);
        for (Countly countly : instances) {
            countly.dispatchActivityDestroyed(activity);
        }
    }

    @Override public void onConfigurationChanged(@NonNull Configuration newConfig) {
        for (Countly countly : instances) {
            countly.dispatchConfigurationChanged(newConfig);
        }
    }

    @Override public void onLowMemory() {
        for (Countly countly : instances) {
            countly.dispatchLowMemory();
        }
    }
}
