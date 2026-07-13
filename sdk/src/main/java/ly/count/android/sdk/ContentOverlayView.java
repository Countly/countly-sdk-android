package ly.count.android.sdk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@SuppressLint("ViewConstructor")
class ContentOverlayView extends FrameLayout {

    WebView webView;
    TransparentActivityConfig configPortrait;
    TransparentActivityConfig configLandscape;
    int currentOrientation;
    private ContentCallback contentCallback;
    private final Set<String> allowedLinkSchemes;
    private final ContentUrlHandler contentUrlHandler;
    private Runnable onCloseRunnable;
    private Runnable onWidgetCancelRunnable;
    private boolean isClosed = false;
    private Activity currentHostActivity;
    private WindowManager windowManager;
    private boolean isAddedToWindow = false;
    private boolean isContentLoaded = false;
    private CountlyWebViewClient webViewClient;
    private int savedSystemUiVisibility = -1;
    private boolean isImmersiveModeActive = false;
    private ComponentCallbacks orientationCallback;
    private Application.ActivityLifecycleCallbacks activityLifecycleCallbacks;

    // Returns a Context suitable for constructing the overlay's Views without retaining
    // a strong Java reference to the constructing Activity:
    //   - Pre-API 31: Application context (current behavior; no StrictMode UI-context check exists).
    //   - API 31+: createConfigurationContext from the Activity. The returned ContextImpl is a
    //     lightweight wrapper that does not strongly retain the Activity instance — only an
    //     IBinder activity token, which does not pin the Activity for GC.
    // Note: createConfigurationContext does not produce a UI context per Android's mIsUiContext
    // contract; the StrictMode#detectIncorrectContextUse fix for getSystemService(WINDOW_SERVICE)
    // lives in UtilsDevice.obtainWindowManager (which uses createWindowContext as a fallback).
    @NonNull
    private static Context resolveOverlayContext(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                return activity.createConfigurationContext(activity.getResources().getConfiguration());
            } catch (Throwable ignored) {
                // Fall back to Application context if config-context creation fails.
            }
        }
        return activity.getApplicationContext();
    }

    @SuppressLint("SetJavaScriptEnabled") ContentOverlayView(@NonNull Activity activity,
        @NonNull TransparentActivityConfig portrait,
        @NonNull TransparentActivityConfig landscape,
        int orientation,
        @Nullable ContentCallback callback,
        @NonNull Runnable onClose,
        @Nullable Set<String> allowedLinkSchemes,
        @Nullable ContentUrlHandler contentUrlHandler) {
        // View.mContext must not pin the constructing activity (overlay outlives activity
        // transitions; window attachment uses currentHostActivity). On API 31+ we additionally
        // need a UI context to satisfy StrictMode#detectIncorrectContextUse — see
        // resolveOverlayContext above.
        super(resolveOverlayContext(activity));

        this.configPortrait = portrait;
        this.configLandscape = landscape;
        this.currentOrientation = orientation;
        this.contentCallback = callback;
        this.onCloseRunnable = onClose;
        this.currentHostActivity = activity;
        // Defensive copy so a later config change cannot retroactively alter this overlay's policy.
        this.allowedLinkSchemes = allowedLinkSchemes == null ? null : new HashSet<>(allowedLinkSchemes);
        this.contentUrlHandler = contentUrlHandler;

        setBackgroundColor(Color.TRANSPARENT);
        setClickable(false);
        setFocusable(false);

        // Recalculate safe area offsets using Activity context (may have been calculated with app context)
        TransparentActivityConfig config = getCurrentConfig();
        if (config.useSafeArea) {
            recalculateSafeAreaOffsets(activity);
            config = getCurrentConfig();
        }
        config = setupConfig(activity, config);

        webView = createWebView(activity, config);
        LayoutParams webParams = new LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        addView(webView, webParams);

        // Register for configuration changes via application context since
        // WindowManager views don't receive onConfigurationChanged through the view hierarchy
        registerOrientationCallback(activity);
        registerActivityLifecycleCallback(activity);
    }

    private void registerActivityLifecycleCallback(@NonNull Activity activity) {
        unregisterActivityLifecycleCallback();
        activityLifecycleCallbacks = new OverlayLifecycleCallbacks(this);
        activity.getApplication().registerActivityLifecycleCallbacks(activityLifecycleCallbacks);
    }

    /**
     * Static class to avoid implicit this$0 reference to the outer ContentOverlayView.
     * Anonymous-inner-class callbacks registered with Application#mActivityLifecycleCallbacks
     * leak the outer instance for the entire process lifetime if any future code path drops
     * the overlay without invoking destroy()/close(). The WeakReference + self-cleanup pattern
     * lets the overlay be GC'd as soon as nothing else holds a strong ref to it; the dormant
     * callback then removes itself from the Application's list on its next invocation.
     *
     * NOTE on two separate, non-fixable LeakCanary reports — both architectural, neither
     * is true memory growth:
     *
     * 1. System-singleton reattachment retention: when the same overlay is shown across
     * multiple activity transitions (View.mWindowAttachCount grows), various Android
     * system singletons hold a reference to the currently-attached ViewRootImpl. The
     * two retainers we've observed are InputMethodManager#mCurRootView (set when the
     * user types into the WebView) and WindowManagerGlobal#mRoots (the process-wide
     * list of attached ViewRootImpls, always populated for any attached window).
     * LeakCanary may report the overlay as "leaking" while its own analyzer also
     * reports the View is currently attached — that combination is the heuristic
     * false-positive signal (one overlay reused, not N overlays leaked). Both
     * references are released by the framework when the overlay's window is detached
     * or rebound to another window.
     *
     * 2. ModuleContent.contentOverlay retention while backgrounded: when no activities
     * are visible, ModuleContent.onActivityStopped(count=0) calls detachFromWindow on
     * the overlay (to avoid WindowLeaked) but intentionally keeps the contentOverlay
     * field non-null so the same instance can be re-attached when the user returns.
     * LeakCanary sees a detached View still strongly referenced through the Countly
     * singleton and reports a "leak". This is bounded (single field, one overlay
     * instance) and released the next time ModuleContent replaces or clears the cached
     * overlay reference. Not a growing-over-time leak — intentional caching for the
     * user-returns-to-same-content UX. Process kill (SIGKILL) reclaims it along with
     * everything else, so persistence-on-kill is a non-concern.
     */
    private static final class OverlayLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {
        private final WeakReference<ContentOverlayView> overlayRef;

        OverlayLifecycleCallbacks(@NonNull ContentOverlayView overlay) {
            this.overlayRef = new WeakReference<>(overlay);
        }

        /** Returns the overlay if still alive; otherwise self-unregisters and returns null. */
        @Nullable
        private ContentOverlayView resolveOrCleanup(@NonNull Activity a) {
            ContentOverlayView overlay = overlayRef.get();
            if (overlay == null) {
                try {
                    a.getApplication().unregisterActivityLifecycleCallbacks(this);
                } catch (Exception ignored) {
                    // Application may already be tearing down; safe to ignore.
                }
            }
            return overlay;
        }

        @Override public void onActivityCreated(@NonNull Activity a, @Nullable android.os.Bundle b) {
        }

        @Override public void onActivityStarted(@NonNull Activity a) {
            // Probe in start callbacks too so dead callbacks don't linger across activity navigations.
            resolveOrCleanup(a);
        }

        @Override public void onActivityResumed(@NonNull Activity a) {
        }

        @Override public void onActivityPaused(@NonNull Activity a) {
        }

        @Override
        public void onActivityStopped(@NonNull Activity a) {
            ContentOverlayView overlay = resolveOrCleanup(a);
            if (overlay == null) return;
            if (a == overlay.currentHostActivity && overlay.isAddedToWindow && a.isFinishing()) {
                Log.d(Countly.TAG, "[ContentOverlayView] onActivityStopped, host activity is finishing, removing from window");
                overlay.removeFromWindow();
            }
        }

        @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull android.os.Bundle b) {
        }

        @Override
        public void onActivityDestroyed(@NonNull Activity a) {
            ContentOverlayView overlay = resolveOrCleanup(a);
            if (overlay == null) return;
            if (a == overlay.currentHostActivity) {
                if (overlay.isAddedToWindow) {
                    Log.d(Countly.TAG, "[ContentOverlayView] onActivityDestroyed, host activity destroyed, removing from window");
                    overlay.removeFromWindow();
                }
                // Drop the strong reference to the destroyed activity so it can be GC'd.
                // The overlay is reattached via ModuleContent.onActivityStarted, which calls attachToActivity()
                // and re-sets currentHostActivity for the next host.
                overlay.currentHostActivity = null;
            }
        }
    }

    private void unregisterActivityLifecycleCallback() {
        if (activityLifecycleCallbacks != null) {
            try {
                ((Application) getContext().getApplicationContext())
                    .unregisterActivityLifecycleCallbacks(activityLifecycleCallbacks);
            } catch (Exception e) {
                Log.w(Countly.TAG, "[ContentOverlayView] unregisterActivityLifecycleCallback, failed", e);
            }
            activityLifecycleCallbacks = null;
        }
    }

    private void registerOrientationCallback(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        orientationCallback = new OverlayComponentCallbacks(this, appContext);
        appContext.registerComponentCallbacks(orientationCallback);
    }

    /**
     * Static class to avoid implicit this$0 reference to the outer ContentOverlayView.
     * Same leak-avoidance pattern as OverlayLifecycleCallbacks — see that class for details.
     */
    private static final class OverlayComponentCallbacks implements ComponentCallbacks {
        private final WeakReference<ContentOverlayView> overlayRef;
        private final WeakReference<Context> appContextRef;

        OverlayComponentCallbacks(@NonNull ContentOverlayView overlay, @NonNull Context appContext) {
            this.overlayRef = new WeakReference<>(overlay);
            this.appContextRef = new WeakReference<>(appContext);
        }

        @Override
        public void onConfigurationChanged(@NonNull Configuration newConfig) {
            ContentOverlayView overlay = overlayRef.get();
            if (overlay == null) {
                Context appContext = appContextRef.get();
                if (appContext != null) {
                    try {
                        appContext.unregisterComponentCallbacks(this);
                    } catch (Exception ignored) {
                        // App may be tearing down; safe to ignore.
                    }
                }
                return;
            }
            if (overlay.isClosed || overlay.currentOrientation == newConfig.orientation) {
                return;
            }
            Log.d(Countly.TAG, "[ContentOverlayView] onConfigurationChanged, orientation changed from [" + overlay.currentOrientation + "] to [" + newConfig.orientation + "]");
            overlay.currentOrientation = newConfig.orientation;

            Activity activity = overlay.currentHostActivity;
            if (activity != null && !activity.isFinishing()) {
                overlay.handleOrientationChange(activity);
            }
        }

        @Override
        public void onLowMemory() {
            // no-op
        }
    }

    private void unregisterOrientationCallback() {
        if (orientationCallback != null) {
            getContext().getApplicationContext().unregisterComponentCallbacks(orientationCallback);
            orientationCallback = null;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            // Forward back press to the host activity so normal navigation works
            Activity activity = currentHostActivity;
            if (activity != null && !activity.isFinishing()) {
                return activity.dispatchKeyEvent(event);
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // Dynamically transfer IME focus between the overlay and the host activity:
        //   - ACTION_OUTSIDE (delivered because of FLAG_WATCH_OUTSIDE_TOUCH): user
        //     touched outside the overlay's bounds; the touch already passed through
        //     to the activity via FLAG_NOT_TOUCH_MODAL, but we additionally restore
        //     FLAG_NOT_FOCUSABLE so the activity's EditText can claim IME focus.
        //   - ACTION_DOWN (touch inside the overlay's bounds): clear FLAG_NOT_FOCUSABLE
        //     so the WebView's form fields can bring up the keyboard.
        // Consumes nothing; existing touch handling (WebView, etc.) continues normally.
        if (ev.getAction() == MotionEvent.ACTION_OUTSIDE) {
            setWindowFocusable(false);
            return true;
        }
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            setWindowFocusable(true);
        }
        return super.dispatchTouchEvent(ev);
    }

    private void setWindowFocusable(boolean focusable) {
        if (!isAddedToWindow || windowManager == null) {
            return;
        }
        try {
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) getLayoutParams();
            if (lp == null) {
                return;
            }
            boolean alreadyFocusable = (lp.flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) == 0;
            if (alreadyFocusable == focusable) {
                return;
            }
            if (focusable) {
                lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            } else {
                lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            }
            windowManager.updateViewLayout(this, lp);
        } catch (Exception e) {
            Log.w(Countly.TAG, "[ContentOverlayView] setWindowFocusable, failed to update flags", e);
        }
    }

    private TransparentActivityConfig getCurrentConfig() {
        if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            return configLandscape;
        }
        return configPortrait;
    }

    @SuppressWarnings("deprecation")
    private WindowManager.LayoutParams createWindowParams(@NonNull Activity activity, @NonNull TransparentActivityConfig config) {
        int adjustedX = config.x;
        int adjustedY = config.y;

        if (config.useSafeArea) {
            if (config.leftOffset > 0) {
                adjustedX += config.leftOffset;
                Log.d(Countly.TAG, "[ContentOverlayView] createWindowParams, adjusting x from [" + config.x + "] to [" + adjustedX + "] (leftOffset: " + config.leftOffset + ")");
            }
            if (config.topOffset > 0) {
                adjustedY += config.topOffset;
                Log.d(Countly.TAG, "[ContentOverlayView] createWindowParams, adjusting y from [" + config.y + "] to [" + adjustedY + "] (topOffset: " + config.topOffset + ")");
            }
        }

        // FLAG_NOT_FOCUSABLE: overlay does NOT grab IME focus by default, so the
        //   underlying Activity's EditText can receive keyboard input even while the
        //   overlay is shown. Toggled off in dispatchTouchEvent on inside touches so
        //   the WebView's <input>/<textarea> can still bring up the keyboard.
        // FLAG_WATCH_OUTSIDE_TOUCH: deliver an ACTION_OUTSIDE event to the overlay
        //   when the user touches outside its bounds, so we can return focus to the
        //   non-focusable state. The actual touch still passes through to the host
        //   activity via FLAG_NOT_TOUCH_MODAL.
        int flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;

        if (!isContentLoaded) {
            flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            config.width,
            config.height,
            WindowManager.LayoutParams.TYPE_APPLICATION,
            flags,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = adjustedX;
        params.y = adjustedY;
        params.token = activity.getWindow().getAttributes().token;
        return params;
    }

    void attachToActivity(@NonNull Activity activity) {
        if (isClosed) {
            Log.w(Countly.TAG, "[ContentOverlayView] attachToActivity, overlay is closed, skipping");
            return;
        }

        if (activity.isFinishing() || activity.isDestroyed()) {
            Log.w(Countly.TAG, "[ContentOverlayView] attachToActivity, activity is finishing or destroyed, skipping");
            return;
        }

        Window window = activity.getWindow();
        if (window == null) {
            Log.w(Countly.TAG, "[ContentOverlayView] attachToActivity, activity window is null, skipping");
            return;
        }

        // Check if we're already attached to this activity
        if (currentHostActivity == activity && isAddedToWindow) {
            // Still check for orientation changes — WindowManager views don't get onConfigurationChanged
            int currentOr = activity.getResources().getConfiguration().orientation;
            if (currentOrientation != currentOr) {
                currentOrientation = currentOr;
                handleOrientationChange(activity);
            }
            if (isImmersiveModeActive) {
                applyImmersiveFlags(activity);
            }
            return;
        }

        currentHostActivity = activity;

        int newOrientation = activity.getResources().getConfiguration().orientation;
        boolean orientationChanged = currentOrientation != newOrientation;
        if (orientationChanged) {
            currentOrientation = newOrientation;
        }

        TransparentActivityConfig config = getCurrentConfig();
        if (config.useSafeArea) {
            recalculateSafeAreaOffsets(activity);
            config = getCurrentConfig();
        }
        config = setupConfig(activity, config);

        WindowManager.LayoutParams wmParams = createWindowParams(activity, config);

        // Window token is immutable after addView — must remove+add to change activity.
        // This happens during the activity transition animation so the user doesn't
        // perceive the single-frame remove/add.
        removeFromWindow();
        addToWindow(activity, wmParams, config, orientationChanged);
    }

    private void addToWindow(@NonNull Activity activity, @NonNull WindowManager.LayoutParams wmParams,
        @NonNull TransparentActivityConfig config, boolean orientationChanged) {
        WindowManager wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) {
            Log.w(Countly.TAG, "[ContentOverlayView] addToWindow, WindowManager is null, skipping");
            return;
        }

        windowManager = wm;

        // Try to add immediately — if the token is valid, this avoids a frame delay
        try {
            wmParams.token = activity.getWindow().getAttributes().token;
            windowManager.addView(this, wmParams);
            isAddedToWindow = true;
            onWindowAttached(activity, config, orientationChanged);
        } catch (WindowManager.BadTokenException e) {
            // Token not ready yet — post to decor view to retry on next frame
            Log.w(Countly.TAG, "[ContentOverlayView] addToWindow, token not ready, retrying on next frame");
            View decor = activity.getWindow().getDecorView();
            decor.post(() -> {
                if (isClosed || activity.isFinishing() || isAddedToWindow) {
                    return;
                }
                try {
                    wmParams.token = activity.getWindow().getAttributes().token;
                    windowManager.addView(this, wmParams);
                    isAddedToWindow = true;
                    onWindowAttached(activity, config, orientationChanged);
                } catch (Exception e2) {
                    Log.e(Countly.TAG, "[ContentOverlayView] addToWindow, retry also failed", e2);
                    isAddedToWindow = false;
                }
            });
        } catch (Exception e) {
            Log.e(Countly.TAG, "[ContentOverlayView] addToWindow, failed to add view", e);
            isAddedToWindow = false;
        }
    }

    private void onWindowAttached(@NonNull Activity activity, @NonNull TransparentActivityConfig config, boolean orientationChanged) {
        hideKeyboard(activity);

        if (!config.useSafeArea) {
            enterImmersiveMode(activity);
        }

        Log.d(Countly.TAG, "[ContentOverlayView] onWindowAttached, attached to [" + activity.getClass().getSimpleName() + "], orientation: [" + currentOrientation + "], size: [" + config.width + "x" + config.height + "]");

        if (orientationChanged) {
            notifyWebViewOfResize(activity);
        }
    }

    private void enableTouchInteraction() {
        if (!isAddedToWindow || windowManager == null) {
            return;
        }
        try {
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) getLayoutParams();
            if (lp != null && (lp.flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0) {
                lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                windowManager.updateViewLayout(this, lp);
            }
        } catch (Exception e) {
            Log.w(Countly.TAG, "[ContentOverlayView] enableTouchInteraction, failed to update flags", e);
        }
    }

    private void removeFromWindow() {
        if (isAddedToWindow && windowManager != null) {
            // Expedite any pending scrollbar-fade Runnables: scrolling inside the WebView
            // schedules a ScrollabilityCache fade Runnable on the main MessageQueue. If
            // the View is detached before that Runnable fires, the pending Message keeps
            // ViewRootImpl alive (and through it, this overlay) for the platform's
            // scrollbar-fade window — visible as a transient leak under repeated show/close
            // cycles. Calling awakenScrollBars(0) re-schedules the existing fade with zero
            // delay, so the Message drains on the next message-loop iteration and the View
            // becomes GC-eligible promptly. No-op if mScrollCache wasn't created (no scrolling
            // occurred). Only the WebView can have a scroll cache here — the FrameLayout
            // itself is non-scrollable.
            if (webView instanceof CountlyWebView) {
                try {
                    ((CountlyWebView) webView).expediteScrollbarFade();
                } catch (Exception e) {
                    // Best-effort drain; if this ever throws, leave a breadcrumb so a future
                    // regression doesn't silently re-introduce the transient ViewRoot retention.
                    Log.w(Countly.TAG, "[ContentOverlayView] removeFromWindow, scrollbar fade expedite failed", e);
                }
            }

            try {
                // Use removeViewImmediate for synchronous removal to prevent WindowLeaked.
                // WindowManager.removeView() is async (posts MSG_DIE), so the view may still
                // be registered when Activity.performDestroy() checks for leaked windows.
                java.lang.reflect.Method removeImmediate =
                    windowManager.getClass().getMethod("removeViewImmediate", View.class);
                removeImmediate.invoke(windowManager, this);
            } catch (Exception e) {
                try {
                    windowManager.removeView(this);
                } catch (Exception e2) {
                    Log.w(Countly.TAG, "[ContentOverlayView] removeFromWindow, failed to remove view", e2);
                }
            }
            isAddedToWindow = false;
        }
    }

    /**
     * Removes the overlay from WindowManager without destroying it.
     * Called when no activities are visible (e.g., app sent to background or killed from recents)
     * to prevent WindowLeaked errors. The overlay can be re-attached via attachToActivity()
     * when an activity becomes available again.
     */
    void detachFromWindow() {
        Log.d(Countly.TAG, "[ContentOverlayView] detachFromWindow, removing from window");
        removeFromWindow();
    }

    private TransparentActivityConfig setupConfig(@NonNull Context context, @NonNull TransparentActivityConfig config) {
        TransparentActivityConfig result = config.copy();

        if (!result.useSafeArea) {
            final DisplayMetrics metrics = UtilsDevice.getDisplayMetrics(context);
            if (result.width < 1) {
                result.width = metrics.widthPixels;
            }
            if (result.height < 1) {
                result.height = metrics.heightPixels;
            }
        } else {
            // Clamp dimensions on the copy so content doesn't exceed the safe area.
            // This must be done here (not on the original configs) because SafeAreaCalculator
            // can return stale WindowMetrics during orientation transitions.
            SafeAreaDimensions safeArea = SafeAreaCalculator.calculateSafeAreaDimensions(context, Countly.sharedInstance().L);
            boolean isLandscape = currentOrientation == Configuration.ORIENTATION_LANDSCAPE;
            int safeWidth = isLandscape ? safeArea.landscapeWidth : safeArea.portraitWidth;
            int safeHeight = isLandscape ? safeArea.landscapeHeight : safeArea.portraitHeight;

            if (result.width > safeWidth) {
                result.width = safeWidth;
            }
            if (result.height > safeHeight) {
                result.height = safeHeight;
            }
        }

        if (result.x < 1) {
            result.x = 0;
        }
        if (result.y < 1) {
            result.y = 0;
        }

        return result;
    }

    private void handleOrientationChange(@NonNull Activity activity) {
        if (isClosed || !isAddedToWindow || windowManager == null) {
            return;
        }

        // Wait for the decor view's layout pass to complete before reading display metrics.
        // A simple post() is not enough — on some API levels, display metrics are still stale
        // one frame after onConfigurationChanged. OnGlobalLayoutListener fires after the
        // system has finished the layout pass, guaranteeing metrics reflect the new orientation.
        View decor = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
        if (decor != null) {
            decor.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    decor.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    applyOrientationUpdate(activity);
                }
            });
        } else {
            applyOrientationUpdate(activity);
        }
    }

    private void applyOrientationUpdate(@NonNull Activity activity) {
        if (isClosed || !isAddedToWindow || windowManager == null || activity.isFinishing()) {
            return;
        }

        // Re-read actual orientation from activity instead of relying on cached value.
        // ComponentCallbacks registered with Application context can deliver stale orientation
        // during rapid rotations when activities handle configChanges="orientation|screenSize".
        int actualOrientation = activity.getResources().getConfiguration().orientation;
        if (actualOrientation != Configuration.ORIENTATION_UNDEFINED) {
            currentOrientation = actualOrientation;
        }

        TransparentActivityConfig currentConfig = getCurrentConfig();

        if (currentConfig.useSafeArea) {
            recalculateSafeAreaOffsets(activity);
            currentConfig = getCurrentConfig();
        }

        currentConfig = setupConfig(activity, currentConfig);
        WindowManager.LayoutParams wmParams = createWindowParams(activity, currentConfig);

        try {
            windowManager.updateViewLayout(this, wmParams);
        } catch (Exception e) {
            Log.w(Countly.TAG, "[ContentOverlayView] applyOrientationUpdate, failed to update layout", e);
        }

        notifyWebViewOfResize(activity);
    }

    private void notifyWebViewOfResize(@NonNull Activity activity) {
        if (webView == null || isClosed) {
            return;
        }

        TransparentActivityConfig currentConfig = getCurrentConfig();
        float density = activity.getResources().getDisplayMetrics().density;
        int widthPx, heightPx;

        if (currentConfig.useSafeArea) {
            SafeAreaDimensions safeArea = SafeAreaCalculator.calculateSafeAreaDimensions(activity, Countly.sharedInstance().L);
            if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                widthPx = safeArea.landscapeWidth;
                heightPx = safeArea.landscapeHeight;
            } else {
                widthPx = safeArea.portraitWidth;
                heightPx = safeArea.portraitHeight;
            }
        } else {
            final DisplayMetrics metrics = UtilsDevice.getDisplayMetrics(activity);
            widthPx = metrics.widthPixels;
            heightPx = metrics.heightPixels;
        }

        int scaledWidth = Math.round(widthPx / density);
        int scaledHeight = Math.round(heightPx / density);
        webView.loadUrl("javascript:window.postMessage({type: 'resize', width: " + scaledWidth + ", height: " + scaledHeight + "}, '*');");
    }

    // --- URL Action Handling ---

    boolean contentUrlAction(String url, WebView view) {
        if (url == null || view == null || isClosed) {
            return false;
        }
        Log.d(Countly.TAG, "[ContentOverlayView] contentUrlAction, url: [" + url + "]");
        Map<String, Object> query = splitQuery(url);

        Object clyEvent = query.get("?cly_x_action_event");
        if (clyEvent == null || !clyEvent.equals("1")) {
            Log.w(Countly.TAG, "[ContentOverlayView] contentUrlAction, not a countly action event url");
            return false;
        }

        Object clyAction = query.get("action");
        if (clyAction instanceof String) {
            String action = (String) clyAction;
            switch (action) {
                case "event":
                    eventAction(query);
                    break;
                case "link":
                    linkAction(query, view);
                    break;
                case "resize_me":
                    resizeMeAction(query);
                    break;
                default:
                    Log.e(Countly.TAG, "[ContentOverlayView] contentUrlAction, unknown action:[" + action + "]");
                    break;
            }
        }

        if (query.containsKey("close") && Objects.equals(query.get("close"), "1")) {
            close(query);
        }

        return true;
    }

    void setOnWidgetCancelRunnable(@Nullable Runnable runnable) {
        this.onWidgetCancelRunnable = runnable;
    }

    boolean widgetUrlAction(String url, WebView view) {
        if (url == null || view == null || isClosed) {
            return false;
        }
        Map<String, Object> query = splitQuery(url);
        Object widgetCommand = query.get("?cly_widget_command");

        if (widgetCommand == null || !widgetCommand.equals("1")) {
            return false;
        }

        if (query.containsKey("close") && Objects.equals(query.get("close"), "1")) {
            close(query);

            if (onWidgetCancelRunnable != null) {
                onWidgetCancelRunnable.run();
            }
        }

        return true;
    }

    private void eventAction(Map<String, Object> query) {
        Log.i(Countly.TAG, "[ContentOverlayView] eventAction, event action detected");
        if (query.containsKey("event")) {
            JSONArray event = (JSONArray) query.get("event");
            if (event == null) {
                Log.w(Countly.TAG, "[ContentOverlayView] eventAction, event is null");
                return;
            }
            for (int i = 0; i < event.length(); i++) {
                try {
                    JSONObject eventJson = event.getJSONObject(i);
                    Map<String, Object> segmentation = new HashMap<>();
                    JSONObject sgJson = eventJson.optJSONObject("sg");
                    JSONObject segmentationJson = eventJson.optJSONObject("segmentation");

                    if (sgJson != null) {
                        segmentationJson = sgJson;
                    }

                    if (segmentationJson == null) {
                        Log.w(Countly.TAG, "[ContentOverlayView] eventAction, missing segmentation data");
                        continue;
                    }

                    JSONArray names = segmentationJson.names();
                    if (names == null) {
                        continue;
                    }

                    for (int j = 0; j < names.length(); j++) {
                        String key = names.getString(j);
                        Object value = segmentationJson.get(key);
                        segmentation.put(key, value);
                    }

                    Countly.sharedInstance().events().recordEvent(eventJson.get("key").toString(), segmentation);
                } catch (JSONException e) {
                    Log.e(Countly.TAG, "[ContentOverlayView] eventAction, Failed to parse event JSON", e);
                }
            }

            Countly.sharedInstance().requestQueue().attemptToSendStoredRequests();
        }
    }

    private void startActivityFromOverlay(@NonNull Intent intent) {
        // Prefer the current host activity so the launched intent stays in the same task.
        // Fall back to Application context with NEW_TASK (mContext is App since the overlay
        // outlives activities), which is the only legal way to start an activity from a
        // non-activity context.
        Activity host = currentHostActivity;
        if (host != null && !host.isFinishing() && !host.isDestroyed()) {
            host.startActivity(intent);
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        }
    }

    // Dispatches an ACTION_VIEW intent for a URL originating from web content, gated by the shared
    // scheme policy: with no allow-list the dangerous schemes (file/content/javascript/jar/data) are
    // blocked while http(s) and deep links are allowed; with an allow-list configured, only those
    // schemes pass. Component/selector and flags are cleared so the intent cannot be redirected to a
    // specific (possibly internal) target.
    private void startSafeExternalIntent(@NonNull String url) {
        // Give the app's content URL handler first refusal (e.g. to route its own deep link). If it
        // reports it handled the URL, the SDK does not dispatch an intent. A handler exception is
        // caught so it can never break link handling; on false/absent handler the SDK opens as usual.
        if (contentUrlHandler != null) {
            try {
                if (contentUrlHandler.onContentUrl(url)) {
                    Log.d(Countly.TAG, "[ContentOverlayView] startSafeExternalIntent, url handled by content URL handler: [" + url + "]");
                    return;
                }
            } catch (Throwable t) {
                Log.e(Countly.TAG, "[ContentOverlayView] startSafeExternalIntent, content URL handler threw", t);
            }
        }

        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        if (!Utils.isExternalSchemeAllowed(scheme, allowedLinkSchemes)) {
            Log.w(Countly.TAG, "[ContentOverlayView] startSafeExternalIntent, blocked link with disallowed scheme: [" + scheme + "]");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setComponent(null);
        intent.setSelector(null);
        intent.setFlags(0);
        startActivityFromOverlay(intent);
    }

    private boolean linkAction(Map<String, Object> query, WebView view) {
        Log.i(Countly.TAG, "[ContentOverlayView] linkAction, link action detected");
        if (!query.containsKey("link")) {
            return false;
        }
        Object link = query.get("link");
        if (!(link instanceof String)) {
            return false;
        }

        startSafeExternalIntent(link.toString());
        return true;
    }

    private void resizeMeAction(Map<String, Object> query) {
        Log.i(Countly.TAG, "[ContentOverlayView] resizeMeAction, resize_me action detected");
        if (!query.containsKey("resize_me")) {
            return;
        }
        Object resizeMe = query.get("resize_me");
        if (!(resizeMe instanceof JSONObject)) {
            return;
        }
        try {
            Activity activity = currentHostActivity;
            if (activity == null || activity.isFinishing()) {
                return;
            }

            float density = activity.getResources().getDisplayMetrics().density;
            JSONObject resizeMeJson = (JSONObject) resizeMe;
            JSONObject portrait = resizeMeJson.getJSONObject("p");
            JSONObject landscape = resizeMeJson.getJSONObject("l");

            configPortrait.x = (int) Math.ceil(portrait.getInt("x") * density);
            configPortrait.y = (int) Math.ceil(portrait.getInt("y") * density);
            configPortrait.width = (int) Math.ceil(portrait.getInt("w") * density);
            configPortrait.height = (int) Math.ceil(portrait.getInt("h") * density);

            configLandscape.x = (int) Math.ceil(landscape.getInt("x") * density);
            configLandscape.y = (int) Math.ceil(landscape.getInt("y") * density);
            configLandscape.width = (int) Math.ceil(landscape.getInt("w") * density);
            configLandscape.height = (int) Math.ceil(landscape.getInt("h") * density);

            resizeContentInternal(activity);
        } catch (JSONException e) {
            Log.e(Countly.TAG, "[ContentOverlayView] resizeMeAction, Failed to parse resize JSON", e);
        }
    }

    private void resizeContentInternal(@NonNull Activity activity) {
        if (!isAddedToWindow || windowManager == null) {
            return;
        }
        TransparentActivityConfig config = setupConfig(activity, getCurrentConfig());
        try {
            windowManager.updateViewLayout(this, createWindowParams(activity, config));
        } catch (Exception e) {
            Log.w(Countly.TAG, "[ContentOverlayView] resizeContentInternal, failed to update layout", e);
        }
    }

    private Map<String, Object> splitQuery(@NonNull String url) {
        Map<String, Object> query_pairs = new HashMap<>();
        String[] pairs = url.split(Utils.COMM_URL + "/?");
        if (pairs.length != 2) {
            return query_pairs;
        }

        String[] pairs2 = pairs[1].split("&");
        for (String pair : pairs2) {
            int idx = pair.indexOf('=');
            if (idx < 0) {
                continue;
            }
            String key = pair.substring(0, idx);
            String value = pair.substring(idx + 1);

            try {
                if ("event".equals(key)) {
                    query_pairs.put(key, new JSONArray(value));
                } else if ("resize_me".equals(key)) {
                    query_pairs.put(key, new JSONObject(value));
                } else {
                    query_pairs.put(key, value);
                }
            } catch (JSONException e) {
                Log.e(Countly.TAG, "[ContentOverlayView] splitQuery, Failed to parse JSON", e);
            }
        }

        return query_pairs;
    }

    private void recalculateSafeAreaOffsets(@NonNull Activity activity) {
        SafeAreaDimensions safeArea = SafeAreaCalculator.calculateSafeAreaDimensions(activity, Countly.sharedInstance().L);

        // Update offsets with correct values from Activity context
        configPortrait.topOffset = safeArea.portraitTopOffset;
        configPortrait.leftOffset = safeArea.portraitLeftOffset;
        configLandscape.topOffset = safeArea.landscapeTopOffset;
        configLandscape.leftOffset = safeArea.landscapeLeftOffset;

        // Note: dimension clamping is done non-destructively in setupConfig() on the copy,
        // not here on the originals. Clamping originals is destructive because SafeAreaCalculator
        // can return stale WindowMetrics during orientation transitions, permanently corrupting
        // the config dimensions.
    }

    // --- Keyboard ---

    private void hideKeyboard(@NonNull Activity activity) {
        View focused = activity.getCurrentFocus();
        if (focused != null) {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
                focused.clearFocus();
            }
        }
    }

    // --- Immersive Mode ---

    private void enterImmersiveMode(@NonNull Activity activity) {
        isImmersiveModeActive = true;
        applyImmersiveFlags(activity);
        Log.d(Countly.TAG, "[ContentOverlayView] enterImmersiveMode, system bars hidden");
    }

    @SuppressWarnings("deprecation")
    private void applyImmersiveFlags(@NonNull Activity activity) {
        int immersiveFlags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN;

        // Apply to the activity's window (controls the system bars)
        Window window = activity.getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController activityController = window.getInsetsController();
            if (activityController != null) {
                activityController.hide(android.view.WindowInsets.Type.systemBars());
                activityController.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            View decorView = window.getDecorView();
            int currentFlags = decorView.getSystemUiVisibility();
            if (savedSystemUiVisibility == -1) {
                savedSystemUiVisibility = currentFlags;
            }
            decorView.setSystemUiVisibility(immersiveFlags);
        }

        // Also apply to our own overlay view (since it's a separate WindowManager window)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController overlayController = getWindowInsetsController();
            if (overlayController != null) {
                overlayController.hide(android.view.WindowInsets.Type.systemBars());
                overlayController.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            setSystemUiVisibility(immersiveFlags);
        }
    }

    @SuppressWarnings("deprecation")
    private void exitImmersiveMode() {
        if (!isImmersiveModeActive) {
            return;
        }
        isImmersiveModeActive = false;

        Activity activity = currentHostActivity;
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        // Restore activity's window
        Window window = activity.getWindow();
        if (window == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController activityController = window.getInsetsController();
            if (activityController != null) {
                activityController.show(android.view.WindowInsets.Type.systemBars());
            }
        } else {
            if (savedSystemUiVisibility != -1) {
                window.getDecorView().setSystemUiVisibility(savedSystemUiVisibility);
                savedSystemUiVisibility = -1;
            }
        }

        // Restore our overlay view
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController overlayController = getWindowInsetsController();
            if (overlayController != null) {
                overlayController.show(android.view.WindowInsets.Type.systemBars());
            }
        } else {
            setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }

        Log.d(Countly.TAG, "[ContentOverlayView] exitImmersiveMode, system bars restored");
    }

    // --- Close / Destroy ---

    void close(Map<String, Object> contentData) {
        if (isClosed) {
            return;
        }
        isClosed = true;

        Log.d(Countly.TAG, "[ContentOverlayView] close, closing content overlay");

        exitImmersiveMode();
        unregisterOrientationCallback();
        unregisterActivityLifecycleCallback();

        if (contentCallback != null) {
            contentCallback.onContentCallback(ContentStatus.CLOSED, contentData);
            contentCallback = null;
        }

        cleanupWebView();
        removeFromWindow();

        if (onCloseRunnable != null) {
            onCloseRunnable.run();
            onCloseRunnable = null;
        }
    }

    void destroy() {
        Log.d(Countly.TAG, "[ContentOverlayView] destroy, destroying content overlay");

        exitImmersiveMode();
        isClosed = true;

        unregisterOrientationCallback();
        unregisterActivityLifecycleCallback();
        cleanupWebView();
        removeFromWindow();

        contentCallback = null;
        onCloseRunnable = null;
        onWidgetCancelRunnable = null;
        currentHostActivity = null;
        windowManager = null;
    }

    private void cleanupWebView() {
        if (webViewClient != null) {
            webViewClient.cancel();
            webViewClient = null;
        }
        if (webView != null) {
            webView.stopLoading();
            webView.setWebViewClient(new WebViewClient());
            webView.loadUrl("about:blank");
            removeView(webView);
            // Post destroy() to the end of the message queue so any pending
            // Chromium async callbacks (e.g. loadingStateChanged) drain first.
            final WebView wv = webView;
            webView = null;
            wv.post(wv::destroy);
        }
    }

    // --- WebView Creation ---

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createWebView(@NonNull Activity activity, @NonNull TransparentActivityConfig config) {
        // WebView's mContext must not retain the constructing activity, since the overlay
        // (and its WebView) outlives activity transitions. Activity-specific operations route
        // through currentHostActivity. See resolveOverlayContext for the API 31+ UI-context handling.
        WebView wv = new CountlyWebView(resolveOverlayContext(activity));
        wv.setVisibility(View.INVISIBLE);
        LayoutParams webLayoutParams = new LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        wv.setLayoutParams(webLayoutParams);

        wv.setBackgroundColor(Color.TRANSPARENT);

        WebSettings settings = wv.getSettings();
        settings.setJavaScriptEnabled(true); // required for interactive content
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        // Security hardening (defense-in-depth). The overlay only ever loads server-issued HTTPS
        // content, so disabling local file/content access closes the local-file exfiltration vector
        // without affecting legitimate content. Sub-resource scheme restrictions are enforced in
        // CountlyWebViewClient. Shared with the feedback/ratings WebViews via Utils.
        Utils.applyWebViewSecurityDefaults(settings);

        wv.clearCache(true);
        wv.clearHistory();

        CountlyWebViewClient client = new CountlyWebViewClient(allowedLinkSchemes);
        webViewClient = client;
        client.registerWebViewUrlListener((url, webView) -> {
            if (url.startsWith(Utils.COMM_URL)) {
                if (url.contains("cly_x_action_event")) {
                    return contentUrlAction(url, webView);
                } else if (url.contains("cly_widget_command")) {
                    return widgetUrlAction(url, webView);
                }
            }

            if (url.endsWith("cly_x_int=1")) {
                startSafeExternalIntent(url);
                return true;
            }

            return false;
        });

        client.afterPageFinished = failed -> {
            if (isClosed) {
                return;
            }
            if (failed) {
                Log.w(Countly.TAG, "[ContentOverlayView] page load failed, closing overlay");
                close(new HashMap<>());
            } else {
                Log.d(Countly.TAG, "[ContentOverlayView] page loaded successfully, making WebView visible");
                isContentLoaded = true;
                if (webView != null) {
                    webView.setVisibility(View.VISIBLE);
                }
                enableTouchInteraction();
            }
        };

        wv.setWebViewClient(client);
        wv.loadUrl(config.url);
        return wv;
    }
}
