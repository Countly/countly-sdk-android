package ly.count.android.sdk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.RelativeLayout;
import androidx.annotation.Nullable;
import java.util.HashMap;
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
    static final String WIDGET_INFO = "widget_info";
    static final String ID_CALLBACK = "id_callback";
    int currentOrientation = 0;
    long ID = -1;
    TransparentActivityConfig configLandscape = null;
    TransparentActivityConfig configPortrait = null;
    WebView webView;
    RelativeLayout relativeLayout;
    static Map<Long, ContentCallback> contentCallbacks = new ConcurrentHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(Countly.TAG, "[TransparentActivity] onCreate, content received, showing it");

        // there is a stripe at the top of the screen for contents
        // we eliminate it with hiding the system ui
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);

        // Get extras
        Intent intent = getIntent();
        ID = intent.getLongExtra(ID_CALLBACK, -1);
        currentOrientation = intent.getIntExtra(ORIENTATION, 0);
        configLandscape = (TransparentActivityConfig) intent.getSerializableExtra(CONFIGURATION_LANDSCAPE);
        configPortrait = (TransparentActivityConfig) intent.getSerializableExtra(CONFIGURATION_PORTRAIT);
        Log.v(Countly.TAG, "[TransparentActivity] onCreate, orientation: " + currentOrientation);
        Log.v(Countly.TAG, "[TransparentActivity] onCreate, configLandscape  x: ["
            + configLandscape.x
            + "] y: ["
            + configLandscape.y
            + "] width: ["
            + configLandscape.width
            + "] height: ["
            + configLandscape.height
            + "], topOffset: ["
            + configLandscape.topOffset
            + "], leftOffset: ["
            + configLandscape.leftOffset
            + "]");
        Log.v(Countly.TAG,
            "[TransparentActivity] onCreate, configPortrait  x: [" + configPortrait.x + "] y: [" + configPortrait.y + "] width: [" + configPortrait.width + "] height: [" + configPortrait.height + "], topOffset: [" + configPortrait.topOffset + "], leftOffset: [" + configPortrait.leftOffset + "]");

        TransparentActivityConfig config;
        if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            config = configLandscape;
        } else {
            config = configPortrait;
        }

        config = setupConfig(config);

        // Configure window layout parameters
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.gravity = Gravity.TOP | Gravity.START; // try out START

        int adjustedX = config.x;
        int adjustedY = config.y;

        if (config.useSafeArea) {
            if (config.leftOffset > 0) {
                adjustedX += config.leftOffset;
                Log.d(Countly.TAG, "[TransparentActivity] onCreate, using safe area mode, adjusting x from [" + config.x + "] to [" + adjustedX + "] (leftOffset: " + config.leftOffset + ")");
            }
            if (config.topOffset > 0) {
                adjustedY += config.topOffset;
                Log.d(Countly.TAG, "[TransparentActivity] onCreate, using safe area mode, adjusting y from [" + config.y + "] to [" + adjustedY + "] (topOffset: " + config.topOffset + ")");
            }
        }

        params.x = adjustedX;
        params.y = adjustedY;

        params.height = config.height;
        params.width = config.width;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        getWindow().setAttributes(params);

        WindowManager.LayoutParams verifyParams = getWindow().getAttributes();
        Log.d(Countly.TAG, "[TransparentActivity] onCreate, AFTER setAttributes - params.x: [" + verifyParams.x + "], params.y: [" + verifyParams.y + "], params.gravity: [" + verifyParams.gravity + "], width: [" + verifyParams.width + "], height: [" + verifyParams.height + "]");

        getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        // Create and configure the layout
        relativeLayout = new RelativeLayout(this);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(config.width, config.height);
        relativeLayout.setLayoutParams(layoutParams);
        webView = createWebView(config);

        // Add views
        relativeLayout.addView(webView);
        setContentView(relativeLayout);
    }

    private TransparentActivityConfig setupConfig(@Nullable TransparentActivityConfig config) {
        if (config == null) {
            Log.w(Countly.TAG, "[TransparentActivity] setupConfig, Config is null, using default values");
            final DisplayMetrics metrics = UtilsDevice.getDisplayMetrics(this);
            return new TransparentActivityConfig(0, 0, metrics.widthPixels, metrics.heightPixels);
        }

        if (!config.useSafeArea) {
            final DisplayMetrics metrics = UtilsDevice.getDisplayMetrics(this);

            if (config.width < 1) {
                config.width = metrics.widthPixels;
            }
            if (config.height < 1) {
                config.height = metrics.heightPixels;
            }
        }

        if (config.x < 1) {
            config.x = 0;
        }
        if (config.y < 1) {
            config.y = 0;
        }

        Log.d(Countly.TAG, "[TransparentActivity] setupConfig, final config - x: [" + config.x + "], y: [" + config.y + "], width: [" + config.width + "], height: [" + config.height + "], useSafeArea: [" + config.useSafeArea + "]");
        return config;
    }

    private void resizeContent(TransparentActivityConfig config) {
        Log.d(Countly.TAG,
            "[TransparentActivity] resizeContent(config), config dimensions (px): [" + config.width + "x" + config.height + "], x: [" + config.x + "], y: [" + config.y + "], useSafeArea: [" + config.useSafeArea + "], topOffset: [" + config.topOffset + "], leftOffset: [" + config.leftOffset + "]");

        int adjustedX = config.x;
        int adjustedY = config.y;

        if (config.useSafeArea) {
            if (config.leftOffset > 0) {
                adjustedX += config.leftOffset;
                Log.d(Countly.TAG, "[TransparentActivity] resizeContent(config), applying left offset, adjusted x: [" + adjustedX + "]");
            }
            if (config.topOffset > 0) {
                adjustedY += config.topOffset;
                Log.d(Countly.TAG, "[TransparentActivity] resizeContent(config), applying top offset, adjusted y: [" + adjustedY + "]");
            }
        }

        WindowManager.LayoutParams params = getWindow().getAttributes();
        Log.d(Countly.TAG, "[TransparentActivity] resizeContent(config), BEFORE - params.x: [" + params.x + "], params.y: [" + params.y + "], params.gravity: [" + params.gravity + "]");
        params.gravity = Gravity.TOP | Gravity.START; // safe?
        params.x = adjustedX;
        params.y = adjustedY;
        params.height = config.height;
        params.width = config.width;
        getWindow().setAttributes(params);

        WindowManager.LayoutParams verifyParams = getWindow().getAttributes();
        Log.d(Countly.TAG, "[TransparentActivity] resizeContent(config), AFTER - params.x: [" + verifyParams.x + "], params.y: [" + verifyParams.y + "], params.gravity: [" + verifyParams.gravity + "], width: [" + verifyParams.width + "], height: [" + verifyParams.height + "]");

        ViewGroup.LayoutParams layoutParams = relativeLayout.getLayoutParams();
        layoutParams.width = config.width;
        layoutParams.height = config.height;
        relativeLayout.setLayoutParams(layoutParams);

        ViewGroup.LayoutParams webLayoutParams = webView.getLayoutParams();
        webLayoutParams.width = config.width;
        webLayoutParams.height = config.height;
        webView.setLayoutParams(webLayoutParams);

        Log.d(Countly.TAG, "[TransparentActivity] resizeContent(config), layout params set - relativeLayout: [" + layoutParams.width + "x" + layoutParams.height + "], webView: [" + webLayoutParams.width + "x" + webLayoutParams.height + "]");
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(Countly.TAG, "[TransparentActivity] onConfigurationChanged orientation: [" + newConfig.orientation + "], currentOrientation: [" + currentOrientation + "]");

        if (currentOrientation != newConfig.orientation) {
            currentOrientation = newConfig.orientation;
        }

        resizeContent();
    }

    private void resizeContent() {
        TransparentActivityConfig currentConfig = (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) ? configLandscape : configPortrait;

        float density = getResources().getDisplayMetrics().density;
        int widthPx, heightPx;

        if (currentConfig != null && currentConfig.useSafeArea) {
            Log.d(Countly.TAG, "[TransparentActivity] resizeContent, recalculating safe area dimensions for orientation change");

            SafeAreaDimensions safeArea = SafeAreaCalculator.calculateSafeAreaDimensions(this, Countly.sharedInstance().L);

            configPortrait.topOffset = safeArea.portraitTopOffset;
            configPortrait.leftOffset = safeArea.portraitLeftOffset;
            configLandscape.topOffset = safeArea.landscapeTopOffset;
            configLandscape.leftOffset = safeArea.landscapeLeftOffset;

            Log.d(Countly.TAG, "[TransparentActivity] resizeContent, updated offsets - Portrait: topOffset=[" + configPortrait.topOffset + "], leftOffset=[" + configPortrait.leftOffset + "]");
            Log.d(Countly.TAG, "[TransparentActivity] resizeContent, updated offsets - Landscape: topOffset=[" + configLandscape.topOffset + "], leftOffset=[" + configLandscape.leftOffset + "]");

            int topOffset, leftOffset;
            if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                widthPx = safeArea.landscapeWidth;
                heightPx = safeArea.landscapeHeight;
                topOffset = safeArea.landscapeTopOffset;
                leftOffset = safeArea.landscapeLeftOffset;
            } else {
                widthPx = safeArea.portraitWidth;
                heightPx = safeArea.portraitHeight;
                topOffset = safeArea.portraitTopOffset;
                leftOffset = safeArea.portraitLeftOffset;
            }

            Log.d(Countly.TAG, "[TransparentActivity] resizeContent, safe area mode - sending dimensions to webview (px): ["
                + widthPx
                + "x"
                + heightPx
                + "], (dp): ["
                + Math.round(widthPx / density)
                + "x"
                + Math.round(heightPx / density)
                + "], density: ["
                + density
                + "], topOffset: ["
                + topOffset
                + "], leftOffset: ["
                + leftOffset
                + "]");
        } else {
            final DisplayMetrics metrics = UtilsDevice.getDisplayMetrics(this);
            widthPx = metrics.widthPixels;
            heightPx = metrics.heightPixels;

            Log.d(Countly.TAG, "[TransparentActivity] resizeContent, immersive mode - sending dimensions to webview (px): [" + widthPx + "x" + heightPx + "], (dp): [" + Math.round(widthPx / density) + "x" + Math.round(heightPx / density) + "], density: [" + density + "]");
        }

        int scaledWidth = Math.round(widthPx / density);
        int scaledHeight = Math.round(heightPx / density);
        webView.loadUrl("javascript:window.postMessage({type: 'resize', width: " + scaledWidth + ", height: " + scaledHeight + "}, '*');");
    }

    @Override
    public void onDestroy() {
        close(new HashMap<>());

        if (Countly.sharedInstance().isInitialized()) {
            Countly.sharedInstance().moduleContent.notifyAfterContentIsClosed();
        }
        super.onDestroy();
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN);

        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
    }

    private void resizeContentInternal() {
        switch (currentOrientation) {
            case Configuration.ORIENTATION_LANDSCAPE:
                if (configLandscape != null) {
                    configLandscape = setupConfig(configLandscape);
                    resizeContent(configLandscape);
                }
                break;
            case Configuration.ORIENTATION_PORTRAIT:
                if (configPortrait != null) {
                    configPortrait = setupConfig(configPortrait);
                    resizeContent(configPortrait);
                }
                break;
            default:
                break;
        }
    }

    private boolean widgetUrlAction(String url, WebView view) {
        Map<String, Object> query = splitQuery(url);
        Object widgetCommand = query.get("?cly_widget_command");

        if (widgetCommand == null || !widgetCommand.equals("1")) {
            Log.w(Countly.TAG, "[TransparentActivity] widgetUrlAction, event:[" + widgetCommand + "] this is not a countly widget command url");
            return false;
        }

        if (query.containsKey("close") && Objects.equals(query.get("close"), "1")) {
            if (Countly.sharedInstance().isInitialized()) {
                close(query);

                ModuleFeedback.CountlyFeedbackWidget widgetInfo = (ModuleFeedback.CountlyFeedbackWidget) getIntent().getSerializableExtra(WIDGET_INFO);
                Countly.sharedInstance().moduleFeedback.reportFeedbackWidgetCancelButton(widgetInfo);
            }
        }

        return true;
    }

    private boolean contentUrlAction(String url, WebView view) {
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
            close(query);

            if (Countly.sharedInstance().isInitialized()) {
                Countly.sharedInstance().moduleContent.notifyAfterContentIsClosed();
            }
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
            final DisplayMetrics metrics = UtilsDevice.getDisplayMetrics(this);
            float density = metrics.density;

            JSONObject resizeMeJson = (JSONObject) resizeMe;
            Log.v(Countly.TAG, "[TransparentActivity] resizeMeAction, resize_me JSON: [" + resizeMeJson + "]");
            JSONObject portrait = resizeMeJson.getJSONObject("p");
            JSONObject landscape = resizeMeJson.getJSONObject("l");

            boolean portraitUseSafeArea = configPortrait.useSafeArea;
            boolean landscapeUseSafeArea = configLandscape.useSafeArea;
            int portraitTopOffset = configPortrait.topOffset;
            int landscapeTopOffset = configLandscape.topOffset;
            int portraitLeftOffset = configPortrait.leftOffset;
            int landscapeLeftOffset = configLandscape.leftOffset;

            configPortrait.x = (int) Math.ceil(portrait.getInt("x") * density);
            configPortrait.y = (int) Math.ceil(portrait.getInt("y") * density);
            configPortrait.width = (int) Math.ceil(portrait.getInt("w") * density);
            configPortrait.height = (int) Math.ceil(portrait.getInt("h") * density);
            configPortrait.useSafeArea = portraitUseSafeArea;
            configPortrait.topOffset = portraitTopOffset;
            configPortrait.leftOffset = portraitLeftOffset;

            configLandscape.x = (int) Math.ceil(landscape.getInt("x") * density);
            configLandscape.y = (int) Math.ceil(landscape.getInt("y") * density);
            configLandscape.width = (int) Math.ceil(landscape.getInt("w") * density);
            configLandscape.height = (int) Math.ceil(landscape.getInt("h") * density);
            configLandscape.useSafeArea = landscapeUseSafeArea;
            configLandscape.topOffset = landscapeTopOffset;
            configLandscape.leftOffset = landscapeLeftOffset;

            Log.d(Countly.TAG, "[TransparentActivity] resizeMeAction, updated configs - Portrait: useSafeArea=[" + portraitUseSafeArea + "], topOffset=[" + portraitTopOffset + "], leftOffset=[" + portraitLeftOffset + "]");
            Log.d(Countly.TAG, "[TransparentActivity] resizeMeAction, updated configs - Landscape: useSafeArea=[" + landscapeUseSafeArea + "], topOffset=[" + landscapeTopOffset + "], leftOffset=[" + landscapeLeftOffset + "]");

            resizeContentInternal();
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
        String[] pairs = url.split(Utils.COMM_URL + "/?");
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

    private void close(Map<String, Object> contentData) {
        if (contentCallbacks.get(ID) != null) {
            contentCallbacks.get(ID).onContentCallback(ContentStatus.CLOSED, contentData);
            contentCallbacks.remove(ID);
        }
        super.finish();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createWebView(TransparentActivityConfig config) {
        WebView webView = new CountlyWebView(this);
        webView.setVisibility(View.INVISIBLE);
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
        client.registerWebViewUrlListener(new WebViewUrlListener() {
            @Override public boolean onUrl(String url, WebView webView) {
                if (url.startsWith(Utils.COMM_URL)) {
                    if (url.contains("cly_x_action_event")) {
                        return contentUrlAction(url, webView);
                    } else if (url.contains("cly_widget_command")) {
                        return widgetUrlAction(url, webView);
                    }
                }

                if (url.endsWith("cly_x_int=1")) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                    return true;
                }

                return false;
            }
        });
        client.afterPageFinished = new WebViewPageLoadedListener() {
            @Override public void onPageLoaded(boolean failed) {
                if (failed) {
                    close(new HashMap<>());

                    if (Countly.sharedInstance().isInitialized()) {
                        Countly.sharedInstance().moduleContent.notifyAfterContentIsClosed();
                    }
                } else {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);

                    TransparentActivityConfig currentConfig = currentOrientation == Configuration.ORIENTATION_LANDSCAPE ? configLandscape : configPortrait;
                    if (currentConfig != null && !currentConfig.useSafeArea) {
                        hideSystemUI();
                    }
                    webView.setVisibility(View.VISIBLE);
                }
            }
        };
        webView.setWebViewClient(client);
        webView.loadUrl(config.url);
        return webView;
    }
}