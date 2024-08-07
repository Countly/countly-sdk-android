package ly.count.android.sdk;

import androidx.annotation.NonNull;
import java.util.LinkedList;
import java.util.List;

public class BreadcrumbHelper {

    private final @NonNull LinkedList<String> logs = new LinkedList<>();
    private final int maxBreadcrumbs;

    private final @NonNull ModuleLog L;

    protected BreadcrumbHelper(int maxBreadcrumbs, @NonNull ModuleLog L) {
        assert maxBreadcrumbs > 0;
        assert L != null;

        this.maxBreadcrumbs = maxBreadcrumbs;
        this.L = L;
    }

    protected void addBreadcrumb(@NonNull String breadcrumb, int valueSize) {
        assert breadcrumb != null;
        assert !breadcrumb.isEmpty();
        assert valueSize > 0;

        if (breadcrumb == null || breadcrumb.isEmpty()) {
            L.e("[BreadcrumbHelper] addBreadcrumb, Can't add a null or empty crash breadcrumb");
            return;
        }

        String truncatedBreadcrumb = UtilsInternalLimits.truncateValueSize(breadcrumb, valueSize, L, "[BreadcrumbHelper] addBreadcrumb");

        if (logs.size() >= maxBreadcrumbs) {
            L.d("[BreadcrumbHelper] addBreadcrumb, Breadcrumb amount limit exceeded, deleting the oldest one");
            logs.removeFirst();
        }
        logs.add(truncatedBreadcrumb);

        assert logs.size() <= maxBreadcrumbs;
    }

    protected @NonNull List<String> getBreadcrumbs() {
        return logs;
    }

    protected void clearBreadcrumbs() {
        logs.clear();
    }
}
