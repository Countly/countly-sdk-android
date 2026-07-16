package ly.count.android.sdk;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
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
        if (appContext instanceof Application) {
            ((Application) appContext).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                    CountlyActivityHolder.getInstance().setActivity(activity);
                }

                @Override
                public void onActivityStarted(@NonNull Activity activity) {
                    CountlyActivityHolder.getInstance().setActivity(activity);
                }

                @Override
                public void onActivityResumed(@NonNull Activity activity) {
                    CountlyActivityHolder.getInstance().setActivity(activity);
                }

                @Override
                public void onActivityPaused(@NonNull Activity activity) {
                }

                @Override
                public void onActivityStopped(@NonNull Activity activity) {
                }

                @Override
                public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
                }

                @Override
                public void onActivityDestroyed(@NonNull Activity activity) {
                    CountlyActivityHolder.getInstance().clearActivity(activity);
                }
            });
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
