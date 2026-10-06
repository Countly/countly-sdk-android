package ly.count.android.demo.hub.host;

import ly.count.android.sdk.hub.CountlyHubService;
import ly.count.android.sdk.hub.HubAllowedApp;
import ly.count.android.sdk.hub.HubConfig;

/**
 * The demo hub: relays to the configured server and accepts the "nav" and "music" demo clients, each
 * with its own app key. The "rogue" demo client is deliberately left out.
 */
public class HostHubService extends CountlyHubService {
    static final String CLIENT_PACKAGE = "ly.count.android.demo.hub.client";

    /**
     * @return the demo hub configuration
     */
    @Override
    protected HubConfig onCreateHubConfig() {
        return new HubConfig(BuildConfig.HUB_SERVER_URL)
            .allowApp(new HubAllowedApp(CLIENT_PACKAGE + ".nav", BuildConfig.NAV_APP_KEY))
            .allowApp(new HubAllowedApp(CLIENT_PACKAGE + ".music", BuildConfig.MUSIC_APP_KEY));
    }
}
