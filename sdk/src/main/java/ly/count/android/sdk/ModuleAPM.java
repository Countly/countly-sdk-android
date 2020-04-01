package ly.count.android.sdk;

import android.util.Log;
import android.util.TimeUtils;

import java.util.HashMap;
import java.util.Map;

import javax.xml.datatype.Duration;

public class ModuleAPM extends ModuleBase {

    Apm apmInterface = null;

    Map<String, Long> traces;

    ModuleAPM(Countly cly, CountlyConfig config) {
        super(cly);

        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleAPM] Initialising");
        }

        traces = new HashMap<>();

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
        }

        Long currentTimestamp = UtilsTime.currentTimestampMs();
        traces.put(traceKey, currentTimestamp);
    }

    void endTraceInternal(String traceKey) {
        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleAPM] Calling 'endTraceInternal' with key:[" + traceKey + "]");
        }

        if (traceKey == null || traceKey.isEmpty()) {
            if (_cly.isLoggingEnabled()) {
                Log.e(Countly.TAG, "[ModuleAPM] Provided a invalid trace key");
            }
        }

        if(traces.containsKey(traceKey)) {
            Long startTimestamp = traces.get(traceKey);
            Long currentTimestamp = UtilsTime.currentTimestampMs();
            Long durationMs = currentTimestamp - startTimestamp;

            _cly.connectionQueue_.sendAPMCustomTrace(traceKey, durationMs, startTimestamp, currentTimestamp);
        }
    }

    @Override
    public void halt() {
        traces = null;
    }

    public class Apm {
        public void startTrace(String traceKey) {
            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[Apm] Calling 'startTrace' with key:[" + traceKey + "]");
            }

            startTraceInternal(traceKey);
        }

        public void endTrace(String traceKey) {
            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[Apm] Calling 'endTrace' with key:[" + traceKey + "]");
            }

            endTraceInternal(traceKey);
        }
    }
}
