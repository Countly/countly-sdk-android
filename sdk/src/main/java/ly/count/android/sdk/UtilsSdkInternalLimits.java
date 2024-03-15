package ly.count.android.sdk;

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
}
