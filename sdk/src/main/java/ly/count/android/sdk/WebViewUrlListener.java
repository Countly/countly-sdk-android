package ly.count.android.sdk;

import android.webkit.WebView;
import java.io.Serializable;

interface WebViewUrlListener extends Serializable {
    String URL_START = "https://countly_action_event?";

    boolean onUrl(String url, WebView webView);
}
