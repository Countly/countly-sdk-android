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
        Assert.assertNull(gate.check(navigation, post(null, "app_key=NAV_KEY&crash=%7B%7D")));
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
     * which the server would use because the body wins.
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
     * An unquoted field name is read too, and a part whose name cannot be read makes the whole body
     * untrusted.
     */
    @Test
    public void multipart_unusualNames() {
        String contentType = "multipart/form-data; boundary=\"" + BOUNDARY + "\"";
        String unquoted = "--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=app_key\r\n\r\nMUSIC_KEY\r\n--" + BOUNDARY + "--\r\n";
        Assert.assertNotNull(gate.check(navigation, post(contentType, unquoted)));

        String extended = "--" + BOUNDARY + "\r\nContent-Disposition: form-data; name*=UTF-8''app_key\r\n\r\nMUSIC_KEY\r\n--" + BOUNDARY + "--\r\n";
        Assert.assertEquals("request body has an unsupported format", gate.check(navigation, post(contentType, extended)));

        String nameless = "--" + BOUNDARY + "\r\nContent-Type: text/plain\r\n\r\nMUSIC_KEY\r\n--" + BOUNDARY + "--\r\n";
        Assert.assertEquals("request body has an unsupported format", gate.check(navigation, postWithQuery(contentType, nameless)));
    }

    /**
     * A body the hub cannot read, such as JSON, is refused, because it could carry an app key the hub
     * does not see.
     */
    @Test
    public void unsupportedBodyFormat_isRefused() {
        Assert.assertEquals("request body has an unsupported format", gate.check(navigation, post("application/json", "{\"app_key\":\"MUSIC_KEY\"}")));
    }

    private static HubRequest postWithQuery(String contentType, String body) {
        return new HubRequest("POST", "/i", "app_key=NAV_KEY", Collections.singletonMap("Content-Type", contentType), body.getBytes(UTF_8), 30_000);
    }
}
