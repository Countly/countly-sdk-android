package ly.count.sdk.internal;

import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.Set;

import ly.count.sdk.ConfigCore;

/**
 * Abstraction over particular SDK implementation: java-native or Android
 */
public interface SDK {
    UserImpl user();
    InternalConfig config();

    void init(Ctx ctx);
    void stop(Ctx ctx, boolean clear);

    void onDeviceId(Ctx ctx, ConfigCore.DID id, ConfigCore.DID old);

    SessionImpl getSession();

    /**
     * Get current {@link SessionImpl} or create new one if current is {@code null}.
     *
     * @param ctx Ctx to create new {@link SessionImpl} in
     * @param id ID of new {@link SessionImpl} if needed
     * @return current {@link SessionImpl} instance
     */
    SessionImpl session(Ctx ctx, Long id);

    /**
     * Notify all {@link Module} instances about new session has just been started
     *
     * @param session session to begin
     * @return supplied session for method chaining
     */
    SessionImpl onSessionBegan(Ctx ctx, SessionImpl session);

    /**
     * Notify all {@link Module} instances session was ended
     *
     * @param session session to end
     * @return supplied session for method chaining
     */
    SessionImpl onSessionEnded(Ctx ctx, SessionImpl session);

    void onRequest(Ctx ctx, Request request);
    void onCrash(Ctx ctx, Throwable t, boolean fatal, String name, Map<String, String> segments, String[] logs);
    void onUserChanged(Ctx ctx, JSONObject changes, Set<String> cohortsAdded, Set<String> cohortsRemoved);

    // -------------------- Storage -----------------------
    int storablePurge(Ctx ctx, String prefix);
    Boolean storableWrite(Ctx ctx, String prefix, Long id, byte[] data);
    <T extends Storable> Boolean storableWrite(Ctx ctx, T storable);
    <T extends Storable> Boolean storableRead(Ctx ctx, T storable);
    byte[] storableReadBytes(Ctx ctx, String name);
    byte[] storableReadBytes(Ctx ctx, String prefix, Long id);
    <T extends Storable> Map.Entry<Long, byte[]> storableReadBytesOneOf(Ctx ctx, T storable, boolean asc);
    <T extends Storable> Boolean storableRemove(Ctx ctx, T storable);
    <T extends Storable> Boolean storablePop(Ctx ctx, T storable);
    List<Long> storableList(Ctx ctx, String prefix, int slice);

    // -------------------- Service ------------------------
    void onSignal(Ctx ctx, int id, Byteable param1, Byteable param2);
    void onSignal(Ctx ctx, int id, String param);
}
