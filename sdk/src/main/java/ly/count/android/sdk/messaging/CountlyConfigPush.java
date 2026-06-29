package ly.count.android.sdk.messaging;

import android.app.Application;
import androidx.annotation.NonNull;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import ly.count.android.sdk.Countly;

public class CountlyConfigPush {
    Application application;
    Countly.CountlyMessagingProvider provider;
    Set<String> allowedIntentClassNames = new HashSet<>();
    Set<String> allowedIntentPackageNames = new HashSet<>();
    Set<String> allowedIntentSchemes = new HashSet<>();
    boolean useAdditionalIntentRedirectionChecks = false;

    CountlyNotificationButtonURLHandler notificationButtonURLHandler;

    /**
     * @param application
     * @param mode
     * @deprecated use the other constructor
     */
    public CountlyConfigPush(final Application application, Countly.CountlyMessagingMode mode) {
        this.application = application;
    }

    public CountlyConfigPush(final Application application) {
        this.application = application;
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

    /**
     * Enable additional intent redirection checks for notification clicks. When enabled, the
     * intent's target component must match the allowed package and class names exactly (see
     * {@link #setAllowedIntentClassNames(List)} and {@link #setAllowedIntentPackageNames(List)})
     * before the notification action is dispatched. Disabled by default.
     *
     * @return Returns the same push config object for convenient linking
     */
    public synchronized CountlyConfigPush enableAdditionalIntentRedirectionChecks() {
        this.useAdditionalIntentRedirectionChecks = true;
        return this;
    }

    /**
     * Set the URI schemes that notification links are allowed to open via ACTION_VIEW. When a
     * non-empty list is provided, only links whose scheme is in the list are opened. When left
     * empty (the default), any scheme except known-dangerous ones ("file", "content", "javascript",
     * "jar", "data") is allowed, so http(s) and deep links keep working. Matched case-insensitively.
     *
     * @param allowedIntentSchemes the URI schemes permitted for notification links, for example ["https", "myapp"]
     * @return Returns the same push config object for convenient linking
     */
    public synchronized CountlyConfigPush setAllowedIntentSchemes(List<String> allowedIntentSchemes) {
        this.allowedIntentSchemes = new HashSet<>();
        if (allowedIntentSchemes != null) {
            for (String scheme : allowedIntentSchemes) {
                if (scheme != null) {
                    this.allowedIntentSchemes.add(scheme.toLowerCase(Locale.ROOT));
                }
            }
        }
        return this;
    }

    /**
     * set notification button URL handler
     *
     * @param notificationButtonURLHandler for handling notification button click
     * @return Returns the same push config object for convenient linking
     */
    public synchronized CountlyConfigPush setNotificationButtonURLHandler(CountlyNotificationButtonURLHandler notificationButtonURLHandler) {
        this.notificationButtonURLHandler = notificationButtonURLHandler;
        return this;
    }
}
