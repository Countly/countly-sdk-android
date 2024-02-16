/*
Copyright (c) 2012, 2013, 2014 Countly

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/
package ly.count.android.sdk;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class DeviceInfoTests {

    DeviceInfo regularDeviceInfo;

    @Before
    public void setUp() {
        Countly.sharedInstance().setLoggingEnabled(true);
        regularDeviceInfo = new DeviceInfo(null);
    }

    @Test
    public void testGetOS() {
        assertEquals("Android", regularDeviceInfo.mp.getOS());
    }

    @Test
    public void testGetOSVersion() {
        assertEquals(android.os.Build.VERSION.RELEASE, regularDeviceInfo.mp.getOSVersion());
    }

    @Test
    public void testGetDevice() {
        assertEquals(android.os.Build.MODEL, regularDeviceInfo.mp.getDevice());
    }

    @Test
    public void testGetResolution() {
        final DisplayMetrics metrics = new DisplayMetrics();
        ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getMetrics(metrics);
        final String expected = metrics.widthPixels + "x" + metrics.heightPixels;
        assertEquals(expected, regularDeviceInfo.mp.getResolution(getContext()));
    }

    @Test
    public void testGetResolution_getWindowManagerReturnsNull() {
        final Context mockContext = mock(Context.class);
        when(mockContext.getSystemService(Context.WINDOW_SERVICE)).thenReturn(null);
        assertEquals("", regularDeviceInfo.mp.getResolution(mockContext));
    }

    @Test
    public void testGetResolution_getDefaultDisplayReturnsNull() {
        final WindowManager mockWindowMgr = mock(WindowManager.class);
        when(mockWindowMgr.getDefaultDisplay()).thenReturn(null);
        final Context mockContext = mock(Context.class);
        when(mockContext.getSystemService(Context.WINDOW_SERVICE)).thenReturn(mockWindowMgr);
        assertEquals("", regularDeviceInfo.mp.getResolution(mockContext));
    }

    private Context mockContextForTestingDensity(final int density) {
        final DisplayMetrics metrics = new DisplayMetrics();
        metrics.densityDpi = density;
        final Resources mockResources = mock(Resources.class);
        when(mockResources.getDisplayMetrics()).thenReturn(metrics);
        final Context mockContext = mock(Context.class);
        when(mockContext.getResources()).thenReturn(mockResources);
        return mockContext;
    }

    @Test
    public void testGetDensity() {
        Context mockContext = mockContextForTestingDensity(DisplayMetrics.DENSITY_LOW);
        assertEquals("LDPI", regularDeviceInfo.mp.getDensity(mockContext));
        mockContext = mockContextForTestingDensity(DisplayMetrics.DENSITY_MEDIUM);
        assertEquals("MDPI", regularDeviceInfo.mp.getDensity(mockContext));
        mockContext = mockContextForTestingDensity(DisplayMetrics.DENSITY_TV);
        assertEquals("TVDPI", regularDeviceInfo.mp.getDensity(mockContext));
        mockContext = mockContextForTestingDensity(DisplayMetrics.DENSITY_HIGH);
        assertEquals("HDPI", regularDeviceInfo.mp.getDensity(mockContext));
        mockContext = mockContextForTestingDensity(DisplayMetrics.DENSITY_XHIGH);
        assertEquals("XHDPI", regularDeviceInfo.mp.getDensity(mockContext));
        mockContext = mockContextForTestingDensity(DisplayMetrics.DENSITY_XXHIGH);
        assertEquals("XXHDPI", regularDeviceInfo.mp.getDensity(mockContext));
        mockContext = mockContextForTestingDensity(DisplayMetrics.DENSITY_XXXHIGH);
        assertEquals("XXXHDPI", regularDeviceInfo.mp.getDensity(mockContext));
        mockContext = mockContextForTestingDensity(DisplayMetrics.DENSITY_260);
        assertEquals("XHDPI", regularDeviceInfo.mp.getDensity(mockContext));
        mockContext = mockContextForTestingDensity(DisplayMetrics.DENSITY_280);
        assertEquals("XHDPI", regularDeviceInfo.mp.getDensity(mockContext));
        mockContext = mockContextForTestingDensity(DisplayMetrics.DENSITY_300);
        assertEquals("XHDPI", regularDeviceInfo.mp.getDensity(mockContext));
        mockContext = mockContextForTestingDensity(DisplayMetrics.DENSITY_340);
        assertEquals("XXHDPI", regularDeviceInfo.mp.getDensity(mockContext));
        mockContext = mockContextForTestingDensity(DisplayMetrics.DENSITY_360);
        assertEquals("XXHDPI", regularDeviceInfo.mp.getDensity(mockContext));
        mockContext = mockContextForTestingDensity(DisplayMetrics.DENSITY_400);
        assertEquals("XXHDPI", regularDeviceInfo.mp.getDensity(mockContext));
        mockContext = mockContextForTestingDensity(DisplayMetrics.DENSITY_420);
        assertEquals("XXHDPI", regularDeviceInfo.mp.getDensity(mockContext));
        mockContext = mockContextForTestingDensity(DisplayMetrics.DENSITY_560);
        assertEquals("XXXHDPI", regularDeviceInfo.mp.getDensity(mockContext));
        mockContext = mockContextForTestingDensity(0);
        assertEquals("other", regularDeviceInfo.mp.getDensity(mockContext));
        mockContext = mockContextForTestingDensity(1_234_567_890);
        assertEquals("other", regularDeviceInfo.mp.getDensity(mockContext));
    }

    @Test
    public void testGetCarrier_nullTelephonyManager() {
        final Context mockContext = mock(Context.class);
        when(mockContext.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(null);
        assertEquals("", regularDeviceInfo.mp.getCarrier(mockContext));
    }

    @Test
    public void testGetCarrier_nullNetOperator() {
        final TelephonyManager mockTelephonyManager = mock(TelephonyManager.class);
        when(mockTelephonyManager.getNetworkOperatorName()).thenReturn(null);
        final Context mockContext = mock(Context.class);
        when(mockContext.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(mockTelephonyManager);
        assertEquals("", regularDeviceInfo.mp.getCarrier(mockContext));
    }

    @Test
    public void testGetCarrier_emptyNetOperator() {
        final TelephonyManager mockTelephonyManager = mock(TelephonyManager.class);
        when(mockTelephonyManager.getNetworkOperatorName()).thenReturn("");
        final Context mockContext = mock(Context.class);
        when(mockContext.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(mockTelephonyManager);
        assertEquals("", regularDeviceInfo.mp.getCarrier(mockContext));
    }

    @Test
    public void testGetCarrier() {
        final TelephonyManager mockTelephonyManager = mock(TelephonyManager.class);
        when(mockTelephonyManager.getNetworkOperatorName()).thenReturn("Verizon");
        final Context mockContext = mock(Context.class);
        when(mockContext.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(mockTelephonyManager);
        assertEquals("Verizon", regularDeviceInfo.mp.getCarrier(mockContext));
    }

    @Test
    public void testGetLocale() {
        final Locale defaultLocale = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("ab", "CD"));
            assertEquals("ab_CD", regularDeviceInfo.mp.getLocale());
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    @Test
    public void testGetAppVersion() throws PackageManager.NameNotFoundException {
        final PackageInfo pkgInfo = new PackageInfo();
        pkgInfo.versionName = "42.0";
        final String fakePkgName = "i.like.chicken";
        final PackageManager mockPkgMgr = mock(PackageManager.class);
        when(mockPkgMgr.getPackageInfo(fakePkgName, 0)).thenReturn(pkgInfo);
        final Context mockContext = mock(Context.class);
        when(mockContext.getPackageName()).thenReturn(fakePkgName);
        when(mockContext.getPackageManager()).thenReturn(mockPkgMgr);
        assertEquals("42.0", regularDeviceInfo.mp.getAppVersion(mockContext));
    }

    @Test
    public void testGetAppVersion_pkgManagerThrows() throws PackageManager.NameNotFoundException {
        final String fakePkgName = "i.like.chicken";
        final PackageManager mockPkgMgr = mock(PackageManager.class);
        when(mockPkgMgr.getPackageInfo(fakePkgName, 0)).thenThrow(new PackageManager.NameNotFoundException());
        final Context mockContext = mock(Context.class);
        when(mockContext.getPackageName()).thenReturn(fakePkgName);
        when(mockContext.getPackageManager()).thenReturn(mockPkgMgr);
        assertEquals("1.0", regularDeviceInfo.mp.getAppVersion(mockContext));
    }

    @Test
    public void testGetMetrics() throws UnsupportedEncodingException, JSONException {
        final JSONObject json = new JSONObject();
        json.put("_device", regularDeviceInfo.mp.getDevice());
        json.put("_os", regularDeviceInfo.mp.getOS());
        json.put("_os_version", regularDeviceInfo.mp.getOSVersion());
        if (!"".equals(regularDeviceInfo.mp.getCarrier(getContext()))) { // ensure tests pass on non-cellular devices
            json.put("_carrier", regularDeviceInfo.mp.getCarrier(getContext()));
        }
        json.put("_resolution", regularDeviceInfo.mp.getResolution(getContext()));
        json.put("_density", regularDeviceInfo.mp.getDensity(getContext()));
        json.put("_locale", regularDeviceInfo.mp.getLocale());
        json.put("_app_version", regularDeviceInfo.mp.getAppVersion(getContext()));
        json.put("_manufacturer", regularDeviceInfo.mp.getManufacturer());
        json.put("_has_hinge", regularDeviceInfo.mp.hasHinge(getContext()));
        json.put("_device_type", regularDeviceInfo.mp.getDeviceType(getContext()));

        String calculatedMetrics = URLDecoder.decode(regularDeviceInfo.getMetrics(getContext(), null), "UTF-8");
        final JSONObject calculatedJSON = new JSONObject(calculatedMetrics);
        TestUtils.bothJSONObjEqual(json, calculatedJSON);
    }

    @Test
    public void testGetMetricsWithOverride() throws UnsupportedEncodingException, JSONException {
        Map<String, String> metricOverride = new HashMap<>();
        metricOverride.put("123", "bb");
        metricOverride.put("456", "cc");
        metricOverride.put("Test", "aa");

        final JSONObject json = new JSONObject();
        json.put("_device", regularDeviceInfo.mp.getDevice());
        json.put("_os", regularDeviceInfo.mp.getOS());
        json.put("_os_version", regularDeviceInfo.mp.getOSVersion());
        if (!"".equals(regularDeviceInfo.mp.getCarrier(getContext()))) { // ensure tests pass on non-cellular devices
            json.put("_carrier", regularDeviceInfo.mp.getCarrier(getContext()));
        }
        json.put("_resolution", regularDeviceInfo.mp.getResolution(getContext()));
        json.put("_density", regularDeviceInfo.mp.getDensity(getContext()));
        json.put("_locale", regularDeviceInfo.mp.getLocale());
        json.put("_app_version", regularDeviceInfo.mp.getAppVersion(getContext()));
        json.put("_manufacturer", regularDeviceInfo.mp.getManufacturer());
        json.put("_device_type", regularDeviceInfo.mp.getDeviceType(getContext()));
        json.put("_has_hinge", regularDeviceInfo.mp.hasHinge(getContext()));
        json.put("123", "bb");
        json.put("456", "cc");
        json.put("Test", "aa");

        String calculatedMetrics = URLDecoder.decode(regularDeviceInfo.getMetrics(getContext(), metricOverride), "UTF-8");
        final JSONObject calculatedJSON = new JSONObject(calculatedMetrics);
        TestUtils.bothJSONObjEqual(json, calculatedJSON);
    }

    @Test
    public void testGetMetricsWithOverride_2() throws UnsupportedEncodingException, JSONException {
        Map<String, String> metricOverride = new HashMap<>();
        metricOverride.put("_device", "a1");
        metricOverride.put("_os", "b2");
        metricOverride.put("_os_version", "c3");
        metricOverride.put("_carrier", "d1");
        metricOverride.put("_resolution", "d2");
        metricOverride.put("_density", "d3");
        metricOverride.put("_locale", "d4");
        metricOverride.put("_app_version", "d5");
        metricOverride.put("asd", "123");

        final JSONObject json = new JSONObject();
        json.put("_device", "a1");
        json.put("_os", "b2");
        json.put("_os_version", "c3");
        json.put("_carrier", "d1");
        json.put("_resolution", "d2");
        json.put("_density", "d3");
        json.put("_locale", "d4");
        json.put("_app_version", "d5");
        json.put("_manufacturer", regularDeviceInfo.mp.getManufacturer());
        json.put("_has_hinge", regularDeviceInfo.mp.hasHinge(getContext()));
        json.put("_device_type", regularDeviceInfo.mp.getDeviceType(getContext()));
        json.put("asd", "123");

        String calculatedMetrics = URLDecoder.decode(regularDeviceInfo.getMetrics(getContext(), metricOverride), "UTF-8");
        final JSONObject calculatedJSON = new JSONObject(calculatedMetrics);
        TestUtils.bothJSONObjEqual(json, calculatedJSON);
    }

    @Test
    public void getAppVersionWithOverride() {
        Map<String, String> metricOverride = new HashMap<>();
        metricOverride.put("_app_version", "d5");
        Assert.assertNotNull(regularDeviceInfo.getAppVersionWithOverride(getContext(), null));

        Assert.assertEquals("d5", regularDeviceInfo.getAppVersionWithOverride(getContext(), metricOverride));

        metricOverride.put("_app_version", null);
        Assert.assertNotNull(regularDeviceInfo.getAppVersionWithOverride(getContext(), metricOverride));

        metricOverride.put("_app_version", "");
        Assert.assertEquals("", regularDeviceInfo.getAppVersionWithOverride(getContext(), metricOverride));
    }
}
