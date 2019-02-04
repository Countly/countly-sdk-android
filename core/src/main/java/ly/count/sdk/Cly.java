package ly.count.sdk;

import java.util.Map;

import ly.count.sdk.internal.CtxCore;
import ly.count.sdk.internal.SDKInterface;

public abstract class Cly implements Usage {
    protected static Cly cly;
    protected CtxCore ctx;
    protected SDKInterface sdkInterface;

    protected Cly() {
        cly = this;
    }

    protected static Session session(CtxCore ctx) {
        return cly.sdkInterface.session(ctx, null);
    }

    protected static Session getSession() {
        return cly.sdkInterface.getSession();
    }

    @Override
    public Event event(String key) {
        return ((Session) sdkInterface.session(ctx, null)).event(key);
    }

    @Override
    public Event timedEvent(String key) {
        return ((Session) sdkInterface.session(ctx, null)).timedEvent(key);
    }

    /**
     * Get current User Profile object.
     *
     * @see User#edit() to get {@link UserEditor} object
     * @see UserEditor#commit() to submit changes to the server
     * @return current User Profile instance
     */
    @Override
    public User user() {
        return ((Session) sdkInterface.session(ctx, null)).user();
    }

    @Override
    public Usage addParam(String key, Object value) {
        return ((Session) sdkInterface.session(ctx, null)).addParam(key, value);
    }

    @Override
    public Usage addCrashReport(Throwable t, boolean fatal) {
        return ((Session) sdkInterface.session(ctx, null)).addCrashReport(t, fatal);
    }

    @Override
    public Usage addCrashReport(Throwable t, boolean fatal, String name, Map<String, String> segments, String... logs) {
        return ((Session) sdkInterface.session(ctx, null)).addCrashReport(t, fatal, name, segments, logs);
    }

    @Override
    public Usage addLocation(double latitude, double longitude) {
        return ((Session) sdkInterface.session(ctx, null)).addLocation(latitude, longitude);
    }

    @Override
    public View view(String name, boolean start) {
        return ((Session) sdkInterface.session(ctx, null)).view(name, start);
    }

    @Override
    public View view(String name) {
        return ((Session) sdkInterface.session(ctx, null)).view(name);
    }
}
