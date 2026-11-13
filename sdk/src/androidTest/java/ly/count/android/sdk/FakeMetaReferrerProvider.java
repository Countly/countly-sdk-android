package ly.count.android.sdk;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stands in for the install referrer provider of a Meta app: answers content://<authority>/<facebook app id>
 * with the columns Meta documents. Each authority answers what the test put in {@link #answers}.
 */
public class FakeMetaReferrerProvider extends ContentProvider {
    static final String AUTHORITY_A = "ly.count.android.sdk.test.metareferrer.a";
    static final String AUTHORITY_B = "ly.count.android.sdk.test.metareferrer.b";

    //authority -> {install_referrer, is_ct, actual_timestamp}; null row values are null columns
    static final Map<String, Object[]> answers = new HashMap<>();
    //authority -> the last path asked for
    static final Map<String, String> asked = new HashMap<>();
    static volatile long delayMs = 0;

    static synchronized void reset() {
        answers.clear();
        asked.clear();
        delayMs = 0;
    }

    @Override public boolean onCreate() {
        return true;
    }

    @Nullable @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        String authority = uri.getAuthority();
        List<String> segments = uri.getPathSegments();
        asked.put(authority, segments.isEmpty() ? null : segments.get(0));
        Object[] row = answers.get(authority);
        if (row == null) {
            return null;
        }
        MatrixCursor cursor = new MatrixCursor(new String[] { "install_referrer", "is_ct", "actual_timestamp" });
        cursor.addRow(row);
        return cursor;
    }

    @Nullable @Override public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable @Override public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }
}
