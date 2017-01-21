package ly.count.android.sdk.internal;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;

/**
 * Utility class
 */

class Utils {
    static String UTF8 = "UTF-8";

    /**
     * Joins objects with a separator
     * @param objects objects to join
     * @param separator separator to use
     * @return resulting string
     */
    static String join(Collection<Object> objects, String separator) {
        StringBuilder sb = new StringBuilder();
        for (Object object : objects) {
            sb.append(object).append(separator);//todo double check, should the seperator always be appended at the end or only between objects?
        }
        return sb.toString();
    }

    /**
     * URLEncoder wrapper to remove try-catch
     * @param str string to encode
     * @return url-encoded {@code str}
     */
    static String urlencode(String str) {
        try {
            return URLEncoder.encode(str, UTF8);
        } catch (UnsupportedEncodingException e) {
            Log.wtf("No UTF-8 encoding?", e);
            return "";
        }
    }
}
