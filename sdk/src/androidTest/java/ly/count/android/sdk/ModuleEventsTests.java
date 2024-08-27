package ly.count.android.sdk;

import android.app.Activity;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ly.count.android.sdk.messaging.ModulePush;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.internal.util.collections.Sets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class ModuleEventsTests {
    Countly mCountly;
    CountlyConfig config;
    String eventKey = "asdf";
    EventQueueProvider eventQueueProvider;

    @Before
    public void setUp() {
        final CountlyStore countlyStore = new CountlyStore(TestUtils.getContext(), mock(ModuleLog.class));
        countlyStore.clear();

        eventQueueProvider = mock(EventQueueProvider.class);

        mCountly = new Countly();
        config = new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        config.eventQueueProvider = eventQueueProvider;
        mCountly.init(config);
    }

    @After
    public void tearDown() {
    }

    @Test
    public void recordEvent_1() {
        EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));
        mCountly.events().recordEvent(eventKey);
        verify(ep).recordEventInternal(eventKey, null, 1, 0.0, 0.0, null, null);
    }

    @Test
    public void recordEvent_2() {
        EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));
        mCountly.events().recordEvent(eventKey, 657);
        verify(ep).recordEventInternal(eventKey, null, 657, 0.0, 0.0, null, null);
    }

    @Test
    public void recordEvent_3() {
        EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));
        mCountly.events().recordEvent(eventKey, 657, 884.213d);
        verify(ep).recordEventInternal(eventKey, null, 657, 884.213d, 0.0, null, null);
    }

    @Test
    public void recordEvent_4() {
        eventWithSegmentation(null, null, null, null);
    }

    @Test
    public void recordEvent_5() {
        eventWithSegmentation(3456, null, null, null);
    }

    @Test
    public void recordEvent_6() {
        eventWithSegmentation(7583, 39457.123d, null, null);
    }

    @Test
    public void recordEvent_7() {
        eventWithSegmentation(1245, 443.76d, 985.33d, null);
    }

    void eventWithSegmentation(Integer count, Double sum, Double dur, Long timestamp) {
        EventQueueProvider eqp = TestUtils.setEventQueueProviderToMock(mCountly, mock(EventQueueProvider.class));

        Map<String, Object> segmUsed = new HashMap<>();
        segmUsed.put("aa", "dd");
        segmUsed.put("aa1", "dda");
        segmUsed.put("1", 1234);
        segmUsed.put("2", 1234.55d);
        segmUsed.put("3", true);
        segmUsed.put("4", 45.4f);
        segmUsed.put("41", new Object());

        // event key is always provided here
        if (timestamp == null) {
            if (count == null && sum == null && dur == null) { // everything is null
                mCountly.events().recordEvent(eventKey, segmUsed);
            } else if (count != null && sum == null && dur == null) { // only count provided
                mCountly.events().recordEvent(eventKey, segmUsed, count);
            } else if (count != null && sum != null && dur == null) { // count and sum provided
                mCountly.events().recordEvent(eventKey, segmUsed, count, sum);
            } else if (count != null && sum != null && dur != null) { // count and sum and duration provided
                mCountly.events().recordEvent(eventKey, segmUsed, count, sum, dur);
            } else {
                Assert.fail("You should not get here"); // says the wise one
            }
        } else {
            if (count == null && sum == null && dur == null) { // only timestamp provided
                mCountly.events().recordPastEvent(eventKey, segmUsed, timestamp);
            } else if (count != null && sum != null && dur != null) { // count and sum and duration and timestamp provided
                mCountly.events().recordPastEvent(eventKey, segmUsed, count, sum, dur, timestamp);
            } else {
                Assert.fail("You should not get here"); // again says the wise one
            }
        }

        final Map<String, Object> segm = new HashMap<>();

        segm.put("aa", "dd");
        segm.put("aa1", "dda");
        segm.put("1", 1234);
        segm.put("2", 1234.55d);
        segm.put("3", true);
        segm.put("4", 45.4f);

        UtilsTime.Instant instant = UtilsTime.getCurrentInstant();

        ArgumentCaptor<Integer> arg1 = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Double> arg2 = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> arg3 = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Long> arg4 = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Integer> arg5 = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> arg6 = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> arg7 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> arg8 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> arg9 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> arg10 = ArgumentCaptor.forClass(String.class);

        verify(eqp).recordEventToEventQueue(eq(eventKey), eq(segm), arg1.capture(), arg2.capture(), arg3.capture(), arg4.capture(), arg5.capture(), arg6.capture(), arg7.capture(), arg8.capture(), arg9.capture(), arg10.capture());

        if (count != null) {
            Assert.assertEquals(count, arg1.getValue());
        }

        if (sum != null) {
            Assert.assertEquals(sum, arg2.getValue());
        }

        if (dur != null) {
            Assert.assertEquals(dur, arg3.getValue());
        }

        if (timestamp == null) {
            Assert.assertNotNull(arg4.getValue());
            Assert.assertNotNull(arg5.getValue());
            Assert.assertNotNull(arg6.getValue());
        } else {
            UtilsTime.Instant instantTimestamp = UtilsTime.Instant.get(timestamp);
            Assert.assertEquals(instantTimestamp.timestampMs, (long) arg4.getValue());
            Assert.assertEquals(instantTimestamp.hour, (int) arg5.getValue());
            Assert.assertEquals(instantTimestamp.dow, (int) arg6.getValue());
            Assert.assertEquals(timestamp.longValue(), (long) arg4.getValue());
        }

        // TODO: Arg 7,8,9,10 check somehow
    }

    @Test
    public void recordPastEvent_1() {
        eventWithSegmentation(null, null, null, 1579463653876L);
    }

    @Test
    public void recordPastEvent_2() {
        eventWithSegmentation(76355, 576334.33d, 85664.64d, 1579463653876L);
    }

    @Test
    public void startEndEvent_noSegments() throws InterruptedException {
        boolean res = mCountly.events().startEvent(eventKey);
        Assert.assertTrue(res);
        verify(eventQueueProvider, times(0)).recordEventToEventQueue(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), any(Long.class), any(Integer.class), any(Integer.class), any(String.class), any(String.class), any(String.class), any(String.class));

        Assert.assertEquals(1, ModuleEvents.timedEvents.size());
        Assert.assertTrue(ModuleEvents.timedEvents.containsKey(eventKey));
        Event startEvent = ModuleEvents.timedEvents.get(eventKey);

        Thread.sleep(1000);

        res = mCountly.events().endEvent(eventKey);
        Assert.assertTrue(res);
        Assert.assertEquals(0, ModuleEvents.timedEvents.size());

        ArgumentCaptor<Long> arg1 = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Integer> arg2 = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> arg3 = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Double> argD = ArgumentCaptor.forClass(Double.class);

        ArgumentCaptor<Map> argS = ArgumentCaptor.forClass(Map.class);
        verify(eventQueueProvider).recordEventToEventQueue(eq(eventKey), argS.capture(), eq(1), eq(0.0d), argD.capture(), arg1.capture(), arg2.capture(), arg3.capture(), any(String.class), isNull(String.class), eq(""), eq(""));

        Assert.assertEquals(startEvent.timestamp, (long) arg1.getValue());
        Assert.assertEquals(startEvent.hour, (int) arg2.getValue());
        Assert.assertEquals(startEvent.dow, (int) arg3.getValue());
        Assert.assertTrue(argS.getValue().isEmpty());

        Double captD = argD.getValue();
        Assert.assertEquals(1, captD, 0.1d);
    }

    @Test
    public void startEndEvent_withSegments() throws InterruptedException {
        EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));
        boolean res = mCountly.events().startEvent(eventKey);
        Assert.assertTrue(res);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class), any(String.class));

        Assert.assertEquals(1, ModuleEvents.timedEvents.size());
        Assert.assertTrue(ModuleEvents.timedEvents.containsKey(eventKey));
        Event startEvent = ModuleEvents.timedEvents.get(eventKey);

        Thread.sleep(2000);

        Map<String, Object> segm = new HashMap<>();
        segm.put("aa", "dd");
        segm.put("aa1", "dda");
        segm.put("1", 1234);
        segm.put("2", 1234.55d);
        segm.put("3", true);

        res = mCountly.events().endEvent(eventKey, segm, 6372, 5856.34d);
        Assert.assertTrue(res);
        Assert.assertEquals(0, ModuleEvents.timedEvents.size());

        final Map<String, Object> segmVals = new HashMap<>();
        segmVals.put("aa", "dd");
        segmVals.put("aa1", "dda");
        segmVals.put("1", 1234);
        segmVals.put("2", 1234.55d);
        segmVals.put("3", true);

        ArgumentCaptor<UtilsTime.Instant> arg = ArgumentCaptor.forClass(UtilsTime.Instant.class);
        ArgumentCaptor<Double> argD = ArgumentCaptor.forClass(Double.class);
        //  TODO: should final param really be null?
        verify(ep).recordEventInternal(eq(eventKey), eq(segmVals), eq(6372), eq(5856.34d), argD.capture(), arg.capture(), isNull(String.class));

        UtilsTime.Instant captV = arg.getValue();
        Assert.assertEquals(startEvent.hour, captV.hour);
        Assert.assertEquals(startEvent.dow, captV.dow);
        Assert.assertEquals(startEvent.timestamp, captV.timestampMs);

        Double captD = argD.getValue();
        Assert.assertEquals(2, captD, 0.1d);
    }

    @Test
    public void startCancelEndEvent() {
        EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));
        boolean res = mCountly.events().startEvent(eventKey);
        Assert.assertTrue(res);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class), any(String.class));

        Assert.assertEquals(1, ModuleEvents.timedEvents.size());
        Assert.assertTrue(ModuleEvents.timedEvents.containsKey(eventKey));

        res = mCountly.events().cancelEvent(eventKey);
        Assert.assertTrue(res);
        Assert.assertEquals(0, ModuleEvents.timedEvents.size());
        // TODO: Check these 2 null event IDs
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class), isNull(String.class));

        res = mCountly.events().endEvent(eventKey);
        Assert.assertFalse(res);
        Assert.assertEquals(0, ModuleEvents.timedEvents.size());
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class), isNull(String.class));
    }

    @Test
    public void startCancelStartEndEvent() throws InterruptedException {
        EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));
        boolean res = mCountly.events().startEvent(eventKey);
        Assert.assertTrue(res);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class), any(String.class));

        Assert.assertEquals(1, ModuleEvents.timedEvents.size());
        Assert.assertTrue(ModuleEvents.timedEvents.containsKey(eventKey));

        res = mCountly.events().cancelEvent(eventKey);
        Assert.assertTrue(res);
        Assert.assertEquals(0, ModuleEvents.timedEvents.size());
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class), isNull(String.class));

        // finished first start and cancel

        res = mCountly.events().startEvent(eventKey);
        Assert.assertTrue(res);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class), isNull(String.class));

        Assert.assertEquals(1, ModuleEvents.timedEvents.size());
        Assert.assertTrue(ModuleEvents.timedEvents.containsKey(eventKey));
        Event startEvent = ModuleEvents.timedEvents.get(eventKey);

        Thread.sleep(1000);

        res = mCountly.events().endEvent(eventKey);
        Assert.assertTrue(res);
        Assert.assertEquals(0, ModuleEvents.timedEvents.size());

        ArgumentCaptor<UtilsTime.Instant> arg = ArgumentCaptor.forClass(UtilsTime.Instant.class);
        ArgumentCaptor<Double> argD = ArgumentCaptor.forClass(Double.class);
        verify(ep).recordEventInternal(eq(eventKey), isNull(Map.class), eq(1), eq(0.0d), argD.capture(), arg.capture(), isNull(String.class));

        UtilsTime.Instant captV = arg.getValue();
        Assert.assertEquals(startEvent.hour, captV.hour);
        Assert.assertEquals(startEvent.dow, captV.dow);
        Assert.assertEquals(startEvent.timestamp, captV.timestampMs);

        Double captD = argD.getValue();
        Assert.assertEquals(1, captD, 0.1d);
    }

    @Test
    public void recordEventInternalProcessedTest() {
        EventQueueProvider eqp = TestUtils.setEventQueueProviderToMock(mCountly, mock(EventQueueProvider.class));
        Map<String, Object> segm1 = new HashMap<>();

        final Map<String, Object> segm2 = new HashMap<>();

        segm1.put("4", 45.4f);
        segm1.put("41", new Object());
        segm1.put("42", new int[][] { { 1, 2, 3 }, { 4, 5, 6 } });
        segm1.put("asd", "123");
        segm1.put("1", 1234);
        segm1.put("2", 1234.55d);
        segm1.put("3", true);

        segm2.put("asd", "123");
        segm2.put("1", 1234);
        segm2.put("2", 1234.55d);
        segm2.put("3", true);
        segm2.put("4", 45.4f);

        Map<String, Object> segm3 = new HashMap<>(segm1);
        mCountly.config_.eventProvider.recordEventInternal(eventKey, segm3, 123, 321.22d, 342.32d, null, null);

        verify(eqp).recordEventToEventQueue(eq(eventKey), eq(segm2), eq(123), eq(321.22d), eq(342.32d), any(Long.class), any(Integer.class), any(Integer.class), any(String.class), isNull(String.class), eq(""), eq(""));
        eqp = TestUtils.setEventQueueProviderToMock(mCountly, mock(EventQueueProvider.class));

        segm3.clear();
        segm3.putAll(segm1);

        mCountly.config_.eventProvider.recordEventInternal(eventKey, segm3, 123, 321.22d, 342.32d, null, null);
        verify(eqp).recordEventToEventQueue(eq(eventKey), eq(segm3), eq(123), eq(321.22d), eq(342.32d), any(Long.class), any(Integer.class), any(Integer.class), any(String.class), isNull(String.class), eq(""), any(String.class));
    }

    /**
     * Validating which event keys are triggering force sending of all queued events
     */
    @Test
    public void eventsForceClearingEQIntoRQ() {
        Countly countly = new Countly().init(new CountlyConfig(TestUtils.getContext(), "appkey", "http://test.count.ly").setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting());

        Assert.assertEquals(0, countly.countlyStore.getEventQueueSize());
        Assert.assertEquals(0, countly.countlyStore.getRequests().length);

        countly.events().recordEvent("rnd_key");
        Assert.assertEquals(1, countly.countlyStore.getEventQueueSize());
        Assert.assertEquals(0, countly.countlyStore.getRequests().length);

        countly.events().recordEvent(ModuleEvents.ACTION_EVENT_KEY);
        Assert.assertEquals(2, countly.countlyStore.getEventQueueSize());
        Assert.assertEquals(0, countly.countlyStore.getRequests().length);

        countly.events().recordEvent(ModuleFeedback.NPS_EVENT_KEY);
        Assert.assertEquals(0, countly.countlyStore.getEventQueueSize());
        Assert.assertEquals(1, countly.countlyStore.getRequests().length);

        countly.events().recordEvent(ModuleFeedback.SURVEY_EVENT_KEY);
        Assert.assertEquals(0, countly.countlyStore.getEventQueueSize());
        Assert.assertEquals(2, countly.countlyStore.getRequests().length);

        countly.events().recordEvent(ModuleFeedback.RATING_EVENT_KEY);
        Assert.assertEquals(1, countly.countlyStore.getEventQueueSize());
        Assert.assertEquals(2, countly.countlyStore.getRequests().length);

        countly.events().recordEvent(ModuleViews.VIEW_EVENT_KEY);
        Assert.assertEquals(2, countly.countlyStore.getEventQueueSize());
        Assert.assertEquals(2, countly.countlyStore.getRequests().length);

        countly.events().recordEvent(ModuleViews.ORIENTATION_EVENT_KEY);
        Assert.assertEquals(3, countly.countlyStore.getEventQueueSize());
        Assert.assertEquals(2, countly.countlyStore.getRequests().length);

        countly.events().recordEvent(ModulePush.PUSH_EVENT_ACTION);
        Assert.assertEquals(0, countly.countlyStore.getEventQueueSize());
        Assert.assertEquals(3, countly.countlyStore.getRequests().length);
    }

    /**
     * Try to record events with internal keys
     * validate they are not truncated
     * try one normal event with a key that is longer than the max allowed length
     * except normal event, all other internal events should not be truncated
     */
    @Test
    public void internalLimits_recordEvent_internalKeys() throws JSONException {
        CountlyConfig config = TestUtils.createBaseConfig();
        config.sdkInternalLimits.setMaxKeyLength(2);
        config.setEventQueueSizeToSend(1);

        Countly countly = new Countly().init(config);

        countly.events().recordEvent(ModuleEvents.ACTION_EVENT_KEY, TestUtils.map("action_event", "ACTION", "no_truncate", 7687)); //force sending
        validateEventInRQ(ModuleEvents.ACTION_EVENT_KEY, TestUtils.map("action_event", "ACTION", "no_truncate", 7687), 0);

        countly.events().recordEvent(ModuleFeedback.NPS_EVENT_KEY, TestUtils.map("nps_event", "NPS", "no_truncate", 555)); //force sending
        validateEventInRQ(ModuleFeedback.NPS_EVENT_KEY, TestUtils.map("nps_event", "NPS", "no_truncate", 555), 1);

        countly.events().recordEvent(ModuleFeedback.SURVEY_EVENT_KEY, TestUtils.map("survey_event", "SURVEY", "no_truncate", 567)); //force sending
        validateEventInRQ(ModuleFeedback.SURVEY_EVENT_KEY, TestUtils.map("survey_event", "SURVEY", "no_truncate", 567), 2);

        countly.events().recordEvent(ModuleFeedback.RATING_EVENT_KEY, TestUtils.map("rating_event", "RATING", "no_truncate", 7475));
        validateEventInRQ(ModuleFeedback.RATING_EVENT_KEY, TestUtils.map("rating_event", "RATING", "no_truncate", 7475), 3);

        countly.events().recordEvent(ModuleViews.VIEW_EVENT_KEY, TestUtils.map("view_event", "VIEW", "no_truncate", 124));
        validateEventInRQ(ModuleViews.VIEW_EVENT_KEY, TestUtils.map("view_event", "VIEW", "no_truncate", 124), 4);

        countly.events().recordEvent(ModuleViews.ORIENTATION_EVENT_KEY, TestUtils.map("orientation_event", "ORIENTATION", "no_truncate", 23_523));
        validateEventInRQ(ModuleViews.ORIENTATION_EVENT_KEY, TestUtils.map("orientation_event", "ORIENTATION", "no_truncate", 23_523), 5);

        countly.events().recordEvent(ModulePush.PUSH_EVENT_ACTION, TestUtils.map("push_event", "PUSH", "no_truncate", 567));
        validateEventInRQ(ModulePush.PUSH_EVENT_ACTION, TestUtils.map("push_event", "PUSH", "no_truncate", 567), 6);

        countly.events().recordEvent("ModuleEvents", TestUtils.map("normal_event", "boo", "no_truncate", 567));
        validateEventInRQ("Mo", TestUtils.map("no", "boo"), 7);
    }

    /**
     * record event with segmentation
     * validate that the segmentation is truncated and two same start keys is merged to one
     */
    @Test
    public void internalLimits_recordEvent_segmentation() throws JSONException {
        CountlyConfig config = TestUtils.createBaseConfig();
        config.sdkInternalLimits.setMaxKeyLength(2);
        config.setEventQueueSizeToSend(1);
        Countly countly = new Countly().init(config);
        Map<String, Object> segmentation = new ConcurrentHashMap<>();
        segmentation.put("ModuleEvents", "ModuleEvents");
        segmentation.put("ModuleFeedback", 567);

        countly.events().recordEvent("TestMe", segmentation); //force sending

        segmentation.clear();
        segmentation.put("Mo", 567);
        validateEventInRQ("Te", segmentation, 0);
    }

    @Test
    public void recordEvent_validateFromRQ() throws JSONException {
        int[] arr = new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
        List<String> list = Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
        CountlyConfig countlyConfig = TestUtils.createBaseConfig();
        countlyConfig.setEventQueueSizeToSend(1);
        Countly countly = new Countly().init(countlyConfig);

        Map<String, Object> segmentation = TestUtils.map(
            "int", 1,
            "double", 1.2d,
            "string", "string",
            "boolean", true,
            "float", 1.5f,
            "long", Long.MAX_VALUE,
            "object", new Object(),
            "array", arr,
            "list", list,
            "null", null
        );

        countly.events().recordEvent("key", segmentation, 1, 1.0d, 1.0d);

        Map<String, Object> expectedSegmentation = TestUtils.map(
            "int", 1,
            "double", 1.2,
            "string", "string",
            "boolean", true,
            "float", 1.5,
            "long", Long.MAX_VALUE,
            "array", new JSONArray(arr),
            "list", new JSONArray(list)
        );

        validateEventInRQ("key", expectedSegmentation, 1, 1.0d, 1.0d, 0);
    }

    /**
     * Validate that only normal events' segmentation values are clipped to the maximum allowed values
     * EQ size is 1 to trigger request generation
     */
    @Test
    public void internalLimits_recordEventInternal_maxSegmentationValues() throws JSONException {
        CountlyConfig config = TestUtils.createBaseConfig();
        config.sdkInternalLimits.setMaxSegmentationValues(2);
        config.setEventQueueSizeToSend(1);
        Countly countly = new Countly().init(config);

        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);
        Map<String, Object> threeSegmentation = TestUtils.map("a", 1, "b", 2, "c", 3);

        countly.events().recordEvent("rnd_key", TestUtils.map("a", 1, "b", 2, "c", 3, "d", 4, "e", 5, "f", 6, "g", 7), 1, 1.1d, 1.1d);
        validateEventInRQ("rnd_key", TestUtils.map("f", 6, "g", 7), 1, 1.1d, 1.1d, 0);

        countly.events().recordEvent(ModuleEvents.ACTION_EVENT_KEY, threeSegmentation);
        validateEventInRQ(ModuleEvents.ACTION_EVENT_KEY, threeSegmentation, 1, 0.0d, 0.0d, 1);

        countly.events().recordEvent(ModuleFeedback.NPS_EVENT_KEY, threeSegmentation);
        validateEventInRQ(ModuleFeedback.NPS_EVENT_KEY, threeSegmentation, 1, 0.0d, 0.0d, 2);

        countly.events().recordEvent(ModuleFeedback.SURVEY_EVENT_KEY, threeSegmentation);
        validateEventInRQ(ModuleFeedback.SURVEY_EVENT_KEY, threeSegmentation, 1, 0.0d, 0.0d, 3);

        countly.events().recordEvent(ModuleFeedback.RATING_EVENT_KEY, threeSegmentation);
        validateEventInRQ(ModuleFeedback.RATING_EVENT_KEY, threeSegmentation, 1, 0.0d, 0.0d, 4);

        countly.events().recordEvent(ModuleViews.VIEW_EVENT_KEY, threeSegmentation);
        validateEventInRQ(ModuleViews.VIEW_EVENT_KEY, threeSegmentation, 1, 0.0d, 0.0d, 5);

        countly.events().recordEvent(ModuleViews.ORIENTATION_EVENT_KEY, threeSegmentation);
        validateEventInRQ(ModuleViews.ORIENTATION_EVENT_KEY, threeSegmentation, 1, 0.0d, 0.0d, 6);

        countly.events().recordEvent(ModulePush.PUSH_EVENT_ACTION, threeSegmentation);
        validateEventInRQ(ModulePush.PUSH_EVENT_ACTION, threeSegmentation, 1, 0.0d, 0.0d, 7);
    }

    /**
     * "recordEvent" max value size limit
     * Validate that all "String" values are clipped to the maximum allowed length
     * EQ size is 1 to trigger request generation
     */
    @Test
    public void internalLimits_recordEventInternal_maxValueSize() throws JSONException {
        CountlyConfig config = TestUtils.createBaseConfig();
        config.sdkInternalLimits.setMaxValueSize(2);
        config.setEventQueueSizeToSend(1);
        Countly countly = new Countly().init(config);

        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);

        countly.events().recordEvent("rnd_key", TestUtils.map("a", 1, "b", "bbb", "c", "ccc"), 1, 1.1d, 1.1d);
        validateEventInRQ("rnd_key", TestUtils.map("a", 1, "b", "bb", "c", "cc"), 1, 1.1d, 1.1d, 0);
    }

    /**
     * "recordEvent" max value size limit and key length
     * Validate that clipped values clashes with same keys and overridden each other
     * "bb" key should have value from the second of the last value which is "dd"
     */
    @Test
    public void internalLimits_recordEventInternal_maxValueSizeKeyLength() throws JSONException {
        CountlyConfig config = TestUtils.createBaseConfig();
        config.sdkInternalLimits.setMaxValueSize(2).setMaxKeyLength(2);
        config.setEventQueueSizeToSend(1);
        Countly countly = new Countly().init(config);

        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);

        countly.events().recordEvent("rnd_key", TestUtils.map("a", 1, "bbb", "bbb", "bbc", "ccc", "bbd", "ddd", "bbe", "eee"), 1, 1.1d, 1.1d);
        validateEventInRQ("rn", TestUtils.map("a", 1, "bb", "dd"), 1, 1.1d, 1.1d, 0);
    }

    /**
     * "recordEvent" with Array segmentations
     * Validate that all primitive types arrays are successfully recorded
     * And validate that Object arrays are not recorded
     * But Generic type of Object array which its values are only primitive types are recorded
     *
     * @throws JSONException if the JSON is not valid
     */
    @Test
    public void recordEvent_validateSupportedArrays() throws JSONException {
        int[] arr = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
        boolean[] arrB = { true, false, true, false, true, false, true, false, true, false };
        String[] arrS = { "1", "2", "3", "4", "5", "6", "7", "8", "9", "10" };
        long[] arrL = { Long.MAX_VALUE, Long.MIN_VALUE };
        double[] arrD = { Double.MAX_VALUE, Double.MIN_VALUE };
        Long[] arrLO = { Long.MAX_VALUE, Long.MIN_VALUE };
        Double[] arrDO = { Double.MAX_VALUE, Double.MIN_VALUE };
        Boolean[] arrBO = { Boolean.TRUE, Boolean.FALSE };
        Integer[] arrIO = { Integer.MAX_VALUE, Integer.MIN_VALUE };
        Object[] arrObj = { "1", 1, 1.1d, true, 1.1f, Long.MAX_VALUE };
        Object[] arrObjStr = { "1", "1", "1.1d", "true", "1.1f", "Long.MAX_VALUE" };

        CountlyConfig countlyConfig = TestUtils.createBaseConfig();
        countlyConfig.setEventQueueSizeToSend(1);
        Countly countly = new Countly().init(countlyConfig);

        Map<String, Object> segmentation = TestUtils.map(
            "arr", arr,
            "arrB", arrB,
            "arrS", arrS,
            "arrL", arrL,
            "arrD", arrD,
            "arrLO", arrLO,
            "arrDO", arrDO,
            "arrBO", arrBO,
            "arrIO", arrIO,
            "arrObj", arrObj,
            "arrObjStr", arrObjStr
        );

        countly.events().recordEvent("key", segmentation, 1, 1.0d, 1.0d);

        Map<String, Object> expectedSegmentation = TestUtils.map(
            "arr", new JSONArray(arr),
            "arrB", new JSONArray(arrB),
            "arrS", new JSONArray(arrS),
            "arrL", new JSONArray(arrL),
            "arrD", new JSONArray(arrD),
            "arrLO", new JSONArray(arrLO),
            "arrDO", new JSONArray(arrDO),
            "arrBO", new JSONArray(arrBO),
            "arrIO", new JSONArray(arrIO)
        );

        validateEventInRQ("key", expectedSegmentation, 1, 1.0d, 1.0d, 0);
    }

    /**
     * "recordEvent" with List segmentations
     * Validate that all primitive types Lists are successfully recorded
     * And validate that List of Objects is not recorded
     * But Generic type of Object list which its values are only primitive types are recorded
     *
     * @throws JSONException if the JSON is not valid
     */
    @Test
    public void recordEvent_validateSupportedLists() throws JSONException {
        List<Integer> arr = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        List<Boolean> arrB = Arrays.asList(true, false, true, false, true, false, true, false, true, false);
        List<String> arrS = Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
        List<Long> arrLO = Arrays.asList(Long.MAX_VALUE, Long.MIN_VALUE);
        List<Double> arrDO = Arrays.asList(Double.MAX_VALUE, Double.MIN_VALUE);
        List<Boolean> arrBO = Arrays.asList(Boolean.TRUE, Boolean.FALSE);
        List<Integer> arrIO = Arrays.asList(Integer.MAX_VALUE, Integer.MIN_VALUE);
        List<Object> arrObj = Arrays.asList("1", 1, 1.1d, true, Long.MAX_VALUE);
        List<Object> arrObjStr = Arrays.asList("1", "1", "1.1d", "true", "Long.MAX_VALUE");

        CountlyConfig countlyConfig = TestUtils.createBaseConfig();
        countlyConfig.setEventQueueSizeToSend(1);
        Countly countly = new Countly().init(countlyConfig);

        // Create segmentation using maps with lists
        Map<String, Object> segmentation = TestUtils.map(
            "arr", arr,
            "arrB", arrB,
            "arrS", arrS,
            "arrLO", arrLO,
            "arrDO", arrDO,
            "arrBO", arrBO,
            "arrIO", arrIO,
            "arrObj", arrObj,
            "arrObjStr", arrObjStr
        );

        // Record event with the created segmentation
        countly.events().recordEvent("key", segmentation, 1, 1.0d, 1.0d);

        // Prepare expected segmentation with JSONArrays
        Map<String, Object> expectedSegmentation = TestUtils.map(
            "arr", new JSONArray(arr),
            "arrB", new JSONArray(arrB),
            "arrS", new JSONArray(arrS),
            "arrLO", new JSONArray(arrLO),
            "arrDO", new JSONArray(arrDO),
            "arrBO", new JSONArray(arrBO),
            "arrIO", new JSONArray(arrIO),
            "arrObjStr", new JSONArray(arrObjStr),
            "arrObj", new JSONArray(arrObj)
        );

        // Validate the recorded event with expected segmentation
        validateEventInRQ("key", expectedSegmentation, 1, 1.0d, 1.0d, 0);
    }

    /**
     * "recordEvent" with JSONArray segmentations
     * Validate that all primitive types JSONArrays are successfully recorded
     * And validate and JSONArray of Objects is not recorded
     *
     * @throws JSONException if the JSON is not valid
     */
    @Test
    public void recordEvent_validateSupportedJSONArrays() throws JSONException {
        JSONArray arr = new JSONArray(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        JSONArray arrB = new JSONArray(Arrays.asList(true, false, true, false, true, false, true, false, true, false));
        JSONArray arrS = new JSONArray(Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"));
        JSONArray arrL = new JSONArray(Arrays.asList(Long.MAX_VALUE, Long.MIN_VALUE));
        JSONArray arrD = new JSONArray(Arrays.asList(Double.MAX_VALUE, Double.MIN_VALUE));
        JSONArray arrBO = new JSONArray(Arrays.asList(Boolean.TRUE, Boolean.FALSE));
        JSONArray arrIO = new JSONArray(Arrays.asList(Integer.MAX_VALUE, Integer.MIN_VALUE));
        JSONArray arrObj = new JSONArray(Arrays.asList("1", 1, 1.1d, true, Long.MAX_VALUE));

        CountlyConfig countlyConfig = TestUtils.createBaseConfig();
        countlyConfig.setEventQueueSizeToSend(1);
        Countly countly = new Countly().init(countlyConfig);

        // Create segmentation using maps with lists
        Map<String, Object> segmentation = TestUtils.map(
            "arr", arr,
            "arrB", arrB,
            "arrS", arrS,
            "arrL", arrL,
            "arrD", arrD,
            "arrBO", arrBO,
            "arrIO", arrIO,
            "arrObj", arrObj
        );

        // Record event with the created segmentation
        countly.events().recordEvent("key", segmentation, 1, 1.0d, 1.0d);

        // Prepare expected segmentation with JSONArrays
        Map<String, Object> expectedSegmentation = TestUtils.map(
            "arr", arr,
            "arrB", arrB,
            "arrS", arrS,
            "arrL", arrL,
            "arrD", arrD,
            "arrBO", arrBO,
            "arrIO", arrIO,
            "arrObj", arrObj
        );

        // Validate the recorded event with expected segmentation
        validateEventInRQ("key", expectedSegmentation, 1, 1.0d, 1.0d, 0);
    }

    /**
     * "recordHandledException" with invalid data types
     * Validate that unsupported data types are not recorded
     *
     * @throws JSONException if the JSON is not valid
     */
    @Test
    public void recordEvent_unsupportedDataTypesSegmentation() throws JSONException {
        CountlyConfig countlyConfig = TestUtils.createBaseConfig();
        countlyConfig.setEventQueueSizeToSend(1);
        Countly countly = new Countly().init(countlyConfig);

        Map<String, Object> segmentation = TestUtils.map(
            "a", TestUtils.map(),
            "b", TestUtils.json(),
            "c", new Object(),
            "d", Sets.newSet(),
            "e", Mockito.mock(ModuleLog.class)
        );

        // Record event with the created segmentation
        countly.events().recordEvent("key", segmentation, 1, 1.0d, 1.0d);

        validateEventInRQ("key", TestUtils.map(), 1, 1.0d, 1.0d, 0);
    }

    /**
     * "recordEvent" with visibility tracking enabled
     * Validate that visibility tracking events are recorded correctly
     * and only added to "Events" and "Views"
     *
     * @throws JSONException if the JSON is not valid
     */
    @Test
    public void recordEvent_visibilityTracking_onlyAddingItToViewsAndEvents() throws JSONException {
        CountlyConfig config = TestUtils.createBaseConfig();
        config.experimental.enableVisibilityTracking();
        config.setEventQueueSizeToSend(1);
        Countly countly = new Countly().init(config);

        Assert.assertEquals(0, TestUtils.getCurrentRQ().length);

        countly.events().recordEvent("rnd_key", TestUtils.map());
        validateEventInRQ("rnd_key", TestUtils.map("cly_v", 0), 1, 0.0d, 0.0d, 0);

        countly.events().recordEvent(ModuleEvents.ACTION_EVENT_KEY, TestUtils.map());
        validateEventInRQ(ModuleEvents.ACTION_EVENT_KEY, TestUtils.map(), 1, 0.0d, 0.0d, 1);

        countly.events().recordEvent(ModuleFeedback.NPS_EVENT_KEY, TestUtils.map());
        validateEventInRQ(ModuleFeedback.NPS_EVENT_KEY, TestUtils.map(), 1, 0.0d, 0.0d, 2);

        countly.events().recordEvent(ModuleFeedback.SURVEY_EVENT_KEY, TestUtils.map());
        validateEventInRQ(ModuleFeedback.SURVEY_EVENT_KEY, TestUtils.map(), 1, 0.0d, 0.0d, 3);

        countly.events().recordEvent(ModuleFeedback.RATING_EVENT_KEY, TestUtils.map());
        validateEventInRQ(ModuleFeedback.RATING_EVENT_KEY, TestUtils.map(), 1, 0.0d, 0.0d, 4);

        countly.events().recordEvent(ModuleViews.VIEW_EVENT_KEY, TestUtils.map());
        validateEventInRQ(ModuleViews.VIEW_EVENT_KEY, TestUtils.map("cly_v", 0), 1, 0.0d, 0.0d, 5);

        countly.events().recordEvent(ModuleViews.ORIENTATION_EVENT_KEY, TestUtils.map());
        validateEventInRQ(ModuleViews.ORIENTATION_EVENT_KEY, TestUtils.map(), 1, 0.0d, 0.0d, 6);

        countly.events().recordEvent(ModulePush.PUSH_EVENT_ACTION, TestUtils.map());
        validateEventInRQ(ModulePush.PUSH_EVENT_ACTION, TestUtils.map(), 1, 0.0d, 0.0d, 7);
    }

    /**
     * "recordEvent" with visibility tracking enabled
     * Validate that visibility tracking events are recorded correctly
     * And validate that the "cly_v" value is correctly set to 1 when the app is in the foreground
     * and 0 when the app is in the background
     *
     * @throws JSONException if the JSON is not valid
     */
    @Test
    public void recordEvent_visibilityTracking_bgFgSwitch() throws JSONException {
        CountlyConfig config = TestUtils.createBaseConfig(TestUtils.getContext());
        config.experimental.enableVisibilityTracking();
        config.setEventQueueSizeToSend(1);
        Countly countly = new Countly().init(config);

        countly.onStart(Mockito.mock(Activity.class)); //foreground

        countly.events().recordEvent(ModuleViews.VIEW_EVENT_KEY, TestUtils.map());
        validateEventInRQ(ModuleViews.VIEW_EVENT_KEY, TestUtils.map("cly_v", 1), 1, 0.0d, 0.0d, 2);

        countly.events().recordEvent("fg", TestUtils.map());
        validateEventInRQ("fg", TestUtils.map("cly_v", 1), 1, 0.0d, 0.0d, 3);

        countly.onStop(); //background

        countly.events().recordEvent(ModuleViews.VIEW_EVENT_KEY, TestUtils.map());
        validateEventInRQ(ModuleViews.VIEW_EVENT_KEY, TestUtils.map("cly_v", 0), 1, 0.0d, 0.0d, 5);

        countly.events().recordEvent("bg", TestUtils.map());
        validateEventInRQ("bg", TestUtils.map("cly_v", 0), 1, 0.0d, 0.0d, 6);
    }

    /**
     * "recordEvent" with visibility tracking disabled
     * Validate that visibility tracking events are not recorded
     * And validate that the "cly_v" value is not set
     *
     * @throws JSONException if the JSON is not valid
     */
    @Test
    public void recordEvent_visibilityTracking_notEnabled() throws JSONException {
        CountlyConfig config = TestUtils.createBaseConfig(TestUtils.getContext());
        config.setEventQueueSizeToSend(1);
        Countly countly = new Countly().init(config);

        countly.onStart(Mockito.mock(Activity.class)); //foreground

        countly.events().recordEvent(ModuleViews.VIEW_EVENT_KEY, TestUtils.map());
        validateEventInRQ(ModuleViews.VIEW_EVENT_KEY, TestUtils.map(), 1, 0.0d, 0.0d, 2);

        countly.events().recordEvent("fg", TestUtils.map());
        validateEventInRQ("fg", TestUtils.map(), 1, 0.0d, 0.0d, 3);

        countly.onStop(); //background

        countly.events().recordEvent(ModuleViews.VIEW_EVENT_KEY, TestUtils.map());
        validateEventInRQ(ModuleViews.VIEW_EVENT_KEY, TestUtils.map(), 1, 0.0d, 0.0d, 5);

        countly.events().recordEvent("bg", TestUtils.map());
        validateEventInRQ("bg", TestUtils.map(), 1, 0.0d, 0.0d, 6);
    }

    protected static void validateEventInRQ(String eventName, Map<String, Object> expectedSegmentation, int count, double sum, double duration, int idx) throws JSONException {
        validateEventInRQ(eventName, expectedSegmentation, count, sum, duration, idx, idx + 1);
    }

    protected static void validateEventInRQ(String eventName, Map<String, Object> expectedSegmentation, int count, double sum, double duration, int idx, int rqCount) throws JSONException {
        validateEventInRQ(TestUtils.commonDeviceId, eventName, expectedSegmentation, count, sum, duration, "_CLY_", "_CLY_", "_CLY_", "_CLY_", idx, rqCount, 0, 1);
    }

    protected static void validateEventInRQ(String deviceId, String eventName, Map<String, Object> expectedSegmentation, int count, Double sum, Double duration, String id, String pvid, String cvid, String peid, int idx, int rqCount, int eventIdx, int eventCount) throws JSONException {
        Map<String, String>[] RQ = TestUtils.getCurrentRQ();
        if (rqCount > -1) {
            Assert.assertEquals(rqCount, RQ.length);
        }
        TestUtils.validateRequiredParams(RQ[idx], deviceId);
        if (!RQ[idx].containsKey("events")) {
            Assert.fail("Not an event request idx:[" + idx + "], request:[" + RQ[idx] + "]");
        }
        JSONArray events = new JSONArray(RQ[idx].get("events"));
        Assert.assertEquals(eventCount, events.length());
        JSONObject event = events.getJSONObject(eventIdx);
        Assert.assertEquals(eventName, event.get("key"));
        Assert.assertEquals(count, event.getInt("count"));
        Assert.assertEquals(sum, event.optDouble("sum", 0.0d), 0.0001);
        Assert.assertEquals(duration, event.optDouble("dur", 0.0d), 0.0001);
        if (expectedSegmentation != null && !expectedSegmentation.isEmpty()) {
            JSONObject segmentation = event.getJSONObject("segmentation");
            Assert.assertEquals("Expected segmentation: " + expectedSegmentation + ", got: " + segmentation, expectedSegmentation.size(), segmentation.length());
            for (Map.Entry<String, Object> entry : expectedSegmentation.entrySet()) {
                Assert.assertEquals(entry.getValue(), segmentation.get(entry.getKey()));
            }
        }

        int dow = event.getInt("dow");
        int hour = event.getInt("hour");
        long timestamp = event.getLong("timestamp");
        Assert.assertTrue(dow >= 0 && dow < 7);
        Assert.assertTrue(hour >= 0 && hour < 24);
        Assert.assertTrue(timestamp >= 0);

        validateId(id, event.optString("id", ""), "Event ID");
        validateId(pvid, event.optString("pvid", ""), "Previous View ID");
        validateId(cvid, event.optString("cvid", ""), "Current View ID");
        validateId(peid, event.optString("peid", ""), "Previous Event ID");
    }

    private static void validateId(String id, String gonnaValidate, String name) {
        if (id != null && id.equals("_CLY_")) {
            if (gonnaValidate != null && !gonnaValidate.isEmpty()) {
                validateSafeRandomVal(gonnaValidate);
            }
        } else {
            Assert.assertEquals(name + " is not validated", id, gonnaValidate);
        }
    }

    /**
     * Validates a random generated safe value,
     * Value length should be 21
     * Value should contain a timestamp at the end
     * Value should be base64 encoded and first 8 should be it
     *
     * @param val
     */
    static void validateSafeRandomVal(String val) {
        Assert.assertEquals(val, 21, val.length());

        Pattern base64Pattern = Pattern.compile("^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{4})$");

        String timestampStr = val.substring(val.length() - 13);
        String base64Str = val.substring(0, val.length() - 13);

        Matcher matcher = base64Pattern.matcher(base64Str);
        if (matcher.matches()) {
            UtilsTime.Instant instant = UtilsTime.Instant.get(Long.parseLong(timestampStr));
            Assert.assertTrue(instant.dow >= 0 && instant.dow < 7);
            Assert.assertTrue(instant.hour >= 0 && instant.hour < 24);
            Assert.assertTrue(instant.timestampMs > 0);
        } else {
            Assert.fail("No match for " + val);
        }
    }

    protected static void validateEventInRQ(String eventName, Map<String, Object> expectedSegmentation, int idx) throws JSONException {
        validateEventInRQ(eventName, expectedSegmentation, 1, 0.0d, 0.0d, idx);
    }

    protected static void validateEventInRQ(String eventName, int rqIdx, int eventIdx, int eventCount) throws JSONException {
        validateEventInRQ(TestUtils.commonDeviceId, eventName, null, 1, 0.0d, 0.0d, "_CLY_", "_CLY_", "_CLY_", "_CLY_", rqIdx, -1, eventIdx, eventCount);
    }

    protected static void validateEventInRQ(String deviceId, String eventName, int rqIdx, int eventIdx, int eventCount) throws JSONException {
        validateEventInRQ(deviceId, eventName, null, 1, 0.0d, 0.0d, "_CLY_", "_CLY_", "_CLY_", "_CLY_", rqIdx, -1, eventIdx, eventCount);
    }

/*
    //todo should be reworked
    @Test
    public void testRecordEvent() {
        final String eventKey = "eventKey";
        final int count = 42;
        final double sum = 3.0d;
        final double dur = 10.0d;
        final HashMap<String, Object> segmentation = new HashMap<>(1);
        segmentation.put("segkey1", "segvalue1");
        final HashMap<String, Object> segm = new HashMap<>();
        segm.put("segkey1", "segvalue1");

        //create a spied countly class
        final Countly countly = spy(mCountly);
        countly.moduleEvents = new ModuleEvents(countly, countly.config_);
        countly.moduleEvents.eventQueueProvider = mock(EventQueueProvider.class);

        doNothing().when(countly).sendEventsIfNeeded(false);
        doReturn(true).when(countly).isInitialized();

        countly.events().recordEvent(eventKey, segmentation, count, sum, dur);

        verify(countly.moduleEvents.eventQueueProvider).recordEventToEventQueue(eq(eventKey), eq(segm), eq(count), eq(sum), eq(dur), any(Long.class), any(Integer.class), any(Integer.class));
        verify(countly).sendEventsIfNeeded(false);
    }
*/
    //todo potential tests to rework
    /*

    @Test
    public void testRecordEvent_keyOnly() {
        final String eventKey = "eventKey";
        final Countly countly = spy(mCountly);
        doNothing().when(countly).recordEvent(eventKey, null, 1, 0.0d);
        countly.recordEvent(eventKey);
        verify(countly).recordEvent(eventKey, null, 1, 0.0d);
    }

    @Test
    public void testRecordEvent_keyAndCount() {
        final String eventKey = "eventKey";
        final int count = 42;
        final Countly countly = spy(mCountly);

        doNothing().when(countly).recordEvent(eventKey, null, count, 0.0d);
        countly.recordEvent(eventKey, null, count, 0.0d);
        verify(countly).recordEvent(eventKey, null, count, 0.0d);
    }

    @Test
    public void testRecordEvent_keyAndCountAndSum() {
        final String eventKey = "eventKey";
        final int count = 42;
        final double sum = 3.0d;
        final Countly countly = spy(mCountly);
        doNothing().when(countly).recordEvent(eventKey, null, count, sum);
        countly.recordEvent(eventKey, count, sum);
        verify(countly).recordEvent(eventKey, null, count, sum);
    }

    @Test
    public void testRecordEvent_keyAndSegmentationAndCount() {
        final String eventKey = "eventKey";
        final int count = 42;
        final HashMap<String, String> segmentation = new HashMap<>(1);
        segmentation.put("segkey1", "segvalue1");
        final Countly countly = spy(mCountly);
        doNothing().when(countly).recordEvent(eventKey, segmentation, count, 0.0d);
        countly.recordEvent(eventKey, segmentation, count);
        verify(countly).recordEvent(eventKey, segmentation, count, 0.0d);
    }

    @Test
    public void testRecordEvent_initNotCalled() {
        final String eventKey = "eventKey";
        final int count = 42;
        final double sum = 3.0d;
        final HashMap<String, String> segmentation = new HashMap<>(1);
        segmentation.put("segkey1", "segvalue1");

        try {
            mUninitedCountly.recordEvent(eventKey, segmentation, count, sum);
            // success
            // should not throw a exception anymore
        } catch (IllegalStateException ignored) {
            fail("expected IllegalStateException when recordEvent called before init");
        }
    }

    @Test
    public void testRecordEvent_nullKey() {
        final String eventKey = null;
        final int count = 42;
        final double sum = 3.0d;
        final HashMap<String, String> segmentation = new HashMap<>(1);
        segmentation.put("segkey1", "segvalue1");

        try {
            //noinspection ConstantConditions
            mCountly.recordEvent(eventKey, segmentation, count, sum);
            fail("expected IllegalArgumentException when recordEvent called with null key");
        } catch (IllegalArgumentException ignored) {
            // success
        }
    }

    @Test
    public void testRecordEvent_emptyKey() {
        final String eventKey = "";
        final int count = 42;
        final double sum = 3.0d;
        final HashMap<String, String> segmentation = new HashMap<>(1);
        segmentation.put("segkey1", "segvalue1");

        try {
            mCountly.recordEvent(eventKey, segmentation, count, sum);
            fail("expected IllegalArgumentException when recordEvent called with empty key");
        } catch (IllegalArgumentException ignored) {
            // success
        }
    }

    @Test
    public void testRecordEvent_countIsZero() {
        final String eventKey = "";
        final int count = 0;
        final double sum = 3.0d;
        final HashMap<String, String> segmentation = new HashMap<>(1);
        segmentation.put("segkey1", "segvalue1");

        try {
            mCountly.recordEvent(eventKey, segmentation, count, sum);
            fail("expected IllegalArgumentException when recordEvent called with count=0");
        } catch (IllegalArgumentException ignored) {
            // success
        }
    }

    @Test
    public void testRecordEvent_countIsNegative() {
        final String eventKey = "";
        final int count = -1;
        final double sum = 3.0d;
        final HashMap<String, String> segmentation = new HashMap<>(1);
        segmentation.put("segkey1", "segvalue1");

        try {
            mCountly.recordEvent(eventKey, segmentation, count, sum);
            fail("expected IllegalArgumentException when recordEvent called with a negative count");
        } catch (IllegalArgumentException ignored) {
            // success
        }
    }

    @Test
    public void testRecordEvent_segmentationHasNullKey() {
        final String eventKey = "";
        final int count = 1;
        final double sum = 3.0d;
        final HashMap<String, String> segmentation = new HashMap<>(1);
        segmentation.put(null, "segvalue1");

        try {
            mCountly.recordEvent(eventKey, segmentation, count, sum);
            fail("expected IllegalArgumentException when recordEvent called with segmentation with null key");
        } catch (IllegalArgumentException ignored) {
            // success
        }
    }

    @Test
    public void testRecordEvent_segmentationHasEmptyKey() {
        final String eventKey = "";
        final int count = 1;
        final double sum = 3.0d;
        final HashMap<String, String> segmentation = new HashMap<>(1);
        segmentation.put("", "segvalue1");

        try {
            mCountly.recordEvent(eventKey, segmentation, count, sum);
            fail("expected IllegalArgumentException when recordEvent called with segmentation with empty key");
        } catch (IllegalArgumentException ignored) {
            // success
        }
    }

    @Test
    public void testRecordEvent_segmentationHasNullValue() {
        final String eventKey = "";
        final int count = 1;
        final double sum = 3.0d;
        final HashMap<String, String> segmentation = new HashMap<>(1);
        segmentation.put("segkey1", null);

        try {
            mCountly.recordEvent(eventKey, segmentation, count, sum);
            fail("expected IllegalArgumentException when recordEvent called with segmentation with null value");
        } catch (IllegalArgumentException ignored) {
            // success
        }
    }

    @Test
    public void testRecordEvent_segmentationHasEmptyValue() {
        final String eventKey = "";
        final int count = 1;
        final double sum = 3.0d;
        final HashMap<String, String> segmentation = new HashMap<>(1);
        segmentation.put("segkey1", "");

        try {
            mCountly.recordEvent(eventKey, segmentation, count, sum);
            fail("expected IllegalArgumentException when recordEvent called with segmentation with empty value");
        } catch (IllegalArgumentException ignored) {
            // success
        }
    }
     */
}
