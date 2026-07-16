package ly.count.android.sdk;

/**
 * Internal callback interface for tracking request completion and queue status.
 * <p>
 * This interface provides two callback methods:
 * <ul>
 *   <li>{@link #onRequestCompleted(String, boolean)} - Called when an individual request completes</li>
 *   <li>{@link #onRQFinished()} - Called when the entire request queue has finished processing</li>
 * </ul>
 * <p>
 * Both methods have default empty implementations, allowing implementations to override
 * only the methods they need.
 */
interface InternalRequestCallback {
    /**
     * Called when a request completes processing.
     * <p>
     * This callback is invoked in the following scenarios:
     * <ul>
     *   <li>Success: response=null, success=true - Request was successfully sent to server</li>
     *   <li>Failure: response=errorMessage, success=false - Server returned an error or request failed</li>
     *   <li>Dropped: response=reason, success=false - Request was dropped (too old or from crawler)</li>
     *   <li>Exception: response=exceptionMessage, success=false - An exception occurred during processing</li>
     * </ul>
     * <p>
     * After this callback is invoked, the callback is automatically removed from the internal map
     * and will not be called again.
     *
     * @param response The server response (null on success, error message on failure)
     * @param success true if the request was successfully sent and acknowledged, false otherwise
     */
    default void onRequestCompleted(String response, boolean success) {
    }

    /**
     * Called when the request queue finishes processing and becomes empty.
     * <p>
     * This callback is invoked when:
     * <ul>
     *   <li>The request queue is empty (no requests to process)</li>
     *   <li>The request queue returns null</li>
     * </ul>
     * <p>
     * This is typically used by the global callback registered in ConnectionQueue to execute
     * all registered global request callback actions.
     */
    default void onRQFinished() {
    }
}
