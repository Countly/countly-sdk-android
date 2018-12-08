package ly.count.sdk;

/**
 * This interface represents session concept, that is one indivisible usage occasion of your application.
 *
 * Any data sent to Countly server is processed in a context of Session.
 * Only one session can send requests at a time, so even if you create 2 parallel sessions,
 * they will be made consequent automatically at the time of Countly SDK choice with no
 * correctness guarantees, so please avoid having parallel sessions.
 *
 */

public interface Session extends Usage {

    /**
     * {@link System#nanoTime()} of this instance creation.
     */
    Long getId();

    /**
     * {@link System#currentTimeMillis()} of {@link #begin()} call.
     */
    Long getBegan();

    /**
     * {@link System#currentTimeMillis()} of {@link #end()} call.
     */
    Long getEnded();

    /**
     * Start this session, add corresponding request to queue.
     *
     * @return this instance for method chaining.
     */
    Session begin();

    /**
     * Send update request to the server saying that user is still using the app.
     *
     * @return this instance for method chaining.
     */
    Session update();

    /**
     * End this session, add corresponding request to queue.
     *
     */
    void end();

    /**
     * Whether this session was started and haven't been ended yet.
     *
     * @see #begin()
     * @see #end()
     * @return {@code true} if session was started and haven't been ended yet, {@code false} otherwise
     */
    boolean isActive();
}
