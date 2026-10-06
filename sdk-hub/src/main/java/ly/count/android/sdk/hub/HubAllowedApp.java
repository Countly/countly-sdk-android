package ly.count.android.sdk.hub;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * An app the hub accepts requests from, and the Countly app keys that app may send data for.
 * <p>
 * The hub identifies callers by the package the system reports for them, not by anything in the
 * request, so an app cannot pass itself off as another one. Requiring the signing certificate as well
 * protects against a different app being installed under an allowed package name.
 */
public final class HubAllowedApp {
    final String packageName;
    final Set<String> appKeys;
    String signingCertificateSha256 = null;

    /**
     * Creates the entry for one app.
     *
     * @param packageName the package name of the app
     * @param appKeys the Countly app keys the app may send data for, at least one
     */
    public HubAllowedApp(@NonNull String packageName, @NonNull String... appKeys) {
        if (appKeys.length == 0) {
            throw new IllegalArgumentException("An allowed app needs at least one app key");
        }
        this.packageName = packageName;
        this.appKeys = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(appKeys)));
    }

    /**
     * Makes the hub accept the app only when it is signed with the given certificate.
     *
     * @param sha256 the SHA-256 digest of the app's signing certificate, as hex, with or without ':' separators
     * @return Returns the same object for convenient linking
     */
    public synchronized HubAllowedApp setSigningCertificateSha256(@Nullable String sha256) {
        signingCertificateSha256 = sha256;
        return this;
    }
}
