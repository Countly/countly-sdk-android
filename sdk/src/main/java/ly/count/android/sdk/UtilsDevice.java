package ly.count.android.sdk;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
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
import androidx.annotation.Nullable;

class UtilsDevice {

    static DisplayCutout cutout = null;

    private UtilsDevice() {
    }

    /**
     * Resolves the theme (dark/light) the app is currently rendering with. Reads from the current
     * foreground Activity when available and falls back to the given context otherwise. The Activity
     * is preferred because per-app night-mode overrides (e.g. AppCompatDelegate.setDefaultNightMode)
     * are applied to the Activity's resources, not the application context, and because in-app
     * messages render in the Activity's window - so its configuration is the effective theme.
     *
     * @param fallbackContext context used when no foreground Activity is available
     * @return "d" for dark mode, "l" for light mode, or null when the mode is undefined/unavailable
     */
    @Nullable
    static String getThemeMode(@NonNull final Context fallbackContext) {
        try {
            final Activity activity = CountlyActivityHolder.getInstance().getActivity();
            final Context context = activity != null ? activity : fallbackContext;
            int nightModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            switch (nightModeFlags) {
                case Configuration.UI_MODE_NIGHT_YES:
                    return "d";
                case Configuration.UI_MODE_NIGHT_NO:
                    return "l";
                default:
                    return null;
            }
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Appends the app's current theme as the "th" query parameter (l = light, d = dark) to the
     * given URL, so a feedback widget or content loaded in a WebView is rendered matching the
     * theme the app is displaying with. Uses "?" as the separator when the URL has no query yet,
     * "&" otherwise. When the theme can not be resolved the URL is returned unchanged.
     *
     * @param url URL that will be loaded in a WebView
     * @param context context used to resolve the theme when no foreground Activity is available
     * @return the URL with "th" appended, or the original URL when the theme is undefined
     */
    @NonNull
    static String appendThemeParam(@NonNull final String url, @NonNull final Context context) {
        final String theme = getThemeMode(context);
        if (theme == null) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + "th=" + theme;
    }

    /**
     * Returns the screen metrics used for the resolution metric and for sizing content and
     * feedback widgets. Reads them through the WindowManager that {@link #obtainWindowManager}
     * resolves, and through DisplayManager when it resolves none.
     *
     * @param context context the metrics are requested with, usually the Application context
     * @return the resolved metrics
     */
    @NonNull
    static DisplayMetrics getDisplayMetrics(@NonNull final Context context) {
        final WindowManager wm = obtainWindowManager(context);
        final DisplayMetrics metrics = new DisplayMetrics();

        if (wm == null) {
            applyDisplayMetrics(context, metrics);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            applyWindowMetrics(context, wm, metrics);
        } else {
            applyLegacyMetrics(context, wm, metrics);
        }
        return metrics;
    }

    /**
     * Resolves WindowManager from a visual context: the given context when it is an Activity,
     * otherwise the held foreground Activity. From API 30, StrictMode reports reading WindowManager
     * from a non-visual context such as the Application, so null is returned there when no Activity
     * is available. Older versions have no such check, so the given context is used there.
     *
     * @param context context the metrics are requested with
     * @return the WindowManager to read metrics from, or null when there is no visual context on API 30+
     */
    @Nullable
    static WindowManager obtainWindowManager(@NonNull Context context) {
        if (context instanceof Activity) {
            return (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        }
        Activity held = CountlyActivityHolder.getInstance().getActivity();
        if (held != null) {
            return (WindowManager) held.getSystemService(Context.WINDOW_SERVICE);
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        }
        return null;
    }

    /**
     * Fills the metrics with the default display's real size, read through DisplayManager, which is
     * not a visual service and can be used from the Application context. The display cutout's safe
     * insets are subtracted, as the WindowManager path does. Falls back to the context's resource
     * metrics when the display can not be resolved.
     *
     * @param context context to resolve DisplayManager and resources from
     * @param outMetrics metrics to fill
     */
    @SuppressWarnings("deprecation")
    static void applyDisplayMetrics(@NonNull Context context, @NonNull DisplayMetrics outMetrics) {
        final DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        final Display display = dm != null ? dm.getDisplay(Display.DEFAULT_DISPLAY) : null;
        if (display == null) {
            outMetrics.setTo(context.getResources().getDisplayMetrics());
            return;
        }
        display.getRealMetrics(outMetrics);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            final DisplayCutout displayCutout = display.getCutout();
            if (displayCutout != null) {
                outMetrics.widthPixels -= displayCutout.getSafeInsetLeft() + displayCutout.getSafeInsetRight();
                outMetrics.heightPixels -= displayCutout.getSafeInsetTop() + displayCutout.getSafeInsetBottom();
            }
        }
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
