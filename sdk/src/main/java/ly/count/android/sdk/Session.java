package ly.count.android.sdk;

/**
 * This interface represents session concept, that is one indivisible usage occasion of your application.
 *
 * Any data sent to Countly server is processed in a context of Session.
 * Only one session can send requests at a time, so even if you create 2 parallel sessions,
 * they will be made consequent automatically at the time of Countly SDK choice with no
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
     * Whether this session was started and haven't been ended yet.
     *
     * @see #begin()
     * @see #end()
     * @return {@code true} if session was started and haven't been ended yet, {@code false} otherwise
     */
    boolean isActive();

    /**
     * Create event object, don't add it to this session yet.
     *
     * @param key key for this event, cannot be null or empty
     * @return Event instance.
     *
     * @see Eve#record()
     */
    Eve event(String key);

    /**
     * Get current User Profile.
     * Note that data is not downloaded from server. Only properties set in current app
     * installation are returned in {@link User} object.
     *
     * @ee Feature is not available in Countly Community Edition
     * @return this instance for method chaining.
     */
    User user();

    /**
     * Add parameter to this session which will be sent along with next request.
     *
     * @param key name of parameter
     * @param value value of parameter
     * @return this instance for method chaining.
     */
    Session addParam(String key, Object value);

    /**
     * Send Crash Report to the server.
     *
     * @param t {@link Throwable} to log
     * @param fatal whether this crash report should be displayed as fatal in dashboard or not
     * @return this instance for method chaining.
     */
    Session addCrashReport(Throwable t, boolean fatal);

    /**
     * Send Crash Report to the server.
     *
     * @param t {@link Throwable} to log
     * @param fatal whether this crash report should be displayed as fatal in dashboard or not
     * @param name (optional, can be {@code null}) name of the report, falls back to first line of stack trace by default
     * @param details (optional, can be {@code null}) additional comment about this crash report
     * @return this instance for method chaining.
     */
    Session addCrashReport(Throwable t, boolean fatal, String name, String details);

    /**
     * Send location information to the server.
     *
     * @param latitude geographical latitude of the user
     * @param longitude geographical longitude of the user
     * @return this instance for method chaining.
     */
    Session addLocation(double latitude, double longitude);

    /**
     * Start new view.
     * In case previous view in this session is not ended yet, it will be ended automatically.
     * In case session ends and last view haven't been ended yet, it will be ended automatically
     *
     * @param name String representing name of this View
     * @param start whether this view is first in current application launch
     * @return new but already started {@link View}, you're responsible for its ending by calling {@link View#end(boolean)}
     */
    View view(String name, boolean start);

    /**
     * Identical to {@link #view(String, boolean)}, but without {@code start} parameter which
     * is determined automatically based on whether this view is first in this session.
     *
     * @param name String representing name of this View
     * @return new but already started {@link View}, you're responsible for its ending by calling {@link View#end(boolean)}
     */
    View view(String name);

    // TODO: to be continued...
}
