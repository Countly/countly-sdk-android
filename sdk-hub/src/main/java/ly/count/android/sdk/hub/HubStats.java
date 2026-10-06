package ly.count.android.sdk.hub;

import androidx.annotation.NonNull;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Counts what the hub did for each app, for "adb shell dumpsys activity service" and for tests. It
 * records no request data, only counts, sizes and the reason of the latest problem.
 */
final class HubStats {
    private final Map<String, long[]> perApp = new LinkedHashMap<>();
    private long unknownCallers = 0;
    private String lastProblem = null;

    private static final int DELIVERED = 0;
    private static final int REFUSED = 1;
    private static final int FAILED = 2;
    private static final int BUSY = 3;
    private static final int BYTES_UP = 4;
    private static final int BYTES_DOWN = 5;

    /**
     * A request was sent to the server and answered.
     *
     * @param packageName the app it came from
     * @param bytesUp the size of the request body
     * @param bytesDown the size of the response body
     */
    synchronized void onDelivered(@NonNull String packageName, long bytesUp, long bytesDown) {
        long[] counters = countersOf(packageName);
        counters[DELIVERED]++;
        counters[BYTES_UP] += bytesUp;
        counters[BYTES_DOWN] += bytesDown;
    }

    /**
     * A request of an allowed app was refused without being sent.
     *
     * @param packageName the app it came from
     * @param reason why
     */
    synchronized void onRefused(@NonNull String packageName, @NonNull String reason) {
        countersOf(packageName)[REFUSED]++;
        lastProblem = packageName + ": refused, " + reason;
    }

    /**
     * A request could not get a server response.
     *
     * @param packageName the app it came from
     * @param reason why
     */
    synchronized void onFailed(@NonNull String packageName, @NonNull String reason) {
        countersOf(packageName)[FAILED]++;
        lastProblem = packageName + ": failed, " + reason;
    }

    /**
     * A request was turned away because too many were already waiting for the uplink.
     *
     * @param packageName the app it came from
     */
    synchronized void onBusy(@NonNull String packageName) {
        countersOf(packageName)[BUSY]++;
        lastProblem = packageName + ": busy";
    }

    /**
     * A caller that is not an allowed app tried to use the hub.
     *
     * @param uid the caller's user id
     */
    synchronized void onUnknownCaller(int uid) {
        unknownCallers++;
        lastProblem = "uid " + uid + ": not an allowed app";
    }

    /**
     * @param packageName the app
     * @return how many of its requests were delivered
     */
    synchronized long delivered(@NonNull String packageName) {
        long[] counters = perApp.get(packageName);
        return counters == null ? 0 : counters[DELIVERED];
    }

    /**
     * @param packageName the app
     * @return how many of its requests were refused
     */
    synchronized long refused(@NonNull String packageName) {
        long[] counters = perApp.get(packageName);
        return counters == null ? 0 : counters[REFUSED];
    }

    /**
     * @return how many calls came from callers that are not allowed apps
     */
    synchronized long unknownCallers() {
        return unknownCallers;
    }

    /**
     * Prints the counters.
     *
     * @param writer where to print
     */
    synchronized void dump(@NonNull PrintWriter writer) {
        writer.println("Countly hub");
        for (Map.Entry<String, long[]> app : perApp.entrySet()) {
            long[] c = app.getValue();
            writer.println("  " + app.getKey() + ": delivered=" + c[DELIVERED] + " refused=" + c[REFUSED] + " failed=" + c[FAILED]
                + " busy=" + c[BUSY] + " bytesUp=" + c[BYTES_UP] + " bytesDown=" + c[BYTES_DOWN]);
        }
        writer.println("  unknown callers: " + unknownCallers);
        writer.println("  last problem: " + (lastProblem == null ? "none" : lastProblem));
    }

    /**
     * @param packageName the app
     * @return its counters, created on first use
     */
    private @NonNull long[] countersOf(@NonNull String packageName) {
        long[] counters = perApp.get(packageName);
        if (counters == null) {
            counters = new long[6];
            perApp.put(packageName, counters);
        }
        return counters;
    }
}
