package ly.count.android.sdk.internal;

import android.app.Application;
import android.app.Service;
import android.content.*;
import android.content.Context;

import junit.framework.Assert;

import java.net.MalformedURLException;

import ly.count.android.sdk.Config;

import static android.content.Context.MODE_PRIVATE;
import static android.content.Context.TELEPHONY_SERVICE;
import static android.content.Context.WINDOW_SERVICE;
import static android.support.test.InstrumentationRegistry.getContext;
import static ly.count.android.sdk.internal.Legacy.PREFERENCES;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestingUtilityInternal {
    public static String LOG_TAG = "CountlyTests";

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
        internalConfig.setLoggingLevel(Config.LoggingLevel.DEBUG);
        Log log = new Log();
        log.init(internalConfig);
        return internalConfig;
    }

    static Core setupBasicCore(android.content.Context context) throws MalformedURLException {
        return setupBasicCore(context, null);
    }

    static Core setupBasicCore(android.content.Context context, Config config) throws MalformedURLException {
        config = TestingUtilityInternal.setupLogs(config);
        Core core = Core.initForApplication(config, context);
        core.onLimitedContextAcquired(context);
        return core;
    }

    static Application mockApplication(android.content.Context context) {
        Application app = mock(Application.class);
        when(app.getApplicationContext()).thenReturn(getContext());
        when(app.getResources()).thenReturn(context.getResources());
        when(app.getSystemService(TELEPHONY_SERVICE)).thenReturn(context.getSystemService(TELEPHONY_SERVICE));
        when(app.getSystemService(WINDOW_SERVICE)).thenReturn(context.getSystemService(WINDOW_SERVICE));
        when(app.getPackageManager()).thenReturn(context.getPackageManager());
        when(app.getPackageName()).thenReturn(context.getPackageName());
        when(app.getSharedPreferences(PREFERENCES, MODE_PRIVATE)).thenReturn(context.getSharedPreferences(PREFERENCES, MODE_PRIVATE));
        when(app.getContentResolver()).thenReturn(context.getContentResolver());
        when(app.getMainLooper()).thenReturn(context.getMainLooper());
        return app;
    }

    public static void setupCoreForApplication(Context context) throws MalformedURLException {
        setupCoreForApplication(context, null);
    }

    public static void setupCoreForApplication(Context context, Config config) throws MalformedURLException {
        config = TestingUtilityInternal.setupLogs(config);
        Core core = Core.initForApplication(config, context);
        core.onContextAcquired(mockApplication(context));
    }

    public static InternalConfig setupCoreForService(Context context, Config config) throws MalformedURLException {
        Service service = mock(Service.class);
        when(service.getApplicationContext()).thenReturn(context);
        InternalConfig internalConfig = TestingUtilityInternal.setupLogs(config);
        Storage.push(new ContextImpl(context), internalConfig);
        internalConfig = Core.initForService(service);
        Core.instance.onLimitedContextAcquired(context);
        return internalConfig;
    }
}
