package ly.count.sdk.internal;

import java.util.HashMap;
import java.util.Map;

import ly.count.sdk.Session;
import ly.count.sdk.View;

/**
 * Views support
 */

public class ModuleViews extends ModuleBase {
    private Map<Integer, View> views = null;

    @Override
    public void init(InternalConfig config) {
        super.init(config);
        views = new HashMap<>();
    }

    /**
     * When new {@code Activity} started, starts new {@link View} with name
     * set as {@code Activity} class name.
     */
    @Override
    public void onActivityStarted(CtxCore ctx) {
        Session session = SDKCore.instance.getSession();
        if (session != null && SDKCore.enabled(CoreFeature.Views) && ctx.getConfig().isAutoViewsTrackingEnabled()) {
            Class cls = ctx.getContext().getClass();
            views.put(ctx.getContext().hashCode(), session.view(cls.getSimpleName()));
        }
    }

    /**
     * When {@code Activity} stopped, stops previously started {@link View}.
     */
    @Override
    public void onActivityStopped(CtxCore ctx) {
        Session session = SDKCore.instance.getSession();
        if (session != null && SDKCore.enabled(CoreFeature.Views) && ctx.getConfig().isAutoViewsTrackingEnabled()) {
            int cls = ctx.getContext().hashCode();
            if (views.containsKey(cls)) {
                views.remove(cls).stop(false);
            }
        }
    }

    // TODO: questionable
    @Override
    public Integer getFeature() {
        return CoreFeature.Views.getIndex();
    }
}
