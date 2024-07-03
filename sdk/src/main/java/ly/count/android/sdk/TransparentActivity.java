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
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.RelativeLayout;
import androidx.annotation.NonNull;

class TransparentActivity extends Activity {

    static final String X_KEY = "x";
    static final String Y_KEY = "y";
    static final String WIDTH_KEY = "width";
    static final String HEIGHT_KEY = "height";
    static final String URI_KEY = "uri";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);

        // Get extras
        Intent intent = getIntent();
        String uri = intent.getStringExtra(URI_KEY);
        int x = intent.getIntExtra(X_KEY, 0);
        int y = intent.getIntExtra(Y_KEY, 0);
        int width = intent.getIntExtra(WIDTH_KEY, 0);
        int height = intent.getIntExtra(HEIGHT_KEY, 0);

        // Configure window layout parameters
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.gravity = Gravity.TOP;
        params.x = x;
        params.y = y;
        params.height = height;
        params.width = width;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        getWindow().setAttributes(params);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        // Create and configure the layout
        RelativeLayout relativeLayout = new RelativeLayout(this);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(width, height);
        relativeLayout.setLayoutParams(layoutParams);
        WebView webView = createWebView(uri, width, height);

        // Add views
        relativeLayout.addView(webView);
        setContentView(relativeLayout);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createWebView(String uri, int width, int height) {
        WebView webView = new WebView(this);
        RelativeLayout.LayoutParams webLayoutParams = new RelativeLayout.LayoutParams(width, height);
        webLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        webLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_END);
        webView.setLayoutParams(webLayoutParams);

        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl(uri);
        return webView;
    }

    /**
     * Show the widget
     *
     * @param context The context
     * @param config The configuration
     */
    protected static void showActivity(@NonNull Context context, @NonNull TransparentActivityConfig config) {
        assert context != null;
        int screenHeight = Resources.getSystem().getDisplayMetrics().heightPixels;
        int screenWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
        calculateSize(screenWidth, screenHeight, config);

        Intent intent = new Intent(context, TransparentActivity.class);
        intent.putExtra(X_KEY, config.x);
        intent.putExtra(Y_KEY, config.y);
        intent.putExtra(WIDTH_KEY, config.width);
        intent.putExtra(HEIGHT_KEY, config.height);
        intent.putExtra(URI_KEY, config.url);

        context.startActivity(intent);
    }

    private static void calculateSize(int screenWidth, int screenHeight, TransparentActivityConfig config) {
        if (config.xPercent != null) {
            config.x = (int) (screenWidth * adjustPercent(config.xPercent));
        }
        if (config.yPercent != null) {
            config.y = (int) (screenHeight * adjustPercent(config.yPercent));
        }

        int remainingWidth = screenWidth - (config.x != null ? config.x : 0);
        int remainingHeight = screenHeight - (config.y != null ? config.y : 0);

        if (config.widthPercent != null) {
            config.width = (int) (remainingWidth * adjustPercent(config.widthPercent));
            config.width = Math.min(config.width, remainingWidth);
        }

        if (config.heightPercent != null) {
            config.height = (int) (remainingHeight * adjustPercent(config.heightPercent));
            config.height = Math.min(config.height, remainingHeight);
        }

        //fallback to remaining screen
        if (config.width == null) {
            config.width = remainingWidth;
        }
        if (config.height == null) {
            config.height = remainingHeight;
        }

        //fallback to top left corner
        if (config.x == null) {
            config.x = 0;
        }
        if (config.y == null) {
            config.y = 0;
        }
    }

    private static Double adjustPercent(Double percent) {
        if (percent > 1 || percent < -1) {
            percent = percent % 1;
        }
        if (percent < 0) {
            percent = 1 + percent;
        }
        return percent;
    }
}
