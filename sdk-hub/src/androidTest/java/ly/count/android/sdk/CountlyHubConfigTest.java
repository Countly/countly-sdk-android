package ly.count.android.sdk;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import ly.count.android.sdk.hub.CountlyHub;
import ly.count.android.sdk.hub.HubClientConfig;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Checks what {@link CountlyHub#useHub} changes in the SDK configuration. It lives in the SDK's package
 * to read the configuration's fields.
 */
@RunWith(AndroidJUnit4.class)
public class CountlyHubConfigTest {
    /**
     * The hub does not relay the requests of content and feedback widgets, so useHub turns the SDK's
     * WebView-based UI off, also in an app that has network access, and gives the SDK the hub as its
     * connection factory.
     */
    @Test
    public void useHub_turnsWebViewOffAndSetsTheConnectionFactory() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        CountlyConfig config = new CountlyConfig(context, "KEY", "https://countly.example.invalid");
        config.webViewEnabled = true;

        CountlyHub.useHub(config, new HubClientConfig(context, context.getPackageName()));

        Assert.assertFalse(config.webViewEnabled);
        Assert.assertNotNull(config.connectionFactory);
    }
}
