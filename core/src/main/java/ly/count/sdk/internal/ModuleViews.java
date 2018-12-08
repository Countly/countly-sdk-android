package ly.count.sdk.internal;

import ly.count.sdk.Config;
import ly.count.sdk.Session;
import ly.count.sdk.View;

/**
 * Views support
 */

public class ModuleViews extends ModuleBase {

    /**
     * When new {@code Activity} started, starts new {@link View} with name
     * set as {@code Activity} class name.
     */
    @Override
    public void onActivityStarted(Ctx ctx) {
        Session session = SDKCore.instance.getSession();
        if (session != null && SDKCore.enabled(CoreFeature.Views) && ctx.getConfig().isAutoViewsTrackingEnabled()) {
            Class cls = ctx.getContext().getClass();
            session.view(cls.getSimpleName());
        }
    }

    /**
     * When {@code Activity} stopped, stops previously started {@link View}.
     */
    @Override
    public void onActivityStopped(Ctx ctx) {
        Session session = SDKCore.instance.getSession();
        if (session != null && SDKCore.enabled(CoreFeature.Views) && ctx.getConfig().isAutoViewsTrackingEnabled()) {
            Class cls = ctx.getContext().getClass();
            // TODO: no correct lastView here
            session.view(cls.getSimpleName()).stop(false);
        }
    }

    // TODO: questionable
    @Override
    public Integer getFeature() {
        return CoreFeature.Views.getIndex();
    }
}
