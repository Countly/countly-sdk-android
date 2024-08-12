package ly.count.android.sdk;

public class ConfigContent {

    int contentUpdateInterval = 30;

    boolean contentUpdatesEnabled = false;

    /**
     * Set the interval for the automatic content update calls
     *
     * @param contentUpdateInterval in seconds
     */
    public synchronized ConfigContent setContentUpdateInterval(int contentUpdateInterval) {
        if (contentUpdateInterval > 0) {
            this.contentUpdateInterval = contentUpdateInterval;
        }
        return this;
    }

    /**
     * Enable periodic content updates
     */
    public synchronized ConfigContent enableContentUpdates() {
        contentUpdatesEnabled = true;
        return this;
    }
}
