package ly.count.android.sdk.messaging;

import android.content.Context;

public interface CountlyNotificationButtonURLHandler {
    /**
     * Called when a notification button is clicked.
     *
     * @param url The URL associated with the button.
     * @param context The context in which the button was clicked.
     * @return true if the URL was handled, false otherwise.
     */
    boolean onClick(String url, Context context);
}
