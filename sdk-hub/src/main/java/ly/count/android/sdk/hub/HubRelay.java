package ly.count.android.sdk.hub;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Relays the requests of allowed apps to the server, one at a time, over a single uplink thread.
 * <p>
 * Admitting a request is quick: it is checked, counted against the limits and queued, and its outcome
 * is reported later through a {@link Completion}. No caller thread waits while a request waits for the
 * network, so a slow server cannot tie up the threads the hub's host app answers other calls with.
 * <p>
 * Requests that need an answer right away, such as remote config or the SDK behavior settings, are sent
 * before requests from the SDK's request queue, which retries by itself. A queued request that has waited
 * longer than {@link Limits#backgroundMaxWaitMillis} goes first, so a steady stream of the other kind
 * cannot hold it back for good. A request that has waited past its caller's budget is not sent at all:
 * the caller has given up and will retry, and sending it anyway could store the same data twice.
 */
final class HubRelay {
    /**
     * How long a request from the SDK's request queue waits behind other requests at most, by default.
     */
    static final long DEFAULT_BACKGROUND_MAX_WAIT_MILLIS = 5_000;

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final long MIN_BUDGET_MILLIS = 1_000;
    private static final long MAX_BUDGET_MILLIS = 600_000;
    private static final long REPLY_GRACE_MILLIS = 10_000;

    /**
     * Receives the outcome of an admitted request, exactly once.
     */
    interface Completion {
        /**
         * @param response the server's response, or null when there is none
         * @param failure why there is no response, or null when there is one
         */
        void complete(@Nullable HubResponse response, @Nullable String failure);
    }

    /**
     * The limits the relay enforces.
     */
    static final class Limits {
        final int maxQueuedRequests;
        final int maxQueuedRequestsPerApp;
        final long maxQueuedBodyBytes;
        final long uplinkTimeoutMillis;
        final long backgroundMaxWaitMillis;

        /**
         * @param maxQueuedRequests how many admitted requests may wait for the uplink
         * @param maxQueuedRequestsPerApp how many requests of one app may be waiting or in flight
         * @param maxQueuedBodyBytes how many body bytes may be held for waiting and in-flight requests
         * @param uplinkTimeoutMillis the uplink's connect and read timeout
         * @param backgroundMaxWaitMillis how long a request from the SDK's request queue may wait behind
         * requests the apps are waiting on before it is sent first
         */
        Limits(int maxQueuedRequests, int maxQueuedRequestsPerApp, long maxQueuedBodyBytes, long uplinkTimeoutMillis, long backgroundMaxWaitMillis) {
            this.maxQueuedRequests = maxQueuedRequests;
            this.maxQueuedRequestsPerApp = maxQueuedRequestsPerApp;
            this.maxQueuedBodyBytes = maxQueuedBodyBytes;
            this.uplinkTimeoutMillis = uplinkTimeoutMillis;
            this.backgroundMaxWaitMillis = backgroundMaxWaitMillis;
        }
    }

    /**
     * One admitted request.
     */
    private static final class Job {
        final HubAllowedApp app;
        final HubRequest request;
        final Completion completion;
        final long queuedAtNanos;
        final long deadlineNanos;

        /**
         * @param app the app the request came from
         * @param request the request, for its allowed path
         * @param completion receives the outcome
         * @param queuedAtNanos when it was admitted
         * @param deadlineNanos the latest time it may still be sent
         */
        Job(@NonNull HubAllowedApp app, @NonNull HubRequest request, @NonNull Completion completion, long queuedAtNanos, long deadlineNanos) {
            this.app = app;
            this.request = request;
            this.completion = completion;
            this.queuedAtNanos = queuedAtNanos;
            this.deadlineNanos = deadlineNanos;
        }
    }

    final HubStats stats = new HubStats();
    private final RequestGate gate;
    private final HubUplink uplink;
    private final Limits limits;
    private final Object lock = new Object();
    private final ArrayDeque<Job> foreground = new ArrayDeque<>();
    private final ArrayDeque<Job> background = new ArrayDeque<>();
    private final Map<String, Integer> outstandingPerApp = new HashMap<>();
    private final Thread worker;
    private long outstandingBodyBytes = 0;
    private boolean stopped = false;

    /**
     * @param gate decides which requests are relayed
     * @param uplink sends the relayed requests to the server
     * @param limits the limits to enforce
     * @param threadFactory creates the uplink thread
     */
    HubRelay(@NonNull RequestGate gate, @NonNull HubUplink uplink, @NonNull Limits limits, @NonNull ThreadFactory threadFactory) {
        this.gate = gate;
        this.uplink = uplink;
        this.limits = limits;
        this.worker = threadFactory.newThread(this::work);
        this.worker.setDaemon(true);
        this.worker.start();
    }

    /**
     * Checks a request of an allowed app and queues it for the uplink when it passes.
     *
     * @param app the app the request came from
     * @param request the request
     * @param completion receives the outcome when the request is queued
     * @return a response made up by the hub when the request is refused, or null when it was queued and
     * its outcome will reach the completion
     * @throws IOException if the hub cannot take the request now; the app's SDK retries it later
     */
    @Nullable HubResponse submit(@NonNull HubAllowedApp app, @NonNull HubRequest request, @NonNull Completion completion) throws IOException {
        RequestGate.Decision decision = gate.check(app, request);
        if (decision.refusal != null) {
            stats.onRefused(app.packageName, decision.refusal);
            return refusal(decision.status, decision.refusal, decision.finalRefusal);
        }

        long bodyLength = request.getBodyLength();
        long budgetNanos = TimeUnit.MILLISECONDS.toNanos(budgetMillis(request));
        synchronized (lock) {
            if (stopped) {
                throw new IOException("the hub is stopping");
            }
            Integer outstanding = outstandingPerApp.get(app.packageName);
            int appOutstanding = outstanding == null ? 0 : outstanding;
            if (foreground.size() + background.size() >= limits.maxQueuedRequests
                || appOutstanding >= limits.maxQueuedRequestsPerApp
                || (bodyLength > 0 && outstandingBodyBytes > 0 && outstandingBodyBytes + bodyLength > limits.maxQueuedBodyBytes)) {
                stats.onBusy(app.packageName);
                throw new IOException("the hub is busy");
            }
            outstandingPerApp.put(app.packageName, appOutstanding + 1);
            outstandingBodyBytes += bodyLength;
            long now = System.nanoTime();
            Job job = new Job(app, request.withPath(decision.path), completion, now, now + budgetNanos);
            (decision.background ? background : foreground).addLast(job);
            lock.notifyAll();
        }
        return null;
    }

    /**
     * Tells how long a caller should wait for the outcome of a queued request: its budget for waiting in
     * the queue, plus the longest the uplink may take once the request is sent, plus a margin. A request
     * that has not been sent within its budget is reported as expired, so the outcome always arrives
     * within this time.
     *
     * @param request the request
     * @return the time in milliseconds
     */
    long replyWithinMillis(@NonNull HubRequest request) {
        return budgetMillis(request) + 2 * limits.uplinkTimeoutMillis + REPLY_GRACE_MILLIS;
    }

    /**
     * Makes the response for a request the hub refused.
     *
     * @param status the HTTP status
     * @param reason why the request was refused
     * @param finalRefusal whether the request can never be accepted, which tells the SDK to drop it
     * @return a JSON response carrying the reason under "error"
     */
    static @NonNull HubResponse refusal(int status, @NonNull String reason, boolean finalRefusal) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        if (finalRefusal) {
            headers.put(HubProtocol.FINAL_REFUSAL_HEADER, reason);
        }
        byte[] body = ("{\"error\":\"" + jsonEscape(reason) + "\"}").getBytes(UTF_8);
        return new HubResponse(status, headers, body);
    }

    /**
     * Stops the uplink thread. Requests still waiting are reported as failed, and the apps' SDKs retry
     * them later.
     */
    void shutdown() {
        List<Job> waiting;
        synchronized (lock) {
            stopped = true;
            waiting = new ArrayList<>(foreground);
            waiting.addAll(background);
            foreground.clear();
            background.clear();
            lock.notifyAll();
        }
        worker.interrupt();
        for (Job job : waiting) {
            finish(job, null, "the hub is stopping");
        }
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
     * @return how many admitted requests are waiting for their turn on the uplink
     */
    int waitingCount() {
        synchronized (lock) {
            return foreground.size() + background.size();
        }
    }

    /**
     * Sends the queued requests one by one until the relay is shut down.
     */
    private void work() {
        while (true) {
            Job job;
            synchronized (lock) {
                while (!stopped && foreground.isEmpty() && background.isEmpty()) {
                    try {
                        lock.wait();
                    } catch (InterruptedException ignored) {
                    }
                }
                if (stopped) {
                    return;
                }
                job = nextJobLocked();
            }
            if (System.nanoTime() > job.deadlineNanos) {
                stats.onExpired(job.app.packageName);
                finish(job, null, "the request waited longer than its budget and was not sent");
                continue;
            }
            try {
                HubResponse response = uplink.send(job.request);
                stats.onDelivered(job.app.packageName, job.request.getBodyLength(), response.getBody().length);
                finish(job, response, null);
            } catch (IOException e) {
                String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                stats.onFailed(job.app.packageName, reason);
                finish(job, null, reason);
            } catch (RuntimeException | OutOfMemoryError e) {
                stats.onFailed(job.app.packageName, "the uplink failed: " + e.getClass().getSimpleName());
                finish(job, null, "the uplink failed");
            }
        }
    }

    /**
     * Picks the next request to send: a request the apps are waiting on before one from the SDK's request
     * queue, unless the oldest queued request has waited longer than the limit.
     *
     * @return the request, taken off its queue
     */
    private @NonNull Job nextJobLocked() {
        Job oldestBackground = background.peekFirst();
        if (oldestBackground != null
            && (foreground.isEmpty() || System.nanoTime() - oldestBackground.queuedAtNanos >= TimeUnit.MILLISECONDS.toNanos(limits.backgroundMaxWaitMillis))) {
            return background.pollFirst();
        }
        return foreground.pollFirst();
    }

    /**
     * Releases what a request counted against the limits and reports its outcome.
     *
     * @param job the request
     * @param response the response, or null
     * @param failure the failure, or null
     */
    private void finish(@NonNull Job job, @Nullable HubResponse response, @Nullable String failure) {
        synchronized (lock) {
            Integer outstanding = outstandingPerApp.get(job.app.packageName);
            if (outstanding != null && outstanding > 1) {
                outstandingPerApp.put(job.app.packageName, outstanding - 1);
            } else {
                outstandingPerApp.remove(job.app.packageName);
            }
            outstandingBodyBytes -= job.request.getBodyLength();
        }
        try {
            job.completion.complete(response, failure);
        } catch (RuntimeException ignored) {
        }
    }

    /**
     * @param request a request
     * @return how long it may wait in the queue, from the caller's own timeout, within sane bounds
     */
    private static long budgetMillis(@NonNull HubRequest request) {
        return Math.max(MIN_BUDGET_MILLIS, Math.min(MAX_BUDGET_MILLIS, request.getTimeoutMillis()));
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
