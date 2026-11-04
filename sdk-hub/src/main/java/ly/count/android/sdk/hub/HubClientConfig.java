package ly.count.android.sdk.hub;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Tells an app where its hub runs: the package of the host app that holds the network access, and
 * optionally the certificate that package must be signed with.
 */
public final class HubClientConfig {
    final Context context;
    final String hubPackageName;
    String hubSigningCertificateSha256 = null;
    int bindTimeoutMillis = 10_000;

    /**
     * Creates the configuration.
     *
     * @param context any context of the app, its application context is kept
     * @param hubPackageName the package name of the host app that runs the hub service
     */
    public HubClientConfig(@NonNull Context context, @NonNull String hubPackageName) {
        this.context = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        this.hubPackageName = hubPackageName;
    }

    /**
     * Makes the app hand requests only to a hub app signed with the given certificate, so that no
     * other app installed under the hub's package name receives them.
     *
     * @param sha256 the SHA-256 digest of the hub app's signing certificate, as hex, with or without ':' separators
     * @return Returns the same config object for convenient linking
     */
    public synchronized HubClientConfig setHubSigningCertificateSha256(@Nullable String sha256) {
        hubSigningCertificateSha256 = sha256;
        return this;
    }

    /**
     * Sets how long a request waits for the connection to the hub service to be established before it
     * fails and is retried later. The default is 10 seconds.
     *
     * @param bindTimeoutMillis the timeout in milliseconds, values below 1 are ignored
     * @return Returns the same config object for convenient linking
     */
    public synchronized HubClientConfig setBindTimeoutMillis(int bindTimeoutMillis) {
        if (bindTimeoutMillis > 0) {
            this.bindTimeoutMillis = bindTimeoutMillis;
        }
        return this;
    }
}
