package ly.count.android.sdk.messaging;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ly.count.android.sdk.ConfigSecurityGradle;
import ly.count.android.sdk.Countly;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PushTests {

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void decodeMessage() {
        //{c.b=[{"t":"Button 1","l":"https:\/\/www.google111.com"},{"t":"Button 2","l":"https:\/\/www.google222.com"}], c.i=5e56ae8c80171b2dc1154f3d, c.l=https://www.google333.com, sound=default, title=112, message=rewrwer}

        Map<String, String> values = new HashMap<>();
        values.put("c.b", "[{\"t\":\"Button 1\",\"l\":\"https:\\/\\/www.google111.com\"},{\"t\":\"Button 2\",\"l\":\"https:\\/\\/www.google222.com\"}]");

        values.put("c.i", "5e56ae8c80171b2dc1154f3d");
        values.put("c.l", "https://www.google333.com");
        values.put("sound", "default");
        values.put("title", "112");
        values.put("message", "rewrwer");

        ModulePush.MessageImpl message = new ModulePush.MessageImpl(values);

        Assert.assertEquals("5e56ae8c80171b2dc1154f3d", message.id);
        Assert.assertEquals("https://www.google333.com", message.link().toString());
        Assert.assertEquals("default", message.sound());
        Assert.assertEquals("112", message.title());
        Assert.assertEquals("rewrwer", message.message());

        List<CountlyPush.Button> buttons = message.buttons();
        Assert.assertEquals("Button 1", buttons.get(0).title());
        Assert.assertEquals("https://www.google111.com", buttons.get(0).link().toString());

        Assert.assertEquals("Button 2", buttons.get(1).title());
        Assert.assertEquals("https://www.google222.com", buttons.get(1).link().toString());
    }

    private static final String OWN_PKG = "com.example.app";
    private static final String OWN_CLASS = "com.example.app.MainActivity";

    private ComponentName comp(String pkg, String cls) {
        return new ComponentName(pkg, cls);
    }

    private ArrayList<String> list(String... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    /**
     * A null component (implicit intent) cannot be validated and must not be trusted.
     * This also guards against the previous NPE on intent.getComponent().
     */
    @Test
    public void isComponentTrusted_nullComponent_notTrusted() {
        Assert.assertFalse(CountlyPushActivity.isComponentTrusted(null, list(OWN_CLASS), list(OWN_CLASS), OWN_PKG));
    }

    /**
     * An exact package match plus an exact (fully-qualified) class match is trusted.
     */
    @Test
    public void isComponentTrusted_exactPackageAndClass_trusted() {
        Assert.assertTrue(CountlyPushActivity.isComponentTrusted(comp(OWN_PKG, OWN_CLASS), new ArrayList<>(), list(OWN_CLASS), OWN_PKG));
    }

    /**
     * The own package is always allowed for the package check, but the class must still be listed
     * exactly: an own-package class that is not in the class allow-list is not trusted (the
     * integrator must opt each target class in manually).
     */
    @Test
    public void isComponentTrusted_ownPackageButUnlistedClass_notTrusted() {
        Assert.assertFalse(CountlyPushActivity.isComponentTrusted(comp(OWN_PKG, OWN_CLASS), new ArrayList<>(), new ArrayList<>(), OWN_PKG));
    }

    /**
     * A foreign package is never trusted, regardless of the class.
     */
    @Test
    public void isComponentTrusted_foreignPackage_notTrusted() {
        Assert.assertFalse(CountlyPushActivity.isComponentTrusted(comp("com.attacker", OWN_CLASS), new ArrayList<>(), list(OWN_CLASS), OWN_PKG));
    }

    /**
     * Matching is exact: neither a sub-package nor a deceptive sibling prefix of an allowed
     * package is trusted.
     */
    @Test
    public void isComponentTrusted_subOrPrefixPackage_notTrusted() {
        ArrayList<String> pkgs = list("com.example.app");
        Assert.assertFalse(CountlyPushActivity.isComponentTrusted(comp("com.example.app.sub", "com.example.app.sub.A"), pkgs, list("com.example.app.sub.A"), OWN_PKG));
        Assert.assertFalse(CountlyPushActivity.isComponentTrusted(comp("com.example.appEvil", "com.example.appEvil.A"), pkgs, list("com.example.appEvil.A"), OWN_PKG));
    }

    /**
     * Matching is exact: a short (non-qualified) class name or a deceptive suffix does not match
     * the fully-qualified target class; only the exact FQN matches.
     */
    @Test
    public void isComponentTrusted_shortOrSuffixClass_notTrusted() {
        Assert.assertFalse(CountlyPushActivity.isComponentTrusted(comp(OWN_PKG, OWN_CLASS), new ArrayList<>(), list("MainActivity"), OWN_PKG));
        Assert.assertFalse(CountlyPushActivity.isComponentTrusted(comp(OWN_PKG, "com.evil.NotMainActivity"), new ArrayList<>(), list("MainActivity"), OWN_PKG));
        Assert.assertTrue(CountlyPushActivity.isComponentTrusted(comp(OWN_PKG, OWN_CLASS), new ArrayList<>(), list(OWN_CLASS), OWN_PKG));
    }

    /**
     * An allowed package other than the own package, with its class listed exactly, is trusted.
     */
    @Test
    public void isComponentTrusted_allowedForeignPackageExactClass_trusted() {
        Assert.assertTrue(CountlyPushActivity.isComponentTrusted(comp("com.partner", "com.partner.Entry"), list("com.partner"), list("com.partner.Entry"), OWN_PKG));
    }

    /**
     * Null allow-lists must not crash; with no class list nothing is trusted (a class match is
     * always required, even for the own package), and a foreign package stays untrusted.
     */
    @Test
    public void isComponentTrusted_nullAllowLists_noCrash() {
        Assert.assertFalse(CountlyPushActivity.isComponentTrusted(comp(OWN_PKG, OWN_CLASS), null, null, OWN_PKG));
        Assert.assertFalse(CountlyPushActivity.isComponentTrusted(comp("com.attacker", "com.attacker.Evil"), null, null, OWN_PKG));
    }

    /**
     * Default (no allow-list): http(s) and legitimate deep-link schemes are allowed.
     */
    @Test
    public void isLinkSchemeAllowed_defaultAllowsWebAndDeepLinks() {
        Assert.assertTrue(CountlyPushActivity.isLinkSchemeAllowed(Uri.parse("https://countly.com/x"), null));
        Assert.assertTrue(CountlyPushActivity.isLinkSchemeAllowed(Uri.parse("http://example.com"), null));
        Assert.assertTrue(CountlyPushActivity.isLinkSchemeAllowed(Uri.parse("myapp://deep/link"), new HashSet<>()));
        Assert.assertTrue(CountlyPushActivity.isLinkSchemeAllowed(Uri.parse("market://details?id=com.x"), null));
        Assert.assertTrue(CountlyPushActivity.isLinkSchemeAllowed(Uri.parse("tel:+1234567890"), null));
        Assert.assertTrue(CountlyPushActivity.isLinkSchemeAllowed(Uri.parse("mailto:a@b.com"), null));
    }

    /**
     * Default (no allow-list): local-data and script schemes and a null/missing scheme are blocked.
     */
    @Test
    public void isLinkSchemeAllowed_defaultBlocksLocalAndScriptSchemes() {
        Assert.assertFalse(CountlyPushActivity.isLinkSchemeAllowed(Uri.parse("file:///data/data/app/shared_prefs/secret.xml"), null));
        Assert.assertFalse(CountlyPushActivity.isLinkSchemeAllowed(Uri.parse("content://app.provider/secret"), null));
        Assert.assertFalse(CountlyPushActivity.isLinkSchemeAllowed(Uri.parse("javascript:alert(1)"), null));
        Assert.assertFalse(CountlyPushActivity.isLinkSchemeAllowed(Uri.parse("jar:file:///x.apk!/a.html"), null));
        Assert.assertFalse(CountlyPushActivity.isLinkSchemeAllowed(Uri.parse("FILE:///x"), null));
        Assert.assertFalse(CountlyPushActivity.isLinkSchemeAllowed(null, null));
        Assert.assertFalse(CountlyPushActivity.isLinkSchemeAllowed(Uri.parse("/no/scheme/path"), null));
    }

    /**
     * Allow-list mode: only listed schemes pass (others blocked even if normally allowed), and an
     * explicitly listed otherwise-dangerous scheme is honored.
     */
    @Test
    public void isLinkSchemeAllowed_allowlistRestricts() {
        Set<String> allow = new HashSet<>(Arrays.asList("https", "myapp"));
        Assert.assertTrue(CountlyPushActivity.isLinkSchemeAllowed(Uri.parse("https://x.com"), allow));
        Assert.assertTrue(CountlyPushActivity.isLinkSchemeAllowed(Uri.parse("myapp://x"), allow));
        Assert.assertFalse(CountlyPushActivity.isLinkSchemeAllowed(Uri.parse("http://x.com"), allow));   // not listed
        Assert.assertFalse(CountlyPushActivity.isLinkSchemeAllowed(Uri.parse("market://x"), allow));     // not listed
        Assert.assertFalse(CountlyPushActivity.isLinkSchemeAllowed(Uri.parse("file:///x"), allow));      // not listed
        // an explicitly allow-listed scheme is honored even if it is otherwise dangerous
        Assert.assertTrue(CountlyPushActivity.isLinkSchemeAllowed(Uri.parse("content://x"), new HashSet<>(Arrays.asList("content"))));
    }

    // ---- linkHandledByCustomHandler: null-safety against an un-initialized push config ----

    /**
     * Regression: tapping a push link must not NPE when CountlyPush has not been initialized in this
     * process (countlyConfigPush == null) — e.g. the notification is tapped after the app process
     * was killed. The handler check must return false (not crash), so the scheme guard then runs.
     */
    @Test
    public void linkHandledByCustomHandler_nullConfig_noCrashReturnsFalse() {
        CountlyConfigPush saved = CountlyPush.countlyConfigPush;
        try {
            CountlyPush.countlyConfigPush = null;
            Assert.assertFalse(CountlyPushActivity.linkHandledByCustomHandler("https://x.com", ctx()));
            Assert.assertFalse(CountlyPushActivity.linkHandledByCustomHandler("file:///etc/hosts", ctx()));
        } finally {
            CountlyPush.countlyConfigPush = saved;
        }
    }

    /** With a config but no handler set, the link is not consumed (and no crash). */
    @Test
    public void linkHandledByCustomHandler_configWithoutHandler_returnsFalse() {
        CountlyConfigPush saved = CountlyPush.countlyConfigPush;
        try {
            CountlyPush.countlyConfigPush = new CountlyConfigPush((android.app.Application) ctx().getApplicationContext());
            Assert.assertFalse(CountlyPushActivity.linkHandledByCustomHandler("https://x.com", ctx()));
        } finally {
            CountlyPush.countlyConfigPush = saved;
        }
    }

    /** A handler that claims the link returns true; one that declines returns false. */
    @Test
    public void linkHandledByCustomHandler_handlerDecision_isHonored() {
        CountlyConfigPush saved = CountlyPush.countlyConfigPush;
        try {
            CountlyPush.countlyConfigPush = new CountlyConfigPush((android.app.Application) ctx().getApplicationContext())
                .setNotificationButtonURLHandler((url, context) -> true);
            Assert.assertTrue(CountlyPushActivity.linkHandledByCustomHandler("https://x.com", ctx()));

            CountlyPush.countlyConfigPush = new CountlyConfigPush((android.app.Application) ctx().getApplicationContext())
                .setNotificationButtonURLHandler((url, context) -> false);
            Assert.assertFalse(CountlyPushActivity.linkHandledByCustomHandler("https://x.com", ctx()));
        } finally {
            CountlyPush.countlyConfigPush = saved;
        }
    }

    /** The one-call push convenience enables the checks and allow-lists the given classes. */
    @Test
    public void enableRecommendedSecuritySettings_push_appliesBundle() {
        CountlyConfigPush cfg = new CountlyConfigPush((Application) ctx().getApplicationContext())
            .enableRecommendedSecuritySettings(Arrays.asList("com.a.A", "com.b.B"));
        Assert.assertTrue(cfg.useAdditionalIntentRedirectionChecks);
        Assert.assertTrue(cfg.allowedIntentClassNames.contains("com.a.A"));
        Assert.assertTrue(cfg.allowedIntentClassNames.contains("com.b.B"));
    }

    // ---- Gradle-driven (resValue -> resource) opt-in push security settings ----

    /** The Gradle settings (master + allow-lists) are applied onto the push config. */
    @Test
    public void applyPushSecurityGradle_appliesSettingsFromBundle() {
        CountlyConfigPush cfg = new CountlyConfigPush((Application) ctx().getApplicationContext());
        Bundle md = new Bundle();
        md.putBoolean(ConfigSecurityGradle.KEY_ENABLE_ALL, true);
        md.putString(ConfigSecurityGradle.KEY_PUSH_ALLOWED_CLASS_NAMES, "com.a.A, com.b.B");
        md.putString(ConfigSecurityGradle.KEY_PUSH_ALLOWED_SCHEMES, "https");

        CountlyPush.applyPushSecurityGradle(cfg, md);

        Assert.assertTrue(cfg.useAdditionalIntentRedirectionChecks);
        Assert.assertTrue(cfg.allowedIntentClassNames.contains("com.a.A"));
        Assert.assertTrue(cfg.allowedIntentClassNames.contains("com.b.B"));
        Assert.assertTrue(cfg.allowedIntentSchemes.contains("https"));
    }

    /** The dedicated push-checks setting works on its own, and absent/null bundles change nothing / do not crash. */
    @Test
    public void applyPushSecurityGradle_individualAndAbsent() {
        CountlyConfigPush on = new CountlyConfigPush((Application) ctx().getApplicationContext());
        Bundle md = new Bundle();
        md.putString(ConfigSecurityGradle.KEY_PUSH_ADDITIONAL_CHECKS, "true"); // resValue string
        CountlyPush.applyPushSecurityGradle(on, md);
        Assert.assertTrue(on.useAdditionalIntentRedirectionChecks);

        CountlyConfigPush off = new CountlyConfigPush((Application) ctx().getApplicationContext());
        CountlyPush.applyPushSecurityGradle(off, new Bundle());
        Assert.assertFalse(off.useAdditionalIntentRedirectionChecks);
        CountlyPush.applyPushSecurityGradle(off, (Bundle) null); // no crash
    }

    /**
     * The master switch must NOT enable the additional redirection checks without a class allow-list
     * (that would reject every notification click). enable_all alone -> checks stay off; enable_all
     * WITH a class allow-list -> checks on.
     */
    @Test
    public void applyPushSecurityGradle_enableAll_gatesChecksOnClassList() {
        CountlyConfigPush noClasses = new CountlyConfigPush((Application) ctx().getApplicationContext());
        Bundle md1 = new Bundle();
        md1.putBoolean(ConfigSecurityGradle.KEY_ENABLE_ALL, true);
        CountlyPush.applyPushSecurityGradle(noClasses, md1);
        Assert.assertFalse(noClasses.useAdditionalIntentRedirectionChecks); // safe: not enabled without classes

        CountlyConfigPush withClasses = new CountlyConfigPush((Application) ctx().getApplicationContext());
        Bundle md2 = new Bundle();
        md2.putBoolean(ConfigSecurityGradle.KEY_ENABLE_ALL, true);
        md2.putString(ConfigSecurityGradle.KEY_PUSH_ALLOWED_CLASS_NAMES, "com.a.A");
        CountlyPush.applyPushSecurityGradle(withClasses, md2);
        Assert.assertTrue(withClasses.useAdditionalIntentRedirectionChecks);
        Assert.assertTrue(withClasses.allowedIntentClassNames.contains("com.a.A"));
    }

    /** enable_all must honor a class allow-list set in CODE (not only a Gradle-provided one). */
    @Test
    public void applyPushSecurityGradle_enableAll_honorsCodeSetClassList() {
        CountlyConfigPush cfg = new CountlyConfigPush((Application) ctx().getApplicationContext());
        cfg.setAllowedIntentClassNames(Arrays.asList("com.code.A")); // set in code, no Gradle classes
        Bundle md = new Bundle();
        md.putBoolean(ConfigSecurityGradle.KEY_ENABLE_ALL, true);
        CountlyPush.applyPushSecurityGradle(cfg, md);
        Assert.assertTrue(cfg.useAdditionalIntentRedirectionChecks); // honored the code-set class list
    }

    /** An empty allow-list value must not wipe a list set in code. */
    @Test
    public void applyPushSecurityGradle_emptyList_doesNotOverride() {
        CountlyConfigPush cfg = new CountlyConfigPush((Application) ctx().getApplicationContext());
        cfg.setAllowedIntentSchemes(Arrays.asList("https")); // set in code
        Bundle md = new Bundle();
        md.putString(ConfigSecurityGradle.KEY_PUSH_ALLOWED_SCHEMES, "   "); // blank resource
        CountlyPush.applyPushSecurityGradle(cfg, md);
        Assert.assertTrue(cfg.allowedIntentSchemes.contains("https")); // code value preserved
    }

    // ---- config -> intent-extra wiring: enableAdditionalIntentRedirectionChecks() / setAllowedIntentSchemes() reach the guards ----

    /**
     * Pins the customer-facing wiring: the values createPushActivityIntent is given (sourced from
     * CountlyConfigPush at display time) must land on the built intent under the exact extra keys the
     * activity reads, and feed the guards. A key rename or value flip here would silently disable a
     * configured protection while every direct-arg guard test still passed.
     */
    @Test
    public void createPushActivityIntent_writesConfigExtras_consumedByGuards() {
        Map<String, String> data = new HashMap<>();
        data.put("c.i", "wiring_test");
        data.put("message", "m");
        CountlyPush.Message msg = CountlyPush.decodeMessage(data);

        // Explicit inner intent (the SDK test app has no launcher, so getLaunchIntentForPackage is null).
        Intent built = CountlyPush.createPushActivityIntent(ctx(), msg, ownTargetInner(), 0,
            new HashSet<>(Arrays.asList("com.example.app.MainActivity")),
            new HashSet<>(Arrays.asList("com.example.app")),
            true,
            new HashSet<>(Arrays.asList("https")));

        // enableAdditionalIntentRedirectionChecks() -> ADDITIONAL_INTENT_REDIRECTION_CHECKS=true on the intent
        Assert.assertTrue(built.getBooleanExtra(CountlyPush.ADDITIONAL_INTENT_REDIRECTION_CHECKS, false));
        Assert.assertTrue(built.getStringArrayListExtra(CountlyPush.ALLOWED_CLASS_NAMES).contains("com.example.app.MainActivity"));
        Assert.assertTrue(built.getStringArrayListExtra(CountlyPush.ALLOWED_PACKAGE_NAMES).contains("com.example.app"));
        Assert.assertNotNull(built.getParcelableExtra(CountlyPush.EXTRA_INTENT));

        // setAllowedIntentSchemes(["https"]) -> the scheme allow-list reaches isLinkSchemeAllowed as an allow-list
        Set<String> schemes = new HashSet<>(built.getStringArrayListExtra(CountlyPush.ALLOWED_INTENT_SCHEMES));
        Assert.assertTrue(CountlyPushActivity.isLinkSchemeAllowed(Uri.parse("https://x"), schemes));
        Assert.assertFalse(CountlyPushActivity.isLinkSchemeAllowed(Uri.parse("countly://x"), schemes));
    }

    // ---- validatePushIntent: the performPushAction guards (R1-R5) ----

    private Context ctx() {
        return ApplicationProvider.getApplicationContext();
    }

    // An explicit intent at a component declared in our own (merged) manifest -> resolves to own package.
    private Intent ownTargetInner() {
        return new Intent(ctx(), CountlyPushActivity.class);
    }

    private Intent activityIntentWith(Intent inner) {
        Intent act = new Intent();
        if (inner != null) {
            act.putExtra(CountlyPush.EXTRA_INTENT, inner);
        }
        return act;
    }

    /** R1: a push activity intent with no inner EXTRA_INTENT is rejected. */
    @Test
    public void validatePushIntent_nullInner_returnsNull() {
        Assert.assertNull(CountlyPushActivity.validatePushIntent(ctx(), new Intent(), null, ctx().getPackageName()));
    }

    /** R4: an inner intent whose target is not our own package is rejected. */
    @Test
    public void validatePushIntent_crossAppTarget_returnsNull() {
        Intent act = activityIntentWith(new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")));
        act.putExtra(CountlyPush.ADDITIONAL_INTENT_REDIRECTION_CHECKS, false);
        Assert.assertNull(CountlyPushActivity.validatePushIntent(ctx(), act, null, ctx().getPackageName()));
    }

    /** R2: URI-grant flags on the inner intent are stripped, and the intent is returned (API 26+). */
    @Test
    public void validatePushIntent_uriGrantFlags_strippedAndReturned() {
        Intent inner = ownTargetInner();
        inner.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        Intent act = activityIntentWith(inner);
        act.putExtra(CountlyPush.ADDITIONAL_INTENT_REDIRECTION_CHECKS, false);
        Intent result = CountlyPushActivity.validatePushIntent(ctx(), act, null, ctx().getPackageName());
        Assert.assertNotNull(result);
        Assert.assertEquals(0, result.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Assert.assertEquals(0, result.getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    }

    /** R3: a calling activity from a foreign package is rejected. */
    @Test
    public void validatePushIntent_untrustedCaller_returnsNull() {
        Intent act = activityIntentWith(ownTargetInner());
        act.putExtra(CountlyPush.ADDITIONAL_INTENT_REDIRECTION_CHECKS, false);
        ComponentName foreignCaller = new ComponentName("com.attacker", "com.attacker.Evil");
        Assert.assertNull(CountlyPushActivity.validatePushIntent(ctx(), act, foreignCaller, ctx().getPackageName()));
    }

    /** Happy path: own-package target with the additional checks disabled returns the inner intent. */
    @Test
    public void validatePushIntent_ownTargetChecksDisabled_returnsIntent() {
        Intent act = activityIntentWith(ownTargetInner());
        act.putExtra(CountlyPush.ADDITIONAL_INTENT_REDIRECTION_CHECKS, false);
        Assert.assertNotNull(CountlyPushActivity.validatePushIntent(ctx(), act, null, ctx().getPackageName()));
    }

    /** R5 default-true: missing flag -> checks run -> own-package target with no class allow-list is rejected (the class must be opted in manually). */
    @Test
    public void validatePushIntent_defaultChecksNoAllowList_returnsNull() {
        Intent act = activityIntentWith(ownTargetInner()); // no ADDITIONAL_INTENT_REDIRECTION_CHECKS extra
        Assert.assertNull(CountlyPushActivity.validatePushIntent(ctx(), act, null, ctx().getPackageName()));
    }

    /** R5 pass: own-package target whose class is exactly allow-listed is returned. */
    @Test
    public void validatePushIntent_additionalChecksAllowlisted_returnsIntent() {
        Intent act = activityIntentWith(ownTargetInner());
        act.putStringArrayListExtra(CountlyPush.ALLOWED_CLASS_NAMES, new ArrayList<>(Arrays.asList(CountlyPushActivity.class.getName())));
        act.putStringArrayListExtra(CountlyPush.ALLOWED_PACKAGE_NAMES, new ArrayList<>(Arrays.asList(ctx().getPackageName())));
        Assert.assertNotNull(CountlyPushActivity.validatePushIntent(ctx(), act, null, ctx().getPackageName()));
    }
}
