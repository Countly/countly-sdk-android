package ly.count.android.sdk.hub;

import ly.count.android.sdk.ConnectionFactory;

/**
 * The contract between the hub client inside an app and the hub service inside the host app: the
 * intent action the service is bound with and the keys of the bundles the two sides exchange.
 * <p>
 * Requests and replies travel as bundles of named values rather than as custom parcelable classes, so
 * a client and a hub released at different times keep understanding each other: a side ignores keys
 * it does not know, and a key is never given a different meaning. For the same reason a hub never
 * lowers what it accepts: {@link #MAX_INLINE_BODY_ACCEPTED} only ever grows.
 * <p>
 * A request is handed over with a short synchronous call that only admits it. The response comes later
 * through a one-way callback, so neither side keeps a binder thread or binder buffer busy while the
 * request waits for the network.
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
    static final String KEY_BODY_MEMORY = "bodyMemory";
    static final String KEY_BODY_FILE = "bodyFile";
    static final String KEY_TIMEOUT_MILLIS = "timeoutMillis";
    static final String KEY_ACCEPTED = "accepted";
    static final String KEY_REPLY_WITHIN_MILLIS = "replyWithinMillis";
    static final String KEY_STATUS = "status";
    static final String KEY_FAILURE = "failure";

    /**
     * The hub replies once the server answered, with the server's response. It is the only reply mode
     * of protocol version 1; other values are reserved for a hub that stores requests and replies
     * before delivering them.
     */
    static final int REPLY_WHEN_DELIVERED = 1;

    /**
     * The largest body either side puts inside a bundle. Larger request and response bodies travel in
     * shared memory, or before Android 8.1 through a file descriptor, because a binder transaction is
     * limited to about 1 MB shared by every call the receiving process has in flight.
     */
    static final int INLINE_BODY_LIMIT = 64 * 1024;

    /**
     * The largest inline request body a hub accepts. It is larger than {@link #INLINE_BODY_LIMIT} and
     * is never lowered, so a client released with a larger inline limit keeps working.
     */
    static final int MAX_INLINE_BODY_ACCEPTED = 256 * 1024;

    /**
     * Response header the hub sets on a request it refuses for good, telling the SDK to drop that
     * request instead of retrying it.
     */
    static final String FINAL_REFUSAL_HEADER = ConnectionFactory.FINAL_REFUSAL_HEADER;

    private HubProtocol() {
    }
}
