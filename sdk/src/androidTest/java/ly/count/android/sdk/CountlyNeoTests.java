package ly.count.android.sdk;

import android.app.Application;
import android.content.Context;
import android.support.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.reflect.Whitebox;

import java.net.MalformedURLException;
import java.util.List;

import ly.count.android.sdk.internal.Core;
import ly.count.android.sdk.internal.CoreTests;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class CountlyNeoTests {
    private String serverUrl = "http://www.serverurl.com";
    private String serverAppKey = "1234";
    private Config config;

    @Before
    public void setupEveryTest() throws MalformedURLException{
        config = new Config(serverUrl, serverAppKey);
    }

    @After
    public void cleanupEveryTests(){
        validateMockitoUsage();
    }

    @Test
    public void init(){
        Application application = mock(Application.class);
        final Core core = mock(Core.class);

        CountlyNeo.init(application, config, new CountlyNeo.CreationOverride() {
            @Override
            public Core createCore(Config config) {
                return core;
            }
        });

        verify(core, times(1)).onApplicationCreated(application);
    }

    @Test
    public void constructor_core(){
        CountlyNeo cn = new CountlyNeo(config);

        CoreTests.assertConfirmConfig(config, Whitebox.<Core>getInternalState(cn, "core"));
    }

    @Test
    public void constructor_config(){
        Core core = new Core(config);
        CountlyNeo cn = new CountlyNeo(core);
        CoreTests.assertConfirmConfig(config, Whitebox.<Core>getInternalState(cn, "core"));
    }

}
