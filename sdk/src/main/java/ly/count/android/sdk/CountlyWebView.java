package ly.count.android.sdk;

import android.content.Context;
import android.webkit.WebView;

class CountlyWebView extends WebView {
    ActivityCallback callback;

    public CountlyWebView(Context context, ActivityCallback callback) {
        super(context);
        this.callback = callback;
    }

    /**
     * Without this override, the keyboard is not showing
     */
    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }
}
