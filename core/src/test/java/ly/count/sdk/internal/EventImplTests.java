package ly.count.sdk.internal;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.powermock.reflect.Whitebox;

import java.util.HashMap;
import java.util.Map;

import ly.count.sdk.ConfigCore;
import ly.count.sdk.Event;

import static org.mockito.Mockito.mock;

@RunWith(JUnit4.class)
public class EventImplTests extends BaseTests {
    private CtxCore ctx;
    SDKInterface sdk = mock(SDKInterface.class);
    InternalConfig config;

    @Before
    public void setupEveryTest() throws Exception {
        config = new InternalConfig(new ConfigCore("http://www.serverurl.com", "1234"));
        Log log = new Log();
        log.init(config);
        ctx = new BaseTests.CtxImpl(sdk, config, new Object());
    }

    @Test
    public void constructor(){
        SessionImpl session = new SessionImpl(ctx);
        String key = "key";
        EventImpl event = new EventImpl(session, key);

        Assert.assertEquals(Whitebox.getInternalState(event, "key"), key);
        Assert.assertEquals(Whitebox.getInternalState(event, "count"), 1);
        Assert.assertNull(Whitebox.getInternalState(event, "sum"));
        Assert.assertNull(Whitebox.getInternalState(event, "duration"));
        Assert.assertTrue((long)Whitebox.getInternalState(event, "timestamp") > 0);
        Assert.assertTrue((int)Whitebox.getInternalState(event, "hour") >= 0);
        Assert.assertTrue((int)Whitebox.getInternalState(event, "dow") >= 0);
        Assert.assertNull(Whitebox.getInternalState(event, "segmentation"));
    }

    @Test
    public void constructor_deserialize(){
        SessionImpl session = new SessionImpl(ctx);
        String key = "key";
        EventImpl event = (EventImpl) new EventImpl(session, key)
                .addSegment("key1", "value1")
                .addSegment("key2", "value2")
                .setCount(2)
                .setDuration(3)
                .setSum(4);

        Assert.assertEquals(event, EventImpl.fromJSON(event.toJSON(), session));
    }

    @Test
    public void constructor_throwsIllegalStateExceptionWhenSessionIsNull() {
        EventImpl event = new EventImpl(null, "key");
        Assert.assertTrue((boolean)Whitebox.getInternalState(event, "invalid"));
    }

    @Test
    public void constructor_throwsIllegalStateExceptionWhenKeyIsNull() {
        EventImpl event = new EventImpl(new SessionImpl(ctx), null);
        Assert.assertTrue((boolean)Whitebox.getInternalState(event, "invalid"));
    }

    @Test
    public void constructor_throwsIllegalStateExceptionWhenKeyIsEmpty() {
        EventImpl event = new EventImpl(new SessionImpl(ctx), "");
        Assert.assertTrue((boolean)Whitebox.getInternalState(event, "invalid"));
    }

    @Test
    public void segmentation_throwsIllegalStateExceptionWhenNull() {
        SessionImpl session = new SessionImpl(ctx);

        EventImpl event = new EventImpl(session, "key");
        Assert.assertNull(Whitebox.getInternalState(event, "segmentation"));
        event.addSegment("k", null);
        Assert.assertTrue((boolean)Whitebox.getInternalState(event, "invalid"));
    }

    @Test
    public void segmentation_throwsIllegalStateExceptionWhenValueEmpty() {
        SessionImpl session = new SessionImpl(ctx);

        EventImpl event = new EventImpl(session, "key");
        Assert.assertNull(Whitebox.getInternalState(event, "segmentation"));
        event.addSegment("k", "");
        Assert.assertTrue((boolean)Whitebox.getInternalState(event, "invalid"));
    }

    @Test
    public void segmentation_addsSegment(){
        SessionImpl session = new SessionImpl(ctx);
        String key = "key", k = "k";
        String v = "whatever";


        EventImpl event = new EventImpl(session, key);
        Assert.assertNull(Whitebox.getInternalState(event, "segmentation"));

        event.addSegment(k, v);
        Assert.assertNotNull(Whitebox.getInternalState(event, "segmentation"));
        Assert.assertEquals(1, ((Map)Whitebox.getInternalState(event, "segmentation")).size());
        Assert.assertEquals(v, ((Map)Whitebox.getInternalState(event, "segmentation")).get(k));
    }

    @Test
    public void segmentation_addsSegments(){
        SessionImpl session = new SessionImpl(ctx);
        String key = "key", k1 = "k1", k2 = "k2";
        String v = "whatever";


        EventImpl event = new EventImpl(session, key);
        Assert.assertNull(Whitebox.getInternalState(event, "segmentation"));

        event.addSegments(k1, v, k2, v);
        Assert.assertNotNull(Whitebox.getInternalState(event, "segmentation"));
        Assert.assertEquals(2, ((Map)Whitebox.getInternalState(event, "segmentation")).size());
        Assert.assertEquals(v, ((Map)Whitebox.getInternalState(event, "segmentation")).get(k1));
        Assert.assertEquals(v, ((Map)Whitebox.getInternalState(event, "segmentation")).get(k2));
    }

    @Test
    public void segmentation_setsSegments(){
        SessionImpl session = new SessionImpl(ctx);
        String key = "key", k1 = "k1", k2 = "k2", k3 = "k3";
        String v = "whatever";


        EventImpl event = new EventImpl(session, key);
        Assert.assertNull(Whitebox.getInternalState(event, "segmentation"));

        event.addSegments(k1, v, k2, v);

        Map<String, String> segmentation = new HashMap<>();
        segmentation.put(k3, v);
        event.setSegmentation(segmentation);

        Assert.assertNotNull(Whitebox.getInternalState(event, "segmentation"));
        Assert.assertEquals(1, ((Map)Whitebox.getInternalState(event, "segmentation")).size());
        Assert.assertNull(((Map)Whitebox.getInternalState(event, "segmentation")).get(k1));
        Assert.assertNull(((Map)Whitebox.getInternalState(event, "segmentation")).get(k2));
        Assert.assertEquals(v, ((Map)Whitebox.getInternalState(event, "segmentation")).get(k3));
    }

    @Test
    public void sum_NaN(){
        EventImpl event = (EventImpl)new EventImpl(new SessionImpl(ctx), "key").setSum(Double.NaN);
        Assert.assertTrue((boolean)Whitebox.getInternalState(event, "invalid"));
    }

    @Test
    public void sum_Inf() {
        EventImpl event = (EventImpl)new EventImpl(new SessionImpl(ctx), "key").setSum(Double.NEGATIVE_INFINITY);
        Assert.assertTrue((boolean)Whitebox.getInternalState(event, "invalid"));
    }

    @Test
    public void dur_NaN(){
        EventImpl event = (EventImpl)new EventImpl(new SessionImpl(ctx), "key").setDuration(Double.NaN);
        Assert.assertTrue((boolean)Whitebox.getInternalState(event, "invalid"));
    }

    @Test
    public void dur_Inf() {
        EventImpl event = (EventImpl)new EventImpl(new SessionImpl(ctx), "key").setDuration(Double.NEGATIVE_INFINITY);
        Assert.assertTrue((boolean)Whitebox.getInternalState(event, "invalid"));
    }

    @Test
    public void dur_Neg() {
        EventImpl event = (EventImpl)new EventImpl(new SessionImpl(ctx), "key").setDuration(-2);
        Assert.assertTrue((boolean)Whitebox.getInternalState(event, "invalid"));
    }

    @Test
    public void dur_Inf_invalid() {
        SessionImpl session = new SessionImpl(ctx);

        Event event = new EventImpl(session, "key");
        try {
            event.setDuration(Double.NEGATIVE_INFINITY);
        } catch (IllegalStateException ignored) {}

        event.record();
        Assert.assertEquals(0, session.events.size());
    }

}
