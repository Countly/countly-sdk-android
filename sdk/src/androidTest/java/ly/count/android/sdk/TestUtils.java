package ly.count.android.sdk;

import android.app.Activity;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.mockito.ArgumentCaptor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class TestUtils {
    //Useful call:
    //mockingDetails(mockObj).printInvocations()
    //
    //

    //convenience arrays for referencing during tests
    public static final String[] eKeys = { "eventKey1", "eventKey2", "eventKey3", "eventKey4", "eventKey5", "eventKey6", "eventKey7" };
    public static final String[] vNames = { "vienName1", "vienName2", "vienName3", "vienName4", "vienName5", "vienName6", "vienName7" };
    public static final String[] requestEntries = { "blah", "blah1", "blah2", "123", "456", "678", "890" };
    public static final String[] tooOldRequestEntries = { "&timestamp=1664273584000", "&timestamp=1664273554000", "&timestamp=1664272584000" };
    public static final String[] viewIDVals = { "idv1", "idv2", "idv3", "idv4", "idv5", "idv6", "idv7", "idv8", "idv9", "idv10" };
    public static final String[] eventIDVals = { "ide1", "ide2", "ide3", "ide4", "ide5", "ide6", "ide7", "ide8", "ide9", "ide10" };

    //common values used for SDK init during tests
    public final static String commonURL = "http://test.count.ly";
    public final static String commonAppKey = "appkey";
    public final static String commonDeviceId = "1234";
    public final static String commonApplicationVersion = "1.0";
    public final static String SDK_NAME = "java-native-android";
    public final static String SDK_VERSION = "24.1.2-RC1";

    public static class Activity2 extends Activity {
    }

    public static class Activity3 extends Activity {
    }

    public static CountlyConfig createConfigurationConfig(boolean enableServerConfig, ImmediateRequestGenerator irGen) {
        CountlyConfig cc = createBaseConfig();

        cc.immediateRequestGenerator = irGen;

        if (enableServerConfig) {
            cc.enableServerConfiguration();
        }

        return cc;
    }

    public static CountlyConfig createVariantConfig(ImmediateRequestGenerator irGen) {
        CountlyConfig cc = createBaseConfig();

        cc.immediateRequestGenerator = irGen;

        return cc;
    }

    public static CountlyConfig createConsentCountlyConfig(boolean requiresConsent, String[] givenConsent, ModuleBase testModuleListener, RequestQueueProvider rqp) {
        CountlyConfig cc = createBaseConfig();
        cc.setRequiresConsent(requiresConsent)
            .setConsentEnabled(givenConsent)
            .disableHealthCheck();//mocked tests fail without disabling this
        cc.testModuleListener = testModuleListener;
        cc.requestQueueProvider = rqp;

        return cc;
    }

    public static CountlyConfig createConsentCountlyConfig(boolean requiresConsent, String[] givenConsent, ModuleBase testModuleListener) {
        return createConsentCountlyConfig(requiresConsent, givenConsent, testModuleListener, null);
    }

    public static CountlyConfig createAttributionCountlyConfig(boolean requiresConsent, String[] givenConsent, ModuleBase testModuleListener, RequestQueueProvider rqp, String daType, String daValue, Map<String, String> iaValues) {
        CountlyConfig cc = createBaseConfig();
        cc.setDirectAttribution(daType, daValue)
            .setIndirectAttribution(iaValues)
            .setRequiresConsent(requiresConsent)
            .setConsentEnabled(givenConsent)
            .disableHealthCheck();//mocked tests fail without disabling this
        cc.testModuleListener = testModuleListener;
        cc.requestQueueProvider = rqp;
        return cc;
    }

    public static CountlyConfig createViewCountlyConfig(boolean orientationTracking, boolean useShortNames, boolean automaticViewTracking, SafeIDGenerator safeViewIDGenerator, Map<String, Object> globalViewSegms) {
        CountlyConfig cc = createBaseConfig();
        cc.setTrackOrientationChanges(orientationTracking);

        if (useShortNames) {
            cc.enableAutomaticViewShortNames();
        }

        cc.safeViewIDGenerator = safeViewIDGenerator;
        cc.setGlobalViewSegmentation(globalViewSegms);
        if (automaticViewTracking) {
            cc.enableAutomaticViewTracking();
        }
        return cc;
    }

    public static CountlyConfig createScenarioEventIDConfig(SafeIDGenerator safeViewIDGenerator, SafeIDGenerator safeEventIDGenerator) {
        CountlyConfig cc = createBaseConfig();

        cc.enableAutomaticViewShortNames();
        cc.safeViewIDGenerator = safeViewIDGenerator;
        cc.safeEventIDGenerator = safeEventIDGenerator;
        return cc;
    }

    public static CountlyConfig createBaseConfig() {
        CountlyConfig cc = new CountlyConfig(getContext(), commonAppKey, commonURL)
            .setDeviceId(commonDeviceId)
            .setLoggingEnabled(true)
            .enableCrashReporting();

        return cc;
    }

    public static String[] createStringArray(int count) {
        String[] sArr = new String[count];
        Random rnd = new Random();

        for (int a = 0; a < sArr.length; a++) {
            sArr[a] = "" + rnd.nextInt();
        }

        return sArr;
    }

    public static Map<String, Object> createMapString(int count) {
        Map<String, Object> mRes = new HashMap<>(count);
        Random rnd = new Random();

        for (int a = 0; a < count; a++) {
            mRes.put("" + rnd.nextInt(), "" + rnd.nextInt());
        }

        return mRes;
    }

    public static StorageProvider setStorageProviderToMock(Countly countly, StorageProvider sp) {
        for (ModuleBase module : countly.modules) {
            module.storageProvider = sp;
        }
        countly.config_.storageProvider = sp;

        return sp;
    }

    public static EventProvider setEventProviderToMock(Countly countly, EventProvider ep) {
        for (ModuleBase module : countly.modules) {
            module.eventProvider = ep;
        }
        countly.config_.eventProvider = ep;

        return ep;
    }

    public static EventQueueProvider setCreateEventQueueProviderMock(Countly countly) {
        return setEventQueueProviderToMock(countly, mock(EventQueueProvider.class));
    }

    public static EventQueueProvider setEventQueueProviderToMock(Countly countly, EventQueueProvider eqp) {
        countly.moduleEvents.eventQueueProvider = eqp;
        countly.config_.eventQueueProvider = eqp;
        return eqp;
    }

    public static RequestQueueProvider setRequestQueueProviderToMock(Countly countly, RequestQueueProvider rqp) {
        for (ModuleBase module : countly.modules) {
            module.requestQueueProvider = rqp;
        }
        countly.config_.requestQueueProvider = rqp;
        countly.requestQueueProvider = rqp;

        return rqp;
    }

    @SuppressWarnings("InfiniteRecursion")
    public static void stackOverflow() {
        stackOverflow();
    }

    @SuppressWarnings("ConstantConditions")
    public static Countly crashTest(int crashNumber) {

        if (crashNumber == 1) {
            stackOverflow();
        } else if (crashNumber == 2) {
            // noinspection divzero
            @SuppressWarnings("NumericOverflow") int test = 10 / 0;
        } else if (crashNumber == 3) {
            throw new RuntimeException("This is a crash");
        } else {
            String test = null;
            //noinspection ResultOfMethodCallIgnored
            test.charAt(1);
        }
        return Countly.sharedInstance();
    }

    public static void bothJSONObjEqual(@NonNull JSONObject jA, @NonNull JSONObject jB) throws JSONException {
        Assert.assertNotNull(jA);
        Assert.assertNotNull(jB);
        Assert.assertEquals(jA.length(), jB.length());

        Iterator<String> iter = jA.keys();
        while (iter.hasNext()) {
            String key = iter.next();

            Assert.assertEquals(jA.get(key), jB.get(key));
        }
    }

    public static List<String> getRequestsWithParam(String[] requests, String param) {
        List<String> filteredRequests = new ArrayList<>();
        String targetParamValue = "&" + param + "=";

        for (String entry : requests) {
            if (entry.contains(targetParamValue)) {
                filteredRequests.add(entry);
            }
        }

        return filteredRequests;
    }

    public static String getParamValueFromRequest(String request, String param) {
        String[] params = request.split("&");

        for (String entry : params) {
            String[] pair = entry.split("=");
            if (pair[0].equals(param)) {
                return pair[1];
            }
        }

        return null;
    }

    public static void validateThatRQContainsCorrectEntry(CountlyStore store, String param, String targetValue, int entryCount) {
        List<String> filteredVals = TestUtils.getRequestsWithParam(store.getRequests(), param);
        Assert.assertEquals(entryCount, filteredVals.size());

        if (entryCount != 0) {
            String paramValue = TestUtils.getParamValueFromRequest(filteredVals.get(0), param);
            Assert.assertEquals(targetValue, paramValue);
        }
    }

    public static String[] subtractConsentFromArray(String[] input, String[] subtraction) {
        ArrayList<String> res = new ArrayList<>();

        for (String v : input) {
            boolean contains = false;
            for (String sv : subtraction) {
                if (sv.equals(v)) {
                    contains = true;
                    break;
                }
            }

            if (!contains) {
                res.add(v);
            }
        }

        return res.toArray(new String[0]);
    }

    public static String[] getReminderConsent(String[] subtraction) {
        return subtractConsentFromArray(ModuleConsentTests.usedFeatureNames, subtraction);
    }

    public static void verifyLocationValuesInRQMockDisabled(RequestQueueProvider rqp) {
        verifyLocationValuesInRQMock(1, true, null, null, null, null, rqp);
    }

    public static void verifyLocationValuesInRQMockNotGiven(RequestQueueProvider rqp) {
        verifyLocationValuesInRQMock(0, true, null, null, null, null, rqp);
    }

    public static void verifyLocationValuesInRQMockValues(String countryCode, String city, String location, String ip, RequestQueueProvider rqp) {
        verifyLocationValuesInRQMock(1, false, countryCode, city, location, ip, rqp);
    }

    public static void verifyLocationValuesInRQMock(int count, Boolean enabled, String countryCode, String city, String location, String ip, RequestQueueProvider rqp) {
        ArgumentCaptor<Boolean> acLocationDisabled = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<String> acCountryCode = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> acCity = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> acGps = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> acIp = ArgumentCaptor.forClass(String.class);
        verify(rqp, times(count)).sendLocation(acLocationDisabled.capture(), acCountryCode.capture(), acCity.capture(), acGps.capture(), acIp.capture());

        if (count == 0) {
            return;
        }

        Assert.assertEquals(enabled, acLocationDisabled.getValue());
        Assert.assertEquals(countryCode, acCountryCode.getValue());
        Assert.assertEquals(city, acCity.getValue());
        Assert.assertEquals(location, acGps.getValue());
        Assert.assertEquals(ip, acIp.getValue());
    }

    public static void verifyConsentValuesInRQMock(int count, String[] valuesTrue, String[] valuesFalse, RequestQueueProvider rqp) throws JSONException {
        ArgumentCaptor<String> consentChanges = ArgumentCaptor.forClass(String.class);
        verify(rqp, times(count)).sendConsentChanges(consentChanges.capture());

        String changes = consentChanges.getValue();
        Assert.assertNotNull(changes);

        JSONObject jObj = new JSONObject(changes);

        Assert.assertEquals(ModuleConsentTests.usedFeatureNames.length, jObj.length());
        Assert.assertEquals(ModuleConsentTests.usedFeatureNames.length, valuesTrue.length + valuesFalse.length);

        for (String v : valuesTrue) {
            Assert.assertTrue((Boolean) jObj.get(v));
        }

        for (String v : valuesFalse) {
            Assert.assertFalse((Boolean) jObj.get(v));
        }
    }

    public static void validateRecordEventInternalMock(EventProvider ep, String eventKey, Map<String, Object> segmentation, Integer count, Double sum, Double duration, UtilsTime.Instant instant, String idOverride) {
        validateRecordEventInternalMock(ep, eventKey, segmentation, count, sum, duration, instant, idOverride, 0, 1);
    }

    public static void validateRecordEventInternalMock(EventProvider ep, String eventKey) {
        validateRecordEventInternalMock(ep, eventKey, null, null, null, null, null, null, 0, 1);
    }

    public static void validateRecordEventInternalMock(EventProvider ep, String eventKey, Map<String, Object> segmentation) {
        validateRecordEventInternalMock(ep, eventKey, segmentation, 1, 0.0, 0.0, null, null, 0, 1);
    }

    public static void validateRecordEventInternalMock(EventProvider ep, String eventKey, Map<String, Object> segmentation, String idOverride, int index, Integer interactionCount) {
        validateRecordEventInternalMock(ep, eventKey, segmentation, 1, 0.0, 0.0, null, idOverride, index, interactionCount);
    }

    public static void validateRecordEventInternalMock(EventProvider ep, String eventKey, double duration, Map<String, Object> segmentation, String idOverride, int index, Integer interactionCount) {
        validateRecordEventInternalMock(ep, eventKey, segmentation, 1, 0.0, duration, null, idOverride, index, interactionCount);
    }

    public static void validateRecordEventInternalMockInteractions(EventProvider ep, int interactionCount) {
        ArgumentCaptor<String> arg1 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> arg2 = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Integer> arg3 = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Double> arg4 = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> arg5 = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<UtilsTime.Instant> arg6 = ArgumentCaptor.forClass(UtilsTime.Instant.class);
        ArgumentCaptor<String> arg7 = ArgumentCaptor.forClass(String.class);

        verify(ep, times(interactionCount)).recordEventInternal(arg1.capture(), arg2.capture(), arg3.capture(), arg4.capture(), arg5.capture(), arg6.capture(), arg7.capture());
    }

    public static void validateRecordEventInternalMock(final @NonNull EventProvider ep, final @NonNull String eventKey, final @Nullable Map<String, Object> segmentation, final @Nullable Integer count, final @Nullable Double sum, final @Nullable Double duration,
        final @Nullable UtilsTime.Instant instant, final @Nullable String idOverride, int index, int interactionCount) {

        ArgumentCaptor<String> arg1 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> arg2 = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Integer> arg3 = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Double> arg4 = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> arg5 = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<UtilsTime.Instant> arg6 = ArgumentCaptor.forClass(UtilsTime.Instant.class);
        ArgumentCaptor<String> arg7 = ArgumentCaptor.forClass(String.class);
        verify(ep, times(interactionCount)).recordEventInternal(arg1.capture(), arg2.capture(), arg3.capture(), arg4.capture(), arg5.capture(), arg6.capture(), arg7.capture());
        //verify(ep).recordEventInternal(arg1.capture(), arg2.capture(), arg3.capture(), arg4.capture(), arg5.capture(), arg6.capture(), arg7.capture());

        if (interactionCount == 0) {
            return;
        }
        String cEventKey = arg1.getAllValues().get(index);
        Map cSegment = arg2.getAllValues().get(index);
        Integer cCount = arg3.getAllValues().get(index);
        Double cSum = arg4.getAllValues().get(index);
        Double cDuration = arg5.getAllValues().get(index);
        UtilsTime.Instant cInstant = arg6.getAllValues().get(index);
        String cIdOverride = arg7.getAllValues().get(index);

        Assert.assertNotNull(cEventKey);
        Assert.assertEquals(eventKey, cEventKey);

        if (segmentation != null) {
            Assert.assertEquals(segmentation, cSegment);
        }

        Assert.assertTrue(cCount > 0);
        if (count != null) {
            Assert.assertEquals(count, cCount);
        }

        if (sum != null) {
            Assert.assertEquals(sum, cSum);
        }

        Assert.assertTrue(cDuration >= 0);
        if (duration != null) {
            Assert.assertEquals(duration, cDuration);
        }

        if (instant != null) {
            Assert.assertTrue(cInstant.timestampMs > 0);
            Assert.assertEquals(instant.timestampMs, cInstant.timestampMs);
            Assert.assertEquals(instant.hour, cInstant.hour);
            Assert.assertEquals(instant.dow, cInstant.dow);
        }

        if (cIdOverride != null) {
            Assert.assertTrue(cIdOverride.length() > 0);
        }
        if (idOverride != null) {
            Assert.assertEquals(idOverride, cIdOverride);
        }
    }

    public static void verifyBeginSessionNotCalled(RequestQueueProvider requestQueueProvider) {
        verifyBeginSessionTimes(requestQueueProvider, 0);
    }

    public static void verifyBeginSessionTimes(RequestQueueProvider requestQueueProvider, int count) {
        ArgumentCaptor<Boolean> arg1 = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<String> arg2 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> arg3 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> arg4 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> arg5 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> arg6 = ArgumentCaptor.forClass(String.class);

        verify(requestQueueProvider, count == 0 ? never() : times(count)).beginSession(arg1.capture(), arg2.capture(), arg3.capture(), arg4.capture(), arg5.capture(), arg6.capture());
    }

    public static void verifyBeginSessionValues(RequestQueueProvider requestQueueProvider, Boolean v1, String v2, String v3, String v4, String v5) {
        ArgumentCaptor<Boolean> arg1 = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<String> arg2 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> arg3 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> arg4 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> arg5 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> arg6 = ArgumentCaptor.forClass(String.class);

        verify(requestQueueProvider, times(1)).beginSession(arg1.capture(), arg2.capture(), arg3.capture(), arg4.capture(), arg5.capture(), arg6.capture());

        Assert.assertEquals(v1, arg1.getAllValues().get(0));
        Assert.assertEquals(v2, arg2.getAllValues().get(0));
        Assert.assertEquals(v3, arg3.getAllValues().get(0));
        Assert.assertEquals(v4, arg4.getAllValues().get(0));
        Assert.assertEquals(v5, arg5.getAllValues().get(0));
    }

    public static void verifyCurrentPreviousViewID(ModuleViews mv, String current, String previous) {
        Assert.assertEquals(current, mv.getCurrentViewId());
        Assert.assertEquals(previous, mv.getPreviousViewId());
    }

    protected static CountlyStore getCountyStore() {
        return new CountlyStore(getContext(), mock(ModuleLog.class), false);
    }

    /**
     * Get current request queue from target folder
     *
     * @return array of request params
     */
    protected static Map<String, String>[] getCurrentRQ() {

        //get all request files from target folder
        String[] requests = getCountyStore().getRequests();
        //create array of request params
        Map<String, String>[] resultMapArray = new ConcurrentHashMap[requests.length];

        for (int i = 0; i < requests.length; i++) {

            String[] params = requests[i].split("&");

            Map<String, String> paramMap = new ConcurrentHashMap<>();
            for (String param : params) {
                String[] pair = param.split("=");
                paramMap.put(UtilsNetworking.urlDecodeString(pair[0]), pair.length == 1 ? "" : UtilsNetworking.urlDecodeString(pair[1]));
            }
            resultMapArray[i] = paramMap;
        }

        return resultMapArray;
    }

    protected static Map<String, Object> map(Object... args) {
        Map<String, Object> map = new ConcurrentHashMap<>();

        if (args.length < 1) {
            return map;
        }

        if (args.length % 2 != 0) {
            return map;
        }

        for (int a = 0; a < args.length; a += 2) {
            if (args[a] != null && args[a + 1] != null) {
                map.put(args[a].toString(), args[a + 1]);
            }
        }
        return map;
    }

    public static Context getContext() {
        return ApplicationProvider.getApplicationContext();
    }

    /**
     * Validate sdk identity params which are sdk version and name
     *
     * @param params params to validate
     */
    public static void validateSdkIdentityParams(Map<String, String> params) {
        Assert.assertEquals(SDK_VERSION, params.get("sdk_version"));
        Assert.assertEquals(SDK_NAME, params.get("sdk_name"));
    }

    public static void validateRequiredParams(Map<String, String> params) {
        validateRequiredParams(params, commonDeviceId);
    }

    public static void validateRequiredParams(Map<String, String> params, String deviceId) {
        int hour = Integer.parseInt(params.get("hour"));
        int dow = Integer.parseInt(params.get("dow"));
        int tz = Integer.parseInt(params.get("tz"));

        validateSdkIdentityParams(params);
        Assert.assertEquals(deviceId, params.get("device_id"));
        Assert.assertEquals(commonAppKey, params.get("app_key"));
        Assert.assertEquals(Countly.DEFAULT_APP_VERSION, params.get("av"));
        Assert.assertTrue(Long.parseLong(params.get("timestamp")) > 0);
        Assert.assertTrue(hour >= 0 && hour < 24);
        Assert.assertTrue(dow >= 0 && dow < 7);
        Assert.assertTrue(tz >= -720 && tz <= 840);
    }
}
