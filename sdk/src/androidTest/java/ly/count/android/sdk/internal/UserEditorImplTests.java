package ly.count.android.sdk.internal;

import android.support.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

import ly.count.android.sdk.Config;
import ly.count.android.sdk.User;
import ly.count.android.sdk.UserEditor;

import static android.support.test.InstrumentationRegistry.getContext;

@RunWith(AndroidJUnit4.class)
public class UserEditorImplTests {
    private UserImpl user;

    @Before
    public void setUp() throws Exception {
        user = new UserImpl();
        user.name = "name";
        user.username = "username";
        user.email = "email";
        user.org = "org";
        user.phone = "phone";
        user.picture = new byte[]{3, 2, 1};
        user.gender = User.Gender.FEMALE;
        user.birthyear = 1900;
    }

    @Test
    public void testBasics() throws Exception {
        UserEditorImpl editor = (UserEditorImpl) user.edit().setName("N").setUsername("U").setEmail("E").setOrg("O").setPhone("P").setPicture(new byte[]{3, 3, 3}).setGender("M").setBirthyear("1900");
        JSONObject object = editor.perform();

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


        Assert.assertTrue(Arrays.equals(new byte[]{3, 3, 3}, user.picture()));
        Assert.assertEquals(UserEditorImpl.PICTURE_IN_USER_PROFILE, object.getString("picturePath"));

        Assert.assertEquals(User.Gender.MALE, user.gender());
        Assert.assertEquals("M", object.getString("gender"));

        Assert.assertTrue(1900 == user.birthyear());
        Assert.assertEquals(1900, object.getInt("byear"));
    }

    @Test
    public void testUnsetting() throws Exception {
        UserEditorImpl editor = (UserEditorImpl) user.edit().setName(null).setUsername(null).setEmail(null).setOrg(null).setPhone(null).setPicture(null).setGender(null).setBirthyear(null);
        JSONObject object = editor.perform();

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


        Assert.assertNull(user.picture());
        Assert.assertNull(user.picturePath());
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
        JSONObject object = editor.perform();
        Assert.assertNotNull(object);
        Assert.assertEquals(URL, user.picturePath());
        Assert.assertEquals(URL, object.getString("picturePath"));

        editor = (UserEditorImpl) user.edit().setPicturePath(SD);
        object = editor.perform();
        Assert.assertNotNull(object);
        Assert.assertEquals(SD, user.picturePath());
        Assert.assertEquals(SD, object.getString("picturePath"));

        editor = (UserEditorImpl) user.edit().setPicturePath(FILE);
        object = editor.perform();
        Assert.assertNotNull(object);
        Assert.assertEquals(FILE, user.picturePath());
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
                .pushUnique("pushUnique", false).pushUnique("pushUnique", true);

        JSONObject object = editor.perform();
        Assert.assertNotNull(object);
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
}
