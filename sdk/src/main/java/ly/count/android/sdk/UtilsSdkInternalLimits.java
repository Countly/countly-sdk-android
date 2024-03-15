package ly.count.android.sdk;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UtilsSdkInternalLimits {

    private UtilsSdkInternalLimits() {
    }

    /**
     * This function is intended to be used to truncate the length of a key to a certain limit.
     * It is used to ensure that the key length does not exceed the limit set by the SDK.
     * If the key length exceeds the limit, the key is truncated to the limit.
     * If the key length is less than or equal to the limit, the key is returned as is.
     * Used truncate method is substring. from 0 to limit.
     * <pre>
     * Intended to be used for those:
     * - event names
     * - view names
     * - custom trace key name (APM)
     * - custom metric key (APM)
     * - segmentation key (for all features)
     * - custom user property
     * - custom user property keys that are used for property modifiers (mul, push, pull, set, increment, etc)
     * </pre>
     *
     * @param key to truncate
     * @param limit to truncate to
     * @param L logger
     * @return truncated key
     */
    protected static String truncateKeyLength(String key, int limit, ModuleLog L) {
        if (key.length() > limit) {
            String truncatedKey = key.substring(0, limit);
            L.d("[UtilsSdkInternalLimits] truncateKeyLength, Key length exceeds limit of " + limit + " characters. Truncating key to " + limit + " characters. Truncated to " + truncatedKey);
            return truncatedKey;
        }
        return key;
    }

    /**
     * This function is intended to be used with truncating map keys
     * Uses truncateKeyLength to truncate keys in a map to a certain limit.
     *
     * @param map to truncate keys
     * @param limit to truncate keys to
     * @param L logger
     * @param <T> type of map value
     */
    protected static <T> void truncateMapKeys(Map<String, T> map, int limit, ModuleLog L) {
        if (map.isEmpty()) {
            L.d("[UtilsSdkInternalLimits] truncateMapKeys, map is empty, returning");
            return;
        }

        L.d("[UtilsSdkInternalLimits] truncateMapKeys, map:[" + map + "]");
        // Replacing keys in a map is not safe, so we create a new map and put them after
        Map<String, T> gonnaReplace = new ConcurrentHashMap<>();

        for (Map.Entry<String, T> entry : map.entrySet()) {
            String truncatedKey = truncateKeyLength(entry.getKey(), limit, L);
            if (!truncatedKey.equals(entry.getKey())) {
                // add truncated key
                gonnaReplace.put(truncatedKey, entry.getValue());
                // remove not truncated key
                map.remove(entry.getKey());
            }
        }
        map.putAll(gonnaReplace);
    }
}
