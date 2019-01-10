package ly.count.sdk.internal;

import java.util.ArrayList;
import java.util.List;

import ly.count.sdk.Event;
import ly.count.sdk.Session;

// TODO: register
public class ModuleEvents extends ModuleBase {
    private List<Event> events = null;
    private Session session = null;

    private final EventImpl.EventRecorder recorder = new EventImpl.EventRecorder() {
        @Override
        public void recordEvent(Event event) {
            ModuleSessions sessions = (ModuleSessions)SDKCore.instance.module(CoreFeature.Sessions.getIndex());
            if (sessions == null) {
                if (events != null) {
                    events.add(event);
                } else {
                    // ignored, module is stopped or not initialized yet
                }
            } else {
                Session session = sessions.getSession();
                ((SessionImpl) session).recordEvent(event);
            }
        }
    };

    @Override
    public Integer getFeature() {
        return CoreFeature.Events.getIndex();
    }

    @Override
    public void init(InternalConfig config) {
        super.init(config);
        events = new ArrayList<>();
    }

    @Override
    public void stop(CtxCore ctx, boolean clear) {
        if (!clear) {
            dumpEvents(ctx);
        }
        events = null;
    }

    @Override
    public void onSessionBegan(Session session, CtxCore ctx) {
        this.session = session;
        if (events != null && events.size() > 0) {
            for (Event event : events) {
                ((SessionImpl)this.session).recordEvent(event);
            }
            events.clear();
        }
    }

    @Override
    public void onSessionEnded(Session session, CtxCore ctx) {
        this.session = null;
    }

    private void dumpEvents(CtxCore ctx) {
        if (events != null && events.size() > SDKCore.instance.config().getEventsBufferSize()) {
            Request request = ModuleRequests.nonSessionRequest(ctx);
            request.params.arr("events").put(events);
            ModuleRequests.pushAsync(ctx, request);
        }
    }

    public Event event(CtxCore ctx, String name) {
        ModuleSessions sessions = (ModuleSessions)SDKCore.instance.module(CoreFeature.Sessions.getIndex());
        dumpEvents(ctx);
        return new EventImpl(sessions == null || sessions.getSession() == null ? recorder : sessions.getSession(), name);
    }
}
