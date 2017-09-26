package ly.count.android.sdk.internal;

import android.app.Activity;
import android.app.Application;

/**
 * Interface for {@link Module} calls uncoupling from Android SDK.
 * Contract:
 * <ul>
 *     <li>Context cannot be saved in a module, it expires as soon as method returns</li>
 * </ul>
 */

interface Context {
    Application getApplication();
    Activity getActivity();
    android.content.Context getContext();
    boolean isExpired();
}
