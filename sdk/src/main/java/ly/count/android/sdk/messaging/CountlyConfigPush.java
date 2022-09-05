package ly.count.android.sdk.messaging;

import android.app.Application;
import androidx.annotation.NonNull;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import ly.count.android.sdk.Countly;

public class CountlyConfigPush {
    Application application;
    Countly.CountlyMessagingMode mode;
    Countly.CountlyMessagingProvider provider;
    Set<String> allowedIntentClassNames = new HashSet<>();
    Set<String> allowedIntentPackageNames = new HashSet<>();

    public CountlyConfigPush(final Application application, Countly.CountlyMessagingMode mode) {
        this.application = application;
        this.mode = mode;
    }

    /**
     * set preferred push provider
     *
     * @param provider
     * @return Returns the same push config object for convenient linking
     */
    public synchronized CountlyConfigPush setProvider(Countly.CountlyMessagingProvider provider) {
        this.provider = provider;
        return this;
    }

    /**
     * set allowed intent class names
     *
     * @param allowedIntentClassNames
     * @return Returns the same push config object for convenient linking
     */

    public synchronized CountlyConfigPush setAllowedIntentClassNames(@NonNull List<String> allowedIntentClassNames) {
        this.allowedIntentClassNames = new HashSet<>(allowedIntentClassNames);
        return this;
    }

    /**
     * set allowed intent package names
     *
     * @param allowedIntentPackageNames
     * @return Returns the same push config object for convenient linking
     */
    public synchronized CountlyConfigPush setAllowedIntentPackageNames(@NonNull List<String> allowedIntentPackageNames) {
        this.allowedIntentPackageNames = new HashSet<>(allowedIntentPackageNames);
        return this;
    }
}
