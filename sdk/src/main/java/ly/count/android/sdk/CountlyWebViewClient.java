package ly.count.android.sdk;

import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

class CountlyWebViewClient extends WebViewClient {
    private final List<WebViewUrlListener> listeners;

    public CountlyWebViewClient() {
        super();
        this.listeners = new ArrayList<>();
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        String url = request.getUrl().toString();
        Log.v(Countly.TAG, "[CountlyWebViewClient] shouldOverrideUrlLoading, url: [" + url + "]");
        try {
            url = URLDecoder.decode(url, "UTF-8");
        } catch (Exception e) {
            Log.e(Countly.TAG, "[CountlyWebViewClient] shouldOverrideUrlLoading, Failed to decode url", e);
            return false;
        }

        Log.d(Countly.TAG, "[CountlyWebViewClient] shouldOverrideUrlLoading, urlDecoded: [" + url + "]");

        for (WebViewUrlListener listener : listeners) {
            if (listener.onUrl(url, view)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
        Log.e("CountlyURL", url + " 1");

        // Countly.sharedInstance().L.i("attempting to load resource: " + url);
        return null;
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        Log.e("CountlyURL", request.getUrl() + " 0");
        String url = request.getUrl().toString();

        // Replace the port number if it matches your condition
        if (url.contains("/o/")) {
            url = url.replace("6001", "3001");

            try {
                // Open the connection to the modified URL
                URL newUrl = new URL(url);
                HttpURLConnection connection = (HttpURLConnection) newUrl.openConnection();
                connection.setRequestMethod("GET");
                connection.connect();

                // Get the MIME type and encoding from the server response
                String contentType = connection.getContentType(); // e.g., "text/html; charset=UTF-8"
                String mimeType = contentType.split(";")[0].trim();
                String encoding = contentType.contains("charset=") ? contentType.split("charset=")[1].trim() : "UTF-8";

                // Return the response as a WebResourceResponse
                return new WebResourceResponse(
                    mimeType,
                    encoding,
                    connection.getInputStream()
                );
            } catch (Exception e) {
                Log.e("CountlyURL", "Error intercepting request: " + e.getMessage(), e);
            }
        }

        // Let the WebView handle the original request
        return null;
    }

    public void registerWebViewUrlListener(WebViewUrlListener listener) {
        this.listeners.add(listener);
    }
}
