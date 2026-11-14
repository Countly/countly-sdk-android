package ly.count.android.sdk;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reads the install referrer of another store (HUAWEI AppGallery, Samsung Galaxy Store). Their libraries copy
 * Play's API and response codes; each subclass wraps its library in a {@link StoreClient}, so the handling of
 * the response codes is here once and tests drive it with a fake client.
 * <p>
 * The library is an optional dependency: {@link #isAvailable()} checks for it at runtime, and a subclass touches
 * its classes only in a nested class created after that check. On a device without the store nothing is asked.
 */
abstract class StoreInstallReferrerReader extends InstallReferrerReader {
    //the response codes both libraries use, the same as Play's
    static final int RESPONSE_SERVICE_DISCONNECTED = -1;
    static final int RESPONSE_OK = 0;
    static final int RESPONSE_SERVICE_UNAVAILABLE = 1;
    static final int RESPONSE_FEATURE_NOT_SUPPORTED = 2;
    static final int RESPONSE_DEVELOPER_ERROR = 3;

    interface StoreListener {
        void onSetupFinished(int responseCode);

        void onDisconnected();
    }

    /**
     * A store's install referrer client
     */
    interface StoreClient {
        void start(@NonNull StoreListener listener);

        @Nullable Details referrer() throws Exception;

        void end();
    }

    /**
     * @return the class of the store's client, to check that the app includes the library
     */
    abstract @NonNull String clientClass();

    /**
     * @return whether the store is on this device
     */
    abstract boolean storePresent(@NonNull Context context);

    /**
     * Creates the library's client; only called after {@link #isAvailable()} and {@link #storePresent(Context)}
     */
    abstract @NonNull StoreClient newClient(@NonNull Context context);

    @Override
    boolean isAvailable() {
        try {
            Class.forName(clientClass());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    @Override
    void read(@NonNull Context context, @NonNull Callback callback) {
        final Callback once = onceOnly(callback);
        if (!storePresent(context)) {
            once.onResult(STATUS_NOT_SUPPORTED, (Details) null);
            return;
        }
        try {
            readWith(newClient(context), once);
        } catch (Exception | LinkageError e) {
            //a library version with a different API, or a store process that misbehaves
            once.onResult(STATUS_RETRY, (Details) null);
        }
    }

    static @NonNull Callback onceOnly(@NonNull final Callback callback) {
        final AtomicBoolean answered = new AtomicBoolean(false);
        return (status, details) -> {
            if (answered.compareAndSet(false, true)) {
                callback.onResult(status, details);
            }
        };
    }

    /**
     * Connects, reads on OK, maps the other codes, and disconnects
     */
    static void readWith(@NonNull final StoreClient client, @NonNull final Callback callback) {
        client.start(new StoreListener() {
            @Override
            public void onSetupFinished(int responseCode) {
                try {
                    if (responseCode == RESPONSE_OK) {
                        callback.onResult(STATUS_OK, client.referrer());
                    } else {
                        callback.onResult(statusOf(responseCode), (Details) null);
                    }
                } catch (Exception | LinkageError e) {
                    callback.onResult(STATUS_RETRY, (Details) null);
                } finally {
                    try {
                        client.end();
                    } catch (Exception ignored) {
                    }
                }
            }

            @Override
            public void onDisconnected() {
                //no-op when the result was already delivered
                callback.onResult(STATUS_RETRY, (Details) null);
            }
        });
    }

    /**
     * A store too old for the referrer (FEATURE_NOT_SUPPORTED) or an app the store refuses (DEVELOPER_ERROR) never
     * answers; a store that cannot answer now (SERVICE_UNAVAILABLE, a lost connection) is asked on a later launch
     */
    static int statusOf(int responseCode) {
        if (responseCode == RESPONSE_FEATURE_NOT_SUPPORTED || responseCode == RESPONSE_DEVELOPER_ERROR) {
            return STATUS_NOT_SUPPORTED;
        }
        return STATUS_RETRY;
    }
}
