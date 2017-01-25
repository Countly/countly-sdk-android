package ly.count.android.sdk.internal;

import android.support.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.reflect.Whitebox;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Objects;

import ly.count.android.sdk.Config;

@RunWith(AndroidJUnit4.class)
public class RequestsTests {
    final int paramsAddedByAddCommon = 6;

    final private String url = "https://www.google.com";
    final private String apiKey = "1234";

    //these vals correspond to this time and date: 01/12/2017 @ 1:21pm (UTC), Thursday
    final private long unixTime = 1484227306L;// unix time in milliseconds
    final private long unixTimeSeconds = unixTime / 1000;//unix timestamp in seconds
    final private long unixTimestampDow = 3;//the corresponding day of the unix timestamp
    final private long unixTimestampHour = 13;//the corresponding hour of the unix timestamp

    private Config config;
    private InternalConfig internalConfig;

    @Before
    public void setupEveryTest() throws MalformedURLException{
        config = new Config(url, apiKey);
        internalConfig = new InternalConfig(config);
    }

    @After
    public void cleanupEveryTests(){
    }

    @Test (expected = NullPointerException.class)
    public void addCommon_null() throws Exception{
        Whitebox.<Request> invokeMethod(Requests.class, "addCommon", new Class<?>[]{InternalConfig.class, long.class, Request.class}, new Object[]{null, 0, null});
    }

    private static Request addCommon(InternalConfig config, long ms, Request request) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(ms);

        request.params.add("app_key", config.getServerAppKey())
                .add("timestamp", (int)(ms / 1000L))
                .add("hour", calendar.get(Calendar.HOUR_OF_DAY))
                //.add("dow", dow(calendar))
                .add("sdk_name", config.getSdkName())
                .add("sdk_version", config.getSdkVersion());
        return request;
    }

    @Test
    public void addCommon_returnsSameObject() throws Exception{
        String initialParams = "aasdfg=123";
        Request request = Whitebox.invokeConstructor(Request.class, initialParams);
        Request returnedRequest = Whitebox.<Request> invokeMethod(Requests.class, "addCommon", internalConfig, unixTime, request);
        Assert.assertSame(request, returnedRequest);
    }

    @Test
    public void addCommon_addsCorrectFields() throws Exception{
        Request request = Whitebox.invokeConstructor(Request.class, "");
        Whitebox.<Request> invokeMethod(Requests.class, "addCommon", internalConfig, unixTime, request);
        String[] paramsParts = request.params.toString().split("&");
        List<String> paramsKeys = new ArrayList<>();

        for (String part: paramsParts) {
            String[] parts = part.split("=");
            paramsKeys.add(parts[0]);
        }

        Assert.assertEquals(true, paramsKeys.contains("app_key"));
        Assert.assertEquals(true, paramsKeys.contains("timestamp"));
        Assert.assertEquals(true, paramsKeys.contains("hour"));
        Assert.assertEquals(true, paramsKeys.contains("dow"));
        Assert.assertEquals(true, paramsKeys.contains("sdk_name"));
        Assert.assertEquals(true, paramsKeys.contains("sdk_version"));
    }

    @Test
    public void addCommon_simple() throws Exception{
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(unixTime);

        String initialParams = "aasdfg=123";
        Request request = Whitebox.invokeConstructor(Request.class, initialParams);
        Whitebox.<Request> invokeMethod(Requests.class, "addCommon", internalConfig, unixTime, request);

        String[] paramsParts = request.params.toString().split("&");
        Assert.assertEquals(true, paramsParts[0].equals(initialParams));

        for(int a = 1 ; a < paramsParts.length ; a++) {
            String[] parts = paramsParts[a].split("=");
            String key = parts[0];
            String value = parts[1];

            switch (key){
                case "app_key":
                    Assert.assertEquals(apiKey, value);
                    break;
                case "timestamp":
                    long val = Long.parseLong(value);
                    Assert.assertEquals(unixTimeSeconds, val);
                    break;
                case "hour":
                    String hourString = "" + calendar.get(Calendar.HOUR_OF_DAY);
                    Assert.assertEquals(hourString, value);
                    break;
                case "dow":
                    String dowString = "" + Whitebox.<Integer>invokeMethod(Requests.class, "dow", calendar);
                    Assert.assertEquals(dowString, value);
                    break;
                case "sdk_name":
                    Assert.assertEquals(internalConfig.getSdkName(), value);
                    break;
                case "sdk_version":
                    Assert.assertEquals(internalConfig.getSdkVersion(), value);
                    break;
                default:
                    Assert.fail("unexpected param encountered");
                    break;
            }
        }
    }

    @Test
    public void addCommon_countNoInitialParams() throws Exception{
        Request request = Whitebox.invokeConstructor(Request.class, "");

        Whitebox.<Request> invokeMethod(Requests.class, "addCommon", internalConfig, unixTime, request);
        Assert.assertEquals(paramsAddedByAddCommon, TestingUtilityInternal.countParams(request.params));
    }

    @Test
    public void addCommon_countWithInitialParamsSingle() throws Exception{
        Request request = Whitebox.invokeConstructor(Request.class, "aasdfg=123");
        Assert.assertEquals(1, TestingUtilityInternal.countParams(request.params));

        Whitebox.<Request> invokeMethod(Requests.class, "addCommon", internalConfig, unixTime, request);
        Assert.assertEquals(1 + paramsAddedByAddCommon, TestingUtilityInternal.countParams(request.params));
    }

    @Test
    public void addCommon_countWithInitialParamsMultiple() throws Exception{
        Request request = Whitebox.invokeConstructor(Request.class, "aasdfg=123&rr=12&ff=45");
        Assert.assertEquals(3, TestingUtilityInternal.countParams(request.params));

        Whitebox.<Request> invokeMethod(Requests.class, "addCommon", internalConfig, unixTime, request);
        Assert.assertEquals(3 + paramsAddedByAddCommon, TestingUtilityInternal.countParams(request.params));
    }

    @Test
    public void addCommon_noDuplicateNoInitialParams() throws Exception{
        Request request = Whitebox.invokeConstructor(Request.class, "");
        Whitebox.<Request> invokeMethod(Requests.class, "addCommon", internalConfig, unixTime, request);
        Assert.assertEquals(true, TestingUtilityInternal.noDuplicateKeysInParams(request.params));
    }

    @Test
    public void addCommon_noDuplicateWithInitialParamsSingle() throws Exception{
        Request request = Whitebox.invokeConstructor(Request.class, "aa=43");
        Whitebox.<Request> invokeMethod(Requests.class, "addCommon", internalConfig, unixTime, request);
        Assert.assertEquals(true, TestingUtilityInternal.noDuplicateKeysInParams(request.params));
    }

    @Test
    public void addCommon_noDuplicateWithInitialParamsMultiple() throws Exception{
        Request request = Whitebox.invokeConstructor(Request.class, "a=3&b=4");
        Whitebox.<Request> invokeMethod(Requests.class, "addCommon", internalConfig, unixTime, request);
        Assert.assertEquals(true, TestingUtilityInternal.noDuplicateKeysInParams(request.params));
    }

    @Test
    public void dow_days() throws Exception{
        Calendar calendar = new GregorianCalendar();

        calendar.set(2017, 0, 16);//monday
        Assert.assertEquals(1, (int)Whitebox.<Integer>invokeMethod(Requests.class, "dow", calendar));
        calendar.set(2017, 0, 17);
        Assert.assertEquals(2, (int)Whitebox.<Integer>invokeMethod(Requests.class, "dow", calendar));
        calendar.set(2017, 0, 18);
        Assert.assertEquals(3, (int)Whitebox.<Integer>invokeMethod(Requests.class, "dow", calendar));
        calendar.set(2017, 0, 19);
        Assert.assertEquals(4, (int)Whitebox.<Integer>invokeMethod(Requests.class, "dow", calendar));
        calendar.set(2017, 0, 20);
        Assert.assertEquals(5, (int)Whitebox.<Integer>invokeMethod(Requests.class, "dow", calendar));
        calendar.set(2017, 0, 21);
        Assert.assertEquals(6, (int)Whitebox.<Integer>invokeMethod(Requests.class, "dow", calendar));
        calendar.set(2017, 0, 22);
        Assert.assertEquals(0, (int)Whitebox.<Integer>invokeMethod(Requests.class, "dow", calendar));
    }
}