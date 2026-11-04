package ly.count.android.sdk.hub;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class HubConnectionTest {
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    /**
     * Records the requests it is given and answers with a fixed response or failure.
     */
    private static final class RecordingTransport implements HubTransport {
        final List<HubRequest> requests = new ArrayList<>();
        HubResponse response;
        IOException failure;

        @Override
        public HubResponse exchange(HubRequest request) throws IOException {
            requests.add(request);
            if (failure != null) {
                throw failure;
            }
            return response;
        }
    }

    private static HubResponse response(int status, String body) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Server", "countly");
        return new HubResponse(status, headers, body.getBytes(UTF_8));
    }

    private static String read(InputStream in) throws IOException {
        StringBuilder text = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            text.append((char) c);
        }
        in.close();
        return text.toString();
    }

    /**
     * A GET request reaches the hub with its method, path, query, headers and the combined timeout,
     * and the host of the URL is not part of it.
     */
    @Test
    public void get_handsMethodPathQueryAndHeadersToTheHub() throws IOException {
        RecordingTransport transport = new RecordingTransport();
        transport.response = response(200, "{\"result\":\"Success\"}");
        HubConnection connection = new HubConnection(new URL("https://anything.example/i?app_key=k&device_id=d"), transport);
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(30_000);
        connection.setReadTimeout(30_000);
        connection.addRequestProperty("X-Custom", "v");

        Assert.assertEquals(200, connection.getResponseCode());

        Assert.assertEquals(1, transport.requests.size());
        HubRequest sent = transport.requests.get(0);
        Assert.assertEquals("GET", sent.getMethod());
        Assert.assertEquals("/i", sent.getPath());
        Assert.assertEquals("app_key=k&device_id=d", sent.getQuery());
        Assert.assertEquals("v", sent.getHeader("X-Custom"));
        Assert.assertNull(sent.getBody());
        Assert.assertEquals(60_000, sent.getTimeoutMillis());
    }

    /**
     * A posted body is buffered and only handed to the hub when the response is first needed, which
     * in the SDK is the header loop right after the body was written.
     */
    @Test
    public void post_bodyIsHandedOverWhenTheResponseIsFirstNeeded() throws IOException {
        RecordingTransport transport = new RecordingTransport();
        transport.response = response(200, "{\"result\":\"Success\"}");
        HubConnection connection = new HubConnection(new URL("https://anything.example/i"), transport);
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        OutputStream body = connection.getOutputStream();
        body.write("app_key=k&crash=x".getBytes(UTF_8));
        body.close();

        Assert.assertTrue(transport.requests.isEmpty());
        Assert.assertEquals("Content-Type", connection.getHeaderFieldKey(0));

        Assert.assertEquals(1, transport.requests.size());
        HubRequest sent = transport.requests.get(0);
        Assert.assertEquals("POST", sent.getMethod());
        Assert.assertNull(sent.getQuery());
        Assert.assertEquals("app_key=k&crash=x", new String(sent.getBody(), UTF_8));
        Assert.assertEquals("application/x-www-form-urlencoded", sent.getHeader("content-type"));
    }

    /**
     * The calls the SDK makes in order (header loop, connect, input stream, response code, disconnect)
     * hand the request over exactly once and play the response back.
     */
    @Test
    public void sdkCallSequence_handsTheRequestOverOnce() throws IOException {
        RecordingTransport transport = new RecordingTransport();
        transport.response = response(200, "{\"result\":\"Success\"}");
        HubConnection connection = new HubConnection(new URL("https://anything.example/i?app_key=k"), transport);

        int headerIndex = 0;
        while (connection.getHeaderFieldKey(headerIndex) != null) {
            Assert.assertNotNull(connection.getHeaderField(headerIndex));
            headerIndex++;
        }
        connection.connect();
        String body = read(connection.getInputStream());
        int status = connection.getResponseCode();
        connection.disconnect();

        Assert.assertEquals(2, headerIndex);
        Assert.assertEquals("{\"result\":\"Success\"}", body);
        Assert.assertEquals(200, status);
        Assert.assertEquals(1, transport.requests.size());
    }

    /**
     * An error status makes the input stream throw like a regular connection does, and the body is
     * then available from the error stream.
     */
    @Test
    public void errorStatus_inputStreamThrows_errorStreamHasTheBody() throws IOException {
        RecordingTransport transport = new RecordingTransport();
        transport.response = response(400, "{\"error\":\"bad\"}");
        HubConnection connection = new HubConnection(new URL("https://anything.example/i?app_key=k"), transport);

        Assert.assertNull(connection.getErrorStream());
        try {
            connection.getInputStream();
            Assert.fail("expected an IOException for status 400");
        } catch (IOException expected) {
            Assert.assertFalse(expected instanceof FileNotFoundException);
        }
        Assert.assertEquals("{\"error\":\"bad\"}", read(connection.getErrorStream()));
        Assert.assertEquals(400, connection.getResponseCode());
    }

    /**
     * A 404 throws FileNotFoundException from the input stream, as a regular connection does.
     */
    @Test
    public void notFound_inputStreamThrowsFileNotFound() throws IOException {
        RecordingTransport transport = new RecordingTransport();
        transport.response = response(404, "");
        HubConnection connection = new HubConnection(new URL("https://anything.example/i?app_key=k"), transport);

        try {
            connection.getInputStream();
            Assert.fail("expected a FileNotFoundException for status 404");
        } catch (FileNotFoundException expected) {
            Assert.assertEquals(404, connection.getResponseCode());
        }
    }

    /**
     * A failed hand-over is attempted once, swallowed by the calls that cannot throw, and thrown again
     * by every call that needs the response.
     */
    @Test
    public void failure_isRememberedAndThrownAgain() throws IOException {
        RecordingTransport transport = new RecordingTransport();
        transport.failure = new IOException("hub unavailable");
        HubConnection connection = new HubConnection(new URL("https://anything.example/i?app_key=k"), transport);

        Assert.assertNull(connection.getHeaderFieldKey(0));
        Assert.assertNull(connection.getHeaderField("Content-Type"));
        Assert.assertTrue(connection.getHeaderFields().isEmpty());
        try {
            connection.getResponseCode();
            Assert.fail("expected the hand-over failure");
        } catch (IOException e) {
            Assert.assertSame(transport.failure, e);
        }
        try {
            connection.getInputStream();
            Assert.fail("expected the hand-over failure");
        } catch (IOException e) {
            Assert.assertSame(transport.failure, e);
        }
        Assert.assertNull(connection.getErrorStream());
        Assert.assertEquals(1, transport.requests.size());
    }

    /**
     * Response headers are available by index and by name, case-insensitively.
     */
    @Test
    public void responseHeaders_byIndexAndName() throws IOException {
        RecordingTransport transport = new RecordingTransport();
        transport.response = response(200, "{}");
        HubConnection connection = new HubConnection(new URL("https://anything.example/o/sdk?method=sc"), transport);

        Assert.assertEquals("Content-Type", connection.getHeaderFieldKey(0));
        Assert.assertEquals("application/json", connection.getHeaderField(0));
        Assert.assertEquals("X-Server", connection.getHeaderFieldKey(1));
        Assert.assertEquals("countly", connection.getHeaderField(1));
        Assert.assertNull(connection.getHeaderFieldKey(2));
        Assert.assertNull(connection.getHeaderField(-1));
        Assert.assertEquals("application/json", connection.getHeaderField("content-type"));
        Assert.assertEquals(2, connection.getHeaderFields().size());
    }

    /**
     * A body cannot be written unless output was enabled, nor after the request was handed over.
     */
    @Test
    public void outputStream_guards() throws IOException {
        RecordingTransport transport = new RecordingTransport();
        transport.response = response(200, "{}");
        HubConnection connection = new HubConnection(new URL("https://anything.example/i"), transport);
        try {
            connection.getOutputStream();
            Assert.fail("expected a ProtocolException without doOutput");
        } catch (ProtocolException expected) {
            Assert.assertTrue(transport.requests.isEmpty());
        }

        connection.setDoOutput(true);
        connection.getResponseCode();
        try {
            connection.getOutputStream();
            Assert.fail("expected a ProtocolException after the hand-over");
        } catch (ProtocolException expected) {
            Assert.assertEquals(1, transport.requests.size());
        }
    }

    /**
     * A server URL configured with a trailing slash produces "//i", which is merged to "/i" the way
     * the web server in front of Countly merges it.
     */
    @Test
    public void repeatedSlashes_areMerged() throws IOException {
        RecordingTransport transport = new RecordingTransport();
        transport.response = response(200, "{}");
        HubConnection connection = new HubConnection(new URL("https://anything.example//i?app_key=k"), transport);

        connection.getResponseCode();

        Assert.assertEquals("/i", transport.requests.get(0).getPath());
    }

    /**
     * Without timeouts set, the hub waits a default amount of time instead of forever.
     */
    @Test
    public void noTimeouts_useTheDefaultWait() throws IOException {
        RecordingTransport transport = new RecordingTransport();
        transport.response = response(200, "{}");
        HubConnection connection = new HubConnection(new URL("https://anything.example/i?app_key=k"), transport);

        connection.getResponseCode();

        Assert.assertEquals(60_000, transport.requests.get(0).getTimeoutMillis());
    }

    /**
     * The connection keeps the request headers itself. Before Android 7 the platform's URLConnection
     * discards them, which would hand every request to the hub without its Content-Type; on the JVM the
     * base class keeps them anyway, so the overrides are checked to be declared.
     */
    @Test
    public void requestHeaderMethods_areDeclaredByTheConnection() throws NoSuchMethodException {
        Assert.assertEquals(HubConnection.class, HubConnection.class.getDeclaredMethod("setRequestProperty", String.class, String.class).getDeclaringClass());
        Assert.assertEquals(HubConnection.class, HubConnection.class.getDeclaredMethod("addRequestProperty", String.class, String.class).getDeclaringClass());
        Assert.assertEquals(HubConnection.class, HubConnection.class.getDeclaredMethod("getRequestProperty", String.class).getDeclaringClass());
        Assert.assertEquals(HubConnection.class, HubConnection.class.getDeclaredMethod("getRequestProperties").getDeclaringClass());
    }

    /**
     * Setting a header replaces it whatever the case of its name, adding keeps the earlier values, and
     * reading matches names case-insensitively, returning the last value.
     */
    @Test
    public void requestHeaders_setAddAndRead() throws IOException {
        RecordingTransport transport = new RecordingTransport();
        transport.response = response(200, "{}");
        HubConnection connection = new HubConnection(new URL("https://anything.example/i?app_key=k"), transport);
        connection.setRequestProperty("Content-Type", "text/plain");
        connection.setRequestProperty("content-type", "application/x-www-form-urlencoded");
        connection.addRequestProperty("Accept", "a");
        connection.addRequestProperty("ACCEPT", "b");

        Assert.assertEquals("application/x-www-form-urlencoded", connection.getRequestProperty("CONTENT-TYPE"));
        Assert.assertEquals("b", connection.getRequestProperty("accept"));
        Assert.assertNull(connection.getRequestProperty("X-Missing"));
        Assert.assertNull(connection.getRequestProperty(null));
        Map<String, List<String>> properties = connection.getRequestProperties();
        Assert.assertEquals(2, properties.size());
        Assert.assertEquals(Collections.singletonList("application/x-www-form-urlencoded"), properties.get("content-type"));
        Assert.assertEquals(Arrays.asList("a", "b"), properties.get("Accept"));

        connection.getResponseCode();

        HubRequest sent = transport.requests.get(0);
        Assert.assertEquals(2, sent.getHeaders().size());
        Assert.assertEquals("application/x-www-form-urlencoded", sent.getHeader("Content-Type"));
        Assert.assertEquals("a, b", sent.getHeader("Accept"));
    }

    /**
     * The headers cannot be changed or read once the request was handed over, and a header needs a
     * name, as on a regular connection.
     */
    @Test
    public void requestHeaders_guards() throws IOException {
        RecordingTransport transport = new RecordingTransport();
        transport.response = response(200, "{}");
        HubConnection connection = new HubConnection(new URL("https://anything.example/i?app_key=k"), transport);
        try {
            connection.setRequestProperty(null, "v");
            Assert.fail("expected a NullPointerException for a header without a name");
        } catch (NullPointerException expected) {
            Assert.assertTrue(connection.getRequestProperties().isEmpty());
        }
        try {
            connection.getRequestProperties().put("X", Collections.singletonList("v"));
            Assert.fail("expected the headers to be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            Assert.assertTrue(connection.getRequestProperties().isEmpty());
        }

        connection.getResponseCode();

        try {
            connection.setRequestProperty("X", "v");
            Assert.fail("expected an IllegalStateException after the hand-over");
        } catch (IllegalStateException expected) {
            Assert.assertEquals(1, transport.requests.size());
        }
        try {
            connection.addRequestProperty("X", "v");
            Assert.fail("expected an IllegalStateException after the hand-over");
        } catch (IllegalStateException expected) {
            Assert.assertEquals(1, transport.requests.size());
        }
        try {
            connection.getRequestProperties();
            Assert.fail("expected an IllegalStateException after the hand-over");
        } catch (IllegalStateException expected) {
            Assert.assertEquals(1, transport.requests.size());
        }
    }

    /**
     * The header the hub sets on a final refusal keeps the name SDKs released with any hub version read.
     */
    @Test
    public void finalRefusalHeader_keepsItsWireName() {
        Assert.assertEquals("X-Countly-Final-Refusal", HubProtocol.FINAL_REFUSAL_HEADER);
    }
}
