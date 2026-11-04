package ly.count.android.demo.hub.host;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/**
 * Shows where the demo hub relays to. The hub itself is the service, which the clients start by
 * binding to it; "adb shell dumpsys activity service ly.count.android.demo.hub.host/.HostHubService"
 * prints what it relayed.
 */
public class HostActivity extends Activity {
    /**
     * @param savedInstanceState unused
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView text = new TextView(this);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        text.setPadding(padding, padding, padding, padding);
        text.setText("Countly hub host\n\nRelays to: " + BuildConfig.HUB_SERVER_URL
            + "\n\nAllowed apps:\n" + HostHubService.CLIENT_PACKAGE + ".nav\n" + HostHubService.CLIENT_PACKAGE + ".music"
            + "\n\nadb shell dumpsys activity service " + getPackageName() + "/.HostHubService");
        setContentView(text);
    }
}
