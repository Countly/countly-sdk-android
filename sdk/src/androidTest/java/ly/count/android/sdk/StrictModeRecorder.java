package ly.count.android.sdk;

import android.os.Build;
import android.os.StrictMode;
import android.os.strictmode.Violation;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.Assert;

/**
 * Records one kind of StrictMode violation while a test runs. Starting a recorder first commits a
 * deliberate violation of that kind and checks it was reported, so an empty record afterwards means
 * the code under test caused none, not that the device does not detect them. Policies are built
 * with detectAll, the configuration integrators run, which on API 30 is also the only way to enable
 * incorrect context use detection. VM policy violations are recorded on the test thread when it
 * reads them, because StrictMode lifts the VM policy for the whole process while a listener runs,
 * which would leave sockets and contexts of other threads unchecked in the meantime.
 */
@RequiresApi(Build.VERSION_CODES.P)
final class StrictModeRecorder {
    private final String violationName;
    private final List<String> stacks = new CopyOnWriteArrayList<>();
    private final Queue<Runnable> pendingVmCallbacks = new ConcurrentLinkedQueue<>();
    private final StrictMode.VmPolicy previousVmPolicy = StrictMode.getVmPolicy();
    private final StrictMode.ThreadPolicy previousThreadPolicy = StrictMode.getThreadPolicy();

    private StrictModeRecorder(@NonNull String violationName) {
        this.violationName = violationName;
    }

    /**
     * Starts recording VM policy violations named violationName, reported from any thread.
     *
     * @param violationName simple class name of the violation, such as UntaggedSocketViolation
     * @param deliberateViolation code that commits one such violation
     * @return the started recorder, to be stopped when the test ends
     */
    static StrictModeRecorder startVm(@NonNull String violationName, @NonNull Runnable deliberateViolation) {
        StrictModeRecorder recorder = new StrictModeRecorder(violationName);
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
            .detectAll()
            .penaltyListener(recorder.pendingVmCallbacks::add, recorder::record)
            .build());
        recorder.checkDetected(deliberateViolation);
        return recorder;
    }

    /**
     * Starts recording thread policy violations named violationName on the calling thread. The
     * thread must have no Looper, like the instrumentation thread, as StrictMode delays reporting
     * thread violations on a Looper thread until its current message ends.
     *
     * @param violationName simple class name of the violation, such as DiskReadViolation
     * @param deliberateViolation code that commits one such violation on the calling thread
     * @return the started recorder, to be stopped when the test ends
     */
    static StrictModeRecorder startThread(@NonNull String violationName, @NonNull Runnable deliberateViolation) {
        StrictModeRecorder recorder = new StrictModeRecorder(violationName);
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
            .detectAll()
            .penaltyListener(Runnable::run, recorder::record)
            .build());
        recorder.checkDetected(deliberateViolation);
        return recorder;
    }

    /**
     * Returns the stack traces of the recorded violations that pass through the given class, or
     * through any class of the package when a package name ending with '.' is given.
     *
     * @param classOrPackage fully qualified class name, or a package prefix ending with '.'
     * @return the matching stack traces, empty when there are none
     */
    @NonNull List<String> violationsThrough(@NonNull String classOrPackage) {
        runPendingVmCallbacks();
        List<String> matching = new ArrayList<>();
        for (String stack : stacks) {
            boolean matches;
            if (classOrPackage.endsWith(".")) {
                matches = stack.contains("at " + classOrPackage);
            } else {
                matches = stack.contains("at " + classOrPackage + ".") || stack.contains("at " + classOrPackage + "$");
            }
            if (matches) {
                matching.add(stack);
            }
        }
        return matching;
    }

    /**
     * Restores the VM and thread policies that were in place before the recorder started.
     */
    void stop() {
        StrictMode.setVmPolicy(previousVmPolicy);
        StrictMode.setThreadPolicy(previousThreadPolicy);
    }

    /**
     * Keeps the violation's stack trace when it is of the recorded kind.
     */
    private void record(@NonNull Violation violation) {
        if (violationName.equals(violation.getClass().getSimpleName())) {
            stacks.add(Log.getStackTraceString(violation));
        }
    }

    /**
     * Runs the StrictMode VM listener callbacks queued since the last call on the calling thread,
     * which records the violations they carry.
     */
    private void runPendingVmCallbacks() {
        for (Runnable callback = pendingVmCallbacks.poll(); callback != null; callback = pendingVmCallbacks.poll()) {
            callback.run();
        }
    }

    /**
     * Commits the deliberate violation, checks it was reported, and clears it from the record.
     * Restores the policies before failing, including when the deliberate violation throws.
     */
    private void checkDetected(@NonNull Runnable deliberateViolation) {
        try {
            deliberateViolation.run();
        } catch (RuntimeException | Error e) {
            stop();
            throw e;
        }
        runPendingVmCallbacks();
        if (stacks.isEmpty()) {
            stop();
            Assert.fail("this device does not report " + violationName);
        }
        stacks.clear();
    }
}
