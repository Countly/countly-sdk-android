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
    private static final String MULTIPART_TYPE = "multipart/form-data; boundary=" + BOUNDARY;
    private static final String UNSUPPORTED = "request body has an unsupported format";

    private final RequestGate gate = new RequestGate(new HashSet<>(HubConfig.DEFAULT_ALLOWED_PATHS), 1024);
    private final HubAllowedApp navigation = new HubAllowedApp("com.example.navigation", "NAV_KEY", "NAV_KEY_2");

    private static HubRequest get(String path, String query) {
        return new HubRequest("GET", path, query, Collections.<String, String>emptyMap(), null, 30_000);
    }

    private static HubRequest post(String contentType, String body) {
        return post("/i", contentType, body);
    }

    private static HubRequest post(String path, String contentType, String body) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (contentType != null) {
            headers.put("Content-Type", contentType);
        }
        return new HubRequest("POST", path, null, headers, body.getBytes(UTF_8), 30_000);
    }

    private static HubRequest postWithQuery(String contentType, String body) {
        return new HubRequest("POST", "/i", "app_key=NAV_KEY", Collections.singletonMap("Content-Type", contentType), body.getBytes(UTF_8), 30_000);
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
     * Builds the multipart body the SDK writes for a profile picture: its parameters and a file part
     * carrying the file name, encoded as UTF-8.
     */
    private static String pictureUpload(String fileName) {
        return "--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"app_key\"\r\n\r\nNAV_KEY\r\n"
            + "--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"user_details\"\r\n\r\n{}\r\n"
            + "--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\nContent-Type: image/jpeg\r\nContent-Transfer-Encoding: binary\r\n\r\nÿØÿ\r\n"
            + "--" + BOUNDARY + "--\r\n";
    }

    private static void assertRelayed(RequestGate.Decision decision, String path, boolean background) {
        Assert.assertNull(decision.refusal);
        Assert.assertEquals(0, decision.status);
        Assert.assertEquals(path, decision.path);
        Assert.assertEquals(background, decision.background);
    }

    private static void assertRefused(RequestGate.Decision decision, int status, String reason, boolean finalRefusal) {
        Assert.assertEquals(reason, decision.refusal);
        Assert.assertEquals(status, decision.status);
        Assert.assertEquals(finalRefusal, decision.finalRefusal);
        Assert.assertNull(decision.path);
    }

    /**
     * A request with an allowed method, path and app key is relayed, for each of the app's keys.
     */
    @Test
    public void allowedRequest_isRelayed() {
        assertRelayed(gate.check(navigation, get("/i", "app_key=NAV_KEY&device_id=d&events=%5B%5D")), "/i", false);
        assertRelayed(gate.check(navigation, get("/o/sdk", "method=rc&app_key=NAV_KEY_2")), "/o/sdk", false);
        assertRelayed(gate.check(navigation, post("application/x-www-form-urlencoded", "app_key=NAV_KEY&crash=%7B%7D")), "/i", false);
    }

    /**
     * A request from the SDK's request queue carries the "rr" parameter and is marked as background
     * work, which the hub sends after requests the app is waiting on.
     */
    @Test
    public void queuedRequest_isBackground() {
        assertRelayed(gate.check(navigation, get("/i", "app_key=NAV_KEY&device_id=d&rr=3")), "/i", true);
        assertRelayed(gate.check(navigation, post("application/x-www-form-urlencoded", "app_key=NAV_KEY&crash=%7B%7D&rr=0")), "/i", true);
    }

    /**
     * A server URL with a path prefix makes the SDK request "/prefix/i"; the request is relayed to the
     * allowed path it ends with, the longest one when several configured paths match.
     */
    @Test
    public void pathPrefix_isDropped() {
        assertRelayed(gate.check(navigation, get("/countly/i", "app_key=NAV_KEY")), "/i", false);
        assertRelayed(gate.check(navigation, get("/a/b/o/sdk", "method=rc&app_key=NAV_KEY")), "/o/sdk", false);
        Assert.assertNull(gate.canonicalPath("/o/sdkx"));
        Assert.assertNull(gate.canonicalPath("/oi"));
        RequestGate overlapping = new RequestGate(new HashSet<>(Arrays.asList("/o/sdk", "/o/sdk/custom")), 1024);
        Assert.assertEquals("/o/sdk/custom", overlapping.canonicalPath("/countly/o/sdk/custom"));
        Assert.assertEquals("/o/sdk", overlapping.canonicalPath("/countly/o/sdk"));
    }

    /**
     * A POST body with no content type is refused for good: the SDK always sets one, and without it the
     * hub cannot know how the server would read the body.
     */
    @Test
    public void bodyWithoutContentType_isRefusedForGood() {
        assertRefused(gate.check(navigation, post(null, "app_key=NAV_KEY&crash=%7B%7D")), 400, UNSUPPORTED, true);
    }

    /**
     * Paths outside the Countly paths, and methods the SDK does not use, are refused for good.
     */
    @Test
    public void pathAndMethod_mustBeAllowed() {
        assertRefused(gate.check(navigation, get("/o/apps/mine", "app_key=NAV_KEY")), 400, "path /o/apps/mine is not relayed", true);
        assertRefused(gate.check(navigation, get("/i/../o/users", "app_key=NAV_KEY")), 400, "path /i/../o/users is not relayed", true);
        assertRefused(gate.check(navigation, new HubRequest("PUT", "/i", "app_key=NAV_KEY", Collections.<String, String>emptyMap(), null, 30_000)), 400, "method PUT is not relayed", true);
        assertRefused(gate.check(navigation, new HubRequest("HEAD", "/i", "app_key=NAV_KEY", Collections.<String, String>emptyMap(), null, 30_000)), 400, "method HEAD is not relayed", true);
    }

    /**
     * The reason of a refused path is shortened, so a long path cannot flood the logs or the response.
     */
    @Test
    public void longRefusedPath_isShortenedInTheReason() {
        char[] filler = new char[300];
        Arrays.fill(filler, 'a');
        String path = "/" + new String(filler);
        RequestGate.Decision decision = gate.check(navigation, get(path, "app_key=NAV_KEY"));
        Assert.assertEquals("path " + path.substring(0, 100) + "... is not relayed", decision.refusal);
    }

    /**
     * A body above the limit is refused for good with 413.
     */
    @Test
    public void oversizedBody_isRefusedForGood() {
        char[] filler = new char[2048];
        Arrays.fill(filler, 'x');
        assertRefused(gate.check(navigation, post("application/x-www-form-urlencoded", "app_key=NAV_KEY&crash=" + new String(filler))), 413, "request body is larger than 1024 bytes", true);
    }

    /**
     * A request without any app key is refused for good. A request with the key of another app is
     * refused but not for good: the hub's list of keys may simply not have been updated yet.
     */
    @Test
    public void appKey_mustBePresentAndAllowed() {
        assertRefused(gate.check(navigation, get("/i", "device_id=d")), 400, "request has no app_key", true);
        assertRefused(gate.check(navigation, get("/i", "app_key=MUSIC_KEY&device_id=d")), 403, "app_key is not allowed for com.example.navigation", false);
    }

    /**
     * The hub is meant for apps that show no Countly UI: the requests of content and feedback widgets
     * are not relayed by default, and are refused for good so that the SDK drops them.
     */
    @Test
    public void uiFeatureRequests_areRefusedForGood() {
        assertRefused(gate.check(navigation, get("/o/sdk/content", "app_key=NAV_KEY&resolution=1920x720")), 400, "path /o/sdk/content is not relayed", true);
        assertRefused(gate.check(navigation, get("/o/surveys/nps/widget", "widget_id=6013&shown=1")), 400, "path /o/surveys/nps/widget is not relayed", true);
        assertRefused(gate.check(navigation, post("/o/feedback/widget", "application/x-www-form-urlencoded", "widget_id=6013&app_key=NAV_KEY")), 400, "path /o/feedback/widget is not relayed", true);
    }

    /**
     * A request without an app key is refused for good on every path, also on a path the host app
     * added, so no request reaches the server without naming an app the sender may send data for.
     */
    @Test
    public void requestWithoutAppKey_isRefusedOnEveryPath() {
        RequestGate widened = new RequestGate(new HashSet<>(Arrays.asList("/i", "/o/surveys/nps/widget")), 1024);

        assertRefused(widened.check(navigation, get("/o/surveys/nps/widget", "widget_id=6013&shown=1")), 400, "request has no app_key", true);
        assertRelayed(widened.check(navigation, get("/o/surveys/nps/widget", "widget_id=6013&app_key=NAV_KEY")), "/o/surveys/nps/widget", false);
    }

    /**
     * Every app key counts: an allowed key in the query does not cover another app's key in the body,
     * which the server also reads.
     */
    @Test
    public void everyAppKeyOccurrence_mustBeAllowed() {
        HubRequest request = new HubRequest("POST", "/i", "app_key=NAV_KEY",
            Collections.singletonMap("Content-Type", "application/x-www-form-urlencoded"), "app_key=MUSIC_KEY".getBytes(UTF_8), 30_000);
        Assert.assertEquals(403, gate.check(navigation, request).status);
        Assert.assertEquals(403, gate.check(navigation, get("/i", "app_key=NAV_KEY&app_key=MUSIC_KEY")).status);
    }

    /**
     * Parameter names are decoded the way the server decodes them, so an encoded name cannot hide a key.
     */
    @Test
    public void encodedParameterName_isRecognised() {
        Assert.assertEquals(403, gate.check(navigation, get("/i", "app_key=NAV_KEY&app%5Fkey=MUSIC_KEY")).status);
        Assert.assertEquals(Arrays.asList("NAV_KEY", "MUSIC_KEY"), RequestGate.findAppKeys(get("/i", "app_key=NAV_KEY&app%5Fkey=MUSIC_KEY")));
    }

    /**
     * A malformed escape makes the request unreadable, and it is refused for good.
     */
    @Test
    public void malformedEscape_isRefusedForGood() {
        assertRefused(gate.check(navigation, get("/i", "app_key=NAV%zzKEY")), 400, "request could not be read", true);
    }

    /**
     * Multipart bodies, which the SDK uses for profile pictures, are read part by part.
     */
    @Test
    public void multipart_appKeysAreChecked() {
        assertRelayed(gate.check(navigation, post(MULTIPART_TYPE, multipart("app_key", "NAV_KEY", "device_id", "d", "user_details", "{}"))), "/i", false);
        Assert.assertEquals(403, gate.check(navigation, post(MULTIPART_TYPE, multipart("app_key", "NAV_KEY", "app_key", "MUSIC_KEY"))).status);
        assertRefused(gate.check(navigation, post(MULTIPART_TYPE, multipart("device_id", "d"))), 400, "request has no app_key", true);
    }

    /**
     * A profile picture whose file name is not ASCII is relayed. In UTF-8 these names hold the byte
     * 0x85, which the body scan sees as the character U+0085, a line terminator to regular expressions.
     */
    @Test
    public void multipart_nonAsciiFileName_isRelayed() {
        String[] fileNames = { "zdjęcie_ą.jpg", "Åsa.jpg", "хорошо.jpg", "照片.jpg" };
        for (String fileName : fileNames) {
            Assert.assertTrue(fileName + " has no 0x85 byte in UTF-8", new String(fileName.getBytes(UTF_8), Charset.forName("ISO-8859-1")).indexOf('\u0085') >= 0);
            RequestGate.Decision decision = gate.check(navigation, post(MULTIPART_TYPE, pictureUpload(fileName)));
            Assert.assertNull(fileName + " was refused: " + decision.refusal, decision.refusal);
        }
        Assert.assertNull(gate.check(navigation, post(MULTIPART_TYPE, pictureUpload("photo.jpg"))).refusal);
    }

    /**
     * A file name cannot run past the end of its header line to smuggle in another parameter.
     */
    @Test
    public void multipart_fileNameSpanningLines_isRefused() {
        String body = "--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"app_key\"\r\n\r\nNAV_KEY\r\n"
            + "--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a\r\nb.jpg\"\r\n\r\nx\r\n"
            + "--" + BOUNDARY + "--\r\n";
        assertRefused(gate.check(navigation, post(MULTIPART_TYPE, body)), 400, UNSUPPORTED, true);
    }

    /**
     * A part whose Content-Disposition is not exactly the form the SDK writes makes the whole body
     * untrusted, so it is refused rather than read on a guess.
     */
    @Test
    public void multipart_unusualNames() {
        String unquoted = "--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=app_key\r\n\r\nMUSIC_KEY\r\n--" + BOUNDARY + "--\r\n";
        assertRefused(gate.check(navigation, postWithQuery(MULTIPART_TYPE, unquoted)), 400, UNSUPPORTED, true);

        String extended = "--" + BOUNDARY + "\r\nContent-Disposition: form-data; name*=UTF-8''app_key\r\n\r\nMUSIC_KEY\r\n--" + BOUNDARY + "--\r\n";
        assertRefused(gate.check(navigation, postWithQuery(MULTIPART_TYPE, extended)), 400, UNSUPPORTED, true);

        String nameless = "--" + BOUNDARY + "\r\nContent-Type: text/plain\r\n\r\nMUSIC_KEY\r\n--" + BOUNDARY + "--\r\n";
        assertRefused(gate.check(navigation, postWithQuery(MULTIPART_TYPE, nameless)), 400, UNSUPPORTED, true);
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
        assertRefused(gate.check(navigation, post(MULTIPART_TYPE, body)), 400, UNSUPPORTED, true);
    }

    /**
     * A boundary that is not the plain hex the SDK writes, such as one holding a space, is refused: the
     * hub and the server could split the body at different points.
     */
    @Test
    public void multipart_oddBoundary_isRefused() {
        String body = "--18ab cd\r\nContent-Disposition: form-data; name=\"app_key\"\r\n\r\nMUSIC_KEY\r\n--18ab cd--\r\n";
        assertRefused(gate.check(navigation, postWithQuery("multipart/form-data; boundary=18ab cd", body)), 400, UNSUPPORTED, true);
    }

    /**
     * A body the hub cannot read, such as JSON, is refused, because it could carry an app key the hub
     * does not see.
     */
    @Test
    public void unsupportedBodyFormat_isRefused() {
        assertRefused(gate.check(navigation, post("application/json", "{\"app_key\":\"MUSIC_KEY\"}")), 400, UNSUPPORTED, true);
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
        assertRefused(gate.check(navigation, request), 400, UNSUPPORTED, true);
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
        assertRefused(gate.check(navigation, post(MULTIPART_TYPE, body)), 400, UNSUPPORTED, true);
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
        assertRelayed(gate.check(navigation, post(MULTIPART_TYPE, body)), "/i", false);
    }

    /**
     * A content type with any extra parameter, even a harmless charset the SDK never sends, is refused:
     * the hub accepts only the exact content types the SDK writes.
     */
    @Test
    public void urlencodedWithExtraParameter_isRefused() {
        assertRefused(gate.check(navigation, post("application/x-www-form-urlencoded; charset=UTF-8", "app_key=NAV_KEY")), 400, UNSUPPORTED, true);
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
        assertRefused(gate.check(navigation, post(MULTIPART_TYPE, body)), 403, "app_key is not allowed for com.example.navigation", false);
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
}
