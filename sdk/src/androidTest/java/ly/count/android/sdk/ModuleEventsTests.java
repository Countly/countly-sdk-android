package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockingDetails;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.booleanThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class ModuleEventsTests {
    Countly mCountly;
    CountlyConfig config;
    EventQueue eventQueue;
    String eventKey = "asdf";
    ModuleEvents mEvents;

    @Before
    public void setUp() {
        final CountlyStore countlyStore = new CountlyStore(getContext());
        countlyStore.clear();

        mCountly = new Countly();
        config = (new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting();
        mCountly.init(config);

        eventQueue = mock(EventQueue.class);
        mCountly.setEventQueue(eventQueue);

        mEvents = mCountly.moduleEvents;
    }

    @After
    public void tearDown() {
    }

    @Test
    public void checkSegmentationTypes() {
        Map<String, Object> segm1 = new HashMap<>();

        Assert.assertTrue(ModuleEvents.checkSegmentationTypes(segm1));

        segm1.put("aa", "dd");
        segm1.put("aa1", "dda");
        Assert.assertTrue(ModuleEvents.checkSegmentationTypes(segm1));

        segm1.put("1", 1234);
        Assert.assertTrue(ModuleEvents.checkSegmentationTypes(segm1));

        segm1.put("2", 1234.55d);
        Assert.assertTrue(ModuleEvents.checkSegmentationTypes(segm1));

        segm1.put("3", true);
        Assert.assertTrue(ModuleEvents.checkSegmentationTypes(segm1));

        segm1.put("4", 45.4f);
        Assert.assertFalse(ModuleEvents.checkSegmentationTypes(segm1));

        segm1.put("4", new Object());
        Assert.assertFalse(ModuleEvents.checkSegmentationTypes(segm1));

        segm1.put("4", new int[]{1, 2});
        Assert.assertFalse(ModuleEvents.checkSegmentationTypes(segm1));
    }

    @Test
    public void fillInSegmentation(){
        Map<String, Object> segm1 = new HashMap<>();

        segm1.put("aa", "dd");
        segm1.put("aa1", "dda");
        segm1.put("1", 1234);
        segm1.put("2", 1234.55d);
        segm1.put("3", true);
        segm1.put("4", 45.4f);
        segm1.put("41", new Object());
        segm1.put("42", new int[]{1, 2});

        Map<String, String> mS = new HashMap<>();
        Map<String, Integer> mI = new HashMap<>();
        Map<String, Double> mD = new HashMap<>();
        Map<String, Boolean> mB = new HashMap<>();
        Map<String, Object> mR = new HashMap<>();

        ModuleEvents.fillInSegmentation(segm1, mS, mI, mD, mB, mR);

        Assert.assertEquals(2, mS.size());
        Assert.assertEquals(1, mI.size());
        Assert.assertEquals(1, mD.size());
        Assert.assertEquals(1, mB.size());
        Assert.assertEquals(3, mR.size());
        Assert.assertEquals(segm1.get("aa"), mS.get("aa"));
        Assert.assertEquals(segm1.get("aa1"), mS.get("aa1"));

        Assert.assertEquals(segm1.get("1"), mI.get("1"));
        Assert.assertEquals(segm1.get("2"), mD.get("2"));
        Assert.assertEquals(segm1.get("3"), mB.get("3"));
        Assert.assertEquals(segm1.get("4"), mR.get("4"));
        Assert.assertEquals(segm1.get("41"), mR.get("41"));
        Assert.assertEquals(segm1.get("42"), mR.get("42"));
    }

    @Test
    public void recordEvent_1(){
        mCountly.events().recordEvent(eventKey);
        verify(eventQueue).recordEvent(eventKey, null, null, null, null, 1, 0.0, 0.0, null);
    }

    @Test
    public void recordEvent_2(){
        mCountly.events().recordEvent(eventKey, 657);
        verify(eventQueue).recordEvent(eventKey, null, null, null, null, 657, 0.0, 0.0, null);
    }

    @Test
    public void recordEvent_3(){
        mCountly.events().recordEvent(eventKey, 657, 884.213d);
        verify(eventQueue).recordEvent(eventKey, null, null, null, null, 657, 884.213d, 0.0, null);
    }

    @Test
    public void recordEvent_4(){
        eventWithSymbolication(null, null, null, null);
    }

    @Test
    public void recordEvent_5(){
        eventWithSymbolication(3456, null, null, null);
    }

    @Test
    public void recordEvent_6(){
        eventWithSymbolication(7583, 39457.123d, null, null);
    }

    @Test
    public void recordEvent_7(){
        eventWithSymbolication(1245, 443.76d, 985.33d, null);
    }

    void eventWithSymbolication(Integer count, Double sum, Double dur, Long timestamp) {
        Map<String, Object> segm = new HashMap<>();
        segm.put("aa", "dd");
        segm.put("aa1", "dda");
        segm.put("1", 1234);
        segm.put("2", 1234.55d);
        segm.put("3", true);
        segm.put("4", 45.4f);
        segm.put("41", new Object());

        if(timestamp == null) {
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
            } else if(count != null && sum != null && dur != null) {
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

        if(timestamp == null) {
            if (count == null && sum == null && dur == null) {
                verify(eventQueue).recordEvent(eventKey, segmS, segmI, segmD, segmB, 1, 0.0, 0.0, null);
            } else if (count != null && sum == null && dur == null) {
                verify(eventQueue).recordEvent(eventKey, segmS, segmI, segmD, segmB, count, 0.0, 0.0, null);
            } else if (count != null && sum != null && dur == null) {
                verify(eventQueue).recordEvent(eventKey, segmS, segmI, segmD, segmB, count, sum, 0.0, null);
            } else if (count != null && sum != null && dur != null) {
                verify(eventQueue).recordEvent(eventKey, segmS, segmI, segmD, segmB, count, sum, dur, null);
            } else {
                Assert.fail("You should not get here");
            }
        } else {
            UtilsTime.Instant instant = UtilsTime.Instant.get(timestamp);
            ArgumentCaptor<UtilsTime.Instant> arg = ArgumentCaptor.forClass(UtilsTime.Instant.class);

            if (count == null && sum == null && dur == null) {
                verify(eventQueue).recordEvent(eq(eventKey), eq(segmS), eq(segmI), eq(segmD), eq(segmB), eq(1), eq(0.0), eq(0.0), arg.capture());
                UtilsTime.Instant captV =  arg.getValue();
                Assert.assertEquals(instant.hour, captV.hour);
                Assert.assertEquals(instant.dow, captV.dow);
                Assert.assertEquals(instant.timestampMs, captV.timestampMs);
                Assert.assertEquals(timestamp.longValue(), captV.timestampMs);
            } else if(count != null && sum != null && dur != null){
                verify(eventQueue).recordEvent(eq(eventKey), eq(segmS), eq(segmI), eq(segmD), eq(segmB), eq(count), eq(sum), eq(dur), arg.capture());
                UtilsTime.Instant captV =  arg.getValue();
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
    public void recordPastEvent_1(){
        eventWithSymbolication(null, null, null, 1579463653876L);
    }

    @Test
    public void recordPastEvent_2(){
        eventWithSymbolication(76355, 576334.33d, 85664.64d, 1579463653876L);
    }

    @Test
    public void startEndEvent_noSegments() throws InterruptedException {
        boolean res = mCountly.events().startEvent(eventKey);
        Assert.assertTrue(res);
        verify(eventQueue, times(0)).recordEvent(any(String.class), any(Map.class), any(Map.class), any(Map.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));

        Assert.assertEquals(1, ModuleEvents.timedEvents.size());
        Assert.assertTrue(ModuleEvents.timedEvents.containsKey(eventKey));
        Event startEvent = ModuleEvents.timedEvents.get(eventKey);

        Thread.sleep(1000);

        res = mCountly.events().endEvent(eventKey);
        Assert.assertTrue(res);
        Assert.assertEquals(0, ModuleEvents.timedEvents.size());

        ArgumentCaptor<UtilsTime.Instant> arg = ArgumentCaptor.forClass(UtilsTime.Instant.class);
        ArgumentCaptor<Double> argD = ArgumentCaptor.forClass(Double.class);
        verify(eventQueue).recordEvent(eq(eventKey), isNull(Map.class), isNull(Map.class), isNull(Map.class), isNull(Map.class), eq(1), eq(0.0d), argD.capture(), arg.capture());

        UtilsTime.Instant captV =  arg.getValue();
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
        verify(eventQueue, times(0)).recordEvent(any(String.class), any(Map.class), any(Map.class), any(Map.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));

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
        verify(eventQueue).recordEvent(eq(eventKey), eq(segmS), eq(segmI), eq(segmD), eq(segmB), eq(6372), eq(5856.34d), argD.capture(), arg.capture());

        UtilsTime.Instant captV =  arg.getValue();
        Assert.assertEquals(startEvent.hour, captV.hour);
        Assert.assertEquals(startEvent.dow, captV.dow);
        Assert.assertEquals(startEvent.timestamp, captV.timestampMs);

        Double captD = argD.getValue();
        Assert.assertEquals(2, captD, 0.1d);
    }

    @Test
    public void startCancelEndEvent(){
        boolean res = mCountly.events().startEvent(eventKey);
        Assert.assertTrue(res);
        verify(eventQueue, times(0)).recordEvent(any(String.class), any(Map.class), any(Map.class), any(Map.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));

        Assert.assertEquals(1, ModuleEvents.timedEvents.size());
        Assert.assertTrue(ModuleEvents.timedEvents.containsKey(eventKey));

        res = mCountly.events().cancelEvent(eventKey);
        Assert.assertTrue(res);
        Assert.assertEquals(0, ModuleEvents.timedEvents.size());

        verify(eventQueue, times(0)).recordEvent(any(String.class), any(Map.class), any(Map.class), any(Map.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));

        res = mCountly.events().endEvent(eventKey);
        Assert.assertFalse(res);
        Assert.assertEquals(0, ModuleEvents.timedEvents.size());
        verify(eventQueue, times(0)).recordEvent(any(String.class), any(Map.class), any(Map.class), any(Map.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));
    }

    @Test
    public void startCancelStartEndEvent() throws InterruptedException {
        boolean res = mCountly.events().startEvent(eventKey);
        Assert.assertTrue(res);
        verify(eventQueue, times(0)).recordEvent(any(String.class), any(Map.class), any(Map.class), any(Map.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));

        Assert.assertEquals(1, ModuleEvents.timedEvents.size());
        Assert.assertTrue(ModuleEvents.timedEvents.containsKey(eventKey));

        res = mCountly.events().cancelEvent(eventKey);
        Assert.assertTrue(res);
        Assert.assertEquals(0, ModuleEvents.timedEvents.size());
        verify(eventQueue, times(0)).recordEvent(any(String.class), any(Map.class), any(Map.class), any(Map.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));

        // finished first start and cancel

        res = mCountly.events().startEvent(eventKey);
        Assert.assertTrue(res);
        verify(eventQueue, times(0)).recordEvent(any(String.class), any(Map.class), any(Map.class), any(Map.class), any(Map.class), any(Integer.class), any(Double.class), any(Double.class), isNull(UtilsTime.Instant.class));

        Assert.assertEquals(1, ModuleEvents.timedEvents.size());
        Assert.assertTrue(ModuleEvents.timedEvents.containsKey(eventKey));
        Event startEvent = ModuleEvents.timedEvents.get(eventKey);

        Thread.sleep(1000);

        res = mCountly.events().endEvent(eventKey);
        Assert.assertTrue(res);
        Assert.assertEquals(0, ModuleEvents.timedEvents.size());

        ArgumentCaptor<UtilsTime.Instant> arg = ArgumentCaptor.forClass(UtilsTime.Instant.class);
        ArgumentCaptor<Double> argD = ArgumentCaptor.forClass(Double.class);
        verify(eventQueue).recordEvent(eq(eventKey), isNull(Map.class), isNull(Map.class), isNull(Map.class), isNull(Map.class), eq(1), eq(0.0d), argD.capture(), arg.capture());

        UtilsTime.Instant captV =  arg.getValue();
        Assert.assertEquals(startEvent.hour, captV.hour);
        Assert.assertEquals(startEvent.dow, captV.dow);
        Assert.assertEquals(startEvent.timestamp, captV.timestampMs);

        Double captD = argD.getValue();
        Assert.assertEquals(1, captD, 0.1d);
    }
}
