package ly.count.sdk.android.internal;

import org.json.JSONObject;

import java.util.Set;

import ly.count.sdk.ConfigCore;
import ly.count.sdk.Session;

/**
 * Created by artem on 05/01/2017.
 */

public abstract class ModuleBase extends ly.count.sdk.internal.ModuleBase {

    public void onDeviceId(Ctx ctx, ConfigCore.DID deviceId, ConfigCore.DID oldDeviceId) {
    }

    public void stop(Ctx ctx, boolean clear) {
    }

    public void onContextAcquired(Ctx ctx) {
    }

    public void onLimitedContextAcquired(Ctx ctx) {
    }

    public void onActivityCreated(Ctx ctx) {
    }

    public void onActivityStarted(Ctx ctx) {
    }

    public void onActivityResumed(Ctx ctx) {
    }

    public void onActivityPaused(Ctx ctx) {
    }

    public void onActivityStopped(Ctx ctx) {
    }

    public void onActivitySaveInstanceState(Ctx ctx) {
    }

    public void onActivityDestroyed(Ctx ctx) {
    }

    public void onSessionBegan(Session session, Ctx ctx) {
    }

    public void onSessionEnded(Session session, Ctx ctx) {
    }

    public void onUserChanged(Ctx ctx, JSONObject changes, Set<String> cohortsAdded, Set<String> cohortsRemoved){
    }

    public void onConfigurationChanged(Ctx ctx) {}

}
