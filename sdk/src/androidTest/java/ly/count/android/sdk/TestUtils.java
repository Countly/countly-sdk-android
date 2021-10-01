package ly.count.android.sdk;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class TestUtils {
    //Useful call:
    //mockingDetails(mockObj).printInvocations()
    //

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
}
