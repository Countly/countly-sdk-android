package ly.count.java.demo;

import java.io.File;

import ly.count.sdk.java.Config;
import ly.count.sdk.java.Countly;

public class Sample {
    public static void main(String[] args) throws Exception {

        String COUNTLY_SERVER_URL = "http://localhost:3001";
        String COUNTLY_APP_KEY = "8f3b5e1e8784eb0447236d2d1ab6211797e3ebb9";

        Config config = new Config(COUNTLY_SERVER_URL, COUNTLY_APP_KEY)
                .setLoggingLevel(Config.LoggingLevel.DEBUG)
                .setDeviceIdStrategy(Config.DeviceIdStrategy.UUID)
                .enableFeatures(Config.Feature.Events, Config.Feature.Sessions, Config.Feature.CrashReporting)
                .setRequiresConsent(true)
                .setAutoSessionsTracking(true)
                .setEventsBufferSize(1);

        // Countly needs persistent storage for requests, configuration storage, user profiles and other temporary data,
        // therefore requires a separate data folder to run
        File targetFolder = new File("/projects/countly-sdk-android/app-java/data");

        // Main initialization call, SDK can be used after this one is done
        Countly.init(targetFolder, config);

        // Usually, all interactions with SDK are to be done through a session instance:
        Countly.session().begin();

        // using .getSession() you can check if an active session exists:
        if (Countly.getSession() != null) {
            Countly.getSession().event("dummy").setCount(2).record();
        }

        // Yet sometimes you might need to use api() instead.
        // Countly.api() returns an object with API very similar to Countly.session() API:
        Countly.api().event("Skipped").setDuration(10).record();

        // GDPR compliance: simulate user granting rights to record events
        // In this particular case all prior event- and session-related calls were basically
        // ignored and didn't result in recording of any data
        //
        // Note! GDPR consent calls must be done on every app launch after Countly.init()
        Countly.onConsent(Config.Feature.Events, Config.Feature.Sessions);

        // Simple app lifecycle simulation
        long ms = System.currentTimeMillis();
        while (System.currentTimeMillis() - ms < 60000) {
            if (Countly.getSession() != null && System.currentTimeMillis() - ms > 40000) {
                Countly.api().event("Recorded").record();

                // You should end sessions this prior to exiting from your app to ensure correct session durations.
                // But even if you don't, this session will be automatically ended on next app launch.
                Countly.session().end();
            }
            // Even if we uncomment this, the crash wouldn't be recorded since there's no crash consent
//            if (System.currentTimeMillis() - ms > 50000) {
//                throw new IllegalStateException("Crashy");
//            }
            Thread.sleep(100);
        }

        // Gracefully stop SDK to stop all SDK threads and allow this app to exit
        // Just in case, usually you don't want to clear data to reuse device id for next app runs
        // and to send any requests which might not be sent
        Countly.stop(true);
    }
}
