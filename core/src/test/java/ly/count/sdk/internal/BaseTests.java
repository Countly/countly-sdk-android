package ly.count.sdk.internal;


import org.junit.After;
import org.junit.Before;
import org.mockito.Mockito;
import org.powermock.reflect.Whitebox;

import java.util.List;

import ly.count.sdk.ConfigCore;

import static org.mockito.Mockito.mock;

public class BaseTests {
    protected static String SERVER = "http://www.serverurl.com";
    protected static String APP_KEY = "1234";

    protected Ctx ctx;
    protected InternalConfig config = null;
    protected Module dummy = null;
    protected Utils utils = null;

    protected SDKCore sdk = null;

    public class CtxImpl implements Ctx {
        private SDK sdk;
        private Object ctx;
        private InternalConfig config;

        public CtxImpl(SDK sdk, InternalConfig config, Object ctx) {
            this.sdk = sdk;
            this.config = config;
            this.ctx = ctx;
        }

        @Override
        public Object getContext() {
            return ctx;
        }

        @Override
        public InternalConfig getConfig() {
            return config;
        }

        @Override
        public SDK getSDK() {
            return sdk;
        }

        @Override
        public boolean isExpired() {
            return false;
        }
    }

    public ConfigCore config() {
        return new ConfigCore(SERVER, APP_KEY).enableTestMode().setLoggingLevel(ConfigCore.LoggingLevel.DEBUG);
    }

    protected ConfigCore defaultConfig() throws Exception {
        return config();
    }

    protected ConfigCore defaultConfigWithLogsForConfigTests() throws Exception {
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

    protected void setUpApplication(ConfigCore config) throws Exception {
        setUpSDK(config == null ? defaultConfig() : config, false);
    }

    private void setUpSDK(ConfigCore config, boolean limited) throws Exception {
        new Log().init(this.config == null ? new InternalConfig(config == null ? defaultConfig() : config) : this.config);
        this.dummy = mock(ModuleBase.class);
        Utils.reflectiveSetField(SDK.class, "testDummyModule", dummy);
        this.sdk = mock(SDKCore.class);
        this.sdk.init(new CtxImpl(this.sdk, new InternalConfig(defaultConfig()), getContext()));
        this.config = this.sdk.config();
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

    private Object getContext() {
        return new Object();
    }

    @After
    public void tearDown() throws Exception  {
        if (this.sdk != null && ctx != null) {
            this.sdk.stop(ctx, true);
            this.sdk = null;
        }
        Utils.reflectiveSetField(SDK.class, "testDummyModule", null);
    }

}
