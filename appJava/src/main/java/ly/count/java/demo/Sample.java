package ly.count.java.demo;

import java.io.File;

import ly.count.sdk.ConfigCore;
import ly.count.sdk.internal.Utils;
import ly.count.sdk.java.Config;
import ly.count.sdk.java.Countly;

public class Sample {
    public static void main(String[] args) throws Exception {

        String COUNTLY_SERVER_URL = "XX";
        String COUNTLY_APP_KEY = "XXX";

        System.out.println("Whaaaat " + 2341);
        //Countly.api()

        Config config = new Config(COUNTLY_SERVER_URL, COUNTLY_APP_KEY);
        config.setLoggingLevel(Config.LoggingLevel.DEBUG);
        config.setDeviceIdStrategy(Config.DeviceIdStrategy.UUID);
        config.enableFeatures(Config.Feature.CrashReporting);
        File targetFolder = new File("d:\\__COUNTLY\\java_test\\");

        Countly.init(targetFolder, config);

        //Thread.sleep(2000l);

        System.out.println("Boop");
        Countly.session().begin();

        //Thread.sleep(2000l);


        //Countly.api().event("Bruh");
        Countly.session().event("ruhh").record();

        //Thread.sleep(2000l);
        Long ms = System.currentTimeMillis();
        while (System.currentTimeMillis() - ms < 4000) {
            Thread.sleep(1);
        }

        Countly.session().end();

        System.out.println("I'm dead");
        //throw new Exception("Fooo");
        //System.exit(0);
    }
}
