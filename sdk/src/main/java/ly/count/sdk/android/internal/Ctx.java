package ly.count.sdk.android.internal;

import android.app.Activity;
import android.app.Application;

import ly.count.sdk.internal.CtxCore;

public interface Ctx extends CtxCore {
    @Override
    android.content.Context getContext();

    Application getApplication();

    Activity getActivity();
}
