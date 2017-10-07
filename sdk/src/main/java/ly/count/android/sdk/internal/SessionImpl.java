package ly.count.android.sdk.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import ly.count.android.sdk.Config;
import ly.count.android.sdk.Eve;
import ly.count.android.sdk.Session;
import ly.count.android.sdk.User;

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
    private static final Log.Module L = Log.module("SessionImpl");

    /**
     * {@link System#nanoTime()} of time when {@link Session} object is created.
     */
    protected final Long id;

    protected final Context ctx;

    /**
     * {@link System#nanoTime()} of {@link #begin()}, {@link #update()} and {@link #end()} calls respectively.
     */
    protected Long began, updated, ended;

    /**
     * List of events still not added to request
     */
    protected final List<Eve> events = new ArrayList<>();

    /**
     * Additional parameters to send with next request
     */
    protected Params params = new Params();

    /**
     * Whether to push changes to storage on every change automatically (false only for testing)
     */
    private boolean pushOnChange = true;

    /**
     * Create session with current time as id.
     */
    protected SessionImpl(Context ctx) {
        this.id = System.nanoTime();
        this.ctx = ctx;
    }

    /**
     * Deserialization constructor (use existing id).
     */
    protected SessionImpl(Context ctx, Long id) {
        this.ctx = ctx;
        this.id = id;
    }

    @Override
    public Session begin() {
        begin(null);
        return this;
    }

    Future<Boolean> begin(Long now) {
        if (Core.instance == null) {
            L.wtf("Countly is not initialized");
            return null;
        } else if (began != null) {
            L.wtf("Session already began");
            return null;
        } else if (ended != null) {
            L.wtf("Session already ended");
            return null;
        } else {
            began = now == null ? System.nanoTime() : now;
        }

        if (pushOnChange) {
            Storage.pushAsync(ctx, this);
        }

        Future<Boolean> ret = ModuleRequests.sessionBegin(ctx, this);

        Core.instance.onSessionBegan(ctx, this);

        return ret;
    }

    @Override
    public Session update() {
        update(null);
        return this;
    }

    Future<Boolean> update(Long now) {
        if (Core.instance == null) {
            L.wtf("Countly is not initialized");
            return null;
        } else if (began == null) {
            L.wtf("Session is not began to update it");
            return null;
        } else if (ended != null) {
            L.wtf("Session is already ended to update it");
            return null;
        }

        Long duration = updateDuration(now);

        if (pushOnChange) {
            Storage.pushAsync(ctx, this);
        }

        return ModuleRequests.sessionUpdate(ctx, this, duration);
    }

    @Override
    public Session end() {
        end(null, null, null);
        return Core.instance.sessionLeading();
    }

    Future<Boolean> end(Long now, final Tasks.Callback<Boolean> callback, String did) {
        if (Core.instance == null) {
            L.wtf("Countly is not initialized");
            return null;
        } else if (began == null) {
            L.wtf("Session is not began to end it");
            return null;
        } else if (ended != null) {
            L.wtf("Session already ended");
            return null;
        }
        ended = now == null ? System.nanoTime() : now;

        Storage.pushAsync(ctx, this);

        Long duration = updateDuration(null);

        Future<Boolean> ret = ModuleRequests.sessionEnd(ctx, this, duration, did, new Tasks.Callback<Boolean>() {
            @Override
            public void call(Boolean removed) throws Exception {
                if (!removed) {
                    L.wtf("Unable to record session end request");
                }
                Storage.removeAsync(ctx, SessionImpl.this, callback);
            }
        });

        Core.instance.onSessionEnded(ctx, this);
        Core.instance.sessionRemove(this);

        return ret;
    }

    Boolean recover(Config config) {
        if (Device.nsToSec(System.nanoTime() - id) < config.getSessionCooldownPeriod() * 2) {
            return null;
        } else {
            Future<Boolean> future = null;
            if (began == null) {
                return Storage.remove(ctx, this);
            } else if (ended == null && updated == null) {
                future = end(began + Device.secToNs(config.getSessionCooldownPeriod()), null, null);
            } else if (ended == null) {
                future = end(updated + Device.secToNs(config.getSessionCooldownPeriod()), null, null);
            } else {
                // began != null && ended != null
                return Storage.remove(ctx, this);
            }

            try {
                return future.get();
            } catch (InterruptedException | ExecutionException e) {
                L.wtf("Interrupted while resolving session recovery future", e);
                return false;
            }
        }
    }

    @Override
    public boolean isActive() {
        return began != null && ended == null;
    }

    /**
     * Calculate time since last {@link #update()} or since {@link #begin()} if no {@link #update()} yet made,
     * set {@link #updated} to now.
     *
     * @return calculated session duration to send in seconds
     */
    private Long updateDuration(Long now){
        now = now == null ? System.nanoTime() : now;
        Long duration;

        if (updated == null) {
            duration = now - began;
        } else {
            duration = now - updated;
        }
        updated = now;
        return Device.nsToSec(duration);
    }

    public Eve event(String key) {
        return new EventImpl(this, key);
    }

    synchronized Session recordEvent(Eve event) {
        events.add(event);
        if (pushOnChange) {
            Storage.pushAsync(ctx, this);
        }
        return this;
    }

    @Override
    public User user() {
        if (Core.instance == null) {
            L.wtf("Countly is not initialized");
            return null;
        } else {
            return Core.instance.user();
        }
    }

    @Override
    public Session addCrashReport(Throwable t, boolean fatal) {
        return addCrashReport(t, fatal, null, null);
    }

    @Override
    public Session addCrashReport(Throwable t, boolean fatal, String name, String details) {
        Core.onCrash(ctx, t, fatal, name, details);
        return this;
    }

    @Override
    public Session addLocation(double latitude, double longitude) {
        return addParam("location", latitude + "," + longitude);
    }

    // TODO: to be continued...

    public Session addParam(String key, Object value) {
        params.add(key, value);
        if (pushOnChange) {
            Storage.pushAsync(ctx, this);
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
        if (Core.instance == null) {
            L.wtf("Countly is not initialized");
            return false;
        } else if (Core.instance.sessionLeading() == null) {
            return false;
        }
        return Core.instance.sessionLeading().getId().equals(getId());
    }

    public Long storageId() {
        return this.id;
    }

    public String storagePrefix() {
        return getStoragePrefix();
    }

    static String getStoragePrefix() {
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
            L.wtf("Cannot serialize session", e);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    L.wtf("Cannot happen", e);
                }
            }
            if (bytes != null) {
                try {
                    bytes.close();
                } catch (IOException e) {
                    L.wtf("Cannot happen", e);
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
                L.wtf("Wrong file for session deserialization");
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
            L.wtf("Cannot deserialize session", e);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    L.wtf("Cannot happen", e);
                }
            }
            if (bytes != null) {
                try {
                    bytes.close();
                } catch (IOException e) {
                    L.wtf("Cannot happen", e);
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
