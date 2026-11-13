package ly.count.android.sdk;

import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reads the Meta Install Referrer from the content providers of the Facebook, Instagram and Facebook Lite apps,
 * {@code content://<authority>/<facebook app id>}. The provider must belong to the expected app. When more than
 * one app answers, the latest interaction wins. The SDK does not decrypt the referrer; the server does.
 * <p>
 * The app has to see these packages on Android 11 and later: the SDK's manifest declares them in {@code <queries>}.
 */
class ContentProviderMetaReferrerReader implements MetaReferrerReader {
    static final String COLUMN_INSTALL_REFERRER = "install_referrer";
    static final String COLUMN_IS_CT = "is_ct";
    static final String COLUMN_ACTUAL_TIMESTAMP = "actual_timestamp";

    //{authority, package that must own it}
    static final String[][] META_PROVIDERS = {
        { "com.facebook.katana.provider.InstallReferrerProvider", "com.facebook.katana" },
        { "com.instagram.contentprovider.InstallReferrerProvider", "com.instagram.android" },
        { "com.facebook.lite.provider.InstallReferrerProvider", "com.facebook.lite" },
    };

    //how long the providers may take together
    static final long DEFAULT_TIMEOUT_MS = 5000;

    final String[][] providers;
    final ModuleLog L;
    long timeoutMs = DEFAULT_TIMEOUT_MS;

    ContentProviderMetaReferrerReader(@NonNull ModuleLog L) {
        this(META_PROVIDERS, L);
    }

    ContentProviderMetaReferrerReader(@NonNull String[][] providers, @NonNull ModuleLog L) {
        this.providers = providers.clone();
        this.L = L;
    }

    @Override
    public void read(@NonNull final Context context, @NonNull final String facebookAppId, @NonNull final Callback callback) {
        final Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        final AtomicReference<Result> answer = new AtomicReference<>();
        final Thread query = new Thread(() -> {
            try {
                answer.set(readNow(appContext, facebookAppId));
            } catch (Exception e) {
                L.w("[ContentProviderMetaReferrerReader] read, reading failed: " + e);
            }
        }, "Countly-MetaReferrer-query");
        query.setDaemon(true);

        Thread waiter = new Thread(() -> {
            query.start();
            try {
                query.join(timeoutMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (query.isAlive()) {
                //a provider that hangs: the Play referrer is not held back by it
                L.w("[ContentProviderMetaReferrerReader] read, no answer in " + timeoutMs + " ms, going on without it");
            }
            callback.onResult(answer.get());
        }, "Countly-MetaReferrer");
        waiter.setDaemon(true);
        waiter.start();
    }

    /**
     * Asks every provider, on the calling thread
     */
    @Nullable Result readNow(@NonNull Context context, @NonNull String facebookAppId) {
        Result best = null;
        for (String[] provider : providers) {
            Result result = readProvider(context, provider[0], provider[1], facebookAppId);
            if (result != null && (best == null || result.actualTimestamp > best.actualTimestamp)) {
                best = result;
            }
        }
        return best;
    }

    @Nullable Result readProvider(@NonNull Context context, @NonNull String authority, @NonNull String expectedPackage, @NonNull String facebookAppId) {
        if (!ownedBy(context, authority, expectedPackage)) {
            return null;
        }
        Uri uri = Uri.parse("content://" + authority + "/" + facebookAppId);
        String[] projection = { COLUMN_INSTALL_REFERRER, COLUMN_IS_CT, COLUMN_ACTUAL_TIMESTAMP };
        try (Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
            return cursor != null && cursor.moveToFirst() ? resultOf(cursor) : null;
        } catch (Exception e) {
            //SecurityException when the provider refuses this app, or a provider process that died
            L.d("[ContentProviderMetaReferrerReader] readProvider, [" + authority + "] did not answer: " + e);
            return null;
        }
    }

    /**
     * Whether the provider is there and belongs to the expected app
     */
    boolean ownedBy(@NonNull Context context, @NonNull String authority, @NonNull String expectedPackage) {
        ProviderInfo info;
        try {
            info = context.getPackageManager().resolveContentProvider(authority, 0);
        } catch (Exception e) {
            return false;
        }
        if (info == null) {
            //the app is not installed, or this app cannot see it
            return false;
        }
        if (!expectedPackage.equals(info.packageName)) {
            L.w("[ContentProviderMetaReferrerReader] ownedBy, [" + authority + "] belongs to [" + info.packageName + "], not to [" + expectedPackage + "], not read");
            return false;
        }
        return true;
    }

    @Nullable static Result resultOf(@NonNull Cursor cursor) {
        String referrer = stringOf(cursor, COLUMN_INSTALL_REFERRER);
        if (isBlank(referrer)) {
            return null;
        }
        //is_ct missing: the server counts it as a click, so send 1
        int isCt = longOf(cursor, COLUMN_IS_CT, 1) == 0 ? 0 : 1;
        return new Result(referrer, isCt, longOf(cursor, COLUMN_ACTUAL_TIMESTAMP, 0));
    }

    @Nullable static String stringOf(@NonNull Cursor cursor, @NonNull String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? null : cursor.getString(index);
    }

    static long longOf(@NonNull Cursor cursor, @NonNull String column, long fallback) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? fallback : cursor.getLong(index);
    }

    static boolean isBlank(@Nullable String value) {
        if (value == null) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
