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
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import androidx.annotation.NonNull;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The app's side of the binder connection to the hub service. It binds on the first request, keeps
 * the binding for later ones, waits for the service when it is still starting, and binds again after
 * the hub process went away.
 * <p>
 * Requests block their calling thread until the hub replies, so they must come from background
 * threads, which is where the SDK sends from.
 */
final class HubChannel implements HubTransport {
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
     * Hands a request to the hub service and waits for its reply. A body larger than
     * {@link HubProtocol#INLINE_BODY_LIMIT} is passed through a temporary file in the app's cache
     * directory that is deleted once the hub replied.
     *
     * @param request the request as the SDK built it
     * @return the response the hub handed back
     * @throws IOException if the hub could not be reached or could not get a response from the server
     */
    @Override
    public @NonNull HubResponse exchange(@NonNull HubRequest request) throws IOException {
        ICountlyHub hub = obtainService();
        boolean inline = request.getBodyLength() <= HubProtocol.INLINE_BODY_LIMIT;
        Bundle bundle = HubBundles.requestToBundle(request, inline);
        File bodyFile = null;
        ParcelFileDescriptor bodyDescriptor = null;
        try {
            if (!inline) {
                bodyFile = writeBodyFile(request.getBody());
                bodyDescriptor = ParcelFileDescriptor.open(bodyFile, ParcelFileDescriptor.MODE_READ_ONLY);
                bundle.putParcelable(HubProtocol.KEY_BODY_FILE, bodyDescriptor);
            }
            return HubBundles.responseFromBundle(hub.exchange(bundle));
        } catch (DeadObjectException e) {
            onServiceDied(hub);
            throw new IOException("The hub process went away", e);
        } catch (RemoteException | RuntimeException e) {
            throw new IOException("The hub could not take the request", e);
        } finally {
            if (bodyDescriptor != null) {
                try {
                    bodyDescriptor.close();
                } catch (IOException ignored) {
                }
            }
            if (bodyFile != null && !bodyFile.delete()) {
                bodyFile.deleteOnExit();
            }
        }
    }

    /**
     * Returns the hub service, binding to it first when there is no binding yet, and waiting up to the
     * configured bind timeout for it to connect.
     *
     * @return the service
     * @throws IOException if the hub cannot be bound or does not connect in time
     */
    private @NonNull ICountlyHub obtainService() throws IOException {
        if (Build.VERSION.SDK_INT < 29 && Looper.myLooper() == Looper.getMainLooper()) {
            throw new IOException("The hub cannot be reached from the main thread");
        }
        CountDownLatch ready;
        synchronized (lock) {
            if (service != null) {
                return service;
            }
            if (connection == null) {
                bindLocked();
            }
            ready = serviceReady;
        }
        try {
            ready.await(config.bindTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while waiting for the hub service");
        }
        synchronized (lock) {
            if (service != null) {
                return service;
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
     * @param body the body to pass through a file
     * @return a temporary file in the cache directory holding the body
     * @throws IOException if the file cannot be written
     */
    private @NonNull File writeBodyFile(byte[] body) throws IOException {
        File file = File.createTempFile("countly-hub-", ".body", config.context.getCacheDir());
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(body);
        }
        return file;
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
