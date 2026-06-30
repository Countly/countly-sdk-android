package ly.count.android.sdk.messaging;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import ly.count.android.sdk.Countly;
import ly.count.android.sdk.Utils;

import static ly.count.android.sdk.messaging.CountlyPush.ALLOWED_CLASS_NAMES;
import static ly.count.android.sdk.messaging.CountlyPush.ALLOWED_PACKAGE_NAMES;
import static ly.count.android.sdk.messaging.CountlyPush.EXTRA_ACTION_INDEX;
import static ly.count.android.sdk.messaging.CountlyPush.EXTRA_INTENT;
import static ly.count.android.sdk.messaging.CountlyPush.EXTRA_MESSAGE;

public class CountlyPushActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        performPushAction(getIntent());
        finish();
    }

    /**
     * Validates that the intent's target component may be launched. Both the package and the class
     * must be allow-listed: the package must equal the app's own package or one of
     * {@code allowedPackageNames}, and the class must equal one of {@code allowedClassNames}. The
     * class must be listed explicitly even when it is in the app's own package, so the integrator
     * opts each launchable target in by its fully-qualified class name (via
     * setAllowedIntentClassNames). Matching is exact (no prefix/suffix matching), e.g.
     * "com.example.app.MainActivity". A null component (implicit intent that cannot be validated) is
     * not trusted, and null allow-lists are treated as empty.
     */
    static boolean isComponentTrusted(ComponentName component, ArrayList<String> allowedPackageNames, ArrayList<String> allowedClassNames, String ownPackageName) {
        if (component == null) {
            // Implicit intent has no component to validate against the allow-lists
            return false;
        }

        String intentPackageName = component.getPackageName();
        String intentClassName = component.getClassName();

        // The package must be the app's own package or an explicitly allow-listed one.
        boolean trustedPackage = ownPackageName != null && ownPackageName.equals(intentPackageName);
        if (!trustedPackage && allowedPackageNames != null) {
            for (String packageName : allowedPackageNames) {
                if (packageName != null && intentPackageName.equals(packageName)) {
                    trustedPackage = true;
                    break;
                }
            }
        }

        if (!trustedPackage) {
            return false;
        }

        // The class must be explicitly allow-listed, even for an own-package target.
        if (allowedClassNames != null) {
            for (String className : allowedClassNames) {
                if (className != null && intentClassName.equals(className)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Whether a notification link may be opened via ACTION_VIEW, per the shared scheme policy:
     * with a non-empty allow-list only those schemes pass; otherwise any scheme except the known
     * dangerous ones (file/content/javascript/jar/data) passes, so deep links keep working.
     */
    static boolean isLinkSchemeAllowed(Uri link, Set<String> allowedSchemes) {
        return Utils.isExternalSchemeAllowed(link == null ? null : link.getScheme(), allowedSchemes);
    }

    /**
     * Whether the integrator's custom URL handler consumed the link. Null-safe against an
     * un-initialized push config: {@code CountlyPush.countlyConfigPush} can be null when the
     * activity runs in a fresh process (e.g. the notification is tapped after the app process was
     * killed and push was not re-initialized), so this must not dereference it blindly. Returns
     * false (handler did not consume the link) when there is no config or no handler.
     */
    static boolean linkHandledByCustomHandler(String url, Context context) {
        return CountlyPush.countlyConfigPush != null
            && CountlyPush.countlyConfigPush.notificationButtonURLHandler != null
            && CountlyPush.countlyConfigPush.notificationButtonURLHandler.onClick(url, context);
    }

    /**
     * Validates the push activity intent and returns the inner intent that is safe to act on, or
     * null if the push must be rejected. Encapsulates the redirection guards so they are testable:
     * a missing inner intent, URI-grant flags (stripped on API 26+, rejected below), an untrusted
     * caller package, a target outside the app's own package, and the optional additional
     * component allow-list checks. The returned inner intent has any URI-grant flags removed.
     */
    static Intent validatePushIntent(Context context, Intent activityIntent, ComponentName callingActivity, String packageNameCurrent) {
        activityIntent.setExtrasClassLoader(CountlyPush.class.getClassLoader());

        Intent intent = activityIntent.getParcelableExtra(EXTRA_INTENT);
        if (intent == null) {
            Countly.sharedInstance().L.e("[CountlyPush, CountlyPushActivity] Received a null Intent, stopping execution");
            return null;
        }

        int flags = intent.getFlags();
        if (((flags & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) || ((flags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0)) {
            Countly.sharedInstance().L.w("[CountlyPush, CountlyPushActivity] Attempt to get URI permissions");
            // Remove not trusted URI flags
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Countly.sharedInstance().L.d("[CountlyPush, CountlyPushActivity] Removed URI permissions");
                intent.removeFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.removeFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } else {
                Countly.sharedInstance().L.d("[CountlyPush, CountlyPushActivity] Can not remove URI permissions. Aborting");
                return null;
            }
        }

        if (callingActivity != null) {
            String callingPackage = callingActivity.getPackageName();
            if (!packageNameCurrent.equals(callingPackage)) {
                Countly.sharedInstance().L.w("[CountlyPushActivity] validatePushIntent, Untrusted intent package");
                return null;
            }
        }

        ComponentName targetComponent = intent.resolveActivity(context.getPackageManager());
        if (targetComponent == null || !packageNameCurrent.equals(targetComponent.getPackageName())) {
            Countly.sharedInstance().L.w("[CountlyPushActivity] validatePushIntent, Untrusted target component");
            return null;
        }

        // Read from the intent the SDK built (not from static state). Defaults to true so an intent
        // that lacks the flag (e.g. a forged/forwarded one) still goes through the stricter checks.
        boolean useAdditionalIntentRedirectionChecks = activityIntent.getBooleanExtra(CountlyPush.ADDITIONAL_INTENT_REDIRECTION_CHECKS, true);
        if (useAdditionalIntentRedirectionChecks) {
            ArrayList<String> allowedIntentClassNames = activityIntent.getStringArrayListExtra(ALLOWED_CLASS_NAMES);
            ArrayList<String> allowedIntentPackageNames = activityIntent.getStringArrayListExtra(ALLOWED_PACKAGE_NAMES);

            if (!isComponentTrusted(intent.getComponent(), allowedIntentPackageNames, allowedIntentClassNames, packageNameCurrent)) {
                Countly.sharedInstance().L.w("[CountlyPush, CountlyPushActivity] Untrusted intent component, aborting");
                return null;
            }
        }

        return intent;
    }

    private void performPushAction(Intent activityIntent) {
        Context context = this;
        Countly.sharedInstance().L.d("[CountlyPush, CountlyPushActivity] Push activity receiver receiving message");

        String packageNameCurrent = getPackageName();
        Intent intent = validatePushIntent(context, activityIntent, getCallingActivity(), packageNameCurrent);
        if (intent == null) {
            return;
        }

        ArrayList<String> allowedSchemesList = activityIntent.getStringArrayListExtra(CountlyPush.ALLOWED_INTENT_SCHEMES);
        Set<String> allowedLinkSchemes = allowedSchemesList == null ? null : new HashSet<>(allowedSchemesList);

        Countly.sharedInstance().L.d("[CountlyPush, CountlyPushActivity] Push activity, after filtering");

        intent.setExtrasClassLoader(CountlyPush.class.getClassLoader());

        int index = intent.getIntExtra(EXTRA_ACTION_INDEX, 0);
        Bundle bundle = intent.getParcelableExtra(EXTRA_MESSAGE);
        if (bundle == null) {
            Countly.sharedInstance().L.e("[CountlyPush, CountlyPushActivity] Received a null Intent bundle, stopping execution");
            return;
        }

        CountlyPush.Message message = bundle.getParcelable(EXTRA_MESSAGE);
        if (message == null) {
            Countly.sharedInstance().L.e("[CountlyPush, CountlyPushActivity] Received a null Intent bundle message, stopping execution");
            return;
        }

        message.recordAction(context, index);

        final NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(message.hashCode());
        }

        try {
            //try/catch required due to Android 12
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                //this needs to be called before Android 12
                Intent closeNotificationsPanel = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
                context.sendBroadcast(closeNotificationsPanel);
            }
        } catch (Exception ex) {
            Countly.sharedInstance().L.e("[CountlyPush, CountlyPushActivity] Encountered issue while trying to send the on click broadcast. [" + ex.toString() + "]");
        }

        if (index == 0) {
            try {
                if (message.link() != null) {
                    Countly.sharedInstance().L.d("[CountlyPush, CountlyPushActivity] Starting activity with given link. Push body. [" + message.link() + "]");

                    if (linkHandledByCustomHandler(message.link().toString(), context)) {
                        Countly.sharedInstance().L.d("[CountlyPush, CountlyPushActivity] Link handled by custom URL handler, skipping default link opening.");
                        return;
                    }

                    if (!isLinkSchemeAllowed(message.link(), allowedLinkSchemes)) {
                        Countly.sharedInstance().L.w("[CountlyPush, CountlyPushActivity] Blocked notification link with disallowed scheme: [" + message.link().getScheme() + "]");
                        return;
                    }

                    Intent i = new Intent(Intent.ACTION_VIEW, message.link());
                    i.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_NEW_TASK);
                    // Only forward the push payload when the link resolves to our own app, so the
                    // message data is not leaked to an external app that happens to handle the link.
                    ComponentName linkTarget = i.resolveActivity(context.getPackageManager());
                    if (linkTarget != null && packageNameCurrent.equals(linkTarget.getPackageName())) {
                        i.putExtra(EXTRA_MESSAGE, bundle);
                        i.putExtra(EXTRA_ACTION_INDEX, index);
                    }
                    context.startActivity(i);
                } else {
                    Countly.sharedInstance().L.d("[CountlyPush, CountlyPushActivity] Starting activity without a link. Push body");
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                }
            } catch (Exception ex) {
                Countly.sharedInstance().L.e("[CountlyPush, displayDialog] Encountered issue while clicking on notification body [" + ex.toString() + "]");
            }
        } else {
            try {
                Uri buttonLink = message.buttons().get(index - 1).link();
                if (buttonLink == null) {
                    Countly.sharedInstance().L.w("[CountlyPush, CountlyPushActivity] Notification button link is null, nothing to open");
                    return;
                }

                if (linkHandledByCustomHandler(buttonLink.toString(), context)) {
                    Countly.sharedInstance().L.d("[CountlyPush, CountlyPushActivity] Link handled by custom URL handler, skipping default link opening.");
                    return;
                }

                if (!isLinkSchemeAllowed(buttonLink, allowedLinkSchemes)) {
                    Countly.sharedInstance().L.w("[CountlyPush, CountlyPushActivity] Blocked notification button link with disallowed scheme: [" + buttonLink.getScheme() + "]");
                    return;
                }

                Countly.sharedInstance().L.d("[CountlyPush, CountlyPushActivity] Starting activity with given button link. [" + (index - 1) + "] [" + buttonLink + "]");
                Intent i = new Intent(Intent.ACTION_VIEW, buttonLink);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                // Only forward the push payload when the link resolves to our own app, so the
                // message data is not leaked to an external app that happens to handle the link.
                ComponentName linkTarget = i.resolveActivity(context.getPackageManager());
                if (linkTarget != null && packageNameCurrent.equals(linkTarget.getPackageName())) {
                    i.putExtra(EXTRA_MESSAGE, bundle);
                    i.putExtra(EXTRA_ACTION_INDEX, index);
                }
                context.startActivity(i);
            } catch (Exception ex) {
                Countly.sharedInstance().L.e("[CountlyPush, displayDialog] Encountered issue while clicking on notification button [" + ex.toString() + "]");
            }
        }
    }
}
