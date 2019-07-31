package ly.count.sdk.internal;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import ly.count.sdk.ConfigCore;

@RunWith(JUnit4.class)
public class SessionImplTests extends BaseTestsCore {
    @Rule
    public TestName testName = new TestName();

    @Before
    public void setUp() throws Exception {
        super.setUp();
        setUpApplication(defaultConfig());
    }

    @Override
    protected ConfigCore defaultConfig() throws Exception {
        return super.defaultConfig().disableTestMode();//
    }

    @Test
    public void constructor_deserialize(){
        long targetID = 11234L;
        SessionImpl session = new SessionImpl(ctx, targetID);
        Assert.assertEquals(targetID, (long)session.getId());
    }

    @Test
    public void addParams() {
        SessionImpl session = new SessionImpl(ctx);
        Assert.assertNotNull(session.params);

        StringBuilder sbParams = new StringBuilder();
        String[] keys = new String[]{"a", "b", "c"};
        String[] vals = new String[]{"11", "22", "33"};

        sbParams.append(keys[0]).append("=").append(vals[0]);
        session.addParam(keys[0], vals[0]);
        Assert.assertEquals(sbParams.toString(), session.params.toString());

        sbParams.append("&").append(keys[1]).append("=").append(vals[1]);
        session.addParam(keys[1], vals[1]);
        Assert.assertEquals(sbParams.toString(), session.params.toString());

        sbParams.append("&").append(keys[2]).append("=").append(vals[2]);
        session.addParam(keys[2], vals[2]);
        Assert.assertEquals(sbParams.toString(), session.params.toString());
    }
}
