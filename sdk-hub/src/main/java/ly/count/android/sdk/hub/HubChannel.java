package ly.count.android.sdk.hub;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The app's side of the binder connection to the hub service. It binds on the first request, keeps
 * the binding for later ones, waits for the service when it is still starting, and binds again after
 * the hub process went away or a binding did not connect in time.
 * <p>
 * A request is handed over with a short call that only admits it, and its response arrives later
 * through a one-way callback. The calling thread waits for that callback up to the time the hub said
 * the response may take, so requests must come from background threads, which is where the SDK sends
 * from. A request whose hub process goes away fails right away instead of waiting out that time.
 */
final class HubChannel implements HubTransport {
    /**
     * The largest response body the app accepts from the hub.
     */
    static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;

    private static final Executor CALLBACK_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "countly-hub-binding");
        thread.setDaemon(true);
        return thread;
    });

    private final HubClientConfig config;
    private final Object lock = new Object();
    private HubServiceConnection connection;
    private ICountlyHub service;
    private CountDownLatch serviceReady;

    /**
     * @param config where the hub runs
     */
    HubChannel(@NonNull HubClientConfig config) {
        this.config = config;
    }

    /**
     * Hands a request to the hub service and waits for its response. A body larger than
     * {@link HubProtocol#INLINE_BODY_LIMIT} travels in shared memory, or before Android 8.1 through the
     * descriptor of a file that is already deleted, so nothing is left behind.
     *
     * @param request the request as the SDK built it
     * @return the response the hub handed back, or the hub's own refusal
     * @throws IOException if the hub could not be reached, did not take the request, or could not get a
     * response from the server in time
     */
    @Override
    public @NonNull HubResponse exchange(@NonNull HubRequest request) throws IOException {
        ICountlyHub hub = obtainService();
        IBinder binder = hub.asBinder();
        PendingResponse pending = new PendingResponse();
        try {
            binder.linkToDeath(pending, 0);
        } catch (RemoteException e) {
            onServiceDied(hub);
            throw new IOException("The hub process went away", e);
        }
        try {
            Bundle reply = submit(hub, request, pending);
            if (!HubBundles.isAccepted(reply)) {
                return HubBundles.responseFromBundle(reply, MAX_RESPONSE_BYTES);
            }
            long replyWithinMillis = HubBundles.replyWithinMillis(reply);
            return pending.await(replyWithinMillis > 0 ? replyWithinMillis : request.getTimeoutMillis());
        } finally {
            try {
                binder.unlinkToDeath(pending, 0);
            } catch (RuntimeException ignored) {
            }
        }
    }

    /**
     * Makes the call that hands the request over. What carries a large body is released as soon as the
     * call returns, because the hub has read the body by then.
     *
     * @param hub the hub service
     * @param request the request
     * @param pending receives the response once the request was admitted
     * @return the hub's reply: an admission, a refusal or a failure
     * @throws IOException if the call could not be made
     */
    private @Nullable Bundle submit(@NonNull ICountlyHub hub, @NonNull HubRequest request, @NonNull PendingResponse pending) throws IOException {
        Bundle bundle = HubBundles.requestToBundle(request);
        Closeable body = HubBundles.putBody(bundle, request.getBody(), config.context.getCacheDir());
        try {
            return hub.submit(bundle, pending);
        } catch (DeadObjectException e) {
            onServiceDied(hub);
            throw new IOException("The hub process went away", e);
        } catch (RemoteException | RuntimeException e) {
            throw new IOException("The hub could not take the request", e);
        } finally {
            try {
                body.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Returns the hub service, binding to it first when there is no binding yet, and waiting up to the
     * configured bind timeout for it to connect. A binding that does not connect in time is dropped, so
     * the next request binds again instead of waiting on a binding that may never connect; before
     * Android 8 the system does not report such a binding.
     *
     * @return the service
     * @throws IOException if the hub cannot be bound or does not connect in time
     */
    private @NonNull ICountlyHub obtainService() throws IOException {
        if (Build.VERSION.SDK_INT < 29 && Looper.myLooper() == Looper.getMainLooper()) {
            throw new IOException("The hub cannot be reached from the main thread");
        }
        CountDownLatch ready;
        HubServiceConnection binding;
        synchronized (lock) {
            if (service != null) {
                return service;
            }
            if (connection == null) {
                bindLocked();
            }
            ready = serviceReady;
            binding = connection;
        }
        boolean signalled;
        try {
            signalled = ready.await(config.bindTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while waiting for the hub service");
        }
        synchronized (lock) {
            if (service != null) {
                return service;
            }
            if (!signalled && connection == binding) {
                unbindLocked();
            }
        }
        throw new IOException("The hub service in " + config.hubPackageName + " did not connect within " + config.bindTimeoutMillis + " ms");
    }

    /**
     * Binds to the hub service. On Android 10 and newer the connection callbacks arrive on a thread of
     * the channel's own, before that on the main thread, which is why requests may not wait for the
     * binding there.
     *
     * @throws IOException if the hub package is missing, signed with an unexpected certificate, or has no hub service
     */
    private void bindLocked() throws IOException {
        Context context = config.context;
        if (config.hubSigningCertificateSha256 != null
            && !HubSignatures.isSignedWith(context.getPackageManager(), config.hubPackageName, config.hubSigningCertificateSha256)) {
            throw new IOException("The hub package " + config.hubPackageName + " is missing or not signed with the expected certificate");
        }

        Intent intent = new Intent(HubProtocol.ACTION_BIND).setPackage(config.hubPackageName);
        HubServiceConnection newConnection = new HubServiceConnection();
        boolean bound;
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                bound = context.bindService(intent, Context.BIND_AUTO_CREATE, CALLBACK_EXECUTOR, newConnection);
            } else {
                bound = context.bindService(intent, newConnection, Context.BIND_AUTO_CREATE);
            }
        } catch (SecurityException e) {
            throw new IOException("This app is not allowed to bind to the hub service in " + config.hubPackageName, e);
        }
        if (!bound) {
            unbindQuietly(newConnection);
            throw new IOException("No hub service found in " + config.hubPackageName);
        }
        connection = newConnection;
        serviceReady = new CountDownLatch(1);
    }

    /**
     * Drops the binding after a call found the hub process gone, so that the next request binds again.
     *
     * @param hub the service the call was made on
     */
    private void onServiceDied(@NonNull ICountlyHub hub) {
        synchronized (lock) {
            if (service == hub) {
                unbindLocked();
            }
        }
    }

    /**
     * Drops a binding the system declared unusable.
     *
     * @param dead the connection the system reported on
     */
    private void drop(@NonNull HubServiceConnection dead) {
        synchronized (lock) {
            if (connection == dead) {
                unbindLocked();
            }
        }
    }

    /**
     * Unbinds and wakes up the requests waiting for the service, which then fail and are retried later.
     */
    private void unbindLocked() {
        if (connection != null) {
            unbindQuietly(connection);
        }
        connection = null;
        service = null;
        if (serviceReady != null) {
            serviceReady.countDown();
        }
        serviceReady = null;
    }

    /**
     * @param serviceConnection the connection to unbind, ignoring that it may not be bound
     */
    private void unbindQuietly(@NonNull ServiceConnection serviceConnection) {
        try {
            config.context.unbindService(serviceConnection);
        } catch (IllegalArgumentException ignored) {
        }
    }

    /**
     * Waits for the outcome of one admitted request: the response the hub sends through the one-way
     * callback, or the death of the hub process, whichever comes first. A response arriving after the
     * request stopped waiting is still read, so that the shared memory or file carrying its body is
     * closed.
     */
    private static final class PendingResponse extends IHubCallback.Stub implements IBinder.DeathRecipient {
        private final CountDownLatch done = new CountDownLatch(1);
        private boolean completed = false;
        private HubResponse response;
        private IOException failure;

        /**
         * Receives the response, or the reason there is none.
         *
         * @param bundle the response bundle
         */
        @Override
        public void onResponse(Bundle bundle) {
            try {
                complete(HubBundles.responseFromBundle(bundle, MAX_RESPONSE_BYTES), null);
            } catch (IOException e) {
                complete(null, e);
            } catch (RuntimeException e) {
                complete(null, new IOException("The hub sent a response that could not be read", e));
            }
        }

        /**
         * Fails the request when the hub process goes away before answering.
         */
        @Override
        public void binderDied() {
            complete(null, new IOException("The hub process went away before the request was answered"));
        }

        /**
         * Records the first outcome and wakes up the waiting request; later outcomes are ignored.
         *
         * @param received the response, or null
         * @param error the failure, or null
         */
        private synchronized void complete(@Nullable HubResponse received, @Nullable IOException error) {
            if (completed) {
                return;
            }
            completed = true;
            response = received;
            failure = error;
            done.countDown();
        }

        /**
         * @param timeoutMillis how long to wait for the outcome
         * @return the response
         * @throws IOException if the request failed, the hub process went away, or no outcome arrived in time
         */
        @NonNull HubResponse await(long timeoutMillis) throws IOException {
            boolean answered;
            try {
                answered = done.await(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Interrupted while waiting for the hub's response");
            }
            if (!answered) {
                throw new IOException("The hub did not answer within " + timeoutMillis + " ms");
            }
            synchronized (this) {
                if (failure != null) {
                    throw failure;
                }
                return response;
            }
        }
    }

    /**
     * Receives the binding callbacks. Callbacks of a connection that was replaced in the meantime are ignored.
     */
    private final class HubServiceConnection implements ServiceConnection {
        /**
         * Makes the service available to the waiting requests.
         *
         * @param name the service component
         * @param binder the service binder
         */
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            synchronized (lock) {
                if (connection != this) {
                    return;
                }
                service = ICountlyHub.Stub.asInterface(binder);
                serviceReady.countDown();
            }
        }

        /**
         * The hub process went away. The binding stays, and the system connects it again once the hub
         * is running again.
         *
         * @param name the service component
         */
        @Override
        public void onServiceDisconnected(ComponentName name) {
            synchronized (lock) {
                if (connection != this) {
                    return;
                }
                service = null;
                serviceReady = new CountDownLatch(1);
            }
        }

        /**
         * The binding can no longer connect, for example after the hub app was updated.
         *
         * @param name the service component
         */
        @Override
        public void onBindingDied(ComponentName name) {
            drop(this);
        }

        /**
         * The hub service refused the binding.
         *
         * @param name the service component
         */
        @Override
        public void onNullBinding(ComponentName name) {
            drop(this);
        }
    }
}
