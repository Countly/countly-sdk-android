package ly.count.sdk.android;

import org.junit.After;
import org.junit.Before;

import ly.count.sdk.ConfigCore;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.validateMockitoUsage;

//@RunWith(AndroidJUnit4.class)
public class CountlyTests {
    private String serverUrl = "http://www.serverurl.com";
    private String serverAppKey = "1234";
    private ConfigCore config;

    @Before
    public void setupEveryTest() throws Exception{
        config = new ConfigCore(serverUrl, serverAppKey);
    }

    @After
    public void cleanupEveryTests(){
        validateMockitoUsage();
    }

/*
    @Test
    public void init(){
        Application application = mock(Application.class);
        final Core sdk = mock(Core.class);

        Cly.init(application, config, new Cly.CreationOverride() {
            @Override
            public Core createCore(ConfigCore config) {
                return sdk;
            }
        });

        verify(sdk, times(1)).onContextAcquired(application);
    }

    @Test
    public void constructor_core(){
        Cly cn = new Cly(config);

        SDKTests.assertConfirmConfig(config, Whitebox.<Core>getInternalState(cn, "sdk"));
    }

    @Test
    public void constructor_config(){
        Core sdk = new Core(config);
        Cly cn = new Cly(sdk);
        SDKTests.assertConfirmConfig(config, Whitebox.<Core>getInternalState(cn, "sdk"));
    }
*/
}
