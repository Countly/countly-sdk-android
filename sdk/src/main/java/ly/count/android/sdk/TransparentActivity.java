package ly.count.android.sdk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.RelativeLayout;
import androidx.annotation.NonNull;

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

        config.listeners.add((url, webView) -> {
            if (url.endsWith("cly_x_close=1")) {
                finish();
                return true;
            } else {
                return false;
            }
        });

        // Configure window layout parameters
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.gravity = Gravity.TOP | Gravity.LEFT;
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
        WebView webView = createWebView(config);

        // Add views
        relativeLayout.addView(webView);
        setContentView(relativeLayout);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createWebView(TransparentActivityConfig config) {
        WebView webView = new CountlyWebView(this);
        RelativeLayout.LayoutParams webLayoutParams = new RelativeLayout.LayoutParams(config.width, config.height);
        webLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        webLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_END);
        webView.setLayoutParams(webLayoutParams);

        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        webView.clearCache(true);
        webView.clearHistory();

        CountlyWebViewClient client = new CountlyWebViewClient();
        client.registerWebViewUrlListeners(config.listeners);

        webView.setWebViewClient(client);
        webView.loadUrl(config.url);
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
        context.startActivity(intent);
    }

    private static void tweakSize(int screenWidth, int screenHeight, TransparentActivityConfig config) {
        //int topLeftX = 0;//-(screenWidth / 2);
        //int topLeftY = 0;//-(screenHeight / 2);
        //fallback to top left corner
        if (config.x == null) {
            config.x = 0;
        } else {
            config.x = (int) Math.ceil(config.x * Resources.getSystem().getDisplayMetrics().density);
        }
        if (config.y == null) {
            config.y = 0;
        } else {
            config.y = (int) Math.ceil(config.y * Resources.getSystem().getDisplayMetrics().density);
        }

        int remainingWidth = screenWidth - config.x;
        int remainingHeight = screenHeight - config.y;

        //fallback to remaining screen
        if (config.width == null) {
            config.width = remainingWidth;
        } else {
            config.width = (int) Math.ceil(config.width * Resources.getSystem().getDisplayMetrics().density);
        }
        if (config.height == null) {
            config.height = remainingHeight;
        } else {
            config.height = (int) Math.ceil(config.height * Resources.getSystem().getDisplayMetrics().density);
        }
    }
}
