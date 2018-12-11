package ly.count.sdk.android.internal;

import android.app.Application;

import org.junit.After;
import org.junit.Before;
import org.mockito.Mockito;
import org.powermock.reflect.Whitebox;

import java.util.List;

import ly.count.sdk.android.Config;
import ly.count.sdk.internal.InternalConfig;
import ly.count.sdk.internal.Log;
import ly.count.sdk.internal.Module;
import ly.count.sdk.internal.Storage;

import static android.content.Context.ACTIVITY_SERVICE;
import static android.content.Context.MODE_PRIVATE;
import static android.content.Context.TELEPHONY_SERVICE;
import static android.content.Context.WINDOW_SERVICE;
import static android.support.test.InstrumentationRegistry.getContext;
import static ly.count.sdk.android.internal.Legacy.PREFERENCES;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class BaseTests {
    protected static String SERVER = "http://www.serverurl.com";
    protected static String APP_KEY = "1234";

    protected Ctx ctx;
    protected InternalConfig config = null;
    protected Module dummy = null;
    protected Utils utils = null;

    protected SDK sdk = null;
    protected CountlyService service = null;

    public Config config() {
        return new Config(SERVER, APP_KEY).enableTestMode().setLoggingLevel(Config.LoggingLevel.DEBUG);
    }

    protected Config defaultConfig() throws Exception {
        return config();
    }

    protected ly.count.sdk.Config defaultConfigWithLogsForConfigTests() throws Exception {
        InternalConfig config = new InternalConfig(defaultConfig());
        new Log().init(config);
        return config;
    }

    @Before
    public void setUp() throws Exception {
        ctx = new CtxImpl(this.sdk, this.config == null ? new InternalConfig(defaultConfig()) : this.config, getContext());
        utils = Mockito.spy(new Utils());
        Utils.reflectiveSetField(Utils.class, "utils", utils);
    }

    protected void setUpApplication(Config config) throws Exception {
        setUpSDK(config == null ? defaultConfig() : config, false);
    }

    protected void setUpService(Config config) throws Exception {
        setUpSDK(config, true);
        this.service = spy(new CountlyService());
        doReturn(ctx.getContext()).when(service).getApplicationContext();
    }

    private void setUpSDK(Config config, boolean limited) throws Exception {
        new Log().init(this.config == null ? new InternalConfig(config == null ? defaultConfig() : config) : this.config);
        this.dummy = mock(ModuleBase.class);
        Utils.reflectiveSetField(SDK.class, "testDummyModule", dummy);
        this.sdk = new SDK();
        this.sdk.init(new CtxImpl(this.sdk, new InternalConfig(defaultConfig()), getContext()));
        this.config = SDK.instance.config();
        this.ctx = new CtxImpl(this.sdk, this.config, getContext());
    }

    @SuppressWarnings("unchecked")
    protected <T extends Module> T module(Class<T> cls, boolean mock) {
        T module = sdk.module(cls);

        if (module == null) {
            return null;
        }

        if (mock) {
            List<Module> list = Whitebox.getInternalState(sdk, "modules");
            list.remove(module);
            module = Mockito.spy(module);
            list.add(1, module);
        }

        return module;
    }

    protected Application application() {
        Application app = mock(Application.class);
        when(app.getApplicationContext()).thenReturn(getContext());
        when(app.getResources()).thenReturn(ctx.getContext().getResources());
        when(app.getSystemService(TELEPHONY_SERVICE)).thenReturn(ctx.getContext().getSystemService(TELEPHONY_SERVICE));
        when(app.getSystemService(WINDOW_SERVICE)).thenReturn(ctx.getContext().getSystemService(WINDOW_SERVICE));
        when(app.getSystemService(ACTIVITY_SERVICE)).thenReturn(ctx.getContext().getSystemService(ACTIVITY_SERVICE));
        when(app.getPackageManager()).thenReturn(ctx.getContext().getPackageManager());
        when(app.getPackageName()).thenReturn(ctx.getContext().getPackageName());
        when(app.getSharedPreferences(PREFERENCES, MODE_PRIVATE)).thenReturn(ctx.getContext().getSharedPreferences(PREFERENCES, MODE_PRIVATE));
        when(app.getContentResolver()).thenReturn(ctx.getContext().getContentResolver());
        when(app.getMainLooper()).thenReturn(ctx.getContext().getMainLooper());
        return app;
    }

    @After
    public void tearDown() throws Exception  {
        if (this.sdk != null && ctx != null) {
            this.sdk.stop(ctx, true);
            this.sdk = null;
        } else {
            Storage.await();
            this.sdk = new SDK();
            this.sdk.storablePurge(ctx == null ? new CtxImpl(this.sdk, this.config == null ? new InternalConfig(defaultConfig()) : this.config, getContext()) : ctx, null);
            this.sdk = null;
        }
        Utils.reflectiveSetField(SDK.class, "testDummyModule", null);
    }

}
