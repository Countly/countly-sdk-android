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
}
