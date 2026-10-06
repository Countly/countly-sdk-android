package ly.count.android.sdk.hub;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decides whether the hub relays a request of an allowed app: the method must be one the SDK uses,
 * the path one of the allowed Countly paths, the body within the size limit, and every app key in the
 * request one the app may send data for.
 * <p>
 * Every occurrence of the app key is checked, in the query and in the body, because the server reads
 * both: checking only one place would let an app send data under another app's key. Only the two body
 * encodings the SDK itself writes are accepted, in exactly the form it writes them; anything the server
 * might parse differently than the hub is refused rather than relayed on a guess.
 */
final class RequestGate {
    private static final Set<String> METHODS = new HashSet<>(Arrays.asList("GET", "POST", "HEAD"));
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");
    private static final String FORM_TYPE = "application/x-www-form-urlencoded";
    private static final Pattern MULTIPART_TYPE = Pattern.compile("(?i)multipart/form-data; boundary=([0-9a-f]+)");
    private static final Pattern STRICT_DISPOSITION = Pattern.compile("form-data; name=\"([^\"]*)\"(?:; filename=\".*\")?");
    private static final int MAX_REASON_PATH_LENGTH = 100;

    private final Set<String> allowedPaths;
    private final int maxRequestBytes;

    /**
     * @param allowedPaths the server paths requests may go to
     * @param maxRequestBytes the largest request body accepted
     */
    RequestGate(@NonNull Set<String> allowedPaths, int maxRequestBytes) {
        this.allowedPaths = new LinkedHashSet<>(allowedPaths);
        this.maxRequestBytes = maxRequestBytes;
    }

    /**
     * Checks a request of an allowed app.
     *
     * @param app the app the request came from
     * @param request the request
     * @return null when the request may be relayed, otherwise why it may not, without any request data
     */
    @Nullable String check(@NonNull HubAllowedApp app, @NonNull HubRequest request) {
        if (!METHODS.contains(request.getMethod())) {
            return "method " + request.getMethod() + " is not relayed";
        }
        if (!allowedPaths.contains(request.getPath())) {
            String path = request.getPath();
            return "path " + (path.length() > MAX_REASON_PATH_LENGTH ? path.substring(0, MAX_REASON_PATH_LENGTH) + "..." : path) + " is not relayed";
        }
        if (request.getBodyLength() > maxRequestBytes) {
            return "request body is larger than " + maxRequestBytes + " bytes";
        }

        List<String> appKeys;
        try {
            appKeys = findAppKeys(request);
        } catch (IllegalArgumentException e) {
            return "request could not be read";
        }
        if (appKeys == null) {
            return "request body has an unsupported format";
        }
        if (appKeys.isEmpty()) {
            return "request has no app_key";
        }
        for (String appKey : appKeys) {
            if (!app.appKeys.contains(appKey)) {
                return "app_key is not allowed for " + app.packageName;
            }
        }
        return null;
    }

    /**
     * Collects every app key in a request, from the query and from the body when the body is in one of
     * the two exact encodings the SDK writes.
     *
     * @param request the request
     * @return the app keys in the order found, or null when the body is not in one of those exact encodings
     * @throws IllegalArgumentException if the query or body is malformed
     */
    static @Nullable List<String> findAppKeys(@NonNull HubRequest request) {
        List<String> appKeys = new ArrayList<>();
        collectFormValues(request.getQuery(), "app_key", appKeys);

        byte[] body = request.getBody();
        if (body == null || body.length == 0) {
            return appKeys;
        }
        String contentType = request.getHeader("Content-Type");
        String type = contentType == null ? "" : contentType.trim();
        if (FORM_TYPE.equalsIgnoreCase(type)) {
            collectFormValues(new String(body, UTF_8), "app_key", appKeys);
            return appKeys;
        }
        Matcher multipart = MULTIPART_TYPE.matcher(type);
        if (multipart.matches()) {
            if (!collectMultipartValues(body, multipart.group(1), "app_key", appKeys)) {
                return null;
            }
            return appKeys;
        }
        return null;
    }

    /**
     * Collects the values of a parameter from form encoded text, decoding names and values the way the
     * server does.
     *
     * @param form the form encoded text, or null
     * @param name the parameter name
     * @param out receives the values
     * @throws IllegalArgumentException if the text contains a malformed escape
     */
    static void collectFormValues(@Nullable String form, @NonNull String name, @NonNull List<String> out) {
        if (form == null || form.isEmpty()) {
            return;
        }
        for (String pair : form.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int separator = pair.indexOf('=');
            String pairName = decode(separator < 0 ? pair : pair.substring(0, separator));
            if (name.equals(pairName)) {
                out.add(separator < 0 ? "" : decode(pair.substring(separator + 1)));
            }
        }
    }

    /**
     * Collects the values of a field from a multipart/form-data body.
     *
     * @param body the body
     * @param boundary the boundary from the content type
     * @param name the field name
     * @param out receives the values
     * @return false when a part has no field name the hub can read, in which case the body cannot be trusted to hold no other value
     */
    static boolean collectMultipartValues(@NonNull byte[] body, @NonNull String boundary, @NonNull String name, @NonNull List<String> out) {
        // A leading CRLF is prepended so the first boundary is anchored the same way as the rest; only a
        // boundary at the start of a line is a delimiter, so a "--boundary--" inside a value is not one
        String text = "\r\n" + new String(body, ISO_8859_1);
        String delimiter = "\r\n--" + boundary;
        int index = text.indexOf(delimiter);
        while (index >= 0) {
            int partStart = index + delimiter.length();
            if (text.startsWith("--", partStart)) {
                break;
            }
            int next = text.indexOf(delimiter, partStart);
            String part = next < 0 ? text.substring(partStart) : text.substring(partStart, next);
            int headersEnd = part.indexOf("\r\n\r\n");
            if (headersEnd < 0) {
                return false;
            }
            String partName = dispositionName(part.substring(0, headersEnd));
            if (partName == null) {
                return false;
            }
            if (name.equals(partName)) {
                String value = part.substring(headersEnd + 4);
                if (value.endsWith("\r\n")) {
                    value = value.substring(0, value.length() - 2);
                }
                out.add(new String(value.getBytes(ISO_8859_1), UTF_8));
            }
            index = next;
        }
        return true;
    }

    /**
     * @param headers the header block of one multipart part
     * @return the field name when the part's Content-Disposition is exactly the form the SDK writes,
     * otherwise null so the whole body is refused rather than read on a guess
     */
    private static @Nullable String dispositionName(@NonNull String headers) {
        String name = null;
        boolean found = false;
        for (String line : headers.split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon < 0 || !line.substring(0, colon).trim().equalsIgnoreCase("Content-Disposition")) {
                continue;
            }
            if (found) {
                // a second Content-Disposition the hub and the server could read differently
                return null;
            }
            found = true;
            Matcher matcher = STRICT_DISPOSITION.matcher(line.substring(colon + 1).trim());
            if (!matcher.matches()) {
                return null;
            }
            name = matcher.group(1);
        }
        return name;
    }

    /**
     * @param text form encoded text
     * @return the decoded text
     * @throws IllegalArgumentException if the text contains a malformed escape
     */
    private static @NonNull String decode(@NonNull String text) {
        try {
            return URLDecoder.decode(text, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
