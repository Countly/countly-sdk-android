package ly.count.android.sdk;

import androidx.annotation.NonNull;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class UtilsListingFilters {

    private UtilsListingFilters() {
    }

    static boolean applyEventFilter(@NonNull String eventName, @NonNull ConfigurationProvider configProvider) {
        ConfigurationProvider.FilterList<Set<String>> eventFilterList = configProvider.getEventFilterList();
        return applyListFilter(eventName, eventFilterList.filterList, eventFilterList.isWhitelist);
    }

    static boolean applyUserPropertyFilter(@NonNull String propertyName, @NonNull ConfigurationProvider configProvider) {
        ConfigurationProvider.FilterList<Set<String>> userPropertyFilterList = configProvider.getUserPropertyFilterList();
        return applyListFilter(propertyName, userPropertyFilterList.filterList, userPropertyFilterList.isWhitelist);
    }

    static void applySegmentationFilter(@NonNull Map<String, Object> segmentation, @NonNull ConfigurationProvider configProvider, @NonNull ModuleLog L) {
        if (segmentation.isEmpty()) {
            return;
        }

        applyMapFilter(segmentation, configProvider.getSegmentationFilterList().filterList, configProvider.getSegmentationFilterList().isWhitelist, L);
    }

    static void applyEventSegmentationFilter(@NonNull String eventName, @NonNull Map<String, Object> segmentation,
        @NonNull ConfigurationProvider configProvider, @NonNull ModuleLog L) {
        ConfigurationProvider.FilterList<Map<String, Set<String>>> eventSegmentationFilterList = configProvider.getEventSegmentationFilterList();
        if (segmentation.isEmpty() || eventSegmentationFilterList.filterList.isEmpty()) {
            return;
        }

        Set<String> segmentationSet = eventSegmentationFilterList.filterList.get(eventName);
        if (segmentationSet == null || segmentationSet.isEmpty()) {
            // No rules defined for this event so allow everything
            return;
        }
        applyMapFilter(segmentation, segmentationSet, eventSegmentationFilterList.isWhitelist, L);
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
