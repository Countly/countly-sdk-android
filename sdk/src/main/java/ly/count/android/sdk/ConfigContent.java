package ly.count.android.sdk;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConfigContent {

    int zoneTimerInterval = 30;
    ContentCallback globalContentCallback = null;
    Set<String> allowedIntentSchemes = new HashSet<>();

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

    /**
     * Set the URI schemes that content (and feedback widget) overlay links are allowed to open via
     * ACTION_VIEW. When a non-empty list is provided, only links whose scheme is in the list are
     * opened. When left empty (the default), any scheme except known-dangerous ones ("file",
     * "content", "javascript", "jar", "data") is allowed, so http(s) and deep links keep working.
     * Schemes are matched case-insensitively.
     *
     * @param allowedIntentSchemes the URI schemes permitted for overlay links, for example ["https", "myapp"]
     * @return config content to chain calls
     */
    public synchronized ConfigContent setAllowedIntentSchemes(List<String> allowedIntentSchemes) {
        this.allowedIntentSchemes = Utils.normalizeSchemeSet(allowedIntentSchemes);
        return this;
    }
}
