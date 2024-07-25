package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.json.JSONObject;

interface RequestObserver {
    /**
     * Called just before when a request is going to be sent to the server.
     *
     * @param request the request
     */
    void onRequest(@NonNull String request);

    /**
     * Called when a response is received from the server.
     * Notifies for all responses, including the ones that are not successful.
     * So take care to check the response code and response body.
     * Because response body can be null for the not successful responses.
     *
     * @param response the response body return null if there is no response body
     * @param responseCode the response code return 0 if there is no response code
     * @param error the error message if there is any return null if there is no error
     */
    void onResponse(int responseCode, @Nullable JSONObject response, @Nullable String error);
}
