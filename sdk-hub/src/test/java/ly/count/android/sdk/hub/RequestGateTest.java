package ly.count.android.sdk.hub;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class RequestGateTest {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String BOUNDARY = "18f3a2b4c5d";

    private final RequestGate gate = new RequestGate(new HashSet<>(HubConfig.DEFAULT_ALLOWED_PATHS), 1024);
    private final HubAllowedApp navigation = new HubAllowedApp("com.example.navigation", "NAV_KEY", "NAV_KEY_2");

    private static HubRequest get(String path, String query) {
        return new HubRequest("GET", path, query, Collections.<String, String>emptyMap(), null, 30_000);
    }

    private static HubRequest post(String contentType, String body) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (contentType != null) {
            headers.put("Content-Type", contentType);
        }
        return new HubRequest("POST", "/i", null, headers, body.getBytes(UTF_8), 30_000);
    }

    private static String multipart(String... nameValuePairs) {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            body.append("--").append(BOUNDARY).append("\r\n")
                .append("Content-Disposition: form-data; name=\"").append(nameValuePairs[i]).append("\"\r\n\r\n")
                .append(nameValuePairs[i + 1]).append("\r\n");
        }
        body.append("--").append(BOUNDARY).append("--\r\n");
        return body.toString();
    }

    /**
     * A request with an allowed method, path and app key passes, for each of the app's keys.
     */
    @Test
    public void allowedRequest_passes() {
        Assert.assertNull(gate.check(navigation, get("/i", "app_key=NAV_KEY&device_id=d&events=%5B%5D")));
        Assert.assertNull(gate.check(navigation, get("/o/sdk", "method=rc&app_key=NAV_KEY_2")));
        Assert.assertNull(gate.check(navigation, post("application/x-www-form-urlencoded", "app_key=NAV_KEY&crash=%7B%7D")));
    }

    /**
     * A POST body with no content type is refused: the SDK always sets one, and without it the hub
     * cannot know how the server would read the body.
     */
    @Test
    public void bodyWithoutContentType_isRefused() {
        Assert.assertEquals("request body has an unsupported format", gate.check(navigation, post(null, "app_key=NAV_KEY&crash=%7B%7D")));
    }

    /**
     * Paths outside the Countly paths, and methods the SDK does not use, are refused.
     */
    @Test
    public void pathAndMethod_mustBeAllowed() {
        Assert.assertNotNull(gate.check(navigation, get("/o/apps/mine", "app_key=NAV_KEY")));
        Assert.assertNotNull(gate.check(navigation, get("/i/../o/users", "app_key=NAV_KEY")));
        Assert.assertNotNull(gate.check(navigation, new HubRequest("PUT", "/i", "app_key=NAV_KEY", Collections.<String, String>emptyMap(), null, 30_000)));
    }

    /**
     * A body above the limit is refused.
     */
    @Test
    public void oversizedBody_isRefused() {
        char[] filler = new char[2048];
        Arrays.fill(filler, 'x');
        Assert.assertNotNull(gate.check(navigation, post("application/x-www-form-urlencoded", "app_key=NAV_KEY&crash=" + new String(filler))));
    }

    /**
     * A request without any app key, or with the key of another app, is refused.
     */
    @Test
    public void appKey_mustBePresentAndAllowed() {
        Assert.assertEquals("request has no app_key", gate.check(navigation, get("/i", "device_id=d")));
        Assert.assertNotNull(gate.check(navigation, get("/i", "app_key=MUSIC_KEY&device_id=d")));
    }

    /**
     * Every app key counts: an allowed key in the query does not cover another app's key in the body,
     * which the server also reads.
     */
    @Test
    public void everyAppKeyOccurrence_mustBeAllowed() {
        HubRequest request = new HubRequest("POST", "/i", "app_key=NAV_KEY",
            Collections.singletonMap("Content-Type", "application/x-www-form-urlencoded"), "app_key=MUSIC_KEY".getBytes(UTF_8), 30_000);
        Assert.assertNotNull(gate.check(navigation, request));
        Assert.assertNotNull(gate.check(navigation, get("/i", "app_key=NAV_KEY&app_key=MUSIC_KEY")));
    }

    /**
     * Parameter names are decoded the way the server decodes them, so an encoded name cannot hide a key.
     */
    @Test
    public void encodedParameterName_isRecognised() {
        Assert.assertNotNull(gate.check(navigation, get("/i", "app_key=NAV_KEY&app%5Fkey=MUSIC_KEY")));
        Assert.assertEquals(Arrays.asList("NAV_KEY", "MUSIC_KEY"), RequestGate.findAppKeys(get("/i", "app_key=NAV_KEY&app%5Fkey=MUSIC_KEY")));
    }

    /**
     * A malformed escape makes the request unreadable, and it is refused.
     */
    @Test
    public void malformedEscape_isRefused() {
        Assert.assertEquals("request could not be read", gate.check(navigation, get("/i", "app_key=NAV%zzKEY")));
    }

    /**
     * Multipart bodies, which the SDK uses for profile pictures, are read part by part.
     */
    @Test
    public void multipart_appKeysAreChecked() {
        String contentType = "multipart/form-data; boundary=" + BOUNDARY;
        Assert.assertNull(gate.check(navigation, post(contentType, multipart("app_key", "NAV_KEY", "device_id", "d", "user_details", "{}"))));
        Assert.assertNotNull(gate.check(navigation, post(contentType, multipart("app_key", "NAV_KEY", "app_key", "MUSIC_KEY"))));
        Assert.assertEquals("request has no app_key", gate.check(navigation, post(contentType, multipart("device_id", "d"))));
    }

    /**
     * A part whose Content-Disposition is not exactly the form the SDK writes makes the whole body
     * untrusted, so it is refused rather than read on a guess.
     */
    @Test
    public void multipart_unusualNames() {
        String contentType = "multipart/form-data; boundary=" + BOUNDARY;
        String unquoted = "--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=app_key\r\n\r\nMUSIC_KEY\r\n--" + BOUNDARY + "--\r\n";
        Assert.assertEquals("request body has an unsupported format", gate.check(navigation, postWithQuery(contentType, unquoted)));

        String extended = "--" + BOUNDARY + "\r\nContent-Disposition: form-data; name*=UTF-8''app_key\r\n\r\nMUSIC_KEY\r\n--" + BOUNDARY + "--\r\n";
        Assert.assertEquals("request body has an unsupported format", gate.check(navigation, postWithQuery(contentType, extended)));

        String nameless = "--" + BOUNDARY + "\r\nContent-Type: text/plain\r\n\r\nMUSIC_KEY\r\n--" + BOUNDARY + "--\r\n";
        Assert.assertEquals("request body has an unsupported format", gate.check(navigation, postWithQuery(contentType, nameless)));
    }

    /**
     * A multipart part with a second Content-Disposition, which the hub and the server could read
     * differently, is refused even when the first names an allowed key.
     */
    @Test
    public void multipart_twoDispositionsPerPart_isRefused() {
        String body = "--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"app_key\"\r\n\r\nNAV_KEY\r\n"
            + "--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"x\"\r\nContent-Disposition: form-data; name=\"app_key\"\r\n\r\nMUSIC_KEY\r\n"
            + "--" + BOUNDARY + "--\r\n";
        Assert.assertEquals("request body has an unsupported format", gate.check(navigation, post("multipart/form-data; boundary=" + BOUNDARY, body)));
    }

    /**
     * A boundary that is not the plain hex the SDK writes, such as one holding a space, is refused: the
     * hub and the server could split the body at different points.
     */
    @Test
    public void multipart_oddBoundary_isRefused() {
        String body = "--18ab cd\r\nContent-Disposition: form-data; name=\"app_key\"\r\n\r\nMUSIC_KEY\r\n--18ab cd--\r\n";
        Assert.assertEquals("request body has an unsupported format", gate.check(navigation, postWithQuery("multipart/form-data; boundary=18ab cd", body)));
    }

    /**
     * A body the hub cannot read, such as JSON, is refused, because it could carry an app key the hub
     * does not see.
     */
    @Test
    public void unsupportedBodyFormat_isRefused() {
        Assert.assertEquals("request body has an unsupported format", gate.check(navigation, post("application/json", "{\"app_key\":\"MUSIC_KEY\"}")));
    }

    /**
     * A content type the server could parse as more than one format is refused, because the hub might
     * read a different app key than the server does.
     */
    @Test
    public void ambiguousContentType_isRefused() {
        String body = "{\"app_key\":\"MUSIC_KEY\",\"x\":\"&app_key=NAV_KEY&\"}";
        HubRequest request = new HubRequest("POST", "/i", null,
            Collections.singletonMap("Content-Type", "application/x-www-form-urlencoded; x=json"), body.getBytes(UTF_8), 30_000);
        Assert.assertEquals("request body has an unsupported format", gate.check(navigation, request));
    }

    /**
     * A multipart part that hides a second app key inside another parameter is refused, because the
     * hub cannot read its name cleanly while the server still would.
     */
    @Test
    public void multipart_hiddenAppKey_isRefused() {
        String body = "--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"app_key\"\r\n\r\nNAV_KEY\r\n"
            + "--" + BOUNDARY + "\r\nContent-Disposition: form-data; x=\"; name=app_key\"\r\n\r\nMUSIC_KEY\r\n"
            + "--" + BOUNDARY + "--\r\n";
        Assert.assertEquals("request body has an unsupported format", gate.check(navigation, post("multipart/form-data; boundary=" + BOUNDARY, body)));
    }

    /**
     * The file part the SDK adds for a profile picture, with a filename and its own content type, is
     * read without refusing the body.
     */
    @Test
    public void multipart_fileField_passes() {
        String body = "--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"app_key\"\r\n\r\nNAV_KEY\r\n"
            + "--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"photo.png\"\r\nContent-Type: image/png\r\n\r\n\u0089PNG\r\n"
            + "--" + BOUNDARY + "--\r\n";
        Assert.assertNull(gate.check(navigation, post("multipart/form-data; boundary=" + BOUNDARY, body)));
    }

    /**
     * A content type with any extra parameter, even a harmless charset the SDK never sends, is refused:
     * the hub accepts only the exact content types the SDK writes.
     */
    @Test
    public void urlencodedWithExtraParameter_isRefused() {
        Assert.assertEquals("request body has an unsupported format", gate.check(navigation, post("application/x-www-form-urlencoded; charset=UTF-8", "app_key=NAV_KEY")));
    }

    /**
     * A part value that embeds the closing delimiter does not end the scan early: a later part naming
     * another app's key is still seen and the request refused, which a line-anchored server also reads.
     */
    @Test
    public void multipart_closingDelimiterInsideValue_doesNotHideLaterKey() {
        String body = "--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"app_key\"\r\n\r\nNAV_KEY\r\n"
            + "--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"x\"\r\n\r\nZ--" + BOUNDARY + "--Z\r\n"
            + "--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"app_key\"\r\n\r\nMUSIC_KEY\r\n"
            + "--" + BOUNDARY + "--\r\n";
        Assert.assertEquals("app_key is not allowed for com.example.navigation", gate.check(navigation, post("multipart/form-data; boundary=" + BOUNDARY, body)));
    }

    /**
     * The digest parser that decides whether a pinned app matches accepts a 64 hex-character digest,
     * with or without ':' separators, and rejects anything else.
     */
    @Test
    public void signingCertificateDigest_isParsedStrictly() {
        Assert.assertNotNull(HubSignatures.parseHex("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"));
        Assert.assertNotNull(HubSignatures.parseHex("01:23:45:67:89:ab:cd:ef:01:23:45:67:89:ab:cd:ef:01:23:45:67:89:ab:cd:ef:01:23:45:67:89:ab:cd:ef"));
        Assert.assertNull(HubSignatures.parseHex("not-a-real-digest"));
        Assert.assertNull(HubSignatures.parseHex("0123"));
    }

    private static HubRequest postWithQuery(String contentType, String body) {
        return new HubRequest("POST", "/i", "app_key=NAV_KEY", Collections.singletonMap("Content-Type", contentType), body.getBytes(UTF_8), 30_000);
    }
}
