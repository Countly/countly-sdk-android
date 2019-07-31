package ly.count.sdk.internal;

import android.support.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.reflect.Whitebox;

import ly.count.sdk.android.Config;
import ly.count.sdk.android.internal.BaseTestsAndroid;
import ly.count.sdk.android.internal.Device;
import ly.count.sdk.android.internal.ModuleDeviceId;

import static org.mockito.Mockito.doReturn;

@RunWith(AndroidJUnit4.class)
public class RequestsTests extends BaseTestsAndroid {
    final int paramsAddedByAddCommon = 6;

    //these vals correspond to this time and date: 01/12/2017 @ 1:21pm (UTC), Thursday
    final private long unixTime = 1484227306L;// unix time in milliseconds
    final private long unixTimeSeconds = unixTime / 1000;//unix timestamp in seconds
    final private long unixTimestampDow = 3;//the corresponding day of the unix timestamp
    final private long unixTimestampHour = 13;//the corresponding hour of the unix timestamp

    private ModuleRequests requests;
    private Request request;

    @Test
    public void filler(){

    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        doReturn(Boolean.TRUE).when(utils)._reflectiveClassExists(ModuleDeviceId.ADVERTISING_ID_CLIENT_CLASS_NAME);
        setUpApplication(defaultConfig().setDeviceIdStrategy(Config.DeviceIdStrategy.ADVERTISING_ID).setDeviceIdFallbackAllowed(false));
        requests = module(ModuleRequests.class, false);
    }

    @Test
    public void metrics() throws Exception {
        Params params = Whitebox.getInternalState(ModuleRequests.class, "metrics");
        Assert.assertNotNull(params);
        Assert.assertTrue(params.has("metrics"));

        JSONObject object = new JSONObject(params.get("metrics"));
        Assert.assertEquals(object.get("_device"), Device.dev.getDevice());
        Assert.assertEquals(object.get("_os"), Device.dev.getOS());
        Assert.assertEquals(object.get("_os_version"), Device.dev.getOSVersion());
        Assert.assertEquals(object.get("_carrier"), Device.dev.getCarrier(ctx.getContext()));
        Assert.assertEquals(object.get("_resolution"), Device.dev.getResolution(ctx.getContext()));
        Assert.assertEquals(object.get("_density"), Device.dev.getDensity(ctx.getContext()));
        Assert.assertEquals(object.get("_locale"), Device.dev.getLocale());
        Assert.assertEquals(object.isNull("_app_version"), Device.dev.getAppVersion(ctx) == null);
        Assert.assertEquals(object.get("_store"), Device.dev.getStore(ctx.getContext()));
    }

    @Test
    public void session() throws Exception {
        SessionImpl session = new SessionImpl(ctx);

        session.params.add("test", "value");
        Long now = System.nanoTime();
        session.begin(now - Device.dev.secToNs(123));

        Request request = Storage.readOne(ctx, new Request(0L), true);
        Log.d("read 1st: " + request);
        Assert.assertNotNull(request);
        Assert.assertEquals(request.params.get("session_id"), "" + session.storageId());
        Assert.assertEquals(request.params.get("begin_session"), "1");
        Assert.assertTrue(request.params.toString().contains("&metrics="));
        Assert.assertTrue(request.params.toString().contains("&test=value"));
        Storage.remove(ctx, request);

        session.params.add("testUpdate", "valueUpdate");
        session.event("eve").addSegment("k", "v").setDuration(3).record();
        session.update(now);

        request = Storage.readOne(ctx, new Request(0L), true);
        Log.d("read second: " + request);
        Assert.assertNotNull(request);
        Assert.assertEquals(request.params.get("session_id"), "" + session.storageId());
        Assert.assertEquals(request.params.get("session_duration"), "123");
        Assert.assertFalse(request.params.toString().contains("metrics="));
        Assert.assertTrue(request.params.toString().contains("&testUpdate=valueUpdate"));
        JSONArray events = new JSONArray(request.params.get("events"));
        Assert.assertNotNull(events);
        Assert.assertEquals(events.length(), 1);
        JSONObject event = events.getJSONObject(0);
        Assert.assertNotNull(event);
        Assert.assertEquals(event.getString("key"), "eve");
        Assert.assertEquals(event.getInt("count"), 1);
        Assert.assertEquals(event.getInt("dur"), 3);
        Assert.assertEquals(event.getInt("dow"), Device.dev.currentDayOfWeek());
        Assert.assertEquals(event.getJSONObject("segmentation").get("k"), "v");
        Assert.assertTrue(event.has("timestamp"));
        Assert.assertTrue(event.has("hour"));
        Storage.remove(ctx, request);

        session.params.add("testEnd", "valueEnd");
        config.setDeviceId(new Config.DID(Config.DeviceIdRealm.DEVICE_ID.getIndex(), Config.DeviceIdStrategy.CUSTOM_ID.getIndex(), "devid"));
        session.end(now + Device.dev.secToNs(19), null, null);

        request = Storage.readOne(ctx, new Request(0L), true);
        Assert.assertNotNull(request);
        Assert.assertEquals(request.params.get("session_id"), "" + session.storageId());
        Assert.assertEquals(request.params.get("end_session"), "1");
        Assert.assertEquals(request.params.get("session_duration"), "19");
        Assert.assertFalse(request.params.toString().contains("metrics="));
        Assert.assertTrue(request.params.toString().contains("&testEnd=valueEnd"));
        Assert.assertTrue(request.params.toString().contains("&device_id=devid"));
        Storage.remove(ctx, request);
    }

    @Test
    public void location() {
        double lat = 12.223, lon = 33.992;
        ModuleRequests.location(ctx, lat, lon);
        Request request = Storage.readOne(ctx, new Request(0L), true);
        Assert.assertNotNull(request);
        Assert.assertEquals(request.params.get("location"), lat + "," + lon);
    }

    @Test
    public void nonSessionRequest() {
        Request request = ModuleRequests.nonSessionRequest(ctx);
        Assert.assertNotNull(request);
        Assert.assertFalse(request.params.has("timestamp"));
        Assert.assertFalse(request.params.has("dow"));
        Assert.assertFalse(request.params.has("hour"));
        Assert.assertFalse(request.params.has("tz"));
        Assert.assertFalse(request.params.has("device_id"));

        config.setDeviceId(new Config.DID(Config.DeviceIdRealm.DEVICE_ID.getIndex(), Config.DeviceIdStrategy.CUSTOM_ID.getIndex(), "devid"));
        request = ModuleRequests.nonSessionRequest(ctx);
        Assert.assertNull(ModuleRequests.pushAsync(ctx, request));

        request = ModuleRequests.nonSessionRequest(ctx);
        Assert.assertNotNull(request);

        request.params.add("some", "param");
        Assert.assertNotNull(ModuleRequests.pushAsync(ctx, request));

        Assert.assertTrue(request.params.has("timestamp"));
        Assert.assertTrue(request.params.has("dow"));
        Assert.assertTrue(request.params.has("hour"));
        Assert.assertTrue(request.params.has("tz"));
        Assert.assertEquals(request.params.get("device_id"), "devid");
    }
}