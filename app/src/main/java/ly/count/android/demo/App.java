package ly.count.android.demo;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.NonNull;

import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;

import java.util.HashMap;
import java.util.Map;

import ly.count.android.sdk.Countly;
import ly.count.android.sdk.CountlyConfig;
import ly.count.android.sdk.CrashFilterCallback;
import ly.count.android.sdk.DeviceId;
import ly.count.android.sdk.ModuleLog;
import ly.count.android.sdk.RemoteConfigCallback;
import ly.count.android.sdk.messaging.CountlyPush;

import static ly.count.android.sdk.Countly.TAG;
import static ly.count.android.sdk.messaging.CountlyPush.COUNTLY_BROADCAST_PERMISSION_POSTFIX;

public class App extends Application {
    /** You should use try.count.ly instead of YOUR_SERVER for the line below if you are using Countly trial service */
    final String COUNTLY_SERVER_URL = "YOUR_SERVER";
    final String COUNTLY_APP_KEY = "YOUR_APP_KEY";
    static long applicationStartTimestamp = System.currentTimeMillis();

    private BroadcastReceiver messageReceiver;

    @Override
    public void onCreate() {
        super.onCreate();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                // Create the NotificationChannel
                NotificationChannel channel = new NotificationChannel(CountlyPush.CHANNEL_ID, getString(R.string.countly_hannel_name), NotificationManager.IMPORTANCE_DEFAULT);
                channel.setDescription(getString(R.string.countly_channel_description));

                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();

                Uri soundUri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + getApplicationContext().getPackageName() + "/" + R.raw.notif_sample);

                channel.setSound(soundUri, audioAttributes);
                notificationManager.createNotificationChannel(channel);
            }
        }

        //sample certificate for the countly try server
        String[] certificates = new String[] {
            "MIIGnjCCBYagAwIBAgIRAN73cVA7Y1nD+S8rToAqBpQwDQYJKoZIhvcNAQELBQAwgY8xCzAJ"
                + "BgNVBAYTAkdCMRswGQYDVQQIExJHcmVhdGVyIE1hbmNoZXN0ZXIxEDAOBgNVBAcTB1"
                + "NhbGZvcmQxGDAWBgNVBAoTD1NlY3RpZ28gTGltaXRlZDE3MDUGA1UEAxMuU2VjdGln"
                + "byBSU0EgRG9tYWluIFZhbGlkYXRpb24gU2VjdXJlIFNlcnZlciBDQTAeFw0yMDA2MD"
                + "EwMDAwMDBaFw0yMjA5MDMwMDAwMDBaMBUxEzARBgNVBAMMCiouY291bnQubHkwggEi"
                + "MA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCl9zmATVRwrGRtRQJcmBmA+zc/ZL"
                + "io3YfkwXO2w8u9lnw60J4JpPNn9OnGcxdM+sqbXKU3jTdjY4j3yaA6NlWibq2jU2x6"
                + "HT2sS+I5gFFE/6tO53WqjoMk48i3FkyoJDittwtQrVaRGcP8RjJH0pfXaP+JLrLAgg"
                + "HuW3tCFqYzkWi3uLGVjQbSIRNiXsM3FI0UMEa/x1I3U4hLjMjH28KagZbZLWnHOvks"
                + "AvGLg3xQkS+GSQ+6ARZ2/bGh5O9q4hCCCk0/PpwAXmrOnWtwrNuwHcCDOvuB22JxLd"
                + "t8jQDYrjwtJIvq4Yut8FQPv/75SKoETWWHyxe0x5NsB34UwA/BAgMBAAGjggNsMIID"
                + "aDAfBgNVHSMEGDAWgBSNjF7EVK2K4Xfpm/mbBeG4AY1h4TAdBgNVHQ4EFgQU8uf/ND"
                + "Rt8cu+AwARVIGXPMfxGbQwDgYDVR0PAQH/BAQDAgWgMAwGA1UdEwEB/wQCMAAwHQYD"
                + "VR0lBBYwFAYIKwYBBQUHAwEGCCsGAQUFBwMCMEkGA1UdIARCMEAwNAYLKwYBBAGyMQ"
                + "ECAgcwJTAjBggrBgEFBQcCARYXaHR0cHM6Ly9zZWN0aWdvLmNvbS9DUFMwCAYGZ4EM"
                + "AQIBMIGEBggrBgEFBQcBAQR4MHYwTwYIKwYBBQUHMAKGQ2h0dHA6Ly9jcnQuc2VjdG"
                + "lnby5jb20vU2VjdGlnb1JTQURvbWFpblZhbGlkYXRpb25TZWN1cmVTZXJ2ZXJDQS5j"
                + "cnQwIwYIKwYBBQUHMAGGF2h0dHA6Ly9vY3NwLnNlY3RpZ28uY29tMB8GA1UdEQQYMB"
                + "aCCiouY291bnQubHmCCGNvdW50Lmx5MIIB9AYKKwYBBAHWeQIEAgSCAeQEggHgAd4A"
                + "dQBGpVXrdfqRIDC1oolp9PN9ESxBdL79SbiFq/L8cP5tRwAAAXJwTJ0kAAAEAwBGME"
                + "QCIEErTN/aGJ8LV9brGklKeGAXMg1EN/FUxXDu13kNfXhcAiBrKMYe+W4flPyuLNm5"
                + "jp6FJwtUTZPNpZ+TmM40dRdwjQB0AN+lXqtogk8fbK3uuF9OPlrqzaISpGpejjsSwC"
                + "BEXCpzAAABcnBMncsAAAQDAEUwQwIfEYSpsSDtKpmj9ZmRWsx73G622N74v09JDjzP"
                + "bkg9RQIgUelIqSwqu69JanH7losrqTTsjwNv+3QJBNJ6GxJKkh0AdgBvU3asMfAxGd"
                + "iZAKRRFf93FRwR2QLBACkGjbIImjfZEwAAAXJwTJ0YAAAEAwBHMEUCIQCMBaaQAoua"
                + "97R+z2zONMUq1XsDP5aoAiutZG4XxuQ6wAIgW1p6XS3az4CCqjwbDKxL9qEnw8fWd+"
                + "yLx2skviSsTS0AdwApeb7wnjk5IfBWc59jpXflvld9nGAK+PlNXSZcJV3HhAAAAXJw"
                + "TJ1PAAAEAwBIMEYCIQDg1YFbJPPKDIyrFZJ9rtrUklkh2k/wpgwjDuIp7tPtOgIhAL"
                + "dZl9s/qISsFm2E64ruYbdE4HKR1ZJ0zbIXOZcds7XXMA0GCSqGSIb3DQEBCwUAA4IB"
                + "AQB2Ar1h2X/S4qsVlw0gEbXO//6Rj8mTB4BFW6c5r84n0vTwvA78h003eX00y0ymxO"
                + "i5hkqB8gd1IUSWP1R1ijYtBVPdFi+SsMjUsB5NKquQNlWpo0GlFjRlcXnDC6R6toN2"
                + "QixJb47VM40Vmn2g0ZuMGfy1XoQKvIyRosT92jGm1YcF+nLEHBDr+89apZ8sUpFfWo"
                + "AnCom+8sBGwje6zP10eBbprHyzM8snvdwo/QNLAzLcvVNKP+Sr4H7HKzec3g1+THI0"
                + "M72TzoguJcOZQEI6Pd+FIP5Xad53rq4jCtRGwYrsieH49a3orBnkkJvUKni+mtkxMb"
                + "PTJ7eeMmX9g/0h"
        };

        HashMap<String, String> customHeaderValues = new HashMap<>();
        customHeaderValues.put("foo", "bar");

        Map<String, Object> automaticViewSegmentation = new HashMap<>();
        automaticViewSegmentation.put("One", 2);
        automaticViewSegmentation.put("Three", 4.44d);
        automaticViewSegmentation.put("Five", "Six");

        Map<String, String> metricOverride = new HashMap<>();
        metricOverride.put("SomeKey", "123");
        metricOverride.put("_carrier", "BoneyK");

        //add some custom segments, like dependency library versions
        HashMap<String, Object> customCrashSegmentation = new HashMap<>();
        customCrashSegmentation.put("EarBook", "3.5");
        customCrashSegmentation.put("AdGiver", "6.5");

        CountlyConfig config = (new CountlyConfig(this, COUNTLY_APP_KEY, COUNTLY_SERVER_URL)).setIdMode(DeviceId.Type.OPEN_UDID)//.setDeviceId("67567")
            .setLoggingEnabled(true).setLogListener(new ModuleLog.LogCallback() {
                @Override public void LogHappened(String logMessage, ModuleLog.LogLevel logLevel) {
                    //duplicated countly logs
                    switch (logLevel) {
                        case Verbose:
                            //Log.v("Countly Duplicate", logMessage);
                            break;
                        case Debug:
                            //Log.d("Countly Duplicate", logMessage);
                            break;
                        case Info:
                            //Log.i("Countly Duplicate", logMessage);
                            break;
                        case Warning:
                            //Log.w("Countly Duplicate", logMessage);
                            break;
                        case Error:
                            //Log.e("Countly Duplicate", logMessage);
                            break;
                    }
                }
            })
            .enableCrashReporting().setCustomCrashSegment(customCrashSegmentation)
            .setViewTracking(true).setAutoTrackingUseShortName(true)//.enableTemporaryDeviceIdMode()
            .setRequiresConsent(true).setConsentEnabled(new String[] {
                Countly.CountlyFeatureNames.push, Countly.CountlyFeatureNames.sessions, Countly.CountlyFeatureNames.location,
                Countly.CountlyFeatureNames.attribution, Countly.CountlyFeatureNames.crashes, Countly.CountlyFeatureNames.events,
                Countly.CountlyFeatureNames.starRating, Countly.CountlyFeatureNames.users, Countly.CountlyFeatureNames.views,
                Countly.CountlyFeatureNames.apm, Countly.CountlyFeatureNames.remoteConfig, Countly.CountlyFeatureNames.feedback
            })
            .addCustomNetworkRequestHeaders(customHeaderValues).setPushIntentAddMetadata(true).setRemoteConfigAutomaticDownload(true, new RemoteConfigCallback() {
                @Override
                public void callback(String error) {
                    if (error == null) {
                        Log.d(Countly.TAG, "Automatic remote config download has completed. " + Countly.sharedInstance().remoteConfig().getAllValues());
                    } else {
                        Log.d(Countly.TAG, "Automatic remote config download encountered a problem, " + error);
                    }
                }
            })
            .setParameterTamperingProtectionSalt("SampleSalt")
            .setAutomaticViewSegmentation(automaticViewSegmentation)
            .setAutoTrackingExceptions(new Class[] { ActivityExampleCustomEvents.class })
            .setRecordAllThreadsWithCrash()
            .setCrashFilterCallback(new CrashFilterCallback() {
                @Override
                public boolean filterCrash(String crash) {
                    return crash.contains("crash");
                }
            })
            .setRecordAppStartTime(true)
            .setHttpPostForced(false)
            .setAppStartTimestampOverride(applicationStartTimestamp)
            //.enableCertificatePinning(certificates)
            //.enablePublicKeyPinning(certificates)

            //.setDisableLocation()
            .setLocation("us", "Böston 墨尔本", "-23.8043604,-46.6718331", "10.2.33.12")
            //.setMetricOverride(metricOverride)
            .setEnableAttribution(true);

        Countly.sharedInstance().init(config);
        //Log.i(demoTag, "After calling init. This should return 'true', the value is:" + Countly.sharedInstance().isInitialized());

        CountlyPush.useAdditionalIntentRedirectionChecks = false;
        CountlyPush.init(this, Countly.CountlyMessagingMode.PRODUCTION, Countly.CountlyMessagingProvider.FCM);
        CountlyPush.setNotificationAccentColor(255, 213, 89, 134);

        FirebaseInstanceId.getInstance().getInstanceId()
            .addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
                @Override
                public void onComplete(@NonNull Task<InstanceIdResult> task) {
                    if (!task.isSuccessful()) {
                        Log.e(TAG, "getInstanceId failed", task.getException());
                        return;
                    }

                    // Get new Instance ID token
                    String token = task.getResult().getToken();
                    CountlyPush.onTokenRefresh(token);
                }
            });

        /* Register for broadcast action if you need to be notified when Countly message clicked */
        messageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                Intent sentIntent = intent.getParcelableExtra(CountlyPush.EXTRA_INTENT);
                sentIntent.setExtrasClassLoader(CountlyPush.class.getClassLoader());

                Bundle bun = sentIntent.getParcelableExtra(CountlyPush.EXTRA_MESSAGE);
                CountlyPush.Message message;

                int actionIndex = sentIntent.getIntExtra(CountlyPush.EXTRA_ACTION_INDEX, -100);

                String msg = "NULL";

                if (bun != null) {
                    message = bun.getParcelable(CountlyPush.EXTRA_MESSAGE);
                    if (message != null) {
                        msg = message.message();
                    }
                }

                Log.i("Countly", "[CountlyActivity] Got a message, :[" + msg + "]");
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(CountlyPush.SECURE_NOTIFICATION_BROADCAST);
        registerReceiver(messageReceiver, filter, getPackageName() + COUNTLY_BROADCAST_PERMISSION_POSTFIX, null);
    }
}
