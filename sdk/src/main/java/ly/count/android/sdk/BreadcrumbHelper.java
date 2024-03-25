package ly.count.android.sdk;

import androidx.annotation.NonNull;
import java.util.LinkedList;
import java.util.List;

public class BreadcrumbHelper {

    private final @NonNull LinkedList<String> logs = new LinkedList<>();
    private final int maxBreadcrumbs;

    private final @NonNull ModuleLog L;

    protected BreadcrumbHelper(int maxBreadcrumbs, @NonNull ModuleLog L) {
        this.maxBreadcrumbs = maxBreadcrumbs;
        this.L = L;
    }

    protected void addBreadcrumb(@NonNull String breadcrumb, int valueSize) { // TODO when valuesize limit added delete this from here
        if (breadcrumb == null || breadcrumb.isEmpty()) {
            L.e("[BreadcrumbHelper] addBreadcrumb, Can't add a null or empty crash breadcrumb");
            return;
        }

        if (breadcrumb.length() > valueSize) {
            L.d("[BreadcrumbHelper] addBreadcrumb, Breadcrumb exceeds character limit: [" + breadcrumb.length() + "], reducing it to: [" + valueSize + "]");
            breadcrumb = breadcrumb.substring(0, valueSize);
        }

        if (logs.size() >= maxBreadcrumbs) {
            L.d("[BreadcrumbHelper] addBreadcrumb, Breadcrumb amount limit exceeded, deleting the oldest one");
            logs.removeFirst();
        }
        logs.add(breadcrumb);
    }

    protected @NonNull List<String> getBreadcrumbs() {
        return logs;
    }

    protected void clearBreadcrumbs() {
        logs.clear();
    }
}
