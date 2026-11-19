package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

public class HealthCheckCounter implements HealthTracker {
    public long countLogWarning = 0;
    public long countLogError = 0;
    public long countBackoffRequest = 0;
    public int statusCode = -1;
    public String errorMessage = "";
    public int consecutiveBackoffRequest = 0;
    private int consecutiveBackoffRequestCounter = 0;

    /** Method usage counts per feature area and method ("fu"), kept until a health check delivers them. */
    final Map<String, Map<String, Integer>> methodUsage = new HashMap<>();
    /** Log code counts ("lc"), kept until a health check delivers them. */
    final Map<String, Integer> logCodes = new HashMap<>();

    private volatile boolean recordingStoppedForCrash = false;
    @Nullable volatile ConsentProvider consentProvider = null;

    static final int countCap = 65535;

    private final static String keyLogError = "LErr";
    private final static String keyLogWarning = "LWar";
    private final static String keyStatusCode = "RStatC";
    private final static String keyErrorMessage = "REMsg";
    private final static String keyBackoffRequest = "BReq";
    private final static String keyConsecutiveBackoffRequest = "CBReq";
    private final static String keyMethodUsage = "MU";
    private final static String keyLogCodes = "LC";

    final static String requestKeyErrorCount = "el";
    final static String requestKeyWarningCount = "wl";
    final static String requestKeyStatusCode = "sc";
    final static String requestKeyRequestError = "em";
    final static String requestKeyBackoffRequest = "bom";
    final static String requestKeyConsecutiveBackoffRequest = "cbom";
    final static String requestKeyMethodUsage = "fu";
    final static String requestKeyLogCodes = "lc";

    StorageProvider storageProvider;
    ModuleLog L;

    public HealthCheckCounter(@NonNull StorageProvider sp, @NonNull ModuleLog L) {
        assert sp != null;
        assert L != null;

        this.L = L;
        storageProvider = sp;
        setupInitialCounters(storageProvider.getHealthCheckCounterState());

        int automaticCrashes = storageProvider.takeAutomaticCrashCount();
        if (automaticCrashes > 0) {
            synchronized (this) {
                addUsage("crashes", "record:a", automaticCrashes);
            }
            saveState();
        }
    }

    /**
     * Adds the counters a previous run stored to the current ones. A stored state that is not valid JSON is reset and
     * reported as log code e503. Logs at debug level only, as every warning and error is counted by this class.
     *
     * @param initialState the stored state, empty when nothing was stored
     */
    void setupInitialCounters(@NonNull String initialState) {
        assert initialState != null;

        if (initialState == null || initialState.isEmpty()) {
            return;
        }

        JSONObject jsonObject;
        try {
            jsonObject = new JSONObject(initialState);
        } catch (JSONException e) {
            clearAndSave();
            synchronized (this) {
                addLogCode("e503", 1);
            }
            L.d("[HealthCheckCounter] Stored health check state could not be read and is reset, " + e);
            return;
        }

        synchronized (this) {
            countLogWarning += optLong(jsonObject, keyLogWarning);
            countLogError += optLong(jsonObject, keyLogError);
            if (statusCode == -1 && jsonObject.opt(keyStatusCode) instanceof Number) {
                statusCode = ((Number) jsonObject.opt(keyStatusCode)).intValue();
            }
            if (errorMessage.isEmpty() && jsonObject.opt(keyErrorMessage) instanceof String) {
                errorMessage = (String) jsonObject.opt(keyErrorMessage);
            }
            countBackoffRequest += optLong(jsonObject, keyBackoffRequest);
            consecutiveBackoffRequest = (int) Math.max(consecutiveBackoffRequest, optLong(jsonObject, keyConsecutiveBackoffRequest));

            JSONObject storedUsage = jsonObject.optJSONObject(keyMethodUsage);
            if (storedUsage != null) {
                Iterator<String> areas = storedUsage.keys();
                while (areas.hasNext()) {
                    String area = areas.next();
                    JSONObject methods = storedUsage.optJSONObject(area);
                    if (methods == null) {
                        continue;
                    }
                    Iterator<String> methodNames = methods.keys();
                    while (methodNames.hasNext()) {
                        String method = methodNames.next();
                        if (methods.opt(method) instanceof Number) {
                            addUsage(area, method, ((Number) methods.opt(method)).intValue());
                        }
                    }
                }
            }

            JSONObject storedCodes = jsonObject.optJSONObject(keyLogCodes);
            if (storedCodes != null) {
                Iterator<String> codes = storedCodes.keys();
                while (codes.hasNext()) {
                    String code = codes.next();
                    if (storedCodes.opt(code) instanceof Number) {
                        addLogCode(code, ((Number) storedCodes.opt(code)).intValue());
                    }
                }
            }
        }

        L.d("[HealthCheckCounter] Loaded initial health check state: [" + jsonObject + "]");
    }

    private static long optLong(@NonNull JSONObject jsonObject, @NonNull String key) {
        Object value = jsonObject.opt(key);
        return value instanceof Number ? ((Number) value).longValue() : 0;
    }

    @Override public void logWarning() {
        if (recordingStoppedForCrash) {
            return;
        }
        synchronized (this) {
            countLogWarning++;
        }
    }

    @Override public void logError() {
        if (recordingStoppedForCrash) {
            return;
        }
        synchronized (this) {
            countLogError++;
        }
    }

    @Override public void logFailedNetworkRequest(int statusCode, @Nullable String errorResponse) {
        if (statusCode <= 0 || statusCode >= 1000) {
            return;
        }

        String response = errorResponse == null ? "" : errorResponse;
        synchronized (this) {
            this.statusCode = statusCode;
            this.errorMessage = response.length() > 1000 ? response.substring(0, 1000) : response;
        }
    }

    /**
     * Counts a backoff of the request queue and extends the current run of backoffs in a row. The longest run ("cbom") is
     * raised right away, so no save has to end the run to keep it. Records log code w320, and w321 when the backoff continues
     * a run. The run itself is kept in memory only, so a restart ends it.
     */
    @Override public void logBackoffRequest() {
        synchronized (this) {
            boolean continuesRun = consecutiveBackoffRequestCounter > 0;
            countBackoffRequest++;
            consecutiveBackoffRequestCounter++;
            consecutiveBackoffRequest = Math.max(consecutiveBackoffRequest, consecutiveBackoffRequestCounter);

            if (!recordingStoppedForCrash) {
                addLogCode("w320", 1);
                if (continuesRun) {
                    addLogCode("w321", 1);
                }
            }
        }
    }

    /**
     * Ends the current run of backoffs in a row, when a queued request was answered in time. The longest run is already
     * kept by logBackoffRequest.
     */
    @Override public void logConsecutiveBackoffRequest() {
        synchronized (this) {
            consecutiveBackoffRequestCounter = 0;
        }
    }

    @Override public void recordUsage(@NonNull String area, @NonNull String method) {
        if (area == null || method == null || area.isEmpty() || method.isEmpty() || recordingStoppedForCrash) {
            return;
        }

        if (!hasConsentForUsageArea(area)) {
            return;
        }

        synchronized (this) {
            addUsage(area, method, 1);
        }
    }

    @Override public void recordLogCode(@NonNull String code) {
        recordLogCode(code, 1);
    }

    @Override public void recordLogCode(@NonNull String code, int count) {
        if (code == null || code.isEmpty() || count <= 0 || recordingStoppedForCrash) {
            return;
        }

        synchronized (this) {
            addLogCode(code, count);
        }
    }

    @Override public void recordLogCode(@NonNull String code, @NonNull String area) {
        if (!hasConsentForUsageArea(area)) {
            return;
        }

        recordLogCode(code, 1);
    }

    @Override public void stopRecordingForCrash() {
        recordingStoppedForCrash = true;
    }

    /**
     * Whether the feature behind a method usage area has consent now. Knowing which methods an app calls is itself
     * tracking information, so a call of a feature without consent is not counted. An unknown area is not counted either,
     * and nothing is counted before the consent module exists. Every consent counts as given while consent is not required.
     *
     * @param area the method usage area
     * @return true when calls of the area are counted
     */
    boolean hasConsentForUsageArea(@Nullable String area) {
        ConsentProvider consent = consentProvider;
        if (consent == null || area == null) {
            return false;
        }

        switch (area) {
            case "sessions":
                return consent.getConsentSilently(Countly.CountlyFeatureNames.sessions);
            case "events":
                return consent.getConsentSilently(Countly.CountlyFeatureNames.events);
            case "views":
                return consent.getConsentSilently(Countly.CountlyFeatureNames.views);
            case "crashes":
                return consent.getConsentSilently(Countly.CountlyFeatureNames.crashes);
            default:
                return false;
        }
    }

    /**
     * Adds to the count of a method, up to the cap a health check reports. Call while holding this object's lock.
     */
    private void addUsage(@NonNull String area, @NonNull String method, int count) {
        if (count <= 0) {
            return;
        }

        Map<String, Integer> methods = methodUsage.get(area);
        if (methods == null) {
            methods = new HashMap<>();
            methodUsage.put(area, methods);
        }
        Integer current = methods.get(method);
        methods.put(method, (int) Math.min((long) (current == null ? 0 : current) + count, countCap));
    }

    /**
     * Adds to the count of a log code, up to the cap a health check reports. Call while holding this object's lock.
     */
    private void addLogCode(@NonNull String code, int count) {
        if (count <= 0) {
            return;
        }

        Integer current = logCodes.get(code);
        logCodes.put(code, (int) Math.min((long) (current == null ? 0 : current) + count, countCap));
    }

    @Override public void clearAndSave() {
        clearValues();
        storageProvider.setHealthCheckCounterState("");
    }

    /**
     * Persists the counters. The current run of backoffs in a row goes on, as "cbom" already holds the longest run. The
     * storage is written outside this object's lock, since the store records log codes while holding its own lock.
     */
    @Override public void saveState() {
        String state;
        try {
            JSONObject jsonObject = new JSONObject();
            synchronized (this) {
                jsonObject.put(keyLogWarning, countLogWarning);
                jsonObject.put(keyLogError, countLogError);
                jsonObject.put(keyStatusCode, statusCode);
                jsonObject.put(keyErrorMessage, errorMessage);
                jsonObject.put(keyBackoffRequest, countBackoffRequest);
                jsonObject.put(keyConsecutiveBackoffRequest, consecutiveBackoffRequest);
                jsonObject.put(keyMethodUsage, usageToJson());
                jsonObject.put(keyLogCodes, new JSONObject(logCodes));
            }
            state = jsonObject.toString();
        } catch (JSONException e) {
            L.d("[HealthCheckCounter] Failed to save current state, " + e);
            return;
        }

        storageProvider.setHealthCheckCounterState(state);
    }

    /**
     * The method usage counts as a JSON object. Call while holding this object's lock.
     */
    private @NonNull JSONObject usageToJson() throws JSONException {
        JSONObject usage = new JSONObject();
        for (Map.Entry<String, Map<String, Integer>> area : methodUsage.entrySet()) {
            usage.put(area.getKey(), new JSONObject(area.getValue()));
        }
        return usage;
    }

    synchronized void clearValues() {
        countLogWarning = 0;
        countLogError = 0;
        statusCode = -1;
        errorMessage = "";
        countBackoffRequest = 0;
        consecutiveBackoffRequest = 0;
        consecutiveBackoffRequestCounter = 0;
        methodUsage.clear();
        logCodes.clear();
    }

    /**
     * The health check parameter as it is sent, taken from the current counters. "fu" and "lc" are always present, empty
     * when nothing was counted, so the server can tell an SDK that reports them from one that does not.
     *
     * @return a copy of the counters that later counting does not change
     */
    @NonNull JSONObject snapshot() {
        JSONObject jsonObject = new JSONObject();
        try {
            synchronized (this) {
                jsonObject.put(requestKeyErrorCount, countLogError);
                jsonObject.put(requestKeyWarningCount, countLogWarning);
                jsonObject.put(requestKeyStatusCode, statusCode);
                jsonObject.put(requestKeyRequestError, errorMessage);
                jsonObject.put(requestKeyBackoffRequest, countBackoffRequest);
                jsonObject.put(requestKeyConsecutiveBackoffRequest, consecutiveBackoffRequest);
                jsonObject.put(requestKeyMethodUsage, usageToJson());
                jsonObject.put(requestKeyLogCodes, new JSONObject(logCodes));
            }
        } catch (JSONException e) {
            L.d("[HealthCheckCounter] Failed to create the health check snapshot, " + e);
        }
        return jsonObject;
    }

    /**
     * Takes away what a health check the server accepted reported, so what was counted while it was on its way is reported
     * next time. The last failed request and the longest backoff run are cleared only when they did not change since.
     *
     * @param sent the snapshot the delivered health check was built from
     */
    void clearSentValues(@NonNull JSONObject sent) {
        synchronized (this) {
            countLogError = Math.max(0, countLogError - sent.optLong(requestKeyErrorCount, 0));
            countLogWarning = Math.max(0, countLogWarning - sent.optLong(requestKeyWarningCount, 0));
            countBackoffRequest = Math.max(0, countBackoffRequest - sent.optLong(requestKeyBackoffRequest, 0));

            if (consecutiveBackoffRequest == sent.optInt(requestKeyConsecutiveBackoffRequest, -1)) {
                consecutiveBackoffRequest = 0;
            }

            if (statusCode == sent.optInt(requestKeyStatusCode, -2) && errorMessage.equals(sent.optString(requestKeyRequestError, ""))) {
                statusCode = -1;
                errorMessage = "";
            }

            JSONObject sentUsage = sent.optJSONObject(requestKeyMethodUsage);
            if (sentUsage != null) {
                Iterator<String> areas = sentUsage.keys();
                while (areas.hasNext()) {
                    String area = areas.next();
                    JSONObject sentMethods = sentUsage.optJSONObject(area);
                    Map<String, Integer> counted = methodUsage.get(area);
                    if (sentMethods == null || counted == null) {
                        continue;
                    }
                    Iterator<String> methods = sentMethods.keys();
                    while (methods.hasNext()) {
                        String method = methods.next();
                        Integer current = counted.get(method);
                        int left = (current == null ? 0 : current) - sentMethods.optInt(method, 0);
                        if (left > 0) {
                            counted.put(method, left);
                        } else {
                            counted.remove(method);
                        }
                    }
                    if (counted.isEmpty()) {
                        methodUsage.remove(area);
                    }
                }
            }

            JSONObject sentCodes = sent.optJSONObject(requestKeyLogCodes);
            if (sentCodes != null) {
                Iterator<String> codes = sentCodes.keys();
                while (codes.hasNext()) {
                    String code = codes.next();
                    Integer current = logCodes.get(code);
                    int left = (current == null ? 0 : current) - sentCodes.optInt(code, 0);
                    if (left > 0) {
                        logCodes.put(code, left);
                    } else {
                        logCodes.remove(code);
                    }
                }
            }
        }

        saveState();
    }

    /**
     * The "&hc=" request parameter for the given snapshot.
     *
     * @param snapshot the counters the request reports, see snapshot()
     * @return the URL encoded parameter
     */
    @NonNull String createRequestParam(@NonNull JSONObject snapshot) {
        String encodedData = snapshot.toString();

        try {
            encodedData = java.net.URLEncoder.encode(encodedData, "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            // should never happen because Android guarantees UTF-8 support
            L.d("[HealthCheckCounter] encode failed, [" + ex + "]");
        }

        return "&hc=" + encodedData;
    }
}
