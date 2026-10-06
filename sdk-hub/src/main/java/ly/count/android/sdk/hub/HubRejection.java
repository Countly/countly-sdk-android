package ly.count.android.sdk.hub;

import androidx.annotation.NonNull;

/**
 * The hub refused a request without sending it to the server. The app receives the given status and
 * the reason, and its SDK keeps the request for a later attempt like it does for any server error.
 */
final class HubRejection extends Exception {
    final int status;

    /**
     * @param status the HTTP status the app receives
     * @param reason why the request was refused, without any request data
     */
    HubRejection(int status, @NonNull String reason) {
        super(reason);
        this.status = status;
    }
}
