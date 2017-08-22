package ly.count.android.sdk.internal;

import android.content.*;

import junit.framework.Assert;

import java.net.MalformedURLException;

import ly.count.android.sdk.Config;

public class TestingUtilityInternal {
    static int countParams(Params params) {
        String paramsString = params.toString();
        return countParams(paramsString);
    }

    static int countParams(String paramsString) {
        String[] paramsParts = paramsString.split("&");
        return paramsParts.length;
    }


    static boolean noDuplicateKeysInParams(Params params){
        String paramsString = params.toString();
        return noDuplicateKeysInParams(paramsString);
    }

    static boolean noDuplicateKeysInParams(String paramsString){
        String[] paramsParts = paramsString.split("&");

        for(int a = 0 ; a < paramsParts.length ; a++){
            String[] parts = paramsParts[a].split("=");
            for(int b = (a + 1) ; b < paramsParts.length; b++) {
                String[] parts2 = paramsParts[b].split("=");
                if(parts[0].equals(parts2[0])){
                    //duplicate key found
                    return false;
                }
            }
        }

        return true;
    }

    static void assertConfigsContainSameData(Config config, InternalConfig internalConfig){
        Assert.assertEquals(config.getSdkVersion(), internalConfig.getSdkVersion());
        Assert.assertEquals(config.getSdkName(), internalConfig.getSdkName());
        Assert.assertEquals(config.isTestModeEnabled(), internalConfig.isTestModeEnabled());
        Assert.assertEquals(config.isProgrammaticSessionsControl(), internalConfig.isProgrammaticSessionsControl());
        Assert.assertEquals(config.isUsePOST(), internalConfig.isUsePOST());
        Assert.assertEquals(config.getLoggingTag(), internalConfig.getLoggingTag());
        Assert.assertEquals(config.getServerAppKey(), internalConfig.getServerAppKey());
        Assert.assertEquals(config.getLoggingLevel(), internalConfig.getLoggingLevel());
        Assert.assertEquals(config.getServerURL(), internalConfig.getServerURL());
        Assert.assertEquals(config.getFeatures(), internalConfig.getFeatures());
    }

    public static Config setupConfig() throws MalformedURLException {
        String serverUrl = "http://www.serverurl.com";
        String serverAppKey = "1234";
        return new Config(serverUrl, serverAppKey);
    }

    public static InternalConfig setupLogs(Config config) throws MalformedURLException {
        InternalConfig internalConfig = new InternalConfig(config == null ? setupConfig() : config);
        Log log = new Log();
        log.init(internalConfig);
        return internalConfig;
    }

    static Core setupBasicCore(android.content.Context context) throws MalformedURLException {
        return setupBasicCore(context, null);
    }

    static Core setupBasicCore(android.content.Context context, Config config) throws MalformedURLException {
        config = TestingUtilityInternal.setupLogs(config);
        Core core = new Core();
        core.init(config, context);
        core.onContextCreated(context);
        return core;
    }
}
