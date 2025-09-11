// This sample code supports Appium Java client >=9
// https://github.com/appium/java-client
package ly.count.android.demo;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.remote.options.BaseOptions;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Random;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebElement;

public class HybridAppTest {

    private AndroidDriver driver;

    @Before
    public void setUp() {
        Capabilities options = new BaseOptions()
            .amend("platformName", "Android")
            .amend("appium:deviceName", "emulator-5554")
            .amend("appium:appPackage", "ly.count.android.demo")
            .amend("appium:appActivity", ".MainActivity")
            .amend("appium:automationName", "UiAutomator2")
            .amend("appium:ensureWebviewsHavePages", true)
            .amend("appium:nativeWebScreenshot", true)
            .amend("appium:newCommandTimeout", 3600)
            .amend("appium:connectHardwareKeyboard", true);

        driver = new AndroidDriver(this.getUrl(), options);
    }

    @Test
    public void sampleTest() throws InterruptedException {
        WebElement el1 = driver.findElement(AppiumBy.id("ly.count.android.demo:id/button71"));
        el1.click();
        // Code generation for action 'elementClick' is not currently supported
        WebElement el2 = driver.findElement(AppiumBy.id("ly.count.android.demo:id/editTextDeviceIdContentZone"));
        int randomInt = new Random().nextInt(1000);
        el2.sendKeys("sticky_appium_" + randomInt);
        // Code generation for action 'elementSendKeys' is not currently supported
        WebElement el3 = driver.findElement(AppiumBy.id("ly.count.android.demo:id/button80"));
        el3.click();
        // Code generation for action 'elementClick' is not currently supported
        WebElement el4 = driver.findElement(AppiumBy.id("ly.count.android.demo:id/button74"));
        el4.click();
        Thread.sleep(1000);
        // Code generation for action 'elementClick' is not currently supported
        driver.context("WEBVIEW_ly.count.android.demo");
        WebElement el5 = driver.findElement(AppiumBy.xpath("//div[@id=\"app\"]/div/div[2]/button[2]"));
        el5.click();
        // Code generation for action 'elementClick' is not currently supported
    }

    @After
    public void tearDown() {
        driver.quit();
    }

    private URL getUrl() {
        try {
            return new URL("http://127.0.0.1:4723");
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return null;
    }
}
