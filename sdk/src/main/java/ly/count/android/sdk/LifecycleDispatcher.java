package ly.count.android.sdk;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The process-wide middleman between the OS lifecycle callbacks and Countly instances.
 * <p>
 * Exactly one {@link Application.ActivityLifecycleCallbacks} (and one {@link ComponentCallbacks})
 * is registered for the whole process, no matter how many Countly instances exist. Instances
 * subscribe here instead of registering their own callbacks, which buys three things:
 * <ul>
 * <li><b>Teardown without contention:</b> dispatch iterates a copy-on-write snapshot on the main
 * thread and holds no lock while calling subscribers, and {@link #unsubscribeAndQuiesce} guarantees
 * that once it returns, no lifecycle event is in flight toward the unsubscribed instance - so a
 * teardown never races the main thread over the instance's internals.</li>
 * <li><b>Exact foreground truth:</b> installed by {@link CountlyInitProvider} before
 * {@code Application.onCreate()}, this dispatcher counts every activity start/stop from process
 * start. That count replaces the ProcessLifecycleOwner heuristic, whose ~700ms stop-debounce could
 * report "foreground" right after the app left it and seed a session that never ends.</li>
 * <li><b>One registration:</b> the previous design registered one callback set per initialised
 * instance plus the provider's own - this consolidates them.</li>
 * </ul>
 * The dispatcher deliberately does not know about modules: the subscriber unit is the Countly
 * instance, which owns its modules' lifetime and fans events out only to the ones still alive.
 */
final class LifecycleDispatcher {

    /** What a Countly instance implements to receive process lifecycle events, in Android's callback order. */
    interface Subscriber {
        void onActivityCreated(@NonNull Activity activity);

        void onActivityStarted(@Nullable Activity activity);

        void onActivityResumed(@Nullable Activity activity);

        void onActivityPaused(@NonNull Activity activity);

        void onActivityStopped(@Nullable Activity activity);

        void onActivitySaveInstanceState(@NonNull Activity activity);

        void onActivityDestroyed(@NonNull Activity activity);

        void onConfigurationChanged(@NonNull Configuration configuration);
    }

    private static final LifecycleDispatcher instance = new LifecycleDispatcher();

    static LifecycleDispatcher getInstance() {
        return instance;
    }

    private LifecycleDispatcher() {
    }

    private final CopyOnWriteArrayList<Subscriber> subscribers_ = new CopyOnWriteArrayList<>();

    // Guards the started-count and makes {count mutation + subscriber-list capture} atomic per
    // event, and {subscribe + count snapshot} atomic against it. Held for nanoseconds (an int and a
    // list reference) - dispatch into subscribers always happens OUTSIDE this lock, so the main
    // thread never blocks here in any meaningful way.
    private final Object stateLock_ = new Object();

    private int startedActivityCount_ = 0;

    private boolean installed_ = false;

    // True only when install() ran from CountlyInitProvider, i.e. before any activity could have
    // started. Only then is startedActivityCount_ the exact process-wide truth; a late install
    // (provider stripped from the manifest, first install at init time) has missed events and the
    // callers must fall back to the ProcessLifecycleOwner heuristic.
    private boolean countExactSinceProcessStart_ = false;

    /**
     * Registers the single process-wide callback set. Idempotent; safe from any thread.
     *
     * @param fromProvider true when called by {@link CountlyInitProvider} (before any activity
     * exists), which is what makes the activity count exact
     */
    void install(@NonNull Application application, boolean fromProvider) {
        synchronized (stateLock_) {
            if (installed_) {
                return;
            }
            installed_ = true;
            countExactSinceProcessStart_ = fromProvider;
        }

        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                CountlyActivityHolder.getInstance().setActivity(activity);
                handleActivityCreated(activity);
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                CountlyActivityHolder.getInstance().setActivity(activity);
                handleActivityStarted(activity);
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                CountlyActivityHolder.getInstance().setActivity(activity);
                handleActivityResumed(activity);
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
                handleActivityPaused(activity);
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
                handleActivityStopped(activity);
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
                handleActivitySaveInstanceState(activity);
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
                CountlyActivityHolder.getInstance().clearActivity(activity);
                handleActivityDestroyed(activity);
            }
        });

        application.registerComponentCallbacks(new ComponentCallbacks() {
            @Override
            public void onConfigurationChanged(@NonNull Configuration newConfig) {
                handleConfigurationChanged(newConfig);
            }

            @Override
            public void onLowMemory() {
            }
        });
    }

    /**
     * Adds a subscriber and returns the started-activity count it should seed itself with, taken
     * atomically with the subscription: the subscriber will receive exactly the events after this
     * snapshot, never one that is also included in it.
     *
     * @return the exact number of currently started activities, or -1 when the dispatcher was not
     * installed before the first activity and the count is therefore not trustworthy
     */
    int subscribe(@NonNull Subscriber subscriber) {
        synchronized (stateLock_) {
            subscribers_.addIfAbsent(subscriber);
            return countExactSinceProcessStart_ ? startedActivityCount_ : -1;
        }
    }

    /**
     * Removes the subscriber and guarantees that when this returns, no lifecycle event is being
     * dispatched into it, so its owner may tear state down without racing the main thread.
     * <p>
     * Dispatch runs on the main thread, so the guarantee is "the main thread has moved past any
     * dispatch that could still see the subscriber". On the main thread that is immediate (we ARE
     * the dispatch thread). From any other thread it is one posted no-op to the main looper - the
     * caller must NOT hold a lock the main thread might want (the Countly teardown path calls this
     * before taking the instance monitor for exactly that reason). If the main looper cannot run
     * the hop in a bounded time (wedged main thread), this gives up and returns: the subscriber is
     * already removed, and the instance monitor still serialises any straggler.
     */
    void unsubscribeAndQuiesce(@NonNull Subscriber subscriber, @NonNull ModuleLog L) {
        subscribers_.remove(subscriber);

        Looper mainLooper = Looper.getMainLooper();
        if (Looper.myLooper() == mainLooper) {
            //we are the dispatch thread: no dispatch can be in flight concurrently with us
            return;
        }

        final CountDownLatch drained = new CountDownLatch(1);
        new Handler(mainLooper).post(new Runnable() {
            @Override
            public void run() {
                //by the time this runs, any dispatch that captured the subscriber before its
                //removal has finished - dispatch and this hop run on the same (main) thread
                drained.countDown();
            }
        });
        try {
            if (!drained.await(1, TimeUnit.SECONDS)) {
                L.w("[LifecycleDispatcher] unsubscribeAndQuiesce, the main thread did not drain within 1s, proceeding with teardown");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            L.w("[LifecycleDispatcher] unsubscribeAndQuiesce, interrupted while waiting for the main thread to drain");
        }
    }

    /** True when {@link #install} ran before any activity could have started (provider path). */
    boolean hasExactActivityCount() {
        synchronized (stateLock_) {
            return countExactSinceProcessStart_;
        }
    }

    /** The number of currently started activities; only meaningful when {@link #hasExactActivityCount()}. */
    int getStartedActivityCount() {
        synchronized (stateLock_) {
            return startedActivityCount_;
        }
    }

    //The handle* methods are the dispatch core, split out from the OS callbacks so instrumented
    //tests can drive lifecycle events directly. Pattern: mutate the count and capture the
    //subscriber snapshot under stateLock_ (nanoseconds), then dispatch OUTSIDE the lock - a
    //subscriber that appears concurrently either is in the snapshot and gets the event (its own
    //subscribe() snapshot predates the count change) or is not and has the event in its snapshot,
    //never both.

    void handleActivityCreated(@NonNull Activity activity) {
        for (Subscriber s : subscribers_) {
            s.onActivityCreated(activity);
        }
    }

    void handleActivityStarted(@Nullable Activity activity) {
        Object[] toNotify;
        synchronized (stateLock_) {
            startedActivityCount_++;
            toNotify = subscribers_.toArray();
        }
        for (Object s : toNotify) {
            ((Subscriber) s).onActivityStarted(activity);
        }
    }

    void handleActivityPaused(@NonNull Activity activity) {
        for (Subscriber s : subscribers_) {
            s.onActivityPaused(activity);
        }
    }

    void handleActivitySaveInstanceState(@NonNull Activity activity) {
        for (Subscriber s : subscribers_) {
            s.onActivitySaveInstanceState(activity);
        }
    }

    void handleActivityResumed(@Nullable Activity activity) {
        for (Subscriber s : subscribers_) {
            s.onActivityResumed(activity);
        }
    }

    void handleActivityStopped(@Nullable Activity activity) {
        Object[] toNotify;
        synchronized (stateLock_) {
            if (startedActivityCount_ > 0) {
                startedActivityCount_--;
            }
            toNotify = subscribers_.toArray();
        }
        for (Object s : toNotify) {
            ((Subscriber) s).onActivityStopped(activity);
        }
    }

    void handleActivityDestroyed(@NonNull Activity activity) {
        for (Subscriber s : subscribers_) {
            s.onActivityDestroyed(activity);
        }
    }

    void handleConfigurationChanged(@NonNull Configuration configuration) {
        for (Subscriber s : subscribers_) {
            s.onConfigurationChanged(configuration);
        }
    }

    // Test support only: instrumented tests share one process and one dispatcher, so a test that
    // simulates lifecycle events must be able to start from a known state without depending on
    // every other test having halted its instances.
    void resetForTests() {
        synchronized (stateLock_) {
            subscribers_.clear();
            startedActivityCount_ = 0;
        }
    }
}
