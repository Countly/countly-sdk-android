package ly.count.sdk;

import java.util.Map;

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

    /**
     * Create event object, don't record it yet. Creates begin request if this session
     * hasn't yet been began.
     *
     * @param key key for this event, cannot be null or empty
     * @return Event instance.
     *
     * @see Event#record()
     */
    Event event(String key);

    /**
     * Get existing or create new timed event object, don't record it. Creates begin request if this session
     * hasn't yet been began.
     *
     * @param key key for this event, cannot be null or empty
     * @return timed Event instance.
     *
     * @see Event#endAndRecord() to end timed event
     */
    Event timedEvent(String key);

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
     * @param segments (optional, can be {@code null}) additional crash segments map
     * @param logs (optional, can be {@code null}) additional log lines (separated by \n) or comment about this crash report
     * @return this instance for method chaining.
     */
    Session addCrashReport(Throwable t, boolean fatal, String name, Map<String, String> segments, String... logs);

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
     * In case session ends and last view haven't been ended yet, it will be ended automatically.
     * Creates begin request if this session hasn't yet been began.
     *
     * @param name String representing name of this View
     * @param start whether this view is first in current application launch
     * @return new but already started {@link View}, you're responsible for its ending by calling {@link View#stop(boolean)}
     */
    View view(String name, boolean start);

    /**
     * Identical to {@link #view(String, boolean)}, but without {@code start} parameter which
     * is determined automatically based on whether this view is first in this session.
     * Creates begin request if this session hasn't yet been began.
     *
     * @param name String representing name of this View
     * @return new but already started {@link View}, you're responsible for its ending by calling {@link View#stop(boolean)}
     */
    View view(String name);
}
