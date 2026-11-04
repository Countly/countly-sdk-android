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
 * the path must end with one of the allowed Countly paths, the body must be within the size limit, and
 * the request must carry an app key, every one of which must be a key the app may send data for.
 * <p>
 * Every occurrence of the app key is checked, in the query and in the body, because the server reads
 * both: checking only one place would let an app send data under another app's key. Only the two body
 * encodings the SDK itself writes are accepted, in exactly the form it writes them; anything the server
 * might parse differently than the hub is refused rather than relayed on a guess.
 * <p>
 * A refusal is final when the request itself can never be accepted, for example because its path or
 * format is not one the hub relays. The SDK then drops it instead of retrying it, so it cannot hold back
 * the requests behind it. A refusal because of the app key is not final: it usually means the hub's
 * list of app keys has not been updated yet, and the request should be kept until it is.
 */
final class RequestGate {
    private static final Set<String> METHODS = new HashSet<>(Arrays.asList("GET", "POST"));
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");
    private static final String FORM_TYPE = "application/x-www-form-urlencoded";
    private static final Pattern MULTIPART_TYPE = Pattern.compile("(?i)multipart/form-data; boundary=([0-9a-f]+)");
    private static final Pattern STRICT_DISPOSITION = Pattern.compile("form-data; name=\"([^\"]*)\"(?:; filename=\"[^\r\n]*\")?");
    private static final String APP_KEY = "app_key";
    private static final String REMAINING_REQUESTS = "rr";
    private static final int MAX_REASON_PATH_LENGTH = 100;

    private final Set<String> allowedPaths;
    private final int maxRequestBytes;

    /**
     * The outcome of checking a request.
     */
    static final class Decision {
        final int status;
        final String refusal;
        final boolean finalRefusal;
        final String path;
        final boolean background;

        /**
         * @param status the HTTP status of a refusal, 0 when the request is relayed
         * @param refusal why the request is refused, or null when it is relayed
         * @param finalRefusal whether the request can never be accepted
         * @param path the allowed path the request is relayed to, or null when refused
         * @param background whether the request comes from the SDK's request queue, which retries it by itself
         */
        private Decision(int status, @Nullable String refusal, boolean finalRefusal, @Nullable String path, boolean background) {
            this.status = status;
            this.refusal = refusal;
            this.finalRefusal = finalRefusal;
            this.path = path;
            this.background = background;
        }

        /**
         * @param path the allowed path the request is relayed to
         * @param background whether the request comes from the SDK's request queue
         * @return a decision to relay the request
         */
        static @NonNull Decision relay(@NonNull String path, boolean background) {
            return new Decision(0, null, false, path, background);
        }

        /**
         * @param status the HTTP status the app receives
         * @param reason why the request is refused, without any request data
         * @param finalRefusal whether the request can never be accepted
         * @return a decision to refuse the request
         */
        static @NonNull Decision refuse(int status, @NonNull String reason, boolean finalRefusal) {
            return new Decision(status, reason, finalRefusal, null, false);
        }
    }

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
     * @return the decision, with the reason of a refusal never containing request data
     */
    @NonNull Decision check(@NonNull HubAllowedApp app, @NonNull HubRequest request) {
        if (!METHODS.contains(request.getMethod())) {
            return Decision.refuse(400, "method " + request.getMethod() + " is not relayed", true);
        }
        String path = canonicalPath(request.getPath());
        if (path == null) {
            String requested = request.getPath();
            return Decision.refuse(400, "path " + (requested.length() > MAX_REASON_PATH_LENGTH ? requested.substring(0, MAX_REASON_PATH_LENGTH) + "..." : requested) + " is not relayed", true);
        }
        if (request.getBodyLength() > maxRequestBytes) {
            return Decision.refuse(413, "request body is larger than " + maxRequestBytes + " bytes", true);
        }

        List<String> appKeys;
        List<String> remainingRequests;
        try {
            appKeys = findValues(request, APP_KEY);
            remainingRequests = findValues(request, REMAINING_REQUESTS);
        } catch (IllegalArgumentException e) {
            return Decision.refuse(400, "request could not be read", true);
        }
        if (appKeys == null || remainingRequests == null) {
            return Decision.refuse(400, "request body has an unsupported format", true);
        }
        if (appKeys.isEmpty()) {
            return Decision.refuse(400, "request has no app_key", true);
        }
        for (String appKey : appKeys) {
            if (!app.appKeys.contains(appKey)) {
                return Decision.refuse(403, "app_key is not allowed for " + app.packageName, false);
            }
        }
        return Decision.relay(path, !remainingRequests.isEmpty());
    }

    /**
     * Maps a requested path to the allowed path it ends with, so that a client whose server URL has a
     * path prefix, such as "https://host/countly", reaches the same endpoints. The hub always sends to
     * its own server URL, so the client's prefix is dropped rather than forwarded.
     *
     * @param requested the path the client requested
     * @return the longest allowed path the requested path ends with, or null when there is none
     */
    @Nullable String canonicalPath(@NonNull String requested) {
        String best = null;
        for (String allowed : allowedPaths) {
            if (requested.endsWith(allowed) && (best == null || allowed.length() > best.length())) {
                best = allowed;
            }
        }
        return best;
    }

    /**
     * Collects every value of a parameter in a request, from the query and from a form or multipart body.
     *
     * @param request the request
     * @param name the parameter name
     * @return the values in the order found, or null when the body is not in one of the two exact
     * encodings the SDK writes
     * @throws IllegalArgumentException if the query or body is malformed
     */
    static @Nullable List<String> findValues(@NonNull HubRequest request, @NonNull String name) {
        List<String> values = new ArrayList<>();
        collectFormValues(request.getQuery(), name, values);

        byte[] body = request.getBody();
        if (body == null || body.length == 0) {
            return values;
        }
        String contentType = request.getHeader("Content-Type");
        String type = contentType == null ? "" : contentType.trim();
        if (FORM_TYPE.equalsIgnoreCase(type)) {
            collectFormValues(new String(body, UTF_8), name, values);
            return values;
        }
        Matcher multipart = MULTIPART_TYPE.matcher(type);
        if (multipart.matches()) {
            if (!collectMultipartValues(body, multipart.group(1), name, values)) {
                return null;
            }
            return values;
        }
        return null;
    }

    /**
     * Collects every app key in a request.
     *
     * @param request the request
     * @return the app keys in the order found, or null when the body has a format the hub does not read
     * @throws IllegalArgumentException if the query or body is malformed
     */
    static @Nullable List<String> findAppKeys(@NonNull HubRequest request) {
        return findValues(request, APP_KEY);
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
     * Collects the values of a field from a multipart/form-data body. The body is read as ISO-8859-1,
     * which maps every byte to one character, so the boundaries are found at the right offsets whatever
     * the encoding of the values; values are turned back into UTF-8 text afterwards.
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
     * Reads the field name of one multipart part. The file name the SDK writes for a profile picture is
     * UTF-8, so after the ISO-8859-1 decoding its bytes can include characters such as U+0085, which a
     * regular expression's "." would not match; the file name is therefore matched as anything up to
     * the end of the line.
     *
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
