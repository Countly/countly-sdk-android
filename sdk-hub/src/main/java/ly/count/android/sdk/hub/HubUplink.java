package ly.count.android.sdk.hub;

import androidx.annotation.NonNull;
import java.io.IOException;

/**
 * Sends the requests the hub accepted to the Countly server.
 * <p>
 * By default the hub sends them over HTTPS itself. A host app that already keeps a connection to its
 * own backend can provide an uplink that carries the requests there instead, and the backend forwards
 * them to the Countly server.
 */
public interface HubUplink {
    /**
     * Sends one request and returns the server's response. The hub calls it from a single thread, one
     * request at a time, in the order the requests arrived.
     *
     * @param request the request, its path and query relative to the configured server
     * @return the server's response
     * @throws IOException if no response could be obtained; the app's SDK then keeps the request for a later attempt
     */
    @NonNull HubResponse send(@NonNull HubRequest request) throws IOException;
}
