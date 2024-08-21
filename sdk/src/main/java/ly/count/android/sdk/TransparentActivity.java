package ly.count.android.sdk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONArray;
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
        Log.e("PIXEL", "placementCoordLAND x: " + configLandscape.x + " y: " + configLandscape.y + " width: " + configLandscape.width + " height: " + configLandscape.height);
        Log.e("PIXEL", "placementCoordPORT x: " + configPortrait.x + " y: " + configPortrait.y + " width: " + configPortrait.width + " height: " + configPortrait.height);

        TransparentActivityConfig config;
        if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            config = configLandscape;
        } else {
            config = configPortrait;
        }

        config = setupConfig(config);

        int width = config.width;
        int height = config.height;

        // todo refactor those
        configLandscape.listeners.add((url, webView) -> {
            if (url.startsWith("https://countly_action_event")) {
                return contentUrlAction(url, configLandscape, webView);
            }
            return false;
        });

        configPortrait.listeners.add((url, webView) -> {
            if (url.startsWith("https://countly_action_event")) {
                return contentUrlAction(url, configPortrait, webView);
            }
            return false;
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

    private boolean contentUrlAction(String url, TransparentActivityConfig config, WebView view) {
        Log.d(Countly.TAG, "[TransparentActivity] contentUrlAction, url: " + url);
        Map<String, Object> query = splitQuery(url);

        Object clyEvent = query.get("cly_x_action_event");

        if (clyEvent == null || !clyEvent.equals("1")) {
            return false;
        }
        Object clyAction = query.get("action");
        if (!(clyAction instanceof String)) {
            return false;
        }
        String action = (String) clyAction;

        boolean result = false;

        switch (action) {
            case "event":
                if (query.containsKey("event")) {
                    JSONArray event = (JSONArray) query.get("event");
                    assert event != null;
                    for (int i = 0; i < Objects.requireNonNull(event).length(); i++) {
                        try {
                            JSONObject eventJson = event.getJSONObject(i);

                            if (!eventJson.has("sg")) {
                                Log.w(Countly.TAG, "[TransparentActivity] contentUrlAction, event JSON is missing segmentation data event:[" + eventJson + "]");
                                continue;
                            }

                            Map<String, Object> segmentation = new ConcurrentHashMap<>();
                            JSONObject segmentationJson = eventJson.getJSONObject("sg");
                            assert segmentationJson != null;
                            //TODO check for null, and refactor here
                            for (int j = 0; j < segmentationJson.names().length(); j++) {
                                String key = segmentationJson.names().getString(j);
                                Object value = segmentationJson.get(key);
                                segmentation.put(key, value);
                            }

                            Countly.sharedInstance().events().recordEvent(eventJson.get("key").toString(), segmentation);
                        } catch (JSONException e) {
                            Log.e(Countly.TAG, "[TransparentActivity] contentUrlAction, Failed to parse event JSON", e);
                        }
                    }
                }
                break;
            case "link":
                if (query.containsKey("link")) {
                    Object link = query.get("link");
                    assert link != null;

                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link.toString()));
                    view.getContext().startActivity(intent);
                    result = true;
                }
                break;
            case "resize_me":
                if (query.containsKey("resize_me")) {
                    Object resizeMe = query.get("resize_me");
                    assert resizeMe != null;
                    try {
                        JSONObject resizeMeJson = (JSONObject) resizeMe;
                        JSONObject portrait = resizeMeJson.getJSONObject("p");
                        JSONObject landscape = resizeMeJson.getJSONObject("l");
                        configPortrait.x = portrait.getInt("x");
                        configPortrait.y = portrait.getInt("y");
                        configPortrait.width = portrait.getInt("w");
                        configPortrait.height = portrait.getInt("h");

                        configLandscape.x = landscape.getInt("x");
                        configLandscape.y = landscape.getInt("y");
                        configLandscape.width = landscape.getInt("w");
                        configLandscape.height = landscape.getInt("h");

                        changeOrientationInternal();
                    } catch (JSONException e) {
                        Log.e(Countly.TAG, "[TransparentActivity] contentUrlAction, Failed to parse resize JSON", e);
                    }
                }
                break;
            default:
                break;
        }

        if (query.containsKey("close") && Objects.equals(query.get("close"), "1")) {
            finish();
            config.globalContentCallback.onContentCallback(ContentStatus.CLOSED, query);
            return true;
        }

        return result;
    }

    private Map<String, Object> splitQuery(String url) {
        Map<String, Object> query_pairs = new ConcurrentHashMap<>();
        String[] pairs = url.split("https://countly_action_event?");
        if (pairs.length != 2) {
            return query_pairs;
        }

        String[] pairs2 = pairs[1].split("&");
        for (String pair : pairs2) {
            int idx = pair.indexOf('=');
            String key = pair.substring(0, idx);
            String value = pair.substring(idx + 1);

            try {
                if (key.equals("event")) {
                    query_pairs.put(key, new JSONArray(value));
                } else if (key.equals("resize_me")) {
                    query_pairs.put(key, new JSONObject(value));
                }
            } catch (JSONException e) {
                Log.e(Countly.TAG, "[TransparentActivity] splitQuery, Failed to parse event JSON", e);
            }
            query_pairs.put(pair.substring(0, idx), pair.substring(idx + 1));
        }

        return query_pairs;
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
}
