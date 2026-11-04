package ly.count.android.sdk.hub;

import androidx.annotation.NonNull;

/**
 * The hub refused a request without sending it to the server. The app receives the given status and
 * the reason. A final refusal tells the app's SDK to drop the request; otherwise the SDK keeps it for a
 * later attempt, as it does for any server error.
 */
final class HubRejection extends Exception {
    final int status;
    final boolean finalRefusal;

    /**
     * @param status the HTTP status the app receives
     * @param reason why the request was refused, without any request data
     * @param finalRefusal whether the request can never be accepted, by this hub or a later one
     */
    HubRejection(int status, @NonNull String reason, boolean finalRefusal) {
        super(reason);
        this.status = status;
        this.finalRefusal = finalRefusal;
    }
}
