package ly.count.android.sdk;

import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.os.strictmode.Violation;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;

/**
 * Opt-in audit that runs the whole suite under StrictMode detectAll, for the VM policy and for the
 * thread policy of the test thread and the main thread, and records every violation whose stack
 * passes through ly.count.android.sdk. Tests call the SDK from the test thread, which stands in for
 * the app's main thread.
 * <p>
 * Records go, one tab separated line each, to {@value #FILE_NAME} in the additional test output
 * directory, which the Gradle connected test task pulls to
 * sdk/build/outputs/connected_android_test_additional_output. Enable it with
 * {@code -Pandroid.testInstrumentationRunnerArguments.listener=ly.count.android.sdk.StrictModeAuditListener}
 * and check the records with .github/scripts/strictmode_audit.py.
 */
public class StrictModeAuditListener extends RunListener {
    static final String FILE_NAME = "strictmode-violations.tsv";
    private static final String TAG = "CountlyStrictAudit";

    //violations are reported on the violating thread, file writes happen here so no thread policy sees them
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private volatile String currentTest = "none";
    private BufferedWriter out;

    @Override
    public void testRunStarted(Description description) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Log.w(TAG, "StrictMode penalty listeners need API 28, the audit is off on API " + Build.VERSION.SDK_INT);
            return;
        }
        final File outputFile = outputFile();
        writer.execute(() -> open(outputFile));
        write("# started\tapi=" + Build.VERSION.SDK_INT);

        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
            .detectAll()
            .penaltyListener(Runnable::run, v -> report("VM", v))
            .build());
        StrictMode.setThreadPolicy(threadPolicy("TEST_THREAD"));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> StrictMode.setThreadPolicy(threadPolicy("MAIN_THREAD")));
    }

    @Override
    public void testStarted(Description description) {
        currentTest = description.getClassName() + "#" + description.getMethodName();
    }

    @Override
    public void testRunFinished(Result result) {
        write("# finished\ttests=" + result.getRunCount());
        writer.execute(this::close);
        writer.shutdown();
        try {
            writer.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * The record file inside the additional test output directory, or null when the runner was not
     * given one, in which case records only go to logcat.
     */
    @Nullable
    private static File outputFile() {
        Bundle arguments = InstrumentationRegistry.getArguments();
        String directory = arguments.getString("additionalTestOutputDir");
        if (directory == null) {
            return null;
        }
        return new File(directory, FILE_NAME);
    }

    /**
     * Opens the record file for appending, on the writer thread.
     */
    private void open(@Nullable File outputFile) {
        if (outputFile == null) {
            Log.w(TAG, "no additionalTestOutputDir argument, violations are only logged");
            return;
        }
        try {
            File parent = outputFile.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                Log.w(TAG, "could not create " + parent);
            }
            out = new BufferedWriter(new FileWriter(outputFile, true));
        } catch (IOException e) {
            Log.w(TAG, "could not open " + outputFile, e);
        }
    }

    /**
     * Closes the record file, on the writer thread.
     */
    private void close() {
        if (out == null) {
            return;
        }
        try {
            out.close();
        } catch (IOException e) {
            Log.w(TAG, "could not close the record file", e);
        }
        out = null;
    }

    /**
     * Logs the line and appends it to the record file on the writer thread, flushing each line so a
     * crash of the instrumentation process keeps everything recorded before it.
     */
    private void write(@NonNull final String line) {
        Log.w(TAG, line);
        writer.execute(() -> {
            if (out == null) {
                return;
            }
            try {
                out.write(line);
                out.newLine();
                out.flush();
            } catch (IOException e) {
                Log.w(TAG, "could not write a record", e);
            }
        });
    }

    /**
     * Builds a thread policy that detects everything and reports through {@link #report}.
     */
    private StrictMode.ThreadPolicy threadPolicy(@NonNull String label) {
        return new StrictMode.ThreadPolicy.Builder()
            .detectAll()
            .penaltyListener(Runnable::run, v -> report(label, v))
            .build();
    }

    /**
     * Records the violation as policy, type, test, message, and every ly.count.android.sdk frame of
     * its stack and of its cause, separated by tabs. Violations without such a frame are dropped.
     */
    private void report(@NonNull String policy, @NonNull Violation violation) {
        StringBuilder frames = new StringBuilder();
        appendSdkFrames(frames, violation);
        if (violation.getCause() != null) {
            appendSdkFrames(frames, violation.getCause());
        }
        if (frames.length() == 0) {
            return;
        }
        String message = String.valueOf(violation.getMessage()).replace('\n', ' ').replace('\t', ' ');
        if (message.length() > 160) {
            message = message.substring(0, 160);
        }
        write(policy + "\t" + violation.getClass().getSimpleName() + "\t" + currentTest + "\t" + message + "\t" + frames);
    }

    /**
     * Appends the ly.count.android.sdk frames of the throwable's stack, in order, separated by ';'.
     */
    private static void appendSdkFrames(@NonNull StringBuilder out, @NonNull Throwable throwable) {
        for (StackTraceElement element : throwable.getStackTrace()) {
            if (element.getClassName().startsWith("ly.count.android.sdk.")) {
                if (out.length() > 0) {
                    out.append(';');
                }
                out.append(element.getClassName()).append('.').append(element.getMethodName()).append(':').append(element.getLineNumber());
            }
        }
    }
}
