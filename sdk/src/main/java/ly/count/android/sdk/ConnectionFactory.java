package ly.count.android.sdk;

import androidx.annotation.NonNull;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Creates the connections the SDK sends its server requests through, in place of opening them with
 * {@link URL#openConnection()}.
 * <p>
 * Set one with {@link CountlyConfig#setConnectionFactory(ConnectionFactory)} to carry the SDK's requests
 * over a transport of your own, for example to another app on the device that holds the network access
 * and relays them. The SDK still builds every request itself and still decides from the response whether
 * to retry, so the returned connection only has to carry the request and hand back the response.
 * <p>
 * The SDK uses the returned connection the same way it uses its own: it sets the request method,
 * timeouts and request properties, writes a body through {@link HttpURLConnection#getOutputStream()} when
 * it posts, reads the response headers through {@link HttpURLConnection#getHeaderFieldKey(int)} and
 * {@link HttpURLConnection#getHeaderField(int)}, calls {@link HttpURLConnection#connect()}, reads the
 * outcome through {@link HttpURLConnection#getResponseCode()} and {@link HttpURLConnection#getInputStream()}
 * or, when that throws, {@link HttpURLConnection#getErrorStream()}, and finally calls
 * {@link HttpURLConnection#disconnect()}. Reading the response headers is the first thing that needs the
 * response, so the request has to be sent no later than that.
 * <p>
 * A request from the request queue that gets an error response is normally kept and retried, and
 * the requests after it wait until it succeeds. When the transport knows that a request can never be
 * accepted, for example because it is malformed for the transport, it can answer with an error status
 * and the {@link #FINAL_REFUSAL_HEADER} header. The SDK then drops that request and continues with the
 * next one, so a single request cannot hold back all later ones. The header is honoured only on
 * connections a factory created.
 */
public interface ConnectionFactory {
    /**
     * Response header a transport sets, together with an error status, on a request it will never
     * accept. Its value is the reason, which the SDK logs.
     */
    String FINAL_REFUSAL_HEADER = "X-Countly-Final-Refusal";

    /**
     * Creates the connection for one server request. It is called on the SDK's background threads.
     *
     * @param url the full request URL: the configured server URL followed by the endpoint and, for GET
     * requests, the query
     * @return a connection that has not been connected yet
     * @throws IOException if no connection can be created; the SDK handles this like a failed request
     */
    @NonNull HttpURLConnection openConnection(@NonNull URL url) throws IOException;
}
