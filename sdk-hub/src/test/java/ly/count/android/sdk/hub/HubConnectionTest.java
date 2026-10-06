package ly.count.android.sdk.hub;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
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
}
