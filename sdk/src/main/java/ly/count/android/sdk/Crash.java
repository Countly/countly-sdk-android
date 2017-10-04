package ly.count.android.sdk;

/**
 * Crash-encapsulating class
 */

public interface Crash {
    /**
     * Set {@link Throwable} object to be sent to the server.
     * Does the same job as {@link #setException(Exception)}.
     *
     * @param t Throwable to send
     * @return this instance for method chaining
     */
    Crash setThrowable(Throwable t);

    /**
     * Set {@link Exception} object to be sent to the server.
     * Does the same job as {@link #setThrowable(Throwable)}.
     *
     * @param e Exception to send
     * @return this instance for method chaining
     */
    Crash setException(Exception e);

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
     * Add some string describing what happened.
     *
     * @param logs log string
     * @return this instance for method chaining
     */
    Crash setDetails(String logs);
}
