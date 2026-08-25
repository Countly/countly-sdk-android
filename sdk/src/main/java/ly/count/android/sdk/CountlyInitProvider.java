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
 * ContentProvider that installs the process-wide {@link LifecycleDispatcher} before
 * Application.onCreate(). Running this early is what makes the dispatcher's started-activity
 * count exact from process start (no activity can start before content providers are created):
 * the SDK then knows the precise foreground state at init time, and captures the first Activity
 * reference even when Countly.init() is called after the Activity has already started (e.g., in
 * Flutter, React Native, or single-activity apps with deferred initialization).
 *
 * The captured Activity is stored in {@link CountlyActivityHolder} and used during
 * SDK initialization to seed modules that need an Activity reference.
 *
 * This provider performs no actual content operations. If an app strips it from the manifest
 * (tools:node="remove"), the SDK installs the dispatcher at first init instead and falls back
 * to the ProcessLifecycleOwner heuristic for foreground state.
 */
public class CountlyInitProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context == null) {
            return false;
        }

        Context appContext = context.getApplicationContext();
        if (appContext instanceof Application) {
            LifecycleDispatcher.getInstance().install((Application) appContext, true);
        }

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
