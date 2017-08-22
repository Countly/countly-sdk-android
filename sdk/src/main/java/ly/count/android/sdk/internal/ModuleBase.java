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
    public void onDeviceId(Config.DID deviceId, Config.DID oldDeviceId) {
    }

    @Override
    public void clear(InternalConfig config) {
    }

    @Override
    public void onContextAcquired(Context context) {
    }

    @Override
    public void onActivityCreated(Context context) {
    }

    @Override
    public void onActivityStarted(Context context) {
    }

    @Override
    public void onActivityResumed(Context context) {
    }

    @Override
    public void onActivityPaused(Context context) {
    }

    @Override
    public void onActivityStopped(Context context) {
    }

    @Override
    public void onActivitySaveInstanceState(Context context) {
    }

    @Override
    public void onActivityDestroyed(Context context) {
    }

    @Override
    public void onSessionBegan(Session session, Context context) {
    }

    @Override
    public void onSessionEnded(Session session, Context context) {
    }
}
