package ly.count.android.sdk.hub;

import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Finds out which allowed app a binder call comes from, from the caller's user id that the system
 * attaches to the call. Nothing the caller sends is trusted for this.
 */
final class CallerVerifier {
    private final PackageManager packageManager;
    private final Map<String, HubAllowedApp> allowedApps;

    /**
     * @param packageManager the package manager
     * @param allowedApps the allowed apps by package name
     */
    CallerVerifier(@NonNull PackageManager packageManager, @NonNull Map<String, HubAllowedApp> allowedApps) {
        this.packageManager = packageManager;
        this.allowedApps = new LinkedHashMap<>(allowedApps);
    }

    /**
     * @param uid the caller's user id
     * @return the allowed app the caller is, or null when none of the caller's packages is allowed or
     * signed as required
     */
    @Nullable HubAllowedApp resolve(int uid) {
        String[] packages = packageManager.getPackagesForUid(uid);
        if (packages == null) {
            return null;
        }
        for (String packageName : packages) {
            HubAllowedApp app = allowedApps.get(packageName);
            if (app == null) {
                continue;
            }
            String certificate = app.signingCertificateSha256;
            if (certificate == null || HubSignatures.isSignedWith(packageManager, packageName, certificate)) {
                return app;
            }
        }
        return null;
    }
}
