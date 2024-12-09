package ly.count.android.sdk;

public class ConfigContent {

    int zoneTimerInterval = 30;
    ContentCallback globalContentCallback = null;

    /**
     * Set the interval for the automatic content update calls
     *
     * @param zoneTimerIntervalSeconds in seconds
     * @return config content to chain calls
     * @apiNote This is an EXPERIMENTAL feature, and it can have breaking changes
     */
    public synchronized ConfigContent setZoneTimerInterval(int zoneTimerIntervalSeconds) {
        if (zoneTimerIntervalSeconds > 15) {
            this.zoneTimerInterval = zoneTimerIntervalSeconds;
        }
        return this;
    }

    /**
     * Listen for content updates
     *
     * @param callback to be called when content is updated
     * @return config content to chain calls
     * @apiNote This is an EXPERIMENTAL feature, and it can have breaking changes
     */
    public synchronized ConfigContent setGlobalContentCallback(ContentCallback callback) {
        this.globalContentCallback = callback;
        return this;
    }
}
