package ly.count.android.sdk.internal;

import android.app.Activity;
import android.app.Application;
import android.content.*;
import android.content.Context;
import android.os.Bundle;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.MalformedURLException;

import ly.count.android.sdk.Config;
import ly.count.android.sdk.Countly;

import static android.support.test.InstrumentationRegistry.getContext;
import static org.mockito.Mockito.RETURNS_SMART_NULLS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;


@RunWith(AndroidJUnit4.class)
public class ContextImplTests {
    @Before
    public void setupEveryTest() throws MalformedURLException{
        Config config = new Config("http://www.serverurl.com", "1234");
        InternalConfig internalConfig = new InternalConfig(config);

        Log log = new Log();
        log.init(internalConfig);
    }

    @After
    public void cleanupEveryTests(){
        validateMockitoUsage();
    }

    @Test
    public void contextImpl_usageWithApplication(){
        Application application = mock(Application.class);
        ContextImpl contextImpl = new ContextImpl(application);

        Assert.assertEquals(application, contextImpl.getApplication());
        Assert.assertEquals(application, contextImpl.getContext());
        Assert.assertEquals(null, contextImpl.getActivity());
    }

    @Test
    public void contextImpl_usageWithActivity(){
        Activity activity = mock(Activity.class);
        ContextImpl contextImpl = new ContextImpl(activity);

        Assert.assertEquals(null, contextImpl.getApplication());
        Assert.assertEquals(activity, contextImpl.getContext());
        Assert.assertEquals(activity, contextImpl.getActivity());
    }

    @Test
    public void contextImpl_usageWithContext(){
        Context context = InstrumentationRegistry.getContext();
        ContextImpl contextImpl = new ContextImpl(context);

        Assert.assertEquals(null, contextImpl.getApplication());
        Assert.assertEquals(null, contextImpl.getActivity());
        Assert.assertEquals(context, contextImpl.getContext());
    }
}
