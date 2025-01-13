package ly.count.android.sdk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
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
    private static final String URL_START = "https://countly_action_event";
    int currentOrientation = 0;
    TransparentActivityConfig configLandscape = null;
    TransparentActivityConfig configPortrait = null;
    WebView webView;
    RelativeLayout relativeLayout;
    static ContentCallback globalContentCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(Countly.TAG, "[TransparentActivity] onCreate, content received, showing it");

        // there is a stripe at the top of the screen for contents
        // we eliminate it with no action bar full screen and this adds more smoothness
        // the stripe is because of our transparency
        setTheme(android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
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
        int navBarHeight = 0;
        if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            config = configLandscape;
        } else {
            // This is only needed for the portrait mode and
            // after android 35 the function that gives height gives the full height of the screen
            // so we need to subtract the height of the navigation bar
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                navBarHeight = getNavigationBarHeight();
            }

            config = configPortrait;
        }

        config = setupConfig(config);

        int width = config.width;
        int height = config.height - navBarHeight;

        configLandscape.listeners.add((url, webView) -> {
            if (url.startsWith(URL_START)) {
                return contentUrlAction(url, configLandscape, webView);
            }
            return false;
        });

        configPortrait.listeners.add((url, webView) -> {
            if (url.startsWith(URL_START)) {
                return contentUrlAction(url, configPortrait, webView);
            }
            return false;
        });

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

    private void changeOrientation(TransparentActivityConfig config, int navBarHeight) {
        Log.d(Countly.TAG, "[TransparentActivity] changeOrientation, config x: [" + config.x + "] y: [" + config.y + "] width: [" + config.width + "] height: [" + config.height + "]");
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.x = config.x;
        params.y = config.y;
        params.height = config.height - navBarHeight;
        params.width = config.width;
        getWindow().setAttributes(params);

        ViewGroup.LayoutParams layoutParams = relativeLayout.getLayoutParams();
        layoutParams.width = config.width;
        layoutParams.height = config.height - navBarHeight;
        relativeLayout.setLayoutParams(layoutParams);

        ViewGroup.LayoutParams webLayoutParams = webView.getLayoutParams();
        webLayoutParams.width = config.width;
        webLayoutParams.height = config.height - navBarHeight;
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
                    changeOrientation(configLandscape, 0);
                }
                break;
            case Configuration.ORIENTATION_PORTRAIT:
                if (configPortrait != null) {
                    configPortrait = setupConfig(configPortrait);
                    // This is only needed for the portrait mode and
                    // after android 35 the function that gives height gives the full height of the screen
                    // so we need to subtract the height of the navigation bar
                    // this is implemented twice because in the future resize_me action will be able to change the height of the content
                    int navBarHeight = 0;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                        navBarHeight = getNavigationBarHeight();
                    }
                    changeOrientation(configPortrait, navBarHeight);
                }
                break;
            default:
                break;
        }
    }

    private boolean contentUrlAction(String url, TransparentActivityConfig config, WebView view) {
        Log.d(Countly.TAG, "[TransparentActivity] contentUrlAction, url: [" + url + "]");
        Map<String, Object> query = splitQuery(url);
        Log.v(Countly.TAG, "[TransparentActivity] contentUrlAction, query: [" + query + "]");

        Object clyEvent = query.get("?cly_x_action_event");

        if (clyEvent == null || !clyEvent.equals("1")) {
            Log.w(Countly.TAG, "[TransparentActivity] contentUrlAction, event:[" + clyEvent + "] this is not a countly action event url");
            return false;
        }

        Object clyAction = query.get("action");
        if (clyAction instanceof String) {
            Log.d(Countly.TAG, "[TransparentActivity] contentUrlAction, action string:[" + clyAction + "]");
            String action = (String) clyAction;

            switch (action) {
                case "event":
                    eventAction(query);
                    break;
                case "link":
                    linkAction(query, view);
                    break;
                case "resize_me":
                    resizeMeAction(query);
                    break;
                default:
                    Log.e(Countly.TAG, "[TransparentActivity] contentUrlAction, unknown action:[" + action + "]");
                    break;
            }
        }

        if (query.containsKey("close") && Objects.equals(query.get("close"), "1")) {
            if (globalContentCallback != null) { // TODO: verify this later
                globalContentCallback.onContentCallback(ContentStatus.CLOSED, query);
            }
            ModuleContent.waitForDelay = 2; // this is indicating that we will wait 1 min after closing the content and before fetching the next one
            finish();
        }

        return true;
    }

    private boolean linkAction(Map<String, Object> query, WebView view) {
        Log.i(Countly.TAG, "[TransparentActivity] linkAction, link action detected");
        if (!query.containsKey("link")) {
            Log.w(Countly.TAG, "[TransparentActivity] linkAction, link action is missing link");
            return false;
        }
        Object link = query.get("link");
        if (!(link instanceof String)) {
            Log.w(Countly.TAG, "[TransparentActivity] linkAction, link action is not a string");
            return false;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link.toString()));
        view.getContext().startActivity(intent);
        return true;
    }

    private void resizeMeAction(Map<String, Object> query) {
        Log.i(Countly.TAG, "[TransparentActivity] resizeMeAction, resize_me action detected");
        if (!query.containsKey("resize_me")) {
            Log.w(Countly.TAG, "[TransparentActivity] resizeMeAction, resize_me action is missing resize_me");
            return;
        }
        Object resizeMe = query.get("resize_me");
        if (!(resizeMe instanceof JSONObject)) {
            Log.w(Countly.TAG, "[TransparentActivity] resizeMeAction, resize_me action is not a JSON object");
            return;
        }
        try {
            final WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            final Display display = wm.getDefaultDisplay();
            final DisplayMetrics metrics = new DisplayMetrics();
            display.getMetrics(metrics);

            float density = metrics.density;

            JSONObject resizeMeJson = (JSONObject) resizeMe;
            Log.v(Countly.TAG, "[TransparentActivity] resizeMeAction, resize_me JSON: [" + resizeMeJson + "]");
            JSONObject portrait = resizeMeJson.getJSONObject("p");
            JSONObject landscape = resizeMeJson.getJSONObject("l");
            configPortrait.x = (int) Math.ceil(portrait.getInt("x") * density);
            configPortrait.y = (int) Math.ceil(portrait.getInt("y") * density);
            configPortrait.width = (int) Math.ceil(portrait.getInt("w") * density);
            configPortrait.height = (int) Math.ceil(portrait.getInt("h") * density);

            configLandscape.x = (int) Math.ceil(landscape.getInt("x") * density);
            configLandscape.y = (int) Math.ceil(landscape.getInt("y") * density);
            configLandscape.width = (int) Math.ceil(landscape.getInt("w") * density);
            configLandscape.height = (int) Math.ceil(landscape.getInt("h") * density);

            changeOrientationInternal();
        } catch (JSONException e) {
            Log.e(Countly.TAG, "[TransparentActivity] resizeMeAction, Failed to parse resize JSON", e);
        }
    }

    private void eventAction(Map<String, Object> query) {
        Log.i(Countly.TAG, "[TransparentActivity] eventAction, event action detected");
        if (query.containsKey("event")) {
            JSONArray event = (JSONArray) query.get("event");
            assert event != null; // this is already checked above
            for (int i = 0; i < Objects.requireNonNull(event).length(); i++) {
                try {
                    JSONObject eventJson = event.getJSONObject(i);
                    Log.v(Countly.TAG, "[TransparentActivity] eventAction, event JSON: [" + eventJson.toString() + "]");

                    Map<String, Object> segmentation = new ConcurrentHashMap<>();
                    JSONObject sgJson = eventJson.optJSONObject("sg");
                    JSONObject segmentationJson = eventJson.optJSONObject("segmentation");

                    if (sgJson != null) {
                        segmentationJson = sgJson;
                    }

                    if (segmentationJson == null) {
                        Log.w(Countly.TAG, "[TransparentActivity] eventAction, event JSON is missing segmentation data event: [" + eventJson + "]");
                        continue;
                    }

                    for (int j = 0; j < segmentationJson.names().length(); j++) {
                        String key = segmentationJson.names().getString(j);
                        Object value = segmentationJson.get(key);
                        segmentation.put(key, value);
                    }

                    Countly.sharedInstance().events().recordEvent(eventJson.get("key").toString(), segmentation);
                } catch (JSONException e) {
                    Log.e(Countly.TAG, "[TransparentActivity] eventAction, Failed to parse event JSON", e);
                }
            }

            Countly.sharedInstance().requestQueue().attemptToSendStoredRequests();
        } else {
            Log.w(Countly.TAG, "[TransparentActivity] eventAction, event action is missing event");
        }
    }

    private Map<String, Object> splitQuery(String url) {
        Map<String, Object> query_pairs = new ConcurrentHashMap<>();
        String[] pairs = url.split("https://countly_action_event/?");
        if (pairs.length != 2) {
            return query_pairs;
        }

        String[] pairs2 = pairs[1].split("&");
        for (String pair : pairs2) {
            int idx = pair.indexOf('=');
            String key = pair.substring(0, idx);
            String value = pair.substring(idx + 1);

            try {
                if ("event".equals(key)) {
                    query_pairs.put(key, new JSONArray(value));
                } else if ("resize_me".equals(key)) {
                    query_pairs.put(key, new JSONObject(value));
                } else {
                    query_pairs.put(key, value);
                }
            } catch (JSONException e) {
                Log.e(Countly.TAG, "[TransparentActivity] splitQuery, Failed to parse event JSON", e);
            }
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

    private int getNavigationBarHeight() {
        int resourceId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return getResources().getDimensionPixelSize(resourceId);
        }
        return 0;
    }
}
