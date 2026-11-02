package ly.count.android.sdk;

import android.net.TrafficStats;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

public class UtilsNetworking {
    // http://stackoverflow.com/questions/9655181/convert-from-byte-array-to-hex-string-in-java
    final private static char[] hexArray = "0123456789ABCDEF".toCharArray();

    /**
     * Opens a connection for a resource the SDK downloads outside its request queue, such as a push notification
     * image. A URL on the default instance's server goes through that instance's SSL socket factory, so a server that
     * is trusted only through the custom factory or certificate pinning works there as well; any other URL gets a
     * plain connection.
     *
     * @param url the resource
     * @return the connection, not connected yet
     * @throws IOException when the connection can not be opened
     */
    public static @NonNull URLConnection openResourceConnection(@NonNull URL url) throws IOException {
        URLConnection connection = url.openConnection();
        if (connection instanceof HttpsURLConnection) {
            SSLSocketFactory factory = Countly.sharedInstance().sslSocketFactoryForHost(url.getHost());
            if (factory != null) {
                ((HttpsURLConnection) connection).setSSLSocketFactory(factory);
            }
        }
        return connection;
    }

    /**
     * Tag the SDK's sockets are attributed to in network usage statistics. Tagged sockets are also
     * not reported by StrictMode's untagged socket detection.
     */
    static final int SOCKET_TAG = 0x434C59;

    /**
     * Tags the sockets the calling thread opens from now on with {@link #SOCKET_TAG}. Pass the
     * returned value to {@link #restoreSocketTag(int)} once the network work is done, since the
     * thread can belong to a pool shared with the app.
     *
     * @return the tag the thread had before
     */
    static int tagSocketsOfThread() {
        int previousTag = TrafficStats.getThreadStatsTag();
        TrafficStats.setThreadStatsTag(SOCKET_TAG);
        return previousTag;
    }

    /**
     * Gives the calling thread back the socket tag it had before {@link #tagSocketsOfThread()}.
     *
     * @param previousTag the value {@link #tagSocketsOfThread()} returned
     */
    static void restoreSocketTag(int previousTag) {
        TrafficStats.setThreadStatsTag(previousTag);
    }

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

    protected static @NonNull String sha256Hash(@NonNull String toHash, @NonNull ModuleLog L) {
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
            L.e("Cannot tamper-protect params", e);
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
