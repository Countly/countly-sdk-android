package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Server armed connection test: a top level 'ct' on a live server config response runs one probe battery of
 * parameterless GETs and queues one 'ct_results' report. Nothing about it is persisted.
 */
class ModuleConnectionTest extends ModuleBase {
    static final String keyResults = "ct_results";

    static final int DEFAULT_PER_REQUEST_TIMEOUT_MS = 10_000;
    static final long BATTERY_CAP_EXTRA_MS = 30_000;
    static final int MAX_ROWS = 32;
    static final int MAX_REPORT_BYTES = 8 * 1024;
    static final int MAX_ERROR_LENGTH = 256;

    static final String errorTimeout = "timeout";
    static final String errorTransport = "transport error";
    static final String errorRedirected = "redirected";
    static final String errorNotRun = "not run: battery cap";
    //success qualifier of opaque (browser) probes, never produced here, but by cross SDK rule it survives the size cap
    static final String errorOpaque = "opaque";

    /** Feature rows in report order. A row with no paths is the {@code sc} row, measured from the fetch that armed us. */
    static final ProbeRow[] ROWS = {
        new ProbeRow("core", true, "/o/ping"),
        new ProbeRow("core-write", false, "/i"),
        new ProbeRow("sc", false),
        new ProbeRow("rc", false, "/o/sdk?method=rc"),
        new ProbeRow("ab", false, "/o/sdk?method=ab_fetch_variants"),
        new ProbeRow("feedback", false, "/o/sdk?method=feedback"),
        new ProbeRow("feedback-widget", false, "/o/surveys/nps/widget", "/o/surveys/survey/widget", "/o/feedback/widget"),
        new ProbeRow("feedback-submit", false, "/i/feedback/inputs"),
        new ProbeRow("content", false, "/o/sdk/content"),
        new ProbeRow("feedback-page", false, "/feedback/nps", "/feedback/survey", "/feedback/rating"),
        new ProbeRow("feedback-assets", true, "/surveys/images/ct-probe.png", "/star-rating/images/ct-probe.png"),
        new ProbeRow("content-page", false, "/_external/content/"),
    };

    static class ProbeRow {
        final String feature;
        /** Rows 1 and 11 have no legitimate 4xx answer, so anything but a 2xx is a fault there. */
        final boolean requiresSuccessStatus;
        final String[] paths;

        ProbeRow(String feature, boolean requiresSuccessStatus, String... paths) {
            this.feature = feature;
            this.requiresSuccessStatus = requiresSuccessStatus;
            this.paths = paths;
        }
    }

    /** Raw outcome of one probe request, before grading. */
    static class ProbeOutcome {
        /** HTTP status, or 0 when no status could be read. */
        int status;
        long ms;
        /** {@link #errorTimeout} or {@link #errorTransport} when status is 0, otherwise null. */
        String failure;
    }

    /** One graded report row. */
    static class ResultRow {
        final String feature;
        boolean ok;
        int status;
        long ms;
        String error;
        int pathCount;

        ResultRow(String feature) {
            this.feature = feature;
        }
    }

    /** Issues one probe. Package-private so tests can swap the network out. */
    interface ProbeTransport {
        /** @param cp supplies the instance's SSL socket factory and custom headers, null only when the queue is mocked */
        @NonNull ProbeOutcome probe(@NonNull String url, int timeoutMs, @Nullable ConnectionProcessor cp);
    }

    final AtomicBoolean batteryRunning = new AtomicBoolean(false);
    volatile boolean halted = false;

    // package-private so tests can shrink the deadlines
    int perRequestTimeoutMs = DEFAULT_PER_REQUEST_TIMEOUT_MS;
    long batteryCapExtraMs = BATTERY_CAP_EXTRA_MS;
    ProbeTransport transport = new HttpProbeTransport();

    ModuleConnectionTest(@NonNull Countly cly, @NonNull CountlyConfig config) {
        super(cly, config);
        L.v("[ModuleConnectionTest] Initialising");
    }

    @Override
    void halt() {
        halted = true;
    }

    /** Runs the battery on a background thread, once per delivery. scLatencyMs is negative when unknown. */
    void startBattery(final long scLatencyMs) {
        if (halted) {
            L.d("[ModuleConnectionTest] startBattery, module halted, ignoring");
            return;
        }

        if (!configProvider.getNetworkingEnabled()) {
            L.d("[ModuleConnectionTest] startBattery, networking is disabled by server config, ignoring");
            return;
        }

        if (!batteryRunning.compareAndSet(false, true)) {
            L.d("[ModuleConnectionTest] startBattery, a battery is already running, ignoring this delivery");
            return;
        }

        L.i("[ModuleConnectionTest] startBattery, connection test armed by the server, running the probe battery");

        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    runBatteryAndReport(scLatencyMs);
                } catch (Throwable t) {
                    L.e("[ModuleConnectionTest] Battery failed unexpectedly, " + t);
                } finally {
                    batteryRunning.set(false);
                }
            }
        }, "Countly-ConnectionTest");
        worker.setDaemon(true);
        worker.start();
    }

    void runBatteryAndReport(long scLatencyMs) {
        List<ResultRow> rows = runBattery(scLatencyMs);

        if (halted) {
            L.d("[ModuleConnectionTest] runBatteryAndReport, module halted during the battery, dropping the report");
            return;
        }

        String report = buildReport(rows);
        L.d("[ModuleConnectionTest] runBatteryAndReport, queueing report [" + report + "]");
        requestQueueProvider.sendConnectionTestResults(report);
    }

    @NonNull List<ResultRow> runBattery(long scLatencyMs) {
        String serverUrl = baseInfoProvider.getServerURL();
        ConnectionProcessor cp = requestQueueProvider.createConnectionProcessor();

        int requestCount = 0;
        for (ProbeRow row : ROWS) {
            requestCount += row.paths.length;
        }
        long capMs = (long) requestCount * perRequestTimeoutMs + batteryCapExtraMs;
        long batteryStart = System.nanoTime();

        List<ResultRow> results = new ArrayList<>(ROWS.length);
        for (ProbeRow row : ROWS) {
            if (halted) {
                break;
            }

            if (row.paths.length == 0) {
                if (scLatencyMs >= 0) {
                    ResultRow sc = new ResultRow(row.feature);
                    sc.ok = true;
                    sc.status = HttpURLConnection.HTTP_OK;
                    sc.ms = scLatencyMs;
                    results.add(sc);
                }
                continue;
            }

            long elapsedMs = (System.nanoTime() - batteryStart) / 1_000_000L;
            if (elapsedMs > capMs) {
                ResultRow notRun = new ResultRow(row.feature);
                notRun.error = errorNotRun;
                results.add(notRun);
                continue;
            }

            results.add(probeRow(row, serverUrl, cp));
        }

        return results;
    }

    @NonNull ResultRow probeRow(@NonNull ProbeRow row, @NonNull String serverUrl, @Nullable ConnectionProcessor cp) {
        ResultRow result = new ResultRow(row.feature);
        result.ok = true;
        result.pathCount = row.paths.length;

        for (int i = 0; i < row.paths.length; i++) {
            String url = buildProbeUrl(serverUrl, row.paths[i]);
            ProbeOutcome outcome = transport.probe(url, perRequestTimeoutMs, cp);
            result.ms += outcome.ms;

            String failure = grade(outcome, row.requiresSuccessStatus);
            L.v("[ModuleConnectionTest] probeRow, [" + row.feature + "] " + url + " -> status[" + outcome.status + "] ms[" + outcome.ms + "] failure[" + failure + "]");

            if (i == 0) {
                //the row reports the first status observed, unless a failing path overrides it below
                result.status = outcome.status;
            }

            if (failure != null && result.ok) {
                // first failing path decides the row's status and reason
                result.ok = false;
                result.status = outcome.status;
                result.error = failure;
            }
        }

        return result;
    }

    /**
     * @return null when the Countly application answered, otherwise the {@code e} reason
     */
    @Nullable static String grade(@NonNull ProbeOutcome outcome, boolean requiresSuccessStatus) {
        int status = outcome.status;
        if (status <= 0) {
            return outcome.failure != null ? outcome.failure : errorTransport;
        }
        if (status >= 200 && status < 300) {
            return null;
        }
        if (status >= 300 && status < 400) {
            return errorRedirected;
        }
        //a 4xx other than 403 is the Countly application answering, unless this row has no legitimate 4xx answer
        if (!requiresSuccessStatus && status >= 400 && status < 500 && status != HttpURLConnection.HTTP_FORBIDDEN) {
            return null;
        }
        return "HTTP " + status;
    }

    /** Resolves a path against the configured URL (path prefix included) and appends the marker and cache buster. */
    @NonNull static String buildProbeUrl(@NonNull String serverUrl, @NonNull String path) {
        String base = serverUrl;
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String joined = path.startsWith("/") ? base + path : base + "/" + path;
        String separator = joined.contains("?") ? "&" : "?";
        return joined + separator + "ct=1&_=" + System.currentTimeMillis();
    }

    @NonNull String buildReport(@NonNull List<ResultRow> rows) {
        JSONObject report = new JSONObject();
        JSONArray results = new JSONArray();
        try {
            report.put("ts", System.currentTimeMillis());
            JSONObject sdk = new JSONObject();
            sdk.put("name", _cly.COUNTLY_SDK_NAME);
            sdk.put("version", _cly.COUNTLY_SDK_VERSION_STRING);
            report.put("sdk", sdk);
            report.put("results", results);

            int rowLimit = Math.min(rows.size(), MAX_ROWS);
            for (int i = 0; i < rowLimit; i++) {
                results.put(rowToJson(rows.get(i)));
            }

            if (utf8Length(report.toString()) > MAX_REPORT_BYTES) {
                L.w("[ModuleConnectionTest] buildReport, report is over the size cap, dropping error details");
                for (int i = 0; i < results.length(); i++) {
                    JSONObject row = results.getJSONObject(i);
                    if (!errorOpaque.equals(row.optString("e"))) {
                        row.remove("e");
                    }
                }
            }

            while (utf8Length(report.toString()) > MAX_REPORT_BYTES && results.length() > 0) {
                L.w("[ModuleConnectionTest] buildReport, report is still over the size cap, dropping the last row");
                results.remove(results.length() - 1);
            }
        } catch (JSONException e) {
            L.w("[ModuleConnectionTest] buildReport, failed to build the report, " + e);
        }

        return report.toString();
    }

    @NonNull static JSONObject rowToJson(@NonNull ResultRow row) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("f", row.feature);
        json.put("ok", row.ok);
        json.put("st", row.status);
        json.put("ms", row.ms);
        if (row.error != null) {
            String error = row.error;
            if (error.length() > MAX_ERROR_LENGTH) {
                error = error.substring(0, MAX_ERROR_LENGTH);
            }
            json.put("e", error);
        }
        if (row.pathCount > 1) {
            json.put("n", row.pathCount);
        }
        return json;
    }

    static int utf8Length(@NonNull String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    /** Default transport, a bare GET built by {@link ConnectionProcessor#urlConnectionForProbe}. */
    static class HttpProbeTransport implements ProbeTransport {
        //the body is read to its end so 'ms' covers the whole response, but a page can be large and only the status
        //matters, so the read stops here
        static final int MAX_DRAIN_BYTES = 64 * 1024;

        @Override
        public @NonNull ProbeOutcome probe(@NonNull String url, int timeoutMs, @Nullable ConnectionProcessor cp) {
            ProbeOutcome outcome = new ProbeOutcome();
            long start = System.nanoTime();
            HttpURLConnection connection = null;
            try {
                if (cp != null) {
                    connection = cp.urlConnectionForProbe(url, timeoutMs);
                } else {
                    connection = (HttpURLConnection) new URL(url).openConnection();
                    connection.setRequestMethod("GET");
                    connection.setInstanceFollowRedirects(false);
                    connection.setUseCaches(false);
                    connection.setConnectTimeout(timeoutMs);
                    connection.setReadTimeout(timeoutMs);
                }

                outcome.status = connection.getResponseCode();
                drain(connection);
            } catch (SocketTimeoutException e) {
                outcome.status = 0;
                outcome.failure = errorTimeout;
            } catch (Exception e) {
                outcome.status = 0;
                outcome.failure = errorTransport;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                outcome.ms = (System.nanoTime() - start) / 1_000_000L;
            }
            return outcome;
        }

        /** Reads the body, up to {@link #MAX_DRAIN_BYTES}, and discards it. */
        private static void drain(@NonNull HttpURLConnection connection) {
            try (InputStream stream = bodyStream(connection)) {
                if (stream == null) {
                    return;
                }
                byte[] buffer = new byte[4096];
                int total = 0;
                int read;
                while (total < MAX_DRAIN_BYTES && (read = stream.read(buffer)) != -1) {
                    total += read;
                }
            } catch (IOException ignored) {
                // the status is already known, a body read failure changes nothing
            }
        }

        /** The response body, or the error body for a fault status. Null when neither is available. */
        @Nullable private static InputStream bodyStream(@NonNull HttpURLConnection connection) {
            try {
                return connection.getInputStream();
            } catch (IOException e) {
                return connection.getErrorStream();
            }
        }
    }
}
