package ly.count.sdk.internal;

import java.util.HashMap;
import java.util.Map;

import ly.count.sdk.Config;
import ly.count.sdk.Session;
import ly.count.sdk.View;
import ly.count.sdk.internal.InternalConfig;

/**
 * Views support
 */

public class ModuleViews extends ModuleBase {
    private Map<Object, View> views = null;

    @Override
    public void init(InternalConfig config) {
        super.init(config);
        views = new HashMap<>();
    }

    @Override
    public void stop(Ctx ctx, boolean clear) {
        super.stop(ctx, clear);
        views = null;
    }

    protected View startView(String name) {

    }

    /**
     * When new {@link android.app.Activity} started, starts new {@link View} with name
     * set as {@link android.app.Activity} class name.
     */
    @Override
    public void onActivityStarted(Ctx context) {
        Ctx ctx = (Ctx) context;
        Session session = SDK.instance.getSession();
        if (session != null && ctx.getConfig().isAutoViewsTrackingEnabled()) {
            views.put(ctx.getActivity().hashCode(), session.view(ctx.getActivity().getClass().getName()));
        }
    }

    /**
     * When {@link android.app.Activity} stopped, stops previously started {@link View}.
     */
    @Override
    public void onActivityStopped(Ctx ctx) {
        int cls = ctx.getActivity().hashCode();
        if (views.containsKey(cls)) {
            views.remove(cls).stop(false);
        }
    }

    @Override
    public Integer getFeature() {
        return Config.Feature.AutoViewTracking;
    }
}
