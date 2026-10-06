package ly.count.android.sdk.hub;

/**
 * The contract between the hub client inside an app and the hub service inside the host app: the
 * intent action the service is bound with and the keys of the bundles the two sides exchange.
 * <p>
 * Requests and replies travel as bundles of named values rather than as custom parcelable classes, so
 * a client and a hub released at different times keep understanding each other: a side ignores keys
 * it does not know, and a key is never given a different meaning.
 */
final class HubProtocol {
    static final String ACTION_BIND = "ly.count.android.sdk.hub.action.BIND";

    static final int VERSION = 1;

    static final String KEY_VERSION = "version";
    static final String KEY_REPLY_WHEN = "replyWhen";
    static final String KEY_METHOD = "method";
    static final String KEY_PATH = "path";
    static final String KEY_QUERY = "query";
    static final String KEY_HEADER_NAMES = "headerNames";
    static final String KEY_HEADER_VALUES = "headerValues";
    static final String KEY_BODY = "body";
    static final String KEY_BODY_FILE = "bodyFile";
    static final String KEY_TIMEOUT_MILLIS = "timeoutMillis";
    static final String KEY_STATUS = "status";
    static final String KEY_FAILURE = "failure";

    /**
     * The hub replies once the server answered, with the server's response. It is the only reply mode
     * of protocol version 1; other values are reserved for a hub that stores requests and replies
     * before delivering them.
     */
    static final int REPLY_WHEN_DELIVERED = 1;

    /**
     * Bodies up to this size travel inside the bundle. Larger ones, such as crash reports with native
     * dumps, travel through a file descriptor, because a binder transaction is limited to about 1 MB
     * shared by every call the process has in flight, so several concurrent inline bodies must stay
     * well under that.
     */
    static final int INLINE_BODY_LIMIT = 64 * 1024;

    private HubProtocol() {
    }
}
