package ly.count.android.sdk;

import android.annotation.TargetApi;
import android.app.Activity;
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

final class UtilsDevice {
    private UtilsDevice() {
    }

    @NonNull
    static DisplayMetrics getDisplayMetrics(@NonNull final Context context) {
        final WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        final DisplayMetrics metrics = new DisplayMetrics();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            applyWindowMetrics(context, wm, metrics);
        } else {
            applyLegacyMetrics(wm, metrics);
        }
        return metrics;
    }

    @TargetApi(Build.VERSION_CODES.R)
    private static void applyWindowMetrics(@NonNull Context context,
        @NonNull WindowManager wm,
        @NonNull DisplayMetrics outMetrics) {
        final WindowMetrics windowMetrics = wm.getCurrentWindowMetrics();
        final WindowInsets windowInsets = windowMetrics.getWindowInsets();

        boolean useCutoutArea = false;

        if (Countly.sharedInstance().isInitialized()) {
            useCutoutArea = Countly.sharedInstance().config_.configProvider.getUseCutoutArea();
        }

        // Always respect status bar & cutout (they affect safe area even in fullscreen)
        int types = 0;
        boolean usePhysicalScreenSize = !(context instanceof Activity);

        // If not activity, we can't know system UI visibility, so always use physical screen size
        if (!usePhysicalScreenSize) {
            // Only subtract navigation bar insets when navigation bar is actually visible
            if (windowInsets.isVisible(WindowInsets.Type.navigationBars())) {
                types |= WindowInsets.Type.navigationBars();
            }

            if (windowInsets.isVisible(WindowInsets.Type.statusBars())) {
                types |= WindowInsets.Type.statusBars();
            }

            if (useCutoutArea) {
                boolean drawUnderCutout;
                WindowManager.LayoutParams params = ((Activity) context).getWindow().getAttributes();
                drawUnderCutout = params.layoutInDisplayCutoutMode
                    == WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;

                // Only subtract display cutout insets when not allowed to draw under the cutout
                if (!drawUnderCutout && windowInsets.isVisible(WindowInsets.Type.displayCutout())) {
                    types |= WindowInsets.Type.displayCutout();
                }

                // Only subtract display cutout insets when not allowed to draw under the cutout
                if (windowInsets.isVisible(WindowInsets.Type.displayCutout())) {
                    types |= WindowInsets.Type.displayCutout();
                }
            }
        }

        if (!useCutoutArea) {
            // Cutout is always respected as safe area for now even in fullscreen mode
            if (windowInsets.isVisible(WindowInsets.Type.displayCutout())) {
                types |= WindowInsets.Type.displayCutout();
            }
        }

        final Insets insets = windowInsets.getInsets(types);
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
    private static void applyLegacyMetrics(@NonNull WindowManager wm,
        @NonNull DisplayMetrics outMetrics) {
        final Display display = wm.getDefaultDisplay();
        display.getRealMetrics(outMetrics);
        //getMetrics gives us size minus navigation bar
    }
}
