package ly.count.android.sdk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.RelativeLayout;
import androidx.annotation.Nullable;
import org.json.JSONException;
import org.json.JSONObject;

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
        Log.v(Countly.TAG, "[TransparentActivity] onCreate, orientation: " + currentOrientation);
        Log.v(Countly.TAG, "[TransparentActivity] onCreate, configLandscape  x: [" + configLandscape.x + "] y: [" + configLandscape.y + "] width: [" + configLandscape.width + "] height: [" + configLandscape.height + "]");
        Log.v(Countly.TAG, "[TransparentActivity] onCreate, configPortrait  x: [" + configPortrait.x + "] y: [" + configPortrait.y + "] width: [" + configPortrait.width + "] height: [" + configPortrait.height + "]");

        TransparentActivityConfig config;
        if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            config = configLandscape;
        } else {
            config = configPortrait;
        }

        config = setupConfig(config);

        int width = config.width;
        int height = config.height;

        // Configure window layout parameters
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.gravity = Gravity.TOP | Gravity.LEFT; // try out START
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
            Log.w(Countly.TAG, "[TransparentActivity] setupConfig, Config is null, using default values with full screen size");
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
        return config;
    }

    private void changeOrientation(TransparentActivityConfig config) {
        Log.d(Countly.TAG, "[TransparentActivity] changeOrientation, config x: [" + config.x + "] y: [" + config.y + "] width: [" + config.width + "] height: [" + config.height + "]");
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
        Log.d(Countly.TAG, "[TransparentActivity] onConfigurationChanged orientation: [" + newConfig.orientation + "], currentOrientation: [" + currentOrientation + "]");
        Log.v(Countly.TAG, "[TransparentActivity] onConfigurationChanged, Landscape: [" + Configuration.ORIENTATION_LANDSCAPE + "] Portrait: [" + Configuration.ORIENTATION_PORTRAIT + "]");
        if (currentOrientation != newConfig.orientation) {
            currentOrientation = newConfig.orientation;
            Log.i(Countly.TAG, "[TransparentActivity] onConfigurationChanged, orientation changed to currentOrientation: [" + currentOrientation + "]");
            changeOrientationInternal();
        }
    }

    private void changeOrientationInternal() {
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

    private void extractCoordinateParams(float density, JSONObject coordinates, TransparentActivityConfig config) {
        config.x = (int) Math.ceil(coordinates.optInt("x") * density);
        config.y = (int) Math.ceil(coordinates.optInt("y") * density);
        config.width = (int) Math.ceil(coordinates.optInt("w") * density);
        config.height = (int) Math.ceil(coordinates.optInt("h") * density);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createWebView(TransparentActivityConfig config) {
        WebView webView = new CountlyWebView(this, new ActivityCallback() {
            @Override public void closeActivity() {
                Log.d(Countly.TAG, "[TransparentActivity] closeActivity");
                finish();
            }

            @Override public void resizeActivity(JSONObject coordinates) {
                Log.d(Countly.TAG, "[TransparentActivity] resizeActivity");
                final WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
                final Display display = wm.getDefaultDisplay();
                final DisplayMetrics metrics = new DisplayMetrics();
                display.getMetrics(metrics);

                float density = metrics.density;

                try {
                    JSONObject portrait = coordinates.getJSONObject("p");
                    JSONObject landscape = coordinates.getJSONObject("l");

                    extractCoordinateParams(density, portrait, configPortrait);
                    extractCoordinateParams(density, landscape, configLandscape);
                } catch (JSONException e) {
                    Log.e(Countly.TAG, "[TransparentActivity] resizeActivity, error: " + e);
                }

                changeOrientationInternal();
            }
        });
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
        client.listeners.addAll(config.listeners);

        webView.setWebViewClient(client);
        webView.loadUrl(config.url);
        return webView;
    }
}
