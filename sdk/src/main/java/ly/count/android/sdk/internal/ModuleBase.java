package ly.count.android.sdk.internal;

import ly.count.android.sdk.Config;
import ly.count.android.sdk.Session;

/**
 * Created by artem on 05/01/2017.
 */

abstract class ModuleBase implements Module {
    @Override
    public void init(InternalConfig config) {
    }

    @Override
    public void onDeviceId(Context ctx, Config.DID deviceId, Config.DID oldDeviceId) {
    }

    @Override
    public void clear(InternalConfig config) {
    }

    @Override
    public void onContextAcquired(Context ctx) {
    }

    @Override
    public void onLimitedContextAcquired(Context ctx) {
    }

    @Override
    public void onActivityCreated(Context ctx) {
    }

    @Override
    public void onActivityStarted(Context ctx) {
    }

    @Override
    public void onActivityResumed(Context ctx) {
    }

    @Override
    public void onActivityPaused(Context ctx) {
    }

    @Override
    public void onActivityStopped(Context ctx) {
    }

    @Override
    public void onActivitySaveInstanceState(Context ctx) {
    }

    @Override
    public void onActivityDestroyed(Context ctx) {
    }

    @Override
    public void onSessionBegan(Session session, Context ctx) {
    }

    @Override
    public void onSessionEnded(Session session, Context ctx) {
    }

    @Override
    public Boolean onRequest (Request request) { return false; }

}
