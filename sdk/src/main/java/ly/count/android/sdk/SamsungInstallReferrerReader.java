package ly.count.android.sdk;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.samsung.android.sdk.sinstallreferrer.api.InstallReferrerClient;
import com.samsung.android.sdk.sinstallreferrer.api.InstallReferrerStateListener;
import com.samsung.android.sdk.sinstallreferrer.api.ReferrerDetails;

/**
 * Reads the Samsung Galaxy Store install referrer through the optional
 * {@code store.galaxy.samsung.installreferrer:samsung_galaxystore_install_referrer} library (Galaxy Store
 * 4.5.20.1 or later). Nothing is asked on a device without Galaxy Store; the library's manifest declares the
 * store's package in {@code <queries>}. The times are Samsung's, in seconds: the click is on the store page's
 * install button, not on an ad, and there are no store-signed server times as Play has.
 */
class SamsungInstallReferrerReader extends StoreInstallReferrerReader {
    static final String CLIENT_CLASS = "com.samsung.android.sdk.sinstallreferrer.api.InstallReferrerClient";
    static final String GALAXY_STORE_PACKAGE = "com.sec.android.app.samsungapps";

    @Override @NonNull String clientClass() {
        return CLIENT_CLASS;
    }

    @Override boolean storePresent(@NonNull Context context) {
        try {
            context.getPackageManager().getPackageInfo(GALAXY_STORE_PACKAGE, 0);
            return true;
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

        @Override public @Nullable Details referrer() {
            ReferrerDetails details = client.getInstallReferrer();
            if (details == null) {
                return null;
            }
            return new Details(details.getInstallReferrer(), details.getReferrerClickTimestampSeconds(), details.getInstallBeginTimestampSeconds(), 0, 0, null);
        }

        @Override public void end() {
            client.endConnection();
        }
    }
}
