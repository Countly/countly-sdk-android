package ly.count.sdk.android;


import android.support.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.URL;
import java.util.Set;

import ly.count.sdk.android.internal.BaseTests;
import ly.count.sdk.internal.InternalConfig;


@RunWith(AndroidJUnit4.class)
public class ConfigTests extends BaseTests {
    private InternalConfig internalConfig;
    private Config config;
    private String serverUrl = "http://www.serverurl.com";
    private String serverAppKey = "1234";

    @Before
    public void setUp() throws Exception {
        internalConfig = defaultConfigWithLogsForConfigTests();
        config = defaultConfig();
    }

    @Override
    protected Config defaultConfig() throws Exception {
        return new Config(serverUrl, serverAppKey).enableTestMode();
    }

    @Test (expected = IllegalArgumentException.class)
    public void setup_malformedUrl() throws Exception{
        new Config("", "");
    }

    @Test
    public void setup_urlAndKey() throws Exception{
        URL url = new URL(serverUrl);
        Assert.assertEquals(serverAppKey, internalConfig.getServerAppKey());
        Assert.assertEquals(url, internalConfig.getServerURL());
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
        Assert.assertFalse(new Config(serverUrl, serverAppKey).isTestModeEnabled());
    }

    @Test
    public void configTestMode_enabling() throws Exception {
        Config config = new Config(serverUrl, serverAppKey);
        Assert.assertFalse(config.isTestModeEnabled());
        config.enableTestMode();
        Assert.assertTrue(config.isTestModeEnabled());
    }

    @Test
    public void sdkName_default(){
        Assert.assertEquals("java-native-android", internalConfig.getSdkName());
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

    @Test
    public void sdkVersion_default(){
        Assert.assertEquals("19.01-sdk2-pre-rc2", internalConfig.getSdkVersion());
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
    public void enableFeatures_null(){
        Assert.assertTrue(config.isFeatureEnabled(Config.Feature.Events));
        Assert.assertTrue(config.isFeatureEnabled(Config.Feature.Sessions));
        Assert.assertTrue(config.isFeatureEnabled(Config.Feature.CrashReporting));
        Assert.assertTrue(config.isFeatureEnabled(Config.Feature.Location));
        Assert.assertTrue(config.isFeatureEnabled(Config.Feature.UserProfiles));
        Assert.assertFalse(config.isFeatureEnabled(Config.Feature.Push));
        Assert.assertFalse(config.isFeatureEnabled(Config.Feature.Views));
    }

    @Test (expected = IllegalStateException.class)
    public void disableFeatures_nullFeature(){
        config.disableFeatures((Config.Feature)null);
    }

    @Test (expected = IllegalStateException.class)
    public void disableFeatures_nullFeatures(){
        config.disableFeatures((Config.Feature[])null);
    }

    @Test
    public void addFeature_simple(){
        Set<Config.Feature> features = config.getFeatures();
        Assert.assertEquals(5, features.size());

        config.enableFeatures(Config.Feature.Views);
        features = config.getFeatures();
        Assert.assertEquals(6, features.size());
        Assert.assertTrue(features.contains(Config.Feature.Views));

        config.enableFeatures(Config.Feature.PerformanceMonitoring);
        features = config.getFeatures();
        Assert.assertEquals(7, features.size());
        Assert.assertTrue(features.contains(Config.Feature.Views));
        Assert.assertTrue(features.contains(Config.Feature.PerformanceMonitoring));
        Assert.assertFalse(features.contains(Config.Feature.Push));
    }

    @Test
    public void setFeature_null(){
        Set<Config.Feature> features = config.getFeatures();
        Assert.assertFalse(features.contains(null));
        Assert.assertEquals(5, features.size());

        Config.Feature[] featureList = null;

        config.setFeatures(featureList);
        features = config.getFeatures();
        Assert.assertFalse(features.contains(null));
        Assert.assertEquals(0, features.size());
    }

    @Test
    public void setFeature_simple(){
        Set<Config.Feature> features = config.getFeatures();
        Assert.assertEquals(5, features.size());

        config.setFeatures(Config.Feature.CrashReporting, Config.Feature.PerformanceMonitoring);
        features = config.getFeatures();
        Assert.assertEquals(2, features.size());
        Assert.assertFalse(features.contains(Config.Feature.Events));
        Assert.assertTrue(features.contains(Config.Feature.CrashReporting));
        Assert.assertTrue(features.contains(Config.Feature.PerformanceMonitoring));
        Assert.assertFalse(features.contains(Config.Feature.Push));
    }

    @Test
    public void setFeature_overwrite(){
        Set<Config.Feature> features = config.getFeatures();
        Assert.assertEquals(5, features.size());

        config.enableFeatures(Config.Feature.Push);
        features = config.getFeatures();
        Assert.assertEquals(6, features.size());
        Assert.assertTrue(features.contains(Config.Feature.Events));
        Assert.assertTrue(features.contains(Config.Feature.Push));
        Assert.assertFalse(features.contains(Config.Feature.Views));
        Assert.assertFalse(features.contains(Config.Feature.PerformanceMonitoring));

        config.setFeatures(Config.Feature.CrashReporting, Config.Feature.PerformanceMonitoring);
        features = config.getFeatures();
        Assert.assertEquals(2, features.size());
        Assert.assertTrue(features.contains(Config.Feature.CrashReporting));
        Assert.assertTrue(features.contains(Config.Feature.PerformanceMonitoring));
        Assert.assertFalse(features.contains(Config.Feature.Push));
        Assert.assertFalse(features.contains(Config.Feature.Views));
    }

    @Test
    public void configVersionSameAsBuildVersion() {
        String buildVersion = ly.count.sdk.android.sdk.BuildConfig.VERSION_NAME;
        Assert.assertEquals(buildVersion, internalConfig.getSdkVersion());
    }

    @Test
    public void enumFeature_values() {
        Config.Feature[] features = Config.Feature.values();
        Assert.assertEquals(Config.Feature.Events, features[0]);
        Assert.assertEquals(Config.Feature.Sessions, features[1]);
        Assert.assertEquals(Config.Feature.Views, features[2]);
        Assert.assertEquals(Config.Feature.CrashReporting, features[3]);
        Assert.assertEquals(Config.Feature.Location, features[4]);
        Assert.assertEquals(Config.Feature.UserProfiles, features[5]);
        Assert.assertEquals(Config.Feature.StarRating, features[6]);
        Assert.assertEquals(Config.Feature.Push, features[7]);
        Assert.assertEquals(Config.Feature.Attribution, features[8]);
        Assert.assertEquals(Config.Feature.RemoteConfig, features[9]);
        Assert.assertEquals(Config.Feature.PerformanceMonitoring, features[10]);
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
        Assert.assertEquals(Config.Feature.CrashReporting, Config.Feature.valueOf("CrashReporting"));
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