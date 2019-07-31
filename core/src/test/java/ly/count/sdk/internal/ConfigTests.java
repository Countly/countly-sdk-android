package ly.count.sdk.internal;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.net.URL;

@RunWith(JUnit4.class)
public class ConfigTests extends BaseTestsCore {
    private InternalConfig internalConfig;
    private String serverUrl = "http://www.serverurl.com";
    private String serverAppKey = "1234";

    @Before
    public void setUp() throws Exception {
        internalConfig = (InternalConfig)defaultConfigWithLogsForConfigTests();
    }

    @Test
    public void setup_urlAndKey() throws Exception{
        URL url = new URL(serverUrl);
        Assert.assertEquals(serverAppKey, internalConfig.getServerAppKey());
        Assert.assertEquals(url, internalConfig.getServerURL());
    }

    @Test
    public void setUsePost_setAndDeset(){
        Assert.assertFalse(internalConfig.isUsePOST());
        internalConfig.enableUsePOST();
        Assert.assertTrue(internalConfig.isUsePOST());
        internalConfig.setUsePOST(false);
        Assert.assertFalse(internalConfig.isUsePOST());
        internalConfig.setUsePOST(true);
        Assert.assertTrue(internalConfig.isUsePOST());
    }

    @Test
    public void setLoggingTag_default(){
        Assert.assertEquals("Countly", internalConfig.getLoggingTag());
    }

    @Test (expected = IllegalStateException.class)
    public void setLoggingTag_null(){
        internalConfig.setLoggingTag(null);
    }

    @Test (expected = IllegalStateException.class)
    public void setLoggingTag_empty(){
        internalConfig.setLoggingTag("");
    }

    @Test
    public void setLoggingTag_simple(){
        String tagName = "simpleName";
        internalConfig.setLoggingTag(tagName);
        Assert.assertEquals(tagName, internalConfig.getLoggingTag());
    }

    @Test(expected = IllegalStateException.class)
    public void setLoggingLevel_null(){
        internalConfig.setLoggingLevel(null);
        Assert.assertNull(internalConfig.getLoggingLevel());
    }

    @Test(expected = IllegalStateException.class)
    public void sdkName_null(){
        internalConfig.setSdkName(null);
        Assert.assertNull(internalConfig.getSdkName());
    }

    @Test(expected = IllegalStateException.class)
    public void sdkName_empty(){
        internalConfig.setSdkName("");
    }

    @Test
    public void sdkName_setting(){
        String newSdkName = "new-some-name";
        internalConfig.setSdkName(newSdkName);
        Assert.assertEquals(newSdkName, internalConfig.getSdkName());

        newSdkName = "another-name";
        internalConfig.setSdkName(newSdkName);
        Assert.assertEquals(newSdkName, internalConfig.getSdkName());
    }

    @Test(expected = IllegalStateException.class)
    public void sdkVersion_null(){
        internalConfig.setSdkVersion(null);
    }

    @Test(expected = IllegalStateException.class)
    public void sdkVersion_empty(){
        internalConfig.setSdkVersion("");
    }

    @Test
    public void sdkVersion_setting(){
        String versionName = "123";
        internalConfig.setSdkVersion(versionName);
        Assert.assertEquals(versionName, internalConfig.getSdkVersion());

        versionName = "asd";
        internalConfig.setSdkVersion(versionName);
        Assert.assertEquals(versionName, internalConfig.getSdkVersion());
    }

    @Test
    public void programmaticSessionsControl_default(){
        Assert.assertTrue(internalConfig.isAutoSessionsTrackingEnabled());
    }

    @Test
    public void programmaticSessionsControl_enableAndDisable(){
        Assert.assertTrue(internalConfig.isAutoSessionsTrackingEnabled());
        internalConfig.setAutoSessionsTracking(false);
        Assert.assertFalse(internalConfig.isAutoSessionsTrackingEnabled());
    }

    @Test
    public void sendUpdateEachSeconds_default(){
        Assert.assertEquals(30, internalConfig.getSendUpdateEachSeconds());
    }

    @Test
    public void sendUpdateEachSeconds_disable(){
        internalConfig.disableUpdateRequests();
        Assert.assertEquals(0, internalConfig.getSendUpdateEachSeconds());
    }

    @Test
    public void sendUpdateEachSeconds_set(){
        int secondsAmount = 123;
        internalConfig.setSendUpdateEachSeconds(secondsAmount);
        Assert.assertEquals(secondsAmount, internalConfig.getSendUpdateEachSeconds());
    }

    @Test
    public void sendUpdateEachEvents_default(){
        Assert.assertEquals(10, internalConfig.getEventsBufferSize());
    }

    @Test
    public void sendUpdateEachEvents_disable(){
        internalConfig.disableUpdateRequests();
        Assert.assertEquals(0, internalConfig.getSendUpdateEachSeconds());
    }

    @Test
    public void sendUpdateEachEvents_set(){
        int eventsAmount = 123;
        internalConfig.setEventsBufferSize(eventsAmount);
        Assert.assertEquals(eventsAmount, internalConfig.getEventsBufferSize());
    }

    @Test
    public void sdkVersion_default(){
        Assert.assertEquals("19.07", internalConfig.getSdkVersion());
    }

}
