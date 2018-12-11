package ly.count.sdk.android.internal;

import android.support.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.internal.util.collections.Sets;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import ly.count.sdk.User;
import ly.count.sdk.internal.Ctx;
import ly.count.sdk.internal.InternalConfig;
import ly.count.sdk.internal.Log;
//import ly.count.sdk.internal.UserEditorImpl;
import ly.count.sdk.internal.UserImpl;

import static android.support.test.InstrumentationRegistry.getContext;

@RunWith(AndroidJUnit4.class)
public class UserEditorImplTests {
    private UserImpl user;
    private Ctx ctx;

    @Test
    public void filler(){

    }
/*
    @Before
    public void setUp() throws Exception {
        ctx = new CtxImpl(getContext());
        user = new UserImpl(ctx);
        user.name = "name";
        user.username = "username";
        user.email = "email";
        user.org = "org";
        user.phone = "phone";
        user.picture = new byte[]{3, 2, 1};
        user.gender = User.Gender.FEMALE;
        user.birthyear = 1900;
        user.locale = "en_US";
        user.country = "US";
        user.city = "NY";
        user.location = "9,9";
        new Log().init(new InternalConfig(BaseTests.config()));
    }

    @Test
    public void testBasics() throws Exception {
        UserEditorImpl editor = (UserEditorImpl) user.edit().setName("N").setUsername("U").setEmail("E").setOrg("O").setPhone("P").setPicture(new byte[]{3, 3, 3}).setGender("M").setBirthyear("1900").setLocale("ru_RU").setCountry("RU").setCity("Moscow").setLocation(1,2).addToCohort("c");
        JSONObject object = new JSONObject();
        Set<String> cohortsAdded = new HashSet<>();
        editor.perform(object, cohortsAdded, new HashSet<String>());

        Assert.assertNotNull(object);

        Assert.assertEquals("N", user.name());
        Assert.assertEquals("N", object.getString("name"));

        Assert.assertEquals("U", user.username());
        Assert.assertEquals("U", object.getString("username"));


        Assert.assertEquals("E", user.email());
        Assert.assertEquals("E", object.getString("email"));


        Assert.assertEquals("O", user.org());
        Assert.assertEquals("O", object.getString("org"));


        Assert.assertEquals("P", user.phone());
        Assert.assertEquals("P", object.getString("phone"));


        Assert.assertEquals("ru_RU", user.locale());
        Assert.assertEquals("ru_RU", object.getString("locale"));

        Assert.assertEquals("RU", user.country());
        Assert.assertEquals("RU", object.getString("country"));

        Assert.assertEquals("Moscow", user.city());
        Assert.assertEquals("Moscow", object.getString("city"));

        Assert.assertEquals("1.0,2.0", user.location());
        Assert.assertEquals("1.0,2.0", object.getString("location"));


        Assert.assertEquals(Sets.newSet("c"), user.cohorts());
        Assert.assertEquals(Sets.newSet("c"), cohortsAdded);


        Assert.assertTrue(Arrays.equals(new byte[]{3, 3, 3}, user.picture()));
        Assert.assertEquals(UserEditorImpl.PICTURE_IN_USER_PROFILE, object.getString("picturePath"));

        Assert.assertEquals(User.Gender.MALE, user.gender());
        Assert.assertEquals("M", object.getString("gender"));

        Assert.assertTrue(1900 == user.birthyear());
        Assert.assertEquals(1900, object.getInt("byear"));
    }

    @Test
    public void testUnsetting() throws Exception {
        UserEditorImpl editor = (UserEditorImpl) user.edit().setName(null).setUsername(null).setEmail(null).setOrg(null).setPhone(null).setPicture(null).setGender(null).setBirthyear(null).setLocale(null).setCountry(null).setCity(null).setLocation(null).removeFromCohort("c");
        JSONObject object = new JSONObject();
        Set<String> cohortsRemoved = new HashSet<>();
        editor.perform(object, new HashSet<String>(), cohortsRemoved);

        Assert.assertNotNull(object);

        Assert.assertNull(user.name());
        Assert.assertEquals(JSONObject.NULL, object.get("name"));

        Assert.assertNull(user.username());
        Assert.assertEquals(JSONObject.NULL, object.get("username"));


        Assert.assertNull(user.email());
        Assert.assertEquals(JSONObject.NULL, object.get("email"));


        Assert.assertNull(user.org());
        Assert.assertEquals(JSONObject.NULL, object.get("org"));


        Assert.assertNull(user.phone());
        Assert.assertEquals(JSONObject.NULL, object.get("phone"));


        Assert.assertNull(user.locale());
        Assert.assertEquals(JSONObject.NULL, object.get("locale"));

        Assert.assertNull(user.country());
        Assert.assertEquals(JSONObject.NULL, object.get("country"));

        Assert.assertNull(user.city());
        Assert.assertEquals(JSONObject.NULL, object.get("city"));

        Assert.assertNull(user.location());
        Assert.assertEquals(JSONObject.NULL, object.get("location"));


        Assert.assertEquals(new HashSet<String>(), user.cohorts());
        Assert.assertEquals(Sets.newSet("c"), cohortsRemoved);


        Assert.assertNull(user.picture());
        Assert.assertNull(user.picturePath);
        Assert.assertEquals(JSONObject.NULL, object.get("picturePath"));

        Assert.assertNull(user.gender());
        Assert.assertEquals(JSONObject.NULL, object.get("gender"));

        Assert.assertNull(user.birthyear());
        Assert.assertEquals(JSONObject.NULL, object.get("byear"));
    }

    @Test
    public void testPicturePath() throws Exception {
        String URL = "http://picture.com";
        String SD = "/storage/usbcard1/file.jpg";
        String FILE = "file:///foo/bar/file.jpg";

        UserEditorImpl editor = (UserEditorImpl) user.edit().setPicturePath(URL);
        JSONObject object = new JSONObject();
        editor.perform(object, new HashSet<String>(), new HashSet<String>());
        Assert.assertNotNull(object);
        Assert.assertEquals(URL, user.picturePath);
        Assert.assertEquals(URL, object.getString("picturePath"));

        editor = (UserEditorImpl) user.edit().setPicturePath(SD);
        object = new JSONObject();
        editor.perform(object, new HashSet<String>(), new HashSet<String>());
        Assert.assertNotNull(object);
        Assert.assertEquals(SD, user.picturePath);
        Assert.assertEquals(SD, object.getString("picturePath"));

        editor = (UserEditorImpl) user.edit().setPicturePath(FILE);
        object = new JSONObject();
        editor.perform(object, new HashSet<String>(), new HashSet<String>());
        Assert.assertNotNull(object);
        Assert.assertEquals(FILE, user.picturePath);
        Assert.assertEquals(FILE, object.getString("picturePath"));
    }

    @Test
    public void testOps() throws Exception {
        UserEditorImpl editor = (UserEditorImpl) user.edit()
                .inc("inc", 1).inc("inc", -3)
                .mul("mul", 1.5).mul("mul", 2)
                .min("min", 2.5).min("min", 2)
                .max("max", 3.5).max("max", 3)
                .setOnce("setOnce", "setstring").setOnce("setOnce", "setstring2")
                .push("push", "pushstring").push("push", "pushstring2")
                .pull("pull", 2).pull("pull", 1)
                .pushUnique("pushUnique", false).pushUnique("pushUnique", true)
                .addToCohort("addToCohort")
                .removeFromCohort("removeFromCohort").addToCohort("removeFromCohort").removeFromCohort("otherCohort");

        Set<String> cohortsToAdd = new HashSet<>();
        Set<String> cohortsToRemove = new HashSet<>();
        JSONObject object = new JSONObject();
        editor.perform(object, cohortsToAdd, cohortsToRemove);

        Assert.assertEquals(cohortsToAdd, Sets.newSet("addToCohort", "removeFromCohort"));
        Assert.assertEquals(cohortsToRemove, Sets.newSet("otherCohort"));

        object = object.getJSONObject("custom");
        Assert.assertNotNull(object);

        Assert.assertNotNull(object);
        Assert.assertEquals(-2, object.getJSONObject("inc").getInt("$inc"));
        Assert.assertEquals(3, object.getJSONObject("mul").getInt("$mul"));
        Assert.assertEquals(2, object.getJSONObject("min").getInt("$min"));
        Assert.assertEquals(3.5, object.getJSONObject("max").getDouble("$max"));
        Assert.assertEquals("setstring2", object.getJSONObject("setOnce").getString("$setOnce"));
        Assert.assertEquals("pushstring", object.getJSONObject("push").getJSONArray("$push").getString(0));
        Assert.assertEquals("pushstring2", object.getJSONObject("push").getJSONArray("$push").getString(1));
        Assert.assertEquals(2, object.getJSONObject("pull").getJSONArray("$pull").getInt(0));
        Assert.assertEquals(1, object.getJSONObject("pull").getJSONArray("$pull").getInt(1));
        Assert.assertEquals(false, object.getJSONObject("pushUnique").getJSONArray("$addToSet").getBoolean(0));
        Assert.assertEquals(true, object.getJSONObject("pushUnique").getJSONArray("$addToSet").getBoolean(1));
    }

    @Test(expected = IllegalStateException.class)
    public void testLocationFormat1() throws Exception {
        user.edit().setLocation("a,b");
    }

    @Test(expected = IllegalStateException.class)
    public void testLocationFormat2() throws Exception {
        user.edit().setLocation("12");
    }

    @Test(expected = IllegalStateException.class)
    public void testLocationFormat3() throws Exception {
        user.edit().setLocation("3,4,5");
    }
    */
}
