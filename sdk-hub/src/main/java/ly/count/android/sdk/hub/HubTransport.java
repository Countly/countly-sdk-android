package ly.count.android.sdk.hub;

import androidx.annotation.NonNull;
import java.io.IOException;

/**
 * Carries a request from an app to the hub and brings back the response.
 */
interface HubTransport {
    /**
     * Hands a request to the hub and waits for the response.
     *
     * @param request the request as the SDK built it
     * @return the response the hub handed back
     * @throws IOException if the hub could not be reached or could not get a response from the server
     */
    @NonNull HubResponse exchange(@NonNull HubRequest request) throws IOException;
}
