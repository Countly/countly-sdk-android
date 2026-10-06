package ly.count.android.sdk.hub;

import androidx.annotation.NonNull;
import java.net.HttpURLConnection;
import java.net.URL;
import ly.count.android.sdk.ConnectionFactory;

/**
 * Gives the SDK hub connections instead of network connections, all sharing one transport to the hub.
 */
final class HubConnectionFactory implements ConnectionFactory {
    private final HubTransport transport;

    /**
     * @param transport carries the requests of every connection to the hub
     */
    HubConnectionFactory(@NonNull HubTransport transport) {
        this.transport = transport;
    }

    /**
     * Creates a hub connection for one request.
     *
     * @param url the full request URL as the SDK built it
     * @return a connection that hands the request to the hub
     */
    @Override
    public @NonNull HttpURLConnection openConnection(@NonNull URL url) {
        return new HubConnection(url, transport);
    }
}
