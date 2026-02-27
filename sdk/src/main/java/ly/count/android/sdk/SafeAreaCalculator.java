package ly.count.android.sdk;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import androidx.annotation.NonNull;

/**
 * Utility class to calculate safe area dimensions for webview display
 * Takes into account cutout, status bar, and navigation bar
 */
class SafeAreaCalculator {

    private static final int DEFAULT_BUTTON_NAV_BAR_HEIGHT_DP = 48;
    private static final int DEFAULT_GESTURE_NAV_BAR_HEIGHT_DP = 24;

    private SafeAreaCalculator() {
    }

    /**
     * Calculates safe area dimensions for both portrait and landscape orientations
     * Compatible with Android 21+
     *
     * @param context Context to use for calculations
     * @param L Logger for debugging
     * @return SafeAreaDimensions object containing calculated dimensions and offsets
     */
    @NonNull
    static SafeAreaDimensions calculateSafeAreaDimensions(@NonNull Context context, @NonNull ModuleLog L) {
        final WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        final Resources resources = context.getResources();
        final int currentOrientation = resources.getConfiguration().orientation;
        final boolean isPortrait = currentOrientation == Configuration.ORIENTATION_PORTRAIT;

        L.d("[SafeAreaCalculator] calculateSafeAreaDimensions, current orientation: [" + (isPortrait ? "portrait" : "landscape") + "], API level: [" + Build.VERSION.SDK_INT + "]");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return calculateSafeAreaDimensionsR(context, wm, isPortrait, L);
        } else {
            return calculateSafeAreaDimensionsLegacy(context, wm, isPortrait, L);
        }
    }

    @TargetApi(Build.VERSION_CODES.R)
    private static SafeAreaDimensions calculateSafeAreaDimensionsR(@NonNull Context context,
        @NonNull WindowManager wm, boolean isPortrait, @NonNull ModuleLog L) {
        final WindowMetrics windowMetrics = wm.getCurrentWindowMetrics();
        final WindowInsets windowInsets = windowMetrics.getWindowInsets();
        final Rect bounds = windowMetrics.getBounds();

        float density = context.getResources().getDisplayMetrics().density;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            density = windowMetrics.getDensity();
        }

        int currentWidth = bounds.width();
        int currentHeight = bounds.height();

        int portraitWidth = isPortrait ? currentWidth : currentHeight;
        int portraitHeight = isPortrait ? currentHeight : currentWidth;
        int landscapeWidth = isPortrait ? currentHeight : currentWidth;
        int landscapeHeight = isPortrait ? currentWidth : currentHeight;

        L.d("[SafeAreaCalculator] calculateSafeAreaDimensionsR, window bounds (px): width=[" + currentWidth + "], height=[" + currentHeight + "], density=[" + density + "]");
        L.d("[SafeAreaCalculator] calculateSafeAreaDimensionsR, mapped orientation dimensions (px) - Portrait: [" + portraitWidth + "x" + portraitHeight + "] Landscape: [" + landscapeWidth + "x" + landscapeHeight + "]");

        SafeAreaInsets portraitInsets = calculateInsetsForOrientation(
            windowInsets, true, density, portraitWidth, portraitHeight, L);
        SafeAreaInsets landscapeInsets = calculateInsetsForOrientation(
            windowInsets, false, density, landscapeWidth, landscapeHeight, L);

        SafeAreaDimensions result = new SafeAreaDimensions(
            portraitInsets.width,
            portraitInsets.height,
            landscapeInsets.width,
            landscapeInsets.height,
            portraitInsets.topOffset,
            landscapeInsets.topOffset,
            portraitInsets.leftOffset,
            landscapeInsets.leftOffset
        );

        L.d("[SafeAreaCalculator] calculateSafeAreaDimensionsR, final safe area (px) - Portrait: [" + result.portraitWidth + "x" + result.portraitHeight + "], topOffset=[" + result.portraitTopOffset + "], leftOffset=[" + result.portraitLeftOffset + "]");
        L.d("[SafeAreaCalculator] calculateSafeAreaDimensionsR, final safe area (px) - Landscape: [" + result.landscapeWidth + "x" + result.landscapeHeight + "], topOffset=[" + result.landscapeTopOffset + "], leftOffset=[" + result.landscapeLeftOffset + "]");

        return result;
    }

    @SuppressWarnings("deprecation")
    private static SafeAreaDimensions calculateSafeAreaDimensionsLegacy(@NonNull Context context,
        @NonNull WindowManager wm, boolean isPortrait, @NonNull ModuleLog L) {
        final Display display = wm.getDefaultDisplay();
        final DisplayMetrics metrics = new DisplayMetrics();
        display.getRealMetrics(metrics);

        if (context instanceof Activity) {
            UtilsDevice.getCutout((Activity) context);
        }

        float density = metrics.density;
        int currentWidth = metrics.widthPixels;
        int currentHeight = metrics.heightPixels;

        int portraitWidth = isPortrait ? currentWidth : currentHeight;
        int portraitHeight = isPortrait ? currentHeight : currentWidth;
        int landscapeWidth = isPortrait ? currentHeight : currentWidth;
        int landscapeHeight = isPortrait ? currentWidth : currentHeight;

        L.d("[SafeAreaCalculator] calculateSafeAreaDimensionsLegacy, display metrics (px): width=[" + currentWidth + "], height=[" + currentHeight + "], density=[" + density + "]");
        L.d("[SafeAreaCalculator] calculateSafeAreaDimensionsLegacy, mapped orientation dimensions (px) - Portrait: [" + portraitWidth + "x" + portraitHeight + "] Landscape: [" + landscapeWidth + "x" + landscapeHeight + "]");

        SafeAreaInsets portraitInsets = calculateInsetsLegacy(
            context, true, density, portraitWidth, portraitHeight, L);
        SafeAreaInsets landscapeInsets = calculateInsetsLegacy(
            context, false, density, landscapeWidth, landscapeHeight, L);

        SafeAreaDimensions result = new SafeAreaDimensions(
            portraitInsets.width,
            portraitInsets.height,
            landscapeInsets.width,
            landscapeInsets.height,
            portraitInsets.topOffset,
            landscapeInsets.topOffset,
            portraitInsets.leftOffset,
            landscapeInsets.leftOffset
        );

        L.d("[SafeAreaCalculator] calculateSafeAreaDimensionsLegacy, final safe area (px) - Portrait: [" + result.portraitWidth + "x" + result.portraitHeight + "], topOffset=[" + result.portraitTopOffset + "], leftOffset=[" + result.portraitLeftOffset + "]");
        L.d("[SafeAreaCalculator] calculateSafeAreaDimensionsLegacy, final safe area (px) - Landscape: [" + result.landscapeWidth + "x" + result.landscapeHeight + "], topOffset=[" + result.landscapeTopOffset + "], leftOffset=[" + result.landscapeLeftOffset + "]");

        return result;
    }

    @TargetApi(Build.VERSION_CODES.R)
    private static SafeAreaInsets calculateInsetsForOrientation(@NonNull WindowInsets windowInsets, boolean isPortrait, float density,
        int widthForOrientation, int heightForOrientation, @NonNull ModuleLog L) {

        String orientationStr = isPortrait ? "portrait" : "landscape";
        L.d("[SafeAreaCalculator] calculateInsetsForOrientation [" + orientationStr + "], total dimensions (px): [" + widthForOrientation + "x" + heightForOrientation + "]");

        int topInset = 0;
        int bottomInset = 0;
        int leftInset = 0;
        int rightInset = 0;
        int statusBarInset = 0;
        int cutoutInset = 0;
        int navBarInset = 0;

        boolean statusBarVisible = windowInsets.isVisible(WindowInsets.Type.statusBars());
        boolean navBarVisible = windowInsets.isVisible(WindowInsets.Type.navigationBars());
        boolean cutoutVisible = windowInsets.isVisible(WindowInsets.Type.displayCutout());

        L.d("[SafeAreaCalculator] calculateInsetsForOrientation [" + orientationStr + "], visibility - statusBar=[" + statusBarVisible + "], navBar=[" + navBarVisible + "], cutout=[" + cutoutVisible + "]");

        if (statusBarVisible) {
            Insets statusBarInsets = windowInsets.getInsets(WindowInsets.Type.statusBars());
            statusBarInset = statusBarInsets.top;
            topInset = Math.max(topInset, statusBarInset);
            L.d("[SafeAreaCalculator] calculateInsetsForOrientation [" + orientationStr + "], status bar inset (px): [" + statusBarInset + "]");
        }

        if (cutoutVisible) {
            Insets cutoutInsets = windowInsets.getInsets(WindowInsets.Type.displayCutout());
            if (isPortrait) {
                cutoutInset = cutoutInsets.top;
                topInset = Math.max(topInset, cutoutInset);
                bottomInset = Math.max(bottomInset, cutoutInsets.bottom);
                L.d("[SafeAreaCalculator] calculateInsetsForOrientation [" + orientationStr + "], cutout insets (px) - top=[" + cutoutInsets.top + "], bottom=[" + cutoutInsets.bottom + "]");
            } else {
                topInset = Math.max(topInset, cutoutInsets.top);
                bottomInset = Math.max(bottomInset, cutoutInsets.bottom);
                leftInset = Math.max(leftInset, cutoutInsets.left);
                rightInset = Math.max(rightInset, cutoutInsets.right);
                L.d("[SafeAreaCalculator] calculateInsetsForOrientation [" + orientationStr + "], cutout insets (px) - top=[" + cutoutInsets.top + "], bottom=[" + cutoutInsets.bottom + "], left=[" + cutoutInsets.left + "], right=[" + cutoutInsets.right + "]");
            }
        }

        L.d("[SafeAreaCalculator] calculateInsetsForOrientation [" + orientationStr + "], top inset (px) - using MAX(statusBar=" + statusBarInset + ", cutout=" + cutoutInset + ") = [" + topInset + "]");

        if (navBarVisible) {
            Insets navBarInsets = windowInsets.getInsets(WindowInsets.Type.navigationBars());

            boolean isGestureNav = isGestureNavigation(navBarInsets, density);
            String navType = isGestureNav ? "gesture" : "button";

            L.d("[SafeAreaCalculator] calculateInsetsForOrientation [" + orientationStr + "], nav bar type: [" + navType + "], raw insets (px) - top=[" + navBarInsets.top + "], bottom=[" + navBarInsets.bottom + "], left=[" + navBarInsets.left + "], right=[" + navBarInsets.right + "]");

            if (isPortrait) {
                navBarInset = navBarInsets.bottom;
                if (navBarInset == 0) {
                    navBarInset = getDefaultNavBarInset(isGestureNav, density);
                    L.d("[SafeAreaCalculator] calculateInsetsForOrientation [" + orientationStr + "], nav bar returned 0, using default [" + navType + "] value (px): [" + navBarInset + "]");
                }
                bottomInset = Math.max(bottomInset, navBarInset);
            } else {
                if (navBarInsets.bottom > 0) {
                    bottomInset = Math.max(bottomInset, navBarInsets.bottom);
                    L.d("[SafeAreaCalculator] calculateInsetsForOrientation [" + orientationStr + "], nav bar at bottom, applying bottom inset (px): [" + navBarInsets.bottom + "]");
                }
                if (navBarInsets.left > 0) {
                    leftInset = Math.max(leftInset, navBarInsets.left);
                    L.d("[SafeAreaCalculator] calculateInsetsForOrientation [" + orientationStr + "], nav bar at left, applying left inset (px): [" + navBarInsets.left + "]");
                }
                if (navBarInsets.right > 0) {
                    rightInset = Math.max(rightInset, navBarInsets.right);
                    L.d("[SafeAreaCalculator] calculateInsetsForOrientation [" + orientationStr + "], nav bar at right, applying right inset (px): [" + navBarInsets.right + "]");
                }

                if (bottomInset == 0 && leftInset == 0 && rightInset == 0) {
                    navBarInset = getDefaultNavBarInset(isGestureNav, density);
                    bottomInset = navBarInset;
                    L.d("[SafeAreaCalculator] calculateInsetsForOrientation [" + orientationStr + "], nav bar returned 0, using default [" + navType + "] value at bottom (px): [" + navBarInset + "]");
                }
            }
            L.d("[SafeAreaCalculator] calculateInsetsForOrientation [" + orientationStr + "], applied nav bar insets (px) - bottom=[" + bottomInset + "], left=[" + leftInset + "], right=[" + rightInset + "]");
        }

        int width = widthForOrientation - leftInset - rightInset;
        int height = heightForOrientation - topInset - bottomInset;

        int leftOffset = 0;
        if (!isPortrait) {
            Insets navBarInsets = navBarVisible ? windowInsets.getInsets(WindowInsets.Type.navigationBars()) : Insets.NONE;
            Insets cutoutInsets = cutoutVisible ? windowInsets.getInsets(WindowInsets.Type.displayCutout()) : Insets.NONE;

            if (navBarInsets.left > 0) {
                leftOffset = navBarInsets.left;
                L.d("[SafeAreaCalculator] calculateInsetsForOrientation [" + orientationStr + "], nav bar at left - leftOffset=[" + leftOffset + "] (navBar=" + navBarInsets.left + ")");
            }
            // cutout inset is already included in the leftInset calculation above
            else if (navBarInsets.right > 0) {
                leftOffset = 0;
                L.d("[SafeAreaCalculator] calculateInsetsForOrientation [" + orientationStr + "], nav bar at right - leftOffset=[" + leftOffset + "] (system will handle cutout positioning)");
            }
        }

        L.d("[SafeAreaCalculator] calculateInsetsForOrientation [" + orientationStr + "], final insets (px) - top=[" + topInset + "], bottom=[" + bottomInset + "], left=[" + leftInset + "], right=[" + rightInset + "]");
        L.d("[SafeAreaCalculator] calculateInsetsForOrientation [" + orientationStr + "], final safe area (px): [" + width + "x" + height + "], leftOffset=[" + leftOffset + "]");

        return new SafeAreaInsets(width, height, topInset, leftOffset);
    }

    private static SafeAreaInsets calculateInsetsLegacy(@NonNull Context context,
        boolean isPortrait, float density, int widthForOrientation, int heightForOrientation, @NonNull ModuleLog L) {

        String orientationStr = isPortrait ? "portrait" : "landscape";
        L.d("[SafeAreaCalculator] calculateInsetsLegacy [" + orientationStr + "], total dimensions (px): [" + widthForOrientation + "x" + heightForOrientation + "]");

        int topInset = 0;
        int bottomInset = 0;
        int leftInset = 0;
        int rightInset = 0;
        int cutoutInset = 0;
        int statusBarInset = 0;
        int navBarInset = 0;

        DisplayCutout cutout = UtilsDevice.cutout;
        boolean hasCutout = (cutout != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P);
        if (hasCutout) {
            if (isPortrait) {
                cutoutInset = cutout.getSafeInsetTop();
                topInset = Math.max(topInset, cutoutInset);
                bottomInset = Math.max(bottomInset, cutout.getSafeInsetBottom());
                leftInset = Math.max(leftInset, cutout.getSafeInsetLeft());
                rightInset = Math.max(rightInset, cutout.getSafeInsetRight());
                L.d("[SafeAreaCalculator] calculateInsetsLegacy [" + orientationStr + "], cutout insets (px) - top=[" + cutout.getSafeInsetTop() + "], bottom=[" + cutout.getSafeInsetBottom() + "], left=[" + cutout.getSafeInsetLeft() + "], right=[" + cutout.getSafeInsetRight() + "]");
            } else {
                topInset = Math.max(topInset, cutout.getSafeInsetTop());
                bottomInset = Math.max(bottomInset, cutout.getSafeInsetBottom());
                leftInset = Math.max(leftInset, cutout.getSafeInsetLeft());
                rightInset = Math.max(rightInset, cutout.getSafeInsetRight());
                L.d("[SafeAreaCalculator] calculateInsetsLegacy [" + orientationStr + "], cutout insets (px) - top=[" + cutout.getSafeInsetTop() + "], bottom=[" + cutout.getSafeInsetBottom() + "], left=[" + cutout.getSafeInsetLeft() + "], right=[" + cutout.getSafeInsetRight() + "]");
            }
        } else {
            L.d("[SafeAreaCalculator] calculateInsetsLegacy [" + orientationStr + "], no cutout detected");
        }

        statusBarInset = getStatusBarHeight(context);
        topInset = Math.max(topInset, statusBarInset);
        L.d("[SafeAreaCalculator] calculateInsetsLegacy [" + orientationStr + "], status bar height (px): [" + statusBarInset + "]");
        L.d("[SafeAreaCalculator] calculateInsetsLegacy [" + orientationStr + "], top inset (px) - using MAX(statusBar=" + statusBarInset + ", cutout=" + cutoutInset + ") = [" + topInset + "]");

        int navBarHeightFromResource = getNavigationBarHeight(context, isPortrait);

        boolean navBarVisible = isNavigationBarVisible(context);
        L.d("[SafeAreaCalculator] calculateInsetsLegacy [" + orientationStr + "], nav bar visible: [" + navBarVisible + "], resource height (px): [" + navBarHeightFromResource + "]");

        if (navBarVisible) {
            boolean isGestureNav = navBarHeightFromResource < (int) (density * 40); //  < 40dp likely gesture
            String navType = isGestureNav ? "gesture" : "button";

            navBarInset = navBarHeightFromResource;
            if (navBarInset == 0) {
                navBarInset = getDefaultNavBarInset(isGestureNav, density);
                L.d("[SafeAreaCalculator] calculateInsetsLegacy [" + orientationStr + "], nav bar height is 0, using default [" + navType + "] value (px): [" + navBarInset + "]");
            } else {
                L.d("[SafeAreaCalculator] calculateInsetsLegacy [" + orientationStr + "], nav bar type: [" + navType + "], height (px): [" + navBarInset + "]");
            }

            if (isPortrait) {
                bottomInset = Math.max(bottomInset, navBarInset);
            } else {
                bottomInset = Math.min(bottomInset, navBarInset);
            }
        }

        int width = widthForOrientation - leftInset - rightInset;
        int height = heightForOrientation - topInset - bottomInset;

        // < Android R, we cannot reliably detect nav bar position
        int leftOffset = 0;

        L.d("[SafeAreaCalculator] calculateInsetsLegacy [" + orientationStr + "], final insets (px) - top=[" + topInset + "], bottom=[" + bottomInset + "], left=[" + leftInset + "], right=[" + rightInset + "]");
        L.d("[SafeAreaCalculator] calculateInsetsLegacy [" + orientationStr + "], final safe area (px): [" + width + "x" + height + "], leftOffset=[" + leftOffset + "]");

        return new SafeAreaInsets(width, height, topInset, leftOffset);
    }

    /**
     * Determine if device is using gesture navigation
     * Gesture navigation typically has smaller navigation bar height
     */
    private static boolean isGestureNavigation(@NonNull Insets navBarInsets, float density) {
        int maxInset = Math.max(Math.max(navBarInsets.bottom, navBarInsets.left), navBarInsets.right);
        int dpValue = (int) (maxInset / density);
        return dpValue < 40;
    }

    /**
     * Provides the default navigation bar inset when system-provided inset is zero.
     */
    private static int getDefaultNavBarInset(boolean isGestureNav, float density) {
        return (int) (density * (isGestureNav ? DEFAULT_GESTURE_NAV_BAR_HEIGHT_DP : DEFAULT_BUTTON_NAV_BAR_HEIGHT_DP));
    }

    private static int getStatusBarHeight(@NonNull Context context) {
        int result = 0;
        int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = context.getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    private static int getNavigationBarHeight(@NonNull Context context, boolean isPortrait) {
        int result = 0;
        String resourceName = isPortrait ? "navigation_bar_height" : "navigation_bar_height_landscape";
        int resourceId = context.getResources().getIdentifier(resourceName, "dimen", "android");
        if (resourceId > 0) {
            result = context.getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    @SuppressWarnings("deprecation")
    private static boolean isNavigationBarVisible(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            Display display = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
            DisplayMetrics realMetrics = new DisplayMetrics();
            display.getRealMetrics(realMetrics);

            DisplayMetrics displayMetrics = new DisplayMetrics();
            display.getMetrics(displayMetrics);

            return (realMetrics.widthPixels - displayMetrics.widthPixels) > 0
                || (realMetrics.heightPixels - displayMetrics.heightPixels) > 0;
        }
        return true;
    }

    /**
     * Helper class to hold inset calculations
     */
    private static class SafeAreaInsets {
        final int width;
        final int height;
        final int topOffset;
        final int leftOffset;

        SafeAreaInsets(int width, int height, int topOffset, int leftOffset) {
            this.width = width;
            this.height = height;
            this.topOffset = topOffset;
            this.leftOffset = leftOffset;
        }
    }
}
