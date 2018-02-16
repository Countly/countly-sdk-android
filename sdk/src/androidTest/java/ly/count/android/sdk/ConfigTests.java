package ly.count.android.sdk;


import android.support.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.URL;
import java.util.Set;

import ly.count.android.sdk.internal.BaseTests;


@RunWith(AndroidJUnit4.class)
public class ConfigTests extends BaseTests {
    private Config config;
    private String serverUrl = "http://www.serverurl.com";
    private String serverAppKey = "1234";

    @Before
    public void setUp() throws Exception {
        config = defaultConfigWithLogsForConfigTests();
    }

    @Test (expected = IllegalArgumentException.class)
    public void setup_malformedUrl() throws Exception{
        new Config("", "");
    }

    @Test
    public void setup_urlAndKey() throws Exception{
        URL url = new URL(serverUrl);
        Assert.assertEquals(serverAppKey, config.getServerAppKey());
        Assert.assertEquals(url, config.getServerURL());
    }

    @Test
    public void setup_urlWithTrailingSlashAndKey() throws Exception{
        String serverUrlWithTrailing = serverUrl + "/";
        Config config = new Config(serverUrlWithTrailing, serverAppKey);
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
        config.setLoggingTag("");
    }

    @Test
    public void setLoggingTag_simple(){
        String tagName = "simpleName";
        config.setLoggingTag(tagName);
        Assert.assertEquals(tagName, config.getLoggingTag());
    }

    @Test(expected = IllegalStateException.class)
    public void setLoggingLevel_null(){
        config.setLoggingLevel(null);
        Assert.assertEquals(null, config.getLoggingLevel());
    }

    @Test
    public void setLoggingLevel_allLevels() throws Exception {
        Config config = new Config(serverUrl, serverAppKey);
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
    public void configTestMode_default() throws Exception {
        Assert.assertEquals(new Config(serverUrl, serverAppKey).isTestModeEnabled(), false);
    }

    @Test
    public void configTestMode_enabling() throws Exception {
        Config config = new Config(serverUrl, serverAppKey);
        Assert.assertEquals(false, config.isTestModeEnabled());
        config.enableTestMode();
        Assert.assertEquals(true, config.isTestModeEnabled());
    }

    @Test
    public void sdkName_default(){
        Assert.assertEquals("java-native-android", config.getSdkName());
    }

    @Test(expected = IllegalStateException.class)
    public void sdkName_null(){
        config.setSdkName(null);
        Assert.assertEquals(null, config.getSdkName());
    }

    @Test(expected = IllegalStateException.class)
    public void sdkName_empty(){
        config.setSdkName("");
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

    @Test(expected = IllegalStateException.class)
    public void sdkVersion_null(){
        config.setSdkVersion(null);
    }

    @Test(expected = IllegalStateException.class)
    public void sdkVersion_empty(){
        config.setSdkVersion("");
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
        Assert.assertEquals(true, config.isFeatureEnabled(Config.Feature.AutoSessionTracking));
    }

    @Test
    public void programmaticSessionsControl_enableAndDisable(){
        Assert.assertEquals(true, config.isFeatureEnabled(Config.Feature.AutoSessionTracking));
        config.disableFeatures(Config.Feature.AutoSessionTracking);
        Assert.assertEquals(false, config.isFeatureEnabled(Config.Feature.AutoSessionTracking));
        config.enableFeatures(Config.Feature.AutoSessionTracking);
        Assert.assertEquals(true, config.isFeatureEnabled(Config.Feature.AutoSessionTracking));
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

    @Test (expected = IllegalStateException.class)
    public void enableFeatures_null(){
        Set<Config.Feature> features = config.getFeatures();
        Assert.assertEquals(false, features.contains(null));
        Assert.assertEquals(1, features.size());

        config.enableFeatures(null);
    }

    @Test (expected = IllegalStateException.class)
    public void disableFeatures_null(){
        config.disableFeatures(null);
    }

    @Test
    public void addFeature_simple(){
        Set<Config.Feature> features = config.getFeatures();
        Assert.assertEquals(1, features.size());

        config.enableFeatures(Config.Feature.Crash);
        features = config.getFeatures();
        Assert.assertEquals(2, features.size());
        Assert.assertEquals(true, features.contains(Config.Feature.Crash));

        config.enableFeatures(Config.Feature.PerformanceMonitoring);
        features = config.getFeatures();
        Assert.assertEquals(3, features.size());
        Assert.assertEquals(true, features.contains(Config.Feature.Crash));
        Assert.assertEquals(true, features.contains(Config.Feature.PerformanceMonitoring));
        Assert.assertEquals(false, features.contains(Config.Feature.Push));
    }

    @Test
    public void setFeature_null(){
        Set<Config.Feature> features = config.getFeatures();
        Assert.assertEquals(false, features.contains(null));
        Assert.assertEquals(1, features.size());

        Config.Feature[] featureList = null;

        config.setFeatures(featureList);
        features = config.getFeatures();
        Assert.assertEquals(false, features.contains(null));
        Assert.assertEquals(0, features.size());
    }

    @Test
    public void setFeature_simple(){
        Set<Config.Feature> features = config.getFeatures();
        Assert.assertEquals(1, features.size());

        config.setFeatures(Config.Feature.Crash, Config.Feature.PerformanceMonitoring);
        features = config.getFeatures();
        Assert.assertEquals(2, features.size());
        Assert.assertEquals(false, features.contains(Config.Feature.AutoSessionTracking));
        Assert.assertEquals(true, features.contains(Config.Feature.Crash));
        Assert.assertEquals(true, features.contains(Config.Feature.PerformanceMonitoring));
        Assert.assertEquals(false, features.contains(Config.Feature.Push));
    }

    @Test
    public void setFeature_overwrite(){
        Set<Config.Feature> features = config.getFeatures();
        Assert.assertEquals(1, features.size());

        config.enableFeatures(Config.Feature.Push);
        features = config.getFeatures();
        Assert.assertEquals(2, features.size());
        Assert.assertEquals(true, features.contains(Config.Feature.AutoSessionTracking));
        Assert.assertEquals(true, features.contains(Config.Feature.Push));
        Assert.assertEquals(false, features.contains(Config.Feature.Crash));
        Assert.assertEquals(false, features.contains(Config.Feature.PerformanceMonitoring));

        config.setFeatures(Config.Feature.Crash, Config.Feature.PerformanceMonitoring);
        features = config.getFeatures();
        Assert.assertEquals(2, features.size());
        Assert.assertEquals(true, features.contains(Config.Feature.Crash));
        Assert.assertEquals(true, features.contains(Config.Feature.PerformanceMonitoring));
        Assert.assertEquals(false, features.contains(Config.Feature.Push));
        Assert.assertEquals(false, features.contains(Config.Feature.AutoSessionTracking));
    }

    @Test
    public void configVersionSameAsBuildVersion() {
        String buildVersion = ly.count.android.sdk.BuildConfig.VERSION_NAME;
        Assert.assertEquals(buildVersion, config.getSdkVersion());
    }

    @Test
    public void enumFeature_values() {
        Config.Feature[] features = Config.Feature.values();
        Assert.assertEquals(Config.Feature.AutoSessionTracking, features[0]);
        Assert.assertEquals(Config.Feature.Crash, features[1]);
        Assert.assertEquals(Config.Feature.Push, features[2]);
        Assert.assertEquals(Config.Feature.Attribution, features[3]);
        Assert.assertEquals(Config.Feature.StarRating, features[4]);
        Assert.assertEquals(Config.Feature.AutoViewTracking, features[5]);
        Assert.assertEquals(Config.Feature.PerformanceMonitoring, features[6]);
    }

    @Test
    public void enumLoggingLevel_values() {
        Config.LoggingLevel[] loggingLevels = Config.LoggingLevel.values();
        Assert.assertEquals(Config.LoggingLevel.DEBUG, loggingLevels[0]);
        Assert.assertEquals(Config.LoggingLevel.INFO, loggingLevels[1]);
        Assert.assertEquals(Config.LoggingLevel.WARN, loggingLevels[2]);
        Assert.assertEquals(Config.LoggingLevel.ERROR, loggingLevels[3]);
        Assert.assertEquals(Config.LoggingLevel.OFF, loggingLevels[4]);
    }

    @Test
    public void enumFeature_valueOff() {
        Assert.assertEquals(Config.Feature.Crash, Config.Feature.valueOf("Crash"));
        Assert.assertEquals(Config.Feature.Push, Config.Feature.valueOf("Push"));
        Assert.assertEquals(Config.Feature.PerformanceMonitoring, Config.Feature.valueOf("PerformanceMonitoring"));
    }

    @Test
    public void enumLoggingLevel_valueOff() {
        Assert.assertEquals(Config.LoggingLevel.DEBUG, Config.LoggingLevel.valueOf("DEBUG"));
        Assert.assertEquals(Config.LoggingLevel.INFO, Config.LoggingLevel.valueOf("INFO"));
        Assert.assertEquals(Config.LoggingLevel.WARN, Config.LoggingLevel.valueOf("WARN"));
        Assert.assertEquals(Config.LoggingLevel.ERROR, Config.LoggingLevel.valueOf("ERROR"));
        Assert.assertEquals(Config.LoggingLevel.OFF, Config.LoggingLevel.valueOf("OFF"));
    }
}