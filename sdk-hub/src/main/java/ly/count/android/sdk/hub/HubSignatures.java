package ly.count.android.sdk.hub;

import android.annotation.SuppressLint;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Checks which certificate an installed package is signed with. Both sides use it: an app to make sure
 * it talks to the real hub, the hub to make sure a caller is the app it claims to be.
 */
final class HubSignatures {
    private HubSignatures() {
    }

    /**
     * Tells whether a package is signed with the certificate of the given SHA-256 digest. On Android 9
     * and newer this follows key rotation, so a package whose signing key was rotated still matches its
     * past certificate.
     *
     * @param packageManager the package manager
     * @param packageName the package to check
     * @param sha256 the SHA-256 digest of the certificate, as hex, with or without ':' separators
     * @return true when the package is installed and signed with that certificate
     */
    @SuppressLint("PackageManagerGetSignatures")
    @SuppressWarnings("deprecation")
    static boolean isSignedWith(@NonNull PackageManager packageManager, @NonNull String packageName, @NonNull String sha256) {
        byte[] expected = parseHex(sha256);
        if (expected == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= 28) {
            return packageManager.hasSigningCertificate(packageName, expected, PackageManager.CERT_INPUT_SHA256);
        }
        try {
            PackageInfo info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
            if (info.signatures == null) {
                return false;
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Signature signature : info.signatures) {
                if (MessageDigest.isEqual(expected, digest.digest(signature.toByteArray()))) {
                    return true;
                }
            }
            return false;
        } catch (PackageManager.NameNotFoundException | NoSuchAlgorithmException e) {
            return false;
        }
    }

    /**
     * @param hex a SHA-256 digest as hex, with or without ':' separators and spaces
     * @return the 32 digest bytes, or null when the text is not such a digest
     */
    static @Nullable byte[] parseHex(@NonNull String hex) {
        String digits = hex.replace(":", "").replace(" ", "").trim();
        if (digits.length() != 64) {
            return null;
        }
        byte[] bytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            int high = Character.digit(digits.charAt(2 * i), 16);
            int low = Character.digit(digits.charAt(2 * i + 1), 16);
            if (high < 0 || low < 0) {
                return null;
            }
            bytes[i] = (byte) ((high << 4) | low);
        }
        return bytes;
    }
}
