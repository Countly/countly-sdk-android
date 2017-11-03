package ly.count.android.sdk.internal;

import android.app.Application;

import org.junit.After;
import org.junit.Before;
import org.mockito.Mockito;
import org.powermock.reflect.Whitebox;

import java.net.MalformedURLException;
import java.util.List;

import ly.count.android.sdk.Config;

import static android.content.Context.ACTIVITY_SERVICE;
import static android.content.Context.MODE_PRIVATE;
import static android.content.Context.TELEPHONY_SERVICE;
import static android.content.Context.WINDOW_SERVICE;
import static android.support.test.InstrumentationRegistry.getContext;
import static ly.count.android.sdk.internal.Legacy.PREFERENCES;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class BaseTests {
    protected static String SERVER = "http://www.serverurl.com";
    protected static String APP_KEY = "1234";

    protected Context ctx;
    protected InternalConfig config = null;
    protected Module dummy = null;
    protected Utils utils = null;

    protected Core core = null;
    protected CountlyService service = null;

    @Before
    public void setUp() throws Exception {
        ctx = new ContextImpl(getContext());
        utils = Mockito.spy(new Utils());
        Utils.reflectiveSetField(Utils.class, "utils", utils);
    }

    protected void setUpApplication(Config config) throws Exception {
        setUpCore(config == null ? defaultConfig() : config, false);
    }

    protected void setUpService(Config config) throws Exception {
        setUpCore(config, true);
        this.service = spy(new CountlyService());
        doReturn(ctx.getContext()).when(service).getApplicationContext();
    }

    private void setUpCore(Config config, boolean limited) throws Exception {
        new Log().init(this.config == null ? new InternalConfig(defaultConfig()) : this.config);
        this.dummy = mock(Module.class);
        Utils.reflectiveSetField(Core.class, "testDummyModule", dummy);
        this.core = Core.init(config, application(), limited);
        this.config = Core.initialized();
    }

    protected Config defaultConfig() throws MalformedURLException {
        return new Config(SERVER, APP_KEY).enableTestMode().setLoggingLevel(Config.LoggingLevel.DEBUG);
    }

    protected Config defaultConfigWithLogsForConfigTests() throws MalformedURLException {
        InternalConfig config = new InternalConfig(defaultConfig());
        new Log().init(config);
        return config;
    }

    @SuppressWarnings("unchecked")
    protected <T extends Module> T module(Class<T> cls, boolean mock) {
        List<Module> list = Whitebox.getInternalState(core, "modules");
        T module = core.module(cls);

        if (module == null) {
            return null;
        }

        if (mock) {
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
        Storage.await();
        Core.deinit();
        if (ctx != null) {
            Core.purgeInternalStorage(ctx, null);
        }
        Utils.reflectiveSetField(Core.class, "testDummyModule", null);
    }

}
