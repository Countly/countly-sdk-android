package ly.count.android.sdk;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class UtilsDeviceTests {

    /**
     * getThemeMode and appendThemeParam prefer the foreground Activity's configuration. These
     * tests exercise the fallback-context path, so make sure no Activity is registered from a
     * previously run test in the same process.
     */
    @Before
    public void setUp() {
        clearForegroundActivity();
    }

    @After
    public void tearDown() {
        clearForegroundActivity();
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
}
