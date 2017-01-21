package ly.count.android.sdk.internal;

/**
 * Sessions module responsible for default sessions handling: starting a session when
 * first {@link android.app.Activity} is started, stopping it when
 * last {@link android.app.Activity} is stopped and updating it each .
 */

public class ModuleSessions extends ModuleBase {
    int activityCount;

    /**
     * @throws IllegalArgumentException when programmaticSessionsControl is on since this module is
     * for a case when it's off
     */
    @Override
    public void init(InternalConfig config) throws IllegalArgumentException {
        super.init(config);
        if (config.isProgrammaticSessionsControl()) {
            throw new IllegalArgumentException("ModuleSessions must not be initialized when programmaticSessionsControl is on");
        }
    }

    @Override
    public void onActivityStarted(Context context) {
        super.onActivityStarted(context);
        if (activityCount == 0) {
            Core.instance.sessionBegin(Core.instance.sessionAdd());
        }
        activityCount++;
    }

    @Override
    public void onActivityStopped(Context context) {
        super.onActivityStopped(context);
        activityCount--;
        if (activityCount == 0 && Core.instance.sessionLeading() != null) {
            Core.instance.sessionRemove(Core.instance.sessionEnd(Core.instance.sessionLeading()));
        }
    }
}
