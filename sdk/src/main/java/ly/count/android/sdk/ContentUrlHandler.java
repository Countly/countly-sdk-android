package ly.count.android.sdk;

public interface ContentUrlHandler {
    /**
     * Called when a link is opened from the content (or feedback) web view, letting the host app
     * take over instead of the SDK opening the link via an ACTION_VIEW intent. This is how an app
     * routes its own deep links (custom scheme or https) to the correct screen.
     *
     * @param url the URL the web content is trying to open
     * @return true if the app handled the URL; return false to let the SDK open it as usual
     */
    boolean onContentUrl(String url);
}
