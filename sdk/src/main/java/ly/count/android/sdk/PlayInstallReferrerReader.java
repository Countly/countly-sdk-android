package ly.count.android.sdk;

import android.content.Context;
import androidx.annotation.NonNull;
import com.android.installreferrer.api.InstallReferrerClient;
import com.android.installreferrer.api.InstallReferrerStateListener;
import com.android.installreferrer.api.ReferrerDetails;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reads the Google Play Install Referrer through the {@code com.android.installreferrer:installreferrer}
 * library. The library is an optional dependency of the SDK: apps that do not add it build and run, and
 * {@link #isAvailable()} returns false for them. Every reference to the library's classes is inside
 * {@link Bridge}, which is only loaded after the availability check passed.
 */
class PlayInstallReferrerReader implements InstallReferrerReader {
    static final String CLIENT_CLASS = "com.android.installreferrer.api.InstallReferrerClient";

    @Override
    public boolean isAvailable() {
        try {
            Class.forName(CLIENT_CLASS);
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    @Override
    public void read(@NonNull Context context, @NonNull Callback callback) {
        final AtomicBoolean answered = new AtomicBoolean(false);
        final Callback once = (status, referrer) -> {
            if (answered.compareAndSet(false, true)) {
                callback.onResult(status, referrer);
            }
        };
        try {
            Bridge.read(context, once);
        } catch (Exception | LinkageError e) {
            //a library version with a different API, or a store process that misbehaves
            once.onResult(STATUS_RETRY, null);
        }
    }

    //abstract: only static members, never instantiated
    private abstract static class Bridge {
        static void read(@NonNull Context context, @NonNull final Callback callback) {
            final InstallReferrerClient client = InstallReferrerClient.newBuilder(context.getApplicationContext()).build();
            client.startConnection(new InstallReferrerStateListener() {
                @Override
                public void onInstallReferrerSetupFinished(int responseCode) {
                    try {
                        if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                            ReferrerDetails details = client.getInstallReferrer();
                            callback.onResult(STATUS_OK, details == null ? null : details.getInstallReferrer());
                        } else if (responseCode == InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED) {
                            callback.onResult(STATUS_NOT_SUPPORTED, null);
                        } else {
                            callback.onResult(STATUS_RETRY, null);
                        }
                    } catch (Exception e) {
                        callback.onResult(STATUS_RETRY, null);
                    } finally {
                        try {
                            client.endConnection();
                        } catch (Exception ignored) {
                        }
                    }
                }

                @Override
                public void onInstallReferrerServiceDisconnected() {
                    //no-op when the result was already delivered
                    callback.onResult(STATUS_RETRY, null);
                }
            });
        }
    }
}
