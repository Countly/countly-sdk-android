package ly.count.android.sdk;


import android.content.Context;
import android.support.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;


@RunWith(AndroidJUnit4.class)
public class ConfigTests {
    private Config config;
    private String serverUrl = "http://www.serverurl.com";
    private String serverAppKey = "1234";

    @Before
    public void setupEveryTest() throws MalformedURLException {
        config = new Config(serverUrl, serverAppKey);
    }

    @After
    public void cleanupEveryTests(){
        config = null;
    }

    @Test (expected = MalformedURLException.class)
    public void setup_malformedUrl() throws MalformedURLException{
        new Config("", "");
    }

    @Test
    public void setup_urlAndKey() throws MalformedURLException{
        URL url = new URL(serverUrl);
        Assert.assertEquals(serverAppKey, config.getServerAppKey());
        Assert.assertEquals(url, config.getServerURL());
    }

    @Test
    public void setUsePost_setAndDeset(){
        Assert.assertEquals(false, config.isUsePOST());
        config.enableUsePOST();
        Assert.assertEquals(true, config.isUsePOST());
        config.setUsePOST(false);
        Assert.assertEquals(false, config.isUsePOST());
        config.setUsePOST(true);
        Assert.assertEquals(true, config.isUsePOST());
    }

    @Test
    public void setLoggingTag_default(){
        Assert.assertEquals("Countly", config.getLoggingTag());
    }

    @Test (expected = IllegalStateException.class)
    public void setLoggingTag_null(){
        config.setLoggingTag(null);
    }

    @Test (expected = IllegalStateException.class)
    public void setLoggingTag_empty(){
        config.setLoggingTag(null);
    }

    @Test
    public void setLoggingTag_simple(){
        String tagName = "simpleName";
        config.setLoggingTag(tagName);
        Assert.assertEquals(tagName, config.getLoggingTag());
    }

    @Test
    public void setLoggingLevel_null(){
        config.setLoggingLevel(null);
        Assert.assertEquals(null, config.getLoggingLevel());
    }

    @Test
    public void setLoggingLevel_allLevels(){
        Assert.assertEquals(Config.LoggingLevel.OFF, config.getLoggingLevel());
        config.setLoggingLevel(Config.LoggingLevel.DEBUG);
        Assert.assertEquals(Config.LoggingLevel.DEBUG, config.getLoggingLevel());
        config.setLoggingLevel(Config.LoggingLevel.INFO);
        Assert.assertEquals(Config.LoggingLevel.INFO, config.getLoggingLevel());
        config.setLoggingLevel(Config.LoggingLevel.WARN);
        Assert.assertEquals(Config.LoggingLevel.WARN, config.getLoggingLevel());
        config.setLoggingLevel(Config.LoggingLevel.ERROR);
        Assert.assertEquals(Config.LoggingLevel.ERROR, config.getLoggingLevel());
        config.setLoggingLevel(Config.LoggingLevel.OFF);
        Assert.assertEquals(Config.LoggingLevel.OFF, config.getLoggingLevel());
    }

    @Test
    public void configTestMode_default(){
        Assert.assertEquals(config.isTestModeEnabled(), false);
    }

    @Test
    public void configTestMode_enabling(){
        Assert.assertEquals(false, config.isTestModeEnabled());
        config.enableTestMode();
        Assert.assertEquals(true, config.isTestModeEnabled());
    }

    @Test
    public void sdkName_default(){
        Assert.assertEquals("java-native-android", config.getSdkName());
    }

    @Test
    public void sdkName_null(){
        config.setSdkName(null);
        Assert.assertEquals(null, config.getSdkName());
    }

    @Test
    public void sdkName_empty(){
        config.setSdkName("");
        Assert.assertEquals("", config.getSdkName());
    }

    @Test
    public void sdkName_setting(){
        String newSdkName = "new-some-name";
        config.setSdkName(newSdkName);
        Assert.assertEquals(newSdkName, config.getSdkName());

        newSdkName = "another-name";
        config.setSdkName(newSdkName);
        Assert.assertEquals(newSdkName, config.getSdkName());
    }

    @Test
    public void sdkVersion_default(){
        Assert.assertEquals("17.04", config.getSdkVersion());
    }

    @Test
    public void sdkVersion_null(){
        config.setSdkVersion(null);
        Assert.assertEquals(null, config.getSdkVersion());
    }

    @Test
    public void sdkVersion_empty(){
        config.setSdkVersion("");
        Assert.assertEquals("", config.getSdkVersion());
    }

    @Test
    public void sdkVersion_setting(){
        String versionName = "123";
        config.setSdkVersion(versionName);
        Assert.assertEquals(versionName, config.getSdkVersion());

        versionName = "asd";
        config.setSdkVersion(versionName);
        Assert.assertEquals(versionName, config.getSdkVersion());
    }

    @Test
    public void programmaticSessionsControl_default(){
        Assert.assertEquals(false, config.isProgrammaticSessionsControl());
    }

    @Test
    public void programmaticSessionsControl_enableAndDisable(){
        Assert.assertEquals(false, config.isProgrammaticSessionsControl());
        config.enableProgrammaticSessionsControl();
        Assert.assertEquals(true, config.isProgrammaticSessionsControl());
        config.setProgrammaticSessionsControl(false);
        Assert.assertEquals(false, config.isProgrammaticSessionsControl());
        config.setProgrammaticSessionsControl(true);
        Assert.assertEquals(true, config.isProgrammaticSessionsControl());
    }

    @Test
    public void sendUpdateEachSeconds_default(){
        Assert.assertEquals(30, config.sendUpdateEachSeconds);
    }

    @Test
    public void sendUpdateEachSeconds_disable(){
        config.disableUpdateRequests();
        Assert.assertEquals(0, config.sendUpdateEachSeconds);
    }

    @Test
    public void sendUpdateEachSeconds_set(){
        int secondsAmount = 123;
        config.setSendUpdateEachSeconds(secondsAmount);
        Assert.assertEquals(secondsAmount, config.sendUpdateEachSeconds);
    }

    @Test
    public void sendUpdateEachEvents_default(){
        Assert.assertEquals(10, config.sendUpdateEachEvents);
    }

    @Test
    public void sendUpdateEachEvents_disable(){
        config.disableUpdateRequests();
        Assert.assertEquals(0, config.sendUpdateEachEvents);
    }

    @Test
    public void sendUpdateEachEvents_set(){
        int eventsAmount = 123;
        config.setSendUpdateEachEvents(eventsAmount);
        Assert.assertEquals(eventsAmount, config.sendUpdateEachEvents);
    }

    @Test
    public void addFeature_null(){
        Set<Config.Feature> features = config.getFeatures();
        Assert.assertEquals(false, features.contains(null));
        Assert.assertEquals(0, features.size());

        config.addFeature(null);
        features = config.getFeatures();
        Assert.assertEquals(true, features.contains(null));
    }

    @Test
    public void addFeature_simple(){
        Set<Config.Feature> features = config.getFeatures();
        Assert.assertEquals(0, features.size());

        config.addFeature(Config.Feature.Crash);
        features = config.getFeatures();
        Assert.assertEquals(1, features.size());
        Assert.assertEquals(true, features.contains(Config.Feature.Crash));

        config.addFeature(Config.Feature.PerformanceMonitoring);
        features = config.getFeatures();
        Assert.assertEquals(2, features.size());
        Assert.assertEquals(true, features.contains(Config.Feature.Crash));
        Assert.assertEquals(true, features.contains(Config.Feature.PerformanceMonitoring));
        Assert.assertEquals(false, features.contains(Config.Feature.Push));
    }

    @Test
    public void setFeature_null(){
        Set<Config.Feature> features = config.getFeatures();
        Assert.assertEquals(false, features.contains(null));
        Assert.assertEquals(0, features.size());

        config.setFeatures(null);
        features = config.getFeatures();
        Assert.assertEquals(false, features.contains(null));
        Assert.assertEquals(0, features.size());
    }

    @Test
    public void setFeature_simple(){
        Set<Config.Feature> features = config.getFeatures();
        Assert.assertEquals(0, features.size());

        config.setFeatures(Config.Feature.Crash, Config.Feature.PerformanceMonitoring);
        features = config.getFeatures();
        Assert.assertEquals(2, features.size());
        Assert.assertEquals(true, features.contains(Config.Feature.Crash));
        Assert.assertEquals(true, features.contains(Config.Feature.PerformanceMonitoring));
        Assert.assertEquals(false, features.contains(Config.Feature.Push));
    }

    @Test
    public void setFeature_overwrite(){
        Set<Config.Feature> features = config.getFeatures();
        Assert.assertEquals(0, features.size());

        config.addFeature(Config.Feature.Push);
        features = config.getFeatures();
        Assert.assertEquals(1, features.size());
        Assert.assertEquals(true, features.contains(Config.Feature.Push));
        Assert.assertEquals(false, features.contains(Config.Feature.Crash));
        Assert.assertEquals(false, features.contains(Config.Feature.PerformanceMonitoring));

        config.setFeatures(Config.Feature.Crash, Config.Feature.PerformanceMonitoring);
        features = config.getFeatures();
        Assert.assertEquals(2, features.size());
        Assert.assertEquals(true, features.contains(Config.Feature.Crash));
        Assert.assertEquals(true, features.contains(Config.Feature.PerformanceMonitoring));
        Assert.assertEquals(false, features.contains(Config.Feature.Push));
    }
}