package ly.count.android.sdk.hub;

import android.app.Service;
import android.content.Intent;
import android.net.TrafficStats;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.Semaphore;

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
 * before anything in the request is read. A call only admits a request and returns at once; the
 * request is then sent to the configured server, one at a time over one connection, and the server's
 * response goes back to the app through a one-way callback. No thread of the host app waits while a
 * request waits for the network. The uplink thread tags its sockets, so a StrictMode policy that detects
 * untagged sockets does not flag them, and the host app can tell the hub's traffic apart.
 * "adb shell dumpsys activity service &lt;host package&gt;/&lt;service class&gt;" prints what the hub did
 * for each app.
 */
public abstract class CountlyHubService extends Service {
    /**
     * The traffic stats tag of the hub's uplink sockets.
     */
    public static final int UPLINK_SOCKET_TAG = 0x434C4842;

    private static final String TAG = "CountlyHub";
    private static final int MAX_CONCURRENT_LARGE_BODY_READS = 2;

    private HubRelay relay;
    private CallerVerifier callerVerifier;
    private int maxRequestBytes;
    private boolean logging;
    private final Semaphore largeBodyReads = new Semaphore(MAX_CONCURRENT_LARGE_BODY_READS);

    private final ICountlyHub.Stub binder = new ICountlyHub.Stub() {
        @Override
        public int getProtocolVersion() {
            return HubProtocol.VERSION;
        }

        @Override
        public Bundle submit(Bundle request, IHubCallback callback) {
            return handle(Binder.getCallingUid(), request, callback);
        }

        /**
         * Refuses a request from an app that is not allowed before anything it sent is read, so that
         * what it attached, such as file descriptors, is released with the call rather than held until
         * garbage collection.
         *
         * @param code the transaction code
         * @param data the call's arguments
         * @param reply where the result goes
         * @param flags the call's flags
         * @return true when the call was handled
         * @throws RemoteException never for a refused call
         */
        @Override
        public boolean onTransact(int code, @NonNull Parcel data, @Nullable Parcel reply, int flags) throws RemoteException {
            if (code == TRANSACTION_submit && reply != null && callerVerifier != null) {
                int callingUid = Binder.getCallingUid();
                if (callerVerifier.resolve(callingUid) == null) {
                    reply.writeNoException();
                    reply.writeInt(1);
                    unknownCallerRefusal(callingUid).writeToParcel(reply, Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
                    return true;
                }
            }
            return super.onTransact(code, data, reply, flags);
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
            logging = config.loggingEnabled;
            HubUplink uplink = config.uplink != null
                ? config.uplink
                : new HttpsUplink(config.serverUrl, config.sslSocketFactory, config.uplinkTimeoutMillis, config.maxResponseBytes);
            RequestGate gate = new RequestGate(config.allowedPaths, config.maxRequestBytes);
            HubRelay.Limits limits = new HubRelay.Limits(config.maxQueuedRequests, config.maxQueuedRequestsPerApp, config.maxQueuedBodyBytes, config.uplinkTimeoutMillis,
                HubRelay.DEFAULT_BACKGROUND_MAX_WAIT_MILLIS);
            relay = new HubRelay(gate, uplink, limits, CountlyHubService::newUplinkThread);
            callerVerifier = new CallerVerifier(getPackageManager(), config.allowedApps);
            for (HubAllowedApp app : config.allowedApps.values()) {
                if (app.signingCertificateSha256 == null) {
                    Log.w(TAG, "Allowed app " + app.packageName + " has no pinned signing certificate, so any app installed under that package name would be accepted; call HubAllowedApp.setSigningCertificateSha256");
                } else if (HubSignatures.parseHex(app.signingCertificateSha256) == null) {
                    Log.w(TAG, "Allowed app " + app.packageName + " has a signing certificate that is not a SHA-256 digest, so it will never match and the app will always be refused");
                }
            }
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
     * Creates the uplink thread, which tags the sockets it opens.
     *
     * @param runnable the uplink loop
     * @return the thread
     */
    static @NonNull Thread newUplinkThread(@NonNull Runnable runnable) {
        return new Thread(() -> {
            TrafficStats.setThreadStatsTag(UPLINK_SOCKET_TAG);
            runnable.run();
        }, "countly-hub-uplink");
    }

    /**
     * Handles one call: identifies the caller, reads its request and admits it to the relay.
     *
     * @param callingUid the caller's user id as the system reports it
     * @param request the request bundle
     * @param callback where the response goes once the request was admitted
     * @return the reply: an admission, a refusal or a failure
     */
    @NonNull Bundle handle(int callingUid, @Nullable Bundle request, @Nullable IHubCallback callback) {
        HubAllowedApp app = callerVerifier.resolve(callingUid);
        if (app == null) {
            return unknownCallerRefusal(callingUid);
        }
        if (request == null || callback == null) {
            relay.stats.onRefused(app.packageName, "empty request");
            return refusalToBundle(HubRelay.refusal(400, "empty request", true));
        }

        HubRequest hubRequest;
        boolean largeBodyRead = false;
        try {
            if (HubBundles.hasOutOfLineBody(request)) {
                if (!largeBodyReads.tryAcquire()) {
                    HubBundles.closeBody(request);
                    relay.stats.onBusy(app.packageName);
                    warn("Turned away a request from " + app.packageName + ", other large requests are being read");
                    return HubBundles.failureToBundle("the hub is busy");
                }
                largeBodyRead = true;
            }
            hubRequest = HubBundles.requestFromBundle(request, maxRequestBytes);
        } catch (HubRejection rejection) {
            relay.stats.onRefused(app.packageName, rejection.getMessage());
            warn("Refused a request from " + app.packageName + ", " + rejection.getMessage());
            return refusalToBundle(HubRelay.refusal(rejection.status, rejection.getMessage(), rejection.finalRefusal));
        } catch (IOException e) {
            relay.stats.onBusy(app.packageName);
            warn("Turned away a request from " + app.packageName + ", its body could not be read");
            return HubBundles.failureToBundle("the request body could not be read");
        } catch (RuntimeException e) {
            relay.stats.onRefused(app.packageName, "malformed request");
            warn("Refused a malformed request from " + app.packageName);
            return refusalToBundle(HubRelay.refusal(400, "malformed request", true));
        } catch (OutOfMemoryError e) {
            relay.stats.onBusy(app.packageName);
            warn("Turned away a request from " + app.packageName + ", not enough memory to read it");
            return HubBundles.failureToBundle("the hub is busy");
        } finally {
            if (largeBodyRead) {
                largeBodyReads.release();
            }
        }

        try {
            HubResponse refusal = relay.submit(app, hubRequest, new CallbackCompletion(app, callback));
            if (refusal != null) {
                warn("Refused a request from " + app.packageName + ", status " + refusal.getStatus());
                return refusalToBundle(refusal);
            }
            return HubBundles.acceptedToBundle(relay.replyWithinMillis(hubRequest));
        } catch (IOException e) {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            warn("Turned away a request from " + app.packageName + ", " + reason);
            return HubBundles.failureToBundle(reason);
        }
    }

    /**
     * Counts and logs a call from an app that is not allowed, and makes its refusal. The refusal is not
     * final: the app may be allowed by a later configuration of the hub.
     *
     * @param callingUid the caller's user id
     * @return the refusal
     */
    private @NonNull Bundle unknownCallerRefusal(int callingUid) {
        relay.stats.onUnknownCaller(callingUid);
        warn("Refused a request from uid " + callingUid + ", it is not an allowed app");
        return refusalToBundle(HubRelay.refusal(403, "this app is not allowed to use the hub", false));
    }

    /**
     * Puts a refusal the hub made up into a reply. Refusals are small, so the body always travels inline
     * and nothing needs releasing after the reply is sent.
     *
     * @param refusal the refusal
     * @return the reply bundle
     */
    private @NonNull Bundle refusalToBundle(@NonNull HubResponse refusal) {
        try {
            return HubBundles.responseToBundle(refusal, getCacheDir()).bundle;
        } catch (IOException e) {
            return HubBundles.failureToBundle("the request was refused");
        }
    }

    /**
     * Logs a per-request warning when the hub's logging is turned on.
     *
     * @param message the warning
     */
    private void warn(@NonNull String message) {
        if (logging) {
            Log.w(TAG, message);
        }
    }

    /**
     * Passes the outcome of an admitted request back to the app through its one-way callback.
     */
    private final class CallbackCompletion implements HubRelay.Completion {
        private final HubAllowedApp app;
        private final IHubCallback callback;

        /**
         * @param app the app the request came from
         * @param callback the app's callback
         */
        CallbackCompletion(@NonNull HubAllowedApp app, @NonNull IHubCallback callback) {
            this.app = app;
            this.callback = callback;
        }

        /**
         * Sends the response, or the failure, to the app. A large body goes in shared memory or a file
         * descriptor, which is released once the one-way call has been handed to the system. An app that
         * went away in the meantime is ignored; its SDK retries the request when it runs again.
         *
         * @param response the server's response, or null
         * @param failure why there is no response, or null
         */
        @Override
        public void complete(@Nullable HubResponse response, @Nullable String failure) {
            if (failure != null) {
                warn("Could not deliver a request from " + app.packageName + ", " + failure);
            }
            HubBundles.Outgoing outgoing = null;
            try {
                Bundle bundle;
                if (response != null) {
                    outgoing = HubBundles.responseToBundle(response, getCacheDir());
                    bundle = outgoing.bundle;
                } else {
                    bundle = HubBundles.failureToBundle(failure == null ? "no response" : failure);
                }
                callback.onResponse(bundle);
            } catch (RemoteException ignored) {
            } catch (IOException | RuntimeException e) {
                try {
                    callback.onResponse(HubBundles.failureToBundle("the response could not be passed on"));
                } catch (RemoteException | RuntimeException ignored) {
                }
            } finally {
                if (outgoing != null) {
                    try {
                        outgoing.release.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }
}
