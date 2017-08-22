package ly.count.android.sdk.internal;

import android.support.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.reflect.Whitebox;

import java.net.MalformedURLException;
import java.net.URL;

@RunWith(AndroidJUnit4.class)
public class RequestTests {
    private final int[] testResultCodes = new int[] {-1, 50, 99, 100, 101, 199, 200, 201, 299, 300, 301, 399, 400, 401};
    private final int GET_LIMIT = 1023;

    private final String urlString = "http://www.google.com";
    private URL url;
    private StringBuilder deviceIDsb = new StringBuilder("");
    private final String paramsEmpty = "";
    private final String paramsFilled = "a=123&r=456&i=890";

    private final String deviceID_1 = "somelongdeviceidthathasnonumbers123ilied";

    @Before
    public void setupEveryTest() throws MalformedURLException{
        url = new URL(urlString);
    }

    @After
    public void cleanupEveryTests(){
    }

//    @Test
//    public void isSuccess() throws Exception{
//        Request request = Whitebox.invokeConstructor(Request.class, "a=1");
//        boolean[] results = new boolean[] {false, false, false, false, false, false, true, true, true, false, false, false, false, false};
//
//        for (int a = 0 ; a < results.length ; a++){
//            request.code = testResultCodes[a];
//            Assert.assertEquals(results[a], request.isSuccess());
//        }
//    }
//
//    @Test
//    public void isError() throws Exception{
//        Request request = Whitebox.invokeConstructor(Request.class, "a=1");
//        boolean[] results = new boolean[] {false, true, true, true, true, true, false, false, false, true, true, true, true, true};
//
//        for (int a = 0 ; a < results.length ; a++){
//            request.code = testResultCodes[a];
//            Assert.assertEquals(results[a], request.isError());
//        }
//    }
//
//    @Test
//    public void isSent() throws Exception{
//        Request request = Whitebox.invokeConstructor(Request.class, "a=1");
//        boolean[] results = new boolean[] {false, true, true, true, true, true, true, true, true, true, true, true, true, true};
//
//        for (int a = 0 ; a < results.length ; a++){
//            request.code = testResultCodes[a];
//            Assert.assertEquals(results[a], request.isSent());
//        }
//    }

    @Test
    public void request_constructorString() throws Exception{
        String paramVals = "a=1&b=2";
        Params params = new Params(paramVals);

        Request request = Whitebox.invokeConstructor(Request.class, paramVals);
        Params requestParams = request.params;
        Assert.assertEquals(params.toString(), requestParams.toString());
    }

    @Test
    public void request_constructorObjectsNull() throws Exception{
        String[] paramsVals = new String[] {"asd", "123"};
        Object[] vals = new Object[]{new Object[]{paramsVals[0], paramsVals[1]}};
        Request request = Whitebox.invokeConstructor(Request.class, vals);
        Assert.assertEquals(paramsVals[0] + "=" + paramsVals[1], request.params.toString());
    }

    @Test
    public void request_constructorObjects() throws Exception{
        String[] paramsParts = new String[] {"abc", "123", "qwe", "456"};
        String paramVals = paramsParts[0] + "=" + paramsParts[1] + "&" + paramsParts[2] + "=" + paramsParts[3];
        Params params = new Params(paramVals);

        Request request = Whitebox.invokeConstructor(Request.class, paramsParts[0], paramsParts[1], paramsParts[2], paramsParts[3]);
        Params requestParams = request.params;
        Assert.assertEquals(params.toString(), requestParams.toString());
    }

    @Test
    public void request_build(){
        String[] paramsParts = new String[] {"abc", "123", "qwe", "456"};
        String paramVals = paramsParts[0] + "=" + paramsParts[1] + "&" + paramsParts[2] + "=" + paramsParts[3];
        Params params = new Params(paramVals);

        Request request = Request.build(paramsParts[0], paramsParts[1], paramsParts[2], paramsParts[3]);
        Params requestParams = request.params;
        Assert.assertEquals(params.toString(), requestParams.toString());
    }

//    @Test
//    public void request_serialize() throws Exception{
//        String paramVals = "a=1&b=2";
//        Request request = Whitebox.invokeConstructor(Request.class, paramVals);
//
//        String manualSerialization = paramVals + Whitebox.<String>getInternalState(Request.class, "EOR");
//        String serializationRes = request.serialize();
//        Assert.assertEquals(manualSerialization, serializationRes);
//    }
//
//    @Test
//    public void request_loadSimple() throws Exception{
//        String paramVals = "a=1&b=2";
//        Request request = Whitebox.invokeConstructor(Request.class, paramVals);
//
//        String serializationRes = request.serialize();
//        Request requestNew = Request.load(serializationRes);
//
//        Assert.assertNotNull("Could not deserialize request", requestNew);
//        Assert.assertEquals(paramVals, requestNew.params.toString());
//    }
//
//    @Test
//    public void request_loadEmpty() {
//        Request requestNew = Request.load("");
//        Assert.assertNull(requestNew);
//    }
//
//    @Test (expected = NullPointerException.class)
//    public void request_loadNull() {
//        Request.load(null);
//    }


    @Test
    public void isGettable_ParamsEmptyUnderLimit() throws MalformedURLException, Exception{
        Request request = Whitebox.invokeConstructor(Request.class, "");
        Assert.assertEquals(true, request.isGettable(url, deviceID_1));
    }

    @Test
    public void isGettable_ParamsFilledAboveLimitLarge() throws MalformedURLException, Exception{
        StringBuilder sbParams = new StringBuilder();

        for(int a = 0 ; a < 1000 ; a++) {
            if(a != 0) sbParams.append("&");
            sbParams.append("qq").append(a);
            sbParams.append("=").append(a);
        }

        Request request = Whitebox.invokeConstructor(Request.class, sbParams.toString());

        Assert.assertEquals(false, request.isGettable(url, deviceID_1));
    }

    @Test
    public void isGettable_ParamsEmptyTestLimit() throws MalformedURLException, Exception{
        TestingGettableLimit(paramsEmpty, urlString, url, 0, deviceIDsb);
    }

    @Test
    public void isGettable_ParamsEmptyTestLimitAddition() throws MalformedURLException, Exception{
        TestingGettableLimit(paramsEmpty, urlString, url, 234, deviceIDsb);
    }

    @Test
    public void isGettable_ParamsFilledTestLimit() throws MalformedURLException, Exception{
        TestingGettableLimit(paramsFilled, urlString, url, 0, deviceIDsb);
    }

    @Test
    public void isGettable_ParamsFilledTestLimitAddition() throws MalformedURLException, Exception{
        TestingGettableLimit(paramsFilled, urlString, url, 123, deviceIDsb);
    }

    private void TestingGettableLimit(String params, String givenUrlString, URL givenUrl, int addition, StringBuilder sb) throws Exception{
        Request request = Whitebox.invokeConstructor(Request.class, params);

        int theLength = givenUrlString.length() + 2 + params.length() + Whitebox.<String>getInternalState(Request.class, "P_DEVICE_ID").length() + addition;
        int neededLength = GET_LIMIT - theLength;

        for(int a = 0 ; a < neededLength ; a++) {
            sb.append("a");
        }

        //should be right at the limit
        Assert.assertEquals(true, request.isGettable(givenUrl, sb.toString(), addition));

        //should be just over limit
        sb.append("d");
        Assert.assertEquals(false, request.isGettable(givenUrl, sb.toString(), addition));
    }

//    @Test
//    public void isGettable_aboveLimit() {
//        Request requestNew = Request.load("");
//
//    }
}