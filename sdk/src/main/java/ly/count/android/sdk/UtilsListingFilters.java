package ly.count.android.sdk;

import androidx.annotation.NonNull;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class UtilsListingFilters {

    private UtilsListingFilters() {
    }

    static boolean applyEventFilter(@NonNull String eventName, @NonNull ConfigurationProvider configProvider) {
        return applyListFilter(eventName, configProvider.getEventFilterSet(), configProvider.getFilterIsWhitelist());
    }

    static boolean applyUserPropertyFilter(@NonNull String propertyName, @NonNull ConfigurationProvider configProvider) {
        return applyListFilter(propertyName, configProvider.getUserPropertyFilterSet(), configProvider.getFilterIsWhitelist());
    }

    static void applySegmentationFilter(@NonNull Map<String, Object> segmentation, @NonNull ConfigurationProvider configProvider, @NonNull ModuleLog L) {
        if (segmentation.isEmpty()) {
            return;
        }
        applyMapFilter(segmentation, configProvider.getSegmentationFilterSet(), configProvider.getFilterIsWhitelist(), L);
    }

    static void applyEventSegmentationFilter(@NonNull String eventName, @NonNull Map<String, Object> segmentation,
        @NonNull ConfigurationProvider configProvider, @NonNull ModuleLog L) {
        if (segmentation.isEmpty() || configProvider.getEventSegmentationFilterMap().isEmpty()) {
            return;
        }

        Set<String> segmentationSet = configProvider.getEventSegmentationFilterMap().get(eventName);
        if (segmentationSet == null || segmentationSet.isEmpty()) {
            // No rules defined for this event so allow everything
            return;
        }
        applyMapFilter(segmentation, segmentationSet, configProvider.getFilterIsWhitelist(), L);
    }

    private static void applyMapFilter(@NonNull Map<String, Object> map, @NonNull Set<String> filterSet, boolean isWhitelist, @NonNull ModuleLog L) {
        if (filterSet.isEmpty()) {
            // No rules defined so allow everything
            return;
        }

        Iterator<Map.Entry<String, Object>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Object> entry = iterator.next();
            String key = entry.getKey();

            boolean contains = filterSet.contains(key);

            // Whitelist: remove if NOT in list
            // Blacklist: remove if IN list
            if ((isWhitelist && !contains) || (!isWhitelist && contains)) {
                iterator.remove();
                L.d("[UtilsListingFilters] applyMapFilter, removed key: " + key + (isWhitelist ? "not in whitelist" : "blacklisted"));
            }
        }
    }

    private static boolean applyListFilter(String item, @NonNull Set<String> filterSet, boolean isWhitelist) {
        if (filterSet.isEmpty()) {
            // No rules defined so allow everything
            return true;
        }
        return isWhitelist == filterSet.contains(item);
    }
}
