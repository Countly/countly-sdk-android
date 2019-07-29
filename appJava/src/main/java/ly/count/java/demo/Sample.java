package ly.count.java.demo;

import java.io.File;

import ly.count.sdk.ConfigCore;
import ly.count.sdk.internal.Utils;
import ly.count.sdk.java.Config;
import ly.count.sdk.java.Countly;

public class Sample {
    public static void main(String[] args) throws InterruptedException {

        String COUNTLY_SERVER_URL = "XX";
        String COUNTLY_APP_KEY = "XXX";

        System.out.println("Whaaaat " + 2341);
        //Countly.api()

        Config config = new Config(COUNTLY_SERVER_URL, COUNTLY_APP_KEY);
        config.setLoggingLevel(ConfigCore.LoggingLevel.DEBUG);
        File targetFolder = new File("d:\\__COUNTLY\\java_test\\");

        Countly.init(targetFolder, config);

        Thread.sleep(2000l);

        System.out.println("Boop");

        Thread.sleep(2000l);

        Countly.api().event("Bruh");

        Thread.sleep(2000l);
    }
}
