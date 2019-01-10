package ly.count.sdk.internal;

import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.Set;

import ly.count.sdk.ConfigCore;

/**
 * Abstraction over particular SDK implementation: java-native or Android
 */
public interface SDKInterface {
    UserImpl user();
    InternalConfig config();

    void init(CtxCore ctx);
    void stop(CtxCore ctx, boolean clear);

    void onDeviceId(CtxCore ctx, ConfigCore.DID id, ConfigCore.DID old);

    SessionImpl getSession();

    /**
     * Get current {@link SessionImpl} or create new one if current is {@code null}.
     *
     * @param ctx Ctx to create new {@link SessionImpl} in
     * @param id ID of new {@link SessionImpl} if needed
     * @return current {@link SessionImpl} instance
     */
    SessionImpl session(CtxCore ctx, Long id);

    /**
     * Notify all {@link Module} instances about new session has just been started
     *
     * @param session session to begin
     * @return supplied session for method chaining
     */
    SessionImpl onSessionBegan(CtxCore ctx, SessionImpl session);

    /**
     * Notify all {@link Module} instances session was ended
     *
     * @param session session to end
     * @return supplied session for method chaining
     */
    SessionImpl onSessionEnded(CtxCore ctx, SessionImpl session);

    void onRequest(CtxCore ctx, Request request);
    void onCrash(CtxCore ctx, Throwable t, boolean fatal, String name, Map<String, String> segments, String[] logs);
    void onUserChanged(CtxCore ctx, JSONObject changes, Set<String> cohortsAdded, Set<String> cohortsRemoved);

    // -------------------- Storage -----------------------
    int storablePurge(CtxCore ctx, String prefix);
    Boolean storableWrite(CtxCore ctx, String prefix, Long id, byte[] data);
    <T extends Storable> Boolean storableWrite(CtxCore ctx, T storable);
    <T extends Storable> Boolean storableRead(CtxCore ctx, T storable);
    byte[] storableReadBytes(CtxCore ctx, String name);
    byte[] storableReadBytes(CtxCore ctx, String prefix, Long id);
    <T extends Storable> Map.Entry<Long, byte[]> storableReadBytesOneOf(CtxCore ctx, T storable, boolean asc);
    <T extends Storable> Boolean storableRemove(CtxCore ctx, T storable);
    <T extends Storable> Boolean storablePop(CtxCore ctx, T storable);
    List<Long> storableList(CtxCore ctx, String prefix, int slice);

    // -------------------- Service ------------------------
    void onSignal(CtxCore ctx, int id, Byteable param1, Byteable param2);
    void onSignal(CtxCore ctx, int id, String param);
}
