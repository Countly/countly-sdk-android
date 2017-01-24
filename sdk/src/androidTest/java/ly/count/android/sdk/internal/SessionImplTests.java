package ly.count.android.sdk.internal;

import android.support.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

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

        long diff = session.id - time;
        Assert.assertEquals(true, diff < allowance);
    }

    @Test
    public void constructor_deserialize(){
        long targetID = 11234L;
        SessionImpl session = new SessionImpl(targetID);
        Assert.assertEquals(targetID, (long)session.id);
    }
}