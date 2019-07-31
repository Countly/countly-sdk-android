package ly.count.sdk.android.internal;

import junit.framework.Assert;

import org.junit.Test;
import org.powermock.reflect.Whitebox;

import java.io.IOException;

import ly.count.sdk.User;
import ly.count.sdk.UserEditor;
import ly.count.sdk.android.Countly;
import ly.count.sdk.internal.Storage;
import ly.count.sdk.internal.Tasks;

import static android.support.test.InstrumentationRegistry.getContext;

public class GDPRTests extends BaseTests {

    @Override
    protected ly.count.sdk.android.Config defaultConfig() throws Exception {
        return super.defaultConfig();//.setRequiresConsent(true);
    }

    String[] getFileList(){
        return ctx.getContext().getApplicationContext().fileList();
    }

    void waitForStorage() throws Exception {
        //wait for storage to finish
        Tasks deviceIdTasks = Utils.reflectiveGetField(Storage.class, "tasks");
        Whitebox.invokeMethod(deviceIdTasks, "await");
    }

    void containsOnlyConfig(){
        String[] files = getFileList();
        Assert.assertEquals("[CLY]_config_0", files[0]);
        Assert.assertEquals(1, files.length);
    }

    @Test
    public void initWithoutConsent() throws Exception {
        setUpApplication(defaultConfig().setRequiresConsent(true), true);

        //wait for storage to finish, there should be only one entry - config
        waitForStorage();
        containsOnlyConfig();
    }

    @Test
    public void noConsentEvents() throws Exception {
        setUpApplication(defaultConfig().setRequiresConsent(true), true);

        Countly.session(getContext()).event("boop").record();
        Countly.session(getContext()).event("FriendRequest").setCount(3).record();
        Countly.session(getContext()).event("AddToCart").setCount(3).setSum(134).record();
        Countly.session(getContext()).event("InCheckout").setCount(1).setDuration(55).record();
        Countly.session(getContext()).event("ProductView").addSegment("category", "pants").record();
        Countly.session(getContext()).event("ProductView").addSegment("category", "jackets").setCount(15).record();
        Countly.session(getContext()).event("AddToCart").addSegment("category", "jackets").setCount(25).setSum(10).record();
        Countly.session(getContext()).event("AddToCart").addSegment("category", "jackets").setCount(25).setSum(10).setDuration(50).record();
        Countly.session(getContext()).timedEvent("TimedEvent");
        Countly.session(getContext()).timedEvent("TimedEvent").endAndRecord();
        Countly.session(getContext()).timedEvent("TimedEvent");
        Countly.session(getContext()).timedEvent("TimedEvent").addSegment("wall", "orange").setCount(4).setSum(34).endAndRecord();

        //wait for storage to finish, there should be only one entry - config
        waitForStorage();
        containsOnlyConfig();
    }

    @Test
    public void noConsentExceptions() throws Exception {
        setUpApplication(defaultConfig().setRequiresConsent(true), true);

        Countly.session(getContext()).addCrashReport(new RuntimeException("Fatal Exception"), true);
        Countly.session(getContext()).addCrashReport(new IOException("Non-Fatal Exception"), false);

        //wait for storage to finish, there should be only one entry - config
        waitForStorage();
        containsOnlyConfig();
    }

    @Test
    public void noConsentLocation() throws Exception {
        setUpApplication(defaultConfig().setRequiresConsent(true), true);

        Countly.session(getContext()).addLocation(234.34, 54.43);

        UserEditor editor = Countly.user(getContext()).edit()
                .set("country", "Jamaica")
                .set("city", "Kingston");
        editor.commit();

        //wait for storage to finish, there should be only one entry - config
        waitForStorage();
        containsOnlyConfig();
    }

    @Test
    public void noConsentViews() throws Exception {
        setUpApplication(defaultConfig().setRequiresConsent(true), true);

        Countly.session(getContext()).view("fdf");

        //wait for storage to finish, there should be only one entry - config
        waitForStorage();
        containsOnlyConfig();
    }

    public void noConsentAttribution() throws Exception {
        setUpApplication(defaultConfig().setRequiresConsent(true), true);

        //todo, nothing here yet

        //wait for storage to finish, there should be only one entry - config
        waitForStorage();
        containsOnlyConfig();
    }

    @Test
    public void noConsentUsers() throws Exception {
        setUpApplication(defaultConfig().setRequiresConsent(true), true);

        UserEditor editor = Countly.user(getContext()).edit()
                .setName("Firstname Lastname")
                .setUsername("nickname")
                .setEmail("test@test.com")
                .setOrg("Tester")
                .setPhone("+123456789")
                .setGender(User.Gender.MALE)
                .setBirthyear(1987)
                .setPicturePath("http://i.pravatar.cc/300")
                .set("country", "Jamaica")
                .set("city", "Kingston")
                .set("address", "6, 56 Hope Rd, Kingston, Jamaica");

        editor.commit();

        //wait for storage to finish, there should be only one entry - config
        waitForStorage();
        containsOnlyConfig();
    }


    public void noConsentPush() throws Exception {
        setUpApplication(defaultConfig().setRequiresConsent(true), true);

        //todo, nothing here yet

        //wait for storage to finish, there should be only one entry - config
        waitForStorage();
        containsOnlyConfig();
    }

    @Test
    public void noConsentStarRating() throws Exception {
        setUpApplication(defaultConfig().setRequiresConsent(true), true);

        Countly.Ratings().setStarRatingDialogTexts("dsds", "fdf", "re");
        Countly.Ratings().showFeedbackPopup("df", "fdfd", null, null);
        Countly.Ratings().clearAutomaticStarRatingSessionCount();

        //wait for storage to finish, there should be only one entry - config
        waitForStorage();
        containsOnlyConfig();
    }
}
