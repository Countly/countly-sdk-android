package ly.count.android.sdk;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;

public class UtilsNetworking {
    // http://stackoverflow.com/questions/9655181/convert-from-byte-array-to-hex-string-in-java
    final private static char[] hexArray = "0123456789ABCDEF".toCharArray();

    protected static String urlEncodeString(String givenValue) {
        String result = "";

        try {
            result = java.net.URLEncoder.encode(givenValue, "UTF-8");
        } catch (UnsupportedEncodingException ignored) {
            // should never happen because Android guarantees UTF-8 support
        }

        return result;
    }

    protected static String urlDecodeString(String givenValue) {
        String decodedResult = "";

        try {
            decodedResult = java.net.URLDecoder.decode(givenValue, "UTF-8");
        } catch (UnsupportedEncodingException ignored) {
            // should never happen because Android guarantees UTF-8 support
        }

        return decodedResult;
    }

    protected static String sha256Hash(String toHash) {
        String hash = null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = toHash.getBytes("UTF-8");
            digest.update(bytes, 0, bytes.length);
            bytes = digest.digest();

            // This is ~55x faster than looping and String.formating()
            hash = bytesToHex(bytes);
        } catch (Throwable e) {
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
    public static String bytesToHex(byte[] bytes) {
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
    static boolean isValidURL(final String urlStr) {
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
