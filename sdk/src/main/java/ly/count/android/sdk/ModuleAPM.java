package ly.count.android.sdk;

import android.app.Activity;
import android.util.Log;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModuleAPM extends ModuleBase {

    final static String[] reservedKeys = new String[] { "response_time", "response_payload_size", "response_code", "request_payload_size", "duration", "slow_rendering_frames", "frozen_frames" };

    Apm apmInterface = null;

    Map<String, Long> codeTraces;

    Map<String, Long> networkTraces;

    //used to determine app start time
    boolean hasFirstOnResumeHappened = false;

    long firstOnResumeTimeMs = -1;// used for determining app start duration
    long lastScreenSwitchTime = -1;// timestamp of when the app last changed from foreground to background

    int activitiesOpen = -1;

    ModuleAPM(Countly cly, CountlyConfig config) {
        super(cly);

        if (_cly.isLoggingEnabled()) {
            Log.v(Countly.TAG, "[ModuleAPM] Initialising");
        }

        codeTraces = new HashMap<>();
        networkTraces = new HashMap<>();

        activitiesOpen = 0;

        apmInterface = new Apm();
    }

    void startTraceInternal(String traceKey) {
        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleAPM] Calling 'startTraceInternal' with key:[" + traceKey + "]");
        }

        if (traceKey == null || traceKey.isEmpty()) {
            if (_cly.isLoggingEnabled()) {
                Log.e(Countly.TAG, "[ModuleAPM] Provided a invalid trace key");
            }
            return;
        }

        Long currentTimestamp = UtilsTime.currentTimestampMs();
        codeTraces.put(traceKey, currentTimestamp);
    }

    void endTraceInternal(String traceKey, Map<String, Integer> customMetrics) {
        //end time counting as fast as possible
        Long currentTimestamp = UtilsTime.currentTimestampMs();

        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleAPM] Calling 'endTraceInternal' with key:[" + traceKey + "]");
        }

        if (traceKey == null || traceKey.isEmpty()) {
            if (_cly.isLoggingEnabled()) {
                Log.e(Countly.TAG, "[ModuleAPM] Provided a invalid trace key");
            }
            return;
        }

        if (codeTraces.containsKey(traceKey)) {
            Long startTimestamp = codeTraces.remove(traceKey);

            if (startTimestamp == null) {
                if (_cly.isLoggingEnabled()) {
                    Log.e(Countly.TAG, "[ModuleAPM] endTraceInternal, retrieved 'startTimestamp' is null, dropping trace");
                }
            } else {
                Long durationMs = currentTimestamp - startTimestamp;

                if (customMetrics != null) {
                    //custom metrics provided
                    //remove reserved keys
                    removeReservedInvalidKeys(customMetrics);
                }

                String metricString = customMetricsToString(customMetrics);

                traceKey = validateAndModifyTraceKey(traceKey);

                _cly.connectionQueue_.sendAPMCustomTrace(traceKey, durationMs, startTimestamp, currentTimestamp, metricString);
            }
        } else {
            if (_cly.isLoggingEnabled()) {
                Log.w(Countly.TAG, "[ModuleAPM] endTraceInternal, trying to end trace which was not started");
            }
        }
    }

    void cancelTraceInternal(String traceKey) {
        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleAPM] Calling 'cancelTraceInternal' with key:[" + traceKey + "]");
        }

        if (traceKey == null || traceKey.isEmpty()) {
            if (_cly.isLoggingEnabled()) {
                Log.e(Countly.TAG, "[ModuleAPM] Provided a invalid trace key");
            }
            return;
        }

        if(!codeTraces.containsKey(traceKey)) {
            if (_cly.isLoggingEnabled()) {
                Log.w(Countly.TAG, "[ModuleAPM] no trace with key [" + traceKey + "] found");
            }
            return;
        }

        codeTraces.remove(traceKey);
    }

    void cancelAllTracesInternal() {
        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleAPM] Calling 'cancelAllTracesInternal'");
        }

        codeTraces.clear();
    }

    static String customMetricsToString(Map<String, Integer> customMetrics) {
        StringBuilder ret = new StringBuilder();

        if (customMetrics == null) {
            return ret.toString();
        }

        for (Iterator<Map.Entry<String, Integer>> it = customMetrics.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Integer> entry = it.next();
            String key = entry.getKey();
            Integer value = entry.getValue();

            ret.append(",\"");
            ret.append(key);
            ret.append("\":");
            ret.append(value);
        }

        return ret.toString();
    }

    static void removeReservedInvalidKeys(Map<String, Integer> customMetrics) {
        if (customMetrics == null) {
            return;
        }

        //remove reserved keys
        for (String rKey : ModuleAPM.reservedKeys) {
            customMetrics.remove(rKey);
        }

        for (Iterator<Map.Entry<String, Integer>> it = customMetrics.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Integer> entry = it.next();
            String key = entry.getKey();
            Integer value = entry.getValue();

            //remove invalid values
            if (key == null || key.isEmpty() || value == null) {
                it.remove();
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.w(Countly.TAG, "[ModuleAPM] custom metrics can't contain null or empty key/value");
                }
                continue;
            }

            //remove invalid keys
            //regex for valid keys serverside
            // /^[a-zA-Z][a-zA-Z0-9_]*$/
            int keyLength = key.length();

            if (keyLength > 32) {
                //remove key longer than 32 characters
                it.remove();
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.w(Countly.TAG, "[ModuleAPM] custom metric key can't be longer than 32 characters, skipping entry, [" + key + "]");
                }
                continue;
            }

            if (key.charAt(0) == '$') {
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.w(Countly.TAG, "[ModuleAPM] custom metric key can't start with '$', it will be removed server side, [" + key + "]");
                }
            }

            if (key.contains(".")) {
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.w(Countly.TAG, "[ModuleAPM] custom metric key can't contain '.', those will be removed server side, [" + key + "]");
                }
            }
        }
    }

    String validateAndModifyTraceKey(String traceKey) {
        if(traceKey.charAt(0) == '$' && _cly.isLoggingEnabled()) {
            Log.w(Countly.TAG, "[ModuleAPM] validateAndModifyTraceKey, trace keys can't start with '$', it will be removed server side");
        }

        if(traceKey.length() > 2048) {
            traceKey = traceKey.substring(0, 2047);

            if(_cly.isLoggingEnabled()) {
                Log.w(Countly.TAG, "[ModuleAPM] validateAndModifyTraceKey, trace keys can't be longer than 2048 characters, it will be trimmed down");
            }
        }

        return traceKey;
    }

    /**
     * Begin the tracking of a network request
     *
     * @param networkTraceKey key that identifies the network trace
     * @param uniqueId this is important in cases where multiple requests in parallel are done
     * for the same trace. This helps to distinguish them
     */
    void startNetworkRequestInternal(String networkTraceKey, String uniqueId) {
        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleAPM] Calling 'startNetworkRequestInternal' with key:[" + networkTraceKey + "]");
        }

        if (networkTraceKey == null || networkTraceKey.isEmpty()) {
            if (_cly.isLoggingEnabled()) {
                Log.e(Countly.TAG, "[ModuleAPM] Provided a invalid trace key");
            }
            return;
        }

        if (uniqueId == null || uniqueId.isEmpty()) {
            if (_cly.isLoggingEnabled()) {
                Log.e(Countly.TAG, "[ModuleAPM] Provided a invalid uniqueId");
            }
            return;
        }

        String internalTraceKey = networkTraceKey + "|" + uniqueId;
        Long currentTimestamp = UtilsTime.currentTimestampMs();
        networkTraces.put(internalTraceKey, currentTimestamp);
    }

    /**
     * Mark that a network request has ended
     *
     * @param networkTraceKey key that identifies the network trace
     * @param uniqueId this is important in cases where multiple requests in parallel are done
     * for the same trace. This helps to distinguish them.
     * @param responseCode returned response code
     * @param requestPayloadSize sent request payload size in bytes
     * @param responsePayloadSize received response payload size in bytes
     */
    void endNetworkRequestInternal(String networkTraceKey, String uniqueId, int responseCode, int requestPayloadSize, int responsePayloadSize) {
        //end time counting as fast as possible
        Long currentTimestamp = UtilsTime.currentTimestampMs();

        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleAPM] Calling 'endNetworkRequestInternal' with key:[" + networkTraceKey + "]");
        }

        if (networkTraceKey == null || networkTraceKey.isEmpty()) {
            if (_cly.isLoggingEnabled()) {
                Log.e(Countly.TAG, "[ModuleAPM] Provided a invalid trace key");
            }
            return;
        }

        if (uniqueId == null || uniqueId.isEmpty()) {
            if (_cly.isLoggingEnabled()) {
                Log.e(Countly.TAG, "[ModuleAPM] Provided a invalid uniqueId");
            }
            return;
        }

        String internalTraceKey = networkTraceKey + "|" + uniqueId;

        if (networkTraces.containsKey(internalTraceKey)) {
            Long startTimestamp = networkTraces.remove(internalTraceKey);

            if (startTimestamp == null) {
                if (_cly.isLoggingEnabled()) {
                    Log.e(Countly.TAG, "[ModuleAPM] endNetworkRequestInternal, retrieved 'startTimestamp' is null");
                }
            } else {
                recordNetworkRequestInternal(networkTraceKey, responseCode, requestPayloadSize, responsePayloadSize, startTimestamp, currentTimestamp);
            }
        } else {
            if (_cly.isLoggingEnabled()) {
                Log.w(Countly.TAG, "[ModuleAPM] endNetworkRequestInternal, trying to end trace which was not started");
            }
        }
    }

    /**
     * Record network trace
     *
     * @param networkTraceKey key that identifies the network trace
     * @param responseCode returned response code
     * @param requestPayloadSize sent request payload size in bytes
     * @param responsePayloadSize received response payload size in bytes
     * @param startTimestamp timestamp in milliseconds of when the request was started
     * @param endTimestamp timestamp in milliseconds of when the request was ended
     */
    void recordNetworkRequestInternal(String networkTraceKey, int responseCode, int requestPayloadSize, int responsePayloadSize, long startTimestamp, long endTimestamp) {
        if (_cly.isLoggingEnabled()) {
            Log.v(Countly.TAG, "[ModuleAPM] Calling 'recordNetworkRequestInternal' with key:[" + networkTraceKey + "]");
        }

        if (networkTraceKey == null || networkTraceKey.isEmpty()) {
            if (_cly.isLoggingEnabled()) {
                Log.e(Countly.TAG, "[ModuleAPM] Provided a invalid trace key, aborting request");
            }
            return;
        }

        if (!(responseCode >= 100 && responseCode < 600)) {
            if (_cly.isLoggingEnabled()) {
                Log.e(Countly.TAG, "[ModuleAPM] Invalid response code was provided, setting to '0'");
            }
            responseCode = 0;
        }

        if (requestPayloadSize < 0) {
            if (_cly.isLoggingEnabled()) {
                Log.e(Countly.TAG, "[ModuleAPM] Invalid request payload size was provided, setting to '0'");
            }
            requestPayloadSize = 0;
        }

        if (responsePayloadSize < 0) {
            if (_cly.isLoggingEnabled()) {
                Log.e(Countly.TAG, "[ModuleAPM] Invalid response payload size was provided, setting to '0'");
            }
            responsePayloadSize = 0;
        }

        if(startTimestamp > endTimestamp) {
            if (_cly.isLoggingEnabled()) {
                Log.e(Countly.TAG, "[ModuleAPM] End timestamp is smaller than start timestamp, switching values");
            }

            Long tmp = startTimestamp;
            startTimestamp = endTimestamp;
            endTimestamp = tmp;
        }

        //validate trace key
        networkTraceKey = validateAndModifyTraceKey(networkTraceKey);

        Long responseTimeMs = endTimestamp - startTimestamp;
        _cly.connectionQueue_.sendAPMNetworkTrace(networkTraceKey, responseTimeMs, responseCode, requestPayloadSize, responsePayloadSize, startTimestamp, endTimestamp);
    }

    void clearNetworkTraces() {
        if (_cly.isLoggingEnabled()) {
            Log.v(Countly.TAG, "[ModuleAPM] Calling 'clearNetworkTraces'");
        }

        networkTraces.clear();
    }

    void recordAppStart() {
        if (_cly.config_.recordAppStartTime) {
            if (Countly.applicationStart == -1) {
                if (_cly.isLoggingEnabled()) {
                    Log.w(Countly.TAG, "[ModuleAPM] Application onCreate start time is not recorded. Don't forget to call 'applicationOnCreate'");
                    return;
                }
            }

            long durationMs = firstOnResumeTimeMs - Countly.applicationStart;

            _cly.connectionQueue_.sendAPMAppStart(durationMs, Countly.applicationStart, firstOnResumeTimeMs);
        }
    }

    void calculateAppRunningTimes(int previousCount, int newCount) {
        boolean goingToBackground = (previousCount == 1 && newCount == 0);
        boolean goingToForeground = (previousCount == 0 && newCount == 1);

        if (goingToBackground || goingToForeground) {
            long currentTimeMs = UtilsTime.currentTimestampMs();

            if (lastScreenSwitchTime != -1) {
                // if it was '-1' then it just started, todo might be a issue with halt where it is only reset on first screen change
                long durationMs = currentTimeMs - lastScreenSwitchTime;

                if (goingToForeground) {
                    // coming from a background mode to the foreground
                    _cly.connectionQueue_.sendAPMScreenTime(false, durationMs, lastScreenSwitchTime, currentTimeMs);
                } else if (goingToBackground) {
                    // going form the foreground to the background
                    _cly.connectionQueue_.sendAPMScreenTime(true, durationMs, lastScreenSwitchTime, currentTimeMs);
                }
            }

            lastScreenSwitchTime = currentTimeMs;
        } else {
            // changing screens normally
        }
    }

    @Override
    void halt() {
        codeTraces = null;
        networkTraces = null;
    }

    /**
     * used for background / foreground time recording
     *
     * @param activity
     */
    @Override
    void onActivityResumed(Activity activity) {
        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[Apm] Calling 'callbackOnActivityResumed', [" + activitiesOpen + "] -> [" + (activitiesOpen + 1) + "]");
        }

        calculateAppRunningTimes(activitiesOpen, activitiesOpen + 1);
        activitiesOpen++;

        if (!hasFirstOnResumeHappened) {
            hasFirstOnResumeHappened = true;

            firstOnResumeTimeMs = UtilsTime.currentTimestampMs();

            recordAppStart();
        }
    }

    /**
     * used for background / foreground time recording
     *
     * @param activity
     */
    @Override
    void onActivityStopped(Activity activity) {
        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[Apm] Calling 'callbackOnActivityStopped', [" + activitiesOpen + "] -> [" + (activitiesOpen - 1) + "]");
        }

        calculateAppRunningTimes(activitiesOpen, activitiesOpen - 1);
        activitiesOpen--;
    }

    public class Apm {
        /**
         * Start a trace of a action you want to track
         *
         * @param traceKey key by which this action is identified
         */
        public void startTrace(String traceKey) {
            if (_cly.isLoggingEnabled()) {
                Log.i(Countly.TAG, "[Apm] Calling 'startTrace' with key:[" + traceKey + "]");
            }

            startTraceInternal(traceKey);
        }

        /**
         * End a trace of a action you want to track
         *
         * @param traceKey key by which this action is identified
         */
        public void endTrace(String traceKey, Map<String, Integer> customMetrics) {
            if (_cly.isLoggingEnabled()) {
                Log.i(Countly.TAG, "[Apm] Calling 'endTrace' with key:[" + traceKey + "]");
            }

            endTraceInternal(traceKey, customMetrics);
        }

        public void cancelTrace(String traceKey) {
            if (_cly.isLoggingEnabled()) {
                Log.i(Countly.TAG, "[Apm] Calling 'cancelTrace' with key:[" + traceKey + "]");
            }

            cancelTraceInternal(traceKey);
        }

        public void cancelAllTraces() {
            if (_cly.isLoggingEnabled()) {
                Log.i(Countly.TAG, "[Apm] Calling 'cancelAllTraces'");
            }

            cancelAllTracesInternal();
            clearNetworkTraces();
        }

        /**
         * Begin the tracking of a network request
         *
         * @param networkTraceKey key that identifies the network trace
         * @param uniqueId this is important in cases where multiple requests in parallel are done
         * for the same trace. This helps to distinguish them.
         */
        public void startNetworkRequest(String networkTraceKey, String uniqueId) {
            if (_cly.isLoggingEnabled()) {
                Log.i(Countly.TAG, "[Apm] Calling 'startNetworkRequest' with key:[" + networkTraceKey + "], uniqueID:[" + uniqueId + "]");
            }

            startNetworkRequestInternal(networkTraceKey, uniqueId);
        }

        /**
         * Mark that a network request has ended
         *
         * @param networkTraceKey key that identifies the network trace
         * @param uniqueId this is important in cases where multiple requests in parallel are done
         * for the same trace. This helps to distinguish them.
         * @param responseCode returned response code
         * @param requestPayloadSize sent request payload size in bytes
         * @param responsePayloadSize received response payload size in bytes
         */
        public void endNetworkRequest(String networkTraceKey, String uniqueId, int responseCode, int requestPayloadSize, int responsePayloadSize) {
            if (_cly.isLoggingEnabled()) {
                Log.i(Countly.TAG, "[Apm] Calling 'endNetworkRequest' with key:[" + networkTraceKey + "], uniqueID:[" + uniqueId + "]");
            }

            endNetworkRequestInternal(networkTraceKey, uniqueId, responseCode, requestPayloadSize, responsePayloadSize);
        }

        /**
         * Mark that a network request has ended
         *
         * @param networkTraceKey key that identifies the network trace
         * @param responseCode returned response code
         * @param requestPayloadSize sent request payload size in bytes
         * @param responsePayloadSize received response payload size in bytes
         * @param requestStartTimestampMs network request start timestamp in milliseconds
         * @param requestEndTimestampMs network request end timestamp in milliseconds
         */
        public void recordNetworkTrace(String networkTraceKey, int responseCode, int requestPayloadSize, int responsePayloadSize, long requestStartTimestampMs, long requestEndTimestampMs) {
            if (_cly.isLoggingEnabled()) {
                Log.i(Countly.TAG, "[Apm] Calling 'recordNetworkTrace' with key:[" + networkTraceKey + "]");
            }

            recordNetworkRequestInternal(networkTraceKey, responseCode, requestPayloadSize, responsePayloadSize, requestStartTimestampMs, requestEndTimestampMs);
        }
    }
}
