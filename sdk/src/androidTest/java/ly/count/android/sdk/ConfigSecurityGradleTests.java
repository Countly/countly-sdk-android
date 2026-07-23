package ly.count.android.sdk;

import android.os.Bundle;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Verifies the Gradle-provided (resValue -> Android resource) opt-in security settings are applied
 * to the app/content config, and that misconfiguration is surfaced. The push side is covered in
 * PushTests. Apply-logic tests build Bundles by hand; {@link #readSettings_readsDeclaredResources}
 * exercises the real resource-read path against fixtures in res/values/countly_security_test.xml.
 */
@RunWith(AndroidJUnit4.class)
public class ConfigSecurityGradleTests {

    private CountlyConfig config() {
        return new CountlyConfig(ApplicationProvider.getApplicationContext(), "appkey", "https://test.count.ly");
    }

    /** A ModuleLog that captures warning messages, so we can assert diagnostics without Mockito. */
    private ModuleLog capturingLog(final List<String> warnings) {
        ModuleLog L = new ModuleLog();
        L.SetListener((message, level) -> {
            if (level == ModuleLog.LogLevel.Warning) {
                warnings.add(message);
            }
        });
        return L;
    }

    /** No settings present -> config unchanged (integrations that declare nothing are unaffected). */
    @Test
    public void absentSettings_noChange() {
        CountlyConfig c = config();
        ConfigSecurityGradle.applyBundleToConfig(c, new Bundle());
        Assert.assertTrue(c.webViewEnabled);
        Assert.assertFalse(c.disableSDKLoggingInProduction);
        Assert.assertTrue(c.content.allowedIntentSchemes.isEmpty());
    }

    /** A null bundle (no resources / not readable) must not crash. */
    @Test
    public void nullBundle_noCrash() {
        CountlyConfig c = config();
        ConfigSecurityGradle.applyBundleToConfig(c, null);
        Assert.assertTrue(c.webViewEnabled);
    }

    /** Individual boolean settings turn on their protection. */
    @Test
    public void individualBooleanSettings_applied() {
        Bundle md = new Bundle();
        md.putBoolean(ConfigSecurityGradle.KEY_DISABLE_WEBVIEW, true);
        md.putBoolean(ConfigSecurityGradle.KEY_DISABLE_LOGGING_IN_PRODUCTION, true);
        CountlyConfig c = config();
        ConfigSecurityGradle.applyBundleToConfig(c, md);
        Assert.assertFalse(c.webViewEnabled);
        Assert.assertTrue(c.disableSDKLoggingInProduction);
    }

    /** A resValue string "true" must be honored like a boolean. */
    @Test
    public void stringTrueValue_honored() {
        Bundle md = new Bundle();
        md.putString(ConfigSecurityGradle.KEY_DISABLE_WEBVIEW, "true");
        CountlyConfig c = config();
        ConfigSecurityGradle.applyBundleToConfig(c, md);
        Assert.assertFalse(c.webViewEnabled);
    }

    /** The master "enable_all" setting turns on the boolean protections at once. */
    @Test
    public void enableAll_turnsOnBooleanProtections() {
        Bundle md = new Bundle();
        md.putBoolean(ConfigSecurityGradle.KEY_ENABLE_ALL, true);
        CountlyConfig c = config();
        ConfigSecurityGradle.applyBundleToConfig(c, md);
        Assert.assertFalse(c.webViewEnabled);
        Assert.assertTrue(c.disableSDKLoggingInProduction);
    }

    /** A comma-separated scheme list is trimmed here; the downstream setter lower-cases and dedups. */
    @Test
    public void contentAllowedSchemes_csvParsed() {
        Bundle md = new Bundle();
        md.putString(ConfigSecurityGradle.KEY_CONTENT_ALLOWED_SCHEMES, "https, myapp ,HTTPS");
        CountlyConfig c = config();
        ConfigSecurityGradle.applyBundleToConfig(c, md);
        Assert.assertTrue(c.content.allowedIntentSchemes.contains("https"));
        Assert.assertTrue(c.content.allowedIntentSchemes.contains("myapp"));
        // setAllowedIntentSchemes (not csvValue) lower-cases, so "HTTPS" folds into "https" -> size 2.
        Assert.assertEquals(2, c.content.allowedIntentSchemes.size());
    }

    /** An empty/blank content scheme value must not wipe a list set in code. */
    @Test
    public void contentAllowedSchemes_emptyDoesNotOverride() {
        CountlyConfig c = config();
        c.content.setAllowedIntentSchemes(Arrays.asList("https")); // set in code
        Bundle md = new Bundle();
        md.putString(ConfigSecurityGradle.KEY_CONTENT_ALLOWED_SCHEMES, "   "); // blank resource
        ConfigSecurityGradle.applyBundleToConfig(c, md);
        Assert.assertTrue(c.content.allowedIntentSchemes.contains("https")); // preserved
        Assert.assertEquals(1, c.content.allowedIntentSchemes.size());
    }

    /** The one-call convenience applies the recommended app-level hardening. */
    @Test
    public void enableRecommendedSecuritySettings_appliesBundle() {
        CountlyConfig c = config().enableRecommendedSecuritySettings();
        Assert.assertFalse(c.webViewEnabled);
        Assert.assertTrue(c.disableSDKLoggingInProduction);
    }

    /** boolValue / csvValue helpers. */
    @Test
    public void valueHelpers() {
        Bundle md = new Bundle();
        md.putString("b", "TRUE");
        md.putBoolean("b2", true);
        md.putString("csv", " a, ,b , c ");
        Assert.assertTrue(ConfigSecurityGradle.boolValue(md, "b"));
        Assert.assertTrue(ConfigSecurityGradle.boolValue(md, "b2"));
        Assert.assertFalse(ConfigSecurityGradle.boolValue(md, "missing"));
        List<String> csv = ConfigSecurityGradle.csvValue(md, "csv");
        Assert.assertEquals(Arrays.asList("a", "b", "c"), csv);
    }

    /** Malformed / wrong-type values must be safe: junk booleans are treated as false, never crash. */
    @Test
    public void malformedValues_areSafe() {
        Bundle md = new Bundle();
        md.putString("yes", "yes");
        md.putString("one", "1");
        md.putString("blank", "  ");
        md.putInt("int", 1);              // wrong type entirely
        md.putString("trueSpaced", "  true ");
        Assert.assertFalse(ConfigSecurityGradle.boolValue(md, "yes"));
        Assert.assertFalse(ConfigSecurityGradle.boolValue(md, "one"));
        Assert.assertFalse(ConfigSecurityGradle.boolValue(md, "blank"));
        Assert.assertFalse(ConfigSecurityGradle.boolValue(md, "int"));   // Integer -> false, no ClassCastException
        Assert.assertTrue(ConfigSecurityGradle.boolValue(md, "trueSpaced"));

        md.putString("commas", ",, ,");
        Assert.assertTrue(ConfigSecurityGradle.csvValue(md, "commas").isEmpty());
        Assert.assertTrue(ConfigSecurityGradle.csvValue(md, "missing").isEmpty());
    }

    /** A junk boolean on a real setting leaves it off (fail-safe) and does not crash the apply. */
    @Test
    public void applyBundle_junkBoolean_settingStaysOff() {
        Bundle md = new Bundle();
        md.putString(ConfigSecurityGradle.KEY_DISABLE_WEBVIEW, "1"); // not "true"
        CountlyConfig c = config();
        ConfigSecurityGradle.applyBundleToConfig(c, md);
        Assert.assertTrue(c.webViewEnabled); // stays enabled — junk did not enable the protection
    }

    /** Misconfiguration is surfaced: unrecognized boolean and declared-but-blank list both warn. */
    @Test
    public void warnOnMisconfiguration_surfacesBadValues() {
        List<String> warnings = new ArrayList<>();
        ModuleLog L = capturingLog(warnings);
        Bundle md = new Bundle();
        md.putString(ConfigSecurityGradle.KEY_DISABLE_WEBVIEW, "yes");        // unrecognized bool
        md.putString(ConfigSecurityGradle.KEY_CONTENT_ALLOWED_SCHEMES, "   "); // declared but blank
        ConfigSecurityGradle.warnOnMisconfiguration(md, L);
        Assert.assertEquals(2, warnings.size());
    }

    /** Valid values produce no warnings. */
    @Test
    public void warnOnMisconfiguration_silentOnValid() {
        List<String> warnings = new ArrayList<>();
        ModuleLog L = capturingLog(warnings);
        Bundle md = new Bundle();
        md.putBoolean(ConfigSecurityGradle.KEY_ENABLE_ALL, true);
        md.putString(ConfigSecurityGradle.KEY_DISABLE_WEBVIEW, "false");
        md.putString(ConfigSecurityGradle.KEY_CONTENT_ALLOWED_SCHEMES, "https");
        ConfigSecurityGradle.warnOnMisconfiguration(md, L);
        Assert.assertTrue(warnings.isEmpty());
    }

    /**
     * End-to-end resource read: readSettings resolves the real resValue-style resources declared in
     * res/values/countly_security_test.xml (all false/none). Proves the getIdentifier bool read AND
     * the string-typed-bool fallback, and that undeclared keys are absent.
     */
    @Test
    public void readSettings_readsDeclaredResources() {
        Bundle md = ConfigSecurityGradle.readSettings(ApplicationProvider.getApplicationContext());
        // declared as <bool> -> present as a Boolean
        Assert.assertTrue(md.containsKey(ConfigSecurityGradle.KEY_ENABLE_ALL));
        Assert.assertTrue(md.get(ConfigSecurityGradle.KEY_ENABLE_ALL) instanceof Boolean);
        Assert.assertFalse(ConfigSecurityGradle.boolValue(md, ConfigSecurityGradle.KEY_ENABLE_ALL));
        Assert.assertTrue(md.containsKey(ConfigSecurityGradle.KEY_DISABLE_WEBVIEW));
        // declared as <string> for a bool key -> the fallback stores it as a String, still understood
        Assert.assertTrue(md.get(ConfigSecurityGradle.KEY_DISABLE_LOGGING_IN_PRODUCTION) instanceof String);
        Assert.assertFalse(ConfigSecurityGradle.boolValue(md, ConfigSecurityGradle.KEY_DISABLE_LOGGING_IN_PRODUCTION));
        // undeclared keys are absent
        Assert.assertFalse(md.containsKey(ConfigSecurityGradle.KEY_CONTENT_ALLOWED_SCHEMES));
        Assert.assertFalse(md.containsKey(ConfigSecurityGradle.KEY_PUSH_ALLOWED_CLASS_NAMES));
    }
}
