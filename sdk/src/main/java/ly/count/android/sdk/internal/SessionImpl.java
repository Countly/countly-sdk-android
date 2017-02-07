package ly.count.android.sdk.internal;

import ly.count.android.sdk.Session;

/**
 * This class represents session concept, that is one indivisible usage occasion of your application.
 *
 * Any data sent to Countly server is processed in a context of Session.
 * Only one session can send requests at a time, so even if you create 2 parallel sessions,
 * they will be made consequent automatically at the time of Countly SDK choice with no
 * correctness guarantees, so please avoid having parallel sessions.
 *
 */

class SessionImpl implements Session {
    /**
     * One second in nanoseconds
     */
    protected static final Double SECOND = 1000000000.0d;

    /**
     * {@link System#nanoTime()} of time when {@link Session} object is created.
     */
    protected final Long id;

    /**
     * {@link System#nanoTime()} of {@link #begin()}, {@link #update()} and {@link #end()} calls respectively.
     */
    protected Long began, updated, ended;

    /**
     * Create session with current time as id.
     */
    protected SessionImpl() {
        this.id = System.nanoTime();
    }

    /**
     * Deserialization constructor (use existing id).
     */
    protected SessionImpl(Long id) {
        this.id = id;
    }

    /**
     * Additional parameters to send with next request
     */
    protected Params params;

    /**
     * Start this session, add corresponding request.
     *
     * @return {@code this} instance for method chaining.
     */
    @Override
    public Session begin() {
        return begin(null);
    }

    protected Session begin(Long time) {
        if (began != null) {
            Log.wtf("Session already began");
            return this;
        } else if (ended != null) {
            Log.wtf("Session already ended");
            return this;
        } else {
            began = time == null ? System.nanoTime() : time;
        }

        // TODO: add request

        return this;
    }

    /**
     * Update session, send unsent duration along with any additional data to the server.
     *
     * @return {@code this} instance for method chaining.
     */
    @Override
    public Session update() {
        if (began == null) {
            Log.wtf("Session is not began to update it");
            return this;
        } else if (ended != null) {
            Log.wtf("Session is already ended to update it");
            return this;
        }

        Long duration = updateDuration();

        // TODO: add request

        return this;
    }

    /**
     * End this session, add corresponding request.
     *
     * @return {@code this} instance for method chaining.
     */
    @Override
    public Session end() {
        return end(null);
    }

    protected Session end(Long time) {
        if (began == null) {
            Log.wtf("Session is not began to end it");
            return this;
        } else if (ended != null) {
            Log.wtf("Session already ended");
            return this;
        }
        ended = time == null ? System.nanoTime() : time;

        Long duration = updateDuration();

        // TODO: add request

        return this;
    }

    /**
     * Calculate time since last {@link #update()} or since {@link #begin()} if no {@link #update()} yet made,
     * set {@link #updated} to now.
     *
     * @return calculated session duration to send in seconds
     */
    private Long updateDuration(){
        Long duration = -1L;
        Long now = System.nanoTime();
        if (updated == null) {
            duration = now - began;
        } else {
            duration = now - updated;
        }
        updated = now;
        duration = Math.round(duration / SECOND);
        return duration;
    }

    @Override
    public Session addUserProfileChange() {
        // TODO: implement, return edit object with commit method
        return this;
    }

    @Override
    public Session addCrashReport(Throwable t, boolean fatal) {
        return addCrashReport(t, fatal, null);
    }

    @Override
    public Session addCrashReport(Throwable t, boolean fatal, String details) {
        // TODO: implement
        return this;
    }

    // TODO: to be continued...

    /**
     * Add additional parameter to {@link #params}
     *
     * @param key name of parameter
     * @param value value of parameter, optional, will be url-encoded if provided
     * @return {@code this} instance for method chaining.
     */
    private Session addParam(String key, Object value) {
        if (params == null) {
            params = new Params();
        }
        params.add(key, value);
        return this;
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public Long getBegan() {
        return began;
    }

    @Override
    public Long getEnded() {
        return ended;
    }

    @Override
    public Boolean isLeading() {
        if (Core.instance.sessionLeading() == null) {
            Log.wtf("Leading session is null");
            return false;
        }
        return Core.instance.sessionLeading().getId().equals(getId());
    }
}
