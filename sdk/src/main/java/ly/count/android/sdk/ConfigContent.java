package ly.count.android.sdk;

public class ConfigContent {

    int contentUpdateInterval = 30;
    boolean contentUpdatesEnabled = false;
    ContentCallback globalContentCallback = null;

    /**
     * Set the interval for the automatic content update calls
     *
     * @param contentUpdateInterval in seconds
     * @return config content to chain calls
     */
    public synchronized ConfigContent setContentUpdateInterval(int contentUpdateInterval) {
        if (contentUpdateInterval > 0) {
            this.contentUpdateInterval = contentUpdateInterval;
        }
        return this;
    }

    /**
     * Enable periodic content updates
     *
     * @return config content to chain calls
     */
    public synchronized ConfigContent enableContentUpdates() {
        contentUpdatesEnabled = true;
        return this;
    }

    /**
     * Listen for content updates
     *
     * @param callback to be called when content is updated
     * @return config content to chain calls
     */
    public synchronized ConfigContent setGlobalContentCallback(ContentCallback callback) {
        this.globalContentCallback = callback;
        return this;
    }
}