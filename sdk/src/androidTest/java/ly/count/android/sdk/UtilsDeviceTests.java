package ly.count.android.sdk;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.StrictMode;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class UtilsDeviceTests {

    private final List<String> incorrectContextUses = new CopyOnWriteArrayList<>();
    private StrictMode.VmPolicy previousVmPolicy;

    /**
     * getThemeMode, appendThemeParam and getDisplayMetrics prefer the foreground Activity, and the
     * request queue is shared by every test in the process, so each test starts with no held
     * Activity and an empty queue.
     */
    @Before
    public void setUp() {
        TestUtils.getCountlyStore().clear();
        clearForegroundActivity();
    }

    @After
    public void tearDown() {
        clearForegroundActivity();
        if (previousVmPolicy != null) {
            StrictMode.setVmPolicy(previousVmPolicy);
            previousVmPolicy = null;
        }
    }

    private void clearForegroundActivity() {
        Activity current = CountlyActivityHolder.getInstance().getActivity();
        if (current != null) {
            CountlyActivityHolder.getInstance().clearActivity(current);
        }
    }

    /**
     * Builds a context whose resources report exactly the given UI_MODE_NIGHT_* flag. A mock is
     * used deliberately: a real createConfigurationContext falls back to the device's night mode
     * for the UNDEFINED case, so it can not report "undefined" independently of the test device.
     */
    private Context contextWithNightMode(int nightModeFlag) {
        Context ctx = mock(Context.class);
        Resources res = mock(Resources.class);
        Configuration cfg = new Configuration();
        cfg.uiMode = nightModeFlag;
        when(ctx.getResources()).thenReturn(res);
        when(res.getConfiguration()).thenReturn(cfg);
        return ctx;
    }

    // ======== getThemeMode ========

    /** A dark-configured context resolves to "d", a light one to "l", undefined to null. */
    @Test
    public void getThemeMode_mapsNightModeFlags() {
        Assert.assertEquals("d", UtilsDevice.getThemeMode(contextWithNightMode(Configuration.UI_MODE_NIGHT_YES)));
        Assert.assertEquals("l", UtilsDevice.getThemeMode(contextWithNightMode(Configuration.UI_MODE_NIGHT_NO)));
        Assert.assertNull(UtilsDevice.getThemeMode(contextWithNightMode(Configuration.UI_MODE_NIGHT_UNDEFINED)));
    }

    /** The foreground Activity's configuration wins over the fallback context. */
    @Test
    public void getThemeMode_prefersForegroundActivity() {
        Activity darkActivity = mock(Activity.class);
        Resources darkResources = mock(Resources.class);
        Configuration darkCfg = new Configuration();
        darkCfg.uiMode = Configuration.UI_MODE_NIGHT_YES;
        when(darkActivity.getResources()).thenReturn(darkResources);
        when(darkResources.getConfiguration()).thenReturn(darkCfg);

        CountlyActivityHolder.getInstance().setActivity(darkActivity);
        try {
            // fallback is light, but the dark Activity must take precedence
            Assert.assertEquals("d", UtilsDevice.getThemeMode(contextWithNightMode(Configuration.UI_MODE_NIGHT_NO)));
        } finally {
            CountlyActivityHolder.getInstance().clearActivity(darkActivity);
        }
    }

    // ======== appendThemeParam ========

    /** With an existing query string the theme is appended with "&". */
    @Test
    public void appendThemeParam_appendsWithAmpersandWhenQueryPresent() {
        String url = "https://widgets.example/feedback/nps?widget_id=abc&app_key=k";
        Assert.assertEquals(url + "&th=d", UtilsDevice.appendThemeParam(url, contextWithNightMode(Configuration.UI_MODE_NIGHT_YES)));
        Assert.assertEquals(url + "&th=l", UtilsDevice.appendThemeParam(url, contextWithNightMode(Configuration.UI_MODE_NIGHT_NO)));
    }

    /** Without a query string the theme is appended with "?". */
    @Test
    public void appendThemeParam_appendsWithQuestionMarkWhenNoQuery() {
        String url = "https://content.example/page";
        Assert.assertEquals(url + "?th=l", UtilsDevice.appendThemeParam(url, contextWithNightMode(Configuration.UI_MODE_NIGHT_NO)));
    }

    /** When the theme is undefined the URL is returned untouched. */
    @Test
    public void appendThemeParam_returnsUrlUnchangedWhenThemeUndefined() {
        String url = "https://content.example/page?a=1";
        Assert.assertEquals(url, UtilsDevice.appendThemeParam(url, contextWithNightMode(Configuration.UI_MODE_NIGHT_UNDEFINED)));
    }

    // ======== getDisplayMetrics ========

    /**
     * With no Activity at all, the remote config download at init, a manual session, a standalone
     * metrics request, and a handled crash all report the default display's resolution, and none
     * of them reads WindowManager from the Application context.
     */
    @Test
    public void getDisplayMetrics_withoutActivity_reportsDisplayWithoutIncorrectContextUse() throws JSONException {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R);
        startRecordingIncorrectContextUse();

        List<String> immediateRequests = new CopyOnWriteArrayList<>();
        CountlyConfig config = TestUtils.createBaseConfig().enableManualSessionControl().enableRemoteConfigAutomaticTriggers();
        config.immediateRequestGenerator = recordingRequestGenerator(immediateRequests);

        Countly countly = new Countly().init(config);
        countly.sessions().beginSession();
        countly.requestQueue().recordMetrics(null);
        countly.crashes().recordHandledException(new Exception("recorded without an Activity"));

        Assert.assertTrue("unexpected incorrect context use: " + incorrectContextUses, incorrectContextUses.isEmpty());

        String expected = defaultDisplayResolution();
        String remoteConfigRequest = null;
        for (String request : immediateRequests) {
            if (request.contains("method=rc")) {
                remoteConfigRequest = request;
            }
        }
        Assert.assertNotNull(remoteConfigRequest);
        Assert.assertEquals(expected, resolutionIn(queryParam(remoteConfigRequest, "metrics")));

        List<String> storedResolutions = new ArrayList<>();
        for (Map<String, String> request : TestUtils.getCurrentRQ()) {
            if (request.containsKey("begin_session")) {
                storedResolutions.add("session " + resolutionIn(request.get("metrics")));
            } else if (request.containsKey("metrics")) {
                storedResolutions.add("metrics " + resolutionIn(request.get("metrics")));
            } else if (request.containsKey("crash")) {
                storedResolutions.add("crash " + resolutionIn(request.get("crash")));
            }
        }
        Assert.assertEquals(Arrays.asList("session " + expected, "metrics " + expected, "crash " + expected), storedResolutions);
    }

    /**
     * A held Activity is the metrics source on every API level, so a session begun with the
     * Application context reports the Activity's window and never reads WindowManager from the
     * Application context.
     */
    @Test
    @SuppressWarnings("deprecation")
    public void getDisplayMetrics_withHeldActivity_readsActivityWindowManager() throws JSONException {
        WindowManager activityWindowManager = mock(WindowManager.class);
        String expected;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startRecordingIncorrectContextUse();
            when(activityWindowManager.getCurrentWindowMetrics()).thenReturn(new WindowMetrics(new Rect(0, 0, 1234, 567), new WindowInsets.Builder().build()));
            expected = "1234x567";
        } else {
            Display display = ((DisplayManager) TestUtils.getContext().getSystemService(Context.DISPLAY_SERVICE)).getDisplay(Display.DEFAULT_DISPLAY);
            when(activityWindowManager.getDefaultDisplay()).thenReturn(display);
            DisplayMetrics realMetrics = new DisplayMetrics();
            display.getRealMetrics(realMetrics);
            expected = realMetrics.widthPixels + "x" + realMetrics.heightPixels;
        }
        Activity activity = mock(Activity.class);
        when(activity.getSystemService(Context.WINDOW_SERVICE)).thenReturn(activityWindowManager);

        Countly countly = new Countly().init(TestUtils.createBaseConfig().enableManualSessionControl());
        CountlyActivityHolder.getInstance().setActivity(activity);
        countly.sessions().beginSession();

        Map<String, String>[] stored = TestUtils.getCurrentRQ();
        Assert.assertEquals(1, stored.length);
        Assert.assertEquals("1", stored[0].get("begin_session"));
        Assert.assertEquals(expected, resolutionIn(stored[0].get("metrics")));
        verify(activity, atLeastOnce()).getSystemService(Context.WINDOW_SERVICE);
        Assert.assertTrue("unexpected incorrect context use: " + incorrectContextUses, incorrectContextUses.isEmpty());
    }

    /**
     * When no display can be resolved, the context's resource metrics are returned, and from API
     * 30 WindowManager is not read from the non-Activity context at all.
     */
    @Test
    public void getDisplayMetrics_withoutDisplay_fallsBackToResourceMetrics() {
        DisplayMetrics resourceMetrics = new DisplayMetrics();
        resourceMetrics.widthPixels = 111;
        resourceMetrics.heightPixels = 222;
        resourceMetrics.density = 1.5f;
        Resources resources = mock(Resources.class);
        when(resources.getDisplayMetrics()).thenReturn(resourceMetrics);
        Context context = mock(Context.class);
        when(context.getResources()).thenReturn(resources);

        DisplayMetrics metrics = UtilsDevice.getDisplayMetrics(context);

        Assert.assertEquals(111, metrics.widthPixels);
        Assert.assertEquals(222, metrics.heightPixels);
        Assert.assertEquals(1.5f, metrics.density, 0.0001f);
        verify(context).getSystemService(Context.DISPLAY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            verify(context, never()).getSystemService(Context.WINDOW_SERVICE);
        }
    }

    /**
     * Routes StrictMode's incorrect context use reports into {@link #incorrectContextUses} and
     * checks that this device reports one, so an empty list afterwards means nothing read
     * WindowManager from the Application context. detectAll is used because on API 30
     * detectIncorrectContextUse is not public and is only enabled through it.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private void startRecordingIncorrectContextUse() {
        previousVmPolicy = StrictMode.getVmPolicy();
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
            .detectAll()
            .penaltyListener(Runnable::run, violation -> {
                if ("IncorrectContextUseViolation".equals(violation.getClass().getSimpleName())) {
                    incorrectContextUses.add(Log.getStackTraceString(violation));
                }
            })
            .build());

        TestUtils.getContext().getSystemService(Context.WINDOW_SERVICE);
        Assert.assertEquals("this device does not report incorrect context use", 1, incorrectContextUses.size());
        incorrectContextUses.clear();
    }

    /**
     * Request generator that records the data of every immediate request and completes each one
     * without a response, so init runs its network calls without reaching a server.
     */
    private static ImmediateRequestGenerator recordingRequestGenerator(@NonNull final List<String> requests) {
        return new ImmediateRequestGenerator() {
            @Override public ImmediateRequestI CreateImmediateRequestMaker() {
                return (requestData, customEndpoint, cp, requestShouldBeDelayed, networkingIsEnabled, callback, log) -> {
                    requests.add(requestData);
                    callback.callback(null);
                };
            }

            @Override public ImmediateRequestI CreatePreflightRequestMaker() {
                return (requestData, customEndpoint, cp, requestShouldBeDelayed, networkingIsEnabled, callback, log) -> callback.callback(null);
            }
        };
    }

    /** Returns the URL-decoded value of the given parameter in a request query string, or null when it is absent. */
    private static String queryParam(@NonNull String requestData, @NonNull String key) {
        for (String pair : requestData.split("&")) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2 && keyValue[0].equals(key)) {
                return UtilsNetworking.urlDecodeString(keyValue[1]);
            }
        }
        return null;
    }

    /** Reads the resolution metric out of a metrics or crash JSON object. */
    private static String resolutionIn(@NonNull String json) throws JSONException {
        return new JSONObject(json).getString("_resolution");
    }

    /** The default display's real size minus its cutout's safe insets, formatted like the resolution metric. */
    @RequiresApi(Build.VERSION_CODES.R)
    private static String defaultDisplayResolution() {
        Display display = TestUtils.getContext().getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
        DisplayMetrics metrics = new DisplayMetrics();
        display.getRealMetrics(metrics);
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        DisplayCutout displayCutout = display.getCutout();
        if (displayCutout != null) {
            width -= displayCutout.getSafeInsetLeft() + displayCutout.getSafeInsetRight();
            height -= displayCutout.getSafeInsetTop() + displayCutout.getSafeInsetBottom();
        }
        return width + "x" + height;
    }
}
