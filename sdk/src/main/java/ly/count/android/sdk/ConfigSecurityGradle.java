package ly.count.android.sdk;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies opt-in security settings provided by the app's build (Gradle) to the SDK configuration at
 * init time. The values are supplied as Android resources — typically via {@code resValue} in the
 * app's (or a shared wrapper library's) build.gradle — so an app can enforce the SDK's security
 * hardening purely from Gradle, without editing its manifest or its init code.
 *
 * <p>Absent resources leave the configuration unchanged, so integrations that declare nothing behave
 * exactly as before. The flags only ever turn protections ON, and an allow-list is applied only when
 * non-empty (an empty value never loosens a list set in code). Each setting has its own resource,
 * plus a master {@link #KEY_ENABLE_ALL} bool that turns on the boolean protections at once. Note the
 * master switch does NOT enable the push additional-intent-redirection checks unless a push class
 * allow-list is also provided, because enabling those without one rejects every notification click;
 * use {@link #KEY_PUSH_ADDITIONAL_CHECKS} to opt into them explicitly.
 *
 * <p>Because these resources are referenced only via {@code Resources.getIdentifier(...)}, the SDK
 * ships a {@code res/raw/countly_keep.xml} keep rule so resource shrinking does not strip them from
 * release builds.
 *
 * <p>Example (app or wrapper {@code build.gradle}):
 * <pre>
 * android { defaultConfig {
 *     resValue "bool",   "countly_security_enable_all", "true"
 *     resValue "string", "countly_security_push_allowed_class_names", "com.example.app.MainActivity"
 * } }
 * </pre>
 */
public class ConfigSecurityGradle {
    // Master switch — turns on the boolean protections below at once.
    public static final String KEY_ENABLE_ALL = "countly_security_enable_all";

    // App / content level (applied to CountlyConfig).
    public static final String KEY_DISABLE_WEBVIEW = "countly_security_disable_webview";
    public static final String KEY_DISABLE_LOGGING_IN_PRODUCTION = "countly_security_disable_logging_in_production";
    public static final String KEY_CONTENT_ALLOWED_SCHEMES = "countly_security_content_allowed_schemes";

    // Push level (applied to CountlyConfigPush, from the messaging package).
    public static final String KEY_PUSH_ADDITIONAL_CHECKS = "countly_security_push_additional_checks";
    public static final String KEY_PUSH_ALLOWED_CLASS_NAMES = "countly_security_push_allowed_class_names";
    public static final String KEY_PUSH_ALLOWED_PACKAGE_NAMES = "countly_security_push_allowed_package_names";
    public static final String KEY_PUSH_ALLOWED_SCHEMES = "countly_security_push_allowed_schemes";

    private static final String[] BOOL_KEYS = {
        KEY_ENABLE_ALL, KEY_DISABLE_WEBVIEW, KEY_DISABLE_LOGGING_IN_PRODUCTION, KEY_PUSH_ADDITIONAL_CHECKS
    };
    private static final String[] STRING_KEYS = {
        KEY_CONTENT_ALLOWED_SCHEMES, KEY_PUSH_ALLOWED_CLASS_NAMES, KEY_PUSH_ALLOWED_PACKAGE_NAMES, KEY_PUSH_ALLOWED_SCHEMES
    };

    private ConfigSecurityGradle() {
    }

    /**
     * Reads the security resources the app declared (via Gradle {@code resValue} or a values XML)
     * into a Bundle. A resource that was not declared is simply not put, so the apply step then
     * leaves that setting untouched. Never returns null.
     */
    public static Bundle readSettings(Context context) {
        return readSettings(context, null);
    }

    public static Bundle readSettings(Context context, ModuleLog L) {
        Bundle out = new Bundle();
        if (context == null) {
            return out;
        }
        Resources res;
        String pkg;
        try {
            res = context.getResources();
            pkg = context.getPackageName();
        } catch (Exception e) {
            if (L != null) {
                L.w("[ConfigSecurityGradle] Could not access app resources to read security settings: " + e);
            }
            return out;
        }
        // Each resource is read in its own try so one bad/overlaid entry cannot drop the rest.
        for (String name : BOOL_KEYS) {
            try {
                int id = res.getIdentifier(name, "bool", pkg);
                if (id != 0) {
                    out.putBoolean(name, res.getBoolean(id));
                } else {
                    // Tolerate a string-typed declaration (e.g. resValue "string", name, "true"),
                    // which boolValue() understands, so a common misdeclaration is not silently lost.
                    int sid = res.getIdentifier(name, "string", pkg);
                    if (sid != 0) {
                        out.putString(name, res.getString(sid));
                    }
                }
            } catch (Exception e) {
                if (L != null) {
                    L.w("[ConfigSecurityGradle] Failed reading security resource [" + name + "]: " + e);
                }
            }
        }
        for (String name : STRING_KEYS) {
            try {
                int id = res.getIdentifier(name, "string", pkg);
                if (id != 0) {
                    out.putString(name, res.getString(id));
                }
            } catch (Exception e) {
                if (L != null) {
                    L.w("[ConfigSecurityGradle] Failed reading security resource [" + name + "]: " + e);
                }
            }
        }
        return out;
    }

    /** A boolean setting value, tolerating a boolean or a "true"/"false" string. */
    public static boolean boolValue(Bundle md, String key) {
        if (md == null) {
            return false;
        }
        Object v = md.get(key);
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        if (v instanceof String) {
            return "true".equalsIgnoreCase(((String) v).trim());
        }
        return false;
    }

    /**
     * Warns when a boolean setting is present but its value is not a recognized boolean
     * ("true"/"false"), e.g. a typo like "yes" or "1". Such a value is treated as false (the setting
     * stays off), so surfacing it avoids a silently-disabled security setting.
     */
    public static void warnIfUnrecognizedBool(Bundle md, String key, ModuleLog L) {
        if (md == null || L == null || !md.containsKey(key)) {
            return;
        }
        Object v = md.get(key);
        if (v instanceof Boolean) {
            return;
        }
        String s = v == null ? null : String.valueOf(v).trim();
        if (s != null && !s.equalsIgnoreCase("true") && !s.equalsIgnoreCase("false")) {
            L.w("[ConfigSecurityGradle] Security setting [" + key + "] has an unrecognized value [" + s + "]; expected true/false. Treating it as false (setting stays off).");
        }
    }

    /** A comma-separated setting value parsed into a trimmed, non-empty list. */
    public static List<String> csvValue(Bundle md, String key) {
        List<String> outList = new ArrayList<>();
        if (md == null) {
            return outList;
        }
        Object v = md.get(key);
        if (v != null) {
            for (String part : String.valueOf(v).split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    outList.add(trimmed);
                }
            }
        }
        return outList;
    }

    /**
     * Reads the Gradle-provided resources, applies the app/content-level settings onto the config,
     * and returns the read Bundle so the caller can surface diagnostics ({@link #warnOnMisconfiguration}
     * / {@link #logEffectiveConfig}) once logging is enabled. The apply itself is silent because it
     * must run before logging is turned on (so a Gradle-provided logging flag is honored).
     */
    static Bundle applyToConfig(CountlyConfig config, Context context, ModuleLog L) {
        Bundle md = readSettings(context, L);
        applyBundleToConfig(config, md);
        return md;
    }

    static void applyBundleToConfig(CountlyConfig config, Bundle md) {
        if (config == null || md == null) {
            return;
        }
        boolean all = boolValue(md, KEY_ENABLE_ALL);

        if (all || boolValue(md, KEY_DISABLE_WEBVIEW)) {
            config.disableWebView();
        }
        if (all || boolValue(md, KEY_DISABLE_LOGGING_IN_PRODUCTION)) {
            config.disableSDKLoggingInProduction();
        }
        // Apply an allow-list only when a non-empty value is provided, so an empty/blank resource
        // never silently loosens an allow-list that was set in code.
        List<String> contentSchemes = csvValue(md, KEY_CONTENT_ALLOWED_SCHEMES);
        if (!contentSchemes.isEmpty()) {
            config.content.setAllowedIntentSchemes(contentSchemes);
        }
    }

    /**
     * Surfaces misconfiguration for the app/content-level settings. Must be called AFTER logging is
     * enabled, otherwise the warnings are dropped (the apply itself runs earlier, before logging is
     * on). Warns for an unrecognized boolean value, and for an allow-list that was declared but
     * resolved to blank (so it silently had no effect).
     */
    static void warnOnMisconfiguration(Bundle md, ModuleLog L) {
        if (md == null || L == null) {
            return;
        }
        warnIfUnrecognizedBool(md, KEY_ENABLE_ALL, L);
        warnIfUnrecognizedBool(md, KEY_DISABLE_WEBVIEW, L);
        warnIfUnrecognizedBool(md, KEY_DISABLE_LOGGING_IN_PRODUCTION, L);
        warnIfDeclaredButBlank(md, KEY_CONTENT_ALLOWED_SCHEMES, L);
    }

    /**
     * Warns when a list setting is declared but resolves to an empty/blank value, so the developer
     * learns their declared resource silently had no effect (rather than confusing it with "not set").
     */
    public static void warnIfDeclaredButBlank(Bundle md, String key, ModuleLog L) {
        if (md == null || L == null || !md.containsKey(key)) {
            return;
        }
        if (csvValue(md, key).isEmpty()) {
            L.w("[ConfigSecurityGradle] Security setting [" + key + "] was declared but is blank; it had no effect.");
        }
    }

    /**
     * Logs the effective app/content security configuration for an audit trail. Called after logging
     * is enabled (the settings themselves are applied earlier, before logging is on). Push settings
     * are logged separately by {@code CountlyPush.applyPushSecurityGradle}.
     */
    static void logEffectiveConfig(CountlyConfig config, ModuleLog L) {
        if (config == null || L == null) {
            return;
        }
        L.d("[ConfigSecurityGradle] Effective security config: webViewEnabled=[" + config.webViewEnabled
            + "], disableSDKLoggingInProduction=[" + config.disableSDKLoggingInProduction
            + "], content allowed intent schemes=[" + config.content.allowedIntentSchemes + "]");
    }
}
