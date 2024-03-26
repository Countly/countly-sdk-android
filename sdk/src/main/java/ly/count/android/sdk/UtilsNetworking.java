package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;

public class UtilsNetworking {
    // http://stackoverflow.com/questions/9655181/convert-from-byte-array-to-hex-string-in-java
    final private static char[] hexArray = "0123456789ABCDEF".toCharArray();

    protected static @NonNull String urlEncodeString(@NonNull String givenValue) {
        assert Utils.isNotNullOrEmpty(givenValue);

        String result = "";

        try {
            result = java.net.URLEncoder.encode(givenValue, "UTF-8");
        } catch (UnsupportedEncodingException ignored) {
            // should never happen because Android guarantees UTF-8 support
        }

        return result;
    }

    protected static @NonNull String encodedArrayBuilder(@NonNull String[] args) {
        assert args != null && args.length > 0;

        StringBuilder encodedUrlBuilder = new StringBuilder();

        encodedUrlBuilder.append("[");

        for (int i = 0; i < args.length; i++) {
            encodedUrlBuilder.append('"').append(args[i]).append('"');
            if (i < args.length - 1) {
                encodedUrlBuilder.append(", ");
            }
        }

        encodedUrlBuilder.append("]");

        return encodedUrlBuilder.toString();
    }

    protected static @NonNull String urlDecodeString(@NonNull String givenValue) {
        assert givenValue != null;

        String decodedResult = "";

        try {
            decodedResult = java.net.URLDecoder.decode(givenValue, "UTF-8");
        } catch (UnsupportedEncodingException ignored) {
            // should never happen because Android guarantees UTF-8 support
        }

        return decodedResult;
    }

    protected static @NonNull String sha256Hash(@NonNull String toHash) {
        assert toHash != null;

        String hash;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = toHash.getBytes("UTF-8");
            digest.update(bytes, 0, bytes.length);
            bytes = digest.digest();

            // This is ~55x faster than looping and String.formating()
            hash = bytesToHex(bytes);
        } catch (Throwable e) {
            hash = "";
            Countly.sharedInstance().L.e("Cannot tamper-protect params", e);
        }
        return hash;
    }

    /**
     * Get hexadecimal string representation of a byte array
     *
     * @param bytes array of bytes to convert
     * @return hex string of the byte array in lower case
     */
    public static @NonNull String bytesToHex(@NonNull byte[] bytes) {
        assert bytes != null && bytes.length > 0;

        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars).toLowerCase();
    }

    /**
     * Utility method for testing validity of a URL.
     */
    @SuppressWarnings("ConstantConditions")
    static boolean isValidURL(@Nullable final String urlStr) {
        boolean validURL = false;
        if (urlStr != null && urlStr.length() > 0) {
            try {
                new URL(urlStr);
                validURL = true;
            } catch (MalformedURLException e) {
                validURL = false;
            }
        }
        return validURL;
    }
}
