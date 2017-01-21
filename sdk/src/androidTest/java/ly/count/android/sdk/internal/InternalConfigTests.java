package ly.count.android.sdk.internal;

import android.support.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;

import ly.count.android.sdk.Config;

import static ly.count.android.sdk.Config.LoggingLevel.WARN;

@RunWith(AndroidJUnit4.class)
public class InternalConfigTests {
    private String serverUrl = "http://www.serverurl.com";
    private String serverAppKey = "1234";

    @Before
    public void setupEveryTest() throws MalformedURLException {

    }

    @After
    public void cleanupEveryTests() {
    }

    @Test(expected = IllegalStateException.class)
    public void constructor_simple() throws MalformedURLException{
        InternalConfig internalConfig = new InternalConfig(serverUrl, serverAppKey);
    }

    @Test (expected = NullPointerException.class)
    public void constructor_null() throws MalformedURLException{
        InternalConfig internalConfig = new InternalConfig(null);
    }

    @Test
    public void constructor_fromConfig() throws MalformedURLException{
        String sdkVersion = "123";
        String loggingTaq = "abc";

        Config config = new Config(serverUrl, serverAppKey);
        config.enableUsePOST();
        config.enableTestMode();
        config.setSdkVersion(sdkVersion);
        config.setLoggingTag(loggingTaq);
        config.setProgrammaticSessionsControl(true);
        config.setLoggingLevel(WARN);
        config.setFeatures(Config.Feature.Push, Config.Feature.Crash);

        InternalConfig internalConfig = new InternalConfig(config);

        Assert.assertEquals(new URL(serverUrl), internalConfig.getServerURL());
        Assert.assertEquals(serverAppKey, internalConfig.getServerAppKey());
        Assert.assertEquals(true, internalConfig.isUsePOST());
        Assert.assertEquals(true, internalConfig.isTestModeEnabled());
        Assert.assertEquals(sdkVersion, internalConfig.getSdkVersion());
        Assert.assertEquals(loggingTaq, internalConfig.getLoggingTag());
        Assert.assertEquals(true, internalConfig.isProgrammaticSessionsControl());
        Assert.assertEquals(WARN, internalConfig.getLoggingLevel());

        Set<Config.Feature> features = internalConfig.getFeatures();

        Assert.assertEquals(2, features.size());
        Assert.assertEquals(true, features.contains(Config.Feature.Push));
        Assert.assertEquals(true, features.contains(Config.Feature.Crash));
    }
}