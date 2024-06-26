package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UtilsInternalLimits {

    private UtilsInternalLimits() {
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
    protected static String truncateKeyLength(@Nullable String key, final int limit, @NonNull ModuleLog L, @NonNull String tag) {
        if (key == null) {
            L.w(tag + ": [UtilsSdkInternalLimits] truncateKeyLength, key is null, returning");
            return key;
        }

        if (key.isEmpty()) {
            L.w(tag + ": [UtilsSdkInternalLimits] truncateKeyLength, key is empty, returning");
            return key;
        }

        if (key.length() > limit) {
            String truncatedKey = key.substring(0, limit);
            L.w(tag + ": [UtilsSdkInternalLimits] truncateKeyLength, Key length exceeds limit of " + limit + " characters. Truncating key to " + limit + " characters. Truncated to " + truncatedKey);
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
    protected static <T> void truncateSegmentationKeys(@Nullable Map<String, T> map, final int limit, @NonNull ModuleLog L, @NonNull String tag) {
        if (map == null) {
            L.w(tag + ": [UtilsSdkInternalLimits] truncateMapKeys, map is null, returning");
            return;
        }

        if (map.isEmpty()) {
            L.w(tag + ": [UtilsSdkInternalLimits] truncateMapKeys, map is empty, returning");
            return;
        }

        L.w(tag + ": [UtilsSdkInternalLimits] truncateMapKeys, map:[" + map + "]");
        // Replacing keys in a map is not safe, so we create a new map and put them after
        Map<String, T> gonnaReplace = new ConcurrentHashMap<>();
        List<String> gonnaRemove = new ArrayList<>();

        for (Map.Entry<String, T> entry : map.entrySet()) {
            String truncatedKey = truncateKeyLength(entry.getKey(), limit, L, tag);
            if (!truncatedKey.equals(entry.getKey())) {
                // add truncated key
                gonnaReplace.put(truncatedKey, entry.getValue());
                // remove not truncated key
                gonnaRemove.add(entry.getKey());
            }
        }

        for (String key : gonnaRemove) {
            map.remove(key);
        }

        map.putAll(gonnaReplace);
    }

    /**
     * Checks and transforms the provided Object if it does not
     * comply with the key count limit.
     *
     * @param maxCount Int @NonNull - max number of keys allowed
     * @param L ModuleLog @NonNull - Logger function
     * @param messagePrefix String @NonNull - name of the module this function was called
     * @param segmentation Map<String, Object> @Nullable- segmentation that will be checked
     */
    static void truncateSegmentationValues(@Nullable final Map<String, Object> segmentation, final int maxCount, @NonNull final String messagePrefix, final @NonNull ModuleLog L) {
        if (segmentation == null) {
            return;
        }

        Iterator<Map.Entry<String, Object>> iterator = segmentation.entrySet().iterator();
        while (iterator.hasNext()) {
            if (segmentation.size() > maxCount) {
                Map.Entry<String, Object> value = iterator.next();
                String key = value.getKey();
                L.w(messagePrefix + ", Value exceeded the maximum segmentation count key:[" + key + "]");
                iterator.remove();
            } else {
                break;
            }
        }
    }

    /**
     * Used to remove reserved keys from segmentation map
     *
     * @param segmentation
     * @param reservedKeys
     * @param messagePrefix
     * @param L
     */
    static void removeReservedKeysFromSegmentation(@Nullable Map<String, Object> segmentation, @NonNull String[] reservedKeys, @NonNull String messagePrefix, @NonNull ModuleLog L) {
        if (segmentation == null) {
            return;
        }

        for (String rKey : reservedKeys) {
            if (segmentation.containsKey(rKey)) {
                L.w(messagePrefix + " provided segmentation contains protected key [" + rKey + "]");
                segmentation.remove(rKey);
            }
        }
    }

    /**
     * Removes unsupported data types
     *
     * @param data
     * @return returns true if any entry had been removed
     */
    static boolean removeUnsupportedDataTypes(Map<String, Object> data) {
        if (data == null) {
            return false;
        }

        boolean removed = false;

        for (Iterator<Map.Entry<String, Object>> it = data.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Object> entry = it.next();
            String key = entry.getKey();
            Object value = entry.getValue();

            if (key == null || key.isEmpty() || !(isSupportedDataType(value))) {
                //found unsupported data type or null key or value, removing
                it.remove();
                removed = true;
            }
        }

        if (removed) {
            Countly.sharedInstance().L.w("[Utils] Unsupported data types were removed from provided segmentation");
        }

        return removed;
    }

    static boolean isSupportedDataType(Object value) {
        return value instanceof String || value instanceof Integer || value instanceof Double || value instanceof Boolean || value instanceof Float;
    }
}
