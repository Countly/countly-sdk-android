package ly.count.android.sdk;

import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
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
}