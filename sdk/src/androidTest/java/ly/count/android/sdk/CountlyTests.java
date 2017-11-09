package ly.count.android.sdk;

import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;

import static org.mockito.Mockito.validateMockitoUsage;

@RunWith(AndroidJUnit4.class)
public class CountlyTests {
    private String serverUrl = "http://www.serverurl.com";
    private String serverAppKey = "1234";
    private Config config;

    @Before
    public void setupEveryTest() throws Exception{
        config = new Config(serverUrl, serverAppKey);
    }

    @After
    public void cleanupEveryTests(){
        validateMockitoUsage();
    }
//
//    @Test
//    public void init(){
//        Application application = mock(Application.class);
//        final Core core = mock(Core.class);
//
//        Countly.init(application, config, new Countly.CreationOverride() {
//            @Override
//            public Core createCore(Config config) {
//                return core;
//            }
//        });
//
//        verify(core, times(1)).onContextAcquired(application);
//    }
//
//    @Test
//    public void constructor_core(){
//        Countly cn = new Countly(config);
//
//        CoreTests.assertConfirmConfig(config, Whitebox.<Core>getInternalState(cn, "core"));
//    }
//
//    @Test
//    public void constructor_config(){
//        Core core = new Core(config);
//        Countly cn = new Countly(core);
//        CoreTests.assertConfirmConfig(config, Whitebox.<Core>getInternalState(cn, "core"));
//    }

}
