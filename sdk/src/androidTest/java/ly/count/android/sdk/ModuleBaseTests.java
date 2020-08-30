package ly.count.android.sdk;

import android.content.res.Configuration;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.mockito.Mockito.mock;

@RunWith(AndroidJUnit4.class)
public class ModuleBaseTests {
    Countly mCountly;

    @Before
    public void setUp() {
        final CountlyStore countlyStore = new CountlyStore(getContext());
        countlyStore.clear();

        mCountly = new Countly();
        mCountly.init((new CountlyConfig((TestApplication) ApplicationProvider.getApplicationContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting());
    }

    @After
    public void tearDown() {
    }

    //making sure all needed modules are added
    @Test
    public void checkup(){
        Assert.assertEquals(9, mCountly.modules.size());
    }

    //just making sure nothing throws exceptions
    @Test
    public void onConfigurationChanged(){
        mCountly.onConfigurationChanged(null);
    }

    //just making sure nothing throws exceptions
    @Test
    public void onActivityStartStop(){
        mCountly.onStart(null);
        mCountly.onStop();
    }
}
