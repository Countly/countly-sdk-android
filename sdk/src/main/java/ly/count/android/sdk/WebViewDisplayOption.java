package ly.count.android.sdk;

/**
 * Enum representing the webview display options for Content and Feedback Widgets
 */
public enum WebViewDisplayOption {
    /**
     * Immersive mode - calculates and sends total area, displays content in immersive mode (hiding system UI)
     */
    IMMERSIVE,

    /**
     * Safe area mode - calculates dimensions excluding system UI (status bar, navigation bar, cutout),
     * displays content without hiding system UI
     */
    SAFE_AREA
}
