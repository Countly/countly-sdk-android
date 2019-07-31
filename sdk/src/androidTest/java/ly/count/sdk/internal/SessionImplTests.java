package ly.count.sdk.internal;

import android.support.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.powermock.reflect.Whitebox;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ly.count.sdk.android.Config;
import ly.count.sdk.android.Countly;
import ly.count.sdk.Crash;
import ly.count.sdk.CrashProcessor;
import ly.count.sdk.Event;
import ly.count.sdk.android.internal.BaseTests;
import ly.count.sdk.android.internal.Device;
import ly.count.sdk.android.internal.Utils;

@RunWith(AndroidJUnit4.class)
public class SessionImplTests extends BaseTests {
    @Rule
    public TestName testName = new TestName();

    @Before
    public void setUp() throws Exception {
        super.setUp();
        if (testName.getMethodName().equals("session_crashRecordedWithCustomProcessor") ||
                testName.getMethodName().equals("session_crashRecordedWithLegacyData")) {
            setUpApplication(defaultConfig().setCrashProcessorClass(CrashProcessorImpl.class), false);
        } else {
            setUpApplication(defaultConfig());
        }
    }

    @Override
    protected Config defaultConfig() throws Exception {
        return super.defaultConfig().disableTestMode().enableFeatures(Config.Feature.CrashReporting);
    }

    @Test
    public void constructor_empty(){
        final long allowance = 3;
        long time = ly.count.sdk.android.internal.Device.dev.uniqueTimestamp();
        SessionImpl session = new SessionImpl(ctx);

        long diff = session.getId() - time;
        Assert.assertTrue(diff < allowance);
    }

    @Test
    public void beginEndSession_doubleBegin() {
        long sessionID = 12345;
        SessionImpl session = new SessionImpl(ctx, sessionID);

        long beginTime = 234;
        session.begin(beginTime);

        session.begin(345L);
        Assert.assertEquals(sessionID, (long)session.getId());
        Assert.assertEquals(beginTime, (long)session.getBegan());

        session.end();
        Assert.assertEquals(sessionID, (long)session.getId());
        Assert.assertEquals(beginTime, (long)session.getBegan());

        session.begin();
        Assert.assertEquals(sessionID, (long)session.getId());
        Assert.assertEquals(beginTime, (long)session.getBegan());
    }

    @Test
    public void beginEndSession_valuesCorrect() {
        SessionImpl session = new SessionImpl(ctx);

        session.begin();
        session.end();

        Assert.assertEquals(session.began, session.getBegan());
        Assert.assertEquals(session.ended, session.getEnded());
    }

    @Test
    public void beginEndSession_simple(){
        SessionImpl session = new SessionImpl(ctx);
        final long timeGuardStart = System.nanoTime();

        session.begin();
        session.end();

        final long timeGuardEnd = System.nanoTime();
        final long timeGuardDiff = timeGuardEnd - timeGuardStart;

        final long sessionTimeStart = session.getBegan();
        final long sessionTimeEnd = session.getEnded();
        final long sessionTimeDiff = sessionTimeEnd - sessionTimeStart;

        Assert.assertTrue(sessionTimeDiff <= timeGuardDiff);
    }

    @Test
    public void secondIsCorrect() {
        long secondInMs = 1000 * 1000 * 1000;
        Assert.assertEquals((double)secondInMs, ly.count.sdk.android.internal.Device.NS_IN_SECOND);
    }

    @Test
    public void getBeganAndEnded() {
        long timeBegin = 123L;
        long timeEnd = 345L;
        SessionImpl session = new SessionImpl(ctx);
        session.begin(timeBegin);
        session.end(timeEnd, null, null);

        Assert.assertEquals(timeBegin, (long)session.getBegan());
        Assert.assertEquals(timeEnd, (long)session.getEnded());
    }

    @Test
    public void beginAfterEnd() {
        long timeBegin = 123L;
        long timeEnd = 345L;
        SessionImpl session = new SessionImpl(ctx);

        session.end();
        Assert.assertNull(session.getBegan());
        Assert.assertNull(session.getEnded());

        session.begin(timeBegin);
        session.end(timeEnd, null, null);

        Assert.assertEquals(timeBegin, (long)session.getBegan());
        Assert.assertEquals(timeEnd, (long)session.getEnded());
    }

    @Test
    public void beginAfterEndSet() {
        long timeEnd = 345L;
        SessionImpl session = new SessionImpl(ctx);

        session.ended = timeEnd;
        session.begin();

        Assert.assertNull(session.getBegan());
        Assert.assertEquals(timeEnd, (long) session.getEnded());
    }

    @Test
    public void endedTwice() {
        long timeBegin = 123L;
        long timeEnd = 345L;
        long timeEndSecond = 678L;
        SessionImpl session = new SessionImpl(ctx);

        Assert.assertNull(session.getBegan());
        Assert.assertNull(session.getEnded());

        session.begin(timeBegin);

        Assert.assertEquals(timeBegin, (long) session.getBegan());
        Assert.assertNull(session.getEnded());

        session.end(timeEnd, null, null);

        Assert.assertEquals(timeBegin, (long)session.getBegan());
        Assert.assertEquals(timeEnd, (long)session.getEnded());

        session.end(timeEndSecond, null, null);

        Assert.assertEquals(timeBegin, (long)session.getBegan());
        Assert.assertEquals(timeEnd, (long)session.getEnded());
    }

    @Test
    public void updateDuration_simple() throws Exception{
        int beginOffsetSeconds = 4;
        long timeBeginOffset = (long) (ly.count.sdk.android.internal.Device.NS_IN_SECOND * beginOffsetSeconds);

        SessionImpl session = new SessionImpl(ctx);
        final long timeGuardBase = System.nanoTime() - timeBeginOffset;
        session.begin(timeGuardBase);

        final long timeGuardStart = System.nanoTime();
        Long duration = Whitebox.<Long>invokeMethod(session, "updateDuration", (Object[]) null);
        final long timeGuardEnd = System.nanoTime();

        final long timeGuardDurStart = Math.round((timeGuardStart - timeGuardBase) / ly.count.sdk.android.internal.Device.NS_IN_SECOND);
        final long timeGuardDurEnded = Math.round((timeGuardEnd - timeGuardBase) / ly.count.sdk.android.internal.Device.NS_IN_SECOND);

        Assert.assertTrue(duration >= timeGuardDurStart);
        Assert.assertTrue(duration <= timeGuardDurEnded);
    }

    @Test
    public void updateDuration_notFirstUpdate() throws Exception{
        int beginOffsetSeconds = 12;
        long timeBeginOffset = (long) (ly.count.sdk.android.internal.Device.NS_IN_SECOND * beginOffsetSeconds);

        final long timeGuardBase = System.nanoTime() - timeBeginOffset;
        SessionImpl session = new SessionImpl(ctx);
        session.begin();
        Whitebox.setInternalState(session, "updated", timeGuardBase);

        final long timeGuardStart = System.nanoTime();
        Long duration = Whitebox.<Long>invokeMethod(session, "updateDuration", (Object[]) null);
        final long timeGuardEnd = System.nanoTime();

        final long timeGuardDurStart = Math.round((timeGuardStart - timeGuardBase) / ly.count.sdk.android.internal.Device.NS_IN_SECOND);
        final long timeGuardDurEnded = Math.round((timeGuardEnd - timeGuardBase) / ly.count.sdk.android.internal.Device.NS_IN_SECOND);

        Assert.assertTrue(duration >= timeGuardDurStart);
        Assert.assertTrue(duration <= timeGuardDurEnded);
    }

    @Test
    public void beginEndSession_simpleWithWait() throws InterruptedException{
        long sleepTimeMs = 500;

        SessionImpl session = new SessionImpl(ctx);
        final long timeGuardStart = System.nanoTime();

        session.begin();
        Thread.sleep(sleepTimeMs);
        session.end();

        final long timeGuardEnd = System.nanoTime();
        final long timeGuardDiff = timeGuardEnd - timeGuardStart;

        final long sessionTimeStart = session.getBegan();
        final long sessionTimeEnd = session.getEnded();
        final long sessionTimeDiff = sessionTimeEnd - sessionTimeStart;

        Assert.assertTrue(sessionTimeDiff >= (sleepTimeMs * 1000));
        Assert.assertTrue(sessionTimeDiff <= timeGuardDiff);
    }


    @Test
    public void update_simple() {
        long timeBegin = 123L;
        long timeEnd = 345L;
        SessionImpl session = new SessionImpl(ctx);

        session.update();

        Assert.assertNull(session.getBegan());
        Assert.assertNull(session.getEnded());

        session.begin(timeBegin);
        session.update();

        Assert.assertEquals(timeBegin, (long) session.getBegan());
        Assert.assertNull(session.getEnded());

        session.end(timeEnd, null, null);
        session.update();

        Assert.assertEquals(timeBegin, (long)session.getBegan());
        Assert.assertEquals(timeEnd, (long)session.getEnded());
    }

    @Test
    public void storeRestore_simple() {
        SessionImpl session = new SessionImpl(ctx);
        byte[] data = session.store();
        Assert.assertNotNull(data);
        SessionImpl restored = new SessionImpl(ctx, session.id);
        Assert.assertTrue(restored.restore(data));
        Assert.assertEquals(session, restored);
    }

    @Test
    public void storeRestore_full() throws InterruptedException {
        SessionImpl session = new SessionImpl(ctx);
        session.begin().event("key1")
                .setCount(2)
                .setDuration(3)
                .setSum(4)
                .addSegment("k1", "v1")
                .addSegment("k2", "v2")
                .record();

        session.event("key2")
                .setCount(2)
                .setDuration(3)
                .setSum(4)
                .addSegment("k1", "v1")
                .addSegment("k2", "v2")
                .record();

        Thread.sleep(100);
        session.update();

        session.addParam("param1", 1)
                .addParam("param2", "value 2");

        Thread.sleep(100);
        session.end();

        byte[] data = session.store();

        Assert.assertNotNull(data);
        SessionImpl restored = new SessionImpl(ctx, session.id);

        Assert.assertTrue(restored.restore(data));
        Assert.assertEquals(session, restored);
    }

    @Test
    public void autostore() {
        SessionImpl session = new SessionImpl(ctx);
        session.begin().event("key1")
                .setCount(2)
                .setDuration(3)
                .setSum(4)
                .addSegment("k1", "v1")
                .addSegment("k2", "v2")
                .record();

        List<Long> ids = Storage.list(ctx, session.storagePrefix());
        Assert.assertNotNull(ids);
        Assert.assertTrue(ids.contains(session.storageId()));

        SessionImpl restored = Storage.pop(ctx, new SessionImpl(ctx, session.getId()));
        Assert.assertNotNull(restored);
        Assert.assertEquals(restored, session);

        // restored is updated and stored, while session has updated = null
        restored.update();

        restored = Storage.pop(ctx, new SessionImpl(ctx, session.getId()));
        Assert.assertNotNull(restored);
        Assert.assertFalse(restored.equals(session));
    }

    @Test
    public void timedEvent() throws Exception {
        SessionImpl session = new SessionImpl(ctx);
        Event event1 = session.begin().timedEvent("key1");
        Assert.assertEquals(1, session.timedEvents().size());

        Thread.sleep(1100);

        session.timedEvent("key1").endAndRecord();
        Assert.assertEquals(1d, ((EventImpl) event1).getDuration());
    }

    @Test
    public void timedEventContinues() {
        SessionImpl session = new SessionImpl(ctx);
        Utils.reflectiveSetField(SDKCore.instance.module(ModuleSessions.class), "session", session);

        Event event1 = session.begin().timedEvent("key1")
                .addSegment("k1", "v1");

        Assert.assertEquals(1, session.timedEvents().size());

        session.timedEvent("key1")
                .addSegment("k2", "v2");

        Assert.assertEquals(1, session.timedEvents().size());

        session.timedEvent("key1")
                .setSum(9.99);

        Assert.assertEquals(1, session.timedEvents().size());

        Event event2 = session.timedEvent("key2");

        Assert.assertEquals(2, session.timedEvents().size());

        session.timedEvent("key1").endAndRecord();

        Assert.assertEquals(1, session.timedEvents().size());
        Assert.assertEquals(1, session.events.size());
        Assert.assertTrue(session.timedEvents().has("key2"));
        Assert.assertSame(event2, session.timedEvents().event(ctx, "key2"));

        event2.addSegment("k2", "v2").addSegment("k1", "v1").setSum(9.99).record();

        Assert.assertEquals(0, session.timedEvents().size());
        Assert.assertEquals(2, session.events.size());
        Assert.assertSame(event1, session.events.get(0));
        Assert.assertSame(event2, session.events.get(1));
        Assert.assertNotNull(((EventImpl) event1).getDuration());
        Assert.assertEquals(0d, ((EventImpl) event1).getDuration());
        Assert.assertNotNull(((EventImpl) event2).getDuration());
    }

    @Test
    public void session_lifecycle() {
        SessionImpl session = new SessionImpl(ctx);
        session.begin();
        Long began = Whitebox.getInternalState(session, "began");
        Assert.assertNotNull(began);
        Assert.assertNull(Whitebox.getInternalState(session, "updated"));
        Assert.assertNull(Whitebox.getInternalState(session, "ended"));

        session.begin();
        Assert.assertEquals(began, Whitebox.getInternalState(session, "began"));
        Assert.assertNull(Whitebox.getInternalState(session, "updated"));
        Assert.assertNull(Whitebox.getInternalState(session, "ended"));
        began = Whitebox.getInternalState(session, "began");

        session.update();
        Assert.assertNotNull(Whitebox.getInternalState(session, "began"));
        Assert.assertNotNull(Whitebox.getInternalState(session, "updated"));
        Assert.assertNull(Whitebox.getInternalState(session, "ended"));

        Long updated = Whitebox.getInternalState(session, "updated");
        Assert.assertTrue(began < updated);

        session.update();
        Assert.assertNotNull(Whitebox.getInternalState(session, "began"));
        Assert.assertNotNull(Whitebox.getInternalState(session, "updated"));
        Assert.assertTrue(updated < (long)Whitebox.getInternalState(session, "updated"));
        Assert.assertNull(Whitebox.getInternalState(session, "ended"));
        updated = Whitebox.getInternalState(session, "updated");

        session.end();
        Assert.assertNotNull(Whitebox.getInternalState(session, "began"));
        Assert.assertNotNull(Whitebox.getInternalState(session, "updated"));
        Assert.assertNotNull(Whitebox.getInternalState(session, "ended"));
        Assert.assertTrue(updated < (long)Whitebox.getInternalState(session, "ended"));
        Assert.assertTrue(began < (long)Whitebox.getInternalState(session, "ended"));
        Assert.assertTrue(began < updated);
    }

    @Test
    public void session_updateBeforeBegin() {
        SessionImpl session = new SessionImpl(ctx);
        session.update();
        Assert.assertNull(Whitebox.getInternalState(session, "began"));
        Assert.assertNull(Whitebox.getInternalState(session, "updated"));
        Assert.assertNull(Whitebox.getInternalState(session, "ended"));
    }

    @Test
    public void session_endBeforeBegin() {
        SessionImpl session = new SessionImpl(ctx);
        session.end();
        Assert.assertNull(Whitebox.getInternalState(session, "began"));
        Assert.assertNull(Whitebox.getInternalState(session, "updated"));
        Assert.assertNull(Whitebox.getInternalState(session, "ended"));
    }

    @Test
    public void session_recoverNoUpdate() throws Exception {
        long beginNs = System.nanoTime() - ly.count.sdk.android.internal.Device.dev.secToNs(config.getSessionCooldownPeriod() * 3);
        SessionImpl session = new SessionImpl(ctx, System.currentTimeMillis() - ly.count.sdk.android.internal.Device.dev.secToNs(config.getSessionCooldownPeriod() * 3));
        session.begin(beginNs);

        Thread.sleep(300);

        session = Storage.read(ctx, session);
        Assert.assertNotNull(session);

        session.recover(config);
        Thread.sleep(300);

        session = Storage.read(ctx, session);
        Assert.assertNull(session);

        List<Long> requests = Storage.list(ctx, Request.getStoragePrefix());
        Assert.assertEquals(2, requests.size());

        Request begin = Storage.read(ctx, new Request(requests.get(0)));
        Request end = Storage.read(ctx, new Request(requests.get(1)));

        Assert.assertNotNull(begin);
        Assert.assertNotNull(end);
        Assert.assertTrue(begin.params.toString().contains("begin_session"));
        Assert.assertTrue(end.params.toString().contains("end_session"));
    }

    @Test
    public void session_recoverWithUpdate() throws Exception {
        long beginNs = System.nanoTime() - ly.count.sdk.android.internal.Device.dev.secToNs(config.getSessionCooldownPeriod() * 3);
        SessionImpl session = new SessionImpl(ctx, System.currentTimeMillis() - ly.count.sdk.android.internal.Device.dev.secToMs(config.getSessionCooldownPeriod() * 3));
        session.begin(beginNs);
        session.update(System.nanoTime() - ly.count.sdk.android.internal.Device.dev.secToNs(config.getSessionCooldownPeriod() * 2));

        Thread.sleep(300);

        session = Storage.read(ctx, session);
        Assert.assertNotNull(session);

        session.recover(config);
        Thread.sleep(300);

        session = Storage.read(ctx, session);
        Assert.assertNull(session);

        List<Long> requests = Storage.list(ctx, Request.getStoragePrefix());
        Assert.assertEquals(3, requests.size());

        Request begin = Storage.read(ctx, new Request(requests.get(0)));
        Request update = Storage.read(ctx, new Request(requests.get(1)));
        Request end = Storage.read(ctx, new Request(requests.get(2)));

        Assert.assertNotNull(begin);
        Assert.assertNotNull(update);
        Assert.assertNotNull(end);
        Assert.assertTrue(begin.params.toString().contains("begin_session"));
        Assert.assertTrue(update.params.toString().contains("session_duration"));
        Assert.assertTrue(end.params.toString().contains("end_session"));
    }

    @Test
    public void session_recoversNothingWithEnd() {
        SessionImpl session = new SessionImpl(ctx);
        session.begin(System.nanoTime() - ly.count.sdk.android.internal.Device.dev.secToNs(20));
        session.update(System.nanoTime() - ly.count.sdk.android.internal.Device.dev.secToNs(10));
        session.end();

        Storage.await();

        Assert.assertNull(Storage.read(ctx, session));

        session.recover(config);
        Storage.await();
        Assert.assertNull(Storage.read(ctx, session));

        List<Long> requests = Storage.list(ctx, Request.getStoragePrefix());
        if (requests.size() > 3) {
            Log.d("" + Storage.read(ctx, new Request(requests.get(0))));
            Log.d("" + Storage.read(ctx, new Request(requests.get(1))));
            Log.d("" + Storage.read(ctx, new Request(requests.get(2))));
            Log.d("" + Storage.read(ctx, new Request(requests.get(3))));
        }
        Assert.assertEquals(3, requests.size());

        Request begin = Storage.read(ctx, new Request(requests.get(0)));
        Request update = Storage.read(ctx, new Request(requests.get(1)));
        Request end = Storage.read(ctx, new Request(requests.get(2)));

        Assert.assertNotNull(begin);
        Assert.assertNotNull(update);
        Assert.assertNotNull(end);
        Assert.assertTrue(begin.params.toString().contains("begin_session"));
        Assert.assertTrue(update.params.toString().contains("session_duration"));
        Assert.assertTrue(end.params.toString().contains("end_session"));
    }

    @Test
    public void session_crashRecorded() {
        String name = "crashname";
        String[] logs = new String[]{"log1", "log2", "log3"};
        Map<String, String> segments = new HashMap<>();
        segments.put("a", "b");
        segments.put("c", "d");
        SessionImpl session = new SessionImpl(ctx);
        session.begin(System.nanoTime() - ly.count.sdk.android.internal.Device.dev.secToNs(20));
        session.addCrashReport(new IllegalStateException("Illegal state out here"), false, name, segments, logs);
        session.end();

        Storage.await();

        Assert.assertNull(Storage.read(ctx, session));

        List<Long> requests = Storage.list(ctx, Request.getStoragePrefix());
        Assert.assertEquals(3, requests.size());

        List<Long> crashes = Storage.list(ctx, ly.count.sdk.android.internal.CrashImpl.getStoragePrefix());
        Assert.assertEquals(0, crashes.size());

        Request request = Storage.read(ctx, new Request(requests.get(1)));
        Assert.assertNotNull(request);

        String json = request.params.get("crash");
        Log.d(json);
        Assert.assertTrue(json.contains("\"_nonfatal\":true"));
        Assert.assertTrue(json.contains("\"_name\":\"" + name + "\""));
        Assert.assertTrue(json.contains("\"_logs\":\"" + logs[0] + "\\n" + logs[1] + "\\n" + logs[2] + "\""));
        Assert.assertTrue(json.contains("\"_custom\":" + new JSONObject(segments).toString() + ""));
        Assert.assertTrue(json.contains("IllegalStateException"));
        Assert.assertTrue(json.contains("Illegal state out here"));
        Assert.assertTrue(json.contains("session_crashRecorded"));
    }

    @Test
    public void session_crashRecordedWithLegacyData() throws Exception {
        Countly.init(application(), defaultConfig());

        String[] logs = new String[]{"log1", "log2", "log3"};
        Map<String, String> segments = new HashMap<>();
        segments.put("a", "b");
        segments.put("c", "d");
        Countly.sharedInstance().setCustomCrashSegments(segments);
        Countly.sharedInstance().addCrashLog(logs[0]);
        Countly.sharedInstance().addCrashLog(logs[1]);
        Countly.sharedInstance().addCrashLog(logs[2]);

        SessionImpl session = new SessionImpl(ctx);
        session.begin(System.nanoTime() - ly.count.sdk.android.internal.Device.dev.secToNs(20));
        session.addCrashReport(new IllegalStateException("Illegal state out here"), true);
        session.end();

        Storage.await();

        Assert.assertNull(Storage.read(ctx, session));

        List<Long> requests = Storage.list(ctx, Request.getStoragePrefix());
        Assert.assertEquals(3, requests.size());

        List<Long> crashes = Storage.list(ctx, ly.count.sdk.android.internal.CrashImpl.getStoragePrefix());
        Assert.assertEquals(0, crashes.size());

        Request request = Storage.read(ctx, new Request(requests.get(1)));
        Assert.assertNotNull(request);

        String json = request.params.get("crash");
        Log.d(json);
        Assert.assertTrue(json.contains("\"_nonfatal\":false"));
        Assert.assertTrue(json.contains("\"_logs\":\"" + logs[0] + "\\n" + logs[1] + "\\n" + logs[2] + "\""));
        Assert.assertTrue(json.contains("\"_custom\":" + new JSONObject(segments).toString() + ""));
        Assert.assertTrue(json.contains("IllegalStateException"));
        Assert.assertTrue(json.contains("Illegal state out here"));
        Assert.assertTrue(json.contains("session_crashRecorded"));
    }

    public static class CrashProcessorImpl implements CrashProcessor {

        @Override
        public Crash process(Crash crash) {
            crash.setName("crashname");
            crash.setLogs(new String[]{"log1", "log2", "log3"});

            Map<String, String> segments = new HashMap<>();
            segments.put("a", "b");
            segments.put("c", "d");
            crash.setSegments(segments);
            return crash;
        }
    }
    @Test
    public void session_crashRecordedWithCustomProcessor() throws Exception {
        String name = "crashname";
        String[] logs = new String[]{"log1", "log2", "log3"};
        Map<String, String> segments = new HashMap<>();
        segments.put("a", "b");
        segments.put("c", "d");

        SessionImpl session = new SessionImpl(ctx);
        session.begin(System.nanoTime() - Device.dev.secToNs(20));
        session.addCrashReport(new IllegalStateException("Illegal state out here"), true);
        session.end();

        Storage.await();

        Assert.assertNull(Storage.read(ctx, session));

        List<Long> requests = Storage.list(ctx, Request.getStoragePrefix());
        Assert.assertEquals(3, requests.size());

        List<Long> crashes = Storage.list(ctx, ly.count.sdk.android.internal.CrashImpl.getStoragePrefix());
        Assert.assertEquals(0, crashes.size());

        Request request = Storage.read(ctx, new Request(requests.get(1)));
        Assert.assertNotNull(request);

        String json = request.params.get("crash");
        Log.d(json);
        Assert.assertTrue(json.contains("\"_name\":\"" + name + "\""));
        Assert.assertTrue(json.contains("\"_nonfatal\":false"));
        Assert.assertTrue(json.contains("\"_logs\":\"" + logs[0] + "\\n" + logs[1] + "\\n" + logs[2] + "\""));
        Assert.assertTrue(json.contains("\"_custom\":" + new JSONObject(segments).toString() + ""));
        Assert.assertTrue(json.contains("IllegalStateException"));
        Assert.assertTrue(json.contains("Illegal state out here"));
        Assert.assertTrue(json.contains("session_crashRecorded"));
    }
}