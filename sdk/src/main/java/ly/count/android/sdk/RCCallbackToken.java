package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.List;

/**
 * Handle to one remote config download callback registered with
 * {@link ModuleRemoteConfig.RemoteConfig#registerDownloadCallbackAndReplay(RCDownloadCallback)}.
 */
public final class RCCallbackToken {
    private final ModuleLog L;
    private final List<RCDownloadCallback> callbacks;
    private RCDownloadCallback callback;
    private boolean added = false;
    private boolean removed = false;

    /**
     * Creates the token of a registration whose callback is not added yet.
     *
     * @param L the log of the instance the callback is registered with
     * @param callbacks the list the callback is added to and removed from
     * @param callback the callback, or null for a token that registers and removes nothing
     */
    RCCallbackToken(@NonNull ModuleLog L, @NonNull List<RCDownloadCallback> callbacks, @Nullable RCDownloadCallback callback) {
        this.L = L;
        this.callbacks = callbacks;
        this.callback = callback;
    }

    /**
     * Adds the callback to the list, unless "remove()" was called first.
     */
    synchronized void add() {
        if (removed) {
            return;
        }
        callbacks.add(callback);
        added = true;
    }

    /**
     * @return true once "remove()" was called
     */
    synchronized boolean isRemoved() {
        return removed;
    }

    /**
     * Removes the callback this token was returned for, so later downloads no longer call it. Called before the
     * callback was registered, it stops the registration. Calling it again does nothing.
     */
    public synchronized void remove() {
        L.i("[RemoteConfig] RCCallbackToken, remove");
        if (removed) {
            return;
        }
        removed = true;
        if (added) {
            callbacks.remove(callback);
        }
        //an app can keep the token after removing it, and the callback may hold on to a screen
        callback = null;
    }
}
