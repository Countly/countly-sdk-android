package ly.count.android.sdk;

import android.content.Context;
import android.support.test.runner.AndroidJUnit4;


import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.support.test.InstrumentationRegistry.getContext;
import static org.mockito.Mockito.RETURNS_SMART_NULLS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class BasicTest {
    @Before
    public void setupEveryTest(){
        Context context = getContext();
    }

    @After
    public void cleanupEveryTests(){
        validateMockitoUsage();
    }

    @Test
    public void SampleTest(){
        Countly mCountly = mock(Countly.class, RETURNS_SMART_NULLS);
        doReturn(true).when(mCountly).isInitialized();

        mCountly.isInitialized();
        verify(mCountly).isInitialized();
    }
}
