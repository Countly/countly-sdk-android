package ly.count.android.sdk.internal;

import android.support.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.reflect.Whitebox;

import ly.count.android.sdk.Session;

import static android.support.test.InstrumentationRegistry.getContext;
import static org.mockito.Mockito.validateMockitoUsage;

@RunWith(AndroidJUnit4.class)
public class SessionImplTests {
    @Before
    public void setupEveryTest(){
        android.content.Context context = getContext();
    }

    @After
    public void cleanupEveryTests(){
        validateMockitoUsage();
    }

    @Test
    public void constructor_empty(){
        final long allowance = 1000000000;
        long time = System.nanoTime();
        SessionImpl session = new SessionImpl();

        long diff = session.getId() - time;
        Assert.assertEquals(true, diff < allowance);
    }

    @Test
    public void constructor_deserialize(){
        long targetID = 11234L;
        SessionImpl session = new SessionImpl(targetID);
        Assert.assertEquals(targetID, (long)session.getId());
    }

    @Test
    public void addParams() throws Exception{
        SessionImpl session = new SessionImpl();
        Assert.assertNull(session.params);

        StringBuilder sbParams = new StringBuilder();
        String[] keys = new String[]{"a", "b", "c"};
        String[] vals = new String[]{"11", "22", "33"};

        sbParams.append(keys[0]).append("=").append(vals[0]);
        Whitebox.<Session>invokeMethod(session, "addParam", keys[0], vals[0]);
        Assert.assertEquals(sbParams.toString(), session.params.toString());

        sbParams.append("&").append(keys[1]).append("=").append(vals[1]);
        Whitebox.<Session>invokeMethod(session, "addParam", keys[1], vals[1]);
        Assert.assertEquals(sbParams.toString(), session.params.toString());

        sbParams.append("&").append(keys[2]).append("=").append(vals[2]);
        Whitebox.<Session>invokeMethod(session, "addParam", keys[2], vals[2]);
        Assert.assertEquals(sbParams.toString(), session.params.toString());
    }

    //addParam

    //begin
    //update
    //end

    //updateDuration

    //get began
    //get ended

    //is Leading
}