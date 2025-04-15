package ly.count.android.sdk.messaging;

public interface CountlyNotificationButtonURLHandler {
    /**
     * Called when a notification button is clicked.
     *
     * @param url The URL associated with the button.
     * @return true if the URL was handled, false otherwise.
     */
    boolean onClick(String url);
}
