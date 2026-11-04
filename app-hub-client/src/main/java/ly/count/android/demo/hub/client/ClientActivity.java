package ly.count.android.demo.hub.client;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;
import ly.count.android.sdk.Countly;

/**
 * Records events, sends them through the hub, and shows that the app cannot open a connection itself.
 * <p>
 * The buttons can also be driven from adb, with "--es action record", "--es action send",
 * "--es action burst" or "--es action direct" on the start intent, for example:
 * "adb shell am start -n ly.count.android.demo.hub.client.nav/ly.count.android.demo.hub.client.ClientActivity --es action record".
 */
public class ClientActivity extends Activity {
    private static final int BURST_SIZE = 20;

    private TextView log;

    /**
     * Builds the screen and runs the action of the start intent, if any.
     *
     * @param savedInstanceState unused
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText(getPackageName() + "\napp key " + BuildConfig.APP_KEY + "\nhub " + BuildConfig.HUB_PACKAGE);
        root.addView(title);
        root.addView(button("Record an event and send", v -> record()));
        root.addView(button("Send stored requests", v -> send()));
        root.addView(button("Download remote config " + BURST_SIZE + " times at once", v -> burst()));
        root.addView(button("Try a direct internet connection", v -> tryDirectConnection()));

        log = new TextView(this);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(log);
        root.addView(scroll);
        setContentView(root);

        runAction(getIntent());
    }

    /**
     * Runs the action of an intent that reaches the already running activity.
     *
     * @param intent the new intent
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        runAction(intent);
    }

    /**
     * @param intent an intent that may carry an "action" extra
     */
    private void runAction(Intent intent) {
        String action = intent == null ? null : intent.getStringExtra("action");
        if ("record".equals(action)) {
            record();
        } else if ("send".equals(action)) {
            send();
        } else if ("burst".equals(action)) {
            burst();
        } else if ("direct".equals(action)) {
            tryDirectConnection();
        }
    }

    /**
     * Records an event named after the app and asks the SDK to send it right away.
     */
    private void record() {
        Countly.sharedInstance().events().recordEvent(BuildConfig.FLAVOR + "_demo_event");
        send();
        append("recorded " + BuildConfig.FLAVOR + "_demo_event and asked the SDK to send");
    }

    /**
     * Asks the SDK to send everything it has stored.
     */
    private void send() {
        Countly.sharedInstance().requestQueue().attemptToSendStoredRequests();
    }

    /**
     * Downloads the remote config several times at once, the way an app starting up asks for several
     * answers in parallel, and logs how many requests were answered once all have finished.
     */
    private void burst() {
        final AtomicInteger answered = new AtomicInteger();
        final AtomicInteger finished = new AtomicInteger();
        for (int i = 0; i < BURST_SIZE; i++) {
            Countly.sharedInstance().remoteConfig().downloadAllKeys((result, error, fullValueUpdate, values) -> {
                if (error == null) {
                    answered.incrementAndGet();
                }
                if (finished.incrementAndGet() == BURST_SIZE) {
                    final String message = "burst: " + answered.get() + " of " + BURST_SIZE + " remote config requests answered";
                    runOnUiThread(() -> append(message));
                }
            });
        }
    }

    /**
     * Tries to open a socket to the internet on a background thread. Without the INTERNET permission
     * the system refuses to create it.
     */
    private void tryDirectConnection() {
        new Thread(() -> {
            String result;
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("example.com", 80), 5_000);
                result = "direct connection opened: this app has network access";
            } catch (Exception e) {
                result = "direct connection refused: " + e;
            }
            final String message = result;
            runOnUiThread(() -> append(message));
        }).start();
    }

    /**
     * @param label the button text
     * @param onClick what the button does
     * @return the button
     */
    private Button button(String label, View.OnClickListener onClick) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(onClick);
        return button;
    }

    /**
     * @param line the line to add to the on-screen log
     */
    private void append(String line) {
        log.append(line + "\n");
        Log.i("HubDemoClient", line);
    }
}
