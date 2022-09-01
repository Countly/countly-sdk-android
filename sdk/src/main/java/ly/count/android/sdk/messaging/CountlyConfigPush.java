package ly.count.android.sdk.messaging;

import android.app.Application;
import androidx.annotation.NonNull;
import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import ly.count.android.sdk.Countly;

public class CountlyConfigPush implements Serializable {
    Application application;
    Countly.CountlyMessagingMode mode;
    Countly.CountlyMessagingProvider provider;
    Set<String> whiteListIntentClassNames = new HashSet<>();
    Set<String> whiteListIntentPackageNames = new HashSet<>();

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
     * set white list intent class names
     *
     * @param whilteListIntentClassNames
     * @return Returns the same push config object for convenient linking
     */

    public synchronized CountlyConfigPush addWhitelistIntentClassName(@NonNull List<String> whilteListIntentClassNames) {
        this.whiteListIntentClassNames.addAll(whilteListIntentClassNames);
        return this;
    }

    /**
     * set white list intent package names
     *
     * @param whilteListIntentPackageNames
     * @return Returns the same push config object for convenient linking
     */
    public synchronized CountlyConfigPush addWhitelistIntentPackageName(@NonNull List<String> whilteListIntentPackageNames) {
        this.whiteListIntentPackageNames.addAll(whilteListIntentPackageNames);
        return this;
    }
}
