package ly.count.android.sdk;

/**
 * This interface represents session concept, that is one indivisible usage occasion of your application.
 *
 * Any data sent to Countly server is processed in a context of Session.
 * Only one session can send requests at a time, so even if you create 2 parallel sessions,
 * they will be made consequent automatically at the time of Countly SDK choise with no
 * correctness guarantees, so please avoid having parallel sessions.
 *
 */

public interface Session {

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
     * Only one session can send requests at a time and this session is called leading.
     * Once leading session is ended, next one (if any) becomes leading automatically
     * and starts sending requests.
     */
    Boolean isLeading();

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
     * @return this instance for method chaining.
     */
    Session end();

    /**
     * Send User Profiles change to the server.
     *
     * @ee Feature is not available in Countly Community Edition
     * @return this instance for method chaining.
     */
    Session addUserProfileChange();

    /**
     * Send Crash Report to the server.
     *
     * @ee Feature is not available in Countly Community Edition
     * @param t {@link Throwable} to log
     * @param fatal whether this crash report should be displayed as fatal in dashboard or not
     * @return this instance for method chaining.
     */
    Session addCrashReport(Throwable t, boolean fatal);

    /**
     * Send Crash Report to the server.
     *
     * @ee Feature is not available in Countly Community Edition
     * @param t {@link Throwable} to log
     * @param fatal whether this crash report should be displayed as fatal in dashboard or not
     * @param details additional comment about this crash report
     * @return this instance for method chaining.
     */
    Session addCrashReport(Throwable t, boolean fatal, String details);

    // TODO: to be continued...
}
