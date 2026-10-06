package ly.count.android.sdk.hub;

import androidx.annotation.NonNull;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Relays the requests of allowed apps to the server, one at a time, over a single uplink thread.
 * <p>
 * Each request is checked first, then queued for the uplink, and the caller waits for the server's
 * response. When the queue is full the request fails right away instead of waiting, so a burst from
 * many apps cannot hold on to the hub's binder threads; the apps' SDKs retry it later as they do after
 * any network failure.
 */
final class HubRelay {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final int MIN_WAIT_MILLIS = 1_000;
    private static final int MAX_WAIT_MILLIS = 120_000;

    final HubStats stats = new HubStats();
    private final RequestGate gate;
    private final HubUplink uplink;
    private final ThreadPoolExecutor sender;

    /**
     * @param gate decides which requests are relayed
     * @param uplink sends the relayed requests to the server
     * @param maxQueuedRequests how many accepted requests may wait for the uplink
     */
    HubRelay(@NonNull RequestGate gate, @NonNull HubUplink uplink, int maxQueuedRequests) {
        this.gate = gate;
        this.uplink = uplink;
        this.sender = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(maxQueuedRequests), runnable -> {
            Thread thread = new Thread(runnable, "countly-hub-uplink");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Checks a request of an allowed app and, when it passes, sends it to the server and waits for the
     * response, at most as long as the app waits for it.
     *
     * @param app the app the request came from
     * @param request the request
     * @return the server's response, or a 403 response made up by the hub when the request was refused
     * @throws IOException if the request could not get a server response
     */
    @NonNull HubResponse relay(@NonNull HubAllowedApp app, @NonNull final HubRequest request) throws IOException {
        String refusal = gate.check(app, request);
        if (refusal != null) {
            stats.onRefused(app.packageName, refusal);
            return refusal(403, refusal);
        }

        Future<HubResponse> pending;
        try {
            pending = sender.submit(() -> uplink.send(request));
        } catch (RejectedExecutionException e) {
            stats.onBusy(app.packageName);
            throw new IOException("the hub is busy");
        }

        long waitMillis = Math.max(MIN_WAIT_MILLIS, Math.min(MAX_WAIT_MILLIS, request.getTimeoutMillis()));
        try {
            HubResponse response = pending.get(waitMillis, TimeUnit.MILLISECONDS);
            stats.onDelivered(app.packageName, request.getBodyLength(), response.getBody().length);
            return response;
        } catch (TimeoutException e) {
            pending.cancel(true);
            stats.onFailed(app.packageName, "no server response within " + waitMillis + " ms");
            throw new IOException("the server did not respond within " + waitMillis + " ms");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            String reason = cause == null || cause.getMessage() == null ? String.valueOf(cause) : cause.getMessage();
            stats.onFailed(app.packageName, reason);
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IOException("the uplink failed: " + reason, cause);
        } catch (InterruptedException e) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("interrupted while waiting for the server");
        }
    }

    /**
     * Makes the response for a request the hub refused.
     *
     * @param status the HTTP status
     * @param reason why the request was refused
     * @return a JSON response carrying the reason under "error"
     */
    static @NonNull HubResponse refusal(int status, @NonNull String reason) {
        byte[] body = ("{\"error\":\"" + jsonEscape(reason) + "\"}").getBytes(UTF_8);
        return new HubResponse(status, Collections.singletonMap("Content-Type", "application/json"), body);
    }

    /**
     * Stops the uplink thread and drops the requests still waiting for it.
     */
    void shutdown() {
        sender.shutdownNow();
    }

    /**
     * Prints the counters and the uplink queue.
     *
     * @param writer where to print
     */
    void dump(@NonNull PrintWriter writer) {
        stats.dump(writer);
        writer.println("  waiting for the uplink: " + waitingCount());
    }

    /**
     * @return how many accepted requests are waiting for their turn on the uplink
     */
    int waitingCount() {
        return sender.getQueue().size();
    }

    /**
     * @param text any text
     * @return the text escaped for a JSON string
     */
    private static @NonNull String jsonEscape(@NonNull String text) {
        StringBuilder escaped = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\\') {
                escaped.append('\\').append(c);
            } else if (c < 0x20) {
                escaped.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
            } else {
                escaped.append(c);
            }
        }
        return escaped.toString();
    }
}
