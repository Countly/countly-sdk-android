package ly.count.sdk.android.internal;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import ly.count.sdk.Config;
import ly.count.sdk.internal.InternalConfig;
import ly.count.sdk.internal.Log;

import static android.support.test.InstrumentationRegistry.getContext;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.validateMockitoUsage;


@RunWith(AndroidJUnit4.class)
public class CtxImplTests {
    Application application = mock(Application.class);
    Activity activity = mock(Activity.class);
    SDK sdk = mock(SDK.class);
    InternalConfig config;

    @Before
    public void setupEveryTest() throws Exception {
        config = new InternalConfig(new Config("http://www.serverurl.com", "1234"));

        Log log = new Log();
        log.init(config);
    }

    @After
    public void cleanupEveryTests(){
        validateMockitoUsage();
    }

    @Test
    public void contextImpl_usageWithApplication(){
        doReturn(getContext()).when(application).getApplicationContext();
        CtxImpl contextImpl = new CtxImpl(sdk, config, application);

        Assert.assertEquals(application, contextImpl.getApplication());
        Assert.assertEquals(application, contextImpl.getContext());
        Assert.assertNull(contextImpl.getActivity());

        contextImpl.expire();

        Assert.assertNull(contextImpl.getApplication());
        Assert.assertNull(contextImpl.getActivity());
        Assert.assertEquals(getContext(), contextImpl.getContext());
    }

    @Test
    public void contextImpl_usageWithActivity(){
        doReturn(getContext()).when(activity).getApplicationContext();
        CtxImpl contextImpl = new CtxImpl(sdk, config, activity);

        Assert.assertNull(contextImpl.getApplication());
        Assert.assertEquals(activity, contextImpl.getContext());
        Assert.assertEquals(activity, contextImpl.getActivity());

        contextImpl.expire();

        Assert.assertNull(contextImpl.getApplication());
        Assert.assertNull(contextImpl.getActivity());
        Assert.assertEquals(getContext(), contextImpl.getContext());
    }

    @Test
    public void contextImpl_usageWithContext(){
        Context context = InstrumentationRegistry.getContext();
        CtxImpl contextImpl = new CtxImpl(sdk, config, context);

        Assert.assertNull(contextImpl.getApplication());
        Assert.assertNull(contextImpl.getActivity());
        Assert.assertEquals(context, contextImpl.getContext());

        contextImpl.expire();

        Assert.assertNull(contextImpl.getApplication());
        Assert.assertNull(contextImpl.getActivity());
        Assert.assertEquals(context.getApplicationContext(), contextImpl.getContext());
    }
}
