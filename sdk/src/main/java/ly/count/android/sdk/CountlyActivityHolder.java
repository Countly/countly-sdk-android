package ly.count.android.sdk;

import android.app.Activity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.lang.ref.WeakReference;

/**
 * Singleton that holds a WeakReference to the current foreground Activity.
 * Populated by {@link CountlyInitProvider} via ActivityLifecycleCallbacks
 * registered before Application.onCreate(), ensuring the first Activity is never missed.
 */
class CountlyActivityHolder {
    private static final CountlyActivityHolder instance = new CountlyActivityHolder();
    private @Nullable WeakReference<Activity> currentActivity;

    private CountlyActivityHolder() {
    }

    static CountlyActivityHolder getInstance() {
        return instance;
    }

    @Nullable Activity getActivity() {
        if (currentActivity != null) {
            return currentActivity.get();
        }
        return null;
    }

    void setActivity(@NonNull Activity activity) {
        if (currentActivity != null && currentActivity.get() == activity) {
            return;
        }
        currentActivity = new WeakReference<>(activity);
    }

    void clearActivity(@NonNull Activity activity) {
        if (currentActivity != null && currentActivity.get() != activity) {
            return;
        }
        currentActivity = null;
    }
}
