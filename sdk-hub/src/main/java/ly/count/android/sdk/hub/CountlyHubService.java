package ly.count.android.sdk.hub;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * The hub service. The host app, the one app that has network access, runs the hub by declaring a
 * subclass of it that returns the hub's configuration:
 * <pre>
 * public class ConnectivityHubService extends CountlyHubService {
 *     protected HubConfig onCreateHubConfig() {
 *         return new HubConfig("https://countly.example.com")
 *             .allowApp(new HubAllowedApp("com.example.navigation", NAVIGATION_APP_KEY));
 *     }
 * }
 * </pre>
 * and by declaring that subclass in its manifest, exported, with the hub's intent action:
 * <pre>
 * &lt;service android:name=".ConnectivityHubService" android:exported="true"&gt;
 *   &lt;intent-filter&gt;
 *     &lt;action android:name="ly.count.android.sdk.hub.action.BIND"/&gt;
 *   &lt;/intent-filter&gt;
 * &lt;/service&gt;
 * </pre>
 * The library does not declare the service itself, so no app that only uses the hub client exposes
 * one by accident.
 * <p>
 * Every call is checked against the allowed apps by the caller's identity as the system reports it,
 * before anything in the request is read. Requests of allowed apps are relayed to the configured
 * server, one at a time over one connection, and the server's response goes back to the app.
 * "adb shell dumpsys activity service &lt;host package&gt;/&lt;service class&gt;" prints what the hub did
 * for each app.
 */
public abstract class CountlyHubService extends Service {
    private static final String TAG = "CountlyHub";

    private HubRelay relay;
    private CallerVerifier callerVerifier;
    private int maxRequestBytes;

    private final ICountlyHub.Stub binder = new ICountlyHub.Stub() {
        @Override
        public int getProtocolVersion() {
            return HubProtocol.VERSION;
        }

        @Override
        public Bundle exchange(Bundle request) {
            return handle(Binder.getCallingUid(), request);
        }
    };

    /**
     * Supplies the hub's configuration. It is called once, when the service is created.
     *
     * @return the configuration
     */
    protected abstract @NonNull HubConfig onCreateHubConfig();

    /**
     * Sets the hub up from the configuration.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        HubConfig config = onCreateHubConfig();
        synchronized (config) {
            maxRequestBytes = config.maxRequestBytes;
            HubUplink uplink = config.uplink != null
                ? config.uplink
                : new HttpsUplink(config.serverUrl, config.sslSocketFactory, config.uplinkTimeoutMillis, config.maxResponseBytes);
            relay = new HubRelay(new RequestGate(config.allowedPaths, config.maxRequestBytes), uplink, config.maxQueuedRequests);
            callerVerifier = new CallerVerifier(getPackageManager(), config.allowedApps);
        }
    }

    /**
     * @param intent the binding intent
     * @return the hub's binder
     */
    @Override
    public @Nullable IBinder onBind(Intent intent) {
        return binder;
    }

    /**
     * Stops the uplink. Requests still waiting fail, and the apps' SDKs retry them later.
     */
    @Override
    public void onDestroy() {
        if (relay != null) {
            relay.shutdown();
        }
        super.onDestroy();
    }

    /**
     * Prints what the hub did for each app.
     *
     * @param fd the output descriptor
     * @param writer where to print
     * @param args the dump arguments, unused
     */
    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        if (relay != null) {
            relay.dump(writer);
        }
    }

    /**
     * Handles one call: identifies the caller, reads its request, and relays it.
     *
     * @param callingUid the caller's user id as the system reports it
     * @param request the request bundle
     * @return the reply bundle
     */
    @NonNull Bundle handle(int callingUid, @Nullable Bundle request) {
        HubAllowedApp app = callerVerifier.resolve(callingUid);
        if (app == null) {
            relay.stats.onUnknownCaller(callingUid);
            Log.w(TAG, "Refused a request from uid " + callingUid + ", it is not an allowed app");
            return HubBundles.responseToBundle(HubRelay.refusal(403, "this app is not allowed to use the hub"));
        }
        if (request == null) {
            relay.stats.onRefused(app.packageName, "empty request");
            return HubBundles.responseToBundle(HubRelay.refusal(400, "empty request"));
        }

        HubRequest hubRequest;
        try {
            hubRequest = HubBundles.requestFromBundle(request, maxRequestBytes);
        } catch (HubRejection rejection) {
            relay.stats.onRefused(app.packageName, rejection.getMessage());
            Log.w(TAG, "Refused a request from " + app.packageName + ", " + rejection.getMessage());
            return HubBundles.responseToBundle(HubRelay.refusal(rejection.status, rejection.getMessage()));
        } catch (RuntimeException e) {
            relay.stats.onRefused(app.packageName, "malformed request");
            Log.w(TAG, "Refused a malformed request from " + app.packageName);
            return HubBundles.responseToBundle(HubRelay.refusal(400, "malformed request"));
        }

        try {
            HubResponse response = relay.relay(app, hubRequest);
            return HubBundles.responseToBundle(response);
        } catch (IOException e) {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            Log.w(TAG, "Could not deliver a request from " + app.packageName + ", " + reason);
            return HubBundles.failureToBundle(reason);
        }
    }
}
