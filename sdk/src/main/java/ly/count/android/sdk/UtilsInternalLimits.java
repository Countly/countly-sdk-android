package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONArray;

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
        return truncateString(key, limit, L, tag + ": [UtilsSdkInternalLimits] truncateKeyLength");
    }

    /**
     * Limits the size of all values in our key-value pairs.
     * "Value" fields include:
     * <pre>
     * - segmentation value in case of strings (for all features)
     * - custom user property string value
     * - user profile named key (username, email, etc) string values. Except the "picture" field, which has a limit of 4096 chars
     * - custom user property modifier string values. For example, for modifiers like "push," "pull," "setOnce", etc.
     * - breadcrumb text
     * - manual feedback widget reporting fields (reported as an event)
     * - rating widget response (reported as an event)
     * </pre>
     *
     * @param value to truncate
     * @param limit to truncate to
     * @param L logger
     * @return truncated key
     */
    protected static String truncateValueSize(@Nullable String value, final int limit, @NonNull ModuleLog L, @NonNull String tag) {
        return truncateString(value, limit, L, tag + ": [UtilsSdkInternalLimits] truncateValueSize");
    }

    private static String truncateString(@Nullable String value, final int limit, @NonNull ModuleLog L, @NonNull String tag) {
        assert limit >= 1;
        assert tag != null;
        assert L != null;

        if (value == null) {
            L.w(tag + ", value is null, returning");
            return value;
        }

        if (value.isEmpty()) {
            L.w(tag + ", value is empty, returning");
            return value;
        }

        assert value != null;

        if (value.length() > limit) {
            String truncatedValue = value.substring(0, limit);
            L.w(tag + ", Value length exceeds limit of " + limit + " characters. Truncating value to " + limit + " characters. Truncated to " + truncatedValue);
            return truncatedValue;
        }
        return value;
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
        assert limit >= 1;
        assert L != null;
        assert tag != null;

        if (map == null) {
            L.w(tag + ": [UtilsSdkInternalLimits] truncateMapKeys, map is null, returning");
            return;
        }

        if (map.isEmpty()) {
            L.w(tag + ": [UtilsSdkInternalLimits] truncateMapKeys, map is empty, returning");
            return;
        }

        assert map != null;

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

    protected static void truncateSegmentationKeysValues(@NonNull Map<String, Object> segmentation, @NonNull ConfigSdkInternalLimits limitsConfig, @NonNull ModuleLog L, @NonNull String tag) {
        assert segmentation != null;
        assert limitsConfig != null;
        assert L != null;
        assert tag != null;

        L.w(tag + ": [UtilsSdkInternalLimits] truncateMapKeys, segmentation:[" + segmentation + "]");
        // Replacing keys in a map is not safe, so we create a new map and put them after
        Iterator<Map.Entry<String, Object>> iterator = segmentation.entrySet().iterator();
        Map<String, Object> gonnaReplace = new ConcurrentHashMap<>();

        while (iterator.hasNext()) {
            Map.Entry<String, Object> entry = iterator.next();
            String truncatedKey = truncateKeyLength(entry.getKey(), limitsConfig.maxKeyLength, L, tag);
            Object value = entry.getValue();

            if (!isSupportedDataType(value)) {
                iterator.remove();
                continue;
            }

            if (value instanceof String) {
                value = truncateValueSize((String) value, limitsConfig.maxValueSize, L, tag);
            }
            if (!truncatedKey.equals(entry.getKey())) {
                iterator.remove(); // Removes the current entry from the original map
                gonnaReplace.put(truncatedKey, value); // Store the new entry to be replaced later
            } else if (value instanceof String && !value.equals(entry.getValue())) {
                segmentation.put(truncatedKey, value); // Update value directly
            }
        }

        segmentation.putAll(gonnaReplace);
    }

    /**
     * Removes unsupported data types and applies following internal limits to the provided segmentation map:
     * - max key length
     * - max value size
     * - max number of keys
     *
     * @param segmentation Map<String, Object> @Nullable - segmentation map to apply limits to
     * @param limitsConfig ConfigSdkInternalLimits @NonNull - limits configuration
     * @param L ModuleLog @NonNull - logger
     * @param tag String @NonNull - tag to use in logs
     */
    protected static void applySdkInternalLimitsToSegmentation(@NonNull Map<String, Object> segmentation, @NonNull ConfigSdkInternalLimits limitsConfig, @NonNull ModuleLog L, @NonNull String tag) {
        assert limitsConfig != null;
        assert L != null;
        assert tag != null;
        assert segmentation != null;

        if (segmentation.isEmpty()) {
            L.w(tag + ": [UtilsSdkInternalLimits] applySdkInternalLimitsToSegmentation, map is empty, returning");
            return;
        }

        truncateSegmentationKeysValues(segmentation, limitsConfig, L, tag);
        truncateSegmentationValues(segmentation, limitsConfig.maxSegmentationValues, tag, L);
    }

    /**
     * Applies the following internal limits to the provided breadcrumbs:
     * - max value size
     * - max number of breadcrumbs
     *
     * @param breadcrumbs List<String> @NonNull - breadcrumbs to apply limits to
     * @param limitsConfig ConfigSdkInternalLimits @NonNull - limits configuration
     * @param L ModuleLog @NonNull - logger
     * @param tag String @NonNull - tag to use in logs
     */
    static void applyInternalLimitsToBreadcrumbs(@NonNull List<String> breadcrumbs, @NonNull ConfigSdkInternalLimits limitsConfig, @NonNull ModuleLog L, @NonNull String tag) {
        assert breadcrumbs != null;
        assert limitsConfig != null;
        assert L != null;
        assert tag != null;

        if (breadcrumbs.isEmpty()) {
            L.w(tag + ": [UtilsSdkInternalLimits] applyInternalLimitsToBreadcrumbs, breadcrumbs is empty, returning");
            return;
        }

        Iterator<String> iterator = breadcrumbs.iterator();
        while (iterator.hasNext()) {
            if (breadcrumbs.size() > limitsConfig.maxBreadcrumbCount) {
                String breadcrumb = iterator.next();
                L.w(tag + ": [UtilsSdkInternalLimits] applyInternalLimitsToBreadcrumbs, breadcrumb:[" + breadcrumb + "]");
                iterator.remove();
            } else {
                break;
            }
        }

        for (int i = 0; i < breadcrumbs.size(); i++) {
            String breadcrumb = breadcrumbs.get(i);
            String truncatedBreadcrumb = truncateValueSize(breadcrumb, limitsConfig.maxValueSize, L, tag);
            if (!truncatedBreadcrumb.equals(breadcrumb)) {
                breadcrumbs.set(i, truncatedBreadcrumb);
            }
        }
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
    static <T> void truncateSegmentationValues(@NonNull final Map<String, T> segmentation, final int maxCount, @NonNull final String messagePrefix, final @NonNull ModuleLog L) {
        assert segmentation != null;
        assert maxCount >= 1;
        assert L != null;
        assert messagePrefix != null;

        Iterator<Map.Entry<String, T>> iterator = segmentation.entrySet().iterator();
        while (iterator.hasNext()) {
            if (segmentation.size() > maxCount) {
                Map.Entry<String, T> value = iterator.next();
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
    static void removeReservedKeysFromSegmentation(@NonNull Map<String, Object> segmentation, @NonNull String[] reservedKeys, @NonNull String messagePrefix, @NonNull ModuleLog L) {
        assert segmentation != null;
        assert reservedKeys != null;
        assert L != null;
        assert messagePrefix != null;

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
    static boolean removeUnsupportedDataTypes(@NonNull Map<String, Object> data) {
        assert data != null;

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

    /**
     * Truncates the provided stack trace to the specified limit per line and returns the truncated stack trace.
     *
     * @param stackTrace the stack trace to truncate
     * @param maxStackTraceLineLength the maximum length of each line in the stack trace
     * @param tag the tag to use in logs
     * @param L the logger
     * @return the truncated stack trace
     */
    protected static String applyInternalLimitsToStackTraces(@NonNull String stackTrace, final int maxStackTraceLineLength, @NonNull String tag, @NonNull ModuleLog L) {
        assert stackTrace != null;
        assert maxStackTraceLineLength >= 1;
        assert tag != null;
        assert L != null;

        StringBuilder sb = new StringBuilder(stackTrace.length());

        String[] stackTraceLines = stackTrace.split("\n");
        for (int i = 0; i < stackTraceLines.length; i++) {
            String truncatedLine = UtilsInternalLimits.truncateString(stackTraceLines[i], maxStackTraceLineLength, L, tag);
            if (i != 0) {
                sb.append('\n');
            }
            sb.append(truncatedLine);
        }

        return sb.toString();
    }

    private static boolean isSupportedDataTypeBasic(@Nullable Object value) {
        return value instanceof String || value instanceof Integer || value instanceof Double || value instanceof Boolean || value instanceof Float || value instanceof Long;
    }

    static boolean isSupportedDataType(@Nullable Object value) {
        if (isSupportedDataTypeBasic(value)) {
            return true;
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
            Set<String> classNames = new HashSet<>();
            // checking for multiple classes because we cannot access generic type of the list
            for (Object element : list) {
                if (!isSupportedDataTypeBasic(element)) {
                    return false;
                }
                classNames.add(element.getClass().getName());
            }
            // if it had multiple classes, it's not supported
            return classNames.size() <= 1;
        } else if (value != null && value.getClass().isArray()) {
            Class<?> componentType = value.getClass().getComponentType();
            return componentType == String.class || componentType == Integer.class || componentType == Double.class || componentType == Boolean.class || componentType == Float.class || componentType == Long.class
                || componentType == int.class || componentType == double.class || componentType == boolean.class || componentType == float.class || componentType == long.class;
        } else if (value instanceof JSONArray) {
            JSONArray jsonArray = (JSONArray) value;
            Set<String> classNames = new HashSet<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                Object element = jsonArray.opt(i);
                if (!isSupportedDataTypeBasic(element)) {
                    return false;
                }
                classNames.add(element.getClass().getName());
            }
            return classNames.size() <= 1;
        }
        return false;
    }
}
