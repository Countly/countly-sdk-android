package ly.count.sdk.android.internal;

import android.support.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.URL;

import ly.count.sdk.android.Config;
import ly.count.sdk.internal.InternalConfig;

import static ly.count.sdk.Config.LoggingLevel.WARN;

@RunWith(AndroidJUnit4.class)
public class InternalConfigTests {
    private String serverUrl = "http://www.serverurl.com";
    private String serverAppKey = "1234";

    @Test(expected = IllegalStateException.class)
    public void constructor_simple() throws Exception{
        InternalConfig internalConfig = new InternalConfig(serverUrl, serverAppKey);
    }

    @Test (expected = NullPointerException.class)
    public void constructor_null() throws Exception{
        InternalConfig internalConfig = new InternalConfig(null);
    }

    @Test
    public void constructor_fromConfig() throws Exception{
        Config config = new Config(serverUrl, serverAppKey);
        config.setFeatures(Config.Feature.Push, Config.Feature.CrashReporting);
        config.setLoggingTag("tag");
        config.setLoggingLevel(WARN);
        config.setSdkName("name");
        config.setSdkVersion("version");
        config.enableUsePOST();
        config.setSendUpdateEachSeconds(123);
        config.setEventsBufferSize(222);
        config.enableTestMode();
        config.setCrashReportingANRTimeout(1);

        InternalConfig internalConfig = new InternalConfig(config);

        Assert.assertEquals(new URL(serverUrl), internalConfig.getServerURL());
        Assert.assertEquals(serverAppKey, internalConfig.getServerAppKey());
        Assert.assertEquals(config.getFeaturesMap(), internalConfig.getFeatures());
        Assert.assertEquals(config.getLoggingTag(), internalConfig.getLoggingTag());
        Assert.assertEquals(config.getLoggingLevel(), internalConfig.getLoggingLevel());
        Assert.assertEquals(config.getSdkName(), internalConfig.getSdkName());
        Assert.assertEquals(config.getSdkVersion(), internalConfig.getSdkVersion());
        Assert.assertEquals(config.isUsePOST(), internalConfig.isUsePOST());
        Assert.assertEquals(config.getParameterTamperingProtectionSalt(), internalConfig.getParameterTamperingProtectionSalt());
        Assert.assertEquals(config.getNetworkConnectionTimeout(), internalConfig.getNetworkConnectionTimeout());
        Assert.assertEquals(config.getNetworkReadTimeout(), internalConfig.getNetworkReadTimeout());
        Assert.assertEquals(config.getPublicKeyPins(), internalConfig.getPublicKeyPins());
        Assert.assertEquals(config.getCertificatePins(), internalConfig.getCertificatePins());
        Assert.assertEquals(config.getSendUpdateEachSeconds(), internalConfig.getSendUpdateEachSeconds());
        Assert.assertEquals(config.getEventsBufferSize(), internalConfig.getEventsBufferSize());
        Assert.assertEquals(config.isTestModeEnabled(), internalConfig.isTestModeEnabled());
        Assert.assertEquals(config.getCrashReportingANRTimeout(), internalConfig.getCrashReportingANRTimeout());
    }

    @Test
    public void serialization() throws Exception{
        Config config = new Config(serverUrl, serverAppKey);
        config.setFeatures(Config.Feature.Push, Config.Feature.CrashReporting);
        config.setLoggingTag("tag");
        config.setLoggingLevel(WARN);
        config.setSdkName("name");
        config.setSdkVersion("version");
        config.enableUsePOST();
        config.enableParameterTamperingProtection("salt");
        config.setNetworkConnectTimeout(11);
        config.setNetworkReadTimeout(22);
        config.addPublicKeyPin("pk-pin").addPublicKeyPin("pk-pin-2");
        config.addCertificatePin("cert-pin");
        config.setSendUpdateEachSeconds(123);
        config.setEventsBufferSize(222);
        config.setCrashReportingANRTimeout(2);
        config.enableTestMode();

        Config.DID dev = new Config.DID(Config.DeviceIdRealm.DEVICE_ID.getIndex(), Config.DeviceIdStrategy.ANDROID_ID.getIndex(), "openudid");
        Config.DID adv = new Config.DID(Config.DeviceIdRealm.ADVERTISING_ID.getIndex(), Config.DeviceIdStrategy.ADVERTISING_ID.getIndex(), "adid");
        Config.DID ptk = new Config.DID(Config.DeviceIdRealm.FCM_TOKEN.getIndex(), Config.DeviceIdStrategy.INSTANCE_ID.getIndex(), "push token");

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
        Assert.assertEquals(config.getFeaturesMap(), internalConfig.getFeatures());
        Assert.assertEquals(config.getLoggingTag(), internalConfig.getLoggingTag());
        Assert.assertEquals(config.getLoggingLevel(), internalConfig.getLoggingLevel());
        Assert.assertEquals(config.getSdkName(), internalConfig.getSdkName());
        Assert.assertEquals(config.getSdkVersion(), internalConfig.getSdkVersion());
        Assert.assertEquals(config.isUsePOST(), internalConfig.isUsePOST());
        Assert.assertEquals(config.getParameterTamperingProtectionSalt(), internalConfig.getParameterTamperingProtectionSalt());
        Assert.assertEquals(config.getNetworkConnectionTimeout(), internalConfig.getNetworkConnectionTimeout());
        Assert.assertEquals(config.getNetworkReadTimeout(), internalConfig.getNetworkReadTimeout());
        Assert.assertEquals(config.getPublicKeyPins(), internalConfig.getPublicKeyPins());
        Assert.assertEquals(config.getCertificatePins(), internalConfig.getCertificatePins());
        Assert.assertEquals(config.getSendUpdateEachSeconds(), internalConfig.getSendUpdateEachSeconds());
        Assert.assertEquals(config.getEventsBufferSize(), internalConfig.getEventsBufferSize());
        Assert.assertEquals(config.isTestModeEnabled(), internalConfig.isTestModeEnabled());
        Assert.assertEquals(config.getCrashReportingANRTimeout(), internalConfig.getCrashReportingANRTimeout());

        Assert.assertNotNull(internalConfig.getDeviceId());
        Assert.assertNotNull(internalConfig.getDeviceId(Config.DeviceIdRealm.ADVERTISING_ID.getIndex()));
        Assert.assertNotNull(internalConfig.getDeviceId(Config.DeviceIdRealm.FCM_TOKEN.getIndex()));
        Assert.assertEquals(dev, internalConfig.getDeviceId());
        Assert.assertEquals(adv, internalConfig.getDeviceId(Config.DeviceIdRealm.ADVERTISING_ID.getIndex()));
        Assert.assertEquals(ptk, internalConfig.getDeviceId(Config.DeviceIdRealm.FCM_TOKEN.getIndex()));
    }
}