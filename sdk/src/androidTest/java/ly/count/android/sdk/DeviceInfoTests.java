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
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class DeviceInfoTests {

    @Before
    public void setUp() {
        Countly.sharedInstance().setLoggingEnabled(true);
    }

    @Test
    public void testGetOS() {
        assertEquals("Android", DeviceInfo.getOS());
    }

    @Test
    public void testGetOSVersion() {
        assertEquals(android.os.Build.VERSION.RELEASE, DeviceInfo.getOSVersion());
    }

    @Test
    public void testGetDevice() {
        assertEquals(android.os.Build.MODEL, DeviceInfo.getDevice());
    }

    @Test
    public void testGetResolution() {
        final DisplayMetrics metrics = new DisplayMetrics();
        ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getMetrics(metrics);
        final String expected = metrics.widthPixels + "x" + metrics.heightPixels;
        assertEquals(expected, DeviceInfo.getResolution(getContext()));
    }

    @Test
    public void testGetResolution_getWindowManagerReturnsNull() {
        final Context mockContext = mock(Context.class);
        when(mockContext.getSystemService(Context.WINDOW_SERVICE)).thenReturn(null);
        assertEquals("", DeviceInfo.getResolution(mockContext));
    }

    @Test
    public void testGetResolution_getDefaultDisplayReturnsNull() {
        final WindowManager mockWindowMgr = mock(WindowManager.class);
        when(mockWindowMgr.getDefaultDisplay()).thenReturn(null);
        final Context mockContext = mock(Context.class);
        when(mockContext.getSystemService(Context.WINDOW_SERVICE)).thenReturn(mockWindowMgr);
        assertEquals("", DeviceInfo.getResolution(mockContext));
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
        assertEquals("LDPI", DeviceInfo.getDensity(mockContext));
        mockContext = mockContextForTestingDensity(DisplayMetrics.DENSITY_MEDIUM);
        assertEquals("MDPI", DeviceInfo.getDensity(mockContext));
        mockContext = mockContextForTestingDensity(DisplayMetrics.DENSITY_TV);
        assertEquals("TVDPI", DeviceInfo.getDensity(mockContext));
        mockContext = mockContextForTestingDensity(DisplayMetrics.DENSITY_HIGH);
        assertEquals("HDPI", DeviceInfo.getDensity(mockContext));
        mockContext = mockContextForTestingDensity(DisplayMetrics.DENSITY_XHIGH);
        assertEquals("XHDPI", DeviceInfo.getDensity(mockContext));
        mockContext = mockContextForTestingDensity(DisplayMetrics.DENSITY_XXHIGH);
        assertEquals("XXHDPI", DeviceInfo.getDensity(mockContext));
        mockContext = mockContextForTestingDensity(DisplayMetrics.DENSITY_XXXHIGH);
        assertEquals("XXXHDPI", DeviceInfo.getDensity(mockContext));
        mockContext = mockContextForTestingDensity(DisplayMetrics.DENSITY_260);
        assertEquals("XHDPI", DeviceInfo.getDensity(mockContext));
        mockContext = mockContextForTestingDensity(DisplayMetrics.DENSITY_280);
        assertEquals("XHDPI", DeviceInfo.getDensity(mockContext));
        mockContext = mockContextForTestingDensity(DisplayMetrics.DENSITY_300);
        assertEquals("XHDPI", DeviceInfo.getDensity(mockContext));
        mockContext = mockContextForTestingDensity(DisplayMetrics.DENSITY_340);
        assertEquals("XXHDPI", DeviceInfo.getDensity(mockContext));
        mockContext = mockContextForTestingDensity(DisplayMetrics.DENSITY_360);
        assertEquals("XXHDPI", DeviceInfo.getDensity(mockContext));
        mockContext = mockContextForTestingDensity(DisplayMetrics.DENSITY_400);
        assertEquals("XXHDPI", DeviceInfo.getDensity(mockContext));
        mockContext = mockContextForTestingDensity(DisplayMetrics.DENSITY_420);
        assertEquals("XXHDPI", DeviceInfo.getDensity(mockContext));
        mockContext = mockContextForTestingDensity(DisplayMetrics.DENSITY_560);
        assertEquals("XXXHDPI", DeviceInfo.getDensity(mockContext));
        mockContext = mockContextForTestingDensity(0);
        assertEquals("other", DeviceInfo.getDensity(mockContext));
        mockContext = mockContextForTestingDensity(1234567890);
        assertEquals("other", DeviceInfo.getDensity(mockContext));
    }

    @Test
    public void testGetCarrier_nullTelephonyManager() {
        final Context mockContext = mock(Context.class);
        when(mockContext.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(null);
        assertEquals("", DeviceInfo.getCarrier(mockContext));
    }

    @Test
    public void testGetCarrier_nullNetOperator() {
        final TelephonyManager mockTelephonyManager = mock(TelephonyManager.class);
        when(mockTelephonyManager.getNetworkOperatorName()).thenReturn(null);
        final Context mockContext = mock(Context.class);
        when(mockContext.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(mockTelephonyManager);
        assertEquals("", DeviceInfo.getCarrier(mockContext));
    }

    @Test
    public void testGetCarrier_emptyNetOperator() {
        final TelephonyManager mockTelephonyManager = mock(TelephonyManager.class);
        when(mockTelephonyManager.getNetworkOperatorName()).thenReturn("");
        final Context mockContext = mock(Context.class);
        when(mockContext.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(mockTelephonyManager);
        assertEquals("", DeviceInfo.getCarrier(mockContext));
    }

    @Test
    public void testGetCarrier() {
        final TelephonyManager mockTelephonyManager = mock(TelephonyManager.class);
        when(mockTelephonyManager.getNetworkOperatorName()).thenReturn("Verizon");
        final Context mockContext = mock(Context.class);
        when(mockContext.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(mockTelephonyManager);
        assertEquals("Verizon", DeviceInfo.getCarrier(mockContext));
    }

    @Test
    public void testGetLocale() {
        final Locale defaultLocale = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("ab", "CD"));
            assertEquals("ab_CD", DeviceInfo.getLocale());
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
        assertEquals("42.0", DeviceInfo.getAppVersion(mockContext));
    }

    @Test
    public void testGetAppVersion_pkgManagerThrows() throws PackageManager.NameNotFoundException {
        final String fakePkgName = "i.like.chicken";
        final PackageManager mockPkgMgr = mock(PackageManager.class);
        when(mockPkgMgr.getPackageInfo(fakePkgName, 0)).thenThrow(new PackageManager.NameNotFoundException());
        final Context mockContext = mock(Context.class);
        when(mockContext.getPackageName()).thenReturn(fakePkgName);
        when(mockContext.getPackageManager()).thenReturn(mockPkgMgr);
        assertEquals("1.0", DeviceInfo.getAppVersion(mockContext));
    }

    @Test
    public void testGetMetrics() throws UnsupportedEncodingException, JSONException {
        final JSONObject json = new JSONObject();
        json.put("_device", DeviceInfo.getDevice());
        json.put("_os", DeviceInfo.getOS());
        json.put("_os_version", DeviceInfo.getOSVersion());
        if (!"".equals(DeviceInfo.getCarrier(getContext()))) { // ensure tests pass on non-cellular devices
            json.put("_carrier", DeviceInfo.getCarrier(getContext()));
        }
        json.put("_resolution", DeviceInfo.getResolution(getContext()));
        json.put("_density", DeviceInfo.getDensity(getContext()));
        json.put("_locale", DeviceInfo.getLocale());
        json.put("_app_version", DeviceInfo.getAppVersion(getContext()));
        json.put("_manufacturer", DeviceInfo.getManufacturer());
        json.put("_device_type", DeviceInfo.getDeviceType(getContext()));
        final String expected = URLEncoder.encode(json.toString(), "UTF-8");
        assertNotNull(expected);
        assertEquals(expected, DeviceInfo.getMetrics(getContext(), null));
    }

    @Test
    public void testGetMetricsWithOverride() throws UnsupportedEncodingException, JSONException {
        Map<String, String> metricOverride = new HashMap<>();
        metricOverride.put("123", "bb");
        metricOverride.put("456", "cc");
        metricOverride.put("Test", "aa");

        final JSONObject json = new JSONObject();
        json.put("_device", DeviceInfo.getDevice());
        json.put("_os", DeviceInfo.getOS());
        json.put("_os_version", DeviceInfo.getOSVersion());
        if (!"".equals(DeviceInfo.getCarrier(getContext()))) { // ensure tests pass on non-cellular devices
            json.put("_carrier", DeviceInfo.getCarrier(getContext()));
        }
        json.put("_resolution", DeviceInfo.getResolution(getContext()));
        json.put("_density", DeviceInfo.getDensity(getContext()));
        json.put("_locale", DeviceInfo.getLocale());
        json.put("_app_version", DeviceInfo.getAppVersion(getContext()));
        json.put("_manufacturer", DeviceInfo.getManufacturer());
        json.put("_device_type", DeviceInfo.getDeviceType(getContext()));
        json.put("123", "bb");
        json.put("456", "cc");
        json.put("Test", "aa");
        final String expected = URLEncoder.encode(json.toString(), "UTF-8");
        assertNotNull(expected);
        assertEquals(expected, DeviceInfo.getMetrics(getContext(), metricOverride));
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
        json.put("_resolution", "d2");
        json.put("_density", "d3");
        json.put("_locale", "d4");
        json.put("_app_version", "d5");
        json.put("_manufacturer", DeviceInfo.getManufacturer());
        json.put("_device_type", DeviceInfo.getDeviceType(getContext()));
        json.put("_carrier", "d1");
        json.put("asd", "123");

        final String expected = URLEncoder.encode(json.toString(), "UTF-8");
        assertNotNull(expected);
        assertEquals(expected, DeviceInfo.getMetrics(getContext(), metricOverride));
    }

    @Test
    public void testFillJSONIfValuesNotEmpty_noValues() {
        final JSONObject mockJSON = mock(JSONObject.class);
        DeviceInfo.fillJSONIfValuesNotEmpty(mockJSON);
        verifyZeroInteractions(mockJSON);
    }

    @Test
    public void testFillJSONIfValuesNotEmpty_oddNumberOfValues() {
        final JSONObject mockJSON = mock(JSONObject.class);
        DeviceInfo.fillJSONIfValuesNotEmpty(mockJSON, "key1", "value1", "key2");
        verifyZeroInteractions(mockJSON);
    }

    @Test
    public void testFillJSONIfValuesNotEmpty() throws JSONException {
        final JSONObject json = new JSONObject();
        DeviceInfo.fillJSONIfValuesNotEmpty(json, "key1", "value1", "key2", null, "key3", "value3", "key4", "", "key5", "value5");
        assertEquals("value1", json.get("key1"));
        assertFalse(json.has("key2"));
        assertEquals("value3", json.get("key3"));
        assertFalse(json.has("key4"));
        assertEquals("value5", json.get("key5"));
    }
}
