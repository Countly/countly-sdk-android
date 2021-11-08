package ly.count.android.sdk;

import android.app.Activity;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ModuleAPM extends ModuleBase {

    final static String[] reservedKeys = new String[] { "response_time", "response_payload_size", "response_code", "request_payload_size", "duration", "slow_rendering_frames", "frozen_frames" };

    Apm apmInterface = null;

    Map<String, Long> codeTraces;

    Map<String, Long> networkTraces;

    //used to determine app start time
    boolean hasFirstOnResumeHappened = false;

    long lastScreenSwitchTime = -1;// timestamp of when the app last changed from foreground to background

    int activitiesOpen = -1;

    boolean useManualAppLoadedTrigger = false;
    long appStartTimestamp;

    boolean manualForegroundBackgroundTriggers = false;
    boolean manualOverrideInForeground = false;//app starts in background

    ModuleAPM(Countly cly, CountlyConfig config) {
        super(cly, config);
        L.v("[ModuleAPM] Initialising");

        codeTraces = new HashMap<>();
        networkTraces = new HashMap<>();

        activitiesOpen = 0;

        useManualAppLoadedTrigger = config.appLoadedManualTrigger;
        if (config.appStartTimestampOverride != null) {
            //if there is a app start time override, use it
            appStartTimestamp = config.appStartTimestampOverride;

            L.d("[ModuleAPM] Using app start timestamp override");
        } else {
            //otherwise use the statically generated timestamp
            appStartTimestamp = Countly.applicationStart;
        }

        if (config.appLoadedManualTrigger) {
            L.d("[ModuleAPM] Using manual app finished loading trigger for app start");
        }

        manualForegroundBackgroundTriggers = config.manualForegroundBackgroundTrigger;
        if (manualForegroundBackgroundTriggers) {
            L.d("[ModuleAPM] Using manual foreground/background triggers");
        }

        apmInterface = new Apm();
    }

    void startTraceInternal(String traceKey) {
        L.d("[ModuleAPM] Calling 'startTraceInternal' with key:[" + traceKey + "]");

        if (traceKey == null || traceKey.isEmpty()) {
            L.e("[ModuleAPM] Provided a invalid trace key");
            return;
        }

        Long currentTimestamp = UtilsTime.currentTimestampMs();
        codeTraces.put(traceKey, currentTimestamp);
    }

    void endTraceInternal(String traceKey, Map<String, Integer> customMetrics) {
        //end time counting as fast as possible
        Long currentTimestamp = UtilsTime.currentTimestampMs();

        L.d("[ModuleAPM] Calling 'endTraceInternal' with key:[" + traceKey + "]");

        if (traceKey == null || traceKey.isEmpty()) {
            L.e("[ModuleAPM] Provided a invalid trace key");
            return;
        }

        if (codeTraces.containsKey(traceKey)) {
            Long startTimestamp = codeTraces.remove(traceKey);

            if (startTimestamp == null) {
                L.e("[ModuleAPM] endTraceInternal, retrieved 'startTimestamp' is null, dropping trace");
            } else {
                Long durationMs = currentTimestamp - startTimestamp;

                if (customMetrics != null) {
                    //custom metrics provided
                    //remove reserved keys
                    removeReservedInvalidKeys(customMetrics);
                }

                String metricString = customMetricsToString(customMetrics);

                traceKey = validateAndModifyTraceKey(traceKey);

                requestQueueProvider.sendAPMCustomTrace(traceKey, durationMs, startTimestamp, currentTimestamp, metricString);
            }
        } else {
            L.w("[ModuleAPM] endTraceInternal, trying to end trace which was not started");
        }
    }

    void cancelTraceInternal(String traceKey) {
        L.d("[ModuleAPM] Calling 'cancelTraceInternal' with key:[" + traceKey + "]");

        if (traceKey == null || traceKey.isEmpty()) {
            L.e("[ModuleAPM] Provided a invalid trace key");
            return;
        }

        if (!codeTraces.containsKey(traceKey)) {
            L.w("[ModuleAPM] no trace with key [" + traceKey + "] found");
            return;
        }

        codeTraces.remove(traceKey);
    }

    void cancelAllTracesInternal() {
        L.d("[ModuleAPM] Calling 'cancelAllTracesInternal'");

        codeTraces.clear();
    }

    static String customMetricsToString(Map<String, Integer> customMetrics) {
        StringBuilder ret = new StringBuilder();

        if (customMetrics == null) {
            return ret.toString();
        }

        for (Map.Entry<String, Integer> entry : customMetrics.entrySet()) {
            String key = entry.getKey();
            Integer value = entry.getValue();

            ret.append(",\"");
            ret.append(key);
            ret.append("\":");
            ret.append(value);
        }

        return ret.toString();
    }

    void removeReservedInvalidKeys(Map<String, Integer> customMetrics) {
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
                L.w("[ModuleAPM] custom metrics can't contain null or empty key/value");
                continue;
            }

            //remove invalid keys
            //regex for valid keys serverside
            // /^[a-zA-Z][a-zA-Z0-9_]*$/
            int keyLength = key.length();

            if (keyLength > 32) {
                //remove key longer than 32 characters
                it.remove();
                L.w("[ModuleAPM] custom metric key can't be longer than 32 characters, skipping entry, [" + key + "]");
                continue;
            }

            if (key.charAt(0) == '$') {
                L.w("[ModuleAPM] custom metric key can't start with '$', it will be removed server side, [" + key + "]");
            }

            if (key.contains(".")) {
                L.w("[ModuleAPM] custom metric key can't contain '.', those will be removed server side, [" + key + "]");
            }
        }
    }

    String validateAndModifyTraceKey(String traceKey) {
        if (traceKey.charAt(0) == '$') {
            L.w("[ModuleAPM] validateAndModifyTraceKey, trace keys can't start with '$', it will be removed server side");
        }

        if (traceKey.length() > 2048) {
            traceKey = traceKey.substring(0, 2047);

            L.w("[ModuleAPM] validateAndModifyTraceKey, trace keys can't be longer than 2048 characters, it will be trimmed down");
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
        L.d("[ModuleAPM] Calling 'startNetworkRequestInternal' with key:[" + networkTraceKey + "]");

        if (networkTraceKey == null || networkTraceKey.isEmpty()) {
            L.e("[ModuleAPM] Provided a invalid trace key");
            return;
        }

        if (uniqueId == null || uniqueId.isEmpty()) {
            L.e("[ModuleAPM] Provided a invalid uniqueId");
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
        long currentTimestamp = UtilsTime.currentTimestampMs();

        L.d("[ModuleAPM] Calling 'endNetworkRequestInternal' with key:[" + networkTraceKey + "]");

        if (networkTraceKey == null || networkTraceKey.isEmpty()) {
            L.e("[ModuleAPM] Provided a invalid trace key");
            return;
        }

        if (uniqueId == null || uniqueId.isEmpty()) {
            L.e("[ModuleAPM] Provided a invalid uniqueId");
            return;
        }

        String internalTraceKey = networkTraceKey + "|" + uniqueId;

        if (networkTraces.containsKey(internalTraceKey)) {
            Long startTimestamp = networkTraces.remove(internalTraceKey);

            if (startTimestamp == null) {
                L.e("[ModuleAPM] endNetworkRequestInternal, retrieved 'startTimestamp' is null");
            } else {
                recordNetworkRequestInternal(networkTraceKey, responseCode, requestPayloadSize, responsePayloadSize, startTimestamp, currentTimestamp);
            }
        } else {
            L.w("[ModuleAPM] endNetworkRequestInternal, trying to end trace which was not started");
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
        L.v("[ModuleAPM] Calling 'recordNetworkRequestInternal' with key:[" + networkTraceKey + "]");

        if (networkTraceKey == null || networkTraceKey.isEmpty()) {
            L.e("[ModuleAPM] Provided a invalid trace key, aborting request");
            return;
        }

        if (!(responseCode >= 100 && responseCode < 600)) {
            L.e("[ModuleAPM] Invalid response code was provided, setting to '0'");
            responseCode = 0;
        }

        if (requestPayloadSize < 0) {
            L.e("[ModuleAPM] Invalid request payload size was provided, setting to '0'");
            requestPayloadSize = 0;
        }

        if (responsePayloadSize < 0) {
            L.e("[ModuleAPM] Invalid response payload size was provided, setting to '0'");
            responsePayloadSize = 0;
        }

        if (startTimestamp > endTimestamp) {
            L.e("[ModuleAPM] End timestamp is smaller than start timestamp, switching values");

            long tmp = startTimestamp;
            startTimestamp = endTimestamp;
            endTimestamp = tmp;
        }

        //validate trace key
        networkTraceKey = validateAndModifyTraceKey(networkTraceKey);

        Long responseTimeMs = endTimestamp - startTimestamp;
        requestQueueProvider.sendAPMNetworkTrace(networkTraceKey, responseTimeMs, responseCode, requestPayloadSize, responsePayloadSize, startTimestamp, endTimestamp);
    }

    void clearNetworkTraces() {
        L.v("[ModuleAPM] Calling 'clearNetworkTraces'");

        networkTraces.clear();
    }

    void recordAppStart(long appLoadedTimestamp) {
        L.d("[ModuleAPM] Calling 'recordAppStart'");
        if (_cly.config_.recordAppStartTime) {
            long durationMs = appLoadedTimestamp - appStartTimestamp;

            if (durationMs <= 0) {
                L.e("[ModuleAPM] Encountered negative app start duration:[" + durationMs + "] dropping app start duration request");
                return;
            }

            requestQueueProvider.sendAPMAppStart(durationMs, appStartTimestamp, appLoadedTimestamp);
        }
    }

    void calculateAppRunningTimes(int previousCount, int newCount) {
        boolean goingToBackground = (previousCount == 1 && newCount == 0);
        boolean goingToForeground = (previousCount == 0 && newCount == 1);

        L.v("[ModuleAPM] calculateAppRunningTimes, toBG[" + goingToBackground + "] toFG[" + goingToForeground + "]");

        doForegroundBackgroundCalculations(goingToBackground, goingToForeground);
    }

    void doForegroundBackgroundCalculations(boolean goingToBackground, boolean goingToForeground) {
        L.d("[ModuleAPM] Calling 'doForegroundBackgroundCalculations', [" + goingToBackground + "] [" + goingToForeground + "]");
        if (goingToBackground || goingToForeground) {

            long currentTimeMs = UtilsTime.currentTimestampMs();

            if (lastScreenSwitchTime != -1) {
                // if it was '-1' then it just started, todo might be a issue with halt where it is only reset on first screen change
                long durationMs = currentTimeMs - lastScreenSwitchTime;

                if (goingToForeground) {
                    // coming from a background mode to the foreground
                    requestQueueProvider.sendAPMScreenTime(false, durationMs, lastScreenSwitchTime, currentTimeMs);
                } else if (goingToBackground) {
                    // going form the foreground to the background
                    requestQueueProvider.sendAPMScreenTime(true, durationMs, lastScreenSwitchTime, currentTimeMs);
                }
            } else {
                L.d("[ModuleAPM] 'doForegroundBackgroundCalculations' last screen switch time was '-1', doing nothing");
            }

            lastScreenSwitchTime = currentTimeMs;
        } else {
            L.d("[ModuleAPM] Calling 'doForegroundBackgroundCalculations', just changing screens, ignoring request");
            // changing screens normally
        }
    }

    void goToForeground() {
        L.d("[ModuleAPM] Calling 'goToForeground'");
        if (manualOverrideInForeground) {
            //if we already are in foreground, do nothing
            return;
        }
        manualOverrideInForeground = true;
        doForegroundBackgroundCalculations(false, true);
    }

    void goToBackground() {
        L.d("[ModuleAPM] Calling 'goToBackground'");
        if (!manualOverrideInForeground) {
            //if we already are in background, do nothing
            return;
        }
        manualOverrideInForeground = false;
        doForegroundBackgroundCalculations(true, false);
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
    void callbackOnActivityResumed(Activity activity) {
        L.d("[Apm] Calling 'callbackOnActivityResumed', [" + activitiesOpen + "] -> [" + (activitiesOpen + 1) + "]");

        long currentTimestamp = System.currentTimeMillis();

        if (!manualForegroundBackgroundTriggers) {
            calculateAppRunningTimes(activitiesOpen, activitiesOpen + 1);
        }
        activitiesOpen++;

        if (!hasFirstOnResumeHappened) {
            hasFirstOnResumeHappened = true;
            if (!useManualAppLoadedTrigger) {
                recordAppStart(currentTimestamp);
            }
        }
    }

    /**
     * used for background / foreground time recording
     *
     * @param activity
     */
    @Override
    void callbackOnActivityStopped(Activity activity) {
        L.d("[Apm] Calling 'callbackOnActivityStopped', [" + activitiesOpen + "] -> [" + (activitiesOpen - 1) + "]");

        if (!manualForegroundBackgroundTriggers) {
            calculateAppRunningTimes(activitiesOpen, activitiesOpen - 1);
        }
        activitiesOpen--;
    }

    public class Apm {
        /**
         * Start a trace of a action you want to track
         *
         * @param traceKey key by which this action is identified
         */
        public void startTrace(String traceKey) {
            synchronized (_cly) {
                L.i("[Apm] Calling 'startTrace' with key:[" + traceKey + "]");

                startTraceInternal(traceKey);
            }
        }

        /**
         * End a trace of a action you want to track
         *
         * @param traceKey key by which this action is identified
         */
        public void endTrace(String traceKey, Map<String, Integer> customMetrics) {
            synchronized (_cly) {
                L.i("[Apm] Calling 'endTrace' with key:[" + traceKey + "]");

                endTraceInternal(traceKey, customMetrics);
            }
        }

        public void cancelTrace(String traceKey) {
            synchronized (_cly) {
                L.i("[Apm] Calling 'cancelTrace' with key:[" + traceKey + "]");

                cancelTraceInternal(traceKey);
            }
        }

        public void cancelAllTraces() {
            synchronized (_cly) {
                L.i("[Apm] Calling 'cancelAllTraces'");

                cancelAllTracesInternal();
                clearNetworkTraces();
            }
        }

        /**
         * Begin the tracking of a network request
         *
         * @param networkTraceKey key that identifies the network trace
         * @param uniqueId this is important in cases where multiple requests in parallel are done
         * for the same trace. This helps to distinguish them.
         */
        public void startNetworkRequest(String networkTraceKey, String uniqueId) {
            synchronized (_cly) {
                L.i("[Apm] Calling 'startNetworkRequest' with key:[" + networkTraceKey + "], uniqueID:[" + uniqueId + "]");

                startNetworkRequestInternal(networkTraceKey, uniqueId);
            }
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
            synchronized (_cly) {
                L.i("[Apm] Calling 'endNetworkRequest' with key:[" + networkTraceKey + "], uniqueID:[" + uniqueId + "]");

                endNetworkRequestInternal(networkTraceKey, uniqueId, responseCode, requestPayloadSize, responsePayloadSize);
            }
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
            synchronized (_cly) {
                L.i("[Apm] Calling 'recordNetworkTrace' with key:[" + networkTraceKey + "]");

                recordNetworkRequestInternal(networkTraceKey, responseCode, requestPayloadSize, responsePayloadSize, requestStartTimestampMs, requestEndTimestampMs);
            }
        }

        /**
         * Manually set that the app is loaded so that the app load duration can be recorded.
         * Should only be used if manual app loading trigger is enabled
         */
        public void setAppIsLoaded() {
            synchronized (_cly) {
                L.i("[Apm] Calling 'setAppIsLoaded'");

                long timestamp = System.currentTimeMillis();

                if (!useManualAppLoadedTrigger) {
                    L.w("[Apm] trying to record that app has finished loading without enabling manual trigger");
                    return;
                }

                recordAppStart(timestamp);
            }
        }

        public void triggerForeground() {
            synchronized (_cly) {
                L.i("[Apm] Calling 'triggerForeground'");

                if (!manualForegroundBackgroundTriggers) {
                    L.w("[Apm] trying to use manual foreground triggers without enabling them");
                    return;
                }

                goToForeground();
            }
        }

        public void triggerBackground() {
            synchronized (_cly) {
                L.i("[Apm] Calling 'triggerBackground'");

                if (!manualForegroundBackgroundTriggers) {
                    L.w("[Apm] trying to use manual background triggers without enabling them");
                    return;
                }

                goToBackground();
            }
        }
    }
}
