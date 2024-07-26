package ly.count.android.sdk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.RelativeLayout;
import androidx.annotation.Nullable;

public class TransparentActivity extends Activity {
    static final String CONFIGURATION_LANDSCAPE = "Landscape";
    static final String CONFIGURATION_PORTRAIT = "Portrait";
    static final String ORIENTATION = "orientation";
    int currentOrientation = 0;
    TransparentActivityConfig configLandscape = null;
    TransparentActivityConfig configPortrait = null;
    WebView webView;
    RelativeLayout relativeLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(Countly.TAG, "[TransparentActivity] onCreate, content received, showing it");
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);

        // Get extras
        Intent intent = getIntent();
        currentOrientation = (int) intent.getSerializableExtra(ORIENTATION);
        configLandscape = (TransparentActivityConfig) intent.getSerializableExtra(CONFIGURATION_LANDSCAPE);
        configPortrait = (TransparentActivityConfig) intent.getSerializableExtra(CONFIGURATION_PORTRAIT);

        TransparentActivityConfig config;
        if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            config = configLandscape;
        } else {
            config = configPortrait;
        }

        config = setupConfig(config);

        int width = config.width;
        int height = config.height;

        config.listeners.add((url, webView) -> {
            if (url.endsWith("&cly_x_close=1")) {
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
        relativeLayout = new RelativeLayout(this);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(width, height);
        relativeLayout.setLayoutParams(layoutParams);
        webView = createWebView(config);

        // Add views
        relativeLayout.addView(webView);
        setContentView(relativeLayout);
    }

    private TransparentActivityConfig setupConfig(@Nullable TransparentActivityConfig config) {
        final WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        final Display display = wm.getDefaultDisplay();
        final DisplayMetrics metrics = new DisplayMetrics(); // this gets all
        display.getMetrics(metrics);

        if (config == null) {
            Log.e("PIXEL", "Config is null");
            return new TransparentActivityConfig(0, 0, metrics.widthPixels, metrics.heightPixels);
        }

        if (config.width < 1) {
            config.width = metrics.widthPixels;
        }
        if (config.height < 1) {
            config.height = metrics.heightPixels;
        }
        if (config.x < 1) {
            config.x = 0;
        }
        if (config.y < 1) {
            config.y = 0;
        }
        Rect rectangle = new Rect();
        Window window = getWindow();
        window.getDecorView().getWindowVisibleDisplayFrame(rectangle);
        int statusBarHeight = rectangle.top;
        int contentViewTop = window.findViewById(Window.ID_ANDROID_CONTENT).getTop();
        int titleBarHeight = contentViewTop - statusBarHeight;

        //config.y = metrics.heightPixels;
        //config.height = config.height * 2;

        Log.e("PIXEL", "screen width: " + metrics.widthPixels + " height: " + metrics.heightPixels);
        Log.e("PIXEL", "density: " + metrics.density);
        Log.e("PIXEL ", "x: " + config.x + " y: " + config.y + " width: " + config.width + " height: " + config.height);

        return config;
    }

    private void changeOrientation(TransparentActivityConfig config) {
        // Configure window layout parameters
        Log.e("PIXEL", "x: " + config.x + " y: " + config.y + " width: " + config.width + " height: " + config.height);
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.x = config.x;
        params.y = config.y;
        params.height = config.height;
        params.width = config.width;
        getWindow().setAttributes(params);

        ViewGroup.LayoutParams layoutParams = relativeLayout.getLayoutParams();
        layoutParams.width = config.width;
        layoutParams.height = config.height;
        relativeLayout.setLayoutParams(layoutParams);

        ViewGroup.LayoutParams webLayoutParams = webView.getLayoutParams();
        webLayoutParams.width = config.width;
        webLayoutParams.height = config.height;
        webView.setLayoutParams(webLayoutParams);
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.e("PIXEL", "onConfigurationChanged");
        if (currentOrientation != newConfig.orientation) {
            currentOrientation = newConfig.orientation;
            Log.e("PIXEL", "Orientation changed");
            Log.e("PIXEL", "Current orientation: " + currentOrientation + " Landscape: " + Configuration.ORIENTATION_LANDSCAPE + " Portrait: " + Configuration.ORIENTATION_PORTRAIT);
            switch (currentOrientation) {
                case Configuration.ORIENTATION_LANDSCAPE:
                    if (configLandscape != null) {
                        configLandscape = setupConfig(configLandscape);
                        changeOrientation(configLandscape);
                    }
                    break;
                case Configuration.ORIENTATION_PORTRAIT:
                    if (configPortrait != null) {
                        configPortrait = setupConfig(configPortrait);
                        changeOrientation(configPortrait);
                    }
                    break;
                default:
                    break;
            }
        }
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

        // TODO this might going to be used in the future
        /*webView.addJavascriptInterface(new JavaScriptInterface(this, (url, view) -> {
            if (url.endsWith("cly_x_close=1")) {
                finish();
                return true;
            } else {
                return false;
            }
        }), "Android");

        String script = "window.addEventListener('message', function(event) {" +
            "    Android.receiveMessage(JSON.stringify(event.data));" +
            "}, false);";
        webView.evaluateJavascript(script, null);*/

        CountlyWebViewClient client = new CountlyWebViewClient();
        client.registerWebViewUrlListeners(config.listeners);

        webView.setWebViewClient(client);
        webView.loadUrl(config.url);
        return webView;
    }

    // TODO this might going to be used in the future
    /*
    static class JavaScriptInterface {

        WebViewUrlListener listener;
        Context context;

        JavaScriptInterface(Context context, WebViewUrlListener listener) {
            this.listener = listener;
            this.context = context;
        }

        @JavascriptInterface
        public void receiveMessage(String message) {
            // Handle the message received from postMessage
            try {
                JSONObject json = new JSONObject(message);
                boolean isClose = json.optBoolean("close", false);
                if (isClose) {
                    // Close the activity
                    listener.onUrl("cly_x_close=1", null);
                    Log.d("PIXEL", "Close the activity");
                }
            } catch (JSONException e) {
                Log.e("PIXEL", "Error parsing JSON: " + e.getMessage());
            }
        }
    }*/
}
