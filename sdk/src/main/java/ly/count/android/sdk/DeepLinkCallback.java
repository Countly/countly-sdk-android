package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Tells the app where a link wants to take the user. Set it with
 * {@link CountlyConfig#setDeepLinkCallback(DeepLinkCallback)}.
 */
public interface DeepLinkCallback {
    enum Type {
        /**
         * The app was opened by a link: automatically from the launch intent, or through
         * {@code attribution().recordDeepLink(Uri)}.
         */
        OPENED,
        /**
         * First launch after an install from a link: the install referrer carried a {@code dl} deep link.
         */
        DEFERRED,
        /**
         * First launch, and no link opened the app and the install referrer carried no deep link.
         */
        NONE
    }

    /**
     * @param type what happened
     * @param path where to route: the {@code dl} parameter when present, otherwise for a Countly smart link
     * ({@code /at/[<app id>/]<code>/<path>}) the path after the campaign code, otherwise the link's path.
     * Null when there is nothing to route to (always null for {@link Type#NONE}).
     * @param url the full link that opened the app for {@link Type#OPENED}, the raw install referrer for
     * {@link Type#DEFERRED}, null for {@link Type#NONE}
     */
    void onDeepLink(@NonNull Type type, @Nullable String path, @Nullable String url);
}
