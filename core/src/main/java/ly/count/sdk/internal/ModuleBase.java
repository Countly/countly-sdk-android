package ly.count.sdk.internal;

import org.json.JSONObject;

import java.util.Set;

import ly.count.sdk.Config;
import ly.count.sdk.Session;

/**
 * Created by artem on 05/01/2017.
 */

public abstract class ModuleBase implements Module {
    private boolean active = false;

    @Override
    public void init(InternalConfig config) {
    }

    @Override
    public void onDeviceId(Ctx ctx, Config.DID deviceId, Config.DID oldDeviceId) {
    }

    @Override
    public void stop(Ctx ctx, boolean clear) {
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void onContextAcquired(Ctx ctx) {
    }

    @Override
    public void onLimitedContextAcquired(Ctx ctx) {
    }

    @Override
    public void onActivityCreated(Ctx ctx) {
    }

    @Override
    public void onActivityStarted(Ctx ctx) {
    }

    @Override
    public void onActivityResumed(Ctx ctx) {
    }

    @Override
    public void onActivityPaused(Ctx ctx) {
    }

    @Override
    public void onActivityStopped(Ctx ctx) {
    }

    @Override
    public void onActivitySaveInstanceState(Ctx ctx) {
    }

    @Override
    public void onActivityDestroyed(Ctx ctx) {
    }

    @Override
    public void onSessionBegan(Session session, Ctx ctx) {
    }

    @Override
    public void onSessionEnded(Session session, Ctx ctx) {
    }

    @Override
    public void onUserChanged(Ctx ctx, JSONObject changes, Set<String> cohortsAdded, Set<String> cohortsRemoved){
    }

    @Override
    public Boolean onRequest (Request request) { return false; }

    @Override
    public void onConfigurationChanged(Ctx ctx) {}

}
