package ly.count.android.sdk;

import android.app.Application;
import androidx.test.core.app.ApplicationProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.runner.Request;
import org.mockito.ArgumentCaptor;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class TestUtils {
    //Useful call:
    //mockingDetails(mockObj).printInvocations()
    //
    //

    public final static String commonURL = "http://test.count.ly";
    public final static String commonAppKey = "appkey";
    public final static String commonDeviceId = "1234";

    public static CountlyConfig createConsentCountlyConfig(boolean requiresConsent, String[] givenConsent, ModuleBase testModuleListener, RequestQueueProvider rqp) {
        CountlyConfig cc = (new CountlyConfig((Application) ApplicationProvider.getApplicationContext(), commonAppKey, commonURL))
            .setDeviceId(commonDeviceId)
            .setLoggingEnabled(true)
            .enableCrashReporting()
            .setRequiresConsent(requiresConsent)
            .setConsentEnabled(givenConsent);
        cc.testModuleListener = testModuleListener;
        cc.requestQueueProvider = rqp;

        return cc;
    }

    public static CountlyConfig createConsentCountlyConfig(boolean requiresConsent, String[] givenConsent, ModuleBase testModuleListener) {

        return createConsentCountlyConfig(requiresConsent, givenConsent, testModuleListener, null);
    }

    public static CountlyConfig createAttributionCountlyConfig(boolean requiresConsent, String[] givenConsent, ModuleBase testModuleListener, RequestQueueProvider rqp, String daType,String daValue, Map<String, String> iaValues) {
        CountlyConfig cc = (new CountlyConfig((Application) ApplicationProvider.getApplicationContext(), commonAppKey, commonURL))
            .setDeviceId(commonDeviceId)
            .setLoggingEnabled(true)
            .enableCrashReporting()
            .setDirectAttribution(daType, daValue)
            .setIndirectAttribution(iaValues)
            .setRequiresConsent(requiresConsent)
            .setConsentEnabled(givenConsent);
        cc.testModuleListener = testModuleListener;
        cc.requestQueueProvider = rqp;
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

    public static Map<String, Object> combineSegmentation(Event event) {
        return combineSegmentation(event.segmentation, event.segmentationInt, event.segmentationDouble, event.segmentationBoolean);
    }

    public static Map<String, Object> combineSegmentation(Map<String, String> sString, Map<String, Integer> sInteger, Map<String, Double> sDouble, Map<String, Boolean> sBoolean) {
        Map<String, Object> res = new HashMap<>();

        if (sString != null) {
            for (Map.Entry<String, String> pair : sString.entrySet()) {
                res.put(pair.getKey(), pair.getValue());
            }
        }

        if (sInteger != null) {
            for (Map.Entry<String, Integer> pair : sInteger.entrySet()) {
                res.put(pair.getKey(), pair.getValue());
            }
        }

        if (sDouble != null) {
            for (Map.Entry<String, Double> pair : sDouble.entrySet()) {
                res.put(pair.getKey(), pair.getValue());
            }
        }

        if (sBoolean != null) {
            for (Map.Entry<String, Boolean> pair : sBoolean.entrySet()) {
                res.put(pair.getKey(), pair.getValue());
            }
        }

        return res;
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

        for(String entry:params) {
            String[] pair = entry.split("=");
            if(pair[0].equals(param)) {
                return pair[1];
            }
        }

        return null;
    }

    public static void validateThatRQContainsCorrectEntry(CountlyStore store, String param, String targetValue, int entryCount) {
        List<String> filteredVals = TestUtils.getRequestsWithParam(store.getRequests(), param);
        Assert.assertEquals(entryCount, filteredVals.size());

        if(entryCount != 0) {
            String paramValue = TestUtils.getParamValueFromRequest(filteredVals.get(0), param);
            Assert.assertEquals(targetValue, paramValue);
        }
    }

    public static String[] subtractConsentFromArray(String[] input, String[] subtraction) {
        ArrayList<String> res = new ArrayList<>();

        for(String v:input) {
            boolean contains = false;
            for(String sv:subtraction) {
                if(sv.equals(v)) {
                    contains = true;
                    break;
                }
            }

            if(!contains) {
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
}
