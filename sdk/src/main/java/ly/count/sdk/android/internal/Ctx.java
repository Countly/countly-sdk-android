package ly.count.sdk.android.internal;

import android.app.Activity;
import android.app.Application;

public interface Ctx extends ly.count.sdk.internal.Ctx {
    @Override
    android.content.Context getContext();

    Application getApplication();

    Activity getActivity();
}
