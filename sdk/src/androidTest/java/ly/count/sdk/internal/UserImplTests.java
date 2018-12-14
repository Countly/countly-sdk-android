package ly.count.sdk.internal;


import android.support.test.runner.AndroidJUnit4;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;


import java.util.Arrays;

import ly.count.sdk.User;
import ly.count.sdk.android.internal.BaseTests;

@RunWith(AndroidJUnit4.class)
public class UserImplTests extends BaseTests {

    @Test
    public void testStorageAllSet() throws Exception {
        UserImpl user = new UserImpl(ctx);
        user.name = "name";
        user.username = "username";
        user.email = "email";
        user.org = "org";
        user.phone = "phone";
        user.picture = new byte[]{3, 2, 1};
        user.picturePath = "http://picture.com";
        user.gender = User.Gender.FEMALE;
        user.birthyear = 1900;

        setUpApplication(null);
        Assert.assertTrue(Storage.push(ctx, user));

        User stored = Storage.read(ctx, new UserImpl(ctx));
        Assert.assertNotNull(stored);

        Assert.assertEquals(user.name, stored.name());
        Assert.assertEquals(user.username, stored.username());
        Assert.assertEquals(user.email, stored.email());
        Assert.assertEquals(user.org, stored.org());
        Assert.assertEquals(user.phone, stored.phone());
        Assert.assertTrue(Arrays.equals(user.picture, stored.picture()));
        Assert.assertEquals(user.picturePath, stored.picturePath());
        Assert.assertEquals(user.gender, stored.gender());
        Assert.assertEquals(user.birthyear, stored.birthyear());

    }

    @Test
    public void testStorageNothingSet() throws Exception {
        UserImpl user = new UserImpl(ctx);

        setUpApplication(null);
        Assert.assertTrue(Storage.push(ctx, user));

        User stored = Storage.read(ctx, new UserImpl(ctx));
        Assert.assertNotNull(stored);

        Assert.assertNull(stored.name());
        Assert.assertNull(stored.username());
        Assert.assertNull(stored.email());
        Assert.assertNull(stored.org());
        Assert.assertNull(stored.phone());
        Assert.assertNull(stored.picture());
        Assert.assertNull(stored.picturePath());
        Assert.assertNull(stored.gender());
        Assert.assertNull(stored.birthyear());

    }

    @Test
    public void testStorageSomeSet() throws Exception {
        UserImpl user = new UserImpl(ctx);
        user.username = "username";
        user.gender = User.Gender.MALE;
        user.phone = "phone";

        setUpApplication(null);
        Assert.assertTrue(Storage.push(ctx, user));

        User stored = Storage.read(ctx, new UserImpl(ctx));
        Assert.assertNotNull(stored);

        Assert.assertNull(stored.name());
        Assert.assertEquals(user.username, stored.username());
        Assert.assertNull(stored.email());
        Assert.assertNull(stored.org());
        Assert.assertEquals(user.phone, stored.phone());
        Assert.assertNull(stored.picture());
        Assert.assertNull(stored.picturePath());
        Assert.assertEquals(user.gender, stored.gender());
        Assert.assertNull(stored.birthyear());

    }

    @Test
    public void testCommit() throws Exception {
        setUpApplication(defaultConfig());

        UserImpl user = sdk.user();
        Assert.assertNotNull(user);

        user.edit().setName("N").setUsername("U").setEmail("E").setOrg("O").setPhone("P")
                .setPicture(new byte[]{3, 3, 3}).setGender("M").setBirthyear("1900")
                .setLocale("ru_RU").setCountry("RU").setCity("Moscow").setLocation(1,2)
                .addToCohort("c").removeFromCohort("d").commit();


        Request request = Storage.readOne(ctx, new Request(), false);
        Assert.assertNotNull(request);

        String query = request.params.toString();
        String json = request.params.get("user_details");
        Assert.assertNotNull(json);
        Assert.assertNotNull(new JSONObject(json));

        Assert.assertTrue(json.contains("\"name\":\"N\""));
        Assert.assertTrue(json.contains("\"username\":\"U\""));
        Assert.assertTrue(json.contains("\"email\":\"E\""));
        Assert.assertTrue(json.contains("\"org\":\"O\""));
        Assert.assertTrue(json.contains("\"phone\":\"P\""));
        Assert.assertTrue(json.contains("\"gender\":\"M\""));
        Assert.assertTrue(json.contains("\"byear\":1900"));
        Assert.assertTrue(query.contains("&add_cohorts=" + Utils.urlencode("[\"c\"]") + "&"));
        Assert.assertTrue(query.contains("&remove_cohorts=" + Utils.urlencode("[\"d\"]") + "&"));
        Assert.assertTrue(query.contains("&locale=ru_RU&"));
        Assert.assertTrue(query.contains("&country_code=RU&"));
        Assert.assertTrue(query.contains("&city=Moscow&"));
        Assert.assertTrue(query.contains("&location=" + Utils.urlencode("1.0,2.0")));

    }

    @Test
    public void testCommitLocationOptOut() throws Exception {
        setUpApplication(defaultConfig());

        UserImpl user = sdk.user();
        Assert.assertNotNull(user);

        user.edit().setName("N").setUsername("U").setEmail("E").setOrg("O").setPhone("P")
                .setPicture(new byte[]{3, 3, 3}).setGender("M").setBirthyear("1900")
                .optOutFromLocationServices()
                .addToCohort("c").removeFromCohort("d").commit();


        Request request = Storage.readOne(ctx, new Request(), false);
        Assert.assertNotNull(request);

        String query = request.params.toString();
        String json = request.params.get("user_details");
        Assert.assertNotNull(json);
        Assert.assertNotNull(new JSONObject(json));

        Assert.assertTrue(json.contains("\"name\":\"N\""));
        Assert.assertTrue(json.contains("\"username\":\"U\""));
        Assert.assertTrue(json.contains("\"email\":\"E\""));
        Assert.assertTrue(json.contains("\"org\":\"O\""));
        Assert.assertTrue(json.contains("\"phone\":\"P\""));
        Assert.assertTrue(json.contains("\"gender\":\"M\""));
        Assert.assertTrue(json.contains("\"byear\":1900"));
        Assert.assertTrue(query.contains("&add_cohorts=" + Utils.urlencode("[\"c\"]") + "&"));
        Assert.assertTrue(query.contains("&remove_cohorts=" + Utils.urlencode("[\"d\"]") + "&"));
        Assert.assertTrue(query.contains("&country_code=&"));
        Assert.assertTrue(query.contains("&city=&"));
        Assert.assertTrue(query.contains("&location=&"));

    }

    @Test
    public void testCommitLocationNulling() throws Exception {
        setUpApplication(defaultConfig());

        UserImpl user = sdk.user();
        Assert.assertNotNull(user);
        user.locale = "en_US";
        user.country = "US";
        user.city = "NY";
        user.location = "1.0,2.0";

        user.edit().setName("N").setUsername("U").setEmail("E").setOrg("O").setPhone("P")
                .setPicture(new byte[]{3, 3, 3}).setGender("M").setBirthyear("1900")
                .setLocale(null).setCountry(null).setCity(null).setLocation(null)
                .addToCohort("c").removeFromCohort("d").commit();


        Request request = Storage.readOne(ctx, new Request(), false);
        Assert.assertNotNull(request);

        String query = request.params.toString();
        String json = request.params.get("user_details");
        Assert.assertNotNull(json);
        Assert.assertNotNull(new JSONObject(json));

        Assert.assertTrue(json.contains("\"name\":\"N\""));
        Assert.assertTrue(json.contains("\"username\":\"U\""));
        Assert.assertTrue(json.contains("\"email\":\"E\""));
        Assert.assertTrue(json.contains("\"org\":\"O\""));
        Assert.assertTrue(json.contains("\"phone\":\"P\""));
        Assert.assertTrue(json.contains("\"gender\":\"M\""));
        Assert.assertTrue(json.contains("\"byear\":1900"));
        Assert.assertTrue(query.contains("&add_cohorts=" + Utils.urlencode("[\"c\"]") + "&"));
        Assert.assertTrue(query.contains("&remove_cohorts=" + Utils.urlencode("[\"d\"]")));
        Assert.assertFalse(query.contains("&country_code="));
        Assert.assertFalse(query.contains("&city="));
        Assert.assertFalse(query.endsWith("&location="));

    }
}
