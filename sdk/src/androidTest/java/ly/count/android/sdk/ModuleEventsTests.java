package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
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
        final CountlyStore countlyStore = new CountlyStore(getContext(), mock(ModuleLog.class));
        countlyStore.clear();

        eventQueueProvider = mock(EventQueueProvider.class);

        mCountly = new Countly();
        config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        config.eventQueueProvider = eventQueueProvider;
        mCountly.init(config);
    }

    @After
    public void tearDown() {
    }

    @Test
    public void fillInSegmentation() {
        Map<String, Object> segm = new HashMap<>();

        segm.put("aa", "dd");
        segm.put("aa1", "dda");
        segm.put("1", 1234);
        segm.put("2", 1234.55d);
        segm.put("3", true);
        segm.put("4", 45.4f);
        segm.put("41", new Object());
        segm.put("42", new int[] { 1, 2 });

        Map<String, String> mS = new HashMap<>();
        Map<String, Integer> mI = new HashMap<>();
        Map<String, Double> mD = new HashMap<>();
        Map<String, Boolean> mB = new HashMap<>();
        Map<String, Object> mR = new HashMap<>();

        Utils.fillInSegmentation(segm, mS, mI, mD, mB, mR);

        Assert.assertEquals(2, mS.size());
        Assert.assertEquals(1, mI.size());
        Assert.assertEquals(1, mD.size());
        Assert.assertEquals(1, mB.size());
        Assert.assertEquals(3, mR.size());
        Assert.assertEquals(segm.get("aa"), mS.get("aa"));
        Assert.assertEquals(segm.get("aa1"), mS.get("aa1"));

        Assert.assertEquals(segm.get("1"), mI.get("1"));
        Assert.assertEquals(segm.get("2"), mD.get("2"));
        Assert.assertEquals(segm.get("3"), mB.get("3"));
        Assert.assertEquals(segm.get("4"), mR.get("4"));
        Assert.assertEquals(segm.get("41"), mR.get("41"));
        Assert.assertEquals(segm.get("42"), mR.get("42"));
    }

    @Test
    public void recordEvent_1() {
        mCountly.events().recordEvent(eventKey);
        verify(eventQueueProvider).recordEventToEventQueue(eventKey, null, null, null, null, 1, 0.0, 0.0, null);
    }

    @Test
    public void recordEvent_2() {
        mCountly.events().recordEvent(eventKey, 657);
        verify(eventQueueProvider).recordEventToEventQueue(eventKey, null, null, null, null, 657, 0.0, 0.0, null);
    }

    @Test
    public void recordEvent_3() {
        mCountly.events().recordEvent(eventKey, 657, 884.213d);
        verify(eventQueueProvider).recordEventToEventQueue(eventKey, null, null, null, null, 657, 884.213d, 0.0, null);
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
        Map<String, Object> segm = new HashMap<>();
        segm.put("aa", "dd");
        segm.put("aa1", "dda");
        segm.put("1", 1234);
        segm.put("2", 1234.55d);
        segm.put("3", true);
        segm.put("4", 45.4f);
        segm.put("41", new Object());

        if (timestamp == null) {
            if (count == null && sum == null && dur == null) {
                mCountly.events().recordEvent(eventKey, segm);
            } else if (count != null && sum == null && dur == null) {
                mCountly.events().recordEvent(eventKey, segm, count);
            } else if (count != null && sum != null && dur == null) {
                mCountly.events().recordEvent(eventKey, segm, count, sum);
            } else if (count != null && sum != null && dur != null) {
                mCountly.events().recordEvent(eventKey, segm, count, sum, dur);
            } else {
                Assert.fail("You should not get here");
            }
        } else {
            if (count == null && sum == null && dur == null) {
                mCountly.events().recordPastEvent(eventKey, segm, timestamp);
            } else if (count != null && sum != null && dur != null) {
                mCountly.events().recordPastEvent(eventKey, segm, count, sum, dur, timestamp);
            } else {
                Assert.fail("You should not get here");
            }
        }

        final Map<String, String> segmS = new HashMap<>();
        final Map<String, Integer> segmI = new HashMap<>();
        final Map<String, Double> segmD = new HashMap<>();
        final Map<String, Boolean> segmB = new HashMap<>();

        segmS.put("aa", "dd");
        segmS.put("aa1", "dda");
        segmI.put("1", 1234);
        segmD.put("2", 1234.55d);
        segmB.put("3", true);

        if (timestamp == null) {
            if (count == null && sum == null && dur == null) {
                verify(eventQueueProvider).recordEventToEventQueue(eventKey, segmS, segmI, segmD, segmB, 1, 0.0, 0.0, null);
            } else if (count != null && sum == null && dur == null) {
                verify(eventQueueProvider).recordEventToEventQueue(eventKey, segmS, segmI, segmD, segmB, count, 0.0, 0.0, null);
            } else if (count != null && sum != null && dur == null) {
                verify(eventQueueProvider).recordEventToEventQueue(eventKey, segmS, segmI, segmD, segmB, count, sum, 0.0, null);
            } else if (count != null && sum != null && dur != null) {
                verify(eventQueueProvider).recordEventToEventQueue(eventKey, segmS, segmI, segmD, segmB, count, sum, dur, null);
            } else {
                Assert.fail("You should not get here");
            }
        } else {
            UtilsTime.Instant instant = UtilsTime.Instant.get(timestamp);
            ArgumentCaptor<UtilsTime.Instant> arg = ArgumentCaptor.forClass(UtilsTime.Instant.class);

            if (count == null && sum == null && dur == null) {
                verify(eventQueueProvider).recordEventToEventQueue(eq(eventKey), eq(segmS), eq(segmI), eq(segmD), eq(segmB), eq(1), eq(0.0), eq(0.0), arg.capture());
                UtilsTime.Instant captV = arg.getValue();
                Assert.assertEquals(instant.hour, captV.hour);
                Assert.assertEquals(instant.dow, captV.dow);
                Assert.assertEquals(instant.timestampMs, captV.timestampMs);
                Assert.assertEquals(timestamp.longValue(), captV.timestampMs);
            } else if (count != null && sum != null && dur != null) {
                verify(eventQueueProvider).recordEventToEventQueue(eq(eventKey), eq(segmS), eq(segmI), eq(segmD), eq(segmB), eq(count), eq(sum), eq(dur), arg.capture());
                UtilsTime.Instant captV = arg.getValue();
                Assert.assertEquals(instant.hour, captV.hour);
                Assert.assertEquals(instant.dow, captV.dow);
                Assert.assertEquals(instant.timestampMs, captV.timestampMs);
                Assert.assertEquals(timestamp.longValue(), captV.timestampMs);
            } else {
                Assert.fail("You should not get here");
            }
        }
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
        verify(eventQueueProvider, times(0)).recordEventToEventQueue(any(String.class), any(Map.class), any(Map.class), any(Map.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));

        Assert.assertEquals(1, ModuleEvents.timedEvents.size());
        Assert.assertTrue(ModuleEvents.timedEvents.containsKey(eventKey));
        Event startEvent = ModuleEvents.timedEvents.get(eventKey);

        Thread.sleep(1000);

        res = mCountly.events().endEvent(eventKey);
        Assert.assertTrue(res);
        Assert.assertEquals(0, ModuleEvents.timedEvents.size());

        ArgumentCaptor<UtilsTime.Instant> arg = ArgumentCaptor.forClass(UtilsTime.Instant.class);
        ArgumentCaptor<Double> argD = ArgumentCaptor.forClass(Double.class);
        verify(eventQueueProvider).recordEventToEventQueue(eq(eventKey), isNull(Map.class), isNull(Map.class), isNull(Map.class), isNull(Map.class), eq(1), eq(0.0d), argD.capture(), arg.capture());

        UtilsTime.Instant captV = arg.getValue();
        Assert.assertEquals(startEvent.hour, captV.hour);
        Assert.assertEquals(startEvent.dow, captV.dow);
        Assert.assertEquals(startEvent.timestamp, captV.timestampMs);

        Double captD = argD.getValue();
        Assert.assertEquals(1, captD, 0.1d);
    }

    @Test
    public void startEndEvent_withSegments() throws InterruptedException {
        boolean res = mCountly.events().startEvent(eventKey);
        Assert.assertTrue(res);
        verify(eventQueueProvider, times(0)).recordEventToEventQueue(any(String.class), any(Map.class), any(Map.class), any(Map.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));

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
        segm.put("4", 45.4f);
        segm.put("41", new Object());

        res = mCountly.events().endEvent(eventKey, segm, 6372, 5856.34d);
        Assert.assertTrue(res);
        Assert.assertEquals(0, ModuleEvents.timedEvents.size());

        final Map<String, String> segmS = new HashMap<>();
        final Map<String, Integer> segmI = new HashMap<>();
        final Map<String, Double> segmD = new HashMap<>();
        final Map<String, Boolean> segmB = new HashMap<>();
        segmS.put("aa", "dd");
        segmS.put("aa1", "dda");
        segmI.put("1", 1234);
        segmD.put("2", 1234.55d);
        segmB.put("3", true);

        ArgumentCaptor<UtilsTime.Instant> arg = ArgumentCaptor.forClass(UtilsTime.Instant.class);
        ArgumentCaptor<Double> argD = ArgumentCaptor.forClass(Double.class);
        verify(eventQueueProvider).recordEventToEventQueue(eq(eventKey), eq(segmS), eq(segmI), eq(segmD), eq(segmB), eq(6372), eq(5856.34d), argD.capture(), arg.capture());

        UtilsTime.Instant captV = arg.getValue();
        Assert.assertEquals(startEvent.hour, captV.hour);
        Assert.assertEquals(startEvent.dow, captV.dow);
        Assert.assertEquals(startEvent.timestamp, captV.timestampMs);

        Double captD = argD.getValue();
        Assert.assertEquals(2, captD, 0.1d);
    }

    @Test
    public void startCancelEndEvent() {
        boolean res = mCountly.events().startEvent(eventKey);
        Assert.assertTrue(res);
        verify(eventQueueProvider, times(0)).recordEventToEventQueue(any(String.class), any(Map.class), any(Map.class), any(Map.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));

        Assert.assertEquals(1, ModuleEvents.timedEvents.size());
        Assert.assertTrue(ModuleEvents.timedEvents.containsKey(eventKey));

        res = mCountly.events().cancelEvent(eventKey);
        Assert.assertTrue(res);
        Assert.assertEquals(0, ModuleEvents.timedEvents.size());

        verify(eventQueueProvider, times(0)).recordEventToEventQueue(any(String.class), any(Map.class), any(Map.class), any(Map.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));

        res = mCountly.events().endEvent(eventKey);
        Assert.assertFalse(res);
        Assert.assertEquals(0, ModuleEvents.timedEvents.size());
        verify(eventQueueProvider, times(0)).recordEventToEventQueue(any(String.class), any(Map.class), any(Map.class), any(Map.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));
    }

    @Test
    public void startCancelStartEndEvent() throws InterruptedException {
        boolean res = mCountly.events().startEvent(eventKey);
        Assert.assertTrue(res);
        verify(eventQueueProvider, times(0)).recordEventToEventQueue(any(String.class), any(Map.class), any(Map.class), any(Map.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));

        Assert.assertEquals(1, ModuleEvents.timedEvents.size());
        Assert.assertTrue(ModuleEvents.timedEvents.containsKey(eventKey));

        res = mCountly.events().cancelEvent(eventKey);
        Assert.assertTrue(res);
        Assert.assertEquals(0, ModuleEvents.timedEvents.size());
        verify(eventQueueProvider, times(0)).recordEventToEventQueue(any(String.class), any(Map.class), any(Map.class), any(Map.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));

        // finished first start and cancel

        res = mCountly.events().startEvent(eventKey);
        Assert.assertTrue(res);
        verify(eventQueueProvider, times(0)).recordEventToEventQueue(any(String.class), any(Map.class), any(Map.class), any(Map.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));

        Assert.assertEquals(1, ModuleEvents.timedEvents.size());
        Assert.assertTrue(ModuleEvents.timedEvents.containsKey(eventKey));
        Event startEvent = ModuleEvents.timedEvents.get(eventKey);

        Thread.sleep(1000);

        res = mCountly.events().endEvent(eventKey);
        Assert.assertTrue(res);
        Assert.assertEquals(0, ModuleEvents.timedEvents.size());

        ArgumentCaptor<UtilsTime.Instant> arg = ArgumentCaptor.forClass(UtilsTime.Instant.class);
        ArgumentCaptor<Double> argD = ArgumentCaptor.forClass(Double.class);
        verify(eventQueueProvider).recordEventToEventQueue(eq(eventKey), isNull(Map.class), isNull(Map.class), isNull(Map.class), isNull(Map.class), eq(1), eq(0.0d), argD.capture(), arg.capture());

        UtilsTime.Instant captV = arg.getValue();
        Assert.assertEquals(startEvent.hour, captV.hour);
        Assert.assertEquals(startEvent.dow, captV.dow);
        Assert.assertEquals(startEvent.timestamp, captV.timestampMs);

        Double captD = argD.getValue();
        Assert.assertEquals(1, captD, 0.1d);
    }

    @Test
    public void recordEventInternalProcessedTest() {
        Map<String, Object> segm1 = new HashMap<>();

        final Map<String, String> segmS = new HashMap<>();
        final Map<String, Integer> segmI = new HashMap<>();
        final Map<String, Double> segmD = new HashMap<>();
        final Map<String, Boolean> segmB = new HashMap<>();

        for (String it : ModuleEvents.reservedSegmentationKeys) {
            segm1.put(it, it);
        }

        segm1.put("4", 45.4f);
        segm1.put("41", new Object());
        segm1.put("42", new int[] { 1, 2 });
        segm1.put("asd", "123");
        segm1.put("1", 1234);
        segm1.put("2", 1234.55d);
        segm1.put("3", true);

        segmS.put("asd", "123");
        segmI.put("1", 1234);
        segmD.put("2", 1234.55d);
        segmB.put("3", true);

        Map<String, Object> segm2 = new HashMap<>(segm1);
        mCountly.config_.eventProvider.recordEventInternal(eventKey, segm2, 123, 321.22d, 342.32d, null, false);

        verify(eventQueueProvider).recordEventToEventQueue(eventKey, segmS, segmI, segmD, segmB, 123, 321.22d, 342.32d, null);

        segm2.clear();
        segm2.putAll(segm1);

        for (String it : ModuleEvents.reservedSegmentationKeys) {
            segmS.put(it, it);
        }

        mCountly.config_.eventProvider.recordEventInternal(eventKey, segm2, 123, 321.22d, 342.32d, null, true);
        verify(eventQueueProvider).recordEventToEventQueue(eventKey, segmS, segmI, segmD, segmB, 123, 321.22d, 342.32d, null);
    }

    /**
     * Verifying the functionality for `recordEventToEventQueue` of the `eventQueueProvider`
     * Providing the 'current' event, with no timestamp/instant
     */
    @Test
    public void testRecordEvent() {
        ModuleEvents moduleEvents = mCountly.moduleEvents;
        moduleEvents.storageProvider = mock(StorageProvider.class);
        moduleEvents.eventQueueProvider = moduleEvents;

        final String eventKey = "eventKey";
        final int count = 42;
        final double sum = 3.0d;
        final double dur = 10.0d;
        final Map<String, String> segmentation = new HashMap<>(1);
        final Map<String, Integer> segmentationInt = new HashMap<>(2);
        final Map<String, Double> segmentationDouble = new HashMap<>(3);
        final Map<String, Boolean> segmentationBoolean = new HashMap<>(4);
        UtilsTime.Instant instant = UtilsTime.getCurrentInstant();
        final long timestamp = instant.timestampMs;
        final int hour = instant.hour;
        final int dow = instant.dow;
        final ArgumentCaptor<Long> arg = ArgumentCaptor.forClass(Long.class);

        moduleEvents.eventQueueProvider.recordEventToEventQueue(eventKey, segmentation, segmentationInt, segmentationDouble, segmentationBoolean, count, sum, dur, null);
        verify(moduleEvents.storageProvider).addEvent(eq(eventKey), eq(segmentation), eq(segmentationInt), eq(segmentationDouble), eq(segmentationBoolean), arg.capture(), eq(hour), eq(dow), eq(count), eq(sum), eq(dur));

        //used timestamp is withing a couple of ms
        assertTrue((arg.getValue() - timestamp) <= 4);
    }

    /**
     * Verifying the functionality for `recordEventToEventQueue` of the `eventQueueProvider`
     * Trying to record a past event
     * Providing the 'current' event, with a custom timestamp/instant
     */
    @Test
    public void testRecordPastEvent() {
        ModuleEvents moduleEvents = mCountly.moduleEvents;
        moduleEvents.storageProvider = mock(StorageProvider.class);
        moduleEvents.eventQueueProvider = moduleEvents;

        final String eventKey = "eventKey";
        final int count = 42;
        final double sum = 3.0d;
        final double dur = 10.0d;
        final Map<String, String> segmentation = new HashMap<>(1);
        final Map<String, Integer> segmentationInt = new HashMap<>(2);
        final Map<String, Double> segmentationDouble = new HashMap<>(3);
        final Map<String, Boolean> segmentationBoolean = new HashMap<>(4);
        UtilsTime.Instant instant = UtilsTime.Instant.get(123456789);
        final long timestamp = instant.timestampMs;
        final int hour = instant.hour;
        final int dow = instant.dow;
        final ArgumentCaptor<Long> arg = ArgumentCaptor.forClass(Long.class);

        moduleEvents.eventQueueProvider.recordEventToEventQueue(eventKey, segmentation, segmentationInt, segmentationDouble, segmentationBoolean, count, sum, dur, instant);
        verify(moduleEvents.storageProvider).addEvent(eq(eventKey), eq(segmentation), eq(segmentationInt), eq(segmentationDouble), eq(segmentationBoolean), arg.capture(), eq(hour), eq(dow), eq(count), eq(sum), eq(dur));
        assertEquals(timestamp, (long) arg.getValue());
    }
}
