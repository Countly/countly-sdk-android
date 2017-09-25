package ly.count.android.sdk.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import ly.count.android.sdk.Eve;
import ly.count.android.sdk.Session;
import ly.count.android.sdk.UserEditor;

/**
 * This class represents session concept, that is one indivisible usage occasion of your application.
 *
 * Any data sent to Countly server is processed in a context of Session.
 * Only one session can send requests at a time, so even if you create 2 parallel sessions,
 * they will be made consequent automatically at the time of Countly SDK choice with no
 * correctness guarantees, so please avoid having parallel sessions.
 *
 */

class SessionImpl implements Session, Storable {
    /**
     * {@link System#nanoTime()} of time when {@link Session} object is created.
     */
    protected final Long id;

    /**
     * {@link System#nanoTime()} of {@link #begin()}, {@link #update()} and {@link #end()} calls respectively.
     */
    protected Long began, updated, ended;

    /**
     * List of events still not added to request
     */
    protected final List<Eve> events = new ArrayList<>();

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
    protected Params params = new Params();

    /**
     * Whether to push changes to storage on every change automatically (false only for testing)
     */
    private boolean pushOnChange = true;

    /**
     * Start this session, add corresponding request.
     *
     * @return {@code this} instance for method chaining.
     */
    @Override
    public Session begin() {
        return begin(null);
    }

    Session begin(Long time) {
        if (began != null) {
            Log.wtf("Session already began");
            return this;
        } else if (ended != null) {
            Log.wtf("Session already ended");
            return this;
        } else {
            began = time == null ? System.nanoTime() : time;
        }

        ModuleRequests.sessionBegin(this);

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

        ModuleRequests.sessionUpdate(this, duration);

        if (pushOnChange) {
            Storage.pushAsync(this);
        }

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

    Session end(Long time) {
        if (began == null) {
            Log.wtf("Session is not began to end it");
            return this;
        } else if (ended != null) {
            Log.wtf("Session already ended");
            return this;
        }
        ended = time == null ? System.nanoTime() : time;

        Long duration = updateDuration();

        ModuleRequests.sessionEnd(this, duration);

        Storage.readAsync(this);

        return this;
    }

    /**
     * Calculate time since last {@link #update()} or since {@link #begin()} if no {@link #update()} yet made,
     * set {@link #updated} to now.
     *
     * @return calculated session duration to send in seconds
     */
    private Long updateDuration(){
        Long duration;
        Long now = System.nanoTime();
        if (updated == null) {
            duration = now - began;
        } else {
            duration = now - updated;
        }
        updated = now;
        return Device.nsToSec(duration);
    }

    /**
     * Create new event object, don't record it yet
     *
     * @return Event instance.
     * @see Eve#record()
     */
    public Eve event(String key) {
        return new EventImpl(this, key);
    }

    /**
     * Record event, push it to storage.
     * @param event Event to record
     * @return this instance for method chaining
     */
    synchronized Session recordEvent(Eve event) {
        events.add(event);
        if (pushOnChange) {
            Storage.pushAsync(this);
        }
        return this;
    }

    @Override
    public UserEditor addUserProfileChange() {
        // TODO: implement, return edit object with commit method
        return null;
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

    @Override
    public Session addLocation(double latitude, double longitude) {
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
    public Session addParam(String key, Object value) {
        params.add(key, value);
        if (pushOnChange) {
            Storage.pushAsync(this);
        }
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

    public Long storageId() {
        return this.id;
    }

    public String storagePrefix() {
        return "session";
    }

    public byte[] store() {
        ByteArrayOutputStream bytes = null;
        ObjectOutputStream stream = null;
        try {
            bytes = new ByteArrayOutputStream();
            stream = new ObjectOutputStream(bytes);
            stream.writeLong(id);
            stream.writeLong(began == null ? 0 : began);
            stream.writeLong(updated == null ? 0 : updated);
            stream.writeLong(ended == null ? 0 : ended);
            stream.writeInt(events.size());
            for (Eve event : events) {
                stream.writeUTF(event.toString());
            }
            stream.writeUTF(params.toString());
            stream.close();
            return bytes.toByteArray();
        } catch (IOException e) {
            Log.wtf("Cannot serialize session", e);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    Log.wtf("Cannot happen", e);
                }
            }
            if (bytes != null) {
                try {
                    bytes.close();
                } catch (IOException e) {
                    Log.wtf("Cannot happen", e);
                }
            }
        }
        return null;
    }

    public boolean restore(byte[] data) {
        ByteArrayInputStream bytes = null;
        ObjectInputStream stream = null;

        try {
            bytes = new ByteArrayInputStream(data);
            stream = new ObjectInputStream(bytes);
            if (id != stream.readLong()) {
                Log.wtf("Wrong file for session deserialization");
            }

            began = stream.readLong();
            began = began == 0 ? null : began;
            updated = stream.readLong();
            updated = updated == 0 ? null : updated;
            ended = stream.readLong();
            ended = ended == 0 ? null : ended;

            int count = stream.readInt();
            for (int i = 0; i < count; i++) {
                Eve event = EventImpl.fromJSON(this, stream.readUTF());
                if (event != null) {
                    events.add(event);
                }
            }

            params = new Params(stream.readUTF());

            return true;
        } catch (IOException e) {
            Log.wtf("Cannot deserialize session", e);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    Log.wtf("Cannot happen", e);
                }
            }
            if (bytes != null) {
                try {
                    bytes.close();
                } catch (IOException e) {
                    Log.wtf("Cannot happen", e);
                }
            }
        }

        return false;
    }

    SessionImpl setPushOnChange(boolean pushOnChange) {
        this.pushOnChange = pushOnChange;
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SessionImpl)) {
            return false;
        }

        SessionImpl session = (SessionImpl)obj;
        if (!id.equals(session.id)) {
            return false;
        }
        if ((began != null && !began.equals(session.began) || (session.began != null && !session.began.equals(began)))) {
            return false;
        }
        if ((updated != null && !updated.equals(session.updated) || (session.updated != null && !session.updated.equals(updated)))) {
            return false;
        }
        if ((ended != null && !ended.equals(session.ended) || (session.ended != null && !session.ended.equals(ended)))) {
            return false;
        }
        if (!params.equals(session.params)) {
            return false;
        }
        if (!events.equals(session.events)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
