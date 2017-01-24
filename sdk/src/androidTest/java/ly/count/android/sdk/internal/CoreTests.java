package ly.count.android.sdk.internal;

import android.support.test.runner.AndroidJUnit4;


import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import ly.count.android.sdk.Config;

import static android.support.test.InstrumentationRegistry.getContext;
import static org.mockito.Mockito.validateMockitoUsage;

@RunWith(AndroidJUnit4.class)
public class CoreTests {
    @Before
    public void setupEveryTest(){
        android.content.Context context = getContext();
    }

    @After
    public void cleanupEveryTests(){
        validateMockitoUsage();
    }


    public static void assertConfirmConfig(Config config, Core core){
        TestingUtilityInternal.assertConfigsContainSameData(config, core.config);

    }
}
