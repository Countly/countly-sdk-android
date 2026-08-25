package ly.count.android.sdk;

import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * ContentProvider that registers ActivityLifecycleCallbacks before Application.onCreate().
 * This ensures that the SDK captures the first Activity reference even when Countly.init()
 * is called after the Activity has already started (e.g., in Flutter, React Native, or
 * single-activity apps with deferred initialization).
 *
 * The captured Activity is stored in {@link CountlyActivityHolder} and used during
 * SDK initialization to seed modules that need an Activity reference.
 *
 * This provider performs no actual content operations.
 */
public class CountlyInitProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context == null) {
            return false;
        }

        Context appContext = context.getApplicationContext();
        //One registration for the whole process. The dispatcher also feeds CountlyActivityHolder, so the
        //behaviour this provider shipped for (capturing the current Activity before Application.onCreate,
        //which single-activity frameworks depend on) is unchanged - it just no longer needs its own
        //callbacks object, and every Countly instance now shares this one registration.
        CountlyLifecycleDispatcher.getInstance().register(appContext);

        return false;
    }

    @Nullable @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }
}
