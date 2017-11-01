package ly.count.android.sdk.internal;

import ly.count.android.sdk.Eve;
import ly.count.android.sdk.Session;

/**
 * Views support
 */

public class ModuleViews extends ModuleBase {
    public static final String EVENT = "[CLY]_view";
    public static final String NAME = "name";
    public static final String VISIT = "visit";
    public static final String VISIT_VALUE = "1";
    public static final String SEGMENT = "segment";
    public static final String SEGMENT_VALUE = "Android";
    public static final String START = "start";
    public static final String START_VALUE = "1";
    public static final String EXIT = "exit";
    public static final String EXIT_VALUE = "1";
    private Eve event = null;

    @Override
    public void onActivityStarted(Context ctx) {
        Session session = Core.instance.sessionLeading();
        if (session != null) {
            event = session.recordView(ctx.getActivity().getClass().getName(), false);
        }
    }

    @Override
    public void onActivityStopped(Context ctx) {
        if (event != null) {
            event.endAndRecord();
            event = null;
        }
    }
}
