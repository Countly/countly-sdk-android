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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
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
        EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));
        mCountly.events().recordEvent(eventKey);
        verify(ep).recordEventInternal(eventKey, null, 1, 0.0, 0.0, null);
    }

    @Test
    public void recordEvent_2() {
        EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));
        mCountly.events().recordEvent(eventKey, 657);
        verify(ep).recordEventInternal(eventKey, null, 657, 0.0, 0.0, null);
    }

    @Test
    public void recordEvent_3() {
        EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));
        mCountly.events().recordEvent(eventKey, 657, 884.213d);
        verify(ep).recordEventInternal(eventKey, null, 657, 884.213d, 0.0, null);
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

        if (timestamp == null) {
            if (count == null && sum == null && dur == null) {
                mCountly.events().recordEvent(eventKey, segmUsed);
            } else if (count != null && sum == null && dur == null) {
                mCountly.events().recordEvent(eventKey, segmUsed, count);
            } else if (count != null && sum != null && dur == null) {
                mCountly.events().recordEvent(eventKey, segmUsed, count, sum);
            } else if (count != null && sum != null && dur != null) {
                mCountly.events().recordEvent(eventKey, segmUsed, count, sum, dur);
            } else {
                Assert.fail("You should not get here");
            }
        } else {
            if (count == null && sum == null && dur == null) {
                mCountly.events().recordPastEvent(eventKey, segmUsed, timestamp);
            } else if (count != null && sum != null && dur != null) {
                mCountly.events().recordPastEvent(eventKey, segmUsed, count, sum, dur, timestamp);
            } else {
                Assert.fail("You should not get here");
            }
        }

        final Map<String, Object> segm = new HashMap<>();

        segm.put("aa", "dd");
        segm.put("aa1", "dda");
        segm.put("1", 1234);
        segm.put("2", 1234.55d);
        segm.put("3", true);

        UtilsTime.Instant instant = UtilsTime.getCurrentInstant();

        ArgumentCaptor<Integer> arg1 = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Double> arg2 = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> arg3 = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Long> arg4 = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Integer> arg5 = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> arg6 = ArgumentCaptor.forClass(Integer.class);

        verify(eqp).recordEventToEventQueue(eq(eventKey), eq(segm), arg1.capture(), arg2.capture(), arg3.capture(), arg4.capture(), arg5.capture(), arg6.capture());

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
        verify(eventQueueProvider, times(0)).recordEventToEventQueue(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), any(Long.class), any(Integer.class), any(Integer.class));

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
        verify(eventQueueProvider).recordEventToEventQueue(eq(eventKey), isNull(Map.class), eq(1), eq(0.0d), argD.capture(), arg1.capture(), arg2.capture(), arg3.capture());

        Assert.assertEquals(startEvent.timestamp, (long) arg1.getValue());
        Assert.assertEquals(startEvent.hour, (int) arg2.getValue());
        Assert.assertEquals(startEvent.dow, (int) arg3.getValue());

        Double captD = argD.getValue();
        Assert.assertEquals(1, captD, 0.1d);
    }

    @Test
    public void startEndEvent_withSegments() throws InterruptedException {
        EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));
        boolean res = mCountly.events().startEvent(eventKey);
        Assert.assertTrue(res);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));

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
        verify(ep).recordEventInternal(eq(eventKey), eq(segmVals), eq(6372), eq(5856.34d), argD.capture(), arg.capture());

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
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));

        Assert.assertEquals(1, ModuleEvents.timedEvents.size());
        Assert.assertTrue(ModuleEvents.timedEvents.containsKey(eventKey));

        res = mCountly.events().cancelEvent(eventKey);
        Assert.assertTrue(res);
        Assert.assertEquals(0, ModuleEvents.timedEvents.size());

        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));

        res = mCountly.events().endEvent(eventKey);
        Assert.assertFalse(res);
        Assert.assertEquals(0, ModuleEvents.timedEvents.size());
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));
    }

    @Test
    public void startCancelStartEndEvent() throws InterruptedException {
        EventProvider ep = TestUtils.setEventProviderToMock(mCountly, mock(EventProvider.class));
        boolean res = mCountly.events().startEvent(eventKey);
        Assert.assertTrue(res);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));

        Assert.assertEquals(1, ModuleEvents.timedEvents.size());
        Assert.assertTrue(ModuleEvents.timedEvents.containsKey(eventKey));

        res = mCountly.events().cancelEvent(eventKey);
        Assert.assertTrue(res);
        Assert.assertEquals(0, ModuleEvents.timedEvents.size());
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));

        // finished first start and cancel

        res = mCountly.events().startEvent(eventKey);
        Assert.assertTrue(res);
        verify(ep, times(0)).recordEventInternal(any(String.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));

        Assert.assertEquals(1, ModuleEvents.timedEvents.size());
        Assert.assertTrue(ModuleEvents.timedEvents.containsKey(eventKey));
        Event startEvent = ModuleEvents.timedEvents.get(eventKey);

        Thread.sleep(1000);

        res = mCountly.events().endEvent(eventKey);
        Assert.assertTrue(res);
        Assert.assertEquals(0, ModuleEvents.timedEvents.size());

        ArgumentCaptor<UtilsTime.Instant> arg = ArgumentCaptor.forClass(UtilsTime.Instant.class);
        ArgumentCaptor<Double> argD = ArgumentCaptor.forClass(Double.class);
        verify(ep).recordEventInternal(eq(eventKey), isNull(Map.class), eq(1), eq(0.0d), argD.capture(), arg.capture());

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
        segm1.put("42", new int[] { 1, 2 });
        segm1.put("asd", "123");
        segm1.put("1", 1234);
        segm1.put("2", 1234.55d);
        segm1.put("3", true);

        segm2.put("asd", "123");
        segm2.put("1", 1234);
        segm2.put("2", 1234.55d);
        segm2.put("3", true);

        Map<String, Object> segm3 = new HashMap<>(segm1);
        mCountly.config_.eventProvider.recordEventInternal(eventKey, segm3, 123, 321.22d, 342.32d, null);

        verify(eqp).recordEventToEventQueue(eq(eventKey), eq(segm2), eq(123), eq(321.22d), eq(342.32d), any(Long.class), any(Integer.class), any(Integer.class));
        eqp = TestUtils.setEventQueueProviderToMock(mCountly, mock(EventQueueProvider.class));

        segm3.clear();
        segm3.putAll(segm1);

        mCountly.config_.eventProvider.recordEventInternal(eventKey, segm3, 123, 321.22d, 342.32d, null);
        verify(eqp).recordEventToEventQueue(eq(eventKey), eq(segm3), eq(123), eq(321.22d), eq(342.32d), any(Long.class), any(Integer.class), any(Integer.class));
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
