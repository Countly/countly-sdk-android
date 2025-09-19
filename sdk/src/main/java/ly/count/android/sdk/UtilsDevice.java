package ly.count.android.sdk;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import androidx.annotation.NonNull;

class UtilsDevice {

    static DisplayCutout cutout = null;

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
        final WindowInsets windowInsets = windowMetrics.getWindowInsets();

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

            boolean drawUnderCutout;
            WindowManager.LayoutParams params = ((Activity) context).getWindow().getAttributes();
            drawUnderCutout = params.layoutInDisplayCutoutMode
                == WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;

            // Only subtract display cutout insets when not allowed to draw under the cutout
            if (!drawUnderCutout && windowInsets.isVisible(WindowInsets.Type.displayCutout())) {
                types |= WindowInsets.Type.displayCutout();
            }
        }

        // Cutout is always respected as safe area for now even in fullscreen mode
        if (windowInsets.isVisible(WindowInsets.Type.displayCutout())) {
            types |= WindowInsets.Type.displayCutout();
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

    /**
     * Tries to extract cutout information from the activity for api level 28-29
     *
     * @param activity Activity to extract cutout from
     */
    static void getCutout(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Window window = activity.getWindow();
            if (window == null) return;

            View decorView = window.getDecorView();
            if (decorView == null) return;

            decorView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    WindowInsets insets = v.getRootWindowInsets();
                    if (insets != null) {
                        DisplayCutout cutout1 = insets.getDisplayCutout();
                        if (cutout1 != null && !cutout1.getBoundingRects().isEmpty()) {
                            cutout = cutout1;
                        }
                    }
                    v.removeOnAttachStateChangeListener(this);
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                }
            });
        }
    }

    @SuppressWarnings("deprecation")
    private static void applyLegacyMetrics(@NonNull Context context,
        @NonNull WindowManager wm,
        @NonNull DisplayMetrics outMetrics) {
        final Display display = wm.getDefaultDisplay();
        display.getRealMetrics(outMetrics);

        if (context instanceof Activity) {
            getCutout((Activity) context);
        }

        boolean isLandscape = context.getResources().getConfiguration().orientation
            == Configuration.ORIENTATION_LANDSCAPE;

        if (cutout != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (isLandscape) {
                // In landscape, top/bottom insets become width, left/right become height
                outMetrics.widthPixels -= (cutout.getSafeInsetTop() + cutout.getSafeInsetBottom());
                outMetrics.heightPixels -= (cutout.getSafeInsetLeft() + cutout.getSafeInsetRight());
            } else {
                // Portrait
                outMetrics.heightPixels -= (cutout.getSafeInsetTop() + cutout.getSafeInsetBottom());
                outMetrics.widthPixels -= (cutout.getSafeInsetLeft() + cutout.getSafeInsetRight());
            }
        }
    }
}
