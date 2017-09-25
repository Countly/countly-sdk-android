package ly.count.android.sdk.internal;

import android.support.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.MalformedURLException;
import java.net.URL;

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
        Config config = new Config(serverUrl, serverAppKey);
        config.setFeatures(Config.Feature.Push, Config.Feature.Crash);
        config.setLoggingTag("tag");
        config.setLoggingLevel(WARN);
        config.setSdkName("name");
        config.setSdkVersion("version");
        config.enableUsePOST();
        config.setSendUpdateEachSeconds(123);
        config.setSendUpdateEachEvents(222);
        config.setProgrammaticSessionsControl(true);
        config.enableTestMode();

        InternalConfig internalConfig = new InternalConfig(config);

        Assert.assertEquals(new URL(serverUrl), internalConfig.getServerURL());
        Assert.assertEquals(serverAppKey, internalConfig.getServerAppKey());
        Assert.assertEquals(config.getFeatures(), internalConfig.getFeatures());
        Assert.assertEquals(config.getLoggingTag(), internalConfig.getLoggingTag());
        Assert.assertEquals(config.getLoggingLevel(), internalConfig.getLoggingLevel());
        Assert.assertEquals(config.getSdkName(), internalConfig.getSdkName());
        Assert.assertEquals(config.getSdkVersion(), internalConfig.getSdkVersion());
        Assert.assertEquals(config.isUsePOST(), internalConfig.isUsePOST());
        Assert.assertEquals(config.getSendUpdateEachSeconds(), internalConfig.getSendUpdateEachSeconds());
        Assert.assertEquals(config.getSendUpdateEachEvents(), internalConfig.getSendUpdateEachEvents());
        Assert.assertEquals(config.isProgrammaticSessionsControl(), internalConfig.isProgrammaticSessionsControl());
        Assert.assertEquals(config.isTestModeEnabled(), internalConfig.isTestModeEnabled());
    }

    @Test
    public void serialization() throws MalformedURLException{
        Config config = new Config(serverUrl, serverAppKey);
        config.setFeatures(Config.Feature.Push, Config.Feature.Crash);
        config.setLoggingTag("tag");
        config.setLoggingLevel(WARN);
        config.setSdkName("name");
        config.setSdkVersion("version");
        config.enableUsePOST();
        config.setSendUpdateEachSeconds(123);
        config.setSendUpdateEachEvents(222);
        config.setProgrammaticSessionsControl(true);
        config.enableTestMode();

        Config.DID dev = new Config.DID(Config.DeviceIdRealm.DEVICE_ID, Config.DeviceIdStrategy.OPEN_UDID, "openudid");
        Config.DID adv = new Config.DID(Config.DeviceIdRealm.ADVERTISING_ID, Config.DeviceIdStrategy.ADVERTISING_ID, "adid");
        Config.DID ptk = new Config.DID(Config.DeviceIdRealm.FCM_TOKEN, Config.DeviceIdStrategy.INSTANCE_ID, "push token");

        InternalConfig internalConfig = new InternalConfig(config);
        internalConfig.setDeviceId(dev);
        internalConfig.setDeviceId(adv);
        internalConfig.setDeviceId(ptk);
        byte[] data = internalConfig.store();
        Assert.assertNotNull(data);

        internalConfig = new InternalConfig(config);
        Assert.assertTrue(internalConfig.restore(data));

        Assert.assertEquals(new URL(serverUrl), internalConfig.getServerURL());
        Assert.assertEquals(serverAppKey, internalConfig.getServerAppKey());
        Assert.assertEquals(config.getFeatures(), internalConfig.getFeatures());
        Assert.assertEquals(config.getLoggingTag(), internalConfig.getLoggingTag());
        Assert.assertEquals(config.getLoggingLevel(), internalConfig.getLoggingLevel());
        Assert.assertEquals(config.getSdkName(), internalConfig.getSdkName());
        Assert.assertEquals(config.getSdkVersion(), internalConfig.getSdkVersion());
        Assert.assertEquals(config.isUsePOST(), internalConfig.isUsePOST());
        Assert.assertEquals(config.getSendUpdateEachSeconds(), internalConfig.getSendUpdateEachSeconds());
        Assert.assertEquals(config.getSendUpdateEachEvents(), internalConfig.getSendUpdateEachEvents());
        Assert.assertEquals(config.isProgrammaticSessionsControl(), internalConfig.isProgrammaticSessionsControl());
        Assert.assertEquals(config.isTestModeEnabled(), internalConfig.isTestModeEnabled());

        Assert.assertNotNull(internalConfig.getDeviceId());
        Assert.assertNotNull(internalConfig.getDeviceId(Config.DeviceIdRealm.ADVERTISING_ID));
        Assert.assertNotNull(internalConfig.getDeviceId(Config.DeviceIdRealm.FCM_TOKEN));
        Assert.assertEquals(dev, internalConfig.getDeviceId());
        Assert.assertEquals(adv, internalConfig.getDeviceId(Config.DeviceIdRealm.ADVERTISING_ID));
        Assert.assertEquals(ptk, internalConfig.getDeviceId(Config.DeviceIdRealm.FCM_TOKEN));
    }
}