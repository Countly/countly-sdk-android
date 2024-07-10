package ly.count.android.sdk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.RelativeLayout;
import androidx.annotation.NonNull;
import java.util.List;

public class TransparentActivity extends Activity {

    static final String CONFIGURATION = "configuration";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);

        // Get extras
        Intent intent = getIntent();
        TransparentActivityConfig config = (TransparentActivityConfig) intent.getSerializableExtra(CONFIGURATION);
        if (config == null) {
            finish();
            return;
        }
        int width = config.width;
        int height = config.height;

        // Configure window layout parameters
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.x = config.x;
        params.y = config.y;
        params.height = height;
        params.width = width;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        getWindow().setAttributes(params);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        // Create and configure the layout
        RelativeLayout relativeLayout = new RelativeLayout(this);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(width, height);
        relativeLayout.setLayoutParams(layoutParams);
        WebView webView = createWebView(config.url, width, height, config.listeners);

        // Add views
        relativeLayout.addView(webView);
        setContentView(relativeLayout);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createWebView(String uri, int width, int height, List<WebViewUrlListener> listeners) {
        WebView webView = new CountlyWebView(this);
        RelativeLayout.LayoutParams webLayoutParams = new RelativeLayout.LayoutParams(width, height);
        webLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        webLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_END);
        webView.setLayoutParams(webLayoutParams);

        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        webView.clearCache(true);
        webView.clearHistory();

        CountlyWebViewClient client = new CountlyWebViewClient();
        client.registerWebViewUrlListeners(listeners);

        webView.setWebViewClient(client);
        webView.loadUrl(uri);
        return webView;
    }

    /**
     * Show the widget
     * TODO remove this method
     *
     * @param context The context
     * @param config The configuration
     */
    public static void showActivity(@NonNull Context context, @NonNull TransparentActivityConfig config) {
        assert context != null;
        int screenHeight = Resources.getSystem().getDisplayMetrics().heightPixels; // only get the parent pixel size
        int screenWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
        tweakSize(screenWidth, screenHeight, config);

        Intent intent = new Intent(context, TransparentActivity.class);
        intent.putExtra(CONFIGURATION, config);
        float density = Resources.getSystem().getDisplayMetrics().density;

        Log.e("PIXEL", "Density " + density);
        Log.e("PIXEL", "Xdpi " + Resources.getSystem().getDisplayMetrics().xdpi);
        Log.e("PIXEL", "Ydpi " + Resources.getSystem().getDisplayMetrics().ydpi);
        Log.e("PIXEL", "screenHeight " + screenHeight);
        Log.e("PIXEL", "screenWidth " + screenWidth);
        Log.e("PIXEL", "h/D" + screenHeight / density);
        Log.e("PIXEL", "w/D" + screenWidth / density);

        Log.e("PIXEL", "X " + config.x);
        Log.e("PIXEL", "Y " + config.y);
        Log.e("PIXEL", "Width " + config.width);
        Log.e("PIXEL", "Height " + config.height);

        context.startActivity(intent);
    }

    private static void tweakSize(int screenWidth, int screenHeight, TransparentActivityConfig config) {
        //fallback to top left corner
        if (config.x == null) {
            config.x = -(screenWidth / 2);
        } else {
            config.x = Double.valueOf(Math.ceil(config.x * Resources.getSystem().getDisplayMetrics().density)).intValue();
        }
        if (config.y == null) {
            config.y = -(screenHeight / 2);
        } else {
            config.y = Double.valueOf(Math.ceil(config.y * Resources.getSystem().getDisplayMetrics().density)).intValue();
        }

        int remainingWidth = screenWidth - config.x;
        int remainingHeight = screenHeight - config.y;

        //fallback to remaining screen
        if (config.width == null) {
            config.width = remainingWidth;
        } else {
            config.width = Double.valueOf(Math.ceil(config.width * Resources.getSystem().getDisplayMetrics().density)).intValue();
        }
        if (config.height == null) {
            config.height = remainingHeight;
        } else {
            config.height = Double.valueOf(Math.ceil(config.height * Resources.getSystem().getDisplayMetrics().density)).intValue();
        }
    }
}
