package ly.count.sdk.android.internal;

import android.support.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.reflect.Whitebox;

import java.net.URL;

import ly.count.sdk.internal.Params;
import ly.count.sdk.internal.Request;

@RunWith(AndroidJUnit4.class)
public class RequestTests {
    private final String urlString = "http://www.google.com";
    private URL url;

    @Before
    public void setupEveryTest() throws Exception{
        url = new URL(urlString);
    }

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
/*
    @Test
    public void request_build(){
        String[] paramsParts = new String[] {"abc", "123", "qwe", "456"};
        String paramVals = paramsParts[0] + "=" + paramsParts[1] + "&" + paramsParts[2] + "=" + paramsParts[3];
        Params params = new Params(paramVals);

        Request request = Request.build(paramsParts[0], paramsParts[1], paramsParts[2], paramsParts[3]);
        Params requestParams = request.params;
        Assert.assertEquals(params.toString(), requestParams.toString());
    }

    @Test
    public void request_serialize() throws Exception{
        String paramVals = "a=1&b=2";
        Request request = Whitebox.invokeConstructor(Request.class, paramVals);

        String manualSerialization = paramVals + Whitebox.<String>getInternalState(Request.class, "EOR");
        String serializationRes = new String(request.store());
        Assert.assertEquals(manualSerialization, serializationRes);
    }

    @Test
    public void request_loadSimple() throws Exception{
        String paramVals = "a=1&b=2";
        Request request = Whitebox.invokeConstructor(Request.class, paramVals);

        byte[] serializationRes = request.store();
        Request requestNew = new Request();

        Assert.assertTrue(requestNew.restore(serializationRes));
        Assert.assertEquals(paramVals, requestNew.params.toString());
    }

    @Test
    public void request_loadEmpty() {
        Assert.assertFalse(new Request().restore("".getBytes()));
        Assert.assertFalse(new Request().restore("a=1&b=2".getBytes()));
    }

    @Test (expected = NullPointerException.class)
    public void request_loadNull() {
        new Request().restore(null);
    }


    @Test
    public void isGettable_ParamsEmptyUnderLimit() throws Exception, Exception{
        Request request = Whitebox.invokeConstructor(Request.class, "");
        Assert.assertEquals(true, request.isGettable(url, 0));
    }

    @Test
    public void isGettable_ParamsFilledAboveLimitLarge() throws Exception, Exception{
        StringBuilder sbParams = new StringBuilder();

        for(int a = 0 ; a < 1000 ; a++) {
            if(a != 0) sbParams.append("&");
            sbParams.append("qq").append(a);
            sbParams.append("=").append(a);
        }

        Request request = Whitebox.invokeConstructor(Request.class, sbParams.toString());

        Assert.assertEquals(false, request.isGettable(url, 0));
    }
    */
}