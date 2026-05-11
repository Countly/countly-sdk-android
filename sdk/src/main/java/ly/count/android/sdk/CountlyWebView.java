package ly.count.android.sdk;

import android.content.Context;
import android.webkit.WebView;

class CountlyWebView extends WebView {
    public CountlyWebView(Context context) {
        super(context);
    }

    /**
     * Without this override, the keyboard is not showing
     */
    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    /**
     * Expedites any pending scrollbar-fade Runnable by re-scheduling it with zero delay.
     * Used by ContentOverlayView before detach to drain MessageQueue entries that would
     * otherwise hold the ViewRootImpl alive for ~550ms (transient leak under stress).
     * No-op if no scroll cache exists. Calls protected View#awakenScrollBars(int).
     */
    void expediteScrollbarFade() {
        awakenScrollBars(0);
    }
}
