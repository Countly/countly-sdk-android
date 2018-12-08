package ly.count.sdk.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import ly.count.sdk.Event;

class TimedEvents implements Storable, EventImpl.EventRecorder {
    private static final Log.Module L = Log.module("TimedEvents");

    private Map<String, EventImpl> events;

    protected TimedEvents() {
        events = new HashMap<>();
    }

    Set<String> keys() {
        return events.keySet();
    }

    EventImpl event(Ctx ctx, String key) {
        EventImpl event = events.get(key);
        if (event == null) {
            event = new EventImpl(this, key);
            events.put(key, event);
            Storage.pushAsync(ctx, this);
        }
        return event;
    }

    boolean has(String key) {
        return events.containsKey(key);
    }

    @Override
    public Long storageId() {
        return 0L;
    }

    @Override
    public String storagePrefix() {
        return "timedEvent";
    }

    @Override
    public byte[] store() {
        ByteArrayOutputStream bytes = null;
        ObjectOutputStream stream = null;
        try {
            bytes = new ByteArrayOutputStream();
            stream = new ObjectOutputStream(bytes);
            stream.writeInt(events.size());
            for (String key : events.keySet()) {
                stream.writeUTF(key);
                stream.writeUTF(events.get(key).toJSON());
            }
            stream.close();
            return bytes.toByteArray();
        } catch (IOException e) {
            L.wtf("Cannot serialize timed events", e);
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

    @Override
    public boolean restore(byte[] data) {
        ByteArrayInputStream bytes = null;
        ObjectInputStream stream = null;

        try {
            bytes = new ByteArrayInputStream(data);
            stream = new ObjectInputStream(bytes);

            int l = stream.readInt();
            while (l-- > 0) {
                String key = stream.readUTF();
                EventImpl event = EventImpl.fromJSON(stream.readUTF(), this);
                events.put(key, event);
            }

            return true;
        } catch (IOException e) {
            L.wtf("Cannot deserialize config", e);
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

    @Override
    public void recordEvent(Event event) {
        event.endAndRecord();
        events.remove(((EventImpl)event).getKey());
    }
}
