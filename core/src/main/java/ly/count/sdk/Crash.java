package ly.count.sdk;

import java.util.List;
import java.util.Map;

/**
 * Crash-encapsulating class
 */

public interface Crash {
    /**
     * Set {@link Throwable} object to be sent to the server.
     * Does the same job as {@link #addException(Exception)}.
     *
     * @param t Throwable to send
     * @return this instance for method chaining
     */
    Crash addThrowable(Throwable t);

    /**
     * Set {@link Exception} object to be sent to the server.
     * Does the same job as {@link #addThrowable(Throwable)}.
     *
     * @param e Exception to send
     * @return this instance for method chaining
     */
    Crash addException(Exception e);

    /**
     * Record stack traces of all threads.
     *
     * @param main Thread to record first
     * @param traces all stack traces to send
     * @return this instance for method chaining
     */
    Crash addTraces(Thread main, Map<Thread, StackTraceElement[]> traces);

    /**
     * Set whether crash was fatal (uncaught {@link Exception} or very bad caught {@link Exception}
     * which prevents app from functioning correctly.
     *
     * @param fatal {@code true} if fatal, {@code false} otherwise
     * @return this instance for method chaining
     */
    Crash setFatal(boolean fatal);

    /**
     * Set crash name, that is its title.
     *
     * @param name title string
     * @return this instance for method chaining
     */
    Crash setName(String name);

    /**
     * Add some segmentation to this crash.
     *
     * @param segments crash segments
     * @return this instance for method chaining
     */
    Crash setSegments(Map<String, String> segments);

    /**
     * Add logs to this crash.
     *
     * @param logs crash logs
     * @return this instance for method chaining
     */
    Crash setLogs(String[] logs);

    /**
     * @return {@link Throwable} of this crash
     */
    Throwable getThrowable();

    /**
     * @return thread traces of this crash
     */
    Map<Thread, StackTraceElement[]> getTraces();

    /**
     * @return whether this crash was recorded as fatal or not
     */
    boolean isFatal();

    /**
     * @return crash name if any, {@code null otherwise}
     */
    String getName();

    /**
     * @return custom crash segments if any, {@code null} otherwise
     */
    Map<String, String> getSegments();

    /**
     * @return custom crash logs if any, {@code null} otherwise
     */
    List<String> getLogs();
}
