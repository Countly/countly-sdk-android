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

/**
 * Reads the HUAWEI AppGallery install referrer through the optional {@code com.huawei.hms:ads-installreferrer}
 * library, which asks HMS Core. Nothing is asked on a device without HMS Core. HUAWEI gives the times in
 * milliseconds; they are passed on in seconds.
 */
class HuaweiInstallReferrerReader extends StoreInstallReferrerReader {
    static final String CLIENT_CLASS = "com.huawei.hms.ads.installreferrer.api.InstallReferrerClient";
    //the HMS Core service the library binds to; the SDK's manifest declares it in <queries>
    static final String HMS_PACKAGE = "com.huawei.hwid";
    static final String CHANNEL_SERVICE_ACTION = "com.huawei.android.hms.CHANNEL_SERVICE";

    @Override @NonNull String clientClass() {
        return CLIENT_CLASS;
    }

    @Override boolean storePresent(@NonNull Context context) {
        return hmsServicePresent(context);
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

    @Override @NonNull StoreClient newClient(@NonNull Context context) {
        return new Bridge(context);
    }

    private static final class Bridge implements StoreClient {
        final InstallReferrerClient client;

        Bridge(@NonNull Context context) {
            client = InstallReferrerClient.newBuilder(context.getApplicationContext()).build();
        }

        @Override public void start(@NonNull final StoreListener listener) {
            client.startConnection(new InstallReferrerStateListener() {
                @Override public void onInstallReferrerSetupFinished(int responseCode) {
                    listener.onSetupFinished(responseCode);
                }

                @Override public void onInstallReferrerServiceDisconnected() {
                    listener.onDisconnected();
                }
            });
        }

        @Override public @Nullable Details referrer() throws Exception {
            ReferrerDetails details = client.getInstallReferrer();
            if (details == null) {
                return null;
            }
            long clickTs = 0;
            long installTs = 0;
            try {
                clickTs = details.getReferrerClickTimestampMillisecond() / 1000;
                installTs = details.getInstallBeginTimestampMillisecond() / 1000;
            } catch (Exception | LinkageError ignored) {
                //a library without the timestamps: the referrer is still sent
            }
            return new Details(details.getInstallReferrer(), clickTs, installTs, 0, 0, null);
        }

        @Override public void end() {
            client.endConnection();
        }
    }
}
