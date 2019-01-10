package ly.count.sdk.internal;

import org.json.JSONObject;

import java.util.Set;

import ly.count.sdk.ConfigCore;
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
    public void onDeviceId(CtxCore ctx, ConfigCore.DID deviceId, ConfigCore.DID oldDeviceId) {
    }

    @Override
    public void stop(CtxCore ctx, boolean clear) {
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void onContextAcquired(CtxCore ctx) {
    }

    @Override
    public void onLimitedContextAcquired(CtxCore ctx) {
    }

    @Override
    public void onActivityCreated(CtxCore ctx) {
    }

    @Override
    public void onActivityStarted(CtxCore ctx) {
    }

    @Override
    public void onActivityResumed(CtxCore ctx) {
    }

    @Override
    public void onActivityPaused(CtxCore ctx) {
    }

    @Override
    public void onActivityStopped(CtxCore ctx) {
    }

    @Override
    public void onActivitySaveInstanceState(CtxCore ctx) {
    }

    @Override
    public void onActivityDestroyed(CtxCore ctx) {
    }

    @Override
    public void onSessionBegan(Session session, CtxCore ctx) {
    }

    @Override
    public void onSessionEnded(Session session, CtxCore ctx) {
    }

    @Override
    public void onUserChanged(CtxCore ctx, JSONObject changes, Set<String> cohortsAdded, Set<String> cohortsRemoved){
    }

    @Override
    public Boolean onRequest (Request request) { return false; }

    @Override
    public void onConfigurationChanged(CtxCore ctx) {}

}
