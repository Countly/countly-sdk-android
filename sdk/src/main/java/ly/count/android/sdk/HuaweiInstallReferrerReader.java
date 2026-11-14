package ly.count.android.sdk;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.huawei.hms.ads.installreferrer.api.InstallReferrerClient;
import com.huawei.hms.ads.installreferrer.api.InstallReferrerStateListener;
import com.huawei.hms.ads.installreferrer.api.ReferrerDetails;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reads the HUAWEI AppGallery install referrer through the {@code com.huawei.hms:ads-installreferrer} library,
 * which asks HMS Core. The library is an optional dependency of the SDK: apps that do not add it build and run,
 * and {@link #isAvailable()} returns false for them. On a device without HMS Core nothing is asked and the
 * reader answers {@link #STATUS_NOT_SUPPORTED}. Every reference to the library's classes is inside
 * {@link Bridge}, which is only loaded after the availability check passed.
 */
class HuaweiInstallReferrerReader extends InstallReferrerReader {
    static final String CLIENT_CLASS = "com.huawei.hms.ads.installreferrer.api.InstallReferrerClient";
    //the HMS Core service the library binds to; the SDK's manifest declares it in <queries>
    static final String HMS_PACKAGE = "com.huawei.hwid";
    static final String CHANNEL_SERVICE_ACTION = "com.huawei.android.hms.CHANNEL_SERVICE";

    @Override
    boolean isAvailable() {
        try {
            Class.forName(CLIENT_CLASS);
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    /**
     * Whether HMS Core with the install referrer service is on the device
     */
    static boolean hmsServicePresent(@NonNull Context context) {
        try {
            Intent intent = new Intent(CHANNEL_SERVICE_ACTION).setPackage(HMS_PACKAGE);
            List<ResolveInfo> services = context.getPackageManager().queryIntentServices(intent, 0);
            return services != null && !services.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    void read(@NonNull Context context, @NonNull Callback callback) {
        final AtomicBoolean answered = new AtomicBoolean(false);
        final Callback once = (status, details) -> {
            if (answered.compareAndSet(false, true)) {
                callback.onResult(status, details);
            }
        };
        if (!hmsServicePresent(context)) {
            once.onResult(STATUS_NOT_SUPPORTED, (Details) null);
            return;
        }
        try {
            new Bridge().read(context, once);
        } catch (Exception | LinkageError e) {
            //a library version with a different API, or an HMS Core process that misbehaves
            once.onResult(STATUS_RETRY, (Details) null);
        }
    }

    private static final class Bridge {
        /**
         * The referrer with its click and install-begin times, which HUAWEI gives in milliseconds: sent in seconds
         */
        @Nullable Details detailsOf(@Nullable ReferrerDetails details) {
            if (details == null) {
                return null;
            }
            String referrer = details.getInstallReferrer();
            long clickTs = 0;
            long installTs = 0;
            try {
                clickTs = details.getReferrerClickTimestampMillisecond() / 1000;
                installTs = details.getInstallBeginTimestampMillisecond() / 1000;
            } catch (Exception | LinkageError ignored) {
                //a library without the timestamps: the referrer is still sent
            }
            return new Details(referrer, clickTs, installTs, 0, 0, null);
        }

        void read(@NonNull Context context, @NonNull final Callback callback) {
            final InstallReferrerClient client = InstallReferrerClient.newBuilder(context.getApplicationContext()).build();
            client.startConnection(new InstallReferrerStateListener() {
                @Override
                public void onInstallReferrerSetupFinished(int responseCode) {
                    try {
                        if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                            callback.onResult(STATUS_OK, detailsOf(client.getInstallReferrer()));
                        } else if (responseCode == InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED
                            || responseCode == InstallReferrerClient.InstallReferrerResponse.DEVELOPER_ERROR) {
                            callback.onResult(STATUS_NOT_SUPPORTED, (Details) null);
                        } else {
                            callback.onResult(STATUS_RETRY, (Details) null);
                        }
                    } catch (Exception e) {
                        callback.onResult(STATUS_RETRY, (Details) null);
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
                    callback.onResult(STATUS_RETRY, (Details) null);
                }
            });
        }
    }
}
