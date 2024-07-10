package ly.count.android.sdk;

import android.webkit.WebView;
import java.io.Serializable;

interface WebViewUrlListener extends Serializable {
    boolean onUrl(String url, WebView webView);
}
