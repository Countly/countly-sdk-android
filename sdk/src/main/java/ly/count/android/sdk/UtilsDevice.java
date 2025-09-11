package ly.count.android.sdk;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import androidx.annotation.NonNull;

class UtilsDevice {
    private UtilsDevice() {
    }

    @NonNull
    static DisplayMetrics getDisplayMetrics(@NonNull final Context context) {
        final WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        final DisplayMetrics metrics = new DisplayMetrics();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            applyWindowMetrics(context, wm, metrics);
        } else {
            applyLegacyMetrics(context, wm, metrics);
        }
        return metrics;
    }

    @TargetApi(Build.VERSION_CODES.R)
    private static void applyWindowMetrics(@NonNull Context context,
        @NonNull WindowManager wm,
        @NonNull DisplayMetrics outMetrics) {
        final WindowMetrics windowMetrics = wm.getCurrentWindowMetrics();

        // Exclude system insets (status bar, nav bar, cutout)
        final Insets insets = windowMetrics.getWindowInsets()
            .getInsetsIgnoringVisibility(
                WindowInsets.Type.navigationBars()
                    | WindowInsets.Type.displayCutout()
                    | WindowInsets.Type.statusBars()
            );

        final Rect bounds = windowMetrics.getBounds();
        final int width = bounds.width() - insets.left - insets.right;
        final int height = bounds.height() - insets.top - insets.bottom;

        outMetrics.widthPixels = width;
        outMetrics.heightPixels = height;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            outMetrics.density = windowMetrics.getDensity();
        } else {
            // Fallback: use resource-based density
            outMetrics.density = context.getResources().getDisplayMetrics().density;
        }
    }

    @SuppressWarnings("deprecation")
    private static void applyLegacyMetrics(@NonNull final Context context,
        @NonNull WindowManager wm,
        @NonNull DisplayMetrics outMetrics) {
        final Display display = wm.getDefaultDisplay();
        display.getMetrics(outMetrics);

        // pre-api level 30 does not include status bar in heightPixels
        int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            outMetrics.heightPixels -= context.getResources().getDimensionPixelSize(resourceId);
        }
    }
}
